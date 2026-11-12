// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import app.lineside.contacts.ContactSurface
import app.lineside.data.Db
import app.lineside.data.LoopRepo

/** Hand-wired dependencies; small enough not to need a DI framework. */
class App : Application() {
    val db by lazy { Db.open(this) }
    val contacts by lazy { ContactSurface(this) }
    val repo by lazy { LoopRepo(db, contacts) }
    val settings by lazy { Settings(this) }
    /** For work started from call hooks and the pill, which outlive any screen. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    companion object {
        fun of(ctx: Context) = ctx.applicationContext as App
    }
}
