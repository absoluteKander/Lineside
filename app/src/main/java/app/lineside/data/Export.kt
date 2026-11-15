// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside.data

import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.io.inputstream.ZipInputStream
import net.lingala.zip4j.io.outputstream.ZipOutputStream
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Lineside's export: every loop, archived ones too, and the text access log. Two files go in one AES-256 ZIP:
 * `lineside.json` (complete, and what import reads back) and `Lineside.html` (the same, readable in any browser).
 */
object Export {
    const val FORMAT = "lineside-export"
    const val VERSION = 1
    const val JSON_NAME = "lineside.json"
    const val HTML_NAME = "Lineside.html"

    /** What an export holds. Ids are the exporting phone's; import gives everything new ones. */
    data class Contents(val loops: List<LoopFull>, val log: List<TextLog>, val exportedAt: Long)

    sealed class Problem(message: String) : Exception(message) {
        class WrongPassphrase : Problem("That passphrase doesn't open this file.")
        class NotLineside : Problem("This isn't a Lineside export.")
        class Newer : Problem("This export is from a newer Lineside. Update Lineside, then import it.")
    }

    // ---- JSON ----

    fun toJson(c: Contents): String = JSONObject().apply {
        put("format", FORMAT)
        put("version", VERSION)
        put("exportedAt", c.exportedAt)
        put("loops", JSONArray().apply {
            for (f in c.loops) put(JSONObject().apply {
                val l = f.loop
                put("id", l.id)
                put("name", l.name)
                put("topic", l.topic)
                put("createdAt", l.createdAt)
                put("lastActivityAt", l.lastActivityAt)
                l.keptAt?.let { put("keptAt", it) }
                l.archivedAt?.let { put("archivedAt", it) }
                put("numbers", JSONArray().apply {
                    for (n in f.numbers.sortedBy { it.id }) put(JSONObject().put("number", n.number).put("label", n.label))
                })
                put("events", JSONArray().apply {
                    for (e in f.events.sortedWith(compareBy({ it.at }, { it.id }))) put(JSONObject().apply {
                        put("kind", e.kind.name)
                        put("at", e.at)
                        put("text", e.text)
                        e.durationSec?.let { put("durationSec", it) }
                        e.number?.let { put("number", it) }
                        put("source", e.source.name)
                        e.callOffsetSec?.let { put("callOffsetSec", it) }
                    })
                })
            })
        })
        put("textLog", JSONArray().apply {
            for (t in c.log.sortedWith(compareBy({ it.at }, { it.id }))) put(JSONObject().apply {
                put("at", t.at)
                t.loopId?.let { put("loopId", it) }
                put("what", t.what)
                put("numbers", t.numbers)
                put("found", t.found)
                put("added", t.added)
            })
        })
    }.toString(1)

    fun fromJson(s: String): Contents {
        val o = runCatching { JSONObject(s) }.getOrElse { throw Problem.NotLineside() }
        if (o.optString("format") != FORMAT) throw Problem.NotLineside()
        if (o.optInt("version", 0) > VERSION) throw Problem.Newer()
        val loops = o.optJSONArray("loops").objects().map { j ->
            val id = j.getLong("id")
            LoopFull(
                Loop(
                    id = id, name = j.getString("name"), topic = j.optString("topic"),
                    createdAt = j.getLong("createdAt"), lastActivityAt = j.getLong("lastActivityAt"),
                    keptAt = j.optLongOrNull("keptAt"), archivedAt = j.optLongOrNull("archivedAt"),
                ),
                j.optJSONArray("numbers").objects().map { n ->
                    LoopNumber(loopId = id, number = n.getString("number"), key = NumberKey.of(n.getString("number")), label = n.optString("label"))
                },
                j.optJSONArray("events").objects().mapNotNull { e ->
                    // A kind or source this version doesn't know: keep the event as a note rather than lose it.
                    val kind = runCatching { EventKind.valueOf(e.getString("kind")) }.getOrDefault(EventKind.NOTE)
                    val source = runCatching { EventSource.valueOf(e.optString("source")) }.getOrDefault(EventSource.MANUAL)
                    Event(
                        loopId = id, kind = kind, at = e.getLong("at"), text = e.optString("text"),
                        durationSec = e.optIntOrNull("durationSec"), number = e.optStringOrNull("number"),
                        source = source, callOffsetSec = e.optIntOrNull("callOffsetSec"),
                    )
                },
            )
        }
        val log = o.optJSONArray("textLog").objects().map { t ->
            TextLog(
                at = t.getLong("at"), loopId = t.optLongOrNull("loopId"), what = t.getString("what"),
                numbers = t.optString("numbers"), found = t.optInt("found"), added = t.optInt("added"),
            )
        }
        return Contents(loops, log, o.optLong("exportedAt"))
    }

    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }
    private fun JSONObject.optLongOrNull(k: String) = if (has(k) && !isNull(k)) getLong(k) else null
    private fun JSONObject.optIntOrNull(k: String) = if (has(k) && !isNull(k)) getInt(k) else null
    private fun JSONObject.optStringOrNull(k: String) = if (has(k) && !isNull(k)) getString(k) else null

    // ---- The readable page ----

    fun toHtml(c: Contents, zone: ZoneId = ZoneId.systemDefault()): String {
        val when_ = DateTimeFormatter.ofPattern("EEE MMM d yyyy, h:mm a", Locale.US).withZone(zone)
        val day = DateTimeFormatter.ofPattern("MMM d yyyy", Locale.US).withZone(zone)
        fun t(ms: Long) = when_.format(Instant.ofEpochMilli(ms))
        fun d(ms: Long) = day.format(Instant.ofEpochMilli(ms))
        val names = c.loops.associate { it.loop.id to it.loop.name }
        val sb = StringBuilder()
        sb.append(
            """<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Lineside export</title><style>
:root{color-scheme:light dark;--ink:#1b1d21;--muted:#6b6f76;--rule:#d9dbdf;--ground:#fafaf8}
@media (prefers-color-scheme:dark){:root{--ink:#e9eaec;--muted:#9a9ea6;--rule:#33363c;--ground:#16181b}}
body{background:var(--ground);color:var(--ink);font:16px/1.5 system-ui,sans-serif;max-width:46rem;margin:0 auto;padding:1.5rem 1rem}
h1{margin:0 0 .25rem}h2{margin:2.5rem 0 .25rem;border-top:2px solid var(--ink);padding-top:1rem}
.m{color:var(--muted);font-size:.9rem}ol{list-style:none;padding:0}li{border-bottom:1px solid var(--rule);padding:.6rem 0}
li b{font-weight:600}p.x{white-space:pre-wrap;margin:.25rem 0 0}table{border-collapse:collapse;width:100%;font-size:.9rem}
td,th{text-align:left;border-bottom:1px solid var(--rule);padding:.35rem .5rem .35rem 0;vertical-align:top}
</style></head><body>
"""
        )
        sb.append("<h1>Lineside</h1><p class=\"m\">Exported ${esc(t(c.exportedAt))}. ${c.loops.size} loop${s(c.loops.size)}")
        val archived = c.loops.count { it.loop.archivedAt != null }
        if (archived > 0) sb.append(", $archived of them archived")
        sb.append(". These are the notes you chose to keep, not a medical or official record.</p>\n")
        for (f in c.loops.sortedWith(compareBy<LoopFull> { it.loop.archivedAt != null }.thenByDescending { it.loop.lastActivityAt })) {
            val l = f.loop
            sb.append("<h2>${esc(l.name)}</h2>")
            sb.append("<p class=\"m\">")
            if (l.topic.isNotBlank()) sb.append("${esc(l.topic)} · ")
            sb.append("Started ${esc(d(l.createdAt))}")
            l.archivedAt?.let { sb.append(" · Archived ${esc(d(it))}") }
            sb.append("</p>")
            if (f.numbers.isNotEmpty()) {
                sb.append("<p>")
                sb.append(f.numbers.sortedBy { it.id }.joinToString("<br>") { n -> esc(n.number) + if (n.label.isNotBlank()) " · " + esc(n.label) else "" })
                sb.append("</p>")
            }
            sb.append("<ol>")
            for (e in f.events.sortedWith(compareBy({ it.at }, { it.id }))) {
                sb.append("<li><b>${kindLabel(e.kind)}</b> <span class=\"m\">${esc(t(e.at))}")
                e.number?.let { sb.append(" · ${esc(it)}") }
                e.durationSec?.let { sb.append(" · ${duration(it)}") }
                e.callOffsetSec?.let { sb.append(" · ${duration(it)} into the call") }
                sb.append("</span>")
                if (e.text.isNotBlank()) sb.append("<p class=\"x\">${esc(e.text)}</p>")
                sb.append("</li>")
            }
            if (f.events.isEmpty()) sb.append("<li class=\"m\">Nothing logged.</li>")
            sb.append("</ol>\n")
        }
        if (c.log.isNotEmpty()) {
            sb.append("<h2>Text access log</h2><p class=\"m\">Each time Lineside looked at the phone's messages, and each time Android's text permission was given or handed back.</p>")
            sb.append("<table><tr><th>When</th><th>What</th><th>Loop</th><th>Numbers</th><th>Found</th><th>Added</th></tr>")
            for (g in c.log.sortedWith(compareByDescending<TextLog> { it.at }.thenByDescending { it.id })) {
                sb.append("<tr><td>${esc(t(g.at))}</td><td>${esc(g.what)}</td><td>${esc(g.loopId?.let { names[it] }.orEmpty())}</td>")
                sb.append("<td>${esc(g.numbers)}</td><td>${g.found}</td><td>${g.added}</td></tr>")
            }
            sb.append("</table>")
        }
        sb.append("</body></html>\n")
        return sb.toString()
    }

    private fun kindLabel(k: EventKind) = when (k) {
        EventKind.NOTE -> "Note"
        EventKind.CALL_IN -> "They called, answered"
        EventKind.CALL_OUT -> "You called"
        EventKind.MISSED -> "Missed call"
        EventKind.VOICEMAIL -> "Voicemail"
        EventKind.TEXT -> "Text"
    }

    private fun duration(sec: Int) = if (sec < 60) "${sec}s" else "${sec / 60}m ${sec % 60}s"
    private fun s(n: Int) = if (n == 1) "" else "s"
    private fun esc(v: String) = buildString(v.length) {
        for (ch in v) when (ch) {
            '<' -> append("&lt;"); '>' -> append("&gt;"); '&' -> append("&amp;"); '"' -> append("&quot;"); '\'' -> append("&#39;")
            else -> append(ch)
        }
    }

    // ---- The encrypted ZIP ----

    /** Writes both files into an AES-256 ZIP on [out], and closes it. */
    fun write(out: OutputStream, passphrase: CharArray, c: Contents, zone: ZoneId = ZoneId.systemDefault()) {
        ZipOutputStream(out, passphrase).use { z ->
            for ((name, body) in listOf(HTML_NAME to toHtml(c, zone), JSON_NAME to toJson(c))) {
                z.putNextEntry(ZipParameters().apply {
                    fileNameInZip = name
                    isEncryptFiles = true
                    encryptionMethod = EncryptionMethod.AES
                    aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
                    compressionMethod = CompressionMethod.DEFLATE
                })
                z.write(body.toByteArray(Charsets.UTF_8))
                z.closeEntry()
            }
        }
    }

    /** No real export comes near this; it stops a hostile file from filling memory. */
    private const val MAX_JSON = 64L * 1024 * 1024

    /** Reads an export back. Throws [Problem] for a wrong passphrase, a file that isn't one, or a newer format. */
    fun read(input: InputStream, passphrase: CharArray): Contents {
        val json = try {
            ZipInputStream(input, passphrase).use { z ->
                val entry = generateSequence { z.nextEntry }.firstOrNull { it.fileName == JSON_NAME } ?: throw Problem.NotLineside()
                // Only an encrypted file counts: an unencrypted "export" was not made by Lineside.
                if (!entry.isEncrypted) throw Problem.NotLineside()
                val buf = ByteArrayOutputStream()
                val chunk = ByteArray(16 * 1024)
                while (true) {
                    val n = z.read(chunk)
                    if (n < 0) break
                    buf.write(chunk, 0, n)
                    if (buf.size() > MAX_JSON) throw Problem.NotLineside()
                }
                buf.toString(Charsets.UTF_8.name())
            }
        } catch (e: ZipException) {
            throw if (e.type == ZipException.Type.WRONG_PASSWORD || e.message.orEmpty().contains("password", ignoreCase = true)) Problem.WrongPassphrase()
            else Problem.NotLineside()
        } catch (e: Problem) {
            throw e
        } catch (e: java.io.IOException) {
            throw if (e.message.orEmpty().contains("password", ignoreCase = true)) Problem.WrongPassphrase() else Problem.NotLineside()
        }
        return fromJson(json)
    }
}
