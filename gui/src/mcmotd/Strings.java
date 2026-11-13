package mcmotd;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.prefs.Preferences;

/**
 * 极简国际化：中文 / English。
 * 优先级：-Dmcmotd.lang / MC_MOTD_LANG > 上次手动选择 > 系统语言。
 */
public final class Strings {

    public enum Lang { ZH, EN }

    private static final Map<String, String[]> TABLE = new HashMap<>();
    private static Lang current = Lang.ZH;

    private Strings() {
    }

    private static void add(String key, String zh, String en) {
        TABLE.put(key, new String[]{zh, en});
    }

    static {
        add("window.title", "MOTD 查看器 · Minecraft", "MOTD Viewer · Minecraft");
        add("app.title", "MOTD 查看器", "MOTD Viewer");

        add("proxy.label", "代理", "Proxy");
        add("proxy.hint", "socks5:// 或 http://", "socks5:// or http://");
        add("footer", "纯 Java，零依赖  ·  CLI 同源：motd <地址>",
                "Pure Java, zero dependencies  ·  CLI: motd <address>");

        add("empty.title", "输入服务器地址，直接读取它的 MOTD",
                "Enter a server address to read its MOTD");
        add("empty.sub", "服务端直连 TCP 25565  ·  自动 SRV 解析  ·  不经过第三方 API",
                "Direct TCP 25565  ·  automatic SRV  ·  no third-party API");

        add("loading", "正在连接 %s …", "Connecting to %s …");
        add("error.empty.title", "查询失败", "Query failed");
        add("error.title", "连不上 %s", "Can't reach %s");
        add("error.hint1", "检查地址和端口（默认 25565）· 群组服可能只开转发端口",
                "Check the address and port (default 25565) · a proxy network may only expose a forwarding port");
        add("error.hint2", "连不上海外服时在下面填代理，比如 http://127.0.0.1:31181",
                "For blocked overseas servers, fill in a proxy below, e.g. http://127.0.0.1:31181");
        add("error.hint3", "只支持 Java 版，基岩版是 UDP 19132",
                "Java Edition only; Bedrock uses UDP 19132");
        add("input.empty", "请输入服务器地址", "Please enter a server address");

        add("motd.empty", "（服务器没有设置 MOTD）", "(no MOTD set)");

        add("facts.title", "服务器信息", "Server info");
        add("facts.srv", "SRV 解析", "SRV lookup");
        add("fact.target", "目标", "Target");
        add("fact.version", "版本", "Version");
        add("fact.players", "在线玩家", "Players");
        add("fact.latency", "延迟", "Latency");
        add("fact.protocol", "协议", "Protocol");
        add("fact.secure", "安全聊天", "Secure chat");
        add("ping.modern", "现代 Ping", "Modern ping");
        add("ping.bedrock", "基岩版 RakNet", "Bedrock RakNet");
        add("fact.na", "不适用", "N/A");
        add("ping.legacy", "旧版 Ping", "Legacy ping");
        add("secure.on", "强制开启", "Enforced");
        add("secure.off", "未强制", "Not enforced");

        add("players.title", "玩家列表", "Players");
        add("players.count", "可见 %d 人", "%d visible");

        add("raw.title", "原始状态 JSON", "Raw status JSON");
        add("raw.badge", "服务器返回", "from server");
        add("raw.copy", "复制 JSON", "Copy JSON");
        add("raw.copied", "已复制", "Copied");

        add("history.label", "最近", "Recent");
    }

    public static void init(Preferences prefs) {
        String forced = System.getProperty("mcmotd.lang");
        if (forced == null || forced.trim().isEmpty()) {
            forced = System.getenv("MC_MOTD_LANG");
        }
        if (forced != null && !forced.trim().isEmpty()) {
            current = forced.trim().toLowerCase(Locale.ROOT).startsWith("zh") ? Lang.ZH : Lang.EN;
            return;
        }
        String saved = prefs == null ? "" : prefs.get("lang", "");
        if ("zh".equals(saved) || "en".equals(saved)) {
            current = "en".equals(saved) ? Lang.EN : Lang.ZH;
            return;
        }
        current = "zh".equalsIgnoreCase(Locale.getDefault().getLanguage()) ? Lang.ZH : Lang.EN;
    }

    public static void setLang(Lang lang) {
        current = lang == null ? Lang.ZH : lang;
    }

    public static void toggle() {
        current = current == Lang.ZH ? Lang.EN : Lang.ZH;
    }

    public static Lang lang() {
        return current;
    }

    public static boolean isEnglish() {
        return current == Lang.EN;
    }

    public static String t(String key) {
        String[] pair = TABLE.get(key);
        if (pair == null) {
            return key;
        }
        return current == Lang.EN ? pair[1] : pair[0];
    }

    /** 带参数的文案，例如 正在连接 %s … */
    public static String f(String key, Object... args) {
        String pattern = t(key);
        try {
            return String.format(pattern, args);
        } catch (RuntimeException e) {
            return pattern;
        }
    }
}
