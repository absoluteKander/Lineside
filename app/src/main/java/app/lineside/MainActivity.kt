// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.lineside.data.Event
import app.lineside.data.EventKind
import app.lineside.data.LoopFull
import app.lineside.ui.CloseLoopDialog
import app.lineside.ui.EventSheet
import app.lineside.ui.LinesideTheme
import app.lineside.ui.LogSheet
import app.lineside.ui.LoopEditor
import app.lineside.ui.LoopScreen
import app.lineside.ui.LoopsScreen
import app.lineside.ui.NoteSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val app get() = application as App

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LinesideTheme { Root() } }
    }

    override fun onResume() {
        super.onResume()
        // Status lines age ("VM 4d" → "VM 5d"); refresh the call-screen copies whenever the app is opened.
        lifecycleScope.launch { app.repo.resyncAll() }
    }

    private fun dial(number: String) = startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))

    private fun hasContacts() =
        checkSelfPermission(Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Simple screen stack: the loop list, one loop, or the editor. */
    private sealed interface Screen
    private data object ListScreen : Screen
    private data class LoopView(val id: Long) : Screen
    private data class Edit(val id: Long?) : Screen

    @Composable
    private fun Root() {
        val repo = app.repo
        val scope = rememberCoroutineScope()
        val loops by repo.observeAll().collectAsStateWithLifecycle(emptyList())
        var screen by remember { mutableStateOf<Screen>(ListScreen) }
        var returnTo by remember { mutableStateOf<Screen>(ListScreen) }
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) { while (true) { delay(60_000); now = System.currentTimeMillis() } }

        var contactsGranted by remember { mutableStateOf(hasContacts()) }
        var hintDismissed by rememberSaveable { mutableStateOf(false) }
        val askContacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            contactsGranted = hasContacts()
            if (contactsGranted) scope.launch { repo.resyncAll() }
        }

        var sheet by remember { mutableStateOf<String?>(null) } // "note", "log", "close"
        var openEvent by remember { mutableStateOf<Event?>(null) }

        BackHandler(enabled = screen != ListScreen) {
            screen = when (val s = screen) { is Edit -> if (s.id != null) LoopView(s.id) else returnTo; else -> ListScreen }
        }

        when (val s = screen) {
            ListScreen -> LoopsScreen(
                loops = loops, now = now,
                showContactsHint = !contactsGranted && !hintDismissed && loops.isNotEmpty(),
                onOpen = { screen = LoopView(it) },
                onNew = { returnTo = ListScreen; screen = Edit(null) },
                onKeep = { scope.launch { repo.keep(it) } },
                onClose = { full -> screen = LoopView(full.loop.id); sheet = "close" },
                onAllowContacts = { askContacts.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)) },
                onDismissHint = { hintDismissed = true },
            )

            is LoopView -> {
                val full = loops.firstOrNull { it.loop.id == s.id }
                if (full == null) { screen = ListScreen; return }
                LoopScreen(
                    full = full, now = now,
                    onBack = { screen = ListScreen },
                    onEdit = { screen = Edit(full.loop.id) },
                    onCall = ::dial,
                    onNote = { sheet = "note" },
                    onLog = { sheet = "log" },
                    onClose = { sheet = "close" },
                    onEventClick = { openEvent = it },
                )
                Sheets(full, sheet, openEvent, onSheet = { sheet = it }, onEvent = { openEvent = it }, onClosed = { screen = ListScreen })
            }

            is Edit -> {
                val existing = s.id?.let { id -> loops.firstOrNull { it.loop.id == id } }
                LoopEditor(
                    existing = existing,
                    onCancel = { screen = if (s.id != null) LoopView(s.id) else returnTo },
                    onSave = { name, topic, numbers, note ->
                        scope.launch {
                            val id = if (s.id == null) repo.create(name, topic, numbers, note)
                            else { repo.edit(s.id, name, topic, numbers); s.id }
                            screen = LoopView(id)
                        }
                    },
                )
            }
        }
    }

    @Composable
    private fun Sheets(full: LoopFull, sheet: String?, openEvent: Event?, onSheet: (String?) -> Unit, onEvent: (Event?) -> Unit, onClosed: () -> Unit) {
        val repo = app.repo
        val scope = rememberCoroutineScope()
        val id = full.loop.id
        when (sheet) {
            "note" -> NoteSheet(onDismiss = { onSheet(null) }) { text ->
                scope.launch { repo.addEvent(Event(loopId = id, kind = EventKind.NOTE, at = System.currentTimeMillis(), text = text)) }
                onSheet(null)
            }
            "log" -> LogSheet(full.numbers.map { it.number }, onDismiss = { onSheet(null) }) { kind, ago, number, text ->
                scope.launch { repo.addEvent(Event(loopId = id, kind = kind, at = System.currentTimeMillis() - ago, number = number, text = text)) }
                onSheet(null)
            }
            "close" -> CloseLoopDialog(full, onDismiss = { onSheet(null) }) {
                onSheet(null); onClosed()
                scope.launch { repo.close(id) }
            }
        }
        openEvent?.let { e ->
            EventSheet(e, onDismiss = { onEvent(null) }, onDelete = { scope.launch { repo.deleteEvent(e) }; onEvent(null) })
        }
    }
}

