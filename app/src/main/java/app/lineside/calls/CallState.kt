// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside.calls

import android.content.Context
import app.lineside.data.Event
import app.lineside.data.EventKind
import app.lineside.data.EventSource
import org.json.JSONObject

/**
 * The call in progress, as Lineside saw it start. Kept in memory and on disk, because Android may stop
 * the app between the call starting and ending. Key everything on [number]: it's what was dialled or
 * what rang, before any forwarding service swaps in its own number.
 */
data class ActiveCall(
    val number: String,
    val incoming: Boolean,
    val startedAt: Long,
    /** The loop this number belongs to, or null for an unknown number. */
    val loopId: Long?,
    val answeredAt: Long? = null,
)

/** A call that has ended, ready to become a timeline event. */
data class EndedCall(val call: ActiveCall, val answered: Boolean, val endedAt: Long) {
    fun toEvent(loopId: Long): Event {
        val kind = when {
            !call.incoming -> EventKind.CALL_OUT
            answered -> EventKind.CALL_IN
            else -> EventKind.MISSED
        }
        val duration = call.answeredAt?.let { ((endedAt - it) / 1000).toInt() }?.takeIf { answered && it >= 0 }
        return Event(loopId = loopId, kind = kind, at = call.startedAt, number = call.number, durationSec = duration, source = EventSource.CALL)
    }
}

object CallStore {
    @Volatile private var mem: ActiveCall? = null

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("call", Context.MODE_PRIVATE)

    fun set(ctx: Context, call: ActiveCall?) {
        mem = call
        prefs(ctx).edit().apply {
            if (call == null) remove("active") else putString("active", JSONObject().apply {
                put("number", call.number); put("incoming", call.incoming); put("startedAt", call.startedAt)
                call.loopId?.let { put("loopId", it) }; call.answeredAt?.let { put("answeredAt", it) }
            }.toString())
        }.apply()
    }

    fun get(ctx: Context): ActiveCall? = mem ?: prefs(ctx).getString("active", null)?.let {
        runCatching {
            val o = JSONObject(it)
            ActiveCall(
                o.getString("number"), o.getBoolean("incoming"), o.getLong("startedAt"),
                if (o.has("loopId")) o.getLong("loopId") else null,
                if (o.has("answeredAt")) o.getLong("answeredAt") else null,
            )
        }.getOrNull()
    }.also { mem = it }

    fun update(ctx: Context, f: (ActiveCall) -> ActiveCall) { get(ctx)?.let { set(ctx, f(it)) } }

    /** The most recent ended call, so a loop started from the after-call pill can still log it. */
    @Volatile var lastEnded: EndedCall? = null
}
