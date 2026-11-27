package dev.mcmotd.app

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

object Bedrock {
    private val MAGIC = byteArrayOf(
        0x00, 0xFF.toByte(), 0xFF.toByte(), 0x00, 0xFE.toByte(), 0xFE.toByte(), 0xFE.toByte(), 0xFE.toByte(),
        0xFD.toByte(), 0xFD.toByte(), 0xFD.toByte(), 0xFD.toByte(), 0x12, 0x34, 0x56, 0x78,
    )

    fun ping(input: String, timeoutMs: Int = 5000): PingResult {
        val (host, port) = Slp.parseAddress(input).let { (h, p) -> h to (if (p == 25565) 19132 else p) }
        val started = System.nanoTime()
        val time = System.currentTimeMillis()
        val packet = ByteArrayOutputStream()
        packet.write(0x01)
        packet.write(ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(time).array())
        packet.write(MAGIC)
        packet.write(ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(0x1234567890ABCDEFL).array())
        val payload = packet.toByteArray()

        DatagramSocket().use { socket ->
            socket.soTimeout = timeoutMs
            val address = InetAddress.getByName(host)
            socket.send(DatagramPacket(payload, payload.size, address, port))
            val buffer = ByteArray(4096)
            val response = DatagramPacket(buffer, buffer.size)
            socket.receive(response)
            val latency = ((System.nanoTime() - started) / 1_000_000).toInt()
            val data = response.data
            val length = response.length
            if (length < 35 || data[0] != 0x1C.toByte()) throw IOException("不是 RakNet Pong 响应")
            val stringLength = ((data[33].toInt() and 0xFF) shl 8) or (data[34].toInt() and 0xFF)
            if (35 + stringLength > length) throw IOException("Pong 字符串长度越界")
            val text = String(data, 35, stringLength, Charsets.UTF_8)
            return parsePong(text, input, host, port, latency)
        }
    }

    /** 形如 "MCPE;MOTD;协议;版本;在线;最大;服务器ID;副标题;游戏模式;…" */
    internal fun parsePong(text: String, query: String, host: String, port: Int, latency: Int): PingResult {
        val parts = text.split(';')
        val motd = listOfNotNull(parts.getOrNull(1), parts.getOrNull(7)).joinToString("\n")
        val online = parts.getOrNull(4)?.toIntOrNull() ?: 0
        val max = parts.getOrNull(5)?.toIntOrNull() ?: 0
        val version = parts.getOrNull(3).orEmpty()
        val protocol = parts.getOrNull(2)?.toIntOrNull() ?: 0
        val description = org.json.JSONObject().put("text", motd).toString()
        return PingResult(
            query = query,
            host = host,
            port = port,
            latencyMs = latency,
            online = online,
            maxPlayers = max,
            versionName = version,
            protocol = protocol,
            descriptionJson = description,
            sample = emptyList(),
            rawJson = text,
            bedrock = true,
        )
    }
}
