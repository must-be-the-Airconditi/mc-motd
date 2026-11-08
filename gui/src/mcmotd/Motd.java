package mcmotd;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** MOTD / 聊天组件解析：§ 颜色码、十六进制色、JSON 组件嵌套。 */
public final class Motd {

    private Motd() {
    }

    public static final Color DEFAULT_COLOR = new Color(0xFFFFFF);

    private static final Map<String, Color> NAMED = new HashMap<>();
    private static final Map<Character, Color> CODES = new HashMap<>();

    static {
        NAMED.put("black", new Color(0x000000));
        NAMED.put("dark_blue", new Color(0x0000AA));
        NAMED.put("dark_green", new Color(0x00AA00));
        NAMED.put("dark_aqua", new Color(0x00AAAA));
        NAMED.put("dark_red", new Color(0xAA0000));
        NAMED.put("dark_purple", new Color(0xAA00AA));
        NAMED.put("gold", new Color(0xFFAA00));
        NAMED.put("gray", new Color(0xAAAAAA));
        NAMED.put("dark_gray", new Color(0x555555));
        NAMED.put("blue", new Color(0x5555FF));
        NAMED.put("green", new Color(0x55FF55));
        NAMED.put("aqua", new Color(0x55FFFF));
        NAMED.put("red", new Color(0xFF5555));
        NAMED.put("light_purple", new Color(0xFF55FF));
        NAMED.put("yellow", new Color(0xFFFF55));
        NAMED.put("white", new Color(0xFFFFFF));

        CODES.put('0', new Color(0x000000));
        CODES.put('1', new Color(0x0000AA));
        CODES.put('2', new Color(0x00AA00));
        CODES.put('3', new Color(0x00AAAA));
        CODES.put('4', new Color(0xAA0000));
        CODES.put('5', new Color(0xAA00AA));
        CODES.put('6', new Color(0xFFAA00));
        CODES.put('7', new Color(0xAAAAAA));
        CODES.put('8', new Color(0x555555));
        CODES.put('9', new Color(0x5555FF));
        CODES.put('a', new Color(0x55FF55));
        CODES.put('b', new Color(0x55FFFF));
        CODES.put('c', new Color(0xFF5555));
        CODES.put('d', new Color(0xFF55FF));
        CODES.put('e', new Color(0xFFFF55));
        CODES.put('f', new Color(0xFFFFFF));
    }

    /** 一段带样式的文字 */
    public static final class Seg {
        public String text;
        public Color color;
        public boolean bold;
        public boolean italic;
        public boolean underlined;
        public boolean strikethrough;
        public boolean obfuscated;

        Seg copy() {
            Seg seg = new Seg();
            seg.text = text;
            seg.color = color;
            seg.bold = bold;
            seg.italic = italic;
            seg.underlined = underlined;
            seg.strikethrough = strikethrough;
            seg.obfuscated = obfuscated;
            return seg;
        }
    }

    /** 解析后的 MOTD */
    public static final class Parsed {
        public List<Seg> segments = new ArrayList<>();
        public List<List<Seg>> lines = new ArrayList<>();
        public String plain = "";

        public String firstLine() {
            return lines.isEmpty() ? "" : join(lines.get(0));
        }
    }

    private static Seg baseStyle() {
        Seg seg = new Seg();
        seg.color = DEFAULT_COLOR;
        return seg;
    }

    private static boolean sameStyle(Seg a, Seg b) {
        return a.color.equals(b.color)
                && a.bold == b.bold
                && a.italic == b.italic
                && a.underlined == b.underlined
                && a.strikethrough == b.strikethrough
                && a.obfuscated == b.obfuscated;
    }

    /** 解析 description 字段 */
    public static Parsed parse(Object description) {
        Parsed parsed = new Parsed();
        if (description instanceof String) {
            pushStyledText((String) description, baseStyle(), parsed.segments);
        } else if (description instanceof List) {
            walk(description, baseStyle(), parsed.segments);
        } else if (description instanceof Map) {
            walk(description, baseStyle(), parsed.segments);
        }

        // 合并同样式相邻片段
        List<Seg> merged = new ArrayList<>();
        for (Seg seg : parsed.segments) {
            if (seg.text.isEmpty()) {
                continue;
            }
            Seg last = merged.isEmpty() ? null : merged.get(merged.size() - 1);
            if (last != null && sameStyle(last, seg)) {
                last.text += seg.text;
            } else {
                merged.add(seg.copy());
            }
        }
        parsed.segments = merged;

        // 按 \n 拆行
        parsed.lines.add(new ArrayList<>());
        for (Seg seg : merged) {
            String[] parts = seg.text.split("\n", -1);
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) {
                    parsed.lines.add(new ArrayList<>());
                }
                if (!parts[i].isEmpty()) {
                    Seg piece = seg.copy();
                    piece.text = parts[i];
                    parsed.lines.get(parsed.lines.size() - 1).add(piece);
                }
            }
        }
        parsed.plain = join(merged);
        return parsed;
    }

    @SuppressWarnings("unchecked")
    private static void walk(Object node, Seg style, List<Seg> out) {
        if (node == null) {
            return;
        }
        if (node instanceof String) {
            pushStyledText((String) node, style, out);
            return;
        }
        if (node instanceof List) {
            for (Object child : (List<Object>) node) {
                walk(child, style, out);
            }
            return;
        }
        if (!(node instanceof Map)) {
            pushStyledText(String.valueOf(node), style, out);
            return;
        }

        Map<String, Object> map = (Map<String, Object>) node;
        Seg next = shallowCopy(style);

        Object color = map.get("color");
        if ("reset".equals(color)) {
            next = baseStyle();
        } else if (color instanceof String) {
            Color resolved = colorOf((String) color);
            if (resolved != null) {
                next.color = resolved;
            }
        }
        applyFlag(map, "bold", next, 0);
        applyFlag(map, "italic", next, 1);
        applyFlag(map, "underlined", next, 2);
        applyFlag(map, "strikethrough", next, 3);
        applyFlag(map, "obfuscated", next, 4);

        Object text = map.get("text");
        if (text != null) {
            pushStyledText(String.valueOf(text), next, out);
        } else if (map.get("translate") instanceof String) {
            pushStyledText((String) map.get("translate"), next, out);
        } else if (map.get("score") instanceof Map) {
            Object value = ((Map<String, Object>) map.get("score")).get("value");
            if (value != null) {
                pushStyledText(String.valueOf(value), next, out);
            }
        } else if (map.get("selector") instanceof String) {
            pushStyledText((String) map.get("selector"), next, out);
        } else if (map.get("keybind") instanceof String) {
            pushStyledText((String) map.get("keybind"), next, out);
        }

        Object extra = map.get("extra");
        if (extra != null) {
            walk(extra, next, out);
        }
    }

    private static void applyFlag(Map<String, Object> map, String key, Seg seg, int flag) {
        Object value = map.get(key);
        if (!(value instanceof Boolean)) {
            return;
        }
        boolean on = (Boolean) value;
        switch (flag) {
            case 0: seg.bold = on; break;
            case 1: seg.italic = on; break;
            case 2: seg.underlined = on; break;
            case 3: seg.strikethrough = on; break;
            default: seg.obfuscated = on; break;
        }
    }

    private static Seg shallowCopy(Seg seg) {
        return seg.copy();
    }

    /** 解析一段可能带 § 码的文本 */
    private static void pushStyledText(String text, Seg style, List<Seg> out) {
        if (text == null || text.isEmpty()) {
            return;
        }
        Seg current = shallowCopy(style);
        StringBuilder buffer = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != '\u00a7' || i + 1 >= text.length()) {
                buffer.append(c);
                continue;
            }
            char code = Character.toLowerCase(text.charAt(i + 1));

            // §x§f§f§8§8§0§0 十六进制色
            if (code == 'x') {
                StringBuilder hex = new StringBuilder();
                int j = i + 2;
                while (hex.length() < 6 && j + 1 < text.length() && text.charAt(j) == '\u00a7') {
                    hex.append(text.charAt(j + 1));
                    j += 2;
                }
                if (hex.length() == 6 && hex.toString().matches("(?i)[0-9a-f]{6}")) {
                    flush(buffer, current, out);
                    current = shallowCopy(current);
                    current.color = new Color(Integer.parseInt(hex.toString(), 16));
                    i = j - 1;
                    continue;
                }
            }

            Color color = CODES.get(code);
            if (code == 'r') {
                flush(buffer, current, out);
                current = baseStyle();
                i++;
                continue;
            } else if (color != null) {
                flush(buffer, current, out);
                current = shallowCopy(current);
                current.color = color;
                i++;
                continue;
            }

            boolean handled = true;
            switch (code) {
                case 'k': current = flag(current, 4, out, buffer); break;
                case 'l': current = flag(current, 0, out, buffer); break;
                case 'm': current = flag(current, 3, out, buffer); break;
                case 'n': current = flag(current, 2, out, buffer); break;
                case 'o': current = flag(current, 1, out, buffer); break;
                default: handled = false;
            }
            if (handled) {
                i++;
            } else {
                buffer.append(c);
            }
        }
        flush(buffer, current, out);
    }

    private static Seg flag(Seg current, int flag, List<Seg> out, StringBuilder buffer) {
        flush(buffer, current, out);
        Seg next = shallowCopy(current);
        switch (flag) {
            case 0: next.bold = true; break;
            case 1: next.italic = true; break;
            case 2: next.underlined = true; break;
            case 3: next.strikethrough = true; break;
            default: next.obfuscated = true; break;
        }
        return next;
    }

    private static void flush(StringBuilder buffer, Seg style, List<Seg> out) {
        if (buffer.length() == 0) {
            return;
        }
        Seg seg = shallowCopy(style);
        seg.text = buffer.toString();
        out.add(seg);
        buffer.setLength(0);
    }

    public static Color colorOf(String name) {
        if (name == null) {
            return null;
        }
        String value = name.trim().toLowerCase();
        if (NAMED.containsKey(value)) {
            return NAMED.get(value);
        }
        if (value.matches("#[0-9a-f]{6}")) {
            return new Color(Integer.parseInt(value.substring(1), 16));
        }
        if (value.matches("#[0-9a-f]{3}")) {
            char r = value.charAt(1);
            char g = value.charAt(2);
            char b = value.charAt(3);
            return new Color(Integer.parseInt("" + r + r + g + g + b + b, 16));
        }
        return null;
    }

    /** 去掉 § 颜色码 */
    public static String stripFormatting(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\u00a7[0-9a-fk-orxX]", "");
    }

    public static String join(List<Seg> segments) {
        StringBuilder sb = new StringBuilder();
        for (Seg seg : segments) {
            sb.append(seg.text);
        }
        return sb.toString();
    }
}
