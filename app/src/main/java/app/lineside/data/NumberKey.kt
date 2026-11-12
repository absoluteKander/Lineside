// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside.data

/**
 * A matching key for phone numbers that ignores formatting and country prefixes:
 * "+1 (555) 014-2299", "555.014.2299" and "15550142299" all match.
 * Uses the last 10 digits, which is how numbers are compared in practice for the North American plan
 * and is close to what Android's own caller lookup does (it compares trailing digits).
 * Short codes and extensions-free short numbers keep all their digits.
 */
object NumberKey {
    fun of(raw: String): String {
        val digits = raw.substringBefore(',').substringBefore(';').filter { it.isDigit() }
        return if (digits.length > 10) digits.takeLast(10) else digits
    }

    fun matches(a: String, b: String): Boolean {
        val ka = of(a); val kb = of(b)
        return ka.isNotEmpty() && ka == kb
    }
}
