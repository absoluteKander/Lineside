// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NumberKeyTest {
    @Test fun formattingAndCountryCodeDoNotMatter() {
        assertTrue(NumberKey.matches("+1 (555) 014-2299", "555.014.2299"))
        assertTrue(NumberKey.matches("15550142299", "tel:+15550142299".removePrefix("tel:")))
    }
    @Test fun differentNumbersDoNotMatch() = assertFalse(NumberKey.matches("5550142299", "5550142200"))
    @Test fun extensionIsIgnored() = assertEquals("5550142299", NumberKey.of("555-014-2299,214"))
    @Test fun shortCodesKeepAllDigits() = assertEquals("72345", NumberKey.of("72345"))
    @Test fun emptyNeverMatches() = assertFalse(NumberKey.matches("", ""))
}
