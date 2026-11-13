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
import app.lineside.calls.Pill
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
import app.lineside.ui.SettingsScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val app get() = application as App

    private val themeMode by lazy { androidx.compose.runtime.mutableStateOf(app.settings.themeMode) }

    /** A loop to open, from a notification (the quiet-loop nudge). */
    private val openLoop = androidx.compose.runtime.mutableStateOf<Long?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getLongExtra(QuietNudge.EXTRA_LOOP, -1L).takeIf { it > 0 }?.let { openLoop.value = it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.getLongExtra(QuietNudge.EXTRA_LOOP, -1L)?.takeIf { it > 0 }?.let { openLoop.value = it }
        // Debug builds only: `am start ... --ez runDaily true` runs the daily check now, for testing.
        if (BuildConfig.DEBUG && intent?.getBooleanExtra("runDaily", false) == true)
            androidx.work.WorkManager.getInstance(this).enqueue(androidx.work.OneTimeWorkRequestBuilder<DailyWorker>().build())
        // Every screen opens on the dark signage band, so status-bar icons are always light.
        enableEdgeToEdge(statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent { LinesideTheme(themeMode.value) { Root() } }
    }

    /** Bumped on resume so permission states re-read after a trip to a system screen. */
    private val resumes = androidx.compose.runtime.mutableIntStateOf(0)

    override fun onResume() {
        super.onResume()
        resumes.intValue++
        Pill.finishIfAfterCall()
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
    private data object SettingsView : Screen

    private fun encode(s: Screen) = when (s) {
        ListScreen -> "list"; SettingsView -> "settings"; is LoopView -> "loop:${s.id}"; is Edit -> s.id?.let { "edit:$it" } ?: "new"
    }
    private fun decode(k: String): Screen = when {
        k == "settings" -> SettingsView
        k == "new" -> Edit(null)
        k.startsWith("loop:") -> LoopView(k.removePrefix("loop:").toLong())
        k.startsWith("edit:") -> Edit(k.removePrefix("edit:").toLong())
        else -> ListScreen
    }

    @Composable
    private fun Root() {
        val repo = app.repo
        val scope = rememberCoroutineScope()
        // null until the database has answered: a restored screen must not mistake "not loaded yet" for "deleted".
        val loaded by repo.observeAll().collectAsStateWithLifecycle(null as List<LoopFull>?)
        val loops = loaded.orEmpty()
        // Survives rotation and theme changes: saved as "list", "loop:<id>", "edit:<id>", "new", "settings".
        var screen by rememberSaveable(stateSaver = androidx.compose.runtime.saveable.Saver<Screen, String>({ encode(it) }, { decode(it) })) {
            mutableStateOf<Screen>(ListScreen)
        }
        val toOpen = openLoop.value
        LaunchedEffect(toOpen) { if (toOpen != null) { screen = LoopView(toOpen); openLoop.value = null } }
        var returnTo by remember { mutableStateOf<Screen>(ListScreen) }
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) { while (true) { delay(60_000); now = System.currentTimeMillis() } }

        val settings = app.settings
        val tick = resumes.intValue
        var permTick by remember { mutableStateOf(0) }
        val granted = remember(tick, permTick) { Access.entries.associateWith { it.granted(this@MainActivity) } }
        val contactsGranted = granted[Access.CONTACTS] == true
        var hintDismissed by remember { mutableStateOf(settings.contactsHintDismissed) }
        var callsHintDismissed by remember { mutableStateOf(settings.callsHintDismissed) }
        var pillOn by remember { mutableStateOf(settings.pillEnabled) }
        var unknownOn by remember { mutableStateOf(settings.unknownCallerPill) }
        var discreetOn by remember { mutableStateOf(settings.discreetCallScreen) }
        var nudgeOn by remember { mutableStateOf(settings.quietNudge) }
        val askPerms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            permTick++
            if (hasContacts()) scope.launch { repo.resyncAll() }
        }
        val openSystem = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { permTick++ }
        val allow: (Access) -> Unit = { a ->
            a.permissions()?.let { askPerms.launch(it) } ?: a.systemIntent(this@MainActivity)?.let { openSystem.launch(it) }
        }

        var sheet by remember { mutableStateOf<String?>(null) } // "note", "log", "close"
        var openEvent by remember { mutableStateOf<Event?>(null) }

        BackHandler(enabled = screen != ListScreen) {
            screen = when (val s = screen) { is Edit -> if (s.id != null) LoopView(s.id) else returnTo; else -> ListScreen }
        }
        val askContacts = { allow(Access.CONTACTS) }

        when (val s = screen) {
            ListScreen -> LoopsScreen(
                loops = loops, now = now,
                showContactsHint = !contactsGranted && !hintDismissed && loops.isNotEmpty(),
                onOpen = { screen = LoopView(it) },
                onNew = { returnTo = ListScreen; screen = Edit(null) },
                onKeep = { scope.launch { repo.keep(it) } },
                onClose = { full -> screen = LoopView(full.loop.id); sheet = "close" },
                onAllowContacts = askContacts,
                onDismissHint = { hintDismissed = true; settings.contactsHintDismissed = true },
                showCallsHint = granted[Access.CALLS] != true && !callsHintDismissed,
                onAllowCalls = { allow(Access.CALLS) },
                onDismissCallsHint = { callsHintDismissed = true; settings.callsHintDismissed = true },
                onSettings = { screen = SettingsView },
            )

            SettingsView -> SettingsScreen(
                pillEnabled = pillOn, unknownPill = unknownOn, granted = granted,
                onBack = { screen = ListScreen },
                onPill = { pillOn = it; settings.pillEnabled = it },
                onUnknownPill = { unknownOn = it; settings.unknownCallerPill = it },
                discreet = discreetOn,
                onDiscreet = { discreetOn = it; settings.discreetCallScreen = it; scope.launch { repo.resyncAll() } },
                quietNudge = nudgeOn,
                onQuietNudge = { nudgeOn = it; settings.quietNudge = it },
                onAllow = allow,
                theme = themeMode.value,
                onTheme = { themeMode.value = it; settings.themeMode = it },
            )

            is LoopView -> {
                val full = loops.firstOrNull { it.loop.id == s.id }
                if (full == null) { if (loaded != null) screen = ListScreen; return }
                LoopScreen(
                    full = full, now = now,
                    onBack = { screen = ListScreen },
                    onEdit = { screen = Edit(full.loop.id) },
                    onCall = ::dial,
                    onNote = { sheet = "note" },
                    onLog = { sheet = "log" },
                    onClose = { sheet = "close" },
                    onEventClick = { openEvent = it },
                    onNameNumber = { number, name -> scope.launch { repo.setNumberLabel(full.loop.id, number, name) } },
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
            "note" -> NoteSheet(full.loop, onDismiss = { onSheet(null) }) { text ->
                scope.launch { repo.addEvent(Event(loopId = id, kind = EventKind.NOTE, at = System.currentTimeMillis(), text = text)) }
                onSheet(null)
            }
            "log" -> LogSheet(full.loop, full.numbers.sortedBy { it.id }.map { it.number to it.label }, onDismiss = { onSheet(null) }) { kind, ago, number, text ->
                scope.launch { repo.addEvent(Event(loopId = id, kind = kind, at = System.currentTimeMillis() - ago, number = number, text = text)) }
                onSheet(null)
            }
            "close" -> CloseLoopDialog(full, onDismiss = { onSheet(null) }) {
                onSheet(null); onClosed()
                scope.launch { repo.close(id) }
            }
        }
        openEvent?.let { e ->
            EventSheet(full, e, onDismiss = { onEvent(null) }, onDelete = { scope.launch { repo.deleteEvent(e) }; onEvent(null) })
        }
    }
}

