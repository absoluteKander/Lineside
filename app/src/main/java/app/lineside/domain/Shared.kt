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
    data class Guess(val kind: EventKind, val numbers: List<String>, val text: String)

    // "+15550142299", or a North American number however it's punctuated: "(555) 014-2299", "+1 555.014.2299".
    private val numberLike = Regex("""(?<![\w+])(?:\+\d{8,15}|(?:\+?1[\s.\-]?)?\(?\d{3}\)?[\s.\-]?\d{3}[\s.\-]?\d{4})(?!\d)""")
    private val url = Regex("""https?://\S+""")

    fun guess(text: String?, subject: String?, mime: String?, fileName: String?): Guess {
        val all = listOfNotNull(subject, text, fileName).joinToString("\n")
        val audio = mime?.startsWith("audio/") == true
        val kind = if (audio || all.contains("voicemail", ignoreCase = true)) EventKind.VOICEMAIL else EventKind.TEXT
        return Guess(kind, numbers(all), clean(text.orEmpty()))
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
        .map { it.replace(url, "").trim() }
        .filterNot { line -> line.isNotEmpty() && numbers(line).isNotEmpty() && line.count(Char::isLetter) == 0 }
        .joinToString("\n")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}
