// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lineside.data.Event
import app.lineside.data.EventKind
import app.lineside.data.LoopFull
import app.lineside.domain.LoopStatus

@Composable
fun LoopScreen(
    full: LoopFull,
    now: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onCall: (String) -> Unit,
    onNote: () -> Unit,
    onLog: () -> Unit,
    onClose: () -> Unit,
    onEventClick: (Event) -> Unit,
) {
    val loop = full.loop
    val events = full.events.sortedByDescending { it.at }
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        item {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
                Column(Modifier.statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) { Icon(LineIcons.Back, "Back") }
                        Box(Modifier.weight(1f))
                        Text(
                            "Loop · opened ${Fmt.date(loop.createdAt)}", color = Brand.AmberInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.background(Brand.AmberWash, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                        IconButton(onClick = onEdit) { Icon(LineIcons.Edit, "Edit loop") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                        Avatar(loop.id, loop.name, 56.dp)
                        Column(Modifier.padding(start = 14.dp)) {
                            Text(loop.name, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                            if (loop.topic.isNotBlank()) Text(loop.topic, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (full.numbers.isNotEmpty()) Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (n in full.numbers) Row(Modifier.clickable { onCall(n.number) }.padding(vertical = 4.dp)) {
                            Text(n.number, fontSize = 14.sp)
                            if (n.label.isNotBlank()) Text(" · ${n.label}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text("Shows on the call screen as: ${LoopStatus.statusLine(full.events, now)}", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val first = full.numbers.firstOrNull()?.number
                        ActionTile("Call", LineIcons.Phone, Modifier.weight(1f), filled = true, enabled = first != null) { first?.let(onCall) }
                        ActionTile("Note", LineIcons.Note, Modifier.weight(1f), onClick = onNote)
                        ActionTile("Log", LineIcons.Log, Modifier.weight(1f), onClick = onLog)
                        ActionTile("Close loop", LineIcons.Done, Modifier.weight(1f), onClick = onClose)
                    }
                }
            }
        }
        item { SectionLabel("Timeline", Modifier.padding(start = 20.dp, top = 16.dp, bottom = 12.dp)) }
        if (events.isEmpty()) item {
            Text("Nothing yet. Calls, voicemails, texts and your notes will show up here.",
                Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(events, key = { it.id }) { e -> TimelineItem(e, last = e == events.last(), onClick = { onEventClick(e) }) }
        item { Box(Modifier.height(32.dp)) }
    }
}

@Composable
private fun ActionTile(label: String, icon: ImageVector, modifier: Modifier, filled: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val bg = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val fg = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = onClick, enabled = enabled, modifier = modifier.height(64.dp), shape = RoundedCornerShape(14.dp), color = bg, contentColor = fg,
        border = if (filled) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, Modifier.size(20.dp))
            Text(label, fontSize = 12.sp, fontWeight = if (filled) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
        }
    }
}

@Composable
private fun TimelineItem(e: Event, last: Boolean, onClick: () -> Unit) {
    val (bg, fg) = Fmt.tint(e.kind)
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.width(32.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(32.dp).background(bg, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Fmt.icon(e.kind), null, tint = fg, modifier = Modifier.size(16.dp))
            }
            if (!last) Box(Modifier.width(2.dp).height(28.dp).background(Brand.Line))
        }
        Column(Modifier.weight(1f).padding(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val meta = buildList {
                add(Fmt.dateTime(e.at))
                e.callOffsetSec?.let { add("during call, ${Fmt.duration(it)}") }
                e.durationSec?.let { add(Fmt.duration(it)) }
            }.joinToString(" · ")
            Row {
                Text(Fmt.kind(e.kind), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(" · $meta", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (e.number != null && e.kind != EventKind.NOTE)
                Text((if (e.kind == EventKind.CALL_OUT) "to " else "from ") + e.number, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (e.text.isNotBlank()) Text(
                if (e.kind == EventKind.VOICEMAIL || e.kind == EventKind.TEXT) "\"${e.text}\"" else e.text,
                fontSize = 13.sp, lineHeight = 19.sp,
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}

