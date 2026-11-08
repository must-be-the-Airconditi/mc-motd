package mcmotd;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 极简 JSON 解析器（只读，够解析服务器状态就行）。
 * 返回 Map / List / String / Double / Boolean / null。
 */
public final class Json {

    private final String text;
    private int pos;

    private Json(String text) {
        this.text = text;
    }

    public static Object parse(String text) {
        Json parser = new Json(text);
        parser.skipWhitespace();
        Object value = parser.parseValue();
        parser.skipWhitespace();
        if (parser.pos != text.length()) {
            throw new IllegalArgumentException("JSON 末尾有多余内容，位置 " + parser.pos);
        }
        return value;
    }

    private void skipWhitespace() {
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                pos++;
            } else {
                break;
            }
        }
    }

    private Object parseValue() {
        skipWhitespace();
        if (pos >= text.length()) {
            throw new IllegalArgumentException("JSON 提前结束");
        }
        char c = text.charAt(pos);
        switch (c) {
            case '{':
                return parseObject();
            case '[':
                return parseArray();
            case '"':
                return parseString();
            case 't':
                expect("true");
                return Boolean.TRUE;
            case 'f':
                expect("false");
                return Boolean.FALSE;
            case 'n':
                expect("null");
                return null;
            default:
                return parseNumber();
        }
    }

    private Map<String, Object> parseObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++; // {
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipWhitespace();
            String key = parseString();
            skipWhitespace();
            if (peek() != ':') {
                throw new IllegalArgumentException("JSON 对象缺少冒号，位置 " + pos);
            }
            pos++;
            Object value = parseValue();
            map.put(key, value);
            skipWhitespace();
            char c = peek();
            if (c == ',') {
                pos++;
                continue;
            }
            if (c == '}') {
                pos++;
                return map;
            }
            throw new IllegalArgumentException("JSON 对象格式错误，位置 " + pos);
        }
    }

    private List<Object> parseArray() {
        List<Object> list = new ArrayList<>();
        pos++; // [
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (true) {
            list.add(parseValue());
            skipWhitespace();
            char c = peek();
            if (c == ',') {
                pos++;
                continue;
            }
            if (c == ']') {
                pos++;
                return list;
            }
            throw new IllegalArgumentException("JSON 数组格式错误，位置 " + pos);
        }
    }

    private String parseString() {
        if (peek() != '"') {
            throw new IllegalArgumentException("JSON 字符串格式错误，位置 " + pos);
        }
        pos++;
        StringBuilder sb = new StringBuilder();
        while (pos < text.length()) {
            char c = text.charAt(pos++);
            if (c == '"') {
                return sb.toString();
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            if (pos >= text.length()) {
                break;
            }
            char esc = text.charAt(pos++);
            switch (esc) {
                case '"': sb.append('"'); break;
                case '\\': sb.append('\\'); break;
                case '/': sb.append('/'); break;
                case 'b': sb.append('\b'); break;
                case 'f': sb.append('\f'); break;
                case 'n': sb.append('\n'); break;
                case 'r': sb.append('\r'); break;
                case 't': sb.append('\t'); break;
                case 'u': {
                    if (pos + 4 > text.length()) {
                        throw new IllegalArgumentException("JSON \\u 转义不完整");
                    }
                    String hex = text.substring(pos, pos + 4);
                    pos += 4;
                    sb.append((char) Integer.parseInt(hex, 16));
                    break;
                }
                default:
                    throw new IllegalArgumentException("JSON 未知转义 \\" + esc);
            }
        }
        throw new IllegalArgumentException("JSON 字符串未闭合");
    }

    private Double parseNumber() {
        int start = pos;
        if (peek() == '-') {
            pos++;
        }
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if ((c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                pos++;
            } else {
                break;
            }
        }
        if (start == pos) {
            throw new IllegalArgumentException("JSON 数值格式错误，位置 " + pos);
        }
        return Double.valueOf(text.substring(start, pos));
    }

    private char peek() {
        if (pos >= text.length()) {
            throw new IllegalArgumentException("JSON 提前结束");
        }
        return text.charAt(pos);
    }

    private void expect(String literal) {
        if (!text.startsWith(literal, pos)) {
            throw new IllegalArgumentException("JSON 期望 " + literal + "，位置 " + pos);
        }
        pos += literal.length();
    }

    /** 从对象里安全取字符串 */
    public static String str(Object node, String key, String fallback) {
        if (!(node instanceof Map)) {
            return fallback;
        }
        Object value = ((Map<?, ?>) node).get(key);
        return value instanceof String ? (String) value : fallback;
    }

    /** 从对象里安全取数字 */
    public static double num(Object node, String key, double fallback) {
        if (!(node instanceof Map)) {
            return fallback;
        }
        Object value = ((Map<?, ?>) node).get(key);
        return value instanceof Number ? ((Number) value).doubleValue() : fallback;
    }

    /** 从对象里安全取子对象 */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Object node, String key) {
        if (!(node instanceof Map)) {
            return null;
        }
        Object value = ((Map<?, ?>) node).get(key);
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> list(Object node, String key) {
        if (!(node instanceof Map)) {
            return null;
        }
        Object value = ((Map<?, ?>) node).get(key);
        return value instanceof List ? (List<Object>) value : null;
    }
}
