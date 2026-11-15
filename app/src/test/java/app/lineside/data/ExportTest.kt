// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside.data

import net.lingala.zip4j.io.outputstream.ZipOutputStream
import net.lingala.zip4j.model.ZipParameters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.ZoneId

class ExportTest {
    private val zone = ZoneId.of("America/New_York")

    private fun sample(): Export.Contents {
        val clinic = LoopFull(
            Loop(id = 7, name = "Clinic", topic = "MRI results", createdAt = 1_000, lastActivityAt = 9_000, keptAt = 5_000, rawContactId = 42, textsOn = true),
            listOf(LoopNumber(id = 1, loopId = 7, number = "5553334444", key = NumberKey.of("5553334444"), label = "front desk", textsSince = 2_000)),
            listOf(
                Event(id = 1, loopId = 7, kind = EventKind.VOICEMAIL, at = 3_000, text = "Call us back <today> & ask for Sam", number = "5553334444", source = EventSource.SHARED),
                Event(id = 2, loopId = 7, kind = EventKind.CALL_IN, at = 4_000, durationSec = 125, number = "5553334444", source = EventSource.CALL),
                Event(id = 3, loopId = 7, kind = EventKind.NOTE, at = 4_060, text = "Results Friday", callOffsetSec = 30),
                Event(id = 4, loopId = 7, kind = EventKind.TEXT, at = 6_000, text = "Reminder", number = "5553334444", source = EventSource.IMPORTED, smsId = 991),
            ),
        )
        val old = LoopFull(Loop(id = 9, name = "Roof claim", createdAt = 500, lastActivityAt = 700, archivedAt = 800), emptyList(), emptyList())
        val log = listOf(
            TextLog(id = 1, at = 2_000, what = "Access given"),
            TextLog(id = 2, at = 6_100, loopId = 7, what = "Opened Lineside", numbers = "Front desk ··4444", found = 1, added = 1),
        )
        return Export.Contents(listOf(clinic, old), log, exportedAt = 10_000)
    }

    @Test fun jsonRoundTripKeepsWhatMatters() {
        val back = Export.fromJson(Export.toJson(sample()))
        assertEquals(2, back.loops.size)
        val c = back.loops.first { it.loop.name == "Clinic" }
        assertEquals("MRI results", c.loop.topic)
        assertEquals(1_000L, c.loop.createdAt)
        assertEquals(5_000L, c.loop.keptAt)
        assertEquals("front desk", c.numbers.single().label)
        assertEquals(NumberKey.of("5553334444"), c.numbers.single().key)
        assertEquals(listOf(EventKind.VOICEMAIL, EventKind.CALL_IN, EventKind.NOTE, EventKind.TEXT), c.events.map { it.kind })
        assertEquals(125, c.events[1].durationSec)
        assertEquals(30, c.events[2].callOffsetSec)
        assertEquals(EventSource.SHARED, c.events[0].source)
        assertEquals(800L, back.loops.first { it.loop.name == "Roof claim" }.loop.archivedAt)
        assertEquals(2, back.log.size)
        assertEquals(7L, back.log.first { it.loopId != null }.loopId)
    }

    @Test fun phoneBoundThingsNeverLeave() {
        val json = Export.toJson(sample())
        // Contact ids, message ids and texts switches belong to this phone (and this phone's consent).
        assertFalse(json.contains("rawContactId"))
        assertFalse(json.contains("smsId"))
        assertFalse(json.contains("textsOn"))
        assertFalse(json.contains("textsSince"))
        val c = Export.fromJson(json).loops.first { it.loop.name == "Clinic" }
        assertNull(c.loop.rawContactId)
        assertFalse(c.loop.textsOn)
        assertNull(c.numbers.single().textsSince)
        assertNull(c.events.last().smsId)
    }

    @Test fun pageEscapesWhatWasTyped() {
        val html = Export.toHtml(sample(), zone)
        assertTrue(html.contains("Call us back &lt;today&gt; &amp; ask for Sam"))
        assertFalse(html.contains("<today>"))
        assertTrue(html.contains("Archived"))
        assertTrue(html.contains("Text access log"))
        assertTrue(html.contains("not a medical or official record"))
        // A page that can be opened anywhere, offline: nothing fetched from outside.
        assertFalse(html.contains("http://") || html.contains("https://") || html.contains("<script"))
    }

    @Test fun encryptedZipRoundTrip() {
        val out = ByteArrayOutputStream()
        Export.write(out, "correct horse battery staple ok".toCharArray(), sample(), zone)
        val bytes = out.toByteArray()
        // The words inside must not be readable in the file.
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains("MRI results"))
        val back = Export.read(ByteArrayInputStream(bytes), "correct horse battery staple ok".toCharArray())
        assertEquals(setOf("Clinic", "Roof claim"), back.loops.map { it.loop.name }.toSet())
    }

    @Test fun wrongPassphraseSaysSo() {
        val out = ByteArrayOutputStream()
        Export.write(out, "the right one here".toCharArray(), sample(), zone)
        try {
            Export.read(ByteArrayInputStream(out.toByteArray()), "the wrong one here".toCharArray())
            fail("opened with the wrong passphrase")
        } catch (e: Export.Problem.WrongPassphrase) { /* expected */ }
    }

    @Test fun unencryptedOrForeignFilesAreRefused() {
        fun zip(name: String, body: String, encrypt: Boolean): ByteArray {
            val out = ByteArrayOutputStream()
            ZipOutputStream(out, if (encrypt) "some passphrase".toCharArray() else null).use { z ->
                z.putNextEntry(ZipParameters().apply {
                    fileNameInZip = name
                    if (encrypt) { isEncryptFiles = true; encryptionMethod = net.lingala.zip4j.model.enums.EncryptionMethod.AES }
                })
                z.write(body.toByteArray()); z.closeEntry()
            }
            return out.toByteArray()
        }
        val json = Export.toJson(sample())
        for (bytes in listOf(zip("lineside.json", json, encrypt = false), zip("other.txt", "hi", encrypt = true), "not a zip".toByteArray())) {
            try {
                Export.read(ByteArrayInputStream(bytes), "some passphrase".toCharArray())
                fail("accepted a file Lineside didn't make")
            } catch (e: Export.Problem.NotLineside) { /* expected */ }
        }
    }

    @Test fun newerFormatAsksForUpdate() {
        val newer = Export.toJson(sample()).replace("\"version\": ${Export.VERSION}", "\"version\": ${Export.VERSION + 1}")
        try { Export.fromJson(newer); fail() } catch (e: Export.Problem.Newer) { /* expected */ }
        try { Export.fromJson("{\"format\":\"something-else\"}"); fail() } catch (e: Export.Problem.NotLineside) { /* expected */ }
    }

    @Test fun unknownKindsBecomeNotesRatherThanVanish() {
        val json = Export.toJson(sample()).replace("\"kind\": \"VOICEMAIL\"", "\"kind\": \"FAX\"")
        val c = Export.fromJson(json).loops.first { it.loop.name == "Clinic" }
        assertEquals(EventKind.NOTE, c.events.first().kind)
        assertEquals("Call us back <today> & ask for Sam", c.events.first().text)
    }
}
