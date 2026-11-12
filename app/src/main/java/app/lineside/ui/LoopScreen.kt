// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lineside.data.Event
import app.lineside.data.EventKind
import app.lineside.data.LoopFull
import app.lineside.data.LoopNumber
import app.lineside.data.NumberKey
import app.lineside.domain.Day
import app.lineside.domain.LoopStatus
import app.lineside.domain.Timeline
import app.lineside.domain.TimelineItem
import java.time.LocalDate

/** Who a number belongs to within a loop, for loops that have gathered several parties. */
private class Parties(numbers: List<LoopNumber>) {
    private val ordered = numbers.sortedBy { it.id }
    val multi = ordered.size > 1
    fun of(number: String?): Pair<String, Color>? {
        if (!multi || number == null) return null
        val i = ordered.indexOfFirst { NumberKey.matches(it.number, number) }
        if (i < 0) return number to Brand.Muted
        val n = ordered[i]
        return n.label.ifBlank { n.number } to Brand.avatars[i % Brand.avatars.size]
    }
    fun color(n: LoopNumber) = Brand.avatars[ordered.indexOf(n).coerceAtLeast(0) % Brand.avatars.size]
}

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
    val parties = Parties(full.numbers)
    val days = Timeline.group(full.events)
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        item { Header(full, parties, now, onBack, onEdit, onCall, onNote, onLog, onClose) }
        if (days.isEmpty()) item {
            Text("Nothing yet. Calls, voicemails, texts and your notes will show up here, grouped by day.",
                Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(days, key = { it.date.toEpochDay() }) { day -> DaySection(day, parties, onEventClick) }
        item { Box(Modifier.height(32.dp)) }
    }
}

@Composable
private fun Header(
    full: LoopFull, parties: Parties, now: Long, onBack: () -> Unit, onEdit: () -> Unit, onCall: (String) -> Unit,
    onNote: () -> Unit, onLog: () -> Unit, onClose: () -> Unit,
) {
    val loop = full.loop
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
        Column(Modifier.statusBarsPadding().padding(start = 8.dp, end = 16.dp, top = 4.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onBack) { Icon(LineIcons.Back, "Back") }
                Avatar(loop.id, loop.name, 44.dp)
                Column(Modifier.weight(1f).padding(start = 2.dp)) {
                    Text(loop.name, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                    if (loop.topic.isNotBlank()) Text(loop.topic, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BallChip(LoopStatus.ball(full.events))
                IconButton(onClick = onEdit) { Icon(LineIcons.Edit, "Edit loop") }
            }
            if (full.numbers.isNotEmpty()) Column(Modifier.padding(start = 8.dp)) {
                for (n in full.numbers.sortedBy { it.id }) Row(
                    Modifier.fillMaxWidth().clickable { onCall(n.number) }.heightIn(min = 32.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (parties.multi) Box(Modifier.size(8.dp).background(parties.color(n), CircleShape))
                    if (n.label.isNotBlank()) Text(n.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(n.number, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("Shows on the call screen as: ${LoopStatus.statusLine(full.events, now, loop.topic, loop.createdAt)}",
                Modifier.padding(start = 8.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.padding(start = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val first = full.numbers.minByOrNull { it.id }?.number
                ActionTile("Call", LineIcons.Phone, Modifier.weight(1f), filled = true, enabled = first != null) { first?.let(onCall) }
                ActionTile("Note", LineIcons.Note, Modifier.weight(1f), onClick = onNote)
                ActionTile("Log", LineIcons.Log, Modifier.weight(1f), onClick = onLog)
                ActionTile("Close loop", LineIcons.Done, Modifier.weight(1f), onClick = onClose)
            }
        }
    }
}

@Composable
private fun ActionTile(label: String, icon: ImageVector, modifier: Modifier, filled: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val bg = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val fg = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = onClick, enabled = enabled, modifier = modifier.height(56.dp), shape = RoundedCornerShape(14.dp), color = bg, contentColor = fg,
        border = if (filled) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, Modifier.size(18.dp))
            Text(label, fontSize = 12.sp, fontWeight = if (filled) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
        }
    }
}

private fun dayLabel(date: LocalDate): String {
    val today = LocalDate.now()
    val base = Fmt.day(date)
    return when (date) {
        today -> "Today · $base"
        today.minusDays(1) -> "Yesterday · $base"
        else -> base
    }
}

@Composable
private fun DaySection(day: Day, parties: Parties, onEventClick: (Event) -> Unit) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.Bottom) {
            Text(dayLabel(day.date).uppercase(), Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.7.sp, color = Brand.Body)
            Text(day.counts, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(Modifier.padding(vertical = 4.dp)) {
                day.items.forEachIndexed { i, item ->
                    if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = Brand.Ground)
                    when (item) {
                        is TimelineItem.Call -> CallRow(item, parties, onEventClick)
                        is TimelineItem.Texts -> TextsRow(item, parties, onEventClick)
                        is TimelineItem.Single -> SingleRow(item.event, parties, onEventClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun Bubble(kind: EventKind) {
    val (bg, fg) = Fmt.tint(kind)
    Box(Modifier.size(28.dp).background(bg, CircleShape), contentAlignment = Alignment.Center) {
        Icon(Fmt.icon(kind), null, tint = fg, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun PartyLine(party: Pair<String, Color>?) {
    party ?: return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(6.dp).background(party.second, CircleShape))
        Text(party.first, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = party.second)
    }
}

@Composable
private fun CallRow(item: TimelineItem.Call, parties: Parties, onEventClick: (Event) -> Unit) {
    val c = item.call
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().clickable { onEventClick(c) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Bubble(c.kind)
            Column(Modifier.weight(1f)) {
                Text(Fmt.kind(c.kind), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                PartyLine(parties.of(c.number))
            }
            Text(Fmt.time(c.at) + (c.durationSec?.let { " · " + Fmt.duration(it) } ?: ""), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (item.attached.isNotEmpty()) {
            val rule = if (c.kind == EventKind.MISSED) Brand.AmberWash else Brand.TealWash
            Row(Modifier.padding(start = 13.dp).height(IntrinsicSize.Min)) {
                Box(Modifier.width(2.dp).fillMaxHeight().background(rule))
                Column(Modifier.padding(start = 23.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (a in item.attached) Box(Modifier.clickable { onEventClick(a) }) {
                        if (a.kind == EventKind.VOICEMAIL) VoicemailBlock(a, "Left a voicemail") else StampedNote(a)
                    }
                }
            }
        }
    }
}

@Composable
private fun StampedNote(e: Event) = Text(
    buildAnnotatedString {
        e.callOffsetSec?.let { withStyle(SpanStyle(color = Brand.Teal, fontWeight = FontWeight.SemiBold)) { append(Fmt.duration(it)) }; append("  ") }
        append(e.text)
    },
    fontSize = 13.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurface,
)

@Composable
private fun VoicemailBlock(e: Event, title: String) = Column(
    Modifier.fillMaxWidth().background(Color(0xFFFBF7EF), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
) {
    Text(title + (e.durationSec?.let { " · " + Fmt.duration(it) } ?: ""), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Brand.AmberInk)
    if (e.text.isNotBlank()) Text("\"${e.text}\"", fontSize = 13.sp, lineHeight = 19.sp, color = Brand.Body)
}

@Composable
private fun SingleRow(e: Event, parties: Parties, onEventClick: (Event) -> Unit) =
    Row(Modifier.fillMaxWidth().clickable { onEventClick(e) }.padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Bubble(e.kind)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (e.kind == EventKind.VOICEMAIL) {
                PartyLine(parties.of(e.number))
                VoicemailBlock(e, "Voicemail · " + Fmt.time(e.at))
            } else {
                Text("${Fmt.kind(e.kind)} · ${Fmt.time(e.at)}" + (if (e.callOffsetSec != null) " · during a call" else ""),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (e.text.isNotBlank()) Text(e.text, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
    }

@Composable
private fun TextsRow(item: TimelineItem.Texts, parties: Parties, onEventClick: (Event) -> Unit) {
    var open by rememberSaveable(item.at) { mutableStateOf(false) }
    Column {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.heightIn(min = 48.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Bubble(EventKind.TEXT)
            val n = item.texts.size
            Text(if (n == 1) "1 text" else "$n texts", Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            val first = Fmt.time(item.texts.first().at); val last = Fmt.time(item.texts.last().at)
            Text(if (first == last) first else "$first – $last", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(if (open) LineIcons.ChevronUp else LineIcons.ChevronDown, if (open) "Hide texts" else "Show texts", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (open) Column(Modifier.padding(start = 52.dp, end = 14.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (t in item.texts) Column(Modifier.fillMaxWidth().clickable { onEventClick(t) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(Fmt.time(t.at), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PartyLine(parties.of(t.number))
                }
                Text(t.text, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
    }
}
