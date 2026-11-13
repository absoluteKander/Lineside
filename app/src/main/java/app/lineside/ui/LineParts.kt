// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lineside.data.Event
import app.lineside.data.EventKind
import app.lineside.domain.Ball
import app.lineside.domain.Initials
import app.lineside.domain.LoopStatus
import app.lineside.domain.photoStatus

/** Signage text: condensed, upper case, a little tracking. */
@Composable
fun Sign(text: String, size: TextUnit = 16.sp, color: Color = Line.c.ink, modifier: Modifier = Modifier, weight: FontWeight = FontWeight.Bold, maxLines: Int = Int.MAX_VALUE) =
    Text(text.uppercase(), modifier, color = color, fontFamily = Type.Signage, fontWeight = weight, fontSize = size, letterSpacing = 0.07.em(size),
        maxLines = maxLines, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)

private fun Double.em(size: TextUnit) = (size.value * this).sp

/** A loop's route bullet: its initials in its colour, ringed like a transit line symbol. */
@Composable
fun RouteBullet(id: Long, name: String, size: Dp = 40.dp) {
    val color = Brand.loop(id, Line.c.dark)
    Box(
        Modifier.size(size + 8.dp).padding(4.dp)
            .drawBehind { drawCircle(color, radius = this.size.minDimension / 2 + 4.dp.toPx(), style = Stroke(2.dp.toPx())) }
            .background(color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(Initials.of(name), color = Color.White, fontFamily = Type.Signage, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.52f).sp)
    }
}

/** Status lamp: amber "Call back" (your move) or green "Waiting" (theirs). */
@Composable
fun Lamp(ball: Ball, onDark: Boolean = false) {
    val c = Line.c
    val (label, lamp, wash, ink) = when (ball) {
        Ball.CALL_BACK -> Quad("Call back", c.amber, if (onDark) Color.Transparent else c.amberWash, if (onDark) c.amber else c.amberInk)
        Ball.WAITING -> Quad("Waiting", c.waiting, if (onDark) Color.Transparent else c.waitingWash, if (onDark) c.waiting else c.waitingInk)
    }
    Row(
        Modifier.background(wash, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).drawBehind {
            if (ball == Ball.CALL_BACK) drawCircle(lamp.copy(alpha = 0.35f), radius = size.minDimension)
            drawCircle(lamp)
        })
        Spacer(Modifier.width(6.dp))
        Sign(label, 14.sp, ink)
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

/** The shape language: circle = call, squircle = something recorded, diamond = your note. */
enum class StopShape { CALL_THEM, CALL_YOU, RECORDED, NOTE }

fun stopShape(e: Event) = when (e.kind) {
    EventKind.CALL_IN, EventKind.MISSED -> StopShape.CALL_THEM
    EventKind.CALL_OUT -> StopShape.CALL_YOU
    EventKind.VOICEMAIL, EventKind.TEXT -> StopShape.RECORDED
    EventKind.NOTE -> StopShape.NOTE
}

/** Draws one stop centred at [c]. [needsYou] paints it amber; [hole] is the colour behind hollow shapes. */
fun DrawScope.drawStop(shape: StopShape, c: Offset, d: Float, line: Color, hole: Color, amber: Color, needsYou: Boolean, noteFill: Color = hole) {
    val fill = if (needsYou) amber else line
    val r = d / 2
    when (shape) {
        StopShape.CALL_THEM -> {
            if (needsYou) drawCircle(amber.copy(alpha = 0.35f), r * 1.7f, c)
            drawCircle(fill, r, c)
        }
        StopShape.CALL_YOU -> {
            drawCircle(hole, r, c)
            drawCircle(if (needsYou) amber else line, r - d * 0.12f, c, style = Stroke(d * 0.24f))
        }
        StopShape.RECORDED -> {
            if (needsYou) drawRoundRect(amber.copy(alpha = 0.35f), Offset(c.x - r * 1.6f, c.y - r * 1.6f), Size(r * 3.2f, r * 3.2f), CornerRadius(r * 0.9f))
            drawRoundRect(fill, Offset(c.x - r, c.y - r), Size(d, d), CornerRadius(d * 0.3f))
        }
        StopShape.NOTE -> {
            val s = r * 0.85f
            val p = Path().apply { moveTo(c.x, c.y - s); lineTo(c.x + s, c.y); lineTo(c.x, c.y + s); lineTo(c.x - s, c.y); close() }
            drawPath(p, noteFill)
            drawPath(p, line, style = Stroke(d * 0.16f))
        }
    }
}

/** The stop that needs you: the latest thing they did, when it's your move. */
fun needsYouEvent(events: List<Event>): Event? =
    if (LoopStatus.ball(events) == Ball.CALL_BACK) LoopStatus.lastContact(events) else null

/**
 * A loop's timeline in miniature, for list cards: its last few stops along its line, the one that needs you
 * glowing amber. The status at the right end is the only text.
 */
@Composable
fun MiniTimeline(id: Long, events: List<Event>, now: Long, modifier: Modifier = Modifier) {
    val c = Line.c
    val color = Brand.loop(id, c.dark)
    val recent = events.sortedBy { it.at }.takeLast(6)
    val needs = needsYouEvent(events)
    val status = LoopStatus.photoStatus(events, now).uppercase()
    val described = if (recent.isEmpty()) "No activity yet" else "Recent activity: " + recent.joinToString(", ") { Fmt.kind(it.kind) }
    Row(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = described }, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.weight(1f).height(22.dp)) {
            val y = size.height / 2
            val pad = 9.dp.toPx()
            drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 4.dp.toPx())
            if (recent.isEmpty()) return@Canvas
            val step = if (recent.size == 1) 0f else (size.width - pad * 2) / (recent.size - 1)
            recent.forEachIndexed { i, e ->
                val x = if (recent.size == 1) size.width - pad else pad + step * i
                drawStop(stopShape(e), Offset(x, y), 14.dp.toPx(), color, c.surface, c.amber, needsYou = e.id == needs?.id)
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(status, fontFamily = Type.Mono, fontSize = 11.sp, color = if (needs != null) c.amberInk else c.muted)
    }
}

/** "4 days ago" in the same small mono used for times. */
@Composable
fun MonoLabel(text: String, color: Color = Line.c.muted, size: TextUnit = 11.sp) =
    Text(text, fontFamily = Type.Mono, fontSize = size, color = color, style = TextStyle(fontWeight = FontWeight.Medium))


/**
 * The one button style: hollow (outlined in [edge]) at rest, filled amber while pressed or [active].
 * Hollow means "you can press this"; amber means "your attention is here".
 */
@Composable
fun LineButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier, edge: Color = Line.c.ink,
    active: Boolean = false, enabled: Boolean = true, height: Dp = 44.dp, textSize: TextUnit = 16.sp,
    /** Solid backing for buttons that float over content; still reads as hollow (same as the page). */
    backing: Color = Color.Transparent,
    leading: (@Composable (ink: Color) -> Unit)? = null,
) {
    val c = Line.c
    val source = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val filled = (pressed || active) && enabled
    Row(
        modifier
            .height(height)
            .background(if (filled) c.amber else backing, RoundedCornerShape(10.dp))
            .border(1.5.dp, if (filled) c.amber else if (enabled) edge else c.muted, RoundedCornerShape(10.dp))
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        val ink = if (filled) c.onAmber else if (enabled) edge else c.muted
        leading?.let { it(ink); Spacer(Modifier.width(8.dp)) }
        Sign(text, textSize, ink)
    }
}


/** The line carrying on: a solid stretch, then dashes. For "Keep". */
@Composable
fun KeepGlyph(ink: Color) = Canvas(Modifier.size(width = 20.dp, height = 14.dp)) {
    val y = size.height / 2; val w = 3.dp.toPx()
    drawLine(ink, Offset(0f, y), Offset(size.width * 0.45f, y), strokeWidth = w)
    drawLine(ink, Offset(size.width * 0.55f, y), Offset(size.width, y), strokeWidth = w,
        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
}

/** The line ending at the terminus bar. For "Close loop". */
@Composable
fun TerminusGlyph(ink: Color) = Canvas(Modifier.size(width = 20.dp, height = 14.dp)) {
    val y = size.height / 2; val w = 3.dp.toPx()
    drawLine(ink, Offset(0f, y), Offset(size.width - w, y), strokeWidth = w)
    drawLine(ink, Offset(size.width - w / 2, 0f), Offset(size.width - w / 2, size.height), strokeWidth = w)
}

/**
 * The Lineside wordmark: the name in signage type riding on its own line. The line leaves a hollow station stop,
 * runs under the word and a little past it, and ends in the note stop's diamond.
 * Not amber: amber is only ever "needs you".
 */
@Composable
fun Wordmark(ink: Color, hole: Color, modifier: Modifier = Modifier, size: TextUnit = 34.sp) {
    Column(modifier.width(androidx.compose.foundation.layout.IntrinsicSize.Max).semantics(mergeDescendants = true) {}) {
        Sign("Lineside", size, ink, Modifier.padding(start = 3.dp, end = 26.dp))
        // Caps leave the font's descender space empty: tuck the line up under the letters.
        Canvas(Modifier.fillMaxWidth().height(18.dp).offset(y = (-5).dp)) {
            val y = this.size.height / 2
            val stroke = 6.dp.toPx()
            val r = 7.dp.toPx()
            val s = 9.dp.toPx() // half the diamond's width
            val cx = r + stroke / 2
            val dx = this.size.width - s
            drawLine(ink, Offset(cx, y), Offset(dx, y), strokeWidth = stroke)
            // The note stop's diamond: its leading half points the line onward.
            drawPath(Path().apply { moveTo(dx, y - s); lineTo(dx + s, y); lineTo(dx, y + s); lineTo(dx - s, y); close() }, ink)
            drawCircle(hole, r, Offset(cx, y))
            drawCircle(ink, r, Offset(cx, y), style = Stroke(stroke * 0.8f))
        }
    }
}
