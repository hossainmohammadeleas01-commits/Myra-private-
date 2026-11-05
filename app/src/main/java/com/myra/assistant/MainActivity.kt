package com.myra.assistant

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.graphics.BitmapFactory
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.util.Linkify
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private fun withAlpha(c: Int, a: Int) = (c and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)

/** Animated orb (idle / listening / speaking / thinking) */
class OrbView(ctx: Context) : View(ctx) {
    var state = "idle"
    private var amp = 0f
    private var t = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val path = Path()

    private fun cols(): Pair<Int, Int> = when (state) {
        "listening" -> Color.parseColor("#FF1744") to Color.parseColor("#D500F9")
        "speaking" -> Color.parseColor("#E040FB") to Color.parseColor("#FF1744")
        "thinking" -> Color.parseColor("#40C4FF") to Color.parseColor("#00B0FF")
        else -> Color.parseColor("#B71C1C") to Color.parseColor("#880E4F")
    }

    override fun onDraw(c: Canvas) {
        t += 0.016f
        val target = when (state) {
            "speaking" -> 0.35f + 0.35f * abs(sin(t * 6f))
            "listening" -> MyraService.level
            else -> 0f
        }
        amp += (target - amp) * 0.25f
        val (c1, c2) = cols()
        val cx = width / 2f
        val cy = height / 2f
        val base = min(width, height) / 2f
        val act = state != "idle"
        val pulse = if (state == "idle") 1f + 0.075f * sin(t * 4.19f) else 1f + amp * 0.2f
        val r = base * 0.48f * pulse

        // glow
        p.reset(); p.isAntiAlias = true
        p.shader = RadialGradient(cx, cy, r * 1.6f, intArrayOf(withAlpha(c1, if (act) 140 else 90), withAlpha(c1, 0)), null, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r * 1.6f, p)
        // core
        p.reset(); p.isAntiAlias = true
        p.shader = RadialGradient(cx - r * 0.3f, cy - r * 0.3f, r * 1.3f, intArrayOf(c2, c1), null, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, p)
        // rings
        for (i in 0 until 3) {
            p.reset(); p.isAntiAlias = true
            p.style = Paint.Style.STROKE; p.strokeWidth = 3f
            p.color = withAlpha(c2, 140 - i * 30)
            p.pathEffect = DashPathEffect(floatArrayOf(28f, 20f), 0f)
            val rr = r * (1.2f + i * 0.14f)
            rect.set(cx - rr, cy - rr, cx + rr, cy + rr)
            c.save()
            val dir = if (i % 2 == 0) 1f else -1f
            c.rotate(Math.toDegrees((t * (if (act) 1f else 0.3f) * (0.6f + i * 0.35f) * dir).toDouble()).toFloat(), cx, cy)
            c.drawArc(rect, 0f, 290f, false, p)
            c.restore()
        }
        // wave ring
        if (act && state != "thinking") {
            p.reset(); p.isAntiAlias = true
            p.style = Paint.Style.STROKE; p.strokeWidth = 3f; p.color = withAlpha(c1, 180)
            path.reset()
            val spd = if (state == "speaking") 7f else 4f
            for (i in 0..120) {
                val a = i / 120f * 2f * PI.toFloat()
                val rr = r * 1.1f + sin(a * 6f + t * spd) * (6f + amp * 28f)
                val x = cx + cos(a) * rr
                val y = cy + sin(a) * rr
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            c.drawPath(path, p)
            // particles
            p.reset(); p.isAntiAlias = true; p.color = withAlpha(c2, 230)
            for (i in 0 until 12) {
                val a = t * (0.8f + amp) + i / 12f * 2f * PI.toFloat()
                val rr = r * 1.55f + sin(t * 2f + i) * 10f * (1f + amp * 2f)
                c.drawCircle(cx + cos(a) * rr, cy + sin(a) * rr, 4f + amp * 4f, p)
            }
        }
        // thinking arcs
        if (state == "thinking") {
            p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE; p.strokeWidth = 8f
            p.color = c1
            var rr = r * 1.3f
            rect.set(cx - rr, cy - rr, cx + rr, cy + rr)
            c.drawArc(rect, Math.toDegrees((t * 4f).toDouble()).toFloat(), 140f, false, p)
            p.color = c2
            rr = r * 1.45f
            rect.set(cx - rr, cy - rr, cx + rr, cy + rr)
            c.drawArc(rect, Math.toDegrees((-t * 3f).toDouble()).toFloat(), 110f, false, p)
        }
        // highlight
        p.reset(); p.isAntiAlias = true
        p.shader = RadialGradient(cx - r * 0.4f, cy - r * 0.4f, r * 0.6f, intArrayOf(withAlpha(Color.WHITE, 90), withAlpha(Color.WHITE, 0)), null, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, p)
        postInvalidateOnAnimation()
    }
}

/** 20-bar waveform */
class WaveView(ctx: Context) : View(ctx) {
    var active = false
    private val bars = FloatArray(20) { 0.05f }
    private var t = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        t += 0.016f
        val lvl = if (MyraService.speakingNow) 0.6f else MyraService.level
        val w = width / 20f
        for (i in 0 until 20) {
            val tg = if (active && lvl > 0.02f) 0.12f + lvl * 0.88f * (0.4f + 0.6f * abs(sin(t * 6f + i * 0.9f))) else 0.05f
            bars[i] += (tg - bars[i]) * 0.3f
            val h = (bars[i] * height).coerceAtLeast(4f)
            p.color = withAlpha(Color.parseColor("#FF1744"), (150 + bars[i] * 105).toInt())
            c.drawRoundRect(i * w + w * 0.2f, (height - h) / 2f, i * w + w * 0.8f, (height + h) / 2f, 4f, 4f, p)
        }
        postInvalidateOnAnimation()
    }
}

class MainActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var mainView: LinearLayout
    private lateinit var settingsView: ScrollView
    private lateinit var redFx: View
    private lateinit var orb: OrbView
    private lateinit var wave: WaveView
    private lateinit var status: TextView
    private lateinit var chatBox: LinearLayout
    private lateinit var chatScroll: ScrollView
    private lateinit var mic: TextView
    private lateinit var action: TextView
    private lateinit var input: EditText
    private lateinit var batt: TextView
    private lateinit var clock: TextView
    private lateinit var key: EditText
    private lateinit var name: EditText
    private lateinit var prime: EditText
    private lateinit var cc: EditText
    private lateinit var modelSp: Spinner
    private lateinit var voiceSp: Spinner
    private lateinit var persSp: Spinner

    private val handler = Handler(Looper.getMainLooper())
    private var tick = 0
    private var lastRed = -1f
    private var lastBubble = ""

    private val models = listOf(
        "models/gemini-2.5-flash-native-audio-preview-12-2025",
        "models/gemini-2.0-flash-live-001",
        "models/gemini-2.5-flash-preview-native-audio-dialog"
    )
    private val voices = listOf("Aoede", "Charon", "Kore", "Fenrir", "Puck", "Leda", "Orus", "Zephyr")
    private val pers = listOf("gf", "pro", "asst")
    private val persLabels = listOf("GF (Hinglish)", "Professional", "Assistant")

    private val RED = Color.parseColor("#FF1744")
    private val BG = Color.parseColor("#050505")

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(t: String) = TextView(this).apply {
        text = t; setTextColor(Color.parseColor("#888888")); textSize = 11f
        setPadding(0, dp(14), 0, dp(4))
    }

    private fun edit(hint: String, pwd: Boolean = false) = EditText(this).apply {
        this.hint = hint; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY)
        setBackgroundColor(Color.parseColor("#151515")); setPadding(dp(12), dp(10), dp(12), dp(10))
        if (pwd) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    }

    private fun spinner(items: List<String>) = Spinner(this).apply {
        adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, items)
        setBackgroundColor(Color.parseColor("#151515"))
    }

    private fun button(t: String, bg: Int = Color.parseColor("#1f1f1f"), click: () -> Unit) = Button(this).apply {
        text = t; setTextColor(Color.WHITE); setBackgroundColor(bg)
        setOnClickListener { click() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("myra", MODE_PRIVATE)
        window.statusBarColor = BG; window.navigationBarColor = BG

        val root = FrameLayout(this).apply { setBackgroundColor(BG) }
        redFx = View(this).apply { setBackgroundColor(RED); alpha = 0f }
        root.addView(redFx, FrameLayout.LayoutParams(-1, -1))

        buildMain()
        buildSettings()
        root.addView(mainView, FrameLayout.LayoutParams(-1, -1))
        root.addView(settingsView, FrameLayout.LayoutParams(-1, -1))
        settingsView.visibility = View.GONE
        setContentView(root)
    }

    // ------------------------------------------------------------- main screen
    private fun buildMain() {
        mainView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(34), dp(14), dp(14))
        }

        // top bar
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        batt = TextView(this).apply { setTextColor(RED); textSize = 12f; typeface = Typeface.MONOSPACE; text = "🔋 --" }
        val titleBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        titleBox.addView(TextView(this).apply {
            text = "MYRA"; setTextColor(RED); textSize = 22f; typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.2f; gravity = Gravity.CENTER
        })
        titleBox.addView(TextView(this).apply {
            text = "YOUR VOICE. YOUR COMMAND."; setTextColor(Color.parseColor("#666666")); textSize = 9f
            typeface = Typeface.MONOSPACE; gravity = Gravity.CENTER
        })
        val right = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END or Gravity.CENTER_VERTICAL }
        clock = TextView(this).apply { setTextColor(RED); textSize = 12f; typeface = Typeface.MONOSPACE }
        val gear = TextView(this).apply {
            text = "⚙"; textSize = 24f; setTextColor(Color.parseColor("#888888")); setPadding(dp(10), 0, 0, 0)
            setOnClickListener { showSettings(true) }
        }
        right.addView(clock); right.addView(gear)
        top.addView(batt, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(titleBox, LinearLayout.LayoutParams(0, -2, 1.6f))
        top.addView(right, LinearLayout.LayoutParams(0, -2, 1f))
        mainView.addView(top)

        // center
        val center = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        orb = OrbView(this)
        wave = WaveView(this)
        status = TextView(this).apply {
            text = "Tap karke bolo 💬"; setTextColor(Color.parseColor("#777777")); textSize = 13f
            gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0)
        }
        center.addView(orb, LinearLayout.LayoutParams(dp(260), dp(260)))
        center.addView(wave, LinearLayout.LayoutParams(dp(200), dp(40)))
        center.addView(status)
        mainView.addView(center, LinearLayout.LayoutParams(-1, 0, 1f))

        // chat
        chatBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        chatScroll = ScrollView(this).apply { addView(chatBox) }
        mainView.addView(chatScroll, LinearLayout.LayoutParams(-1, dp(190)))

        // input bar (Claude-style): + | text | mic | send/stop
        input = EditText(this).apply {
            hint = "Message MYRA or tap mic"
            setHintTextColor(Color.parseColor("#777777")); setTextColor(Color.WHITE)
            textSize = 15f; background = null; maxLines = 4
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { refreshAction() }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        val plus = circleBtn("+", "#232323") { pickImage() }
        mic = circleBtn("\uD83C\uDFA4", "#232323") { onMicTap() }
        action = circleBtn("\u25A0", "#FF1744") { onAction() }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(32).toFloat(); setColor(Color.parseColor("#141414"))
                setStroke(dp(1), Color.parseColor("#2a2a2a"))
            }
        }
        bar.addView(plus, LinearLayout.LayoutParams(dp(44), dp(44)))
        bar.addView(input, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(10); rightMargin = dp(6) })
        bar.addView(mic, LinearLayout.LayoutParams(dp(44), dp(44)))
        bar.addView(action, LinearLayout.LayoutParams(dp(44), dp(44)).apply { leftMargin = dp(6) })
        mainView.addView(bar, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        mainView.addView(TextView(this).apply {
            text = "\uD83C\uDFA4 voice / mute  \u2022  \u27A4 send  \u2022  \u25A0 stop  \u2022  + image"
            setTextColor(Color.parseColor("#666666")); textSize = 11f; gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
        })
    }

    private fun circleBtn(t: String, fill: String, click: () -> Unit) = TextView(this).apply {
        text = t; textSize = 20f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor(fill)) }
        setOnClickListener { click() }
    }

    private fun refreshAction() {
        val hasText = !input.text.isNullOrBlank()
        action.text = if (hasText) "\u27A4" else "\u25A0"
        action.alpha = if (hasText || MyraService.running) 1f else 0.35f
    }

    private fun onAction() {
        val t = input.text.toString().trim()
        if (t.isNotEmpty()) {
            input.setText("")
            if (MyraService.running) {
                startService(Intent(this, MyraService::class.java)
                    .setAction(MyraService.ACTION_TEXT).putExtra("text", t))
            } else {
                MyraService.pendingText = t
                onMicTap()
            }
            return
        }
        if (MyraService.running) {
            startService(Intent(this, MyraService::class.java).setAction(MyraService.ACTION_STOP))
        }
    }

    private fun pickImage() {
        if (!MyraService.running) {
            Toast.makeText(this, "আগে 🎤 চেপে MYRA চালু করো", Toast.LENGTH_SHORT).show(); return
        }
        startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), 7)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req != 7 || res != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
            contentResolver.openInputStream(uri)?.use {
                MyraService.pendingImage = BitmapFactory.decodeStream(it, null, opts)
            }
            startService(Intent(this, MyraService::class.java).setAction(MyraService.ACTION_IMAGE))
        } catch (e: Exception) {
            Toast.makeText(this, "Image পড়া যায়নি: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ------------------------------------------------------------- settings screen
    private fun buildSettings() {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(36), dp(18), dp(24)) }
        col.addView(TextView(this).apply {
            text = "SETTINGS"; setTextColor(RED); textSize = 22f; typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.15f
        })

        key = edit("Gemini API Key", true)
        name = edit("Your name")
        prime = edit("Prime contacts (প্রতি লাইনে Name:Number)").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
        }
        cc = edit("Country code, যেমন 880 (WhatsApp-এ 01.. নম্বরের জন্য)").apply {
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        modelSp = spinner(listOf("Native Audio (Human Voice)", "Flash Live (Fast)", "Pro Audio Dialog"))
        voiceSp = spinner(voices)
        persSp = spinner(persLabels)

        key.setText(prefs.getString("api_key", ""))
        name.setText(prefs.getString("name", ""))
        prime.setText(prefs.getString("prime", ""))
        cc.setText(prefs.getString("cc", ""))
        modelSp.setSelection(models.indexOf(prefs.getString("model", models[0])).coerceAtLeast(0))
        voiceSp.setSelection(voices.indexOf(prefs.getString("voice", "Aoede")).coerceAtLeast(0))
        persSp.setSelection(pers.indexOf(prefs.getString("personality", "gf")).coerceAtLeast(0))

        col.addView(label("API KEY")); col.addView(key)
        col.addView(label("YOUR NAME")); col.addView(name)
        col.addView(label("AI MODEL")); col.addView(modelSp)
        col.addView(label("VOICE")); col.addView(voiceSp)
        col.addView(label("PERSONALITY")); col.addView(persSp)
        col.addView(label("PRIME CONTACTS")); col.addView(prime)
        col.addView(label("COUNTRY CODE")); col.addView(cc)
        col.addView(button("SAVE", RED) { save(); showSettings(false) },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        col.addView(label("PERMISSIONS (ব্যাকগ্রাউন্ডে ভালো চলার জন্য)"))
        col.addView(button("Accessibility অন করো (screenshot/স্ক্রিন পড়ার জন্য)") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        col.addView(button("App permissions (Phone / SMS / Contacts / Call log সব Allow করো)") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        })
        col.addView(button("Display over other apps (অ্যাপ খোলার জন্য)") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        col.addView(button("Battery: Unrestricted করো") {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            } else Toast.makeText(this, "আগে থেকেই ঠিক আছে ✓", Toast.LENGTH_SHORT).show()
        })
        col.addView(button("← BACK") { showSettings(false) },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        settingsView = ScrollView(this).apply { setBackgroundColor(BG); addView(col) }
    }

    private fun showSettings(show: Boolean) {
        settingsView.visibility = if (show) View.VISIBLE else View.GONE
        mainView.visibility = if (show) View.GONE else View.VISIBLE
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (settingsView.visibility == View.VISIBLE) showSettings(false) else super.onBackPressed()
    }

    // ------------------------------------------------------------- lifecycle
    private val ticker = object : Runnable {
        override fun run() {
            val running = MyraService.running
            val st = status.text.toString()
            orb.state = when {
                !running -> "idle"
                st.startsWith("Connecting") || st.startsWith("Reconnecting") -> "thinking"
                MyraService.speakingNow -> "speaking"
                else -> "listening"
            }
            wave.active = running
            val red = if (running) 0.08f else 0f
            if (red != lastRed) { lastRed = red; redFx.animate().alpha(red).setDuration(400).start() }
            mic.text = if (running && MyraService.mutedNow) "🔇" else "🎤"
            mic.alpha = if (running && MyraService.mutedNow) 0.5f else 1f
            refreshAction()
            if (!running && (st.startsWith("Listening") || st.startsWith("Connecting"))) status.text = "Tap karke bolo 💬"
            if (tick % 300 == 0) updateInfo()
            tick++
            handler.postDelayed(this, 100)
        }
    }

    private fun updateInfo() {
        clock.text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
        val b = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val lv = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val sc = b?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        if (lv >= 0) batt.text = "🔋 ${lv * 100 / sc}%"
    }

    override fun onResume() {
        super.onResume()
        chatBox.removeAllViews(); lastBubble = ""
        MyraService.log.forEach { onEvent(it.first, it.second) }
        MyraService.listener = { k, t -> runOnUiThread { onEvent(k, t) } }
        updateInfo()
        handler.removeCallbacks(ticker); handler.post(ticker)
    }

    override fun onPause() {
        MyraService.listener = null
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    private fun onEvent(kind: String, text: String) {
        when (kind) {
            "s" -> status.text = text
            "u" -> bubble(text, true)
            "m" -> bubble(text, false)
            "t" -> toolLine(text)
        }
    }

    private fun toolLine(text: String) {
        val tv = TextView(this).apply {
            this.text = text; setTextColor(Color.parseColor("#777777")); textSize = 11f
            typeface = Typeface.MONOSPACE; setPadding(dp(4), dp(3), dp(4), dp(3))
        }
        chatBox.addView(tv)
        chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun bubble(text: String, user: Boolean) {
        if (!user && text == lastBubble) return
        lastBubble = if (user) "" else text
        val tv = TextView(this).apply {
            this.text = if (user) text else "●  $text"
            setTextColor(Color.WHITE); textSize = 14f; maxWidth = dp(280)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                if (user) { setColor(Color.parseColor("#2a0710")); setStroke(dp(1), RED) }
                else setColor(Color.parseColor("#1c1c1c"))
            }
        }
        if (text.contains("http")) { Linkify.addLinks(tv, Linkify.WEB_URLS); tv.setLinkTextColor(RED) }
        val lp = LinearLayout.LayoutParams(-2, -2).apply {
            topMargin = dp(5); gravity = if (user) Gravity.END else Gravity.START
        }
        chatBox.addView(tv, lp)
        chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
    }

    // ------------------------------------------------------------- actions
    private fun save() {
        prefs.edit()
            .putString("api_key", key.text.toString().trim())
            .putString("name", name.text.toString().trim().ifEmpty { "Friend" })
            .putString("prime", prime.text.toString().trim())
            .putString("cc", cc.text.toString().trim())
            .putString("model", models[modelSp.selectedItemPosition])
            .putString("voice", voices[voiceSp.selectedItemPosition])
            .putString("personality", pers[persSp.selectedItemPosition])
            .apply()
        Toast.makeText(this, "Saved ✓ (চালু থাকলে Stop → Start করো)", Toast.LENGTH_SHORT).show()
    }

    private fun onMicTap() {
        if (MyraService.running) {
            startService(Intent(this, MyraService::class.java).setAction(MyraService.ACTION_MUTE))
            return
        }
        if (prefs.getString("api_key", "").isNullOrBlank()) {
            Toast.makeText(this, "আগে Settings-এ API Key দাও", Toast.LENGTH_LONG).show()
            showSettings(true); return
        }
        val need = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CALL_LOG
        )
        if (Build.VERSION.SDK_INT >= 33) need.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = need.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 1) else startMyra()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startMyra()
        } else Toast.makeText(this, "Mic permission ছাড়া চলবে না", Toast.LENGTH_LONG).show()
    }

    private fun startMyra() {
        startForegroundService(Intent(this, MyraService::class.java).setAction(MyraService.ACTION_START))
        status.text = "Connecting..."
    }
}
