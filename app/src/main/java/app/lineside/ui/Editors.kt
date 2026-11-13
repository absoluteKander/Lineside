// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.lineside.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.lineside.data.Event
import app.lineside.data.EventKind
import app.lineside.data.LoopFull
import app.lineside.data.NumberInput

/** New loop, or editing one. Full screen: it's the one form with several fields. */
@Composable
fun LoopEditor(existing: LoopFull?, onCancel: () -> Unit, onSave: (name: String, topic: String, numbers: List<NumberInput>, note: String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(existing?.loop?.name.orEmpty()) }
    var topic by rememberSaveable { mutableStateOf(existing?.loop?.topic.orEmpty()) }
    var note by rememberSaveable { mutableStateOf("") }
    val numbers = remember {
        mutableStateListOf<NumberInput>().apply {
            addAll(existing?.numbers?.map { NumberInput(it.number, it.label) }.orEmpty())
            if (isEmpty()) add(NumberInput(""))
        }
    }
    val canSave = name.isNotBlank()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (existing == null) focus.requestFocus() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCancel) { Icon(LineIcons.Close, "Cancel") }
                Text(if (existing == null) "New loop" else "Edit loop", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Button(onClick = { onSave(name, topic, numbers.toList(), note) }, enabled = canSave) { Text("Save") }
            }
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                OutlinedTextField(
                    name, { name = it }, Modifier.fillMaxWidth().focusRequester(focus),
                    label = { Text("Who") }, placeholder = { Text("Dr. Patel's office") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                OutlinedTextField(
                    topic, { topic = it }, Modifier.fillMaxWidth(),
                    label = { Text("About (optional)") }, placeholder = { Text("Blood work follow-up") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                SectionLabel("Numbers")
                numbers.forEachIndexed { i, n ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            n.number, { numbers[i] = n.copy(number = it) }, Modifier.weight(1.2f),
                            label = { Text("Number") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        )
                        OutlinedTextField(
                            n.label, { numbers[i] = n.copy(label = it) }, Modifier.weight(1f),
                            label = { Text("Label") }, placeholder = { Text("front desk") }, singleLine = true,
                        )
                        if (numbers.size > 1) IconButton(onClick = { numbers.removeAt(i) }) { Icon(LineIcons.Close, "Remove number") }
                    }
                }
                TextButton(onClick = { numbers.add(NumberInput("")) }) {
                    Icon(LineIcons.Plus, null); Text("  Add another number")
                }
                Text(
                    "Offices often call from a different number than the one you dial. Add both and Lineside will recognise either.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (existing == null) OutlinedTextField(
                    note, { note = it }, Modifier.fillMaxWidth(), minLines = 2,
                    label = { Text("First note (optional)") }, placeholder = { Text("They said results take 3–5 days") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            }
        }
    }
}

@Composable
fun NoteSheet(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Add a note", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                text, { text = it }, Modifier.fillMaxWidth().focusRequester(focus), minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(onClick = { onSave(text) }, enabled = text.isNotBlank()) { Text("Save note") }
            }
        }
    }
}

private val logKinds = listOf(
    EventKind.MISSED to "Missed call", EventKind.VOICEMAIL to "Voicemail", EventKind.TEXT to "Text",
    EventKind.CALL_IN to "They called", EventKind.CALL_OUT to "I called",
)
private val whens = listOf(0L to "Just now", 60 * 60_000L to "An hour ago", 3 * 60 * 60_000L to "Earlier today", 24 * 60 * 60_000L to "Yesterday")

/** Logging by hand: for anyone who skipped the permissions, or a call made from another phone. */
@Composable
fun LogSheet(numbers: List<Pair<String, String>>, onDismiss: () -> Unit, onSave: (kind: EventKind, agoMs: Long, number: String?, text: String) -> Unit) {
    var kind by rememberSaveable { mutableStateOf(EventKind.MISSED) }
    var ago by rememberSaveable { mutableStateOf(0L) }
    var number by rememberSaveable { mutableStateOf(numbers.firstOrNull()?.first) }
    var text by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Log a call or message", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((k, label) in logKinds) FilterChip(kind == k, { kind = k }, { Text(label) })
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((ms, label) in whens) FilterChip(ago == ms, { ago = ms }, { Text(label) })
            }
            if (numbers.size > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Names, not digits: the last four tell unnamed numbers apart.
                for ((n, label) in numbers) FilterChip(number == n, { number = n }, { Text(label.ifBlank { Fmt.last4(n) }) })
            }
            val hint = when (kind) {
                EventKind.VOICEMAIL -> "What they said (optional)"
                EventKind.TEXT -> "The message (optional)"
                else -> "Anything worth remembering (optional)"
            }
            OutlinedTextField(
                text, { text = it }, Modifier.fillMaxWidth(), minLines = 2, label = { Text(hint) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(onClick = { onSave(kind, ago, number, text) }) { Text("Log it") }
            }
        }
    }
}

/** Tapping a timeline item: see it in full, or delete it. */
@Composable
fun EventSheet(event: Event, onDismiss: () -> Unit, onDelete: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${Fmt.kind(event.kind)} · ${Fmt.dateTime(event.at)}", style = MaterialTheme.typography.titleMedium)
            if (event.text.isNotBlank()) Text(event.text)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDelete) { Icon(LineIcons.Trash, null); Text("  Delete") }
                Button(onClick = onDismiss) { Text("Done") }
            }
        }
    }
}

/** Closing removes everything Lineside holds for the loop. Say exactly that. */
@Composable
fun CloseLoopDialog(full: LoopFull, onDismiss: () -> Unit, onConfirm: () -> Unit) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Close ${full.loop.name}?") },
    text = {
        Text(
            "This removes the loop and everything in it: its calls, notes and messages, and its entry in your contacts. " +
                "Your phone's own call history isn't touched.",
        )
    },
    confirmButton = { Button(onClick = onConfirm) { Text("Close loop", fontWeight = FontWeight.SemiBold) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Keep it") } },
)

