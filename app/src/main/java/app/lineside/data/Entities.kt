// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/** A loop: one ongoing matter you're waiting on a call about (the lab, the insurance claim). */
@Entity
data class Loop(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** What it's about, e.g. "Blood work follow-up". Optional. */
    val topic: String = "",
    val createdAt: Long,
    /** Last call, voicemail, text or note. Drives "quiet" and the status line. */
    val lastActivityAt: Long,
    /** When the user last said "Keep" on a quiet loop; it stays out of the quiet list for a while. */
    val keptAt: Long? = null,
    /** The system contact that shows this loop on the call screen, if Contacts access is allowed. */
    val rawContactId: Long? = null,
    /** Texts for this loop: the first of two switches (the loop, then each number). Off until the user turns it on. */
    @ColumnInfo(defaultValue = "0") val textsOn: Boolean = false,
    /**
     * Set when the user archives the loop instead of deleting it: everything is kept, but the loop leaves the list,
     * call matching, the contact surface and text importing until it's restored.
     */
    val archivedAt: Long? = null,
)

@Entity(
    foreignKeys = [ForeignKey(entity = Loop::class, parentColumns = ["id"], childColumns = ["loopId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("loopId"), Index("key")],
)
data class LoopNumber(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val loopId: Long,
    /** As the user typed it, for display. */
    val number: String,
    /** Matching key, see [NumberKey]. */
    val key: String,
    /** e.g. "front desk", "calls out from this line". Optional. */
    val label: String = "",
    /** When the user switched on texts from this number; only texts after this are added. null: off. */
    val textsSince: Long? = null,
)

enum class EventKind { NOTE, CALL_IN, CALL_OUT, MISSED, VOICEMAIL, TEXT }

/** Where an event came from: typed by the user, noticed during a call, or shared in from another app. */
enum class EventSource { MANUAL, CALL, SHARED, IMPORTED }

@Entity(
    foreignKeys = [ForeignKey(entity = Loop::class, parentColumns = ["id"], childColumns = ["loopId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("loopId"), Index("at")],
)
data class Event(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val loopId: Long,
    val kind: EventKind,
    val at: Long,
    /** Note text, voicemail transcript, text message body. */
    val text: String = "",
    val durationSec: Int? = null,
    /** The number involved, when it was a call or text. */
    val number: String? = null,
    val source: EventSource = EventSource.MANUAL,
    /** For a note jotted during a call: seconds into that call. */
    val callOffsetSec: Int? = null,
    /** For a text added from the phone's messages: its id there, so it's never added twice. */
    val smsId: Long? = null,
)

/**
 * One line in the text access log: each time Lineside looked at the phone's messages for a loop, and each time
 * Android's text permission was given or handed back. Deleted with the loop; lines without a loop are about the
 * permission itself.
 */
@Entity(
    foreignKeys = [ForeignKey(entity = Loop::class, parentColumns = ["id"], childColumns = ["loopId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("loopId"), Index("at")],
)
data class TextLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val loopId: Long? = null,
    /** "Opened Lineside", "Daily check", "Access given", "Access handed back". */
    val what: String,
    /** Which numbers were asked about, as the user named them, e.g. "Front desk ··2299". */
    val numbers: String = "",
    val found: Int = 0,
    val added: Int = 0,
)

data class LoopFull(
    @Embedded val loop: Loop,
    @Relation(parentColumn = "id", entityColumn = "loopId") val numbers: List<LoopNumber>,
    @Relation(parentColumn = "id", entityColumn = "loopId") val events: List<Event>,
)
