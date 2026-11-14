// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside.domain

import app.lineside.data.EventKind
import org.junit.Assert.assertEquals
import org.junit.Test

class SharedTest {
    @Test fun findsNumbersHoweverFormatted() =
        assertEquals(listOf("5550142299", "+15552017788"), Shared.numbers("Call (555) 014-2299 or +1 555-201-7788, not 555-0142"))

    @Test fun sameNumberTwiceIsOne() = assertEquals(1, Shared.numbers("+15550142299 / (555) 014-2299").size)

    @Test fun audioIsAVoicemail() =
        assertEquals(EventKind.VOICEMAIL, Shared.guess(null, null, "audio/mpeg", "voicemail.mp3").kind)

    @Test fun plainTextIsAText() = assertEquals(EventKind.TEXT, Shared.guess("Your results are in", null, "text/plain", null).kind)

    @Test fun mentionOfVoicemailMakesItOne() =
        assertEquals(EventKind.VOICEMAIL, Shared.guess("New voicemail from (555) 014-2299", null, "text/plain", null).kind)

    @Test fun cleanDropsLinksAndBareNumbers() = assertEquals(
        "Hi, it's the lab. Please call back.",
        Shared.clean("(555) 014-2299\nHi, it's the lab. Please call back.\nhttps://voice.google.com/u/0/voicemail"),
    )

    @Test fun numberFromFileName() =
        assertEquals(listOf("+15550142299"), Shared.guess(null, null, "audio/ogg", "Voicemail +15550142299 2026-11-14.ogg").numbers)
}
