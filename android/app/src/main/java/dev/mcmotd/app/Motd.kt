package dev.mcmotd.app

import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import org.json.JSONArray
import org.json.JSONObject

data class Seg(
    val text: String,
    val color: String? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underlined: Boolean = false,
    val strikethrough: Boolean = false,
    val obfuscated: Boolean = false,
)

object Motd {
    private val COLORS = mapOf(
        "black" to 0x000000, "dark_blue" to 0x0000AA, "dark_green" to 0x00AA00, "dark_aqua" to 0x00AAAA,
        "dark_red" to 0xAA0000, "dark_purple" to 0xAA00AA, "gold" to 0xFFAA00, "gray" to 0xAAAAAA,
        "dark_gray" to 0x555555, "blue" to 0x5555FF, "green" to 0x55FF55, "aqua" to 0x55FFFF,
        "red" to 0xFF5555, "light_purple" to 0xFF55FF, "yellow" to 0xFFFF55, "white" to 0xFFFFFF,
    )

    fun segments(descriptionJson: String?): List<Seg> {
        if (descriptionJson.isNullOrBlank()) return emptyList()
        val root = runCatching { JSONObject(descriptionJson) }.getOrNull() ?: return listOf(Seg(strip(descriptionJson)))
        val out = ArrayList<Seg>()
        walk(root, null, false, false, false, false, out)
        return out
    }

    fun plain(descriptionJson: String?): String = segments(descriptionJson).joinToString("") { it.text }

    private fun walk(
        node: JSONObject?,
        parentColor: String?,
        bold: Boolean,
        italic: Boolean,
        underlined: Boolean,
        strikethrough: Boolean,
        out: MutableList<Seg>,
    ) {
        if (node == null) return
        val color = if (node.has("color")) node.optString("color", parentColor ?: "") else parentColor
        val b = if (node.has("bold")) node.optBoolean("bold") else bold
        val i = if (node.has("italic")) node.optBoolean("italic") else italic
        val u = if (node.has("underlined")) node.optBoolean("underlined") else underlined
        val s = if (node.has("strikethrough")) node.optBoolean("strikethrough") else strikethrough
        val text = when (val raw = node.opt("text")) {
            null -> ""
            is String -> raw
            else -> ""
        }
        if (text.isNotEmpty()) {
            append(out, text, color, b, i, u, s)
        }
        val extra = node.optJSONArray("extra")
        if (extra != null) {
            for (index in 0 until extra.length()) {
                val child = extra.optJSONObject(index) ?: continue
                walk(child, color, b, i, u, s, out)
            }
        }
    }

    private fun append(out: MutableList<Seg>, text: String, color: String?, bold: Boolean, italic: Boolean, underlined: Boolean, strikethrough: Boolean) {
        // 兼容 § 颜色码
        var current = color
        var buffer = StringBuilder()
        var index = 0
        while (index < text.length) {
            val ch = text[index]
            if (ch == '§' && index + 1 < text.length) {
                if (buffer.isNotEmpty()) {
                    out.add(Seg(buffer.toString(), current, bold, italic, underlined, strikethrough))
                    buffer = StringBuilder()
                }
                val code = text[index + 1].lowercaseChar()
                when (code) {
                    '0' -> current = "black"; '1' -> current = "dark_blue"; '2' -> current = "dark_green"
                    '3' -> current = "dark_aqua"; '4' -> current = "dark_red"; '5' -> current = "dark_purple"
                    '6' -> current = "gold"; '7' -> current = "gray"; '8' -> current = "dark_gray"
                    '9' -> current = "blue"; 'a' -> current = "green"; 'b' -> current = "aqua"
                    'c' -> current = "red"; 'd' -> current = "light_purple"; 'e' -> current = "yellow"
                    'f' -> current = "white"
                }
                index += 2
                continue
            }
            buffer.append(ch)
            index++
        }
        if (buffer.isNotEmpty()) out.add(Seg(buffer.toString(), current, bold, italic, underlined, strikethrough))
    }

    private fun strip(text: String): String = text.replace(Regex("§."), "")

    fun spannable(segments: List<Seg>, light: Boolean = false): CharSequence {
        val builder = SpannableStringBuilder()
        for (seg in segments) {
            if (seg.obfuscated) continue
            val start = builder.length
            builder.append(seg.text)
            val end = builder.length
            if (end <= start) continue
            val base = seg.color?.let { COLORS[it] } ?: 0xFFFFFF
            val color = if (light) readableOnLight(base) else base
            builder.setSpan(ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (seg.bold) builder.setSpan(StyleSpan(android.graphics.Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (seg.italic) builder.setSpan(StyleSpan(android.graphics.Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (seg.underlined) builder.setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (seg.strikethrough) builder.setSpan(StrikethroughSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return builder
    }

    private fun readableOnLight(rgb: Int): Int {
        val r = Color.red(rgb); val g = Color.green(rgb); val b = Color.blue(rgb)
        val luminance = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0
        if (luminance <= 0.62) return rgb
        val scale = 0.55 / luminance
        return Color.rgb((r * scale).toInt().coerceIn(0, 255), (g * scale).toInt().coerceIn(0, 255), (b * scale).toInt().coerceIn(0, 255))
    }
}
