package mcmotd;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Minecraft Bedrock Edition 服务器查询（RakNet Unconnected Ping）。 */
public final class Bedrock {

    public static final int DEFAULT_PORT = 19132;

    private static final byte[] MAGIC = {
        (byte) 0x00, (byte) 0xff, (byte) 0xff, (byte) 0x00,
        (byte) 0xfe, (byte) 0xfe, (byte) 0xfe, (byte) 0xfe,
        (byte) 0xfd, (byte) 0xfd, (byte) 0xfd, (byte) 0xfd,
        (byte) 0x12, (byte) 0x34, (byte) 0x56, (byte) 0x78,
    };

    private Bedrock() {
    }

    public static Slp.Ping ping(String address, int timeoutMs, String lang) throws IOException {
        Slp.Address parsed = Slp.parseAddress(address);
        String host = parsed.host;
        int port = parsed.port == null ? DEFAULT_PORT : parsed.port;

        byte[] ping = new byte[1 + 8 + 16 + 8];
        ping[0] = 0x01;
        ByteBuffer.wrap(ping, 1, 8).putLong(System.currentTimeMillis());
        System.arraycopy(MAGIC, 0, ping, 9, 16);
        for (int i = 25; i < 33; i++) {
            ping[i] = (byte) (Math.random() * 256);
        }

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(timeoutMs);
            InetAddress addr = InetAddress.getByName(host);
            socket.send(new DatagramPacket(ping, ping.length, addr, port));

            byte[] buf = new byte[2048];
            DatagramPacket response = new DatagramPacket(buf, buf.length);
            long started = System.nanoTime();
            socket.receive(response);
            double latencyMs = (System.nanoTime() - started) / 1e6;

            byte[] data = response.getData();
            int length = response.getLength();
            if (length < 35 || data[0] != 0x1c) {
                throw new IOException("基岩版响应格式不正确");
            }
            for (int i = 0; i < 16; i++) {
                if (data[17 + i] != MAGIC[i]) {
                    throw new IOException("基岩版响应 magic 不匹配");
                }
            }
            int strLen = ((data[33] & 0xff) << 8) | (data[34] & 0xff);
            int end = Math.min(length, 35 + strLen);
            String text = new String(data, 35, end - 35, StandardCharsets.UTF_8);

            String[] fields = text.split(";", -1);
            String line1 = fields.length > 1 ? fields[1] : "";
            String line2 = fields.length > 7 ? fields[7] : "";
            String description = line2.isEmpty() ? line1 : line1 + "\n" + line2;
            String versionName = fields.length > 3 && !fields[3].isEmpty() ? fields[3] : "Bedrock";
            int protocol = fields.length > 2 ? parseInt(fields[2]) : 0;
            int online = fields.length > 4 ? parseInt(fields[4]) : 0;
            int max = fields.length > 5 ? parseInt(fields[5]) : 0;

            Map<String, Object> version = new LinkedHashMap<>();
            version.put("name", versionName);
            version.put("protocol", protocol);

            Map<String, Object> players = new LinkedHashMap<>();
            players.put("online", online);
            players.put("max", max);

            Map<String, Object> status = new LinkedHashMap<>();
            status.put("version", version);
            status.put("players", players);
            status.put("description", description);

            Slp.Ping result = new Slp.Ping();
            result.query = address;
            result.host = host;
            result.port = port;
            result.latencyMs = latencyMs;
            result.bedrock = true;
            result.status = status;
            result.rawJson = "{\"version\":{\"name\":\"" + escape(versionName)
                    + "\",\"protocol\":" + protocol + "},\"players\":{\"online\":" + online
                    + ",\"max\":" + max + "},\"description\":\"" + escape(description) + "\"}";
            return result;
        } catch (SocketTimeoutException e) {
            throw new IOException("基岩版服务器无响应：" + host + ":" + port);
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
}
