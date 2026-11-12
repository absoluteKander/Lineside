// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as SysSettings

/**
 * Everything Lineside can be allowed to do. Every item is optional: without any of them, the user keeps
 * their loops by hand. Each has a plain reason, shown wherever we ask for it.
 */
enum class Access(val title: String, val why: String, val without: String) {
    CALLS(
        "Notice calls",
        "See the number of each call as it starts (nothing else, no audio), to match it to a loop and log it.",
        "You log calls yourself.",
    ),
    CONTACTS(
        "Contacts",
        "Show a loop's name and status on your phone app's call screen, and stay out of the way for people already in your contacts.",
        "Callers from loops show as plain numbers.",
    ),
    PHONE(
        "Phone state",
        "Know when a call is answered and when it ends, for note timestamps and call length.",
        "Calls are logged without their length.",
    ),
    NOTIFICATIONS(
        "Notifications",
        "A quiet \"Tap to jot a note\" for calls you answer on the lock screen.",
        "Notes wait until after the call.",
    ),
    PILL(
        "Display over other apps",
        "The pill: a loop's context and a note field during and just after calls.",
        "Open Lineside yourself during a call.",
    );

    fun granted(ctx: Context): Boolean = when (this) {
        CALLS -> ctx.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        CONTACTS -> perm(ctx, Manifest.permission.WRITE_CONTACTS) && perm(ctx, Manifest.permission.READ_CONTACTS)
        PHONE -> perm(ctx, Manifest.permission.READ_PHONE_STATE)
        NOTIFICATIONS -> Build.VERSION.SDK_INT < 33 || perm(ctx, Manifest.permission.POST_NOTIFICATIONS)
        PILL -> SysSettings.canDrawOverlays(ctx)
    }

    /** Runtime permissions to request, or null when this needs a system screen instead. */
    fun permissions(): Array<String>? = when (this) {
        CONTACTS -> arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
        PHONE -> arrayOf(Manifest.permission.READ_PHONE_STATE)
        NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else null
        else -> null
    }

    /** The system screen for items that aren't plain permissions. */
    fun systemIntent(ctx: Context): Intent? = when (this) {
        CALLS -> ctx.getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
        PILL -> Intent(SysSettings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
        else -> null
    }

    private fun perm(ctx: Context, p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
}
