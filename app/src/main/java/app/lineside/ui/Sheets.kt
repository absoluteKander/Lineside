// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

@file:OptIn(ExperimentalMaterial3Api::class)

package app.lineside.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lineside.data.EventKind
import app.lineside.data.Loop
import app.lineside.data.LoopNumber

/** Every sheet opens on the loop's signage band: its route bullet, its name, and what you're doing. */
@Composable
internal fun SheetBand(loop: Loop, action: String) {
    val c = Line.c
    Row(
        Modifier.fillMaxWidth().background(c.header).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RouteBullet(loop.id, loop.name, 30.dp)
        Column(Modifier.padding(start = 8.dp)) {
            Sign(action, 14.sp, c.amber)
            Text(loop.name, fontFamily = Type.Signage, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = c.headerInk, maxLines = 1)
        }
    }
}

@Composable
internal fun LineSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val c = Line.c
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.ground,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
    ) { Column(Modifier.navigationBarsPadding().imePadding()) { content() } }
}

/** A labelled text area: a white plate with a signage label, the loop's colour as its underline. */
@Composable
internal fun PlateField(
    label: String, value: String, onChange: (String) -> Unit, lineColor: Color, placeholder: String, minHeight: Int,
    focus: FocusRequester? = null, singleLine: Boolean = false,
    keyboard: KeyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    textStyle: TextStyle = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
) {
    val c = Line.c
    Column(Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Sign(label, 13.sp, c.muted)
        BasicTextField(
            value, onChange,
            Modifier.fillMaxWidth().heightIn(min = minHeight.dp).let { if (focus != null) it.focusRequester(focus) else it },
            textStyle = textStyle.merge(TextStyle(color = c.ink)),
            cursorBrush = SolidColor(c.amber),
            singleLine = singleLine,
            keyboardOptions = keyboard,
            decorationBox = { inner -> Box { if (value.isEmpty()) Text(placeholder, style = textStyle.merge(TextStyle(color = c.muted))); inner() } },
        )
        Box(Modifier.fillMaxWidth().height(2.dp).background(lineColor))
    }
}

// ---- Note ----

@Composable
fun NoteSheet(loop: Loop, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val c = Line.c
    var text by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LineSheet(onDismiss) {
        SheetBand(loop, "Note")
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PlateField("Your note", text, { text = it }, Brand.loop(loop.id, c.dark), "What did they say? What happens next?", 96, focus)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LineButton("Cancel", onDismiss, Modifier.weight(1f), edge = c.muted)
                LineButton("Add note", { onSave(text) }, Modifier.weight(1f), enabled = text.isNotBlank(),
                    leading = { ink -> Canvas(Modifier.size(14.dp)) { drawStop(StopShape.NOTE, center, size.minDimension, ink, Color.Transparent, ink, false) } })
            }
        }
    }
}

// ---- Log ----

private data class Kind(val kind: EventKind, val label: String, val icon: ImageVector, val shape: StopShape)

private val kinds = listOf(
    Kind(EventKind.MISSED, "Missed", LineIcons.Missed, StopShape.CALL_THEM),
    Kind(EventKind.VOICEMAIL, "Voicemail", LineIcons.Voicemail, StopShape.RECORDED),
    Kind(EventKind.TEXT, "Text", LineIcons.Text, StopShape.RECORDED),
    Kind(EventKind.CALL_IN, "Answered", LineIcons.Incoming, StopShape.CALL_THEM),
    Kind(EventKind.CALL_OUT, "I called", LineIcons.Outgoing, StopShape.CALL_YOU),
)

/** Oldest on the left, like the timeline. */
private val whens = listOf(24 * 60 * 60_000L to "Yesterday", 3 * 60 * 60_000L to "Earlier", 60 * 60_000L to "An hour ago", 0L to "Just now")

/** Logging by hand: for anyone who skipped the permissions, or a call made from another phone. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogSheet(loop: Loop, numbers: List<Pair<String, String>>, onDismiss: () -> Unit, onSave: (kind: EventKind, agoMs: Long, number: String?, text: String) -> Unit) {
    val c = Line.c
    val lineColor = Brand.loop(loop.id, c.dark)
    var kind by rememberSaveable { mutableStateOf(EventKind.MISSED) }
    var ago by rememberSaveable { mutableLongStateOf(0L) }
    var number by rememberSaveable { mutableStateOf(numbers.firstOrNull()?.first) }
    var text by rememberSaveable { mutableStateOf("") }
    LineSheet(onDismiss) {
        SheetBand(loop, "Log a waypoint")
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Sign("What happened", 13.sp, c.muted)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (k in kinds) ShapeChoice(k, kind == k.kind, lineColor) { kind = k.kind }
            }
            Sign("When", 13.sp, c.muted)
            WhenLine(ago, lineColor) { ago = it }
            if (numbers.size > 1) {
                Sign("Who", 13.sp, c.muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((n, label) in numbers) Pill(label.ifBlank { Fmt.last4(n) }, number == n) { number = n }
                }
            }
            val hint = when (kind) {
                EventKind.VOICEMAIL -> "What they said"
                EventKind.TEXT -> "The message"
                else -> "Anything worth remembering"
            }
            PlateField("$hint (optional)", text, { text = it }, lineColor, "", 44)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LineButton("Cancel", onDismiss, Modifier.weight(1f), edge = c.muted)
                LineButton("Add to timeline", { onSave(kind, ago, number, text) }, Modifier.weight(1.4f))
            }
        }
    }
}

/** A waypoint shape as a choice: hollow in the loop's colour, amber when chosen (or pressed). */
@Composable
private fun ShapeChoice(k: Kind, chosen: Boolean, lineColor: Color, onClick: () -> Unit) {
    val c = Line.c
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val on = chosen || pressed
    Column(
        Modifier.width(62.dp).clickable(interactionSource = source, indication = null, role = Role.RadioButton, onClick = onClick)
            .semantics { selected = chosen },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(46.dp)) {
                val w = 3.5.dp.toPx(); val fill = if (on) c.amber else c.surface; val edge = if (on) c.amber else lineColor
                if (k.shape == StopShape.RECORDED) {
                    drawRoundRect(fill, cornerRadius = CornerRadius(14.dp.toPx()))
                    drawRoundRect(edge, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), CornerRadius(12.dp.toPx()), style = Stroke(w))
                } else {
                    drawCircle(fill, size.minDimension / 2)
                    drawCircle(edge, size.minDimension / 2 - w / 2, style = Stroke(w))
                }
            }
            Icon(k.icon, null, Modifier.size(20.dp), tint = if (on) c.onAmber else c.ink)
        }
        Sign(k.label, 13.sp, if (chosen) c.ink else c.muted, maxLines = 1)
    }
}

/** "When" as a stretch of timeline: stops from Yesterday to Just now. */
@Composable
private fun WhenLine(selected: Long, lineColor: Color, onPick: (Long) -> Unit) {
    val c = Line.c
    Box(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(28.dp)) {
            val y = size.height / 2; val step = size.width / whens.size
            drawLine(lineColor, Offset(step / 2, y), Offset(size.width - step / 2, y), strokeWidth = 4.dp.toPx())
        }
        Row(Modifier.fillMaxWidth()) {
            for ((ms, label) in whens) {
                val chosen = ms == selected
                Column(
                    Modifier.weight(1f).clickable(role = Role.RadioButton) { onPick(ms) }.semantics { this.selected = chosen },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Canvas(Modifier.size(28.dp)) {
                        val r = (if (chosen) 11 else 8).dp.toPx()
                        if (chosen) drawCircle(c.amber.copy(alpha = 0.35f), r * 1.6f)
                        drawCircle(if (chosen) c.amber else c.ground, r)
                        drawCircle(if (chosen) c.amber else lineColor, r - 1.5.dp.toPx(), style = Stroke(3.dp.toPx()))
                    }
                    Sign(label, 13.sp, if (chosen) c.ink else c.muted, maxLines = 1)
                }
            }
        }
    }
}

/** A hollow pill choice that fills amber when chosen. */
@Composable
internal fun Pill(label: String, chosen: Boolean, onClick: () -> Unit) {
    val c = Line.c
    Box(
        Modifier.heightIn(min = 40.dp)
            .background(if (chosen) c.amber else Color.Transparent, RoundedCornerShape(20.dp))
            .border(1.5.dp, if (chosen) c.amber else c.ink, RoundedCornerShape(20.dp))
            .clickable(role = Role.RadioButton, onClick = onClick).semantics { selected = chosen }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Sign(label, 15.sp, if (chosen) c.onAmber else c.ink, maxLines = 1) }
}

// ---- Call ----

/** Which number to call, when a loop has several: each a hollow call circle with its name. */
@Composable
fun CallSheet(loop: Loop, numbers: List<LoopNumber>, onDismiss: () -> Unit, onCall: (String) -> Unit) {
    val c = Line.c
    val lineColor = Brand.loop(loop.id, c.dark)
    LineSheet(onDismiss) {
        SheetBand(loop, "Call")
        Column(Modifier.padding(vertical = 8.dp)) {
            for (n in numbers.sortedBy { it.id }) {
                val source = remember { MutableInteractionSource() }
                val pressed by source.collectIsPressedAsState()
                Row(
                    Modifier.fillMaxWidth().clickable(interactionSource = source, indication = null, role = Role.Button) { onCall(n.number) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(44.dp)) {
                            drawCircle(if (pressed) c.amber else c.surface, size.minDimension / 2)
                            drawCircle(if (pressed) c.amber else lineColor, size.minDimension / 2 - 2.dp.toPx(), style = Stroke(3.5.dp.toPx()))
                        }
                        Icon(LineIcons.Phone, null, Modifier.size(18.dp), tint = if (pressed) c.onAmber else c.ink)
                    }
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(n.label.ifBlank { "Unnamed caller" }, fontFamily = Type.Signage, fontWeight = FontWeight.SemiBold, fontSize = 20.sp,
                            color = if (n.label.isBlank()) c.muted else c.ink)
                        MonoLabel(Fmt.phone(n.number), size = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
