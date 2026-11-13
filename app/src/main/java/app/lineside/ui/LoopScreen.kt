// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

@file:OptIn(ExperimentalFoundationApi::class)

package app.lineside.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.launch

/** Who a number is within a loop. Only matters once a loop has gathered several numbers. */
private class Parties(numbers: List<LoopNumber>) {
    private val ordered = numbers.sortedBy { it.id }
    val multi = ordered.size > 1
    /** The name for this number, or null. [unnamed] is true when the loop has several numbers and this one has no name yet. */
    fun of(number: String?): String? {
        if (!multi || number == null) return null
        return ordered.firstOrNull { NumberKey.matches(it.number, number) }?.label?.ifBlank { null }
    }
    fun unnamed(number: String?) = multi && number != null && of(number) == null
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
    onNameNumber: (number: String, name: String) -> Unit,
) {
    val c = Line.c
    val loop = full.loop
    var naming by remember { mutableStateOf<String?>(null) }
    val lineColor = Brand.loop(loop.id, c.dark)
    val parties = Parties(full.numbers)
    val days = remember(full.events) { Timeline.group(full.events) }
    val stops = remember(full.events) { TimelineLayout.stops(full.events) }
    val needs = needsYouEvent(full.events)
    val scope = rememberCoroutineScope()
    // Opening a loop always lands on the latest: fresh list position and lens every time.
    val list = remember(loop.id) { LazyListState() }
    var selectedId by remember(loop.id) { mutableStateOf<Long?>(null) }
    var latestKey by remember(loop.id) { mutableIntStateOf(0) }
    var pickNumber by remember { mutableStateOf(false) }

    // List layout: [stations] then per day: [plate] [items...]. Map each event to the row that shows it.
    val rowOfEvent = remember(days) {
        val m = mutableMapOf<Long, Int>(); var i = 1
        for (d in days) { i++; for (it in d.items) { eventsOf(it).forEach { e -> m[e.id] = i }; i++ } }
        m
    }
    val dayOfRow = remember(days) {
        val m = mutableListOf<LocalDate?>(days.firstOrNull()?.date)
        for (d in days) { m += d.date; d.items.forEach { _ -> m += d.date } }
        m
    }
    val hereDay by remember(days) { derivedStateOf { dayOfRow.getOrNull(list.firstVisibleItemIndex) } }
    val plateHeight = with(LocalDensity.current) { 46.dp.roundToPx() }

    Column(Modifier.fillMaxSize().background(c.ground)) {
        Column(Modifier.fillMaxWidth().background(c.header).statusBarsPadding().padding(start = 4.dp, end = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(LineIcons.Back, "Back", tint = c.headerInk) }
                RouteBullet(loop.id, loop.name, 34.dp)
                // Tapping the name edits the loop (name, topic, numbers). No separate pencil: it read as a second "Note".
                Column(Modifier.weight(1f).padding(start = 8.dp).clickable(role = Role.Button, onClickLabel = "Edit loop", onClick = onEdit)) {
                    Text(loop.name, fontFamily = Type.Signage, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 25.sp,
                        color = c.headerInk, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
                    if (loop.topic.isNotBlank()) Text(loop.topic, fontSize = 12.sp, color = c.headerMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.padding(end = 12.dp)) { Lamp(LoopStatus.ball(full.events), onDark = true) }
            }
            Box(Modifier.padding(horizontal = 12.dp)) {
                TimelineMap(stops, lineColor, needs?.id, selectedId, if (list.firstVisibleItemIndex == 0) null else hereDay, latestKey) { e ->
                    selectedId = e.id
                    rowOfEvent[e.id]?.let { row -> scope.launch { list.animateScrollToItem(row, -plateHeight) } }
                }
            }
        }

        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = 40.dp)) {
            item {
                Stations(
                    lineColor,
                    onCall = { if (full.numbers.size > 1) pickNumber = true else full.numbers.firstOrNull()?.let { onCall(it.number) } },
                    callEnabled = full.numbers.isNotEmpty(), onNote = onNote, onLog = onLog, onClose = onClose,
                )
            }
            if (days.isEmpty()) item {
                Text("No waypoints yet. Calls, voicemails, texts and your notes will appear here, newest first.",
                    Modifier.padding(20.dp), color = c.muted, lineHeight = 20.sp)
            }
            days.forEachIndexed { di, day ->
                stickyHeader(key = "d" + day.date.toEpochDay()) {
                    val pinnedOld = hereDay == day.date && di > 0 && list.firstVisibleItemIndex > 0
                    DayPlate(day, showLatest = pinnedOld) {
                        selectedId = null
                        scope.launch { list.animateScrollToItem(0) }
                        latestKey++
                    }
                }
                day.items.forEachIndexed { ii, item ->
                    val last = di == days.lastIndex && ii == day.items.lastIndex
                    item(key = "e" + eventsOf(item).first().id) {
                        Waypoint(item, parties, lineColor, needs?.id, selectedId, last, onEventClick) { naming = it }
                    }
                }
            }
        }
    }

    if (pickNumber) AlertDialog(
        onDismissRequest = { pickNumber = false },
        title = { Text("Call which number?", fontFamily = Type.Signage, fontWeight = FontWeight.SemiBold, fontSize = 22.sp) },
        text = {
            Column {
                for (n in full.numbers.sortedBy { it.id }) Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { pickNumber = false; onCall(n.number) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(n.label.ifBlank { "Unnamed" }, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    MonoLabel(Fmt.phone(n.number), size = 13.sp)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { pickNumber = false }) { Text("Cancel") } },
    )

    naming?.let { number -> NameNumberDialog(number, onDismiss = { naming = null }) { name -> naming = null; onNameNumber(number, name) } }
}

/** "Who's on (555) 014-2299?" Names it once; every waypoint from that number picks it up. */
@Composable
private fun NameNumberDialog(number: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Who's on ${Fmt.phone(number)}?", fontFamily = Type.Signage, fontWeight = FontWeight.SemiBold, fontSize = 22.sp) },
        text = {
            androidx.compose.material3.OutlinedTextField(
                name, { name = it }, singleLine = true, placeholder = { Text("Front desk, Radiology, Billing…") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words),
            )
        },
        confirmButton = { LineButton("Save", { onSave(name.trim()) }, enabled = name.isNotBlank()) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The placeholder for a number in this loop that has no name yet: hollow, so it reads as "tap me". */
@Composable
private fun NameChip(onClick: () -> Unit) {
    val c = Line.c
    Row(
        Modifier.border(1.5.dp, c.muted, RoundedCornerShape(6.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { Sign("+ Name this number", 12.sp, c.muted) }
}

private fun eventsOf(item: TimelineItem): List<Event> = when (item) {
    is TimelineItem.Call -> listOf(item.call) + item.attached
    is TimelineItem.Texts -> item.texts
    is TimelineItem.Single -> listOf(item.event)
}

// ---- Stations: the four actions, as stops on a short stretch of the loop's line, ending at the terminus. ----

private enum class StationShape { CIRCLE, DIAMOND, SQUIRCLE, TERMINUS }

@Composable
private fun Stations(lineColor: Color, onCall: () -> Unit, callEnabled: Boolean, onNote: () -> Unit, onLog: () -> Unit, onClose: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Canvas(Modifier.fillMaxWidth().height(48.dp)) {
            // The line runs in from the left and ends at the terminus (the centre of the last column).
            val end = size.width * 7f / 8f
            drawLine(lineColor, Offset(0f, size.height / 2), Offset(end, size.height / 2), strokeWidth = 5.dp.toPx())
        }
        Row(Modifier.fillMaxWidth()) {
            Station("Call", StationShape.CIRCLE, LineIcons.Phone, lineColor, Modifier.weight(1f), callEnabled, onCall)
            Station("Note", StationShape.DIAMOND, LineIcons.Note, lineColor, Modifier.weight(1f), true, onNote)
            Station("Log", StationShape.SQUIRCLE, LineIcons.Log, lineColor, Modifier.weight(1f), true, onLog)
            Station("Close loop", StationShape.TERMINUS, null, lineColor, Modifier.weight(1f), true, onClose)
        }
    }
}

/** Hollow in the loop's colour; fills amber while pressed. The shape is the shape of what it creates. */
@Composable
private fun Station(label: String, shape: StationShape, icon: ImageVector?, lineColor: Color, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    val c = Line.c
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val fill = if (pressed) c.amber else c.ground
    val edge = if (pressed) c.amber else if (enabled) lineColor else c.muted
    Column(
        modifier.clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(48.dp)) {
                val w = 4.dp.toPx()
                when (shape) {
                    StationShape.CIRCLE -> { drawCircle(fill, size.minDimension / 2); drawCircle(edge, size.minDimension / 2 - w / 2, style = Stroke(w)) }
                    StationShape.SQUIRCLE -> {
                        drawRoundRect(fill, cornerRadius = CornerRadius(15.dp.toPx()))
                        drawRoundRect(edge, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), CornerRadius(13.dp.toPx()), style = Stroke(w))
                    }
                    StationShape.DIAMOND -> {
                        val r = size.minDimension / 2 - w / 2; val m = center
                        val p = Path().apply { moveTo(m.x, m.y - r); lineTo(m.x + r, m.y); lineTo(m.x, m.y + r); lineTo(m.x - r, m.y); close() }
                        drawPath(p, fill); drawPath(p, edge, style = Stroke(w, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                    }
                    StationShape.TERMINUS -> {
                        val bw = 16.dp.toPx(); val l = (size.width - bw) / 2
                        drawRoundRect(fill, Offset(l, 0f), Size(bw, size.height), CornerRadius(4.dp.toPx()))
                        drawRoundRect(edge, Offset(l + w / 2, w / 2), Size(bw - w, size.height - w), CornerRadius(3.dp.toPx()), style = Stroke(w))
                    }
                }
            }
            if (icon != null) Icon(icon, null, Modifier.size(20.dp), tint = if (enabled) c.ink else c.muted)
        }
        Sign(label, 15.sp, if (enabled) c.ink else c.muted)
    }
}

// ---- Waypoints ----

private fun plateLabel(date: LocalDate): String {
    val today = LocalDate.now()
    val base = Fmt.day(date)
    val ago = ChronoUnit.DAYS.between(date, today)
    return when (ago) {
        0L -> "Today · $base"
        1L -> "Yesterday · $base"
        else -> "$base · $ago days ago"
    }
}

/** A day's name plate. It sits across the line; when you've scrolled back in time it offers "↑ Latest". */
@Composable
private fun DayPlate(day: Day, showLatest: Boolean, onLatest: () -> Unit) {
    val c = Line.c
    Row(Modifier.fillMaxWidth().background(c.ground).padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 4.dp).height(36.dp)) {
        Row(
            Modifier.weight(1f).fillMaxWidth().height(36.dp)
                .background(c.surface, if (showLatest) RoundedCornerShape(6.dp, 0.dp, 0.dp, 6.dp) else RoundedCornerShape(6.dp))
                .border(2.dp, c.plateEdge, if (showLatest) RoundedCornerShape(6.dp, 0.dp, 0.dp, 6.dp) else RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp).semantics(mergeDescendants = true) { heading() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Sign(plateLabel(day.date), 16.sp, c.ink, Modifier.weight(1f), maxLines = 1)
            // Pinned with "↑ Latest" there's no room for the counts; they show whenever the plate isn't pinned.
            if (!showLatest) { Spacer(Modifier.width(8.dp)); Sign(day.counts, 14.sp, c.muted, weight = FontWeight.SemiBold, maxLines = 1) }
        }
        if (showLatest) Row(
            Modifier.height(36.dp).background(Brand.Ink, RoundedCornerShape(0.dp, 6.dp, 6.dp, 0.dp))
                .border(2.dp, c.plateEdge, RoundedCornerShape(0.dp, 6.dp, 6.dp, 0.dp))
                .clickable(role = Role.Button, onClickLabel = "Jump to the latest waypoint", onClick = onLatest).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(LineIcons.ArrowUp, null, Modifier.size(16.dp), tint = c.amber)
            Spacer(Modifier.width(4.dp))
            Sign("Latest", 16.sp, c.amber)
        }
    }
}

@Composable
private fun Waypoint(item: TimelineItem, parties: Parties, lineColor: Color, needsYouId: Long?, selectedId: Long?, last: Boolean, onEventClick: (Event) -> Unit, onName: (String) -> Unit) {
    val c = Line.c
    val main = eventsOf(item).first()
    val selected = eventsOf(item).any { it.id == selectedId }
    val needs = eventsOf(item).any { it.id == needsYouId }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(end = 16.dp)) {
        // The gutter: the loop's line runs through every row; the oldest waypoint is where it began.
        Box(Modifier.width(56.dp).fillMaxHeight().drawBehind {
            val x = size.width / 2 + 4.dp.toPx(); val my = 30.dp.toPx()
            drawLine(lineColor, Offset(x, 0f), Offset(x, if (last) my else size.height), strokeWidth = 6.dp.toPx())
        }.heightIn(min = 60.dp)) {
            Canvas(Modifier.padding(start = 4.dp).size(52.dp, 60.dp)) {
                val m = Offset(size.width / 2, 30.dp.toPx())
                if (selected) drawCircle(c.amber.copy(alpha = 0.4f), 18.dp.toPx(), m)
                drawCircle(c.ground, 13.dp.toPx(), m)
                drawStop(
                    if (item is TimelineItem.Texts) StopShape.RECORDED else stopShape(main), m,
                    (if (needs || selected) 22 else 18).dp.toPx(), lineColor, c.ground, c.amber, needsYou = needs, noteFill = c.ground,
                )
            }
        }
        Column(
            Modifier.weight(1f).padding(vertical = 6.dp)
                .background(c.surface, RoundedCornerShape(12.dp))
                .then(if (selected) Modifier.border(2.dp, c.amber, RoundedCornerShape(12.dp)) else Modifier)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (item) {
                is TimelineItem.Call -> {
                    TitleRow(Fmt.kind(item.call.kind), parties.of(item.call.number), Fmt.time(item.call.at) + (item.call.durationSec?.let { " · " + Fmt.duration(it) } ?: "")) { onEventClick(item.call) }
                    if (parties.unnamed(item.call.number)) NameChip { onName(item.call.number!!) }
                    for (a in item.attached) Box(Modifier.clickable { onEventClick(a) }) {
                        if (a.kind == EventKind.VOICEMAIL) Voicemail(a) else Stamped(a, lineColor)
                    }
                }
                is TimelineItem.Single -> {
                    val e = item.event
                    TitleRow(Fmt.kind(e.kind), parties.of(e.number), Fmt.time(e.at)) { onEventClick(e) }
                    if (parties.unnamed(e.number) && e.kind != EventKind.NOTE) NameChip { onName(e.number!!) }
                    if (e.kind == EventKind.VOICEMAIL) Voicemail(e, showTitle = false)
                    else if (e.text.isNotBlank()) Text(e.text, fontSize = 14.sp, lineHeight = 20.sp, color = c.body, modifier = Modifier.clickable { onEventClick(e) })
                }
                is TimelineItem.Texts -> Texts(item, parties, onEventClick)
            }
        }
    }
}

@Composable
private fun TitleRow(title: String, party: String?, meta: String, onClick: () -> Unit) {
    val c = Line.c
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Text(listOfNotNull(title, party).joinToString(" · "), Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
            color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        MonoLabel(meta)
    }
}

@Composable
private fun Stamped(e: Event, lineColor: Color) {
    val c = Line.c
    Text(
        buildAnnotatedString {
            e.callOffsetSec?.let { withStyle(SpanStyle(fontFamily = Type.Mono, fontSize = 12.sp, color = lineColor)) { append(Fmt.duration(it)) }; append("  ") }
            append(e.text)
        },
        fontSize = 14.sp, lineHeight = 20.sp, color = c.body,
    )
}

@Composable
private fun Voicemail(e: Event, showTitle: Boolean = true) {
    val c = Line.c
    Column(Modifier.fillMaxWidth().background(c.amberWash, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (showTitle) Sign("Voicemail" + (e.durationSec?.let { " · " + Fmt.duration(it) } ?: ""), 14.sp, c.amberInk)
        if (e.text.isNotBlank()) Text("\"${e.text}\"", fontSize = 14.sp, lineHeight = 20.sp, color = c.body)
    }
}

@Composable
private fun Texts(item: TimelineItem.Texts, parties: Parties, onEventClick: (Event) -> Unit) {
    val c = Line.c
    var open by rememberSaveable(item.at) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = if (open) "Hide texts" else "Show texts") { open = !open }, verticalAlignment = Alignment.CenterVertically) {
        val n = item.texts.size
        Text(if (n == 1) "1 text" else "$n texts", Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = c.ink)
        MonoLabel(Fmt.time(item.texts.first().at))
        Icon(if (open) LineIcons.ChevronUp else LineIcons.ChevronDown, null, Modifier.size(18.dp), tint = c.muted)
    }
    if (open) for (t in item.texts) Column(Modifier.fillMaxWidth().clickable { onEventClick(t) }) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MonoLabel(Fmt.time(t.at))
            parties.of(t.number)?.let { MonoLabel(it.uppercase()) }
        }
        Text(t.text, fontSize = 14.sp, lineHeight = 20.sp, color = c.body)
    }
}
