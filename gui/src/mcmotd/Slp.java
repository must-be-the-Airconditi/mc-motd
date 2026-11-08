package mcmotd;

import javax.naming.Context;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Hashtable;
import java.util.Map;

/**
 * Minecraft Java Edition Server List Ping 客户端。
 * 现代协议（1.7+）、旧版协议（1.6-）、SRV 解析、HTTP/SOCKS 代理。
 */
public final class Slp {

    public static final int DEFAULT_PORT = 25565;
    public static final int PROTOCOL_VERSION = 769;
    private static final int MAX_PACKET = 2 * 1024 * 1024;

    private Slp() {
    }

    /** 查询结果 */
    public static final class Ping {
        public String query;
        public String host;
        public int port;
        public Srv srv;
        public double latencyMs;
        public boolean legacy;
        public String rawJson;
        public Map<String, Object> status;
    }

    /** SRV 记录 */
    public static final class Srv {
        public final String host;
        public final int port;

        Srv(String host, int port) {
            this.host = host;
            this.port = port;
        }
    }

    /** 地址：主机 + 可选端口 */
    public static final class Address {
        public final String host;
        public final Integer port;

        Address(String host, Integer port) {
            this.host = host;
            this.port = port;
        }
    }

    /**
     * 查询服务器。
     *
     * @param address   形如 play.example.com / play.example.com:25565 / [::1]:25565
     * @param timeoutMs 连接与读取超时
     * @param proxyUrl  可选代理，支持 http:// 和 socks5://
     */
    public static Ping ping(String address, int timeoutMs, String proxyUrl) throws IOException {
        Address parsed = parseAddress(address);
        String host = parsed.host;
        int port = parsed.port == null ? DEFAULT_PORT : parsed.port;
        Srv srv = null;

        if (parsed.port == null && !isIp(host)) {
            srv = lookupSrv(host);
            if (srv != null) {
                host = srv.host;
                port = srv.port;
            }
        }

        ProxyConfig proxy = proxyUrl == null || proxyUrl.trim().isEmpty() ? null : parseProxy(proxyUrl);

        Ping result;
        try {
            result = pingModern(parsed.host, host, port, timeoutMs, proxy);
        } catch (IOException modernError) {
            try {
                result = pingLegacy(parsed.host, host, port, timeoutMs, proxy);
            } catch (IOException legacyError) {
                modernError.addSuppressed(legacyError);
                throw modernError;
            }
        }

        result.query = address;
        result.srv = srv;
        return result;
    }

    /* ------------------------------------------------------------ 地址与代理 */

    public static Address parseAddress(String input) {
        if (input == null) {
            throw new IllegalArgumentException("地址不能为空");
        }
        String raw = input.trim();
        if (raw.isEmpty()) {
            throw new IllegalArgumentException("地址不能为空");
        }
        raw = raw.replaceFirst("(?i)^minecraft://", "");
        while (raw.endsWith("/")) {
            raw = raw.substring(0, raw.length() - 1);
        }

        String host;
        Integer port = null;

        if (raw.startsWith("[")) {
            int end = raw.indexOf(']');
            if (end < 0) {
                throw new IllegalArgumentException("IPv6 地址缺少 ]");
            }
            host = raw.substring(1, end);
            String rest = raw.substring(end + 1);
            if (rest.startsWith(":")) {
                port = parsePort(rest.substring(1));
            } else if (!rest.isEmpty()) {
                throw new IllegalArgumentException("地址格式不正确");
            }
        } else {
            int colons = 0;
            for (int i = 0; i < raw.length(); i++) {
                if (raw.charAt(i) == ':') colons++;
            }
            if (colons > 1) {
                host = raw;
            } else if (colons == 1) {
                int idx = raw.lastIndexOf(':');
                host = raw.substring(0, idx);
                port = parsePort(raw.substring(idx + 1));
            } else {
                host = raw;
            }
        }

        if (host.isEmpty()) {
            throw new IllegalArgumentException("缺少主机名");
        }
        if (host.length() > 253) {
            throw new IllegalArgumentException("主机名过长");
        }
        if (!isIp(host) && !host.matches("[a-zA-Z0-9._-]+")) {
            throw new IllegalArgumentException("主机名包含非法字符");
        }
        return new Address(host.toLowerCase(), port);
    }

    private static int parsePort(String text) {
        try {
            int port = Integer.parseInt(text.trim());
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("端口超出范围 (1-65535)");
            }
            return port;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("端口必须是数字");
        }
    }

    public static boolean isIp(String host) {
        if (host.indexOf(':') >= 0) {
            return true;
        }
        return host.matches("\\d{1,3}(\\.\\d{1,3}){3}");
    }

    private static final class ProxyConfig {
        final String type;
        final String host;
        final int port;

        ProxyConfig(String type, String host, int port) {
            this.type = type;
            this.host = host;
            this.port = port;
        }
    }

    private static ProxyConfig parseProxy(String url) {
        String raw = url.trim();
        if (!raw.contains("://")) {
            raw = "socks5://" + raw;
        }
        int schemeEnd = raw.indexOf("://");
        String scheme = raw.substring(0, schemeEnd).toLowerCase();
        String rest = raw.substring(schemeEnd + 3);
        int at = rest.lastIndexOf('@');
        if (at >= 0) {
            rest = rest.substring(at + 1); // 暂不支持认证
        }
        int colon = rest.lastIndexOf(':');
        if (colon < 0) {
            throw new IllegalArgumentException("代理地址缺少端口");
        }
        String host = rest.substring(0, colon);
        int port;
        try {
            port = Integer.parseInt(rest.substring(colon + 1));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("代理端口不正确");
        }
        if (!scheme.equals("socks5") && !scheme.equals("socks5h") && !scheme.equals("http")) {
            throw new IllegalArgumentException("不支持的代理类型：" + scheme);
        }
        return new ProxyConfig(scheme, host, port);
    }

    /* --------------------------------------------------------------- SRV */

    static Srv lookupSrv(String host) {
        DirContext ctx = null;
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
            env.put("com.sun.jndi.dns.timeout.initial", "2000");
            env.put("com.sun.jndi.dns.timeout.retries", "1");
            ctx = new InitialDirContext(env);
            Attributes attrs = ctx.getAttributes("_minecraft._tcp." + host);
            Attribute srv = attrs.get("SRV");
            if (srv == null || srv.size() == 0) {
                return null;
            }
            int bestPriority = Integer.MAX_VALUE;
            Srv best = null;
            for (int i = 0; i < srv.size(); i++) {
                String[] parts = String.valueOf(srv.get(i)).trim().split("\\s+");
                if (parts.length < 4) {
                    continue;
                }
                int priority = Integer.parseInt(parts[0]);
                int port = Integer.parseInt(parts[2]);
                String target = parts[3];
                if (target.endsWith(".")) {
                    target = target.substring(0, target.length() - 1);
                }
                if (priority < bestPriority) {
                    bestPriority = priority;
                    best = new Srv(target, port);
                }
            }
            return best;
        } catch (Exception e) {
            return null;
        } finally {
            if (ctx != null) {
                try {
                    ctx.close();
                } catch (Exception ignored) {
                    // 忽略
                }
            }
        }
    }

    /* ------------------------------------------------------------ 连接 */

    private static Socket connect(String host, int port, int timeoutMs, ProxyConfig proxy) throws IOException {
        Socket socket = new Socket();
        try {
            if (proxy != null && proxy.type.startsWith("socks")) {
                Proxy javaProxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(proxy.host, proxy.port));
                Socket socks = new Socket(javaProxy);
                socks.connect(new InetSocketAddress(host, port), timeoutMs);
                socket = socks;
            } else if (proxy != null) {
                Socket tunnel = new Socket();
                tunnel.connect(new InetSocketAddress(proxy.host, proxy.port), timeoutMs);
                tunnel.setSoTimeout(timeoutMs);
                OutputStream out = tunnel.getOutputStream();
                String request = "CONNECT " + host + ":" + port + " HTTP/1.1\r\n"
                        + "Host: " + host + ":" + port + "\r\n"
                        + "Proxy-Connection: Keep-Alive\r\n\r\n";
                out.write(request.getBytes(StandardCharsets.ISO_8859_1));
                out.flush();
                String status = readHttpHeader(tunnel.getInputStream());
                if (!status.contains(" 200 ")) {
                    tunnel.close();
                    throw new IOException("HTTP 代理拒绝 CONNECT：" + status);
                }
                socket = tunnel;
            } else {
                socket.connect(new InetSocketAddress(host, port), timeoutMs);
            }
            socket.setSoTimeout(timeoutMs);
            socket.setTcpNoDelay(true);
            return socket;
        } catch (IOException e) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // 忽略
            }
            throw new IOException(friendly(e, host, port), e);
        }
    }

    private static String readHttpHeader(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int state = 0;
        while (buf.size() < 16 * 1024) {
            int b = in.read();
            if (b < 0) {
                break;
            }
            buf.write(b);
            if ((state == 0 || state == 2) && b == '\r') {
                state++;
            } else if (state == 1 && b == '\n') {
                state = 2;
            } else if (state == 2 && b == '\r') {
                state = 3;
            } else if (state == 3 && b == '\n') {
                break;
            } else {
                state = 0;
            }
        }
        String header = buf.toString(StandardCharsets.ISO_8859_1.name());
        int lineEnd = header.indexOf("\r\n");
        return lineEnd > 0 ? header.substring(0, lineEnd) : header;
    }

    private static String friendly(IOException e, String host, int port) {
        if (e instanceof ConnectException) {
            return "连接被拒绝：" + host + ":" + port + " 上没有服务在监听，或端口写错了";
        }
        if (e instanceof SocketTimeoutException) {
            return "连接超时：" + host + ":" + port;
        }
        if (e instanceof UnknownHostException) {
            return "找不到主机：" + host + "（DNS 解析失败）";
        }
        return e.getMessage() == null ? e.toString() : e.getMessage();
    }

    /* ---------------------------------------------------------- 现代协议 */

    private static Ping pingModern(String handshakeHost, String host, int port, int timeoutMs, ProxyConfig proxy)
            throws IOException {
        Socket socket = connect(host, port, timeoutMs, proxy);
        try {
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            ByteArrayOutputStream payload = new ByteArrayOutputStream();
            writeVarInt(payload, 0x00);
            writeVarInt(payload, PROTOCOL_VERSION);
            writeString(payload, handshakeHost);
            payload.write((port >> 8) & 0xFF);
            payload.write(port & 0xFF);
            writeVarInt(payload, 1);

            long started = System.nanoTime();
            writeFrame(out, payload.toByteArray());
            writeFrame(out, new byte[]{0x00});

            int length = readVarInt(in);
            if (length <= 0 || length > MAX_PACKET) {
                throw new IOException("服务器返回的数据长度异常");
            }
            byte[] packet = readFully(in, length);

            ByteArrayReader reader = new ByteArrayReader(packet);
            int packetId = reader.readVarInt();
            if (packetId != 0x00) {
                throw new IOException("状态响应格式不正确");
            }
            String json = reader.readString();
            double latencyMs = (System.nanoTime() - started) / 1e6;

            Ping ping = new Ping();
            ping.host = host;
            ping.port = port;
            ping.latencyMs = latencyMs;
            ping.rawJson = json;
            ping.status = parseStatus(json);
            return ping;
        } finally {
            closeQuietly(socket);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseStatus(String json) throws IOException {
        Object parsed;
        try {
            parsed = Json.parse(json);
        } catch (RuntimeException e) {
            throw new IOException("服务器返回的 JSON 无法解析");
        }
        if (!(parsed instanceof Map)) {
            throw new IOException("状态数据不是 JSON 对象");
        }
        return (Map<String, Object>) parsed;
    }

    /* ---------------------------------------------------------- 旧版协议 */

    private static Ping pingLegacy(String handshakeHost, String host, int port, int timeoutMs, ProxyConfig proxy)
            throws IOException {
        Socket socket = connect(host, port, timeoutMs, proxy);
        try {
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            long started = System.nanoTime();
            out.write(new byte[]{(byte) 0xFE, 0x01});
            out.flush();

            int first = in.read();
            if (first != 0xFF) {
                throw new IOException("旧版 Ping 响应格式不正确");
            }
            int chars = (in.read() << 8) | in.read();
            byte[] data = readFully(in, chars * 2);
            String text = new String(data, StandardCharsets.UTF_16BE);
            double latencyMs = (System.nanoTime() - started) / 1e6;

            String[] fields = text.split("\u0000", -1);
            String motd = fields.length > 3 ? fields[3] : "";
            int protocol = fields.length > 1 ? parseInt(fields[1]) : 0;
            String version = fields.length > 2 ? fields[2] : "";
            int online = fields.length > 4 ? parseInt(fields[4]) : 0;
            int max = fields.length > 5 ? parseInt(fields[5]) : 0;

            String legacyJson = "{\"version\":{\"name\":\"" + escape(version) + "\",\"protocol\":" + protocol
                    + "},\"players\":{\"online\":" + online + ",\"max\":" + max + "},"
                    + "\"description\":\"" + escape(motd) + "\"}";

            Ping ping = new Ping();
            ping.host = host;
            ping.port = port;
            ping.latencyMs = latencyMs;
            ping.legacy = true;
            ping.rawJson = legacyJson;
            ping.status = parseStatus(legacyJson);
            return ping;
        } finally {
            closeQuietly(socket);
        }
    }

    private static int parseInt(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String escape(String text) {
        StringBuilder sb = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    /* ------------------------------------------------------------ 编解码 */

    public static void writeVarInt(OutputStream out, int value) throws IOException {
        int v = value;
        do {
            int b = v & 0x7F;
            v >>>= 7;
            if (v != 0) {
                b |= 0x80;
            }
            out.write(b);
        } while (v != 0);
    }

    public static void writeString(OutputStream out, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    private static void writeFrame(OutputStream out, byte[] payload) throws IOException {
        writeVarInt(out, payload.length);
        out.write(payload);
        out.flush();
    }

    public static int readVarInt(InputStream in) throws IOException {
        int value = 0;
        int size = 0;
        int b;
        do {
            b = in.read();
            if (b < 0) {
                throw new EOFException("服务器关闭了连接");
            }
            value |= (b & 0x7F) << (7 * size);
            size++;
            if (size > 5) {
                throw new IOException("VarInt 过长（数据损坏）");
            }
        } while ((b & 0x80) != 0);
        return value;
    }

    private static byte[] readFully(InputStream in, int length) throws IOException {
        byte[] buffer = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = in.read(buffer, offset, length - offset);
            if (read < 0) {
                throw new EOFException("服务器关闭了连接");
            }
            offset += read;
        }
        return buffer;
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // 忽略
        }
    }

    /** 在 byte[] 上读 VarInt / 字符串 */
    public static final class ByteArrayReader {
        private final byte[] data;
        private int pos;

        public ByteArrayReader(byte[] data) {
            this.data = data;
        }

        public int readVarInt() throws IOException {
            int value = 0;
            int size = 0;
            int b;
            do {
                if (pos >= data.length) {
                    throw new EOFException("数据不完整");
                }
                b = data[pos++] & 0xFF;
                value |= (b & 0x7F) << (7 * size);
                size++;
                if (size > 5) {
                    throw new IOException("VarInt 过长");
                }
            } while ((b & 0x80) != 0);
            return value;
        }

        public String readString() throws IOException {
            int length = readVarInt();
            if (length < 0 || pos + length > data.length) {
                throw new IOException("字符串长度异常");
            }
            String text = new String(data, pos, length, StandardCharsets.UTF_8);
            pos += length;
            return text;
        }
    }
}
