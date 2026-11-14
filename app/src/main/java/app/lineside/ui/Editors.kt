// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lineside.data.Event
import app.lineside.data.LoopFull
import app.lineside.data.NumberInput
import app.lineside.data.NumberKey
import app.lineside.domain.Initials

/**
 * The signage band that opens every full screen: a back or close control, a small sign saying what this screen is,
 * and an optional title under it. Dark in both themes, so the status-bar icons are always light.
 */
@Composable
internal fun ScreenBand(
    sign: String, onBack: () -> Unit, backIsClose: Boolean = false, title: String? = null,
    leading: (@Composable () -> Unit)? = null, trailing: (@Composable () -> Unit)? = null,
) {
    val c = Line.c
    Row(
        Modifier.fillMaxWidth().background(c.header).statusBarsPadding().padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(if (backIsClose) LineIcons.Close else LineIcons.Back, if (backIsClose) "Cancel" else "Back", tint = c.headerInk)
        }
        leading?.let { it(); Spacer(Modifier.width(8.dp)) }
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
            if (title == null) Sign(sign, 26.sp, c.headerInk, maxLines = 1)
            else {
                Sign(sign, 13.sp, c.headerMuted, maxLines = 1)
                Text(title, fontFamily = Type.Signage, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = c.headerInk, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
        trailing?.invoke()
    }
}

/** A route bullet for a loop that doesn't exist yet: its initials appear as you type the name. */
@Composable
private fun DraftBullet(name: String, color: Color) {
    val c = Line.c
    Box(
        Modifier.size(38.dp).padding(4.dp).background(color, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Text(Initials.of(name.ifBlank { "?" }), color = c.headerInk, fontFamily = Type.Signage, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
}

/** New loop, or editing one. Full screen: it's the one form with several fields. */
@Composable
fun LoopEditor(existing: LoopFull?, onCancel: () -> Unit, onSave: (name: String, topic: String, numbers: List<NumberInput>, note: String) -> Unit) {
    val c = Line.c
    var name by rememberSaveable { mutableStateOf(existing?.loop?.name.orEmpty()) }
    var topic by rememberSaveable { mutableStateOf(existing?.loop?.topic.orEmpty()) }
    var note by rememberSaveable { mutableStateOf("") }
    val numbers = remember {
        mutableStateListOf<NumberInput>().apply {
            addAll(existing?.numbers?.sortedBy { it.id }?.map { NumberInput(Fmt.phone(it.number), it.label) }.orEmpty())
            if (isEmpty()) add(NumberInput(""))
        }
    }
    val canSave = name.isNotBlank()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (existing == null) focus.requestFocus() }
    // A new loop has no colour yet; it gets one when saved.
    val lineColor = existing?.let { Brand.loop(it.loop.id, c.dark) } ?: c.muted

    Column(Modifier.fillMaxSize().background(c.ground).navigationBarsPadding().imePadding()) {
        ScreenBand(
            sign = if (existing == null) "New loop" else "Edit loop",
            title = name.ifBlank { if (existing == null) "Who are you waiting on?" else " " },
            onBack = onCancel, backIsClose = true,
            leading = {
                if (existing != null) RouteBullet(existing.loop.id, name.ifBlank { existing.loop.name }, 30.dp)
                else DraftBullet(name, c.headerMuted.copy(alpha = 0.5f))
            },
            trailing = {
                LineButton("Save", { onSave(name, topic, numbers.toList(), note) }, enabled = canSave, edge = c.headerInk)
            },
        )
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PlateField(
                "Who", name, { name = it }, lineColor, "Dr Patel's office", 0, focus, singleLine = true,
                keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                textStyle = TextStyle(fontFamily = Type.Signage, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 28.sp),
            )
            PlateField("About (optional)", topic, { topic = it }, lineColor, "Blood work follow-up", 0, singleLine = true)

            Sign("Numbers", 13.sp, c.muted, Modifier.padding(start = 4.dp, top = 8.dp))
            Column(Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(12.dp)).padding(vertical = 4.dp)) {
                numbers.forEachIndexed { i, n ->
                    if (i > 0) Box(Modifier.padding(start = 52.dp).fillMaxWidth().height(1.dp).background(c.divider))
                    NumberRow(
                        n, lineColor,
                        onNumber = { numbers[i] = n.copy(number = it) },
                        onLabel = { numbers[i] = n.copy(label = it) },
                        onRemove = if (numbers.size > 1) ({ numbers.removeAt(i) }) else null,
                    )
                }
            }
            LineButton("Add another number", { numbers.add(NumberInput("")) }, Modifier.fillMaxWidth(), edge = lineColor.takeIf { existing != null } ?: c.ink,
                leading = { ink -> Icon(LineIcons.Plus, null, Modifier.size(18.dp), tint = ink) })
            Text(
                "Offices often call from a different number than the one you dial. Add both, name who's on each, and Lineside will recognise either.",
                Modifier.padding(horizontal = 4.dp), fontSize = 13.sp, lineHeight = 18.sp, color = c.muted,
            )
            if (existing == null) {
                Spacer(Modifier.height(4.dp))
                PlateField("First note (optional)", note, { note = it }, lineColor, "They said results take 3 to 5 days", 52)
            }
        }
    }
}

/** One number on one line: a hollow call stop, the number in mono, who's on it, and a remove control. */
@Composable
private fun NumberRow(n: NumberInput, lineColor: Color, onNumber: (String) -> Unit, onLabel: (String) -> Unit, onRemove: (() -> Unit)?) {
    val c = Line.c
    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp).height(56.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(22.dp)) {
            drawCircle(c.surface, size.minDimension / 2)
            drawCircle(lineColor, size.minDimension / 2 - 1.5.dp.toPx(), style = Stroke(3.dp.toPx()))
        }
        Spacer(Modifier.width(16.dp))
        InlineField(n.number, onNumber, "Number", Modifier.weight(1.15f).semantics { contentDescription = "Phone number" },
            TextStyle(fontFamily = Type.Mono, fontSize = 15.sp), KeyboardOptions(keyboardType = KeyboardType.Phone))
        Box(Modifier.padding(horizontal = 10.dp).width(1.dp).height(28.dp).background(c.divider))
        InlineField(n.label, onLabel, "Who's on it", Modifier.weight(1f).semantics { contentDescription = "Who's on this number" },
            TextStyle(fontFamily = Type.Signage, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
            KeyboardOptions(capitalization = KeyboardCapitalization.Words))
        if (onRemove != null) IconButton(onClick = onRemove) { Icon(LineIcons.Close, "Remove number", Modifier.size(18.dp), tint = c.muted) }
        else Spacer(Modifier.width(12.dp))
    }
}

@Composable
private fun InlineField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier, style: TextStyle, keyboard: KeyboardOptions) {
    val c = Line.c
    BasicTextField(
        value, onChange, modifier, singleLine = true,
        textStyle = style.merge(TextStyle(color = c.ink)), cursorBrush = SolidColor(c.amber), keyboardOptions = keyboard,
        decorationBox = { inner -> Box(contentAlignment = Alignment.CenterStart) { if (value.isEmpty()) Text(placeholder, style = style.merge(TextStyle(color = c.muted))); inner() } },
    )
}

/** Tapping a waypoint: see it in full, or delete it. */
@Composable
fun EventSheet(
    full: LoopFull, event: Event, onDismiss: () -> Unit, onDelete: () -> Unit,
    /** Hands the waypoint to the calendar app, with the date found in its text (if any) already set. */
    onCalendar: ((Event, app.lineside.domain.WhenParse.When?) -> Unit)? = null,
) {
    val c = Line.c
    val lineColor = Brand.loop(full.loop.id, c.dark)
    val found = androidx.compose.runtime.remember(event.text) {
        if (event.text.isBlank()) null else app.lineside.domain.WhenParse.find(event.text, java.time.ZonedDateTime.now())
    }
    val who = event.number?.let { num -> full.numbers.firstOrNull { NumberKey.matches(it.number, num) } }
        ?.let { it.label.ifBlank { null } ?: Fmt.last4(it.number) }
    LineSheet(onDismiss) {
        SheetBand(full.loop, Fmt.kind(event.kind))
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(18.dp)) { drawStop(stopShape(event), center, size.minDimension, lineColor, c.ground, c.amber, false, c.surface) }
                Spacer(Modifier.width(10.dp))
                MonoLabel(
                    listOfNotNull(Fmt.dateTime(event.at), event.durationSec?.let(Fmt::duration), event.callOffsetSec?.let { Fmt.duration(it) + " into the call" }, who).joinToString(" · "),
                    c.body, 13.sp,
                )
            }
            if (event.text.isNotBlank()) Text(
                event.text,
                Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(12.dp)).padding(14.dp),
                fontSize = 16.sp, lineHeight = 23.sp, color = c.ink,
            )
            // "MRI next Friday 9am" → a calendar entry, prefilled. The found date is shown so there's no surprise.
            if (onCalendar != null && event.text.isNotBlank()) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LineButton(
                    "Add to calendar", { onCalendar(event, found) }, Modifier.fillMaxWidth(), edge = lineColor,
                    leading = { ink -> Icon(LineIcons.Calendar, null, Modifier.size(18.dp), tint = ink) },
                )
                Text(
                    found?.let { w -> if (w.hasTime) Fmt.day(w.start.toLocalDate()) + ", " + Fmt.time(w.start.toInstant().toEpochMilli()) else Fmt.day(w.start.toLocalDate()) + ", all day" }
                        ?: "No date found in the text; you'll pick one in the calendar.",
                    fontSize = 12.sp, color = c.muted, modifier = Modifier.padding(start = 2.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LineButton("Delete", onDelete, Modifier.weight(1f), edge = c.muted,
                    leading = { ink -> Icon(LineIcons.Trash, null, Modifier.size(18.dp), tint = ink) })
                LineButton("Done", onDismiss, Modifier.weight(1f))
            }
        }
    }
}

/** Closing removes everything Lineside holds for the loop; archiving keeps it out of the way. Say exactly that. */
@Composable
fun CloseLoopDialog(full: LoopFull, onDismiss: () -> Unit, onArchive: () -> Unit, onConfirm: () -> Unit) {
    val c = Line.c
    val lineColor = Brand.loop(full.loop.id, c.dark)
    LineSheet(onDismiss) {
        SheetBand(full.loop, "Close loop")
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // The line meeting its terminus: what closing means.
            Canvas(Modifier.fillMaxWidth().height(24.dp)) {
                val y = size.height / 2; val w = 5.dp.toPx(); val end = size.width - w / 2
                drawLine(lineColor, Offset(0f, y), Offset(end, y), strokeWidth = w)
                drawLine(lineColor, Offset(end, 0f), Offset(end, size.height), strokeWidth = w)
            }
            Text(
                "Closing removes the loop and everything in it: its calls, notes and messages, and its entry in your contacts. " +
                    "Your phone's own call history isn't touched.",
                fontSize = 15.sp, lineHeight = 21.sp, color = c.body,
            )
            Text(
                "Or archive it, if they might come back. Everything is kept, out of the way: it leaves your loops and contacts, " +
                    "Lineside stops recognising its numbers and stops adding texts. Restore it from the Archive any time.",
                fontSize = 15.sp, lineHeight = 21.sp, color = c.body,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LineButton("Keep it", onDismiss, Modifier.weight(1f), edge = c.muted, leading = { ink -> KeepGlyph(ink) })
                LineButton("Archive", onArchive, Modifier.weight(1f), edge = lineColor, leading = { ink -> ArchiveGlyph(ink) })
            }
            LineButton("Close loop", onConfirm, Modifier.fillMaxWidth(), edge = lineColor, leading = { ink -> TerminusGlyph(ink) })
        }
    }
}
