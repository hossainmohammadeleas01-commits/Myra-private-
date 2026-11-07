package com.myra.assistant

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private fun al(c: Int, a: Int) = (c and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)

private fun shade(c: Int, f: Float): Int {
    val hsv = FloatArray(3)
    Color.colorToHSV(c, hsv)
    hsv[2] = (hsv[2] * f).coerceIn(0f, 1f)
    return Color.HSVToColor(hsv)
}

private fun hueShift(c: Int, d: Float): Int {
    val hsv = FloatArray(3)
    Color.colorToHSV(c, hsv)
    hsv[0] = (hsv[0] + d + 360f) % 360f
    return Color.HSVToColor(hsv)
}

private fun lighten(c: Int, f: Float): Int {
    val r = Color.red(c) + ((255 - Color.red(c)) * f).toInt()
    val g = Color.green(c) + ((255 - Color.green(c)) * f).toInt()
    val b = Color.blue(c) + ((255 - Color.blue(c)) * f).toInt()
    return Color.rgb(r, g, b)
}

/** Illustrated avatar presets */
class GirlStyle(
    val name: String, val hair: Int, val hairHi: Int, val skin: Int, val top: Int,
    val iris: Int, val bg1: Int, val bg2: Int, val bob: Boolean, val acc: Int
)

val GIRLS = listOf(
    GirlStyle("Myra", 0xFF3B0F4F.toInt(), 0xFF7B3A9A.toInt(), 0xFFF6CBB0.toInt(), 0xFFFF1744.toInt(),
        0xFF8E2FB5.toInt(), 0xFF3A0016.toInt(), 0xFF12000A.toInt(), false, 0),
    GirlStyle("Ruby", 0xFFB3261E.toInt(), 0xFFF0675C.toInt(), 0xFFF2BE9B.toInt(), 0xFF1B1B1B.toInt(),
        0xFF5A3A1E.toInt(), 0xFF3A1500.toInt(), 0xFF140800.toInt(), false, 1),
    GirlStyle("Luna", 0xFFD5D8F2.toInt(), 0xFFFFFFFF.toInt(), 0xFFF9D9C6.toInt(), 0xFF5B6CFF.toInt(),
        0xFF3C6BFF.toInt(), 0xFF101648.toInt(), 0xFF070A24.toInt(), false, 2),
    GirlStyle("Aria", 0xFF6B3A1E.toInt(), 0xFFA8643A.toInt(), 0xFFE8B08A.toInt(), 0xFFFFB300.toInt(),
        0xFF2E7D4F.toInt(), 0xFF3A2008.toInt(), 0xFF160B03.toInt(), true, 3)
)

private val gp = Paint(Paint.ANTI_ALIAS_FLAG)

private fun fillP(c: Canvas, color: Int, p: Path) {
    gp.reset(); gp.isAntiAlias = true; gp.style = Paint.Style.FILL; gp.color = color
    c.drawPath(p, gp)
}

private fun strokeP(c: Canvas, color: Int, w: Float, p: Path) {
    gp.reset(); gp.isAntiAlias = true; gp.style = Paint.Style.STROKE; gp.color = color
    gp.strokeWidth = w; gp.strokeCap = Paint.Cap.ROUND
    c.drawPath(p, gp)
}

private fun ovalP(c: Canvas, color: Int, l: Float, t: Float, r: Float, b: Float) {
    gp.reset(); gp.isAntiAlias = true; gp.style = Paint.Style.FILL; gp.color = color
    c.drawOval(l, t, r, b, gp)
}

/** Draws the stylised girl inside a circle (centre cx,cy radius r). mouth 0..1 = open, blink 0..1 */
fun drawGirl(c: Canvas, cx: Float, cy: Float, r: Float, st: GirlStyle, mouth: Float, blink: Float) {
    c.save()
    val clip = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }
    c.clipPath(clip)
    c.translate(cx, cy)
    val u = r / 50f * 1.12f
    c.scale(u, u)
    c.translate(0f, 3f)

    // background
    gp.reset(); gp.isAntiAlias = true
    gp.shader = LinearGradient(0f, -50f, 0f, 50f, st.bg1, st.bg2, Shader.TileMode.CLAMP)
    c.drawRect(-50f, -50f, 50f, 50f, gp)

    // back hair
    val back = Path()
    back.moveTo(0f, -40f)
    back.cubicTo(-30f, -40f, -38f, -8f, -35f, if (st.bob) 18f else 26f)
    if (st.bob) {
        back.quadTo(-33f, 27f, -24f, 28f); back.lineTo(24f, 28f); back.quadTo(33f, 27f, 35f, 18f)
    } else {
        back.lineTo(-28f, 52f); back.lineTo(28f, 52f); back.lineTo(35f, 26f)
    }
    back.cubicTo(38f, -8f, 30f, -40f, 0f, -40f)
    back.close()
    fillP(c, st.hair, back)
    val hi = Path().apply { moveTo(-24f, -28f); quadTo(-31f, -4f, -27f, 22f) }
    strokeP(c, al(st.hairHi, 150), 2.2f, hi)

    // top / shoulders
    val top = Path().apply {
        moveTo(-38f, 52f); cubicTo(-36f, 36f, -20f, 32f, -9f, 31f)
        lineTo(9f, 31f); cubicTo(20f, 32f, 36f, 36f, 38f, 52f); close()
    }
    fillP(c, st.top, top)
    // neck
    val neck = Path().apply {
        moveTo(-7f, 18f); lineTo(7f, 18f); lineTo(7f, 30f)
        cubicTo(4f, 36f, -4f, 36f, -7f, 30f); close()
    }
    fillP(c, shade(st.skin, 0.92f), neck)

    // face
    val face = Path().apply {
        moveTo(0f, -25f)
        cubicTo(-18f, -25f, -20f, -6f, -17f, 6f)
        cubicTo(-14f, 17f, -6f, 22f, 0f, 22f)
        cubicTo(6f, 22f, 14f, 17f, 17f, 6f)
        cubicTo(20f, -6f, 18f, -25f, 0f, -25f)
        close()
    }
    fillP(c, st.skin, face)

    // fringe
    val fringe = Path().apply {
        moveTo(-20f, -2f); cubicTo(-25f, -30f, -8f, -35f, 0f, -35f)
        cubicTo(8f, -35f, 25f, -30f, 20f, -2f)
        cubicTo(17f, -18f, 7f, -22f, 3f, -16f)
        cubicTo(-3f, -21f, -14f, -17f, -20f, -2f); close()
    }
    fillP(c, st.hair, fringe)
    strokeP(c, al(st.hairHi, 130), 1.6f, Path().apply { moveTo(-8f, -31f); quadTo(2f, -27f, 12f, -29f) })

    // brows
    for (sx in floatArrayOf(-8.2f, 8.2f)) {
        strokeP(c, al(shade(st.hair, 0.9f), 210), 1.3f,
            Path().apply { moveTo(sx - 5f, -10.5f); quadTo(sx, -13.5f, sx + 5f, -11f) })
    }
    // eyes
    val eh = 5.0f * (1f - 0.92f * blink)
    for (sx in floatArrayOf(-8.2f, 8.2f)) {
        ovalP(c, 0xFFFFFFFF.toInt(), sx - 5f, -3f - eh, sx + 5f, -3f + eh)
        ovalP(c, st.iris, sx - 3.3f, -3f - eh * 0.9f, sx + 3.3f, -3f + eh * 0.9f)
        ovalP(c, 0xFF14060E.toInt(), sx - 1.7f, -3f - eh * 0.55f, sx + 1.7f, -3f + eh * 0.55f)
        if (blink < 0.5f) ovalP(c, 0xFFFFFFFF.toInt(), sx - 1.9f, -3f - eh * 0.6f, sx - 0.5f, -3f - eh * 0.2f)
        strokeP(c, 0xFF1A0A14.toInt(), 1.5f,
            Path().apply { moveTo(sx - 6f, -2f - eh * 0.4f); quadTo(sx, -3f - eh * 1.7f, sx + 6f, -2f - eh * 0.4f) })
        val dir = if (sx < 0) -1f else 1f
        strokeP(c, 0xFF1A0A14.toInt(), 1.3f,
            Path().apply { moveTo(sx + dir * 5.6f, -3f - eh * 0.5f); lineTo(sx + dir * 8f, -5.5f - eh * 0.4f) })
    }
    // nose
    strokeP(c, al(shade(st.skin, 0.8f), 170), 0.9f, Path().apply { moveTo(0f, 4f); quadTo(1.4f, 6.5f, -0.3f, 7f) })
    // blush
    ovalP(c, 0x55FF6B8A, -17f, 4.5f, -9f, 9f)
    ovalP(c, 0x55FF6B8A, 9f, 4.5f, 17f, 9f)

    // mouth
    val lip = 0xFFE0245E.toInt()
    if (mouth < 0.08f) {
        fillP(c, lip, Path().apply { moveTo(-4.2f, 13f); quadTo(0f, 17.4f, 4.2f, 13f); quadTo(0f, 14.4f, -4.2f, 13f); close() })
        strokeP(c, lip, 1.2f, Path().apply { moveTo(-4.6f, 13f); quadTo(0f, 11.8f, 4.6f, 13f) })
    } else {
        val m = Path().apply {
            moveTo(-4.8f, 13f); quadTo(0f, 11.4f, 4.8f, 13f); quadTo(0f, 13f + 13f * mouth, -4.8f, 13f); close()
        }
        fillP(c, 0xFF5A0A25.toInt(), m)
        if (mouth > 0.3f) {
            fillP(c, 0xCCFFFFFF.toInt(), Path().apply {
                moveTo(-3.3f, 12.7f); quadTo(0f, 11.9f, 3.3f, 12.7f); lineTo(3f, 14.2f); quadTo(0f, 13.9f, -3f, 14.2f); close()
            })
        }
        strokeP(c, lip, 1.5f, m)
    }

    // accessories
    when (st.acc) {
        0 -> {
            val bow = Path().apply {
                moveTo(17f, -30f); lineTo(25f, -35f); lineTo(25f, -25f); close()
                moveTo(17f, -30f); lineTo(9f, -35f); lineTo(9f, -25f); close()
            }
            fillP(c, st.top, bow)
            ovalP(c, lighten(st.top, 0.5f), 15f, -32f, 19f, -28f)
        }
        1 -> {
            for (a in 0 until 5) {
                val ang = a * 2f * PI.toFloat() / 5f
                ovalP(c, 0xFFFFD54F.toInt(), -20f + cos(ang) * 3.2f - 2.2f, -22f + sin(ang) * 3.2f - 2.2f,
                    -20f + cos(ang) * 3.2f + 2.2f, -22f + sin(ang) * 3.2f + 2.2f)
            }
            ovalP(c, 0xFFB3261E.toInt(), -21.5f, -23.5f, -18.5f, -20.5f)
            strokeP(c, 0xFFFFD54F.toInt(), 1.2f, Path().apply { addCircle(-17.5f, 12f, 2.4f, Path.Direction.CW) })
            strokeP(c, 0xFFFFD54F.toInt(), 1.2f, Path().apply { addCircle(17.5f, 12f, 2.4f, Path.Direction.CW) })
        }
        2 -> {
            val star = Path()
            for (i in 0 until 10) {
                val rr = if (i % 2 == 0) 4.2f else 1.8f
                val ang = (-90f + i * 36f) * PI.toFloat() / 180f
                val x = -17f + cos(ang) * rr
                val y = -28f + sin(ang) * rr
                if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
            }
            star.close()
            fillP(c, 0xFFFFF59D.toInt(), star)
            ovalP(c, 0xFFFFFFFF.toInt(), -18.8f, 10f, -16.2f, 12.6f)
            ovalP(c, 0xFFFFFFFF.toInt(), 16.2f, 10f, 18.8f, 12.6f)
        }
        else -> {
            strokeP(c, st.top, 3.2f, Path().apply { moveTo(-19f, -14f); quadTo(0f, -44f, 19f, -14f) })
            ovalP(c, 0xFFFFFFFF.toInt(), 16.2f, 10f, 18.8f, 12.6f)
            ovalP(c, 0xFFFFFFFF.toInt(), -18.8f, 10f, -16.2f, 12.6f)
        }
    }
    c.restore()
}

/** Animated orb / avatar (idle / listening / speaking / thinking) */
class OrbView(ctx: Context) : View(ctx) {
    var state = "idle"
    var accent = 0xFFFF1744.toInt()
    var avatar = "orb"         // "orb" | "g0".."g3" | "custom"
    var custom: Bitmap? = null
    var mouthX = 0.5f          // where the mouth is on the photo (0..1)
    var mouthY = 0.72f
    private var amp = 0f
    private var t = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val path = Path()

    private fun cols(): Pair<Int, Int> = when (state) {
        "listening" -> accent to hueShift(accent, 50f)
        "speaking" -> lighten(hueShift(accent, -35f), 0.25f) to accent
        "thinking" -> 0xFF40C4FF.toInt() to 0xFF00B0FF.toInt()
        else -> shade(accent, 0.8f) to shade(hueShift(accent, -30f), 0.6f)
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
        val pulse = if (state == "idle") 1f + 0.05f * sin(t * 4.19f) else 1f + amp * 0.12f
        val isAvatar = avatar != "orb"
        val r = base * (if (isAvatar) 0.5f else 0.48f) * pulse

        // glow
        p.reset(); p.isAntiAlias = true
        p.shader = RadialGradient(cx, cy, r * 1.6f, intArrayOf(al(c1, if (act) 150 else 100), al(c1, 0)), null, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r * 1.6f, p)

        // core: avatar picture or plain orb
        if (!isAvatar) {
            p.reset(); p.isAntiAlias = true
            p.shader = RadialGradient(cx - r * 0.3f, cy - r * 0.3f, r * 1.3f, intArrayOf(c2, c1), null, Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, r, p)
        } else if (avatar == "custom" && custom != null) {
            val bmp = custom!!
            c.save()
            path.reset(); path.addCircle(cx, cy, r, Path.Direction.CW)
            c.clipPath(path)
            val zoom = 1f + amp * 0.02f
            val s = max(2f * r / bmp.width, 2f * r / bmp.height) * zoom
            c.translate(cx, cy); c.scale(s, s)
            val bw = bmp.width.toFloat()
            val bh = bmp.height.toFloat()
            val l = -bw / 2f
            val tp = -bh / 2f
            p.reset(); p.isAntiAlias = true; p.isFilterBitmap = true
            c.drawBitmap(bmp, l, tp, p)
            // talking: the lower face drops a little, a dark mouth opening shows
            if (state == "speaking") {
                val mouth = ((0.25f + 0.75f * abs(sin(t * 11f))) * (0.45f + amp)).coerceIn(0f, 1f)
                val d = bh * 0.03f * mouth
                if (d > 0.8f) {
                    val mx = l + bw * mouthX
                    val my = tp + bh * mouthY
                    val mw = bw * 0.075f
                    p.reset(); p.isAntiAlias = true; p.color = 0xFF2A0610.toInt()
                    c.drawOval(mx - mw, my - d * 0.25f, mx + mw, my + d, p)
                    c.save()
                    c.clipRect(l, my + d, l + bw, tp + bh + d)
                    c.translate(0f, d)
                    p.reset(); p.isAntiAlias = true; p.isFilterBitmap = true
                    c.drawBitmap(bmp, l, tp, p)
                    c.restore()
                }
            }
            c.restore()
        } else {
            val idx = avatar.removePrefix("g").toIntOrNull()?.coerceIn(0, GIRLS.size - 1) ?: 0
            val mouth = if (state == "speaking")
                ((0.25f + 0.75f * abs(sin(t * 11f))) * (0.45f + amp)).coerceIn(0f, 1f) else 0f
            val bt = t % 3.8f
            val blink = if (bt < 0.16f) sin(bt / 0.16f * PI.toFloat()) else 0f
            drawGirl(c, cx, cy, r, GIRLS[idx], mouth, blink)
        }
        // avatar frame
        if (isAvatar) {
            p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE; p.strokeWidth = 5f
            p.color = al(c1, 230)
            c.drawCircle(cx, cy, r, p)
        }

        // rings
        for (i in 0 until 3) {
            p.reset(); p.isAntiAlias = true
            p.style = Paint.Style.STROKE; p.strokeWidth = 3f
            p.color = al(c2, 160 - i * 35)
            p.pathEffect = DashPathEffect(floatArrayOf(28f, 20f), 0f)
            val rr = r * (1.2f + i * 0.14f)
            rect.set(cx - rr, cy - rr, cx + rr, cy + rr)
            c.save()
            val dir = if (i % 2 == 0) 1f else -1f
            c.rotate(Math.toDegrees((t * (if (act) 1f else 0.3f) * (0.6f + i * 0.35f) * dir).toDouble()).toFloat(), cx, cy)
            c.drawArc(rect, 0f, 290f, false, p)
            c.restore()
        }
        // wave ring + particles
        if (act && state != "thinking") {
            p.reset(); p.isAntiAlias = true
            p.style = Paint.Style.STROKE; p.strokeWidth = 3f; p.color = al(c1, 190)
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
            p.reset(); p.isAntiAlias = true; p.color = al(c2, 235)
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
        // highlight on plain orb
        if (!isAvatar) {
            p.reset(); p.isAntiAlias = true
            p.shader = RadialGradient(cx - r * 0.4f, cy - r * 0.4f, r * 0.6f, intArrayOf(al(Color.WHITE, 90), al(Color.WHITE, 0)), null, Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, r, p)
        }
        postInvalidateOnAnimation()
    }
}

/** Small round preview used in Settings to pick an avatar */
class AvatarThumb(ctx: Context, val kind: String) : View(ctx) {
    var accent = 0xFFFF1744.toInt()
    var chosen = false
    var custom: Bitmap? = null
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f - 6f
        when {
            kind == "orb" -> {
                p.reset(); p.isAntiAlias = true
                p.shader = RadialGradient(cx - r * 0.3f, cy - r * 0.3f, r * 1.3f,
                    intArrayOf(hueShift(accent, 50f), accent), null, Shader.TileMode.CLAMP)
                c.drawCircle(cx, cy, r, p)
            }
            kind == "custom" || kind == "media" -> {
                val bmp = custom
                if (bmp != null) {
                    c.save()
                    val clip = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }
                    c.clipPath(clip)
                    val s = max(2f * r / bmp.width, 2f * r / bmp.height)
                    c.translate(cx, cy); c.scale(s, s)
                    p.reset(); p.isAntiAlias = true; p.isFilterBitmap = true
                    c.drawBitmap(bmp, -bmp.width / 2f, -bmp.height / 2f, p)
                    c.restore()
                } else {
                    p.reset(); p.isAntiAlias = true; p.color = 0xFF262626.toInt()
                    c.drawCircle(cx, cy, r, p)
                    p.color = Color.WHITE; p.textSize = r * 0.9f; p.textAlign = Paint.Align.CENTER
                    c.drawText(if (kind == "media") "\u25B6" else "+", cx, cy + r * 0.3f, p)
                }
            }
            else -> {
                val idx = kind.removePrefix("g").toIntOrNull()?.coerceIn(0, GIRLS.size - 1) ?: 0
                drawGirl(c, cx, cy, r, GIRLS[idx], 0f, 0f)
            }
        }
        p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE
        p.strokeWidth = if (chosen) 5f else 2f
        p.color = if (chosen) accent else 0xFF444444.toInt()
        c.drawCircle(cx, cy, r + 2f, p)
    }
}


/** Full-screen colourful edge glow that jumps with the voice (used over photo / video backgrounds) */
class GlowView(ctx: Context) : View(ctx) {
    var accent = 0xFFFF1744.toInt()
    var active = false
    private var t = 0f
    private var amp = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        t += 0.016f
        val target = when {
            !active -> 0f
            MyraService.speakingNow -> 0.45f + 0.55f * abs(sin(t * 6f))
            else -> MyraService.level
        }
        amp += (target - amp) * 0.2f
        val w = width.toFloat()
        val h = height.toFloat()
        val th = w * 0.07f * (0.5f + amp * 2.4f)
        val a = (if (active) 70 + amp * 185 else 45).toInt()
        for (k in 0 until 4) {
            val col = hueShift(accent, (t * 70f + k * 80f) % 360f)
            val c0 = al(col, a)
            val c1 = al(col, 0)
            p.shader = when (k) {
                0 -> LinearGradient(0f, 0f, 0f, th, c0, c1, Shader.TileMode.CLAMP)
                1 -> LinearGradient(0f, h, 0f, h - th, c0, c1, Shader.TileMode.CLAMP)
                2 -> LinearGradient(0f, 0f, th, 0f, c0, c1, Shader.TileMode.CLAMP)
                else -> LinearGradient(w, 0f, w - th, 0f, c0, c1, Shader.TileMode.CLAMP)
            }
            when (k) {
                0 -> c.drawRect(0f, 0f, w, th, p)
                1 -> c.drawRect(0f, h - th, w, h, p)
                2 -> c.drawRect(0f, 0f, th, h, p)
                else -> c.drawRect(w - th, 0f, w, h, p)
            }
        }
        postInvalidateOnAnimation()
    }
}

/** Colourful moving background (shown behind a full-screen photo / video) */
class AuraView(ctx: Context) : View(ctx) {
    var accent = 0xFFFF1744.toInt()
    private var t = 0f
    private var amp = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        t += 0.016f
        val target = if (MyraService.speakingNow) 0.5f + 0.5f * abs(sin(t * 5f)) else MyraService.level
        amp += (target - amp) * 0.15f
        c.drawColor(0xFF050505.toInt())
        val w = width.toFloat()
        val h = height.toFloat()
        for (i in 0 until 6) {
            val ang = t * (0.25f + i * 0.07f) + i * 1.1f
            val x = w * (0.5f + 0.38f * cos(ang * (1f + i * 0.13f)))
            val y = h * (0.5f + 0.34f * sin(ang * (0.8f + i * 0.11f)))
            val r = min(w, h) * (0.55f + 0.28f * amp + 0.08f * sin(t * 2f + i))
            val col = hueShift(accent, i * 55f + t * 20f)
            p.reset(); p.isAntiAlias = true
            p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
            p.shader = RadialGradient(x, y, r, intArrayOf(al(col, 190), al(col, 0)), null, Shader.TileMode.CLAMP)
            c.drawCircle(x, y, r, p)
        }
        postInvalidateOnAnimation()
    }
}
