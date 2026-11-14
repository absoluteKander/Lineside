// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lineside.data.LoopFull
import app.lineside.data.LoopNumber
import app.lineside.data.TextLog

/** One line under the stations on a loop: whether texts come in, and from whom. Opens the Texts sheet. */
@Composable
fun TextsRow(full: LoopFull, lineColor: Color, onClick: () -> Unit) {
    val c = Line.c
    val on = full.numbers.filter { it.textsSince != null }.takeIf { full.loop.textsOn }.orEmpty()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 10.dp)
            .border(1.5.dp, if (on.isEmpty()) c.divider else lineColor, RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClickLabel = "Texts for this loop", onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(LineIcons.Text, null, Modifier.size(18.dp), tint = if (on.isEmpty()) c.muted else c.ink)
        Spacer(Modifier.width(10.dp))
        Sign("Texts", 15.sp, if (on.isEmpty()) c.muted else c.ink)
        Spacer(Modifier.width(10.dp))
        Text(
            if (on.isEmpty()) "Not added" else "Adding from " + on.joinToString(", ") { it.label.ifBlank { Fmt.last4(it.number) } },
            Modifier.weight(1f), fontSize = 13.sp, color = if (on.isEmpty()) c.muted else c.body, maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        Icon(LineIcons.ChevronDown, null, Modifier.size(16.dp), tint = c.muted)
    }
}

/**
 * Texts for one loop: the loop's switch, then one switch per number. Both must be on before Lineside looks
 * at anything, and Android's permission is only asked for at the first number.
 */
@Composable
fun TextsSheet(
    full: LoopFull, granted: Boolean, handingBack: Boolean, canHandBack: Boolean,
    onDismiss: () -> Unit, onLoop: (Boolean) -> Unit, onNumber: (LoopNumber, Boolean) -> Unit,
    onCloseApp: () -> Unit, onLog: () -> Unit,
    onPick: () -> Unit,
) {
    val c = Line.c
    val lineColor = Brand.loop(full.loop.id, c.dark)
    LineSheet(onDismiss) {
        SheetBand(full.loop, "Texts")
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                "Add texts from this loop's numbers to its timeline. Switch on the loop, then each number you want. " +
                    "Only texts that arrive after you switch a number on, and only from that number. " +
                    "Lineside doesn't look at any other messages, and every time it looks is in the text access log.",
                fontSize = 14.sp, lineHeight = 20.sp, color = c.body,
            )
            if (handingBack) HandingBack(onCloseApp)
            Plate {
                SwitchRow("Add texts to ${full.loop.name}", null, full.loop.textsOn, enabled = !handingBack, onChange = onLoop)
                if (full.loop.textsOn) for (n in full.numbers.sortedBy { it.id }) {
                    Box(Modifier.padding(start = 16.dp).fillMaxWidth().heightIn(min = 1.dp).background(c.divider))
                    SwitchRow(
                        n.label.ifBlank { "Unnamed" } + " · " + Fmt.phone(n.number),
                        n.textsSince?.let { "Adding texts since ${Fmt.dateTime(it)}" } ?: "Off",
                        n.textsSince != null, enabled = !handingBack, indent = true,
                    ) { onNumber(n, it) }
                }
                if (full.loop.textsOn && full.numbers.isEmpty())
                    Text("This loop has no numbers yet.", Modifier.padding(16.dp), fontSize = 13.sp, color = c.muted)
            }
            // The one-off way in: see the loop's texts from the last three months and choose. Switches not needed.
            Plate {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Or pick texts by hand: Lineside shows this loop's texts from the last ${app.lineside.Texts.PICK_DAYS} days and adds only the ones you choose. " +
                            "Android asks for text access for that; it's handed back afterwards unless a switch above needs it.",
                        fontSize = 13.sp, lineHeight = 18.sp, color = c.body,
                    )
                    LineButton("Pick texts", onPick, Modifier.fillMaxWidth(), edge = lineColor, enabled = full.numbers.isNotEmpty() && !handingBack)
                }
            }
            AccessLine(granted, handingBack, canHandBack)
            LineButton("Text access log", onLog, Modifier.fillMaxWidth(), edge = lineColor)
        }
    }
}

/** The loop's recent texts, each with a hollow box that fills amber when chosen. Already-added ones are marked. */
@Composable
fun PickTextsSheet(full: LoopFull, picks: List<app.lineside.Texts.Pick>?, onDismiss: () -> Unit, onAdd: (List<app.lineside.Texts.Pick>) -> Unit) {
    val c = Line.c
    val lineColor = Brand.loop(full.loop.id, c.dark)
    var chosen by rememberSaveable { mutableStateOf(setOf<Long>()) }
    LineSheet(onDismiss) {
        SheetBand(full.loop, "Pick texts")
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                picks == null -> Text("Looking…", color = c.muted)
                picks.isEmpty() -> Text("No texts from this loop's numbers in the last ${app.lineside.Texts.PICK_DAYS} days.", color = c.body, lineHeight = 20.sp)
                else -> LazyColumn(Modifier.heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(picks, key = { it.id }) { p ->
                        val on = p.id in chosen
                        Row(
                            Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(10.dp))
                                .toggleable(value = on, enabled = !p.added, role = Role.Checkbox) { chosen = if (it) chosen + p.id else chosen - p.id }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top,
                        ) {
                            Box(
                                Modifier.padding(top = 2.dp).size(22.dp)
                                    .background(if (on) c.amber else Color.Transparent, RoundedCornerShape(5.dp))
                                    .border(2.dp, if (on) c.amber else if (p.added) c.divider else lineColor, RoundedCornerShape(5.dp)),
                                contentAlignment = Alignment.Center,
                            ) { if (on || p.added) Icon(LineIcons.Done, null, Modifier.size(14.dp), tint = if (on) c.onAmber else c.muted) }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row {
                                    Text(p.who, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = if (p.added) c.muted else c.ink)
                                    MonoLabel(if (p.added) "ADDED" else Fmt.dateTime(p.at))
                                }
                                Text(p.body, fontSize = 14.sp, lineHeight = 19.sp, color = if (p.added) c.muted else c.body, maxLines = 3,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LineButton("Cancel", onDismiss, Modifier.weight(1f), edge = c.muted)
                LineButton(
                    if (chosen.isEmpty()) "Add to timeline" else "Add ${chosen.size} to timeline",
                    { onAdd(picks.orEmpty().filter { it.id in chosen }) }, Modifier.weight(1.4f), edge = lineColor, enabled = chosen.isNotEmpty(),
                )
            }
        }
    }
}

/** Android's own answer, read fresh: is the text permission held right now? */
@Composable
fun AccessLine(granted: Boolean, handingBack: Boolean, canHandBack: Boolean) {
    val c = Line.c
    val (lamp, label, detail) = when {
        handingBack -> Triple(c.amber, "Being handed back", "Gone once Lineside closes. Android will ask again next time.")
        granted -> Triple(c.waiting, "Allowed", if (canHandBack) "Handed back to Android as soon as no number is switched on."
            else "This version of Android can't take it back automatically: remove it in Settings → Apps → Lineside → Permissions.")
        else -> Triple(c.muted, "Not allowed", "Android asks when you switch on a number.")
    }
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 6.dp).size(8.dp).drawBehind { drawCircle(lamp) })
        Spacer(Modifier.width(8.dp))
        Column {
            Sign("Android text access: $label", 14.sp, c.ink)
            Text(detail, fontSize = 13.sp, lineHeight = 18.sp, color = c.muted)
        }
    }
}

@Composable
fun HandingBack(onCloseApp: () -> Unit) {
    val c = Line.c
    Column(
        Modifier.fillMaxWidth().background(c.amberWash, RoundedCornerShape(12.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "No number is switched on, so Lineside is handing text access back to Android. " +
                "Android finishes that when Lineside closes. To switch texts on again, reopen Lineside first.",
            fontSize = 14.sp, lineHeight = 20.sp, color = c.body,
        )
        LineButton("Close Lineside now", onCloseApp, Modifier.fillMaxWidth())
    }
}

@Composable
private fun Plate(content: @Composable () -> Unit) =
    Column(Modifier.fillMaxWidth().background(Line.c.surface, RoundedCornerShape(12.dp))) { content() }

@Composable
private fun SwitchRow(title: String, detail: String?, checked: Boolean, enabled: Boolean = true, indent: Boolean = false, onChange: (Boolean) -> Unit) {
    val c = Line.c
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(start = if (indent) 28.dp else 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontFamily = Type.Signage, fontWeight = FontWeight.SemiBold, fontSize = if (indent) 17.sp else 19.sp,
                color = if (enabled) c.ink else c.muted)
            detail?.let { Text(it, fontSize = 13.sp, lineHeight = 18.sp, color = c.muted) }
        }
        Switch(
            checked, onCheckedChange = null, enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = c.amber, checkedThumbColor = c.onAmber, checkedBorderColor = c.amber,
                uncheckedTrackColor = c.surface, uncheckedThumbColor = c.ink, uncheckedBorderColor = c.ink,
            ),
        )
    }
}

/** Every look at the phone's messages, and every change to Android's permission. Newest first. */
@Composable
fun TextLogSheet(lines: List<TextLog>, loopNames: Map<Long, String>, onDismiss: () -> Unit) {
    val c = Line.c
    LineSheet(onDismiss) {
        Row(Modifier.fillMaxWidth().background(c.header).padding(horizontal = 16.dp, vertical = 14.dp)) {
            Column {
                Sign("Texts", 14.sp, c.amber)
                Text("Text access log", fontFamily = Type.Signage, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = c.headerInk)
            }
        }
        Text(
            "Every time Lineside looked at your messages, which numbers it asked about and what it found, and every time " +
                "Android's text permission was given or handed back. Kept for six months; a loop's lines go when you close it.",
            Modifier.padding(16.dp), fontSize = 13.sp, lineHeight = 18.sp, color = c.muted,
        )
        if (lines.isEmpty()) Text("Nothing yet. Lineside hasn't looked at any messages.", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = c.body)
        LazyColumn(Modifier.heightIn(max = 520.dp).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(lines, key = { it.id }) { l ->
                Column(Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(10.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row {
                        Text(l.what, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = c.ink)
                        MonoLabel(Fmt.dateTime(l.at))
                    }
                    l.loopId?.let { id ->
                        Text(
                            listOfNotNull(loopNames[id], l.numbers.ifBlank { null }).joinToString(" · "),
                            fontSize = 13.sp, color = c.body,
                        )
                        Text(
                            when {
                                l.found == 0 -> "No new texts"
                                l.added == l.found -> "${l.found} found, all added"
                                else -> "${l.found} found, ${l.added} new added"
                            },
                            fontSize = 13.sp, color = c.muted,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.size(16.dp))
    }
}
