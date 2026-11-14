// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lineside.data.EventKind
import app.lineside.data.LoopFull
import app.lineside.data.NumberKey
import app.lineside.domain.Shared

/** Exactly what another app handed over, shown so nothing is a mystery. */
data class Arrived(
    val from: String?, val type: String?, val subject: String?, val text: String?,
    val fileName: String?, val fileType: String?, val fileSize: Long?,
)

data class ShareSave(
    /** null: start a new loop called [newName]. */
    val loopId: Long?, val newName: String,
    val kind: EventKind, val agoMs: Long, val number: String?, val text: String, val addNumber: Boolean,
)

private const val NEW = -1L

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShareScreen(arrived: Arrived, guess: Shared.Guess, loops: List<LoopFull>?, onCancel: () -> Unit, onSave: (ShareSave) -> Unit) {
    val c = Line.c
    var kind by rememberSaveable { mutableStateOf(guess.kind) }
    var number by rememberSaveable { mutableStateOf(guess.numbers.firstOrNull()) }
    var ago by rememberSaveable { mutableLongStateOf(0L) }
    var text by rememberSaveable { mutableStateOf(guess.text) }
    var loopId by rememberSaveable { mutableStateOf<Long?>(null) }
    var newName by rememberSaveable { mutableStateOf("") }
    var addNumber by rememberSaveable { mutableStateOf(true) }
    var showRaw by rememberSaveable { mutableStateOf(false) }

    val list = loops.orEmpty().sortedByDescending { it.loop.lastActivityAt }
    fun owns(f: LoopFull, n: String?) = n != null && f.numbers.any { NumberKey.matches(it.number, n) }
    // Pick the loop that owns the shared number, once the loops have loaded.
    LaunchedEffect(loops == null) {
        if (loops != null && loopId == null) loopId = list.firstOrNull { owns(it, number) }?.loop?.id ?: if (list.isEmpty()) NEW else null
    }
    val chosen = list.firstOrNull { it.loop.id == loopId }
    val lineColor = chosen?.let { Brand.loop(it.loop.id, c.dark) } ?: c.ink
    val ready = (chosen != null) || (loopId == NEW && newName.isNotBlank())

    Column(Modifier.fillMaxSize().background(c.ground)) {
        ScreenBand("Shared to Lineside", onCancel, backIsClose = true, title = when (kind) {
            EventKind.VOICEMAIL -> "A voicemail"; EventKind.TEXT -> "A text"; else -> "A note"
        })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Label("What it is")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((k, l) in listOf(EventKind.VOICEMAIL to "Voicemail", EventKind.TEXT to "Text", EventKind.NOTE to "Note")) Pill(l, kind == k) { kind = k }
            }

            Label("From")
            if (guess.numbers.isEmpty()) Text("No phone number came with it.", fontSize = 14.sp, color = c.muted)
            else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (n in guess.numbers) Pill(Fmt.phone(n), number == n) { number = n }
                Pill("Not from these", number == null) { number = null }
            }

            Label("When")
            WhenLine(ago, lineColor) { ago = it }

            Label("Which loop")
            Column(Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(12.dp)).padding(vertical = 4.dp)) {
                if (loops == null) Text("Loading…", Modifier.padding(16.dp), color = c.muted)
                for (f in list) LoopChoice(f, loopId == f.loop.id, matches = owns(f, number) && number != null, number) { loopId = f.loop.id }
                NewLoopChoice(loopId == NEW) { loopId = NEW }
            }
            if (loopId == NEW) PlateField(
                "New loop's name", newName, { newName = it }, c.ink, "e.g. Dr Patel's office", 24, singleLine = true,
                keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
            if (chosen != null && number != null && !owns(chosen, number)) Row(verticalAlignment = Alignment.CenterVertically) {
                Pill("Add ${Fmt.phone(number!!)} to ${chosen.loop.name}", addNumber) { addNumber = !addNumber }
            }

            PlateField(
                when (kind) { EventKind.VOICEMAIL -> "What they said"; EventKind.TEXT -> "The message"; else -> "Your note" } + " (optional)",
                text, { text = it }, lineColor, if (kind == EventKind.VOICEMAIL) "No transcript came with it" else "", 64,
            )

            Raw(arrived, showRaw) { showRaw = !showRaw }
        }
        Row(Modifier.navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LineButton("Cancel", onCancel, Modifier.weight(1f), edge = c.muted)
            LineButton("Add to timeline", {
                onSave(ShareSave(loopId?.takeIf { it != NEW }, newName, kind, ago, number, text, addNumber))
            }, Modifier.weight(1.4f), enabled = ready)
        }
    }
}

@Composable
private fun Label(text: String) = Sign(text, 13.sp, Line.c.muted, Modifier.semantics { heading() })

@Composable
private fun LoopChoice(f: LoopFull, chosen: Boolean, matches: Boolean, number: String?, onClick: () -> Unit) {
    val c = Line.c
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.RadioButton, onClick = onClick).semantics { selected = chosen }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.then(if (chosen) Modifier.border(2.5.dp, c.amber, RoundedCornerShape(50)) else Modifier).padding(2.dp)) {
            RouteBullet(f.loop.id, f.loop.name, 34.dp)
        }
        Column(Modifier.weight(1f)) {
            Text(f.loop.name, fontFamily = Type.Signage, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            when {
                matches && number != null -> MonoLabel("HAS ${Fmt.last4(number)}", c.body)
                f.loop.topic.isNotBlank() -> Text(f.loop.topic, fontSize = 13.sp, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Radio(chosen)
    }
}

@Composable
private fun NewLoopChoice(chosen: Boolean, onClick: () -> Unit) {
    val c = Line.c
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.RadioButton, onClick = onClick).semantics { selected = chosen }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) { Icon(LineIcons.Plus, null, Modifier.size(20.dp), tint = c.ink) }
        Sign("New loop", 19.sp, c.ink, Modifier.weight(1f), FontWeight.SemiBold)
        Radio(chosen)
    }
}

@Composable
private fun Radio(on: Boolean) {
    val c = Line.c
    Canvas(Modifier.size(22.dp)) {
        drawCircle(if (on) c.amber else c.ink, size.minDimension / 2 - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
        if (on) drawCircle(c.amber, size.minDimension / 4)
    }
}

/** Everything the other app sent, as it sent it. */
@Composable
private fun Raw(a: Arrived, open: Boolean, onToggle: () -> Unit) {
    val c = Line.c
    Column(Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(12.dp))) {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onToggle).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Sign("What arrived", 15.sp, c.ink, Modifier.weight(1f))
            Icon(if (open) LineIcons.ChevronUp else LineIcons.ChevronDown, if (open) "Hide" else "Show", Modifier.size(18.dp), tint = c.muted)
        }
        if (open) Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("From app", a.from)
            Field("Type", a.type)
            Field("Subject", a.subject)
            Field("Text", a.text)
            Field("File", listOfNotNull(a.fileName, a.fileType, a.fileSize?.let { "%,d bytes".format(it) }).joinToString(" · ").ifBlank { null })
            if (a.fileName != null || a.fileType?.startsWith("audio/") == true)
                Text("Lineside doesn't keep or play audio. Only the text above goes on the timeline.", fontSize = 13.sp, lineHeight = 18.sp, color = c.muted)
        }
    }
}

@Composable
private fun Field(label: String, value: String?) {
    val c = Line.c
    Column {
        MonoLabel(label.uppercase())
        Text(value ?: "—", fontSize = 13.sp, lineHeight = 18.sp, color = if (value == null) c.muted else c.body)
    }
}

