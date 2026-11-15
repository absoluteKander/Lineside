// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lineside.Access

@Composable
fun SettingsScreen(
    pillEnabled: Boolean,
    unknownPill: Boolean,
    granted: Map<Access, Boolean>,
    onBack: () -> Unit,
    onPill: (Boolean) -> Unit,
    onUnknownPill: (Boolean) -> Unit,
    onAllow: (Access) -> Unit,
    discreet: Boolean,
    onDiscreet: (Boolean) -> Unit,
    quietNudge: Boolean,
    onQuietNudge: (Boolean) -> Unit,
    theme: ThemeMode,
    onTheme: (ThemeMode) -> Unit,
    /** Texts (foss only): null hides the section. */
    texts: TextsState? = null,
    lock: LockState? = null,
    backup: BackupState? = null,
    onIntro: (() -> Unit)? = null,
) {
    val c = Line.c
    Column(Modifier.fillMaxSize().background(c.ground)) {
        ScreenBand("Settings", onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            Section("During calls")
            Group {
                Toggle("Show the pill", "Context and a note field during and just after a loop's calls. Off: the call screen still shows the loop's status.", pillEnabled, onPill)
                Divider()
                Toggle(
                    "Offer to start a loop for unknown numbers",
                    "A \"+ Start a loop\" pill for numbers that aren't in your contacts. It disappears after the call if you ignore it.",
                    unknownPill && pillEnabled, onUnknownPill, enabled = pillEnabled,
                )
            }
            Section("On the call screen")
            Group {
                Toggle(
                    "Discreet",
                    "A ringing phone is readable by anyone nearby. Discreet shows only \"Call back\" or \"Waiting\" and how long ago, " +
                        "never a loop's topic or who within it is calling. The loop's name still shows.",
                    discreet, onDiscreet,
                )
            }
            lock?.let { l ->
                Section("App lock")
                Group {
                    Toggle(
                        "Lock Lineside",
                        if (l.available) "Uses your phone's screen lock (fingerprint, face or PIN) when you open Lineside after a while away. Nothing extra to remember, nothing stored here. " +
                            "Covers Lineside and the share sheet; the pill during your own calls stays as it is."
                        else "Your phone has no screen lock set. Add one in your phone's settings and the app lock turns on.",
                        l.on && l.available, l.onLock, enabled = l.available,
                    )
                    if (l.on && l.available) {
                        Divider()
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Ask again after", fontFamily = Type.Signage, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = c.ink)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                for ((ms, label) in listOf(0L to "Right away", 60_000L to "1 minute", 5 * 60_000L to "5 minutes"))
                                    Pill(label, l.afterMs == ms) { l.onAfter(ms) }
                            }
                        }
                    }
                }
            }
            backup?.let { b ->
                Section("Backup and export")
                Group {
                    Toggle(
                        "Include in Android backup",
                        if (b.on) "Android can back up Lineside (Google's backup, or Seedvault on GrapheneOS) only when that backup is encrypted " +
                            "with your screen lock, so the backup service can't read it. Moving to a new phone with Android's transfer works too. " +
                            "Your phone's own backup has to be switched on as well."
                        else "Off: Android's backup gets nothing from Lineside. Switch on to allow it, but only into a backup encrypted with your " +
                            "screen lock, or a transfer to your new phone.",
                        b.on, b.onBackup,
                    )
                    if (b.on && b.refusedAt > 0) Text(
                        "The last time Android asked (${Fmt.dateTime(b.refusedAt)}), its backup wasn't encrypted with your screen lock, so Lineside sent nothing.",
                        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp), fontSize = 13.sp, lineHeight = 18.sp, color = c.amberInk,
                    )
                    Divider()
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Encrypted export", fontFamily = Type.Signage, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, color = c.ink)
                        Text(
                            "A file of everything, archived loops and the text access log too, locked with a passphrase. " +
                                "Read it on a computer, keep it as your own backup, or import it on another phone.",
                            fontSize = 13.sp, lineHeight = 18.sp, color = c.muted,
                        )
                        if (b.lastExportAt > 0) Text("Last export: ${Fmt.dateTime(b.lastExportAt)}", fontSize = 13.sp, color = c.body)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            LineButton("Export…", b.onExport, Modifier.weight(1f))
                            LineButton("Import…", b.onImport, Modifier.weight(1f))
                        }
                    }
                }
            }
            Section("Reminders")
            Group {
                Toggle(
                    "Remind me about quiet loops",
                    "One silent notification when a loop has had no calls or notes for three weeks, asking whether you're still waiting. " +
                        "Off: quiet loops just move to the bottom of the list.",
                    quietNudge, onQuietNudge,
                )
            }
            Section("Appearance")
            Group {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((mode, label) in listOf(ThemeMode.SYSTEM to "Phone's setting", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark"))
                            Pill(label, theme == mode) { onTheme(mode) }
                    }
                    Text("The signage band at the top stays dark either way.", fontSize = 13.sp, lineHeight = 18.sp, color = c.muted)
                }
            }
            texts?.let { t ->
                Section("Texts")
                Group {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            if (t.numbers == 0) "Texts aren't added to any loop. To add them, open a loop and tap Texts. " +
                                "Without that, a text only gets into Lineside if you share it here."
                            else "Adding texts from ${t.numbers} number${if (t.numbers == 1) "" else "s"}. Turn them off in each loop's Texts.",
                            fontSize = 13.sp, lineHeight = 18.sp, color = c.body,
                        )
                        if (t.handingBack) HandingBack(t.onCloseApp)
                        AccessLine(t.granted, t.handingBack, t.canHandBack)
                        LineButton("Text access log", t.onLog, Modifier.fillMaxWidth())
                    }
                }
            }
            onIntro?.let { go ->
                Section("Introduction")
                Group {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("What Lineside does and doesn't do, the practice loop, and every choice from the first run.",
                            Modifier.weight(1f), fontSize = 13.sp, lineHeight = 18.sp, color = c.muted)
                        LineButton("Show again", go)
                    }
                }
            }
            Section("What Lineside can do")
            Text(
                "All optional. Lineside has no internet access, no account and never reads your call log.",
                Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp), fontSize = 13.sp, lineHeight = 18.sp, color = c.muted,
            )
            Group {
                Access.entries.forEachIndexed { i, a ->
                    if (i > 0) Divider()
                    AccessRow(a, granted[a] == true) { onAllow(a) }
                }
            }
        }
    }
}

class LockState(val on: Boolean, val available: Boolean, val afterMs: Long, val onLock: (Boolean) -> Unit, val onAfter: (Long) -> Unit)

class TextsState(
    val numbers: Int, val granted: Boolean, val handingBack: Boolean, val canHandBack: Boolean,
    val onLog: () -> Unit, val onCloseApp: () -> Unit,
)

@Composable
private fun Section(text: String) =
    Sign(text, 15.sp, Line.c.muted, Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp).semantics { heading() }, FontWeight.SemiBold)

@Composable
internal fun Group(inset: androidx.compose.ui.unit.Dp = 16.dp, content: @Composable () -> Unit) =
    Column(Modifier.fillMaxWidth().padding(horizontal = inset).background(Line.c.surface, RoundedCornerShape(12.dp))) { content() }

@Composable
internal fun Divider() = Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(1.dp).background(Line.c.divider))

@Composable
internal fun Toggle(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val c = Line.c
    // The whole row is the switch: easier to hit, and read as one control by TalkBack.
    Row(
        Modifier.fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontFamily = Type.Signage, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, color = if (enabled) c.ink else c.muted)
            Text(detail, fontSize = 13.sp, lineHeight = 18.sp, color = c.muted)
        }
        // On = amber, like every other active control; off = hollow in ink.
        Switch(
            checked, onCheckedChange = null, enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = c.amber, checkedThumbColor = c.onAmber, checkedBorderColor = c.amber,
                uncheckedTrackColor = c.surface, uncheckedThumbColor = c.ink, uncheckedBorderColor = c.ink,
            ),
        )
    }
}

@Composable
internal fun AccessRow(a: Access, on: Boolean, onAllow: () -> Unit) {
    val c = Line.c
    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(a.title, fontFamily = Type.Signage, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, color = c.ink)
            Text(a.why, fontSize = 13.sp, lineHeight = 18.sp, color = c.body)
            if (!on) Text("Without it: ${a.without}", fontSize = 13.sp, lineHeight = 18.sp, color = c.muted)
        }
        if (on) Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).drawBehind { drawCircle(c.waiting) })
            Spacer(Modifier.width(6.dp))
            Sign("On", 15.sp, c.waitingInk)
        } else LineButton("Allow", onAllow)
    }
}
