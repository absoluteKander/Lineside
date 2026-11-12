// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside.domain

import app.lineside.data.Event
import app.lineside.data.EventKind
import app.lineside.data.Loop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoopStatusTest {
    private val now = 1_800_000_000_000L
    private val h = 60 * 60 * 1000L
    private fun ev(kind: EventKind, hoursAgo: Long, text: String = "") = Event(loopId = 1, kind = kind, at = now - hoursAgo * h, text = text)

    @Test fun voicemailAfterYourCallMeansCallBack() {
        val e = listOf(ev(EventKind.CALL_OUT, 100), ev(EventKind.VOICEMAIL, 96, "results are in"))
        assertEquals(Ball.CALL_BACK, LoopStatus.ball(e))
        assertEquals("Call back · VM 4d", LoopStatus.statusLine(e, now))
    }

    @Test fun yourCallAfterTheirVoicemailMeansWaiting() {
        val e = listOf(ev(EventKind.VOICEMAIL, 96), ev(EventKind.CALL_OUT, 2))
        assertEquals(Ball.WAITING, LoopStatus.ball(e))
        assertEquals("Waiting · you called 2h", LoopStatus.statusLine(e, now))
    }

    @Test fun notesDoNotChangeWhoseMoveItIs() {
        val e = listOf(ev(EventKind.VOICEMAIL, 5, "results are in"), ev(EventKind.NOTE, 1, "ask for Dana"))
        assertEquals(Ball.CALL_BACK, LoopStatus.ball(e))
        // Your move: the card shows what you need to answer, not your own later note.
        assertEquals("results are in", LoopStatus.highlight(e)?.text)
    }

    @Test fun whileWaitingTheLatestWordsWin() {
        val e = listOf(ev(EventKind.CALL_OUT, 5), ev(EventKind.NOTE, 1, "ask for Dana"))
        assertEquals("ask for Dana", LoopStatus.highlight(e)?.text)
    }

    @Test fun newLoop() = assertEquals("Loop · new", LoopStatus.statusLine(emptyList(), now))

    @Test fun statusLineFitsTheCallScreen() {
        val e = listOf(ev(EventKind.CALL_OUT, 24 * 30))
        assertTrue(LoopStatus.statusLine(e, now).length <= 30)
    }

    @Test fun quietAfterThreeWeeksUnlessKept() {
        val old = Loop(id = 1, name = "x", createdAt = 0, lastActivityAt = now - 22 * 24 * h)
        assertTrue(LoopStatus.isQuiet(old, now))
        assertFalse(LoopStatus.isQuiet(old.copy(keptAt = now - 24 * h), now))
    }

    @Test fun counts() = assertEquals(
        "2 calls · 1 voicemail · 1 note",
        LoopStatus.counts(listOf(ev(EventKind.MISSED, 3), ev(EventKind.CALL_OUT, 2), ev(EventKind.VOICEMAIL, 3), ev(EventKind.NOTE, 1))),
    )

    @Test fun initials() {
        assertEquals("P", Initials.of("Dr. Patel's office"))
        assertEquals("NI", Initials.of("Northside Imaging"))
        assertEquals("Q", Initials.of("Quest Labs"))
        assertEquals("?", Initials.of("  "))
    }
}
