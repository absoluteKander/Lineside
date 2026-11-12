// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** The design canvas's stroke icons (24-unit grid, 2-unit round strokes). Tinted by Icon(). */
object LineIcons {
    private fun icon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            for (d in paths) addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            )
        }.build()

    val Back = icon("back", "M19 12H5", "M12 19l-7-7 7-7")
    val Plus = icon("plus", "M12 5v14", "M5 12h14")
    val Close = icon("close", "M18 6L6 18", "M6 6l12 12")
    val Phone = icon("phone", "M22 16.9v3a2 2 0 0 1-2.2 2 19.8 19.8 0 0 1-8.6-3.1 19.5 19.5 0 0 1-6-6A19.8 19.8 0 0 1 2.1 4.2 2 2 0 0 1 4.1 2h3a2 2 0 0 1 2 1.7c.1.9.4 1.8.7 2.7a2 2 0 0 1-.5 2.1L8 9.8a16 16 0 0 0 6 6l1.3-1.3a2 2 0 0 1 2.1-.4c.9.3 1.8.6 2.7.7a2 2 0 0 1 1.7 2z")
    val Voicemail = icon("voicemail", "M2 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0", "M14 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0", "M6 16h12")
    val Note = icon("note", "M12 20h9", "M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z")
    val Text = icon("text", "M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z")
    val Outgoing = icon("outgoing", "M7 17L17 7", "M8 7h9v9")
    val Incoming = icon("incoming", "M17 7L7 17", "M16 17H7V8")
    val Missed = icon("missed", "M3 7l6 6 4-4 8 8", "M15 17h6v-6")
    val Log = icon("log", "M12 8v4l3 2", "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0")
    val Done = icon("done", "M22 11.1V12a10 10 0 1 1-5.9-9.1", "M22 4L12 14l-3-3")
    val Edit = icon("edit", "M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7", "M18.5 2.5a2.1 2.1 0 0 1 3 3L12 15l-4 1 1-4z")
    val ChevronDown = icon("chevron-down", "M6 9l6 6 6-6")
    val ChevronUp = icon("chevron-up", "M18 15l-6-6-6 6")
    val Settings = icon("settings", "M4 21v-7", "M4 10V3", "M12 21v-9", "M12 8V3", "M20 21v-5", "M20 12V3", "M1 14h6", "M9 8h6", "M17 16h6")
    val Trash = icon("trash", "M3 6h18", "M8 6V4h8v2", "M19 6l-1 14H6L5 6")
}
