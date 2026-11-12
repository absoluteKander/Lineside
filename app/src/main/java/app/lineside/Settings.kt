// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside

import android.content.Context

/** User settings plus small bits of state. Local SharedPreferences, never backed up unless the user opts in. */
class Settings(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** The pill during calls. Off: Lineside still updates the call screen and logs calls to loops. */
    var pillEnabled: Boolean
        get() = p.getBoolean("pillEnabled", true)
        set(v) = p.edit().putBoolean("pillEnabled", v).apply()

    /** "+ Start a loop" for numbers that aren't in your contacts. On by default; it disappears in 5 s if ignored. */
    var unknownCallerPill: Boolean
        get() = p.getBoolean("unknownCallerPill", true)
        set(v) = p.edit().putBoolean("unknownCallerPill", v).apply()

    var contactsHintDismissed: Boolean
        get() = p.getBoolean("contactsHintDismissed", false)
        set(v) = p.edit().putBoolean("contactsHintDismissed", v).apply()

    var callsHintDismissed: Boolean
        get() = p.getBoolean("callsHintDismissed", false)
        set(v) = p.edit().putBoolean("callsHintDismissed", v).apply()

    /** Where the user last dragged the pill, or null for the default spot. */
    var pillPosition: Pair<Int, Int>?
        get() = if (p.contains("pillX")) p.getInt("pillX", 0) to p.getInt("pillY", 0) else null
        set(v) { p.edit().apply { if (v == null) remove("pillX").remove("pillY") else putInt("pillX", v.first).putInt("pillY", v.second) }.apply() }
}
