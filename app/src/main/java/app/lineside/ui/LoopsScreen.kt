// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lineside.data.LoopFull
import app.lineside.domain.Ago
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
) {
    val (quiet, open) = loops.partition { LoopStatus.isQuiet(it.loop, now) }
    // Your move first, then most recent.
    val sorted = open.sortedWith(compareBy<LoopFull> { LoopStatus.ball(it.events).ordinal }.thenByDescending { it.loop.lastActivityAt })
    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNew,
                icon = { Icon(LineIcons.Plus, contentDescription = null) },
                text = { Text("New loop", fontWeight = FontWeight.SemiBold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Lineside", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp))
            }
            if (showContactsHint) item { ContactsHint(onAllowContacts, onDismissHint) }
            if (loops.isEmpty()) item { EmptyState() }
            if (sorted.isNotEmpty()) item { SectionLabel("Open loops", Modifier.padding(start = 4.dp, top = 4.dp)) }
            items(sorted, key = { it.loop.id }) { LoopCard(it, now) { onOpen(it.loop.id) } }
            if (quiet.isNotEmpty()) item { SectionLabel("Quiet for 3+ weeks", Modifier.padding(start = 4.dp, top = 8.dp)) }
            items(quiet, key = { it.loop.id }) { QuietRow(it, now, { onOpen(it.loop.id) }, { onKeep(it.loop.id) }, { onClose(it) }) }
        }
    }
}

@Composable
private fun LoopCard(full: LoopFull, now: Long, onClick: () -> Unit) {
    val loop = full.loop
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(loop.id, loop.name)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(loop.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val sub = loop.topic.ifBlank { full.numbers.firstOrNull()?.number.orEmpty() }
                    if (sub.isNotBlank()) Text(sub, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                BallChip(LoopStatus.ball(full.events))
            }
            LoopStatus.highlight(full.events)?.let { e ->
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Fmt.icon(e.kind), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    val words = if (e.text.isBlank()) "" else " — \"${e.text}\""
                    Text("${Fmt.kind(e.kind)} · ${Ago.long(now - e.at)}$words", fontSize = 13.sp, lineHeight = 18.sp,
                        maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            val counts = LoopStatus.counts(full.events)
            if (counts.isNotEmpty()) Text(counts, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun QuietRow(full: LoopFull, now: Long, onClick: () -> Unit, onKeep: () -> Unit, onClose: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(full.loop.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Last activity ${Ago.long(now - full.loop.lastActivityAt)}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = onKeep, modifier = Modifier.height(44.dp)) { Text("Keep") }
            Spacer(Modifier.size(8.dp))
            Button(
                onClick = onClose, modifier = Modifier.height(44.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Brand.TealWash, contentColor = Brand.Teal),
            ) { Text("Close") }
        }
    }
}

@Composable
private fun ContactsHint(onAllow: () -> Unit, onDismiss: () -> Unit) = Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = Brand.TealWash, contentColor = Brand.Ink),
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Show loops on the call screen?", fontWeight = FontWeight.SemiBold)
        Text(
            "With Contacts access, your phone app shows a loop's name and status when they call. " +
                "Loop contacts are kept in Lineside's own list on this phone and never sync anywhere.",
            fontSize = 13.sp, lineHeight = 18.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onAllow) { Text("Allow Contacts") }
            TextButton(onClick = onDismiss) { Text("Not now") }
        }
    }
}

@Composable
private fun EmptyState() = Column(Modifier.padding(horizontal = 4.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("No open loops", style = MaterialTheme.typography.titleMedium)
    Text(
        "Waiting on a call from the doctor's office, the lab or a contractor? Start a loop for them. " +
            "Their calls, voicemails, texts and your notes collect here until you close it.",
        color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp,
    )
}
