// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class WhenParseTest {
    private val zone = ZoneId.of("America/New_York")
    // A Saturday afternoon.
    private val now = ZonedDateTime.of(2026, 11, 14, 14, 0, 0, 0, zone)
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) = ZonedDateTime.of(y, mo, d, h, mi, 0, 0, zone)

    @Test fun weekdayAndTime() {
        val w = WhenParse.find("Need to get MRI next Friday 9am", now)!!
        assertEquals(at(2026, 11, 20, 9, 0), w.start); assertTrue(w.hasTime)
    }

    @Test fun weekdayAlone_isAllDay() {
        val w = WhenParse.find("call back Tuesday", now)!!
        assertEquals(at(2026, 11, 17, 0, 0), w.start); assertFalse(w.hasTime)
    }

    @Test fun monthDayWithAt() {
        val w = WhenParse.find("Follow-up Nov 21 at 2:30", now)!!
        assertEquals(at(2026, 11, 21, 14, 30), w.start)
    }

    @Test fun pastMonthDayRollsToNextYear() = assertEquals(at(2027, 1, 5, 0, 0), WhenParse.find("Jan 5 recheck", now)!!.start)

    @Test fun numericDate() = assertEquals(at(2026, 12, 2, 10, 0), WhenParse.find("Appt 12/2 10:00 AM", now)!!.start)

    @Test fun tomorrowNoon() = assertEquals(at(2026, 11, 15, 12, 0), WhenParse.find("they'll call tomorrow around noon", now)!!.start)

    @Test fun timeAloneLaterToday() = assertEquals(at(2026, 11, 14, 16, 0), WhenParse.find("call at 4", now)!!.start)

    @Test fun timeAlonePassed_isTomorrow() = assertEquals(at(2026, 11, 15, 9, 0), WhenParse.find("call at 9am", now)!!.start)

    @Test fun verbsAreNotWeekdays() = assertNull(WhenParse.find("sat on hold for 20 minutes, no sun", now))
    @Test fun capitalAbbreviationIsAWeekday() = assertEquals(at(2026, 11, 16, 0, 0), WhenParse.find("Call Mon.", now)!!.start)

    @Test fun nothingToFind() = assertNull(WhenParse.find("Results normal, recheck in 6 months", now))
}
