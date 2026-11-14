// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import app.lineside.data.Event
import app.lineside.data.EventKind
import app.lineside.data.EventSource
import app.lineside.data.LoopFull
import app.lineside.data.NumberKey
import app.lineside.data.TextLog
import app.lineside.ui.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Texts from a loop's numbers, added to its timeline. Android's text permission covers every message on the
 * phone, so Lineside narrows it itself:
 *  - nothing is read unless a loop's switch AND that number's switch are on;
 *  - only the inbox, only those numbers, only texts that arrived after the number was switched on;
 *  - every look is written to the text access log;
 *  - when no number anywhere is switched on, Lineside hands the permission back to Android.
 * Never asks for RECEIVE_SMS: Lineside isn't told when texts arrive, it looks when opened and once a day.
 * Only in the foss build; the Play build has no text permission at all.
 */
object Texts {
    val available get() = BuildConfig.READS_SMS

    fun granted(ctx: Context) = available && ctx.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    /**
     * Set once Lineside has asked Android to take the permission back. Android finishes that when Lineside's
     * process ends, so until then the permission still shows as granted. Lives only as long as the process does.
     */
    @Volatile var handingBack = false
        private set

    /** Every number with its switch on, in a loop with its switch on. */
    fun active(loops: List<LoopFull>) = loops.filter { it.loop.textsOn }.flatMap { f -> f.numbers.filter { it.textsSince != null }.map { f to it } }

    /** After any change to loops or switches: if nothing needs texts any more, give the permission back. */
    suspend fun reconcile(ctx: Context, loops: List<LoopFull>) {
        if (!granted(ctx) || handingBack || active(loops).isNotEmpty()) return
        if (Build.VERSION.SDK_INT < 33) return // Android 12 can't hand a permission back; Settings explains how to remove it.
        ctx.revokeSelfPermissionsOnKill(listOf(Manifest.permission.READ_SMS))
        handingBack = true
        App.of(ctx).repo.log(TextLog(at = System.currentTimeMillis(), what = "Lineside handed text access back to Android"))
    }

    suspend fun noteGranted(ctx: Context) =
        App.of(ctx).repo.log(TextLog(at = System.currentTimeMillis(), what = "Android gave Lineside text access"))

    private val lock = Mutex()

    /** Looks for new texts from switched-on numbers and adds them. [why] goes in the access log. */
    suspend fun importNow(ctx: Context, why: String) = lock.withLock {
        if (!granted(ctx) || handingBack) return@withLock
        val repo = App.of(ctx).repo
        val now = System.currentTimeMillis()
        for (pairs in active(repo.openLoopsAll()).groupBy { it.first.loop.id }.values) {
            val full = pairs.first().first
            val nums = pairs.map { it.second }
            val known = repo.smsIds(full.loop.id)
            var found = 0
            val fresh = mutableListOf<Event>()
            for (n in nums) {
                val msgs = inbox(ctx, n.key, n.textsSince!!)
                found += msgs.size
                for (m in msgs) if (m.id !in known && fresh.none { it.smsId == m.id })
                    fresh += Event(loopId = full.loop.id, kind = EventKind.TEXT, at = m.at, text = m.body, number = n.number,
                        source = EventSource.IMPORTED, smsId = m.id)
            }
            repo.addTexts(full.loop.id, fresh)
            repo.log(TextLog(
                at = now, loopId = full.loop.id, what = why,
                numbers = nums.joinToString(", ") { n -> listOfNotNull(n.label.ifBlank { null }, Fmt.last4(n.number)).joinToString(" ") },
                found = found, added = fresh.size,
            ))
        }
    }

    private data class Msg(val id: Long, val at: Long, val body: String)

    /** A text the user can pick by hand: from one of the loop's numbers, in the last [PICK_DAYS] days. */
    data class Pick(val id: Long, val at: Long, val body: String, val number: String, val who: String, val added: Boolean)

    const val PICK_DAYS = 90

    /**
     * Texts from the loop's numbers to pick from, newest first. Needs the permission, but not the switches:
     * picking is the one-off way in. The look itself goes in the access log.
     */
    suspend fun recent(ctx: Context, full: LoopFull): List<Pick> = lock.withLock {
        if (!granted(ctx)) return@withLock emptyList()
        val repo = App.of(ctx).repo
        val known = repo.smsIds(full.loop.id)
        val since = System.currentTimeMillis() - PICK_DAYS * 24 * 60 * 60_000L
        val out = mutableListOf<Pick>()
        for (n in full.numbers.sortedBy { it.id }) for (m in inbox(ctx, n.key, since))
            out += Pick(m.id, m.at, m.body, n.number, n.label.ifBlank { Fmt.last4(n.number) }, added = m.id in known)
        repo.log(TextLog(
            at = System.currentTimeMillis(), loopId = full.loop.id, what = "Looked, to pick by hand",
            numbers = full.numbers.joinToString(", ") { n -> listOfNotNull(n.label.ifBlank { null }, Fmt.last4(n.number)).joinToString(" ") },
            found = out.size, added = 0,
        ))
        out.sortedByDescending { it.at }
    }

    /** The picked texts go on the timeline. Then, if no switch anywhere needs the permission, it's handed back. */
    suspend fun addPicked(ctx: Context, loopId: Long, picks: List<Pick>) {
        val repo = App.of(ctx).repo
        val events = picks.filter { !it.added }.distinctBy { it.id }.map {
            Event(loopId = loopId, kind = EventKind.TEXT, at = it.at, text = it.body, number = it.number, source = EventSource.IMPORTED, smsId = it.id)
        }
        repo.addTexts(loopId, events)
        repo.log(TextLog(at = System.currentTimeMillis(), loopId = loopId, what = "Picked by hand", numbers = picks.map { it.who }.distinct().joinToString(", "), found = picks.size, added = events.size))
        reconcile(ctx, repo.openLoopsAll())
    }

    /**
     * The only query Lineside makes of the phone's messages: received texts whose sender ends in this number,
     * newer than [since]. Sender formats vary ("+1555…", "555…"), so it asks by the number's last ten digits and
     * then checks each match exactly.
     */
    private suspend fun inbox(ctx: Context, key: String, since: Long): List<Msg> = withContext(Dispatchers.IO) {
        if (key.length < 7) return@withContext emptyList()
        val out = mutableListOf<Msg>()
        runCatching {
            ctx.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.DATE, Telephony.Sms.BODY),
                "${Telephony.Sms.ADDRESS} LIKE ? AND ${Telephony.Sms.DATE} > ?",
                arrayOf("%$key", since.toString()),
                "${Telephony.Sms.DATE} ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    if (!NumberKey.matches(c.getString(1).orEmpty(), key)) continue
                    out += Msg(c.getLong(0), c.getLong(2), c.getString(3).orEmpty())
                }
            }
        }
        out
    }
}
