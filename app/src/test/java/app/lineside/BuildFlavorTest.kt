// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside

import org.junit.Assert.assertEquals
import org.junit.Test

class BuildFlavorTest {
    /** Only the GitHub/F-Droid build may read SMS; the Play build must not even be capable of it. */
    @Test fun smsOnlyInFossBuild() = assertEquals(BuildConfig.FLAVOR == "foss", BuildConfig.READS_SMS)
}
