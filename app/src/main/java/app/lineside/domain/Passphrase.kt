// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside.domain

import java.security.SecureRandom

/**
 * Passphrases for the encrypted export: five words from the EFF large wordlist (7,776 words), about 64 bits.
 * The ZIP format's own key stretching is light, which is why it's five words and not four.
 */
object Passphrase {
    const val WORDS = 5
    /** The shortest passphrase of the user's own that Lineside accepts. */
    const val MIN_OWN = 12

    fun suggest(words: List<String>, random: java.util.Random = SecureRandom()): String {
        require(words.size >= 1000) { "wordlist too short" }
        return List(WORDS) { words[random.nextInt(words.size)] }.joinToString(" ")
    }

    /** Null when it's fine, else why not. */
    fun problem(p: String): String? = when {
        p.isBlank() -> "Enter a passphrase."
        p != p.trim() -> "No spaces at the start or end: they're easy to lose when typing it on a computer."
        p.length < MIN_OWN -> "At least $MIN_OWN characters. ${MIN_OWN - p.length} more."
        else -> null
    }
}
