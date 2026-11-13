package mcmotd;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/** 把协议层结果整理成界面好用的结构 */
public final class Info {

    public String query = "";
    public String host = "";
    public int port;
    public Slp.Srv srv;
    public double latencyMs;
    public boolean legacy;
    public String edition = "java";

    public String versionName = "未知";
    public Integer protocol;
    public int online;
    public int max;
    public final List<String> sample = new ArrayList<>();

    public Motd.Parsed motd;
    public String faviconDataUri;
    public byte[] faviconPng;
    public String rawJson = "{}";
    public boolean secureChat;
    public boolean previewsChat;

    public static Info of(Slp.Ping ping) {
        Info info = new Info();
        info.query = ping.query;
        info.host = ping.host;
        info.port = ping.port;
        info.srv = ping.srv;
        info.latencyMs = ping.latencyMs;
        info.legacy = ping.legacy;
        info.edition = ping.bedrock ? "bedrock" : "java";
        info.rawJson = ping.rawJson;

        Map<String, Object> status = ping.status;
        Map<String, Object> version = Json.obj(status, "version");
        if (version != null) {
            info.versionName = Motd.stripFormatting(Json.str(version, "name", "未知"));
            Object protocol = version.get("protocol");
            if (protocol instanceof Number) {
                info.protocol = ((Number) protocol).intValue();
            }
        }

        Map<String, Object> players = Json.obj(status, "players");
        if (players != null) {
            info.online = (int) Json.num(players, "online", 0);
            info.max = (int) Json.num(players, "max", 0);
            List<Object> list = Json.list(players, "sample");
            if (list != null) {
                for (Object entry : list) {
                    if (entry instanceof Map) {
                        String name = Json.str(entry, "name", "");
                        name = Motd.stripFormatting(name);
                        if (!name.isEmpty()) {
                            info.sample.add(name);
                        }
                    }
                }
            }
        }

        info.motd = Motd.parse(status.get("description"));
        info.secureChat = Boolean.TRUE.equals(status.get("enforcesSecureChat"));
        info.previewsChat = Boolean.TRUE.equals(status.get("previewsChat"));

        Object favicon = status.get("favicon");
        if (favicon instanceof String) {
            info.setFavicon((String) favicon);
        }
        return info;
    }

    private void setFavicon(String value) {
        String raw = value.trim();
        if (raw.isEmpty()) {
            return;
        }
        faviconDataUri = raw.startsWith("data:image/") ? raw : "data:image/png;base64," + raw;
        int comma = faviconDataUri.indexOf(',');
        if (comma < 0) {
            return;
        }
        try {
            faviconPng = Base64.getDecoder().decode(faviconDataUri.substring(comma + 1).replaceAll("\\s", ""));
        } catch (IllegalArgumentException e) {
            faviconPng = null;
        }
    }

    public String target() {
        if (srv != null) {
            return srv.host + ":" + srv.port;
        }
        return host + ":" + port;
    }

    public String versionLine() {
        return versionName + (protocol != null ? " (" + protocol + ")" : "");
    }
}
