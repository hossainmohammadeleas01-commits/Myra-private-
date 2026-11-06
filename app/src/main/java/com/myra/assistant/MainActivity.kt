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
import android.app.AlertDialog
import android.app.Dialog
import android.content.ContentValues
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.method.LinkMovementMethod
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.SeekBar
import java.io.File
import java.io.FileOutputStream
import android.graphics.drawable.ColorDrawable
import android.provider.MediaStore
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Window
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

/** 20-bar waveform */
class WaveView(ctx: Context) : View(ctx) {
    var active = false
    var color = Color.parseColor("#FF1744")
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
            p.color = withAlpha(color, (150 + bars[i] * 105).toInt())
            c.drawRoundRect(i * w + w * 0.2f, (height - h) / 2f, i * w + w * 0.8f, (height + h) / 2f, 4f, 4f, p)
        }
        postInvalidateOnAnimation()
    }
}

/** waveform-bars icon used on the round "voice mode" button */
class BarsIcon(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    override fun onDraw(c: Canvas) {
        val hs = floatArrayOf(0.28f, 0.5f, 0.72f, 0.5f, 0.28f)
        val cw = width / 2f
        val ch = height / 2f
        val bw = width * 0.06f
        val gap = width * 0.1f
        for (i in 0 until 5) {
            val x = cw + (i - 2) * (bw + gap)
            val h = height * hs[i] * 0.6f
            c.drawRoundRect(x - bw / 2, ch - h / 2, x + bw / 2, ch + h / 2, bw / 2, bw / 2, p)
        }
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
    private lateinit var actionBox: FrameLayout
    private lateinit var actionText: TextView
    private lateinit var actionBars: BarsIcon
    private lateinit var centerBox: LinearLayout
    private lateinit var barRow: LinearLayout
    private lateinit var hintTv: TextView
    private lateinit var modeBtn: TextView
    private lateinit var dictSp: Spinner
    private val thumbs = mutableListOf<AvatarThumb>()
    private var customBmp: Bitmap? = null
    private val chatLog = StringBuilder()
    private val swatches = listOf("#FF1744", "#FF4081", "#E040FB", "#7C4DFF", "#40C4FF", "#00E676", "#FFEA00", "#FF9100", "#FFFFFF")
    private var recognizer: SpeechRecognizer? = null
    private var dictating = false
    private var dictBase = ""
    private var cameraUri: Uri? = null
    private var layoutKey = ""
    private val dictCodes = listOf("bn-BD", "hi-IN", "en-US", "en-IN")
    private val connKeys = listOf("search", "apps", "calls", "sms", "screen")
    private val connLabels = arrayOf(
        "Google Search", "Apps & Browser (অ্যাপ/লিংক খোলা)", "Calls & Contacts (কল, কল লিস্ট)",
        "SMS & WhatsApp", "Screen reading (screenshot)"
    )
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

    private var RED = Color.parseColor("#FF1744")
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
        try { RED = Color.parseColor(prefs.getString("theme", "#FF1744")) } catch (_: Exception) {}
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
        applyAppearance()
        if (prefs.getBoolean("reopen", false)) {
            prefs.edit().putBoolean("reopen", false).apply()
            showSettings(true)
        }
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
        modeBtn = TextView(this).apply {
            text = "🔴"; textSize = 18f; setPadding(dp(10), 0, 0, 0)
            setOnClickListener { toggleDisplayMode() }
        }
        val copyBtn = TextView(this).apply {
            text = "\uD83D\uDCCB"; textSize = 18f; setPadding(dp(8), 0, 0, 0)
            setOnClickListener { copyAll() }
        }
        right.addView(clock); right.addView(copyBtn); right.addView(modeBtn); right.addView(gear)
        top.addView(batt, LinearLayout.LayoutParams(0, -2, 0.8f))
        top.addView(titleBox, LinearLayout.LayoutParams(0, -2, 1.1f))
        top.addView(right, LinearLayout.LayoutParams(0, -2, 1.7f))
        mainView.addView(top)

        // chat (sits ABOVE the orb)
        chatBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        chatScroll = ScrollView(this).apply { addView(chatBox); isVerticalScrollBarEnabled = false }
        mainView.addView(chatScroll, LinearLayout.LayoutParams(-1, 0, 1f))

        // orb area
        centerBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        orb = OrbView(this)
        orb.setOnClickListener {
            if (MyraService.running) {
                startService(Intent(this, MyraService::class.java).setAction(MyraService.ACTION_STOP))
            } else onMicTap()
        }
        wave = WaveView(this)
        status = TextView(this).apply {
            text = "Tap karke bolo 💬"; setTextColor(Color.parseColor("#777777")); textSize = 13f
            gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0)
        }
        centerBox.addView(orb, LinearLayout.LayoutParams(dp(260), dp(260)))
        centerBox.addView(wave, LinearLayout.LayoutParams(dp(200), dp(40)))
        centerBox.addView(status)
        mainView.addView(centerBox, LinearLayout.LayoutParams(-1, 0, 1f))

        // input bar (Claude-style): + | text | mic(dictate) | voice/send
        input = EditText(this).apply {
            hint = "Message MYRA or tap mic to dictate"
            setHintTextColor(Color.parseColor("#777777")); setTextColor(Color.WHITE)
            textSize = 15f; background = null; maxLines = 4
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { refreshAction() }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        val plus = circleBtn("+", "#232323") { showAddSheet() }
        mic = circleBtn("🎤", "#232323") { onMicBtn() }
        actionText = TextView(this).apply {
            text = "➤"; textSize = 20f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
        }
        actionBars = BarsIcon(this)
        actionBox = FrameLayout(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(RED) }
            addView(actionText, FrameLayout.LayoutParams(-1, -1))
            addView(actionBars, FrameLayout.LayoutParams(-1, -1))
            setOnClickListener { onAction() }
        }
        barRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(32).toFloat(); setColor(Color.parseColor("#141414"))
                setStroke(dp(1), Color.parseColor("#2a2a2a"))
            }
        }
        barRow.addView(plus, LinearLayout.LayoutParams(dp(44), dp(44)))
        barRow.addView(input, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(10); rightMargin = dp(6) })
        barRow.addView(mic, LinearLayout.LayoutParams(dp(44), dp(44)))
        barRow.addView(actionBox, LinearLayout.LayoutParams(dp(44), dp(44)).apply { leftMargin = dp(6) })
        mainView.addView(barRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        hintTv = TextView(this).apply {
            text = "🎤 dictate  •  ➤ send  •  bars = live voice  •  + add"
            setTextColor(Color.parseColor("#666666")); textSize = 11f; gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
        }
        mainView.addView(hintTv)
        applyLayout()
    }

    private fun circleBtn(t: String, fill: String, click: () -> Unit) = TextView(this).apply {
        text = t; textSize = 20f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor(fill)) }
        setOnClickListener { click() }
    }

    // ---- layout: chat above, orb below; or orb-only red screen
    private fun toggleDisplayMode() {
        val orbOnly = prefs.getString("display_mode", "chat") == "orb"
        prefs.edit().putString("display_mode", if (orbOnly) "chat" else "orb").apply()
        applyLayout()
    }

    private fun applyLayout() {
        val orbOnly = prefs.getString("display_mode", "chat") == "orb"
        val hasChat = chatBox.childCount > 0
        val key = "$orbOnly-$hasChat"
        if (key == layoutKey) return
        layoutKey = key
        val big = orbOnly || !hasChat
        val o = dp(if (orbOnly) 300 else if (big) 260 else 120)
        orb.layoutParams = LinearLayout.LayoutParams(o, o)
        wave.layoutParams = LinearLayout.LayoutParams(dp(if (big) 200 else 140), dp(if (big) 40 else 22))
        chatScroll.visibility = if (orbOnly || !hasChat) View.GONE else View.VISIBLE
        centerBox.layoutParams =
            if (big) LinearLayout.LayoutParams(-1, 0, 1f) else LinearLayout.LayoutParams(-1, -2)
        barRow.visibility = if (orbOnly) View.GONE else View.VISIBLE
        hintTv.visibility = if (orbOnly) View.GONE else View.VISIBLE
        modeBtn.text = if (orbOnly) "💬" else "🔴"
    }

    private fun refreshAction() {
        val hasText = !input.text.isNullOrBlank()
        val running = MyraService.running
        val showText = hasText || running
        actionText.visibility = if (showText) View.VISIBLE else View.GONE
        actionBars.visibility = if (showText) View.GONE else View.VISIBLE
        actionText.text = if (hasText) "➤" else "■"
    }

    private fun refreshMic() {
        val muted = MyraService.running && MyraService.mutedNow
        mic.text = when {
            dictating -> "■"
            muted -> "🔇"
            else -> "🎤"
        }
        mic.alpha = if (muted) 0.5f else 1f
        (mic.background as? GradientDrawable)?.setColor(Color.parseColor(if (dictating) "#FF1744" else "#232323"))
    }

    private fun applyAppearance() {
        val av = prefs.getString("avatar", "orb") ?: "orb"
        if (av == "custom" && customBmp == null) {
            val f = File(filesDir, "avatar.jpg")
            if (f.exists()) customBmp = BitmapFactory.decodeFile(f.path)
        }
        orb.accent = RED
        orb.avatar = if (av == "custom" && customBmp == null) "orb" else av
        orb.custom = customBmp
        orb.mouthX = prefs.getFloat("mouth_x", 0.5f)
        orb.mouthY = prefs.getFloat("mouth_y", 0.72f)
        wave.color = RED
        redFx.setBackgroundColor(RED)
        lastRed = -1f
        thumbs.forEach {
            it.accent = RED
            it.chosen = (it.kind == av)
            if (it.kind == "custom") it.custom = customBmp
            it.invalidate()
        }
    }

    private fun selectAvatar(kind: String) {
        if (kind == "custom" && !File(filesDir, "avatar.jpg").exists()) { pickFile("image/*", 10); return }
        prefs.edit().putString("avatar", kind).apply()
        applyAppearance()
        toast("Avatar সেট ✓")
    }

    private fun saveCustomAvatar(uri: Uri) {
        try {
            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
            val bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return
            val side = minOf(bmp.width, bmp.height)
            val sq = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
            val out = Bitmap.createScaledBitmap(sq, 512, 512, true)
            FileOutputStream(File(filesDir, "avatar.jpg")).use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            customBmp = out
            prefs.edit().putString("avatar", "custom").apply()
            applyAppearance()
            toast("তোমার ছবি avatar হয়েছে ✓")
            calibrateMouth()
        } catch (e: Exception) { toast("ছবি সেট করা যায়নি: ${e.message}") }
    }

    private fun calibrateMouth() {
        val bmp = customBmp
        if (bmp == null) { toast("আগে নিজের ছবি দাও (প্রথম ঘর)"); return }
        val d = Dialog(this)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val size = dp(300)
        val ms = dp(36)
        val box = FrameLayout(this)
        val iv = ImageView(this)
        iv.setImageBitmap(bmp)
        iv.scaleType = ImageView.ScaleType.FIT_XY
        box.addView(iv, FrameLayout.LayoutParams(size, size))
        val marker = View(this)
        marker.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL; setStroke(dp(3), RED); setColor(Color.parseColor("#33FFFFFF"))
        }
        box.addView(marker, FrameLayout.LayoutParams(ms, ms))
        var fx = prefs.getFloat("mouth_x", 0.5f)
        var fy = prefs.getFloat("mouth_y", 0.72f)
        fun place() {
            marker.translationX = fx * size - ms / 2f
            marker.translationY = fy * size - ms / 2f
        }
        place()
        iv.setOnTouchListener { _, e ->
            fx = (e.x / size).coerceIn(0f, 1f)
            fy = (e.y / size).coerceIn(0f, 1f)
            place(); true
        }
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(16), dp(16), dp(16))
        col.setBackgroundColor(Color.parseColor("#1a1a1a"))
        col.addView(TextView(this).apply {
            text = "ছবিতে ঠোঁটের ঠিক মাঝখানে চাপো"; setTextColor(Color.WHITE); textSize = 15f
            setPadding(0, 0, 0, dp(10))
        })
        col.addView(box, LinearLayout.LayoutParams(size, size))
        col.addView(button("SAVE", RED) {
            prefs.edit().putFloat("mouth_x", fx).putFloat("mouth_y", fy).apply()
            applyAppearance(); d.dismiss(); toast("Saved ✓")
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        d.setContentView(col)
        d.show()
    }

    private fun applyThemeColor(hex: String) {
        val h = if (hex.startsWith("#")) hex else "#$hex"
        try { Color.parseColor(h) } catch (_: Exception) { toast("রঙ ঠিক নেই (যেমন #00BCD4)"); return }
        prefs.edit().putString("theme", h).putBoolean("reopen", true).apply()
        recreate()
    }

    private fun copyAll() {
        if (chatLog.isEmpty()) { toast("কপি করার মতো লেখা নেই"); return }
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("MYRA chat", chatLog.toString().trim()))
        toast("সব লেখা কপি হয়েছে ✓")
    }

    private fun copyText(t: String) {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("MYRA", t))
        toast("কপি হয়েছে ✓")
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()

    // ---- right button: send text / stop / start live voice
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
        } else onMicTap()
    }

    // ---- mic button: dictate into the text box (or mute when live voice is on)
    private fun onMicBtn() {
        if (MyraService.running) {
            startService(Intent(this, MyraService::class.java).setAction(MyraService.ACTION_MUTE))
        } else toggleDictation()
    }

    private fun toggleDictation() {
        if (dictating) { recognizer?.stopListening(); return }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 2); return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast("Speech recognition নেই (Google অ্যাপ লাগবে)"); return
        }
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer = r
        val cur = input.text.toString()
        dictBase = if (cur.isBlank()) "" else cur.trimEnd() + " "
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) { dictating = true; refreshMic(); status.text = "Bolo... বলো 🎤" }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(e: Int) {
                endDictation()
                if (e != SpeechRecognizer.ERROR_NO_MATCH && e != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) toast("Mic error $e")
            }
            override fun onResults(b: Bundle?) { putDictation(b); endDictation() }
            override fun onPartialResults(b: Bundle?) { putDictation(b) }
            override fun onEvent(t: Int, p: Bundle?) {}
        })
        val lang = prefs.getString("dict_lang", "bn-BD")
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        })
    }

    private fun putDictation(b: Bundle?) {
        val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
        input.setText(dictBase + t)
        input.setSelection(input.text.length)
    }

    private fun endDictation() {
        dictating = false
        refreshMic()
        if (!MyraService.running) status.text = "Tap karke bolo 💬"
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
    }

    override fun onDestroy() {
        try { recognizer?.destroy() } catch (_: Exception) {}
        super.onDestroy()
    }

    // ---- "+" sheet: Camera / Photos / Files / Project / Permission / Connectors / Skills
    private fun needRunning(): Boolean {
        if (MyraService.running) return true
        toast("আগে MYRA চালু করো (লাল বাটন চাপো)")
        return false
    }

    private fun showAddSheet() {
        val d = Dialog(this)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val rad = dp(24).toFloat()
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(14), dp(16), dp(22))
        col.background = GradientDrawable().apply {
            setColor(Color.parseColor("#1a1a1a")); cornerRadii = floatArrayOf(rad, rad, rad, rad, 0f, 0f, 0f, 0f)
        }
        col.addView(TextView(this).apply {
            text = "Add to session"; setTextColor(Color.WHITE); textSize = 17f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(14))
        })
        val tiles = LinearLayout(this)
        tiles.orientation = LinearLayout.HORIZONTAL
        fun tile(icon: String, label: String, click: () -> Unit) {
            val t = LinearLayout(this)
            t.orientation = LinearLayout.VERTICAL; t.gravity = Gravity.CENTER
            t.background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(Color.parseColor("#262626")) }
            t.addView(TextView(this).apply { text = icon; textSize = 26f; gravity = Gravity.CENTER })
            t.addView(TextView(this).apply {
                text = label; setTextColor(Color.WHITE); textSize = 13f; gravity = Gravity.CENTER; setPadding(0, dp(4), 0, 0)
            })
            t.setOnClickListener { d.dismiss(); click() }
            tiles.addView(t, LinearLayout.LayoutParams(0, dp(90), 1f).apply { setMargins(dp(4), 0, dp(4), 0) })
        }
        fun row(icon: String, title: String, sub: String, click: () -> Unit) {
            val r2 = LinearLayout(this)
            r2.orientation = LinearLayout.HORIZONTAL; r2.gravity = Gravity.CENTER_VERTICAL
            r2.setPadding(dp(14), dp(12), dp(14), dp(12))
            r2.background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(Color.parseColor("#262626")) }
            r2.addView(TextView(this).apply { text = icon; textSize = 22f; setPadding(0, 0, dp(14), 0) })
            val tb = LinearLayout(this)
            tb.orientation = LinearLayout.VERTICAL
            tb.addView(TextView(this).apply { text = title; setTextColor(Color.WHITE); textSize = 16f })
            tb.addView(TextView(this).apply { text = sub; setTextColor(Color.parseColor("#999999")); textSize = 13f })
            r2.addView(tb, LinearLayout.LayoutParams(0, -2, 1f))
            r2.addView(TextView(this).apply { text = "›"; setTextColor(Color.GRAY); textSize = 22f })
            r2.setOnClickListener { d.dismiss(); click() }
            col.addView(r2, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }
        tile("📷", "Camera") { if (needRunning()) takePhoto() }
        tile("🖼", "Photos") { if (needRunning()) pickFile("image/*", 7) }
        tile("📄", "Files") { if (needRunning()) pickFile("*/*", 9) }
        col.addView(tiles)

        val projSub = if (prefs.getString("project", "").isNullOrBlank()) "None" else "Added"
        val perm = if (prefs.getString("perm_mode", "manual") == "manual") "Manual" else "Auto"
        val connOn = connKeys.count { prefs.getBoolean("conn_$it", true) }
        val skillN = (prefs.getString("skills", "") ?: "").lines().count { it.isNotBlank() }
        row("📁", "Add to project", projSub) {
            editDialog("Project (MYRA-কে এই context মনে রাখতে বলো)", "যেমন: আমি একজন ছাত্র, HSC পরীক্ষার প্রস্তুতি নিচ্ছি…", "project")
        }
        row("✋", "Permission", perm) { permDialog() }
        row("🧩", "Connectors", "$connOn of ${connKeys.size} on") { connDialog() }
        row("📚", "Skills", if (skillN == 0) "None" else "$skillN added") {
            editDialog("Skills (প্রতি লাইনে একটা)", "English tutor: আমার ভুল ঠিক করে দেবে\nCook: রেসিপি বলবে", "skills")
        }
        d.setContentView(col)
        d.window?.setLayout(-1, -2)
        d.window?.setGravity(Gravity.BOTTOM)
        d.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        d.show()
    }

    private fun editDialog(title: String, hint: String, key: String) {
        val et = EditText(this)
        et.setText(prefs.getString(key, ""))
        et.hint = hint
        et.minLines = 4
        et.gravity = Gravity.TOP or Gravity.START
        et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        et.setPadding(dp(16), dp(12), dp(16), dp(12))
        AlertDialog.Builder(this).setTitle(title).setView(et)
            .setPositiveButton("Save") { _, _ ->
                prefs.edit().putString(key, et.text.toString().trim()).apply()
                toast("Saved ✓ (MYRA Stop → Start করলে কাজ করবে)")
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun permDialog() {
        val items = arrayOf("Manual — কল/SMS/মেসেজের আগে MYRA জিজ্ঞেস করবে", "Auto — সরাসরি করে ফেলবে")
        val cur = if (prefs.getString("perm_mode", "manual") == "manual") 0 else 1
        AlertDialog.Builder(this).setTitle("Permission")
            .setSingleChoiceItems(items, cur) { dlg, which ->
                prefs.edit().putString("perm_mode", if (which == 0) "manual" else "auto").apply()
                dlg.dismiss(); toast("Saved ✓ (MYRA Stop → Start করলে কাজ করবে)")
            }.show()
    }

    private fun connDialog() {
        val checked = BooleanArray(connKeys.size) { prefs.getBoolean("conn_${connKeys[it]}", true) }
        AlertDialog.Builder(this).setTitle("Connectors")
            .setMultiChoiceItems(connLabels, checked) { _, i, on -> checked[i] = on }
            .setPositiveButton("Save") { _, _ ->
                val e = prefs.edit()
                connKeys.forEachIndexed { i, k -> e.putBoolean("conn_$k", checked[i]) }
                e.apply(); toast("Saved ✓ (MYRA Stop → Start করলে কাজ করবে)")
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun pickFile(mime: String, code: Int) {
        startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType(mime), code)
    }

    private fun takePhoto() {
        if (Build.VERSION.SDK_INT < 29) { toast("Camera Android 10+ লাগে"); return }
        val cv = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "MYRA_cam_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MYRA")
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv) ?: return
        cameraUri = uri
        try {
            startActivityForResult(Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, uri), 8)
        } catch (e: Exception) { toast("Camera খোলা যায়নি: ${e.message}") }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (res != RESULT_OK) return
        when (req) {
            7 -> data?.data?.let { sendImageUri(it) }
            8 -> cameraUri?.let { sendImageUri(it) }
            9 -> data?.data?.let { handleFile(it) }
            10 -> data?.data?.let { saveCustomAvatar(it) }
        }
    }

    private fun sendImageUri(uri: Uri) {
        try {
            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
            contentResolver.openInputStream(uri)?.use {
                MyraService.pendingImage = BitmapFactory.decodeStream(it, null, opts)
            }
            startService(Intent(this, MyraService::class.java).setAction(MyraService.ACTION_IMAGE))
        } catch (e: Exception) { toast("Image পড়া যায়নি: ${e.message}") }
    }

    private fun handleFile(uri: Uri) {
        val mime = contentResolver.getType(uri) ?: ""
        val nm = uri.lastPathSegment ?: "file"
        val isText = mime.startsWith("text/") || nm.endsWith(".txt") || nm.endsWith(".md") ||
            nm.endsWith(".csv") || nm.endsWith(".json")
        when {
            mime.startsWith("image/") -> sendImageUri(uri)
            isText -> {
                val txt = try {
                    contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }?.take(15000) ?: ""
                } catch (e: Exception) { "" }
                if (txt.isBlank()) toast("ফাইল পড়া যায়নি") else
                    startService(Intent(this, MyraService::class.java).setAction(MyraService.ACTION_TEXT)
                        .putExtra("text", "I'm sharing a file named $nm. Its content:\n$txt\n\nRead it and help me with it."))
            }
            else -> toast("এই ফাইল টাইপ এখনো সাপোর্ট করে না (শুধু ছবি ও টেক্সট ফাইল)")
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
        dictSp = spinner(listOf("বাংলা (bn-BD)", "हिन्दी (hi-IN)", "English (en-US)", "English (en-IN)"))
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
        dictSp.setSelection(dictCodes.indexOf(prefs.getString("dict_lang", "bn-BD")).coerceAtLeast(0))

        col.addView(label("API KEY")); col.addView(key)
        col.addView(label("YOUR NAME")); col.addView(name)
        col.addView(label("AI MODEL")); col.addView(modelSp)
        col.addView(label("VOICE")); col.addView(voiceSp)
        col.addView(label("PERSONALITY")); col.addView(persSp)
        col.addView(label("PRIME CONTACTS")); col.addView(prime)
        col.addView(label("COUNTRY CODE")); col.addView(cc)
        col.addView(label("DICTATION LANGUAGE (🎤 দিয়ে লেখার ভাষা)")); col.addView(dictSp)
        col.addView(button("SAVE", RED) { save(); showSettings(false) },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        // ---- appearance
        col.addView(label("AVATAR — প্রথম ঘরটা তোমার নিজের ছবি (চাপো; বদলাতে লং-প্রেস)"))
        val hs = HorizontalScrollView(this)
        val trow = LinearLayout(this)
        trow.orientation = LinearLayout.HORIZONTAL
        thumbs.clear()
        for (k in listOf("custom", "orb", "g0", "g1", "g2", "g3")) {
            val th = AvatarThumb(this, k)
            th.setOnClickListener { selectAvatar(k) }
            if (k == "custom") th.setOnLongClickListener { pickFile("image/*", 10); true }
            thumbs.add(th)
            trow.addView(th, LinearLayout.LayoutParams(dp(72), dp(72)).apply { rightMargin = dp(8) })
        }
        hs.addView(trow)
        col.addView(hs)
        col.addView(button("\uD83D\uDC44 ছবিতে মুখ কোথায় সেট করো (কথা বললে মুখ নড়বে)") { calibrateMouth() },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        col.addView(label("THEME COLOR (ব্যাকগ্রাউন্ড / orb / বাটনের রঙ)"))
        val sw = LinearLayout(this)
        sw.orientation = LinearLayout.HORIZONTAL
        val swScroll = HorizontalScrollView(this)
        for (hex in swatches) {
            val v = View(this)
            v.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL; setColor(Color.parseColor(hex)); setStroke(dp(1), Color.parseColor("#555555"))
            }
            v.setOnClickListener { applyThemeColor(hex) }
            sw.addView(v, LinearLayout.LayoutParams(dp(40), dp(40)).apply { rightMargin = dp(10) })
        }
        swScroll.addView(sw)
        col.addView(swScroll)
        val hexRow = LinearLayout(this)
        hexRow.orientation = LinearLayout.HORIZONTAL
        val hexEt = edit("যেকোনো রঙ, যেমন #00BCD4 বা #FF5722")
        hexRow.addView(hexEt, LinearLayout.LayoutParams(0, -2, 1f))
        hexRow.addView(button("APPLY") { applyThemeColor(hexEt.text.toString().trim()) },
            LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
        col.addView(hexRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        col.addView(label("SCREEN COLOR STRENGTH (orb-only স্ক্রিন কতটা উজ্জ্বল হবে)"))
        val sb = SeekBar(this)
        sb.max = 100
        sb.progress = prefs.getInt("bg_alpha", 85)
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, v: Int, u: Boolean) {
                prefs.edit().putInt("bg_alpha", v.coerceAtLeast(10)).apply()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        col.addView(sb)

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
            val orbOnly = prefs.getString("display_mode", "chat") == "orb"
            val base = prefs.getInt("bg_alpha", 85) / 100f
            val red = if (orbOnly) (if (running) base else base * 0.5f) else if (running) 0.10f else 0f
            if (red != lastRed) { lastRed = red; redFx.animate().alpha(red).setDuration(400).start() }
            refreshMic()
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
        chatBox.removeAllViews(); lastBubble = ""; layoutKey = ""; chatLog.setLength(0)
        MyraService.log.forEach { onEvent(it.first, it.second) }
        applyLayout()
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
            setTextIsSelectable(true)
        }
        chatBox.addView(tv)
        applyLayout()
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
        tv.setTextIsSelectable(true)
        if (text.contains("http")) {
            Linkify.addLinks(tv, Linkify.WEB_URLS); tv.setLinkTextColor(RED)
            tv.movementMethod = LinkMovementMethod.getInstance()
            tv.setOnLongClickListener { copyText(text); true }
        }
        chatLog.append(if (user) "You: " else "MYRA: ").append(text).append("\n\n")
        val lp = LinearLayout.LayoutParams(-2, -2).apply {
            topMargin = dp(5); gravity = if (user) Gravity.END else Gravity.START
        }
        chatBox.addView(tv, lp)
        applyLayout()
        chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
    }

    // ------------------------------------------------------------- actions
    private fun save() {
        prefs.edit()
            .putString("api_key", key.text.toString().trim())
            .putString("name", name.text.toString().trim().ifEmpty { "Friend" })
            .putString("prime", prime.text.toString().trim())
            .putString("cc", cc.text.toString().trim())
            .putString("dict_lang", dictCodes[dictSp.selectedItemPosition])
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
        if (code == 2) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) toggleDictation()
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startMyra()
        } else Toast.makeText(this, "Mic permission ছাড়া চলবে না", Toast.LENGTH_LONG).show()
    }

    private fun startMyra() {
        startForegroundService(Intent(this, MyraService::class.java).setAction(MyraService.ACTION_START))
        status.text = "Connecting..."
    }
}
