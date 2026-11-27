package dev.mcmotd.app

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

data class PingResult(
    val query: String,
    val host: String,
    val port: Int,
    val latencyMs: Int,
    val online: Int,
    val maxPlayers: Int,
    val versionName: String,
    val protocol: Int,
    val descriptionJson: String?,
    val sample: List<String>,
    val rawJson: String,
    val bedrock: Boolean,
)

object Slp {
    /** "host" / "host:port" / "[::1]:25565" */
    fun parseAddress(input: String): Pair<String, Int> {
        val text = input.trim()
        if (text.isEmpty()) throw IOException("请输入服务器地址")
        if (text.startsWith("[")) {
            val end = text.indexOf(']')
            if (end < 0) throw IOException("IPv6 地址缺少 ]")
            val host = text.substring(1, end)
            val rest = text.substring(end + 1)
            val port = if (rest.startsWith(":")) rest.substring(1).toIntOrNull() ?: throw IOException("端口不是数字") else 25565
            return host to port
        }
        val parts = text.split(':')
        return when (parts.size) {
            1 -> parts[0] to 25565
            2 -> parts[0] to (parts[1].toIntOrNull() ?: throw IOException("端口不是数字"))
            else -> throw IOException("地址格式不对，IPv6 请用 [addr]:port")
        }
    }

    fun ping(input: String, timeoutMs: Int = 5000): PingResult {
        val (host, port) = parseAddress(input)
        val started = System.nanoTime()
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            socket.soTimeout = timeoutMs
            val out = DataOutputStream(socket.getOutputStream())
            val handshake = ByteArrayOutputStream()
            val h = DataOutputStream(handshake)
            writeVarInt(h, 0x00)
            writeVarInt(h, 769)
            writeString(h, host)
            h.writeShort(port)
            writeVarInt(h, 1)
            writeVarInt(out, handshake.size())
            out.write(handshake.toByteArray())
            out.flush()

            val request = ByteArrayOutputStream()
            DataOutputStream(request).let { r ->
                writeVarInt(r, 0x00)
            }
            writeVarInt(out, request.size())
            out.write(request.toByteArray())
            out.flush()

            val inStream = DataInputStream(socket.getInputStream())
            val length = readVarInt(inStream)
            if (length <= 0 || length > 2 * 1024 * 1024) throw IOException("响应长度异常：$length")
            val packet = ByteArray(length)
            inStream.readFully(packet)
            var offset = 0
            fun readVarIntAt(): Int {
                var value = 0
                var position = 0
                while (true) {
                    val byte = packet[offset++].toInt() and 0xFF
                    value = value or ((byte and 0x7F) shl position)
                    if (byte and 0x80 == 0) return value
                    position += 7
                    if (position >= 35) throw IOException("VarInt 过长")
                }
            }
            readVarIntAt() // packet id
            val jsonLength = readVarIntAt()
            if (offset + jsonLength > packet.size) throw IOException("JSON 长度越界")
            val jsonText = String(packet, offset, jsonLength, Charsets.UTF_8)
            val latency = ((System.nanoTime() - started) / 1_000_000).toInt()
            return parse(jsonText, input, host, port, latency)
        }
    }

    internal fun parse(
        jsonText: String,
        query: String,
        host: String,
        port: Int,
        latency: Int,
        bedrock: Boolean = false,
    ): PingResult {
        val root = JSONObject(jsonText)
        val version = root.optJSONObject("version")
        val players = root.optJSONObject("players")
        val description = root.opt("description")
        val sample = ArrayList<String>()
        players?.optJSONArray("sample")?.let { array ->
            for (i in 0 until array.length()) {
                val entry = array.optJSONObject(i) ?: continue
                entry.optString("name", "").takeIf { it.isNotEmpty() }?.let { sample.add(it) }
            }
        }
        return PingResult(
            query = query,
            host = host,
            port = port,
            latencyMs = latency,
            online = players?.optInt("online", 0) ?: 0,
            maxPlayers = players?.optInt("max", 0) ?: 0,
            versionName = version?.optString("name", "").orEmpty(),
            protocol = version?.optInt("protocol", 0) ?: 0,
            descriptionJson = description?.toString(),
            sample = sample,
            rawJson = jsonText,
            bedrock = bedrock,
        )
    }

    internal fun writeVarInt(out: DataOutputStream, value: Int) {
        var v = value
        while (true) {
            val temp = v and 0x7F
            v = v ushr 7
            if (v != 0) out.write(temp or 0x80) else {
                out.write(temp)
                return
            }
        }
    }

    internal fun readVarInt(input: DataInputStream): Int {
        var value = 0
        var position = 0
        while (true) {
            val byte = input.readUnsignedByte()
            value = value or ((byte and 0x7F) shl position)
            if (byte and 0x80 == 0) return value
            position += 7
            if (position >= 35) throw IOException("VarInt 过长")
        }
    }

    internal fun writeString(out: DataOutputStream, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        writeVarInt(out, bytes.size)
        out.write(bytes)
    }
}
