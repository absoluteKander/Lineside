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
import app.lineside.data.Export
import app.lineside.ui.ArchiveScreen
import app.lineside.ui.BackupState
import app.lineside.ui.ExportSheet
import app.lineside.ui.ImportSheet
import app.lineside.ui.ImportUi
import app.lineside.ui.ArchivedSheet
import app.lineside.ui.CloseLoopDialog
import app.lineside.ui.EventSheet
import app.lineside.ui.LinesideTheme
import app.lineside.ui.LockState
import app.lineside.ui.LogSheet
import app.lineside.ui.LoopEditor
import app.lineside.ui.LoopScreen
import app.lineside.ui.LoopsScreen
import app.lineside.ui.NoteSheet
import app.lineside.ui.SettingsScreen
import app.lineside.ui.PickTextsSheet
import app.lineside.ui.TextLogSheet
import app.lineside.ui.TextsSheet
import app.lineside.ui.TextsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
        setContent { LinesideTheme(themeMode.value) { LockGate(this) { Root() } } }
    }

    /** Bumped on resume so permission states re-read after a trip to a system screen. */
    private val resumes = androidx.compose.runtime.mutableIntStateOf(0)

    override fun onResume() {
        super.onResume()
        resumes.intValue++
        Pill.finishIfAfterCall()
        // Status lines age ("VM 4d" → "VM 5d"); refresh the call-screen copies whenever the app is opened.
        lifecycleScope.launch { app.repo.resyncAll() }
        // Texts from switched-on numbers. At most every couple of minutes: each look is a line in the access log.
        if (System.currentTimeMillis() - lastTextLook > 2 * 60_000) {
            lastTextLook = System.currentTimeMillis()
            lifecycleScope.launch { Texts.importNow(this@MainActivity, "Opened Lineside") }
        }
    }

    private var lastTextLook = 0L

    private fun dial(number: String) = startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))

    /** A waypoint becomes a calendar entry in whatever calendar app the user has. Needs no permission; nothing is read back. */
    private fun calendar(full: LoopFull, e: Event, w: app.lineside.domain.WhenParse.When?) {
        val title = e.text.lineSequence().first().trim().let { if (it.length > 80) it.take(79).trimEnd() + "…" else it }
        val i = Intent(Intent.ACTION_INSERT).setData(android.provider.CalendarContract.Events.CONTENT_URI)
            .putExtra(android.provider.CalendarContract.Events.TITLE, title)
            .putExtra(android.provider.CalendarContract.Events.DESCRIPTION,
                listOf(full.loop.name, full.loop.topic).filter { it.isNotBlank() }.joinToString(" · ") + "\n\n" + e.text.trim())
        if (w != null) {
            val start = w.start.toInstant().toEpochMilli()
            i.putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
            if (w.hasTime) i.putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, start + 60 * 60_000L)
            else i.putExtra(android.provider.CalendarContract.EXTRA_EVENT_ALL_DAY, true).putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, start + 24 * 60 * 60_000L)
        }
        runCatching { startActivity(i) }.onFailure { android.widget.Toast.makeText(this, "No calendar app found.", android.widget.Toast.LENGTH_SHORT).show() }
    }

    /** A new message to this number in whatever texting app the user has. Needs no permission. */
    private fun text(number: String) = runCatching { startActivity(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null))) }
        .onFailure { android.widget.Toast.makeText(this, "No texting app found.", android.widget.Toast.LENGTH_SHORT).show() }

    private fun hasContacts() =
        checkSelfPermission(Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Simple screen stack: the loop list, one loop, or the editor. */
    private sealed interface Screen
    private data object ListScreen : Screen
    private data class LoopView(val id: Long) : Screen
    private data class Edit(val id: Long?) : Screen
    private data object SettingsView : Screen
    private data object ArchiveView : Screen

    private fun encode(s: Screen) = when (s) {
        ListScreen -> "list"; SettingsView -> "settings"; ArchiveView -> "archive"; is LoopView -> "loop:${s.id}"; is Edit -> s.id?.let { "edit:$it" } ?: "new"
    }
    private fun decode(k: String): Screen = when {
        k == "settings" -> SettingsView
        k == "archive" -> ArchiveView
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
        val archived by remember { repo.observeArchived() }.collectAsStateWithLifecycle(null as List<LoopFull>?)
        var openArchived by remember { mutableStateOf<Long?>(null) }
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
        var lockOn by remember { mutableStateOf(settings.appLock) }
        var lockAfter by remember { mutableStateOf(settings.lockAfterMs) }
        val askPerms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            permTick++
            if (hasContacts()) scope.launch { repo.resyncAll() }
        }
        val openSystem = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { permTick++ }
        val allow: (Access) -> Unit = { a ->
            a.permissions()?.let { askPerms.launch(it) } ?: a.systemIntent(this@MainActivity)?.let { openSystem.launch(it) }
        }

        var sheet by remember { mutableStateOf<String?>(null) } // "note", "log", "close", "texts"

        val ctx = this@MainActivity

        // ---- Backup and export ----
        var backupOn by remember { mutableStateOf(settings.backupOn) }
        var lastExport by remember { mutableLongStateOf(settings.lastExportAt) }
        var exporting by remember { mutableStateOf(false) }
        val saveExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            val pass = app.pendingExport
            app.pendingExport = null
            if (uri == null) return@rememberLauncherForActivityResult
            if (pass == null) {
                // Lineside was closed while the save screen was open: don't leave an empty file behind.
                runCatching { android.provider.DocumentsContract.deleteDocument(contentResolver, uri) }
                android.widget.Toast.makeText(ctx, "The export was interrupted. Please try again.", android.widget.Toast.LENGTH_LONG).show()
                return@rememberLauncherForActivityResult
            }
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        val contents = repo.exportContents()
                        contentResolver.openOutputStream(uri, "wt")!!.use { Export.write(it, pass.toCharArray(), contents) }
                    }.onFailure { e ->
                        android.util.Log.w("Lineside", "export failed", e)
                        runCatching { android.provider.DocumentsContract.deleteDocument(contentResolver, uri) }
                    }.isSuccess
                }
                if (ok) { settings.lastExportAt = System.currentTimeMillis(); lastExport = settings.lastExportAt }
                android.widget.Toast.makeText(
                    ctx, if (ok) "Export saved. Keep the passphrase somewhere safe." else "Couldn't save the export. Nothing was written.",
                    android.widget.Toast.LENGTH_LONG,
                ).show()
            }
        }
        var importUri by rememberSaveable { mutableStateOf<String?>(null) }
        var importUi by remember { mutableStateOf<ImportUi>(ImportUi.Ready) }
        val pickImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) { importUi = ImportUi.Ready; importUri = uri.toString() }
        }
        val restored by app.restoredNotice
        LaunchedEffect(restored) {
            if (restored) {
                app.restoredNotice.value = false
                android.widget.Toast.makeText(
                    ctx, "Lineside was restored from a backup." + if (Texts.available) " Texts switches are off on this phone until you turn them on." else "",
                    android.widget.Toast.LENGTH_LONG,
                ).show()
            }
        }

        // ---- Texts (foss): two switches, Android's permission only at the first number, handed back when unused. ----
        var handing by remember { mutableStateOf(Texts.handingBack) }
        LaunchedEffect(loaded) { loaded?.let { Texts.reconcile(ctx, it); handing = Texts.handingBack } }
        val textsGranted = remember(tick, permTick, loaded, handing) { Texts.granted(ctx) }
        // What to do once Android answers the text-permission prompt (switch a number on, or open the picker).
        var afterSms by remember { mutableStateOf<(suspend () -> Unit)?>(null) }
        val askSms = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            permTick++
            val then = afterSms
            afterSms = null
            if (ok && then != null) scope.launch { Texts.noteGranted(ctx); then() }
            else if (!ok) android.widget.Toast.makeText(
                ctx, "Android didn't allow text access, so nothing was added or switched on. If it no longer asks, allow it in Settings → Apps → Lineside → Permissions.",
                android.widget.Toast.LENGTH_LONG,
            ).show()
        }
        val textLog by remember { repo.observeLog() }.collectAsStateWithLifecycle(emptyList())
        var picking by remember { mutableStateOf(false) }
        var picks by remember { mutableStateOf<List<Texts.Pick>?>(null) }
        var showTextLog by remember { mutableStateOf(false) }
        // Ends the process too: Android finishes handing the permission back when Lineside's process is gone.
        val closeApp = { finishAndRemoveTask(); android.os.Process.killProcess(android.os.Process.myPid()) }
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
                archivedCount = archived.orEmpty().size,
                onArchive = { screen = ArchiveView },
            )

            ArchiveView -> {
                ArchiveScreen(archived, onBack = { screen = ListScreen }, onOpen = { openArchived = it.loop.id })
                archived.orEmpty().firstOrNull { it.loop.id == openArchived }?.let { f ->
                    ArchivedSheet(
                        f, onDismiss = { openArchived = null },
                        onRestore = {
                            openArchived = null
                            scope.launch { repo.restore(f.loop.id) }
                            android.widget.Toast.makeText(ctx, "${f.loop.name} is back in your loops.", android.widget.Toast.LENGTH_SHORT).show()
                            if (archived.orEmpty().size <= 1) screen = ListScreen
                        },
                        onDelete = {
                            openArchived = null
                            scope.launch { repo.close(f.loop.id) }
                            settings.forgetLoop(f.loop.id)
                        },
                    )
                }
            }

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
                lock = LockState(
                    on = lockOn, available = AppLock.available(ctx), afterMs = lockAfter,
                    onLock = { want ->
                        if (!want) { lockOn = false; settings.appLock = false }
                        else AppLock.authenticate(this@MainActivity, "Turn on the app lock") { ok -> if (ok) { lockOn = true; settings.appLock = true; settings.lockedAwayAt = System.currentTimeMillis() } }
                    },
                    onAfter = { lockAfter = it; settings.lockAfterMs = it },
                ),
                backup = BackupState(
                    on = backupOn, refusedAt = settings.backupRefusedAt, lastExportAt = lastExport,
                    onBackup = { backupOn = it; settings.backupOn = it; if (!it) settings.backupRefusedAt = 0L },
                    // Everything in one file: ask for the screen lock again first, even if Lineside is already open.
                    onExport = {
                        if (AppLock.enabled(ctx)) AppLock.authenticate(this@MainActivity, "Export your loops") { ok -> if (ok) exporting = true }
                        else exporting = true
                    },
                    onImport = { AppLock.holdForOwnScreen(); pickImport.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                ),
                texts = if (!Texts.available) null else TextsState(
                    numbers = Texts.active(loops).size, granted = textsGranted, handingBack = handing,
                    canHandBack = android.os.Build.VERSION.SDK_INT >= 33, onLog = { showTextLog = true }, onCloseApp = closeApp,
                ),
            )

            is LoopView -> {
                var folded by remember(s.id) { mutableStateOf(settings.folded(s.id)) }
                var unfolded by remember(s.id) { mutableStateOf(settings.unfolded(s.id)) }
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
                    onTexts = if (Texts.available) ({ sheet = "texts" }) else null,
                    onReply = ::text,
                    folded = folded, unfolded = unfolded,
                    onFold = { day, fold ->
                        val k = day.toEpochDay()
                        folded = if (fold) folded + k else folded - k
                        unfolded = if (fold) unfolded - k else unfolded + k
                        settings.setFolded(full.loop.id, folded); settings.setUnfolded(full.loop.id, unfolded)
                    },
                )
                if (sheet == "texts") TextsSheet(
                    full, textsGranted, handing, android.os.Build.VERSION.SDK_INT >= 33,
                    onDismiss = { sheet = null },
                    onLoop = { on -> scope.launch { repo.setLoopTexts(full.loop.id, on) } },
                    onNumber = { n, on ->
                        when {
                            !on -> scope.launch { repo.setNumberTexts(full.loop.id, n.number, false) }
                            textsGranted -> scope.launch { repo.setNumberTexts(full.loop.id, n.number, true) }
                            else -> { afterSms = { repo.setNumberTexts(full.loop.id, n.number, true) }; askSms.launch(Manifest.permission.READ_SMS) }
                        }
                    },
                    onCloseApp = closeApp,
                    onLog = { showTextLog = true },
                    onPick = {
                        val look: suspend () -> Unit = { picks = null; picking = true; picks = Texts.recent(ctx, full) }
                        if (textsGranted) scope.launch { look() } else { afterSms = look; askSms.launch(Manifest.permission.READ_SMS) }
                    },
                )
                if (picking) PickTextsSheet(full, picks, onDismiss = { picking = false }) { chosen ->
                    picking = false
                    scope.launch {
                        Texts.addPicked(ctx, full.loop.id, chosen)
                        handing = Texts.handingBack
                        android.widget.Toast.makeText(ctx, "Added ${chosen.size} text${if (chosen.size == 1) "" else "s"}.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
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
        if (exporting) ExportSheet({ app.passphrase() }, onDismiss = { exporting = false }) { pass ->
            exporting = false
            app.pendingExport = pass
            AppLock.holdForOwnScreen()
            saveExport.launch("Lineside export ${java.time.LocalDate.now()}.zip")
        }
        importUri?.let { u ->
            val uri = Uri.parse(u)
            val name = remember(u) { runCatching { contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } }.getOrNull() }
            ImportSheet(name, importUi, onDismiss = { importUri = null; importUi = ImportUi.Ready }) { pass ->
                importUi = ImportUi.Working
                scope.launch {
                    importUi = withContext(Dispatchers.IO) {
                        runCatching {
                            // Lineside's own passphrases never start or end with a space, so a pasted newline is dropped.
                            val contents = contentResolver.openInputStream(uri)!!.use { Export.read(it, pass.trim().toCharArray()) }
                            repo.import(contents)
                        }.fold(
                            { ImportUi.Done(it.added, it.archived, it.alreadyHere) },
                            { e -> ImportUi.Failed((e as? Export.Problem)?.message ?: "Couldn't read that file.") },
                        )
                    }
                }
            }
        }
        if (showTextLog) TextLogSheet(textLog, loops.associate { it.loop.id to it.loop.name }) { showTextLog = false }
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
            "close" -> CloseLoopDialog(full, onDismiss = { onSheet(null) }, onArchive = {
                onSheet(null); onClosed()
                scope.launch { repo.archive(id) }
                android.widget.Toast.makeText(this, "Archived. Find it at the bottom of your loops.", android.widget.Toast.LENGTH_SHORT).show()
            }) {
                onSheet(null); onClosed()
                scope.launch { repo.close(id) }
                app.settings.forgetLoop(id)
            }
        }
        openEvent?.let { e ->
            EventSheet(full, e, onDismiss = { onEvent(null) }, onDelete = { scope.launch { repo.deleteEvent(e) }; onEvent(null) },
                onCalendar = { ev, w -> calendar(full, ev, w) })
        }
    }
}

