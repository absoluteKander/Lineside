// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside.domain

import app.lineside.data.LoopFull

/** Searching the archive: plain matching over a loop's name, topic, numbers, their names, and every note and text. */
object ArchiveSearch {
    /**
     * null when [query] doesn't match the loop. Otherwise a line to show under it: the matching note or text,
     * the matching number, or (for an empty query or a name/topic match) the loop's latest words.
     */
    fun match(f: LoopFull, query: String): String? {
        val q = query.trim()
        val latest = f.events.filter { it.text.isNotBlank() }.maxByOrNull { it.at }?.text.orEmpty()
        if (q.isEmpty()) return latest
        if (f.loop.name.contains(q, true) || f.loop.topic.contains(q, true)) return latest
        val digits = q.filter(Char::isDigit)
        f.numbers.firstOrNull { n -> n.label.contains(q, true) || (digits.length >= 3 && n.number.contains(digits)) }
            ?.let { n -> return listOf(n.label, n.number).filter { it.isNotBlank() }.joinToString(" · ") }
        return f.events.sortedByDescending { it.at }.firstOrNull { it.text.contains(q, true) }?.let { snippet(it.text, q) }
    }

    /** The words around the match, so a hit deep in a long note still shows why it matched. */
    fun snippet(text: String, q: String): String {
        val i = text.indexOf(q, ignoreCase = true)
        if (i < 0 || i < 40) return text
        val start = text.lastIndexOf(' ', i - 30).let { if (it < 0) 0 else it + 1 }
        return "…" + text.substring(start)
    }
}
