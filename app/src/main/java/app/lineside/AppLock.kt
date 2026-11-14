// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside

import android.app.Activity
import android.app.KeyguardManager
import android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK
import android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.lineside.ui.Line
import app.lineside.ui.LineButton
import app.lineside.ui.Wordmark

/**
 * The app lock: Lineside asks for the phone's own screen lock (fingerprint, face, PIN) when opened after being
 * away for a while. Nothing of its own to remember and nothing stored: Android does the checking.
 * It covers Lineside's screens and the share sheet. The pill during a call is yours already, so it stays out.
 */
object AppLock {
    /** The phone has a screen lock to lean on. Without one the app lock can't be on. */
    fun available(ctx: android.content.Context) = ctx.getSystemService(KeyguardManager::class.java).isDeviceSecure

    /** On by default where a screen lock exists; turning it off is a choice the user makes (in onboarding, later). */
    fun enabled(ctx: android.content.Context) = App.of(ctx).settings.appLock && available(ctx)

    fun shouldLock(ctx: android.content.Context): Boolean {
        if (!enabled(ctx)) return false
        val s = App.of(ctx).settings
        val away = s.lockedAwayAt
        return away == 0L || System.currentTimeMillis() - away > s.lockAfterMs
    }

    /** Android's prompt. [onDone] gets true when the user proved it's them. */
    fun authenticate(activity: Activity, title: String, onDone: (Boolean) -> Unit): CancellationSignal {
        val signal = CancellationSignal()
        val prompt = BiometricPrompt.Builder(activity)
            .setTitle(title)
            .setSubtitle("Lineside uses your phone's screen lock.")
            .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            .build()
        runCatching {
            prompt.authenticate(signal, activity.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onDone(true)
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onDone(false)
            })
        }.onFailure { android.util.Log.w("Lineside", "app lock prompt failed", it); onDone(false) }
        return signal
    }
}

/**
 * Wraps a screen: shows [content] only once unlocked. Locks again when the activity has been stopped for longer
 * than the chosen delay. While locked, only the wordmark and an Unlock button are drawn, so nothing shows through.
 */
@Composable
fun LockGate(activity: Activity, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val settings = App.of(ctx).settings
    var locked by remember { mutableStateOf(AppLock.shouldLock(ctx)) }
    var asking by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                // Away: remember when, so a quick switch back doesn't ask again.
                Lifecycle.Event.ON_STOP -> if (!locked) settings.lockedAwayAt = System.currentTimeMillis()
                Lifecycle.Event.ON_START -> if (!locked && AppLock.shouldLock(ctx)) { locked = true; attempt++ }
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    if (!locked) { content(); return }
    LaunchedEffect(attempt) {
        if (asking) return@LaunchedEffect
        asking = true
        AppLock.authenticate(activity, "Unlock Lineside") { ok ->
            asking = false
            if (ok) { locked = false; settings.lockedAwayAt = 0L }
        }
    }
    val c = Line.c
    Column(
        Modifier.fillMaxSize().background(c.header).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Wordmark(c.headerInk, c.header)
        Text("Locked. Your loops stay behind your phone's screen lock.", Modifier.padding(top = 18.dp, bottom = 24.dp), color = c.headerMuted, fontSize = 15.sp, lineHeight = 21.sp)
        LineButton("Unlock", { attempt++ }, Modifier.fillMaxWidth(), edge = c.headerInk, enabled = !asking)
    }
}
