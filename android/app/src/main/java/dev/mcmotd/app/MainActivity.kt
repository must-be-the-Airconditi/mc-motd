package dev.mcmotd.app

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("mc-motd", Context.MODE_PRIVATE) }

    private lateinit var addressInput: TextInputEditText
    private lateinit var editionGroup: MaterialButtonToggleGroup
    private lateinit var pingButton: MaterialButton
    private lateinit var progress: LinearProgressIndicator
    private lateinit var output: LinearLayout
    private lateinit var themeButton: MaterialButton
    private lateinit var langButton: MaterialButton

    private enum class Mode(val key: String) {
        SYSTEM("system"), LIGHT("light"), DARK("dark"), MOCHA("mocha"), LATTE("latte")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val mode = Mode.entries.firstOrNull { it.key == prefs.getString("theme", "system") } ?: Mode.SYSTEM
        when (mode) {
            Mode.SYSTEM -> setTheme(R.style.Theme_McMotd)
            Mode.LIGHT -> { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO); setTheme(R.style.Theme_McMotd) }
            Mode.DARK -> { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES); setTheme(R.style.Theme_McMotd) }
            Mode.MOCHA -> { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO); setTheme(R.style.Theme_McMotd_Mocha) }
            Mode.LATTE -> { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO); setTheme(R.style.Theme_McMotd_Latte) }
        }
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        showEmpty()
    }

    private fun isDark(): Boolean {
        val mode = prefs.getString("theme", "system")
        return when (mode) {
            "dark", "mocha" -> true
            "light", "latte" -> false
            else -> (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
    }

    private fun schemeColor(attr: Int): Int = MaterialColors.getColor(window.decorView, attr)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(schemeColor(com.google.android.material.R.attr.colorSurface))
        }

        val toolbar = MaterialToolbar(this).apply {
            title = getString(R.string.app_name)
            setTitleTextColor(schemeColor(com.google.android.material.R.attr.colorOnSurface))
            subtitle = "Minecraft · Server List Ping"
            setSubtitleTextColor(schemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
        }
        themeButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = themeLabel()
            textSize = 12f
            minWidth = 0
            insetTop = 0
            insetBottom = 0
            setOnClickListener { cycleTheme() }
        }
        langButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = if (isEnglish()) "中文" else "EN"
            textSize = 12f
            minWidth = 0
            insetTop = 0
            insetBottom = 0
            setOnClickListener { toggleLanguage() }
        }
        toolbar.addView(themeButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(8) })
        toolbar.addView(langButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(toolbar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val form = MaterialCardView(this).apply {
            radius = dp(20).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            strokeColor = schemeColor(com.google.android.material.R.attr.colorOutlineVariant)
            setCardBackgroundColor(schemeColor(com.google.android.material.R.attr.colorSurface))
        }
        val formInner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        form.addView(formInner)

        val addressLayout = TextInputLayout(this).apply {
            hint = getString(R.string.address_hint)
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            setBoxCornerRadii(dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat())
        }
        addressInput = TextInputEditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
            textSize = 15f
        }
        addressLayout.addView(addressInput)
        formInner.addView(addressLayout)

        editionGroup = MaterialButtonToggleGroup(this).apply {
            setSingleSelection(true)
            setSelectionRequired(true)
            setPadding(0, dp(12), 0, 0)
        }
        val javaBtn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply { text = getString(R.string.edition_java); textSize = 13f }
        val bedrockBtn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply { text = getString(R.string.edition_bedrock); textSize = 13f }
        editionGroup.addView(javaBtn)
        editionGroup.addView(bedrockBtn)
        editionGroup.check(if (prefs.getBoolean("bedrock", false)) bedrockBtn.id else javaBtn.id)
        formInner.addView(editionGroup)

        pingButton = MaterialButton(this).apply {
            text = getString(R.string.ping)
            textSize = 15f
            setOnClickListener { startPing() }
        }
        formInner.addView(pingButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })

        progress = LinearProgressIndicator(this).apply {
            isIndeterminate = true
            visibility = View.GONE
            setPadding(0, dp(8), 0, 0)
        }
        formInner.addView(progress)
        content.addView(form, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        output = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(16), 0, 0) }
        content.addView(output)

        addressInput.setText(prefs.getString("address", ""))
        return root
    }

    private fun themeLabel(): String {
        val labelsZh = mapOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色", "mocha" to "Mocha", "latte" to "Latte")
        val labelsEn = mapOf("system" to "System", "light" to "Light", "dark" to "Dark", "mocha" to "Mocha", "latte" to "Latte")
        val key = prefs.getString("theme", "system")
        return (if (isEnglish()) labelsEn else labelsZh)[key] ?: key!!
    }

    private fun cycleTheme() {
        val order = listOf("system", "light", "dark", "mocha", "latte")
        val next = order[(order.indexOf(prefs.getString("theme", "system")).coerceAtLeast(0) + 1) % order.size]
        prefs.edit { putString("theme", next) }
        recreate()
    }

    private fun isEnglish(): Boolean {
        val tag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        if (tag.isNotEmpty()) return !tag.startsWith("zh")
        return resources.configuration.locales[0].language != "zh"
    }

    private fun toggleLanguage() {
        val target = if (isEnglish()) "zh-CN" else "en"
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(target))
        recreate()
    }

    private fun startPing() {
        val address = addressInput.text?.toString()?.trim().orEmpty()
        if (address.isEmpty()) {
            showError(getString(R.string.error_address))
            return
        }
        prefs.edit {
            putString("address", address)
            putBoolean("bedrock", editionGroup.checkedButtonId != editionGroup.getChildAt(0).id)
        }
        val bedrock = prefs.getBoolean("bedrock", false)
        progress.visibility = View.VISIBLE
        pingButton.isEnabled = false
        output.removeAllViews()
        output.addView(label(getString(R.string.pinging), true))
        thread {
            val result = runCatching {
                if (bedrock) Bedrock.ping(address) else Slp.ping(address)
            }
            runOnUiThread {
                progress.visibility = View.GONE
                pingButton.isEnabled = true
                result.onSuccess { showResult(it) }
                    .onFailure { showError(it.message ?: it.javaClass.simpleName) }
            }
        }
    }

    private fun label(value: CharSequence, muted: Boolean = false, size: Float = 14f): AppCompatTextView =
        AppCompatTextView(this).apply {
            text = value
            textSize = size
            setTextColor(schemeColor(if (muted) com.google.android.material.R.attr.colorOnSurfaceVariant else com.google.android.material.R.attr.colorOnSurface))
        }

    private fun card(): MaterialCardView = MaterialCardView(this).apply {
        radius = dp(16).toFloat()
        cardElevation = 0f
        strokeWidth = dp(1)
        strokeColor = schemeColor(com.google.android.material.R.attr.colorOutlineVariant)
        setCardBackgroundColor(schemeColor(com.google.android.material.R.attr.colorSurface))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) }
    }

    private fun showEmpty() {
        output.removeAllViews()
        val c = card()
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(24))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        body.addView(label(getString(R.string.empty_title), size = 17f).apply { gravity = Gravity.CENTER })
        body.addView(label(getString(R.string.empty_sub), muted = true, size = 13f).apply {
            setPadding(0, dp(6), 0, dp(10)); gravity = Gravity.CENTER
        })
        val examples = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        listOf("play.cubecraft.net", "mc.hypixel.net", "2b2t.org").forEach { host ->
            examples.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = host
                textSize = 12f
                minWidth = 0
                setOnClickListener { addressInput.setText(host); startPing() }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        body.addView(examples)
        c.addView(body)
        output.addView(c)
    }

    private fun showError(message: String) {
        output.removeAllViews()
        val c = card()
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        body.addView(label(message, size = 15f))
        body.addView(label(getString(R.string.exception_hint), muted = true, size = 13f).apply { setPadding(0, dp(6), 0, 0) })
        c.addView(body)
        output.addView(c)
    }

    private fun showResult(result: PingResult) {
        output.removeAllViews()
        val segments = Motd.segments(result.descriptionJson)

        val server = card()
        val serverBody = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14)) }
        val motd = TextView(this).apply {
            text = Motd.spannable(segments, light = !isDark())
            textSize = 17f
            setLineSpacing(dp(2).toFloat(), 1f)
        }
        serverBody.addView(motd)
        serverBody.addView(label(
            "${result.online}/${result.maxPlayers}  ·  ${result.versionName.ifEmpty { "?" }}  ·  ${result.latencyMs} ms" +
                if (result.bedrock) "  ·  RakNet" else "",
            muted = true, size = 13f,
        ).apply { setPadding(0, dp(8), 0, 0) })
        server.addView(serverBody)
        output.addView(server)

        val facts = card()
        val factsBody = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14)) }
        factsBody.addView(label(getString(R.string.players), muted = true, size = 12f))
        factsBody.addView(label("${result.online} / ${result.maxPlayers}").apply { setPadding(0, dp(4), 0, dp(10)) })
        factsBody.addView(label(getString(R.string.version), muted = true, size = 12f))
        factsBody.addView(label(result.versionName.ifEmpty { "?" }).apply { setPadding(0, dp(4), 0, dp(10)) })
        factsBody.addView(label(getString(R.string.protocol), muted = true, size = 12f))
        factsBody.addView(label(result.protocol.toString()).apply { setPadding(0, dp(4), 0, dp(10)) })
        factsBody.addView(label(getString(R.string.latency), muted = true, size = 12f))
        factsBody.addView(label("${result.latencyMs} ms").apply { setPadding(0, dp(4), 0, 0) })
        factsBody.addView(label("${result.host}:${result.port}", muted = true, size = 12f).apply { setPadding(0, dp(10), 0, 0) })
        facts.addView(factsBody)
        output.addView(facts)

        if (result.sample.isNotEmpty()) {
            val players = card()
            val playersBody = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14)) }
            playersBody.addView(label(getString(R.string.players), muted = true, size = 12f))
            playersBody.addView(label(result.sample.joinToString("  ·  ")).apply { setPadding(0, dp(8), 0, 0) })
            players.addView(playersBody)
            output.addView(players)
        }
    }
}
