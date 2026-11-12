// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside.calls

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.telecom.DisconnectCause
import android.telecom.TelecomManager
import androidx.core.app.NotificationCompat
import app.lineside.App
import app.lineside.R
import kotlinx.coroutines.launch

/**
 * Telecom launches this (android.telecom.action.POST_CALL) in the call-screening app whenever a call ends,
 * even if Lineside was asleep. No UI of its own: it logs the call to its loop, then hands off to the pill.
 */
class PostCallActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = App.of(this)
        CallNotice.cancel(app)
        CallWatcher.stop(app)
        val call = CallStore.get(app)
        CallStore.set(app, null)
        if (call == null) { Pill.removeNow(); finish(); return }

        val cause = intent.getIntExtra("android.telecom.extra.DISCONNECT_CAUSE", DisconnectCause.UNKNOWN)
        // Answered if we saw it go off-hook, or if it ended by someone hanging up (not missed or declined).
        val answered = call.answeredAt != null ||
            (call.incoming && (cause == DisconnectCause.LOCAL || cause == DisconnectCause.REMOTE)) || !call.incoming
        val ended = EndedCall(call, answered, System.currentTimeMillis())
        CallStore.lastEnded = ended
        val locked = ScreeningService.isLocked(this)

        app.scope.launch {
            call.loopId?.let { app.repo.addEvent(ended.toEvent(it)) }
            val content = if (app.settings.pillEnabled) CallContext.content(app, call.number, call.loopId) else null
            Pill.afterCall(app, content, locked)
        }
        finish()
    }
}

/** The doorway to the pill for calls that start on the lock screen. Android holds it, so nothing can freeze. */
object CallNotice {
    private const val ID = 7
    private const val CHANNEL = "during_call"

    fun post(ctx: Context, c: PillContent) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "During a call", NotificationManager.IMPORTANCE_LOW).apply {
            description = "A quiet shortcut to jot notes during a call you answered on the lock screen."
        })
        val open = Intent(ctx, PillLauncherActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        val pi = PendingIntent.getActivity(ctx, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val text = c.headline ?: if (c.loopId == null) "Not in your contacts" else c.label
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_lineside)
            .setContentTitle(c.title)
            .setContentText("$text · Tap to jot a note")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setSilent(true)
            .setTimeoutAfter(3 * 60 * 60 * 1000L) // in case the end of the call is never reported
            .build()
        runCatching { nm.notify(ID, n) }
    }

    fun cancel(ctx: Context) = ctx.getSystemService(NotificationManager::class.java).cancel(ID)
}

/** Invisible: opened by tapping the call notification. The tap brings Lineside forward, so the pill is allowed. */
class PillLauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = App.of(this)
        CallNotice.cancel(app)
        val call = CallStore.get(app)
        val inCall = runCatching { getSystemService(TelecomManager::class.java).isInCall }.getOrDefault(false)
        if (call != null && inCall) app.scope.launch {
            CallContext.content(app, call.number, call.loopId)?.let { c ->
                Pill.show(app, c) { CallWatcher.start(app) }
                Pill.expand(app)
            }
        }
        finish()
    }
}
