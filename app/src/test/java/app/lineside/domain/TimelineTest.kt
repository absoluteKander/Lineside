// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside.domain

import app.lineside.data.Event
import app.lineside.data.EventKind
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineTest {
    private val zone = ZoneOffset.UTC
    private fun at(day: Int, h: Int, m: Int = 0) = LocalDate.of(2026, 11, day).atTime(h, m).toInstant(zone).toEpochMilli()
    private var nextId = 1L
    private fun ev(kind: EventKind, t: Long, text: String = "", offset: Int? = null, number: String? = "5550142299") =
        Event(id = nextId++, loopId = 1, kind = kind, at = t, text = text, callOffsetSec = offset, number = number)

    @Test fun groupsByDayNewestFirst() {
        val days = Timeline.group(listOf(ev(EventKind.CALL_OUT, at(3, 9)), ev(EventKind.MISSED, at(11, 9))), zone)
        assertEquals(listOf(LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 3)), days.map { it.date })
    }

    @Test fun notesDuringACallSitUnderThatCall() {
        val call = ev(EventKind.CALL_IN, at(11, 21, 30))
        val n1 = ev(EventKind.NOTE, at(11, 21, 31), "results normal", offset = 7)
        val later = ev(EventKind.NOTE, at(11, 22, 0), "standalone")
        val day = Timeline.group(listOf(n1, call, later), zone).single()
        val item = day.items.filterIsInstance<TimelineItem.Call>().single()
        assertEquals(listOf("results normal"), item.attached.map { it.text })
        assertTrue(day.items.any { it is TimelineItem.Single && it.event.text == "standalone" })
    }

    @Test fun voicemailSitsUnderTheMissedCallItFollows() {
        val missed = ev(EventKind.MISSED, at(11, 9, 29))
        val vm = ev(EventKind.VOICEMAIL, at(11, 9, 30), "call us back")
        val item = Timeline.group(listOf(missed, vm), zone).single().items.single() as TimelineItem.Call
        assertEquals("call us back", item.attached.single().text)
    }

    @Test fun voicemailLongAfterIsItsOwnRow() {
        val missed = ev(EventKind.MISSED, at(11, 9))
        val vm = ev(EventKind.VOICEMAIL, at(11, 13), "later")
        assertEquals(2, Timeline.group(listOf(missed, vm), zone).single().items.size)
    }

    @Test fun textsThreadByNumberWithinADay() {
        val a1 = ev(EventKind.TEXT, at(11, 9), "a1"); val a2 = ev(EventKind.TEXT, at(11, 15), "a2")
        val b = ev(EventKind.TEXT, at(11, 12), "b", number = "5552017788")
        val threads = Timeline.group(listOf(a1, b, a2), zone).single().items.filterIsInstance<TimelineItem.Texts>()
        assertEquals(listOf(listOf("a1", "a2"), listOf("b")), threads.map { t -> t.texts.map { it.text } })
    }

    @Test fun textsCollapseToOneRowPerDay() {
        val day = Timeline.group(listOf(ev(EventKind.TEXT, at(11, 14, 10)), ev(EventKind.TEXT, at(11, 14, 14)), ev(EventKind.CALL_OUT, at(11, 9))), zone).single()
        assertEquals(2, day.items.size)
        assertEquals(2, (day.items.first { it is TimelineItem.Texts } as TimelineItem.Texts).texts.size)
        assertEquals("1 call · 2 texts", day.counts)
    }

    @Test fun noteJustAfterACallBelongsToIt() {
        val call = ev(EventKind.CALL_IN, at(11, 21, 32)).copy(durationSec = 24)
        val note = ev(EventKind.NOTE, at(11, 21, 34), "after the call", number = null)
        val item = Timeline.group(listOf(call, note), zone).single().items.single() as TimelineItem.Call
        assertEquals("after the call", item.attached.single().text)
    }

    @Test fun noteLongAfterACallStandsAlone() {
        val call = ev(EventKind.CALL_IN, at(11, 9)).copy(durationSec = 60)
        val note = ev(EventKind.NOTE, at(11, 9, 30), "unrelated", number = null)
        assertEquals(2, Timeline.group(listOf(call, note), zone).single().items.size)
    }

    @Test fun backToBackMissedCallsFoldIntoOneLine() {
        val m = listOf(at(11, 21, 4), at(11, 21, 5), at(11, 21, 26)).map { ev(EventKind.MISSED, it) }
        val item = Timeline.group(m, zone).single().items.single() as TimelineItem.Calls
        assertEquals(m.map { it.id }, item.calls.map { it.id })
    }

    @Test fun differentKindsOrNumbersDontFold() {
        val evs = listOf(
            ev(EventKind.MISSED, at(11, 9)), ev(EventKind.CALL_IN, at(11, 10)),
            ev(EventKind.MISSED, at(11, 11)), ev(EventKind.MISSED, at(11, 12), number = "5552017788"),
        )
        assertEquals(4, Timeline.group(evs, zone).single().items.size)
    }
}
