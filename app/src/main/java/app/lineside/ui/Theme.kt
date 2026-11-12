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
    val Amber = Color(0xFFFFC46B)
    val Ink = Color(0xFF12181D)
}

private val Light = lightColorScheme(
    primary = Brand.Teal, onPrimary = Color.White,
    secondary = Brand.Amber, onSecondary = Brand.Ink,
    background = Color(0xFFF4F6F7), surface = Color.White, onSurface = Brand.Ink,
)
private val Dark = darkColorScheme(
    primary = Brand.TealLight, onPrimary = Brand.Ink,
    secondary = Brand.Amber, onSecondary = Brand.Ink,
    background = Color(0xFF0F1418), surface = Color(0xFF1E2A32),
)

@Composable
fun LinesideTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
