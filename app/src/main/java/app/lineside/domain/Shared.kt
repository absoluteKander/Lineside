// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside.domain

import app.lineside.data.EventKind
import app.lineside.data.NumberKey

/**
 * Reads what another app shared (a voicemail, a text) into a waypoint guess.
 * Only text is kept: a shared audio file is noted by name and type, never read or stored.
 */
object Shared {
    /** [name] and [at] come from a dialer's voicemail file name, e.g. "Second Phone_11-11-26_0929PM.amr". */
    data class Guess(val kind: EventKind, val numbers: List<String>, val text: String, val name: String? = null, val at: Long? = null)

    // The phone app names a shared voicemail "<caller>_<MM-dd-yy>_<hhmm><AM|PM>.<ext>".
    private val dialerFile = Regex("""^(.+)_(\d{2})-(\d{2})-(\d{2})_(\d{2})(\d{2})\s?([AaPp][Mm])\.\w+$""")

    /** The caller (a name, or a number) and when the voicemail was left, from the phone app's file name. */
    fun dialerFile(fileName: String?, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Pair<String, Long?>? {
        val m = dialerFile.matchEntire(fileName?.trim() ?: return null) ?: return null
        val (who, mo, d, y, h, mi, ap) = m.destructured
        val at = runCatching {
            val hour = h.toInt() % 12 + if (ap.equals("PM", true)) 12 else 0
            java.time.LocalDateTime.of(2000 + y.toInt(), mo.toInt(), d.toInt(), hour, mi.toInt()).atZone(zone).toInstant().toEpochMilli()
        }.getOrNull()
        return who.trim() to at
    }

    // "+15550142299", or a North American number however it's punctuated: "(555) 014-2299", "+1 555.014.2299".
    private val numberLike = Regex("""(?<![\w+])(?:\+\d{8,15}|(?:\+?1[\s.\-]?)?\(?\d{3}\)?[\s.\-]?\d{3}[\s.\-]?\d{4})(?!\d)""")
    private val url = Regex("""https?://\S+""")
    // "New voicemail from (555) 014-2299", "Voicemail from Dr Patel's office:", "Transcript:" — the wrapper, not the words.
    private val header = Regex("""^\s*(?:new\s+)?voicemail(?:\s+(?:from|received|message)\b.*)?\s*[:.]?\s*$""", RegexOption.IGNORE_CASE)
    private val transcript = Regex("""^\s*transcript(?:ion)?\s*:\s*""", RegexOption.IGNORE_CASE)

    fun guess(text: String?, subject: String?, mime: String?, fileName: String?): Guess {
        val all = listOfNotNull(subject, text, fileName).joinToString("\n")
        val audio = mime?.startsWith("audio/") == true
        val kind = if (audio || all.contains("voicemail", ignoreCase = true)) EventKind.VOICEMAIL else EventKind.TEXT
        val file = dialerFile(fileName)
        val name = file?.first?.takeIf { numbers(it).isEmpty() && it.any(Char::isLetter) }
        return Guess(kind, numbers(all), clean(text.orEmpty()), name, file?.second)
    }

    /** Phone numbers in the order they appear, one per number however it's formatted. */
    fun numbers(s: String): List<String> = numberLike.findAll(s)
        .map { it.value.trim() }
        .filter { n -> n.count(Char::isDigit).let { it in 10..15 } }
        .map { n -> (if (n.startsWith("+")) "+" else "") + n.filter(Char::isDigit) }
        .distinctBy { NumberKey.of(it) }
        .toList()

    /** The message itself: links and lines that are only a phone number dropped, blank runs collapsed. */
    fun clean(text: String): String = text.lines()
        .map { it.replace(url, "").replace(transcript, "").trim() }
        .filterNot { line -> header.matches(line) }
        .filterNot { line -> line.isNotEmpty() && numbers(line).isNotEmpty() && line.count(Char::isLetter) == 0 }
        .joinToString("\n")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}
