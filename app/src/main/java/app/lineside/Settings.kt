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

    /**
     * Discreet call screen: the phone app shows only "Call back"/"Waiting" and how long ago, never a loop's
     * topic or who within it is calling, since a ringing phone is readable by anyone nearby.
     */
    var discreetCallScreen: Boolean
        get() = p.getBoolean("discreetCallScreen", false)
        set(v) = p.edit().putBoolean("discreetCallScreen", v).apply()

    /** One reminder when a loop has been quiet for three weeks. Off unless the user asks for it (offered in onboarding). */
    var quietNudge: Boolean
        get() = p.getBoolean("quietNudge", false)
        set(v) = p.edit().putBoolean("quietNudge", v).apply()

    /** When each loop was last nudged as quiet (loop id → time). */
    /** Days the user folded on a loop's timeline, as epoch days. */
    fun folded(loopId: Long): Set<Long> = p.getStringSet("folded_$loopId", emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
    fun setFolded(loopId: Long, days: Set<Long>) = p.edit().putStringSet("folded_$loopId", days.map { it.toString() }.toSet()).apply()
    /** A closed loop leaves nothing behind here either. */
    fun forgetLoop(loopId: Long) = p.edit().remove("folded_$loopId").remove("nudged_$loopId").apply()

    fun nudgedAt(loopId: Long): Long? = p.getLong("nudged_$loopId", 0L).takeIf { it > 0 }
    fun setNudgedAt(loopId: Long, at: Long) = p.edit().putLong("nudged_$loopId", at).apply()

    /** Light, dark, or follow the phone. */
    var themeMode: app.lineside.ui.ThemeMode
        get() = runCatching { app.lineside.ui.ThemeMode.valueOf(p.getString("themeMode", "SYSTEM")!!) }.getOrDefault(app.lineside.ui.ThemeMode.SYSTEM)
        set(v) = p.edit().putString("themeMode", v.name).apply()

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
