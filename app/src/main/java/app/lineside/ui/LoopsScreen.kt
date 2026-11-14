// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lineside.data.Event
import app.lineside.data.EventKind
import app.lineside.data.EventSource
import app.lineside.data.LoopFull
import app.lineside.domain.Ago
import app.lineside.domain.Ball
import app.lineside.domain.LoopStatus

@Composable
fun LoopsScreen(
    loops: List<LoopFull>,
    now: Long,
    showContactsHint: Boolean,
    onOpen: (Long) -> Unit,
    onNew: () -> Unit,
    onKeep: (Long) -> Unit,
    onClose: (LoopFull) -> Unit,
    onAllowContacts: () -> Unit,
    onDismissHint: () -> Unit,
    showCallsHint: Boolean,
    onAllowCalls: () -> Unit,
    onDismissCallsHint: () -> Unit,
    onSettings: () -> Unit,
    archivedCount: Int = 0,
    onArchive: () -> Unit = {},
) {
    val c = Line.c
    val (quiet, open) = loops.partition { LoopStatus.isQuiet(it.loop, now) }
    // Your move first, then most recent.
    val sorted = open.sortedWith(compareBy<LoopFull> { LoopStatus.ball(it.events).ordinal }.thenByDescending { it.loop.lastActivityAt })
    val callBack = open.count { LoopStatus.ball(it.events) == Ball.CALL_BACK }
    Box(Modifier.fillMaxSize().background(c.ground)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 112.dp)) {
            item { Header(callBack, open.size - callBack, onSettings) }
            if (showContactsHint) item {
                Hint(
                    "Show loops on the call screen?",
                    "With Contacts access, your phone app shows a loop's name and status when they call. Loop contacts stay in " +
                        "Lineside's own list on this phone and never sync anywhere.",
                    "Allow contacts", onAllowContacts, onDismissHint,
                )
            } else if (showCallsHint) item {
                Hint(
                    "Let Lineside notice calls?",
                    "When a loop calls, Lineside adds a waypoint and can show its context during the call. It only sees the number, " +
                        "never the audio, and never blocks or changes a call.",
                    "Allow", onAllowCalls, onDismissCallsHint,
                )
            }
            if (loops.isEmpty()) item { EmptyState() }
            items(sorted, key = { it.loop.id }) { LoopCard(it, now) { onOpen(it.loop.id) } }
            val recent = recentCalls(loops, now)
            if (recent.isNotEmpty()) {
                item { SectionSign("Recent calls") }
                item { RecentCalls(recent, now, onOpen) }
            }
            if (quiet.isNotEmpty()) item { SectionSign("Quiet · 3+ weeks") }
            items(quiet, key = { it.loop.id }) { QuietRow(it, now, { onOpen(it.loop.id) }, { onKeep(it.loop.id) }, { onClose(it) }) }
            if (archivedCount > 0) item {
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onArchive).padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArchiveGlyph(c.muted)
                    Spacer(Modifier.width(10.dp))
                    Sign("Archive · $archivedCount", 15.sp, c.muted, weight = FontWeight.SemiBold)
                }
            }
        }
        NewLoopButton(onNew, Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(18.dp))
    }
}

@Composable
private fun Header(callBack: Int, waiting: Int, onSettings: () -> Unit) {
    val c = Line.c
    Column(
        Modifier.fillMaxWidth().background(c.header).statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Wordmark(c.headerInk, c.header, Modifier.semantics { heading() })
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSettings) { Icon(LineIcons.Settings, "Settings", tint = c.headerMuted) }
        }
        if (callBack + waiting > 0) Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            if (callBack > 0) LampCount(c.amber, "$callBack to call back", c.headerInk, glow = true)
            if (waiting > 0) LampCount(c.waiting, "$waiting waiting", c.headerMuted, glow = false)
        }
    }
}

@Composable
private fun LampCount(lamp: androidx.compose.ui.graphics.Color, label: String, ink: androidx.compose.ui.graphics.Color, glow: Boolean) =
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).drawBehind { if (glow) drawCircle(lamp.copy(alpha = 0.35f), size.minDimension); drawCircle(lamp) })
        Spacer(Modifier.width(8.dp))
        Sign(label, 16.sp, ink, weight = FontWeight.SemiBold)
    }

@Composable
private fun SectionSign(text: String) =
    Sign(text, 15.sp, Line.c.muted, Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 2.dp).semantics { heading() }, FontWeight.SemiBold)

@Composable
private fun LoopCard(full: LoopFull, now: Long, onClick: () -> Unit) {
    val c = Line.c
    val loop = full.loop
    Column(
        Modifier.padding(horizontal = 16.dp).padding(top = 12.dp).fillMaxWidth()
            .background(c.surface, RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClickLabel = "Open loop", onClick = onClick)
            .padding(start = 12.dp, end = 14.dp, top = 12.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RouteBullet(loop.id, loop.name)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(loop.name, fontFamily = Type.Signage, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 24.sp,
                    color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val sub = loop.topic.ifBlank { full.numbers.firstOrNull()?.number?.let(Fmt::phone).orEmpty() }
                if (sub.isNotBlank()) Text(sub, fontSize = 13.sp, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Lamp(LoopStatus.ball(full.events))
        }
        MiniTimeline(loop.id, full.events, now, Modifier.padding(start = 4.dp))
        LoopStatus.highlight(full.events)?.takeIf { it.text.isNotBlank() }?.let { e ->
            Text(
                if (e.kind == EventKind.NOTE) e.text else "\"${e.text}\"",
                fontSize = 14.sp, lineHeight = 20.sp, color = c.body, maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Calls Lineside noticed this week, one line per loop (most recently called first, at most three). */
private fun recentCalls(loops: List<LoopFull>, now: Long) = loops
    .map { l -> l to l.events.filter { it.source == EventSource.CALL && now - it.at < 7 * LoopStatus.DAY }.sortedBy { it.at } }
    .filter { it.second.isNotEmpty() }
    .sortedByDescending { it.second.last().at }
    .take(3)

@Composable
private fun RecentCalls(rows: List<Pair<LoopFull, List<Event>>>, now: Long, onOpen: (Long) -> Unit) {
    val c = Line.c
    Column(Modifier.padding(horizontal = 16.dp).padding(top = 8.dp).fillMaxWidth().background(c.surface, RoundedCornerShape(14.dp)).padding(vertical = 4.dp)) {
        for ((l, calls) in rows) Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Open loop") { onOpen(l.loop.id) }.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // A short stretch of this loop's line with its recent calls as stops, the one that needs you in amber.
            val line = Brand.loop(l.loop.id, c.dark)
            val needs = needsYouEvent(l.events)
            val shown = calls.takeLast(4)
            Canvas(Modifier.size(width = 64.dp, height = 20.dp)) {
                val y = size.height / 2; val pad = 7.dp.toPx()
                drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 3.dp.toPx())
                val step = if (shown.size == 1) 0f else (size.width - pad * 2) / (shown.size - 1)
                shown.forEachIndexed { i, e ->
                    val x = if (shown.size == 1) size.width - pad else pad + step * i
                    drawStop(stopShape(e), Offset(x, y), 11.dp.toPx(), line, c.surface, c.amber, needsYou = e.id == needs?.id)
                }
            }
            Text(l.loop.name, Modifier.weight(1f), fontFamily = Type.Signage, fontWeight = FontWeight.SemiBold, fontSize = 18.sp,
                color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val n = calls.size
            MonoLabel((if (n == 1) "1 call" else "$n calls") + " · " + Ago.short(now - calls.last().at).uppercase())
        }
    }
}

@Composable
private fun QuietRow(full: LoopFull, now: Long, onClick: () -> Unit, onKeep: () -> Unit, onClose: () -> Unit) {
    val c = Line.c
    Row(
        Modifier.padding(horizontal = 16.dp).padding(top = 10.dp).fillMaxWidth()
            .drawBehind {
                drawRoundRect(c.muted.copy(alpha = 0.5f), cornerRadius = CornerRadius(14.dp.toPx()),
                    style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
            }
            .clickable(role = Role.Button, onClickLabel = "Open loop", onClick = onClick)
            .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(full.loop.name, fontFamily = Type.Signage, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = c.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Quiet " + Ago.long(now - full.loop.lastActivityAt).removeSuffix(" ago"), fontSize = 12.sp, color = c.muted, maxLines = 1)
        }
        LineButton("Keep", onKeep, leading = { KeepGlyph(it) })
        LineButton("Close", onClose, leading = { TerminusGlyph(it) })
    }
}

@Composable
private fun Hint(title: String, body: String, action: String, onAction: () -> Unit, onDismiss: () -> Unit) {
    val c = Line.c
    Column(
        Modifier.padding(horizontal = 16.dp).padding(top = 14.dp).fillMaxWidth().background(c.surface, RoundedCornerShape(14.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, fontFamily = Type.Signage, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = c.ink)
        Text(body, fontSize = 14.sp, lineHeight = 20.sp, color = c.body)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LineButton(action, onAction)
            LineButton("Not now", onDismiss, edge = c.muted)
        }
    }
}

@Composable
private fun NewLoopButton(onNew: () -> Unit, modifier: Modifier) =
    // Hollow like every button (fills amber while pressed); a page-coloured backing keeps it readable over the list.
    LineButton(
        "New loop", onNew, modifier.shadowLift(), height = 56.dp, textSize = 19.sp, backing = Line.c.ground,
        leading = { ink -> Icon(LineIcons.Plus, null, tint = ink, modifier = Modifier.size(20.dp)) },
    )

private fun Modifier.shadowLift() = this.shadow(8.dp, RoundedCornerShape(10.dp), clip = false)

@Composable
private fun EmptyState() {
    val c = Line.c
    Column(Modifier.padding(horizontal = 20.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("No open loops", fontFamily = Type.Signage, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, color = c.ink)
        Text(
            "Waiting on a call from the doctor's office, the lab or a contractor? Start a loop for them. Their calls, voicemails, " +
                "texts and your notes become waypoints on its timeline until you close it.",
            color = c.body, lineHeight = 21.sp,
        )
    }
}
