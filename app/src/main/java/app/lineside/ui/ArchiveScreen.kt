// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lineside.data.LoopFull
import app.lineside.domain.ArchiveSearch
import app.lineside.domain.LoopStatus

/** Archived loops, newest first, with one search box over everything in them. */
@Composable
fun ArchiveScreen(archived: List<LoopFull>?, onBack: () -> Unit, onOpen: (LoopFull) -> Unit) {
    val c = Line.c
    var query by rememberSaveable { mutableStateOf("") }
    val hits = remember(archived, query) { archived.orEmpty().mapNotNull { f -> ArchiveSearch.match(f, query)?.let { f to it } } }
    Column(Modifier.fillMaxSize().background(c.ground).imePadding()) {
        ScreenBand("Archive", onBack)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text(
                    "Loops you archived instead of closing. Lineside doesn't recognise their numbers or add their texts " +
                        "until you restore them.",
                    fontSize = 13.sp, lineHeight = 18.sp, color = c.muted,
                )
            }
            if (!archived.isNullOrEmpty()) item {
                PlateField(
                    "Search", query, { query = it }, c.ink, "Name, number, or anything in a note or text", 24, singleLine = true,
                    keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                )
            }
            if (archived != null && archived.isEmpty()) item { Text("Nothing archived.", color = c.body) }
            if (archived.orEmpty().isNotEmpty() && hits.isEmpty()) item { Text("Nothing matches \"$query\".", color = c.body) }
            items(hits, key = { it.first.loop.id }) { (f, hit) -> ArchivedRow(f, hit) { onOpen(f) } }
        }
    }
}

@Composable
private fun ArchivedRow(f: LoopFull, hit: String, onClick: () -> Unit) {
    val c = Line.c
    Row(
        Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top,
    ) {
        RouteBullet(f.loop.id, f.loop.name, 34.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(f.loop.name, Modifier.weight(1f), fontFamily = Type.Signage, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, color = c.ink,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                f.loop.archivedAt?.let { MonoLabel("ARCHIVED " + Fmt.date(it).uppercase()) }
            }
            if (f.loop.topic.isNotBlank()) Text(f.loop.topic, fontSize = 13.sp, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(hit, fontSize = 13.sp, lineHeight = 18.sp, color = c.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** What's in an archived loop, and the two ways out: back into the list, or gone for good. */
@Composable
fun ArchivedSheet(full: LoopFull, onDismiss: () -> Unit, onRestore: () -> Unit, onDelete: () -> Unit) {
    val c = Line.c
    val lineColor = Brand.loop(full.loop.id, c.dark)
    var confirm by remember { mutableStateOf(false) }
    LineSheet(onDismiss) {
        SheetBand(full.loop, "Archived")
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (full.loop.topic.isNotBlank()) Text(full.loop.topic, fontSize = 15.sp, color = c.body)
            Text(
                listOfNotNull(
                    "Started ${Fmt.date(full.loop.createdAt)}",
                    "last activity ${Fmt.date(full.loop.lastActivityAt)}",
                    full.loop.archivedAt?.let { "archived ${Fmt.date(it)}" },
                ).joinToString(", "),
                fontSize = 13.sp, color = c.muted,
            )
            if (full.events.isNotEmpty()) Text(LoopStatus.counts(full.events), fontSize = 13.sp, color = c.muted)
            for (n in full.numbers.sortedBy { it.id }) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(n.label.ifBlank { "Unnamed" }, fontFamily = Type.Signage, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = c.ink)
                PhoneLabel(n.number)
            }
            if (!confirm) {
                Text(
                    "Restoring puts it back in your loops and contacts with everything in it. Texts stay off until you switch them on again.",
                    fontSize = 14.sp, lineHeight = 20.sp, color = c.body,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LineButton("Delete forever", { confirm = true }, Modifier.weight(1f), edge = c.muted, leading = { ink -> TerminusGlyph(ink) })
                    LineButton("Restore", onRestore, Modifier.weight(1f), edge = lineColor, leading = { ink -> KeepGlyph(ink) })
                }
            } else {
                Text(
                    "This removes ${full.loop.name} and everything in it for good. Lineside can't get it back.",
                    fontSize = 14.sp, lineHeight = 20.sp, color = c.body,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LineButton("Cancel", { confirm = false }, Modifier.weight(1f), edge = c.muted)
                    LineButton("Delete forever", onDelete, Modifier.weight(1f), edge = lineColor, leading = { ink -> TerminusGlyph(ink) })
                }
            }
        }
    }
}
