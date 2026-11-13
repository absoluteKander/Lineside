// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lineside.data.EventKind
import app.lineside.domain.Ball
import app.lineside.domain.Initials
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun Avatar(id: Long, name: String, size: Dp = 44.dp) = Box(
    Modifier.size(size).background(Brand.avatar(id), CircleShape),
    contentAlignment = Alignment.Center,
) {
    Text(Initials.of(name), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.36f).sp)
}

@Composable
fun BallChip(ball: Ball) {
    val (label, bg, fg) = when (ball) {
        Ball.CALL_BACK -> Triple("Call back", Brand.AmberWash, Brand.AmberInk)
        Ball.WAITING -> Triple("Waiting", Brand.Chip, Brand.Body)
    }
    Text(
        label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.background(bg, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) = Text(
    text.uppercase(), modifier = modifier, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
    letterSpacing = 0.7.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
)

object Fmt {
    private val zone get() = ZoneId.systemDefault()
    private val dateTime = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.getDefault())
    private val date = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    fun dateTime(ms: Long): String = dateTime.format(Instant.ofEpochMilli(ms).atZone(zone))
    fun date(ms: Long): String = date.format(Instant.ofEpochMilli(ms).atZone(zone))
    private val time = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    private val day = DateTimeFormatter.ofPattern("EEE MMM d", Locale.getDefault())
    private val dayYear = DateTimeFormatter.ofPattern("EEE MMM d yyyy", Locale.getDefault())
    fun time(ms: Long): String = time.format(Instant.ofEpochMilli(ms).atZone(zone))
    fun day(d: java.time.LocalDate): String = (if (d.year == java.time.LocalDate.now().year) day else dayYear).format(d)
    fun duration(sec: Int): String = "%d:%02d".format(sec / 60, sec % 60)

    fun kind(kind: EventKind): String = when (kind) {
        EventKind.NOTE -> "Your note"
        EventKind.CALL_IN -> "They called"
        EventKind.CALL_OUT -> "You called"
        EventKind.MISSED -> "Missed call"
        EventKind.VOICEMAIL -> "Voicemail"
        EventKind.TEXT -> "Text"
    }

    fun icon(kind: EventKind) = when (kind) {
        EventKind.NOTE -> LineIcons.Note
        EventKind.CALL_IN -> LineIcons.Incoming
        EventKind.CALL_OUT -> LineIcons.Outgoing
        EventKind.MISSED -> LineIcons.Missed
        EventKind.VOICEMAIL -> LineIcons.Voicemail
        EventKind.TEXT -> LineIcons.Text
    }

    /** Icon bubble colours: their move (amber), your words (neutral), calls (teal). */
    fun tint(kind: EventKind): Pair<Color, Color> = when (kind) {
        EventKind.MISSED, EventKind.VOICEMAIL, EventKind.TEXT -> Brand.AmberWash to Brand.AmberInk
        EventKind.NOTE -> Brand.Chip to Brand.Body
        EventKind.CALL_IN, EventKind.CALL_OUT -> Brand.TealWash to Brand.Teal
    }
}
