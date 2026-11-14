// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/**
 * Finds the one date and time a note is about ("MRI next Friday 9am", "call back Tuesday", "Nov 21 at 2:30"),
 * to prefill a calendar entry. Deliberately simple: the calendar app shows the result and the user can change it.
 */
object WhenParse {
    /** [start] is the day's midnight when no time was found ([hasTime] false): an all-day entry. */
    data class When(val start: ZonedDateTime, val hasTime: Boolean)

    private val weekday = Regex(
        """\b(?:next\s+|this\s+)?(mon(?:day)?|tue(?:s(?:day)?)?|wed(?:nesday)?|thu(?:rs?(?:day)?)?|fri(?:day)?|sat(?:urday)?|sun(?:day)?)\b\.?""",
        RegexOption.IGNORE_CASE,
    )
    private val relative = Regex("""\b(today|tomorrow|tonight)\b""", RegexOption.IGNORE_CASE)
    private val monthDay = Regex("""\b(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\.?\s+(\d{1,2})(?:st|nd|rd|th)?\b""", RegexOption.IGNORE_CASE)
    private val numeric = Regex("""\b(\d{1,2})/(\d{1,2})(?:/(\d{2,4}))?\b""")
    private val clock = Regex("""\b(\d{1,2})(?::(\d{2}))?\s*([ap])\.?m\.?\b""", RegexOption.IGNORE_CASE)
    private val clock24 = Regex("""\b(\d{1,2}):(\d{2})\b""")
    private val atHour = Regex("""\bat\s+(\d{1,2})\b(?!\s*[:/])""", RegexOption.IGNORE_CASE)
    private val words = Regex("""\b(noon|midday|midnight)\b""", RegexOption.IGNORE_CASE)
    private val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private val days = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")

    fun find(text: String, now: ZonedDateTime): When? {
        val date = date(text, now.toLocalDate())
        val time = time(text)
        if (date == null && time == null) return null
        val day = date ?: run {
            // A time with no day: today if it's still ahead, else tomorrow.
            val today = now.toLocalDate()
            if (time!!.isAfter(now.toLocalTime())) today else today.plusDays(1)
        }
        return if (time != null) When(day.atTime(time).atZone(now.zone), true) else When(day.atStartOfDay(now.zone), false)
    }

    private fun date(text: String, today: LocalDate): LocalDate? {
        relative.find(text)?.let { m ->
            return if (m.groupValues[1].lowercase() == "tomorrow") today.plusDays(1) else today
        }
        // A bare "sat" or "sun" is usually a verb or the sky; the three-letter forms count only when capitalised.
        weekday.findAll(text).firstOrNull { m -> m.groupValues[1].length > 3 || m.groupValues[1][0].isUpperCase() }?.let { m ->
            val dow = DayOfWeek.of(days.indexOf(m.groupValues[1].lowercase().take(3)) + 1)
            return today.with(TemporalAdjusters.next(dow))
        }
        monthDay.find(text)?.let { m ->
            val month = months.indexOf(m.groupValues[1].lowercase().take(3)) + 1
            val d = m.groupValues[2].toInt()
            return runCatching { LocalDate.of(today.year, month, d) }.getOrNull()?.let { if (it.isBefore(today.minusDays(1))) it.plusYears(1) else it }
        }
        numeric.find(text)?.let { m ->
            val (mo, d, y) = m.destructured
            val year = y.toIntOrNull()?.let { if (it < 100) 2000 + it else it } ?: today.year
            return runCatching { LocalDate.of(year, mo.toInt(), d.toInt()) }.getOrNull()
                ?.let { if (y.isEmpty() && it.isBefore(today.minusDays(1))) it.plusYears(1) else it }
        }
        return null
    }

    private fun time(text: String): LocalTime? {
        words.find(text)?.let { return if (it.groupValues[1].lowercase() == "midnight") LocalTime.MIDNIGHT else LocalTime.NOON }
        clock.find(text)?.let { m ->
            var h = m.groupValues[1].toInt() % 12
            if (m.groupValues[3].lowercase() == "p") h += 12
            return runCatching { LocalTime.of(h, m.groupValues[2].ifEmpty { "0" }.toInt()) }.getOrNull()
        }
        clock24.find(text)?.let { m ->
            val h = m.groupValues[1].toInt(); val min = m.groupValues[2].toInt()
            // "4:30" with no am/pm: an appointment at 4:30 is almost always afternoon.
            return runCatching { LocalTime.of(if (h in 1..6) h + 12 else h, min) }.getOrNull()
        }
        atHour.find(text)?.let { m ->
            val h = m.groupValues[1].toInt()
            return runCatching { LocalTime.of(if (h in 1..6) h + 12 else h, 0) }.getOrNull()
        }
        return null
    }
}
