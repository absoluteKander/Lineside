// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside.calls

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import app.lineside.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Android hands every call (incoming and outgoing) to the call-screening app as it starts, before it rings.
 * Lineside only reads the number. It never blocks, silences or changes a call.
 */
class ScreeningService : CallScreeningService() {
    override fun onScreenCall(details: Call.Details) {
        val app = App.of(this)
        val number = details.handle?.schemeSpecificPart
        val incoming = details.callDirection == Call.Details.DIRECTION_INCOMING
        val plan = try {
            // Telecom allows a few seconds; we cap our lookup well under that.
            runBlocking(Dispatchers.IO) { withTimeoutOrNull(2_000) { CallContext.plan(app, number) } } ?: CallPlan.StayOut
        } catch (e: Exception) {
            Log.w("Lineside", "screening lookup failed", e); CallPlan.StayOut
        } finally {
            respondToCall(details, CallResponse.Builder().build()) // allow, change nothing
        }

        val content = when (plan) {
            CallPlan.StayOut -> { CallStore.set(app, null); return }
            is CallPlan.ForLoop -> plan.content
            is CallPlan.Unknown -> if (app.settings.unknownCallerPill) plan.content else { CallStore.set(app, null); return }
        }
        CallStore.set(app, ActiveCall(number!!, incoming, System.currentTimeMillis(), content.loopId))
        if (!app.settings.pillEnabled) { CallWatcher.start(app); return }

        if (isLocked(this)) {
            // A pill added now would hide behind the lock screen (and can freeze the app). Android holds a
            // notification for us instead; tapping it after unlocking opens the pill.
            CallNotice.post(app, content)
        } else {
            Pill.show(this, content) { CallWatcher.start(app) }
        }
    }

    companion object {
        /** Screen off counts as locked: the lock screen often engages a moment after the screen goes dark. */
        fun isLocked(ctx: Context) =
            ctx.getSystemService(KeyguardManager::class.java).isKeyguardLocked ||
                !ctx.getSystemService(PowerManager::class.java).isInteractive
    }
}

/**
 * Follows the call's state in-process: answered (for note timestamps and call length) and ended.
 * Needs the optional Phone permission; without it, calls are still logged, just without their length.
 * No foreground service: Android refuses to start one from the screening hook.
 */
object CallWatcher {
    private var cb: TelephonyCallback? = null
    private var sawActive = false

    fun start(ctx: Context) {
        val app = ctx.applicationContext
        if (cb != null) return
        if (app.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return
        sawActive = false
        val startedWatching = System.currentTimeMillis()
        val tm = app.getSystemService(TelephonyManager::class.java)
        val c = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) {
                when (state) {
                    TelephonyManager.CALL_STATE_RINGING -> sawActive = true
                    TelephonyManager.CALL_STATE_OFFHOOK -> {
                        // Already off-hook when we started watching, well after the call began: we came in late
                        // (from the lock-screen notification) and don't know when it was answered.
                        val call = CallStore.get(app)
                        val late = !sawActive && call != null && startedWatching - call.startedAt > 3_000
                        sawActive = true
                        if (!late) CallStore.update(app) { if (it.answeredAt == null) it.copy(answeredAt = System.currentTimeMillis()) else it }
                        Pill.markInCall(late)
                    }
                    TelephonyManager.CALL_STATE_IDLE -> if (sawActive) { Pill.callIdle(); stop(app) }
                }
            }
        }
        runCatching { tm.registerTelephonyCallback(app.mainExecutor, c); cb = c }
            .onFailure { Log.w("Lineside", "call-state listener failed", it) }
    }

    fun stop(ctx: Context) {
        cb?.let { runCatching { ctx.getSystemService(TelephonyManager::class.java).unregisterTelephonyCallback(it) } }
        cb = null
    }
}
