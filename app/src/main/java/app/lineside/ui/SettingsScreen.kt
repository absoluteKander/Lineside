// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(LineIcons.Back, "Back") }
            Text("Settings", style = MaterialTheme.typography.titleLarge)
        }
        SectionLabel("During calls", Modifier.padding(start = 20.dp, top = 8.dp, bottom = 8.dp))
        Group {
            Toggle("Show the pill", "Context and a note field during and just after a loop's calls. Off: the call screen still shows the loop's status.", pillEnabled, onPill)
            HorizontalDivider()
            Toggle(
                "Offer to start a loop for unknown numbers",
                "A \"+ Start a loop\" pill for numbers that aren't in your contacts. It disappears after the call if you ignore it.",
                unknownPill && pillEnabled, onUnknownPill, enabled = pillEnabled,
            )
        }
        SectionLabel("What Lineside can do", Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp))
        Text(
            "All optional. Lineside has no internet access, no account and never reads your call log.",
            Modifier.padding(horizontal = 20.dp, vertical = 4.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Group {
            Access.entries.forEachIndexed { i, a ->
                if (i > 0) HorizontalDivider()
                AccessRow(a, granted[a] == true) { onAllow(a) }
            }
        }
    }
}

@Composable
private fun Group(content: @Composable () -> Unit) = Card(
    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
) { Column { content() } }

@Composable
private fun Toggle(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) =
    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(detail, fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange, enabled = enabled)
    }

@Composable
private fun AccessRow(a: Access, on: Boolean, onAllow: () -> Unit) =
    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(a.title, fontWeight = FontWeight.SemiBold)
            Text(a.why, fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!on) Text("Without it: ${a.without}", fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (on) Text("On", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        else Button(onClick = onAllow) { Text("Allow") }
    }
