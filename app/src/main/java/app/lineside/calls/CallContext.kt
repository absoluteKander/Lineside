// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside.calls

import android.content.Context
import android.telephony.TelephonyManager
import app.lineside.App
import app.lineside.data.EventKind
import app.lineside.domain.Ago
import app.lineside.domain.Initials
import app.lineside.domain.LoopStatus

/** What Lineside should do for a call, decided in the first moment of it. */
sealed interface CallPlan {
    /** Not ours to touch: a permanent contact, an emergency number, a hidden caller. */
    data object StayOut : CallPlan
    data class ForLoop(val loopId: Long, val content: PillContent) : CallPlan
    data class Unknown(val content: PillContent) : CallPlan
}

object CallContext {
    /** Runs off the main thread. Also refreshes the loop's status line, so the call screen is current. */
    suspend fun plan(ctx: Context, number: String?): CallPlan {
        val app = App.of(ctx)
        if (number.isNullOrBlank() || isEmergency(ctx, number)) return CallPlan.StayOut
        val loopId = app.repo.loopsForNumber(number).let { ids ->
            // Two loops sharing a number: the most recently active one wins.
            ids.mapNotNull { app.repo.get(it) }.maxByOrNull { it.loop.lastActivityAt }?.loop?.id
        }
        if (loopId != null) {
            app.repo.resync(loopId)
            return content(ctx, number, loopId)?.let { CallPlan.ForLoop(loopId, it) } ?: CallPlan.StayOut
        }
        if (app.contacts.isPermanentContact(number)) return CallPlan.StayOut
        return CallPlan.Unknown(unknown(number))
    }

    suspend fun content(ctx: Context, number: String, loopId: Long?): PillContent? {
        if (loopId == null) return unknown(number)
        val full = App.of(ctx).repo.get(loopId) ?: return null
        val now = System.currentTimeMillis()
        val lines = mutableListOf<String>()
        val hi = LoopStatus.highlight(full.events)
        if (hi != null) lines += line(hi.kind, hi.text, now - hi.at)
        // Your own latest note, if it isn't already the highlight: often the thing you need on the call.
        full.events.filter { it.kind == EventKind.NOTE }.maxByOrNull { it.at }
            ?.takeIf { it.id != hi?.id }?.let { lines += line(it.kind, it.text, now - it.at) }
        if (full.loop.topic.isNotBlank()) lines.add(0, full.loop.topic)
        val status = LoopStatus.statusLine(full.events, now, full.loop.topic, full.loop.createdAt)
        val headline = hi?.let { line(it.kind, it.text, now - it.at) } ?: full.loop.topic.ifBlank { null }
        return PillContent(loopId, number, full.loop.name, "${Initials.of(full.loop.name)} · $status", lines, headline)
    }

    private fun unknown(number: String) = PillContent(null, number, number, "+ Start a loop", emptyList())

    private fun line(kind: EventKind, text: String, ago: Long): String {
        val what = when (kind) {
            EventKind.NOTE -> "Your note"; EventKind.VOICEMAIL -> "Voicemail"; EventKind.MISSED -> "Missed call"
            EventKind.TEXT -> "Text"; EventKind.CALL_IN -> "They called"; EventKind.CALL_OUT -> "You called"
        }
        return "$what · ${Ago.long(ago)}" + if (text.isBlank()) "" else ": \"${text.take(140)}\""
    }

    private fun isEmergency(ctx: Context, number: String): Boolean {
        if (number.count { it.isDigit() } <= 3) return true // 911, 112, 999 and other short codes
        return runCatching { ctx.getSystemService(TelephonyManager::class.java).isEmergencyNumber(number) }.getOrDefault(false)
    }
}
