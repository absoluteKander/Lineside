// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside

import app.lineside.domain.Passphrase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BackupAndPassphraseTest {
    @Test fun backupOnlyWhenSwitchedOnAndEncryptedOrDirect() {
        assertFalse(Backup.allowed(on = false, encrypted = true, transfer = true))
        assertFalse(Backup.allowed(on = true, encrypted = false, transfer = false))
        assertTrue(Backup.allowed(on = true, encrypted = true, transfer = false))
        assertTrue(Backup.allowed(on = true, encrypted = false, transfer = true))
    }

    private val words: List<String> by lazy {
        // Unit tests run from the module directory.
        File("src/main/assets/eff_large_wordlist.txt").readLines().filter { it.isNotBlank() }
    }

    @Test fun wordlistIsTheFullEffList() {
        assertEquals(7776, words.size)
        assertEquals(7776, words.toSet().size)
        assertTrue(words.all { w -> w.all { it in 'a'..'z' || it == '-' } })
    }

    @Test fun suggestionsAreFiveListWords() {
        val p = Passphrase.suggest(words, java.util.Random(1))
        val parts = p.split(" ")
        assertEquals(Passphrase.WORDS, parts.size)
        assertTrue(parts.all { it in words })
        assertNull(Passphrase.problem(p))
    }

    @Test fun ownPassphrasesNeedLengthAndNoEdgeSpaces() {
        assertNotNull(Passphrase.problem(""))
        assertNotNull(Passphrase.problem("short"))
        assertNotNull(Passphrase.problem(" twelve chars ok"))
        assertNull(Passphrase.problem("twelve chars"))
    }
}
