// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.lineside.R

/** The Line map look: transit signage type, a timeline per loop, amber for "where your attention is". */
object Type {
    /** Signage: names, labels, buttons. Barlow Condensed (SIL OFL). */
    val Signage = FontFamily(
        Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
        Font(R.font.barlow_condensed_bold, FontWeight.Bold),
    )
    /** Times and dates. JetBrains Mono (SIL OFL). */
    val Mono = FontFamily(Font(R.font.jetbrains_mono_medium, FontWeight.Medium))
}

/** Colour tokens. Every screen reads these, never raw hex, so light and dark stay in step. */
@Immutable
data class LineColors(
    val dark: Boolean,
    val ground: Color,      // page background
    val surface: Color,     // cards, waypoints, day plates
    val ink: Color,         // primary text and icons
    val body: Color,        // secondary text
    val muted: Color,       // labels, hints
    val divider: Color,
    val plateEdge: Color,   // day plate outline
    val header: Color,      // the signage band at the top (dark in both themes)
    val headerInk: Color,
    val headerMuted: Color,
    val amber: Color,       // where your attention is: needs you, selected, pressed
    val onAmber: Color,
    val amberWash: Color,   // background for "they said" blocks
    val amberInk: Color,    // amber-family text on light surfaces
    val waiting: Color,     // the "waiting" lamp
    val waitingWash: Color,
    val waitingInk: Color,
)

val LightLine = LineColors(
    dark = false,
    ground = Color(0xFFEEF1F2), surface = Color.White, ink = Color(0xFF12181D), body = Color(0xFF2A343C),
    muted = Color(0xFF5B6670), divider = Color(0xFFDDE3E6), plateEdge = Color(0xFF12181D),
    header = Color(0xFF12181D), headerInk = Color.White, headerMuted = Color(0xFFB9C4CC),
    amber = Color(0xFFFFB547), onAmber = Color(0xFF12181D), amberWash = Color(0xFFFFF4DF), amberInk = Color(0xFF8A4B08),
    waiting = Color(0xFF2FA37F), waitingWash = Color(0xFFDDF3EC), waitingInk = Color(0xFF1E6B54),
)

val DarkLine = LineColors(
    dark = true,
    ground = Color(0xFF0C1114), surface = Color(0xFF182127), ink = Color(0xFFEAF0F2), body = Color(0xFFC9D3D8),
    muted = Color(0xFF93A2AA), divider = Color(0xFF26323A), plateEdge = Color(0xFFC9D3D8),
    header = Color(0xFF141B20), headerInk = Color.White, headerMuted = Color(0xFF9AA8B0),
    amber = Color(0xFFFFB547), onAmber = Color(0xFF12181D), amberWash = Color(0xFF33281A), amberInk = Color(0xFFFFD58A),
    waiting = Color(0xFF4FC3A1), waitingWash = Color(0xFF173A30), waitingInk = Color(0xFF8FE3C8),
)

val LocalLine = staticCompositionLocalOf { LightLine }

/** Shorthand: `Line.c.ink`. */
object Line {
    val c: LineColors @Composable get() = LocalLine.current
}

/** Kept for code and drawing outside Compose (e.g. the contact photo), and loop colours. */
object Brand {
    val Teal = Color(0xFF0B6E6E)
    val TealLight = Color(0xFF7FD6D6)
    val TealWash = Color(0xFFDCEEEE)
    val Amber = Color(0xFFFFB547)
    val AmberWash = Color(0xFFFFF4DF)
    val AmberInk = Color(0xFF8A4B08)
    val Ink = Color(0xFF12181D)
    val Muted = Color(0xFF5B6670)
    val Body = Color(0xFF2A343C)
    val Line = Color(0xFFD5DCE0)
    val Chip = Color(0xFFE6EAED)
    val Ground = Color(0xFFEEF1F2)
    val DangerWash = Color(0xFFFBE4E1)
    val DangerInk = Color(0xFF9B2C1F)

    /** Each loop's line colour. All pass 4.5:1 under white text; the dark-theme versions are brighter. */
    val avatars = listOf(Color(0xFF6B4E8C), Color(0xFF0B6E6E), Color(0xFF3B5B8C), Color(0xFF8A4B08), Color(0xFF2F6B3F), Color(0xFF8C3B5B))
    private val avatarsDark = listOf(Color(0xFF9C7FC2), Color(0xFF3FB0AE), Color(0xFF7A9BD0), Color(0xFFD08A3E), Color(0xFF5FAE70), Color(0xFFC7718F))
    fun avatar(id: Long) = avatars[(id % avatars.size).toInt()]
    fun loop(id: Long, dark: Boolean) = (if (dark) avatarsDark else avatars)[(id % avatars.size).toInt()]
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Composable
fun LinesideTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }
    val l = if (dark) DarkLine else LightLine
    // Material components (sheets, dialogs, text fields) pick up the same tokens.
    val scheme = if (dark) darkColorScheme(
        primary = l.amber, onPrimary = l.onAmber, secondary = l.amber, onSecondary = l.onAmber,
        background = l.ground, onBackground = l.ink, surface = l.surface, onSurface = l.ink,
        surfaceVariant = l.ground, onSurfaceVariant = l.muted, outline = l.muted, surfaceContainerLow = l.surface,
        surfaceContainer = l.surface, surfaceContainerHigh = l.surface,
    ) else lightColorScheme(
        primary = l.ink, onPrimary = Color.White, secondary = l.amber, onSecondary = l.onAmber,
        background = l.ground, onBackground = l.ink, surface = l.surface, onSurface = l.ink,
        surfaceVariant = l.ground, onSurfaceVariant = l.muted, outline = Color(0xFFC3CCD2), surfaceContainerLow = l.surface,
        surfaceContainer = l.surface, surfaceContainerHigh = l.surface,
    )
    CompositionLocalProvider(LocalLine provides l) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
