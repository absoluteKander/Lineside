// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside.domain

import app.lineside.data.Event
import app.lineside.data.EventKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One row in a day of the timeline. */
sealed interface TimelineItem {
    val at: Long

    /** A call, with what belongs to it: notes jotted during it, or the voicemail a missed call left. */
    data class Call(val call: Event, val attached: List<Event>) : TimelineItem { override val at get() = call.at }

    /** A day's texts, collapsed into one expandable row. */
    data class Texts(val texts: List<Event>) : TimelineItem { override val at get() = texts.first().at }

    /** A note or voicemail that doesn't belong to a call. */
    data class Single(val event: Event) : TimelineItem { override val at get() = event.at }
}

data class Day(val date: LocalDate, val items: List<TimelineItem>, val counts: String)

object Timeline {
    /** A voicemail logged this soon after a missed call from the same number is that call's voicemail. */
    private const val VOICEMAIL_WINDOW = 15 * 60 * 1000L

    private val callKinds = setOf(EventKind.CALL_IN, EventKind.CALL_OUT, EventKind.MISSED)

    /** Groups events by day (newest day first, newest item first within a day). */
    fun group(events: List<Event>, zone: ZoneId = ZoneId.systemDefault()): List<Day> =
        events.groupBy { Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() }
            .toSortedMap(compareByDescending { it })
            .map { (date, dayEvents) -> Day(date, items(dayEvents), LoopStatus.counts(dayEvents)) }

    private fun items(dayEvents: List<Event>): List<TimelineItem> {
        val calls = mutableListOf<TimelineItem.Call>()
        val attached = mutableMapOf<Long, MutableList<Event>>() // call event id → its notes/voicemail
        val singles = mutableListOf<TimelineItem.Single>()
        val texts = mutableListOf<Event>()
        for (e in dayEvents.sortedBy { it.at }) when {
            e.kind in callKinds -> calls += TimelineItem.Call(e, emptyList())
            e.kind == EventKind.TEXT -> texts += e
            // Jotted during a call: belongs to the latest call that started before it.
            e.kind == EventKind.NOTE && e.callOffsetSec != null ->
                calls.lastOrNull { it.call.at <= e.at }?.let { attached.getOrPut(it.call.id) { mutableListOf() } += e }
                    ?: run { singles += TimelineItem.Single(e) }
            e.kind == EventKind.VOICEMAIL ->
                calls.lastOrNull {
                    it.call.kind == EventKind.MISSED && e.at - it.call.at in 0..VOICEMAIL_WINDOW &&
                        (e.number == null || it.call.number == null || e.number == it.call.number) &&
                        attached[it.call.id].orEmpty().none { a -> a.kind == EventKind.VOICEMAIL }
                }?.let { attached.getOrPut(it.call.id) { mutableListOf() } += e }
                    ?: run { singles += TimelineItem.Single(e) }
            else -> singles += TimelineItem.Single(e)
        }
        val out = mutableListOf<TimelineItem>()
        calls.mapTo(out) { it.copy(attached = attached[it.call.id].orEmpty()) }
        out += singles
        if (texts.isNotEmpty()) out += TimelineItem.Texts(texts)
        return out.sortedByDescending { it.at }
    }
}
