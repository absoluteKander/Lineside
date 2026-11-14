// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside.domain

import app.lineside.data.Event
import app.lineside.data.EventKind
import app.lineside.data.Loop
import app.lineside.data.LoopFull
import app.lineside.data.LoopNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArchiveSearchTest {
    private val f = LoopFull(
        Loop(id = 1, name = "Kitchen cabinets", topic = "Install and trim", createdAt = 0, lastActivityAt = 0, archivedAt = 5),
        listOf(LoopNumber(id = 1, loopId = 1, number = "5550142299", key = "5550142299", label = "Mike")),
        listOf(
            Event(id = 1, loopId = 1, kind = EventKind.NOTE, at = 1, text = "Quote was 4200 for maple"),
            Event(id = 2, loopId = 1, kind = EventKind.TEXT, at = 2, text = "Coming Tuesday with the crown moulding"),
        ),
    )

    @Test fun emptyQueryShowsLatestWords() = assertEquals("Coming Tuesday with the crown moulding", ArchiveSearch.match(f, ""))
    @Test fun matchesNoteText() = assertEquals("Quote was 4200 for maple", ArchiveSearch.match(f, "maple"))
    @Test fun matchesNumberName() = assertEquals("Mike · 5550142299", ArchiveSearch.match(f, "mike"))
    @Test fun matchesDigits() = assertEquals("Mike · 5550142299", ArchiveSearch.match(f, "014-2299"))
    @Test fun matchesTopic() = assertEquals("Coming Tuesday with the crown moulding", ArchiveSearch.match(f, "trim"))
    @Test fun noMatchIsNull() = assertNull(ArchiveSearch.match(f, "plumbing"))
}
