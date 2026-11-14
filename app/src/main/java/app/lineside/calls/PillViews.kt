// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside.calls

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import app.lineside.R
import app.lineside.data.Event
import app.lineside.data.EventKind
import app.lineside.ui.Brand
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The pill's palette and type: the app's signage band, carried onto the call screen. Dark in both themes. */
internal object PillStyle {
    const val BAND = 0xFF12181D.toInt()
    const val QUOTE = 0xFF1E2A32.toInt()
    const val INK = 0xFFFFFFFF.toInt()
    const val BODY = 0xFFDFE6EA.toInt()
    const val MUTED = 0xFF8C98A3.toInt()
    const val SOFT = 0xFFB9C4CC.toInt()
    const val RULE = 0xFF3D4852.toInt()
    const val AMBER = 0xFFFFB547.toInt()
    const val AMBER_SOFT = 0xFFFFD58A.toInt()
    const val WAITING = 0xFF4FC3A1.toInt()

    private var signage: Typeface? = null
    private var signageSemi: Typeface? = null
    private var mono: Typeface? = null
    fun signage(ctx: Context) = signage ?: ctx.resources.getFont(R.font.barlow_condensed_bold).also { signage = it }
    fun signageSemi(ctx: Context) = signageSemi ?: ctx.resources.getFont(R.font.barlow_condensed_semibold).also { signageSemi = it }
    fun mono(ctx: Context) = mono ?: ctx.resources.getFont(R.font.jetbrains_mono_medium).also { mono = it }

    fun night(ctx: Context) = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    /** The loop's line colour, matching what the app shows in the current theme. Unknown callers get a neutral rule. */
    fun loop(ctx: Context, id: Long?) = if (id == null || id <= 0) RULE else Brand.loop(id, night(ctx)).toArgb()

    fun dp(ctx: Context, v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, ctx.resources.displayMetrics)

    /** Signage text: condensed caps with a little tracking. */
    fun sign(tv: TextView, sp: Float, color: Int, semi: Boolean = false) = tv.apply {
        typeface = if (semi) signageSemi(context) else signage(context)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp); setTextColor(color)
        isAllCaps = !semi; letterSpacing = if (semi) 0f else 0.06f
    }

    fun monoText(tv: TextView, sp: Float, color: Int) = tv.apply {
        typeface = mono(context); setTextSize(TypedValue.COMPLEX_UNIT_SP, sp); setTextColor(color)
    }

    /** A hollow button outlined in [edge]; it fills amber while pressed, the way every button in the app does. */
    fun hollow(tv: TextView, edge: Int) = tv.apply {
        val r = dp(context, 10f)
        val stroke = dp(context, 1.5f).toInt()
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), GradientDrawable().apply { cornerRadius = r; setColor(AMBER); setStroke(stroke, AMBER) })
            addState(intArrayOf(), GradientDrawable().apply { cornerRadius = r; setColor(0); setStroke(stroke, edge) })
        }
        setTextColor(ColorStateList(arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf()), intArrayOf(BAND, INK)))
        minHeight = dp(context, 44f).toInt(); gravity = Gravity.CENTER
        isClickable = true; isFocusable = true
    }

    fun day(at: Long): String = SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(at)).uppercase()

    fun kindShort(k: EventKind) = when (k) {
        EventKind.VOICEMAIL -> "VM"; EventKind.MISSED -> "MISSED"; EventKind.TEXT -> "TEXT"
        EventKind.CALL_IN -> "CALLED"; EventKind.CALL_OUT -> "YOU CALLED"; EventKind.NOTE -> "NOTE"
    }
}

/** The app's shape language, drawn with a plain Canvas: circle = call (filled = them, hollow = you), squircle = recorded, diamond = note. */
internal object StopPainter {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()

    fun draw(cv: Canvas, kind: EventKind, cx: Float, cy: Float, d: Float, line: Int, hole: Int, needsYou: Boolean) {
        val r = d / 2
        val fill = if (needsYou) PillStyle.AMBER else line
        p.style = Paint.Style.FILL
        when (kind) {
            EventKind.CALL_IN, EventKind.MISSED -> {
                if (needsYou) { p.color = 0x59FFB547; cv.drawCircle(cx, cy, r * 1.7f, p) }
                p.color = fill; cv.drawCircle(cx, cy, r, p)
            }
            EventKind.CALL_OUT -> {
                p.color = hole; cv.drawCircle(cx, cy, r, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = d * 0.24f; p.color = fill
                cv.drawCircle(cx, cy, r - d * 0.12f, p)
            }
            EventKind.VOICEMAIL, EventKind.TEXT -> {
                if (needsYou) { p.color = 0x59FFB547; rect.set(cx - r * 1.6f, cy - r * 1.6f, cx + r * 1.6f, cy + r * 1.6f); cv.drawRoundRect(rect, r * 0.9f, r * 0.9f, p) }
                p.color = fill
                if (kind == EventKind.TEXT) {
                    // Same bubble as the app's: squircle body, tail at the bottom left (ui/LineParts.bubblePath).
                    val bodyB = cy - r + d * 1.2f * 0.82f
                    rect.set(cx - r, cy - r, cx + r, bodyB); cv.drawRoundRect(rect, d * 0.3f, d * 0.3f, p)
                    cv.drawPath(android.graphics.Path().apply {
                        moveTo(cx - r + d * 0.16f, bodyB - d * 0.2f); lineTo(cx - r + d * 0.52f, bodyB - d * 0.02f); lineTo(cx - r + d * 0.1f, cy - r + d * 1.2f); close()
                    }, p)
                } else { rect.set(cx - r, cy - r, cx + r, cy + r); cv.drawRoundRect(rect, d * 0.3f, d * 0.3f, p) }
            }
            EventKind.NOTE -> diamond(cv, cx, cy, r * 0.85f, PillStyle.INK, line, d * 0.16f)
        }
    }

    fun diamond(cv: Canvas, cx: Float, cy: Float, s: Float, fillColor: Int, edge: Int, stroke: Float) {
        path.reset(); path.moveTo(cx, cy - s); path.lineTo(cx + s, cy); path.lineTo(cx, cy + s); path.lineTo(cx - s, cy); path.close()
        p.style = Paint.Style.FILL; p.color = fillColor; cv.drawPath(path, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = stroke; p.color = edge; cv.drawPath(path, p)
    }

    /** "You are here": the call happening now, a white ring around the band colour, ringed again in the loop's colour. */
    fun now(cv: Canvas, cx: Float, cy: Float, d: Float, line: Int) {
        val r = d / 2
        p.style = Paint.Style.FILL
        p.color = line; cv.drawCircle(cx, cy, r + d * 0.14f, p)
        p.color = PillStyle.INK; cv.drawCircle(cx, cy, r, p)
        p.color = PillStyle.BAND; cv.drawCircle(cx, cy, r - d * 0.16f, p)
    }
}

/** A loop's route bullet: initials on its colour, ringed. Unknown callers get a hollow grey bullet. */
internal class BulletView(ctx: Context, private val sizeDp: Float) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    var color = PillStyle.RULE; private set
    var text = ""; private set
    var hollow = false; private set

    fun bind(color: Int, text: String, hollow: Boolean) {
        this.color = color; this.text = text; this.hollow = hollow; invalidate()
    }

    override fun onMeasure(w: Int, h: Int) {
        val s = PillStyle.dp(context, sizeDp + 4f).toInt()
        setMeasuredDimension(s, s)
    }

    override fun onDraw(cv: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        val ring = PillStyle.dp(context, 1.5f)
        val r = PillStyle.dp(context, sizeDp / 2)
        p.style = Paint.Style.STROKE; p.strokeWidth = ring; p.color = color
        cv.drawCircle(cx, cy, r + ring, p)
        p.style = Paint.Style.FILL; p.color = if (hollow) PillStyle.BAND else color
        cv.drawCircle(cx, cy, r - ring * 0.6f, p)
        p.color = if (hollow) PillStyle.SOFT else PillStyle.INK
        p.typeface = PillStyle.signage(context); p.textSize = r * 1.15f; p.textAlign = Paint.Align.CENTER
        cv.drawText(text, cx, cy - (p.descent() + p.ascent()) / 2, p)
    }
}

/** The pill's mini line: the loop's last three stops, the one that needs you glowing; the call in progress as the last stop. */
internal class MiniLineView(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var line = PillStyle.RULE
    private var stops: List<Event> = emptyList()
    private var needsId: Long? = null
    private var live = false

    fun bind(line: Int, stops: List<Event>, needsId: Long?, live: Boolean) {
        this.line = line; this.stops = stops; this.needsId = needsId; this.live = live
        visibility = if (stops.isEmpty() && !live) GONE else VISIBLE
        invalidate()
    }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(PillStyle.dp(context, 50f).toInt(), PillStyle.dp(context, 16f).toInt())

    override fun onDraw(cv: Canvas) {
        val y = height / 2f
        val d = PillStyle.dp(context, 9f)
        val pad = PillStyle.dp(context, 6f)
        p.color = line; p.strokeWidth = PillStyle.dp(context, 3f); p.strokeCap = Paint.Cap.ROUND
        cv.drawLine(pad / 2, y, width - pad / 2, y, p)
        val shown = stops.takeLast(if (live) 2 else 3)
        val n = shown.size + if (live) 1 else 0
        if (n == 0) return
        val step = if (n == 1) 0f else (width - pad * 2) / (n - 1)
        shown.forEachIndexed { i, e ->
            val x = if (n == 1) width - pad else pad + step * i
            val needs = e.id == needsId
            StopPainter.draw(cv, e.kind, x, y, if (needs) d * 1.2f else d, line, PillStyle.BAND, needs)
        }
        if (live) StopPainter.now(cv, width - pad, y, d, line)
    }
}

/**
 * The card's line: the loop's header in miniature. Recent stops along the loop's line, the one that needs you lit,
 * notes jotted on this call as white diamonds, and this call as the last stop. Labels only where they don't collide.
 */
internal class CardLineView(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG)
    private var line = PillStyle.RULE
    private var stops: List<Event> = emptyList()
    private var needsId: Long? = null
    private var jots = 0
    private var nowLabel: String? = null

    fun bind(line: Int, stops: List<Event>, needsId: Long?, jots: Int, nowLabel: String?) {
        this.line = line; this.stops = stops; this.needsId = needsId; this.jots = jots; this.nowLabel = nowLabel
        visibility = if (stops.isEmpty() && nowLabel == null && jots == 0) GONE else VISIBLE
        contentDescription = describe()
        invalidate()
    }

    private fun describe() = buildString {
        append("This loop's line: ")
        append(stops.joinToString(", ") { PillStyle.day(it.at).lowercase() + " " + PillStyle.kindShort(it.kind).lowercase() })
        if (jots > 0) append(", $jots notes this call")
        nowLabel?.let { append(", $it") }
    }

    override fun onMeasure(w: Int, h: Int) =
        setMeasuredDimension(MeasureSpec.getSize(w), PillStyle.dp(context, 42f).toInt())

    override fun onDraw(cv: Canvas) {
        val d = PillStyle.dp(context, 14f)
        val y = PillStyle.dp(context, 12f)
        val pad = PillStyle.dp(context, 11f)
        p.color = line; p.strokeWidth = PillStyle.dp(context, 4f); p.strokeCap = Paint.Cap.ROUND
        cv.drawLine(pad / 2, y, width - pad / 2, y, p)

        val slots = mutableListOf<Any>()
        val maxStops = (8 - minOf(jots, 3) - if (nowLabel != null) 1 else 0).coerceAtLeast(2)
        val shown = stops.takeLast(maxStops)
        slots.addAll(shown)
        repeat(minOf(jots, 3)) { slots.add(Unit) }
        if (nowLabel != null) slots.add("now")
        if (slots.isEmpty()) return
        val n = slots.size
        val step = if (n == 1) 0f else (width - pad * 2) / (n - 1)
        fun xAt(i: Int) = if (n == 1) width - pad else pad + step * i

        slots.forEachIndexed { i, s ->
            val x = xAt(i)
            when (s) {
                is Event -> { val needs = s.id == needsId; StopPainter.draw(cv, s.kind, x, y, if (needs) d * 1.25f else d, line, PillStyle.BAND, needs) }
                Unit -> StopPainter.diamond(cv, x, y, d * 0.42f, PillStyle.INK, PillStyle.INK, 0f)
                else -> StopPainter.now(cv, x, y, d * 1.3f, line)
            }
        }

        // Labels, most important first; any that would overlap one already placed is dropped.
        tp.typeface = PillStyle.mono(context); tp.textSize = PillStyle.dp(context, 10f)
        val placed = mutableListOf<ClosedFloatingPointRange<Float>>()
        val gap = PillStyle.dp(context, 8f)
        val ty = y + PillStyle.dp(context, 24f)
        fun label(text: String, x: Float, color: Int) {
            val w = tp.measureText(text)
            val left = (x - w / 2).coerceIn(0f, width - w)
            val span = (left - gap)..(left + w + gap)
            if (placed.any { it.start < span.endInclusive && span.start < it.endInclusive }) return
            placed += span; tp.color = color; cv.drawText(text, left, ty, tp)
        }
        nowLabel?.let { label(it, xAt(n - 1), PillStyle.INK) }
        shown.indexOfFirst { it.id == needsId }.takeIf { it >= 0 }?.let { i ->
            val e = shown[i]; label("${PillStyle.kindShort(e.kind)} · ${PillStyle.day(e.at)}", xAt(i), PillStyle.AMBER_SOFT)
        }
        shown.firstOrNull()?.let { label(PillStyle.day(it.at), xAt(0), PillStyle.MUTED) }
        shown.lastOrNull()?.takeIf { shown.size > 1 }?.let { label(PillStyle.day(it.at), xAt(shown.size - 1), PillStyle.MUTED) }
    }
}
