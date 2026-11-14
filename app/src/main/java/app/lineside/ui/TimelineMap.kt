// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lineside.data.Event
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** A waypoint placed on the timeline: evenly spaced, with an extra gap between days so days read as groups. */
data class MapStop(val event: Event, val unit: Float, val day: LocalDate)

object TimelineLayout {
    const val DAY_GAP = 1.2f

    fun stops(events: List<Event>, zone: ZoneId = ZoneId.systemDefault()): List<MapStop> {
        var unit = 0f
        var lastDay: LocalDate? = null
        return events.sortedBy { it.at }.mapIndexed { i, e ->
            val day = Instant.ofEpochMilli(e.at).atZone(zone).toLocalDate()
            if (i > 0) unit += 1f + if (day != lastDay) DAY_GAP else 0f
            lastDay = day
            MapStop(e, unit, day)
        }
    }
}

private val dayFmt = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

/**
 * The loop's timeline, across the dark header. Tap a dot to jump to its waypoint below. When the dots would be
 * closer than a fingertip, a lens appears: the strip under the map is the whole timeline, the amber window is
 * the lens, and the map shows what's under it. Touch the strip to move the lens there, or drag it.
 */
@Composable
fun TimelineMap(
    stops: List<MapStop>,
    lineColor: Color,
    needsYouId: Long?,
    selectedId: Long?,
    hereDay: LocalDate?,
    /** Changes whenever the lens should go back to the latest waypoints (opening the loop, "↑ Latest"). */
    latestKey: Int,
    onTap: (Event) -> Unit,
) {
    val c = Line.c
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val pad = with(density) { 14.dp.toPx() }
        val width = with(density) { maxWidth.toPx() }
        val usable = width - pad * 2
        val minGap = with(density) { 30.dp.toPx() } // a fingertip apart
        val total = stops.lastOrNull()?.unit ?: 0f
        val fits = total * minGap <= usable
        val window = if (fits) total else usable / minGap
        var start by remember(stops.size) { mutableFloatStateOf((total - window).coerceAtLeast(0f)) }
        LaunchedEffect(latestKey, stops.size) { start = (total - window).coerceAtLeast(0f) }
        val scale = when {
            total == 0f -> 0f
            fits -> usable / total
            else -> minGap
        }
        fun xOf(u: Float) = if (total == 0f) width - pad else pad + (u - start) * scale

        Column {
            Canvas(
                Modifier.fillMaxWidth().height(58.dp)
                    .semantics { contentDescription = "Timeline with ${stops.size} waypoints. Tap a dot to jump to its waypoint below." }
                    .pointerInput(stops, start, scale) {
                        detectTapGestures { p ->
                            stops.filter { abs(xOf(it.unit) - p.x) < minGap * 0.8f }
                                .minByOrNull { abs(xOf(it.unit) - p.x) }?.let { onTap(it.event) }
                        }
                    },
            ) {
                val y = 38.dp.toPx()
                drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 6.dp.toPx())
                if (stops.isEmpty()) return@Canvas
                val visible = stops.filter { xOf(it.unit) in -minGap..(size.width + minGap) }
                // "You are here": a white bracket around the day you're reading in the waypoints below.
                val here = visible.filter { it.day == hereDay }
                if (hereDay != null && here.isNotEmpty() && here.size < stops.size) {
                    val l = xOf(here.first().unit) - 16.dp.toPx(); val r = xOf(here.last().unit) + 16.dp.toPx()
                    drawRoundRect(Color.White, Offset(l, y - 17.dp.toPx()), Size(r - l, 34.dp.toPx()), CornerRadius(9.dp.toPx()), style = Stroke(2.dp.toPx()))
                }
                // A date over the first dot of each day, kept on screen and never on top of the one before.
                var lastDay: LocalDate? = null
                var lastRight = Float.NEGATIVE_INFINITY
                for (s in visible) {
                    if (s.day != lastDay) {
                        lastDay = s.day
                        val bright = s.day == hereDay || s.day == stops.last().day
                        val laid = measurer.measure(dayFmt.format(s.day).uppercase(),
                            TextStyle(fontFamily = Type.Mono, fontSize = 10.sp, color = if (bright) c.headerInk else c.headerMuted), softWrap = false, maxLines = 1)
                        val x = (xOf(s.unit) - 8.dp.toPx()).coerceAtMost(size.width - laid.size.width - 4.dp.toPx())
                        if (x < lastRight + 6.dp.toPx()) continue
                        drawText(laid, topLeft = Offset(x, 2.dp.toPx()))
                        lastRight = x + laid.size.width
                    }
                }
                for (s in visible) {
                    val x = xOf(s.unit)
                    val sel = s.event.id == selectedId
                    if (sel) drawCircle(c.amber.copy(alpha = 0.45f), 17.dp.toPx(), Offset(x, y))
                    drawStop(stopShape(s.event), Offset(x, y), (if (sel) 22 else 18).dp.toPx(), lineColor, c.header, c.amber,
                        needsYou = s.event.id == needsYouId, noteFill = Color.White)
                }
            }
            if (!fits) LensStrip(stops, total, window, start, lineColor, needsYouId) { start = it.coerceIn(0f, total - window) }
        }
    }
}

/** The whole timeline in miniature with the amber lens over it. Touch to move the lens there; drag to slide it. */
@Composable
private fun LensStrip(stops: List<MapStop>, total: Float, window: Float, start: Float, lineColor: Color, needsYouId: Long?, onMove: (Float) -> Unit) {
    val c = Line.c
    Canvas(
        Modifier.fillMaxWidth().padding(top = 6.dp).height(34.dp)
            .semantics { contentDescription = "Lens. Touch or drag along this strip to move along the timeline." }
            .pointerInput(total, window) {
                val pad = 10.dp.toPx(); val s = (size.width - pad * 2) / total
                detectTapGestures { p -> onMove((p.x - pad) / s - window / 2) }
            }
            .pointerInput(total, window, start) {
                val pad = 10.dp.toPx(); val s = (size.width - pad * 2) / total
                var pos = start
                detectHorizontalDragGestures(onDragStart = { pos = start }) { _, dx -> pos += dx / s; onMove(pos) }
            },
    ) {
        val pad = 10.dp.toPx(); val s = (size.width - pad * 2) / total; val y = size.height / 2
        drawRoundRect(c.ink.copy(alpha = 0.06f).compositeOver(c.header), size = size, cornerRadius = CornerRadius(10.dp.toPx()))
        drawLine(c.headerMuted.copy(alpha = 0.5f), Offset(pad, y), Offset(size.width - pad, y), strokeWidth = 2.dp.toPx())
        for (st in stops) drawCircle(if (st.event.id == needsYouId) c.amber else lineColor.copy(alpha = 0.9f), 3.dp.toPx(), Offset(pad + st.unit * s, y))
        val l = pad + start * s - 6.dp.toPx(); val w = window * s + 12.dp.toPx()
        drawRoundRect(c.amber.copy(alpha = 0.16f), Offset(l, 2.dp.toPx()), Size(w, size.height - 4.dp.toPx()), CornerRadius(8.dp.toPx()))
        drawRoundRect(c.amber, Offset(l, 2.dp.toPx()), Size(w, size.height - 4.dp.toPx()), CornerRadius(8.dp.toPx()), style = Stroke(2.5.dp.toPx()))
    }
}

private fun Color.compositeOver(bg: Color) = Color(
    red = red * alpha + bg.red * (1 - alpha), green = green * alpha + bg.green * (1 - alpha),
    blue = blue * alpha + bg.blue * (1 - alpha), alpha = 1f,
)
