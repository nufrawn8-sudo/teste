package com.example.sesoynatici

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.floor

/**
 * Tüm ekran, 415x739'luk tasarım koordinatlarında çizilir ve genişliğe göre ölçeklenir.
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class PlayerView(context: Context) : View(context) {

    var topInset = 0f
        set(v) { field = v; invalidate() }
    var bottomInset = 0f
        set(v) { field = v; invalidate() }
    var peaks = FloatArray(0)
        set(v) { field = v; invalidate() }
    var progress = 0f
        set(v) { field = v; invalidate() }
    var playing = true
        set(v) { field = v; invalidate() }
    var title = "1 of 1"
        set(v) { field = v; invalidate() }
    var dragging = false
        private set

    var onDone: (() -> Unit)? = null
    var onShare: (() -> Unit)? = null
    var onList: (() -> Unit)? = null
    var onToggle: (() -> Unit)? = null
    var onSeek: ((Float) -> Unit)? = null
    var onSwipe: ((Int) -> Unit)? = null

    private val blue = Color.rgb(0x2A, 0x7D, 0xE1)
    private val gray = Color.rgb(0x8E, 0x8E, 0x93)
    private val headerColor = Color.rgb(0x16, 0x16, 0x17)
    private val bottomColor = Color.rgb(0x14, 0x14, 0x15)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 18f }

    private val iconPath = Path().apply {
        moveTo(96f, 369f); lineTo(128f, 369f); lineTo(136f, 331f); lineTo(153f, 421f)
        lineTo(171f, 300f); lineTo(190f, 463f); lineTo(208f, 255f); lineTo(226f, 463f)
        lineTo(244f, 300f); lineTo(263f, 421f); lineTo(280f, 331f); lineTo(289f, 369f)
        lineTo(321f, 369f)
    }
    private val sharePath1 = Path().apply {
        moveTo(25f, 711f); lineTo(22f, 711f); lineTo(22f, 727f); lineTo(40f, 727f)
        lineTo(40f, 711f); lineTo(37f, 711f)
    }
    private val sharePath2 = Path().apply {
        moveTo(31f, 721f); lineTo(31f, 702f)
        moveTo(26f, 707f); lineTo(31f, 702f); lineTo(36f, 707f)
    }
    private val playPath = Path().apply {
        moveTo(202f, 705f); lineTo(218f, 714.5f); lineTo(202f, 724f); close()
    }

    private val pitch = 3f
    private var downX = 0f
    private var downY = 0f
    private var zone = 0
    private var dragStartProgress = 0f

    private fun s() = width / 415f
    private fun headerBottom() = topInset + 41f * s()
    private fun stripTop() = height - bottomInset - 103f * s()

    override fun onDraw(c: Canvas) {
        val s = s()
        val w = width.toFloat()
        val h = height.toFloat()
        val hb = headerBottom()
        val st = stripTop()

        c.drawColor(Color.BLACK)
        fill.color = headerColor
        c.drawRect(0f, 0f, w, hb, fill)
        fill.color = bottomColor
        c.drawRect(0f, st, w, h, fill)

        c.save()
        c.translate(0f, topInset - 30f * s)
        c.scale(s, s)
        text.color = blue
        text.typeface = Typeface.DEFAULT
        text.textAlign = Paint.Align.LEFT
        c.drawText("Done", 18f, 53f, text)
        text.color = Color.WHITE
        text.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        text.textAlign = Paint.Align.CENTER
        c.drawText(title, 207.5f, 53f, text)
        c.restore()

        c.save()
        c.translate(0f, (hb + st) / 2f - 359f * s)
        c.scale(s, s)
        stroke.color = gray
        stroke.strokeWidth = 11f
        c.drawPath(iconPath, stroke)
        c.restore()

        val n = peaks.size
        if (n > 0) {
            val cur = progress * n
            val cy = st + 25f * s
            val first = floor(cur - 207.5f / pitch - 1).toInt()
            val last = (cur + 207.5f / pitch + 2).toInt()
            fill.color = gray
            for (i in first..last) {
                if (i < 0 || i >= n) continue
                val x = (207.5f + (i - cur) * pitch) * s
                val hh = (3f + peaks[i] * 15f) * s
                c.drawRoundRect(x, cy - hh, x + 2f * s, cy + hh, s, s, fill)
            }
        }
        fill.color = Color.argb(140, 0, 0, 0)
        c.drawRect(195f * s, st, 207f * s, st + 50f * s, fill)
        fill.color = blue
        c.drawRect(206.5f * s, st, 208f * s, st + 50f * s, fill)

        c.save()
        c.translate(0f, st - 636f * s)
        c.scale(s, s)
        stroke.color = blue
        stroke.strokeWidth = 2f
        c.drawPath(sharePath1, stroke)
        c.drawPath(sharePath2, stroke)
        c.drawLine(378f, 707f, 394f, 707f, stroke)
        c.drawLine(378f, 713f, 394f, 713f, stroke)
        c.drawLine(378f, 719f, 394f, 719f, stroke)
        stroke.strokeWidth = 3f
        c.drawLine(372f, 707f, 372.5f, 707f, stroke)
        c.drawLine(372f, 713f, 372.5f, 713f, stroke)
        c.drawLine(372f, 719f, 372.5f, 719f, stroke)
        fill.color = blue
        if (playing) {
            c.drawRoundRect(201f, 706f, 205.5f, 723f, 1.5f, 1.5f, fill)
            c.drawRoundRect(211f, 706f, 215.5f, 723f, 1.5f, 1.5f, fill)
        } else {
            stroke.strokeWidth = 2f
            c.drawPath(playPath, fill)
            c.drawPath(playPath, stroke)
        }
        c.restore()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val s = s()
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x
                downY = e.y
                val st = stripTop()
                zone = when {
                    e.y < headerBottom() -> 1
                    e.y >= st + 50f * s -> 2
                    e.y >= st -> 3
                    else -> 4
                }
                if (zone == 3 && peaks.isNotEmpty()) {
                    dragging = true
                    dragStartProgress = progress
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    val n = peaks.size
                    val deltaBars = (e.x - downX) / (pitch * s)
                    progress = (dragStartProgress - deltaBars / n).coerceIn(0f, 1f)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    dragging = false
                    onSeek?.invoke(progress)
                } else {
                    when (zone) {
                        1 -> if (downX < 120f * s) onDone?.invoke()
                        2 -> when {
                            downX < 100f * s -> onShare?.invoke()
                            downX in 150f * s..265f * s -> onToggle?.invoke()
                            downX > 315f * s -> onList?.invoke()
                        }
                        4 -> {
                            val dx = e.x - downX
                            if (abs(dx) > 100f * s) onSwipe?.invoke(if (dx < 0) 1 else -1)
                        }
                    }
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                return true
            }
        }
        return super.onTouchEvent(e)
    }
}
