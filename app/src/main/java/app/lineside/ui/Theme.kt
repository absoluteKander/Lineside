// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Colours from the design canvas: deep teal, amber loop badge, cool neutrals. */
object Brand {
    val Teal = Color(0xFF0B6E6E)
    val TealLight = Color(0xFF7FD6D6)
    val TealWash = Color(0xFFDCEEEE)
    val Amber = Color(0xFFFFC46B)
    val AmberWash = Color(0xFFFDEFD3)
    val AmberInk = Color(0xFF8A4B08)
    val Ink = Color(0xFF15202B)
    val Muted = Color(0xFF5B6670)
    val Body = Color(0xFF3D4852)
    val Line = Color(0xFFD5DCE0)
    val Chip = Color(0xFFE6EAED)
    val Ground = Color(0xFFF3F5F6)
    val DangerWash = Color(0xFFFBE4E1)
    val DangerInk = Color(0xFF9B2C1F)

    /** Avatar colours; each loop gets one by its id. All pass 4.5:1 against white initials. */
    val avatars = listOf(Color(0xFF0B6E6E), Color(0xFF3B5B8C), Color(0xFF6B4E8C), Color(0xFF8A4B08), Color(0xFF2F6B3F), Color(0xFF8C3B5B))
    fun avatar(id: Long) = avatars[(id % avatars.size).toInt()]
}

private val Light = lightColorScheme(
    primary = Brand.Teal, onPrimary = Color.White,
    secondary = Brand.Amber, onSecondary = Brand.Ink,
    background = Brand.Ground, surface = Color.White, onSurface = Brand.Ink, onSurfaceVariant = Brand.Muted,
    surfaceVariant = Brand.Ground, outline = Color(0xFFC9D1D6),
)
private val Dark = darkColorScheme(
    primary = Brand.TealLight, onPrimary = Brand.Ink,
    secondary = Brand.Amber, onSecondary = Brand.Ink,
    background = Color(0xFF0F1418), surface = Color(0xFF1E2A32),
)

@Composable
fun LinesideTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
