// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.lineside.domain.LoopStatus
import app.lineside.domain.shouldNudge
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch

/**
 * Once a day: refresh every loop's call-screen copy (status lines and photos age), and nudge once about
 * any loop that has gone quiet. Runs on the phone only; there is no network to use.
 */
class DailyWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = App.of(applicationContext)
        app.repo.resyncAll()
        val now = System.currentTimeMillis()
        if (QuietNudge.canPost(app)) for (l in app.repo.openLoopsAll()) {
            if (LoopStatus.shouldNudge(l.loop, app.settings.nudgedAt(l.loop.id), now)) {
                QuietNudge.post(app, l.loop.id, l.loop.name)
                app.settings.setNudgedAt(l.loop.id, now)
            }
        }
        return Result.success()
    }

    companion object {
        fun schedule(ctx: Context) = WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            "daily", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<DailyWorker>(1, TimeUnit.DAYS).build(),
        )
    }
}

object QuietNudge {
    private const val CHANNEL = "quiet_loops"
    const val EXTRA_LOOP = "loopId"

    fun canPost(ctx: Context) =
        android.os.Build.VERSION.SDK_INT < 33 || ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun post(ctx: Context, loopId: Long, name: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Quiet loops", NotificationManager.IMPORTANCE_LOW).apply {
            description = "One reminder when a loop has had no activity for three weeks."
        })
        val open = PendingIntent.getActivity(
            ctx, loopId.toInt(),
            Intent(ctx, MainActivity::class.java).putExtra(EXTRA_LOOP, loopId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val keep = PendingIntent.getBroadcast(
            ctx, loopId.toInt(),
            Intent(ctx, KeepReceiver::class.java).putExtra(EXTRA_LOOP, loopId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_lineside)
            .setContentTitle("Still waiting on $name?")
            .setContentText("No calls or notes for three weeks. Keep it, or open it to close the loop.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, "Keep", keep)
            .build()
        runCatching { nm.notify(id(loopId), n) }
    }

    fun id(loopId: Long) = 1000 + loopId.toInt()
}

/** "Keep" on a quiet-loop nudge: the loop stays, and leaves the quiet list for another three weeks. */
class KeepReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getLongExtra(QuietNudge.EXTRA_LOOP, -1L).takeIf { it > 0 } ?: return
        val pending = goAsync()
        App.of(ctx).scope.launch {
            App.of(ctx).repo.keep(id)
            ctx.getSystemService(NotificationManager::class.java).cancel(QuietNudge.id(id))
            pending.finish()
        }
    }
}
