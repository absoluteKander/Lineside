// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import app.lineside.contacts.ContactSurface
import app.lineside.data.Db
import app.lineside.data.LoopRepo

/** Hand-wired dependencies; small enough not to need a DI framework. */
class App : Application() {
    val db by lazy { Db.open(this) }
    val contacts by lazy { ContactSurface(this) { settings.discreetCallScreen } }
    val repo by lazy { LoopRepo(db, contacts) }
    val settings by lazy { Settings(this) }
    /** For work started from call hooks and the pill, which outlive any screen. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        DailyWorker.schedule(this)
        // Just restored from a backup or moved from another phone: let go of what belonged to that phone.
        if (settings.restoredPending) scope.launch {
            repo.forgetOldPhone()
            settings.lockedAwayAt = 0L
            settings.restoredPending = false
            restoredNotice.value = true
        }
    }

    /** Shown once after a restore. */
    val restoredNotice = androidx.compose.runtime.mutableStateOf(false)

    /** The EFF large wordlist, for suggesting export passphrases. */
    val words: List<String> by lazy { assets.open("eff_large_wordlist.txt").bufferedReader().readLines().filter { it.isNotBlank() } }

    /**
     * The export's passphrase while Android's "save to" screen is open, so it survives the activity being recreated
     * meanwhile. Cleared as soon as the file is written or the save is cancelled; never written anywhere.
     */
    var pendingExport: String? = null

    fun passphrase() = app.lineside.domain.Passphrase.suggest(words)

    companion object {
        fun of(ctx: Context) = ctx.applicationContext as App
    }
}
