// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside.domain

import app.lineside.data.Event
import app.lineside.data.EventKind
import app.lineside.data.Loop

/** Whose move it is. */
enum class Ball { CALL_BACK, WAITING }

object LoopStatus {
    const val DAY = 24 * 60 * 60 * 1000L
    /** A loop with no activity (and no "Keep") for this long shows under "Quiet". */
    const val QUIET_AFTER = 21 * DAY

    private val contactKinds = setOf(EventKind.CALL_IN, EventKind.CALL_OUT, EventKind.MISSED, EventKind.VOICEMAIL, EventKind.TEXT)

    /** The latest call/voicemail/text, ignoring the user's own notes. */
    fun lastContact(events: List<Event>): Event? =
        events.filter { it.kind in contactKinds }.maxByOrNull { it.at }

    /**
     * They reached out and you haven't answered yet (missed call, voicemail, text) → call back.
     * Otherwise (you called, you talked, or nothing yet) → you're waiting on them.
     */
    fun ball(events: List<Event>): Ball = when (lastContact(events)?.kind) {
        EventKind.MISSED, EventKind.VOICEMAIL, EventKind.TEXT -> Ball.CALL_BACK
        else -> Ball.WAITING
    }

    fun isQuiet(loop: Loop, now: Long): Boolean =
        now - maxOf(loop.lastActivityAt, loop.keptAt ?: 0L) >= QUIET_AFTER

    /**
     * The most useful single thing to show on a card. When it's your move, that's what they said or did
     * (the voicemail you need to answer). Otherwise, the latest event with words in it, else the latest event.
     */
    fun highlight(events: List<Event>): Event? {
        if (ball(events) == Ball.CALL_BACK) return lastContact(events)
        return events.filter { it.text.isNotBlank() }.maxByOrNull { it.at } ?: events.maxByOrNull { it.at }
    }

    fun counts(events: List<Event>): String {
        val calls = events.count { it.kind in setOf(EventKind.CALL_IN, EventKind.CALL_OUT, EventKind.MISSED) }
        val parts = listOfNotNull(
            plural(calls, "call"),
            plural(events.count { it.kind == EventKind.VOICEMAIL }, "voicemail"),
            plural(events.count { it.kind == EventKind.TEXT }, "text"),
            plural(events.count { it.kind == EventKind.NOTE }, "note"),
        )
        return parts.joinToString(" · ")
    }

    private fun plural(n: Int, word: String) = if (n == 0) null else "$n $word" + if (n == 1) "" else "s"

    /**
     * The short line the phone app shows under the loop's name on the call screen (as the number's label).
     * Kept short: Google Phone truncates around 30 characters. e.g. "Call back · VM 4d", "Waiting · you called 2h".
     */
    fun statusLine(events: List<Event>, now: Long, topic: String = "", createdAt: Long = now): String {
        // Nothing has happened yet: what it's about is the most useful thing to show.
        val last = lastContact(events) ?: return topic.trim().take(30).ifBlank { "Waiting · opened ${Ago.short(now - createdAt)}" }
        val ago = Ago.short(now - last.at)
        val what = when (last.kind) {
            EventKind.VOICEMAIL -> "VM $ago"
            EventKind.MISSED -> "missed $ago"
            EventKind.TEXT -> "texted $ago"
            EventKind.CALL_OUT -> "you called $ago"
            EventKind.CALL_IN -> "talked $ago"
            EventKind.NOTE -> "note $ago"
        }
        return (if (ball(events) == Ball.CALL_BACK) "Call back · " else "Waiting · ") + what
    }
}

object Ago {
    /** "now", "5m", "3h", "4d", "6w". */
    fun short(ms: Long): String {
        val m = ms / 60_000
        return when {
            m < 1 -> "now"
            m < 60 -> "${m}m"
            m < 60 * 24 -> "${m / 60}h"
            m < 60 * 24 * 14 -> "${m / (60 * 24)}d"
            else -> "${m / (60 * 24 * 7)}w"
        }
    }

    /** "just now", "5 minutes ago", "yesterday", "4 days ago". */
    fun long(ms: Long): String {
        val m = ms / 60_000
        val h = m / 60; val d = h / 24
        return when {
            m < 1 -> "just now"
            m < 60 -> if (m == 1L) "1 minute ago" else "$m minutes ago"
            h < 24 -> if (h == 1L) "1 hour ago" else "$h hours ago"
            d == 1L -> "yesterday"
            d < 14 -> "$d days ago"
            else -> "${d / 7} weeks ago"
        }
    }
}

object Initials {
    private val skip = setOf("dr", "the", "of", "mr", "mrs", "ms", "and", "office", "clinic", "lab", "labs", "inc", "llc")

    /** "Dr. Patel's office" → "P", "Northside Imaging" → "NI", "Quest Labs" → "Q". */
    fun of(name: String): String = name
        .split(' ', '-', '.', '&', '/')
        .map { it.trim().removeSuffix("'s").removeSuffix("’s") }
        .filter { it.isNotBlank() && it.lowercase() !in skip && it.first().isLetterOrDigit() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { name.trim().take(1).uppercase().ifEmpty { "?" } }
}
