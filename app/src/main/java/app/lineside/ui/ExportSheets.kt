// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.PersistableBundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lineside.domain.Passphrase

/** The Settings section's state: Android backup, and the export. */
class BackupState(
    val on: Boolean, val refusedAt: Long, val lastExportAt: Long,
    val onBackup: (Boolean) -> Unit, val onExport: () -> Unit, val onImport: () -> Unit,
)

@Composable
private fun Band(sign: String, title: String) {
    val c = Line.c
    Column(Modifier.fillMaxWidth().background(c.header).padding(horizontal = 16.dp, vertical = 14.dp)) {
        Sign(sign, 14.sp, c.amber)
        Text(title, fontFamily = Type.Signage, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = c.headerInk, maxLines = 1)
    }
}

/** Passwords keyboard: no suggestions, and the keyboard doesn't learn the words. */
private val secret = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false)
private val mono = TextStyle(fontFamily = Type.Mono, fontSize = 17.sp, lineHeight = 24.sp)

/**
 * Choose the export's passphrase. A suggested one is filled in; the user can type their own instead.
 * Lineside never keeps it: once the file is written it's gone from memory.
 */
@Composable
fun ExportSheet(suggest: () -> String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val c = Line.c
    val ctx = LocalContext.current
    // Deliberately not saved across a rotation: a passphrase has no business in saved state.
    var suggested by remember { mutableStateOf(suggest()) }
    var pass by remember { mutableStateOf(suggested) }
    var copied by remember { mutableStateOf(false) }
    val problem = if (pass == suggested) null else Passphrase.problem(pass)
    LineSheet(onDismiss) {
        Band("Export", "Encrypted export")
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                "One file with every loop, archived ones too, and the text access log: a page you can read in a browser, " +
                    "and the data Lineside can import again. It's locked with this passphrase.",
                fontSize = 14.sp, lineHeight = 20.sp, color = c.body,
            )
            PlateField("Passphrase", pass, { pass = it; copied = false }, c.ink, "Your own passphrase", 0, singleLine = false, keyboard = secret, textStyle = mono)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LineButton("New suggestion", { suggested = suggest(); pass = suggested; copied = false }, Modifier.weight(1f))
                LineButton(if (copied) "Copied" else "Copy", {
                    val clip = ClipData.newPlainText("Lineside export passphrase", pass)
                    // Android 13+: keeps it out of the clipboard preview.
                    clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
                    ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
                    copied = true
                }, Modifier.weight(1f), enabled = pass.isNotEmpty())
            }
            problem?.let { Text(it, fontSize = 13.sp, lineHeight = 18.sp, color = c.amberInk) }
            Text(
                "Write it down or keep it in a password manager. Lineside doesn't keep it, and without it nobody can open the file, you included.",
                fontSize = 13.sp, lineHeight = 18.sp, color = c.ink, fontWeight = FontWeight.SemiBold,
            )
            Text(
                "To open it on a computer: 7-Zip or PeaZip on Windows and Linux, Keka on a Mac. " +
                    "The unzip built into Windows and macOS can't open encrypted files like this one.",
                fontSize = 13.sp, lineHeight = 18.sp, color = c.muted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LineButton("Cancel", onDismiss, Modifier.weight(1f), edge = c.muted)
                LineButton("Save file…", { onSave(pass) }, Modifier.weight(1f), enabled = problem == null)
            }
        }
    }
}

sealed interface ImportUi {
    data object Ready : ImportUi
    data object Working : ImportUi
    data class Done(val added: Int, val archived: Int, val alreadyHere: Int) : ImportUi
    data class Failed(val why: String) : ImportUi
}

/** Open a Lineside export: the passphrase, then what came in. */
@Composable
fun ImportSheet(fileName: String?, state: ImportUi, onDismiss: () -> Unit, onImport: (String) -> Unit) {
    val c = Line.c
    var pass by remember { mutableStateOf("") }
    LineSheet(onDismiss) {
        Band("Import", fileName ?: "Lineside export")
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (state is ImportUi.Done) {
                Text(
                    when {
                        state.added == 0 && state.alreadyHere > 0 -> "Nothing new: every loop in this export is already here."
                        state.added == 0 -> "This export has no loops in it."
                        else -> buildString {
                            append("Added ${state.added} loop${if (state.added == 1) "" else "s"}")
                            if (state.archived > 0) append(", ${state.archived} of them to the archive")
                            append(".")
                            if (state.alreadyHere > 0) append(" ${state.alreadyHere} ${if (state.alreadyHere == 1) "was" else "were"} already here and left as ${if (state.alreadyHere == 1) "it was" else "they were"}.")
                        }
                    },
                    fontSize = 15.sp, lineHeight = 21.sp, color = c.ink,
                )
                if (state.added > 0) Text(
                    "Texts switches start off on this phone; turn them on in a loop's Texts if you want them.",
                    fontSize = 13.sp, lineHeight = 18.sp, color = c.muted,
                )
                LineButton("Done", onDismiss, Modifier.fillMaxWidth())
                return@Column
            }
            Text(
                "Adds the loops in this export to Lineside, archived ones to the archive. Loops already here are left as they are.",
                fontSize = 14.sp, lineHeight = 20.sp, color = c.body,
            )
            PlateField("Passphrase", pass, { pass = it }, c.ink, "The passphrase it was saved with", 0, singleLine = false, keyboard = secret, textStyle = mono)
            (state as? ImportUi.Failed)?.let { Text(it.why, fontSize = 13.sp, lineHeight = 18.sp, color = c.amberInk) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LineButton("Cancel", onDismiss, Modifier.weight(1f), edge = c.muted)
                LineButton(if (state is ImportUi.Working) "Opening…" else "Import", { onImport(pass) }, Modifier.weight(1f),
                    enabled = pass.isNotEmpty() && state !is ImportUi.Working)
            }
        }
    }
}
