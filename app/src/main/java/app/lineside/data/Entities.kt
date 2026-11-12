// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside.data

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
)

enum class EventKind { NOTE, CALL_IN, CALL_OUT, MISSED, VOICEMAIL, TEXT }

/** Where an event came from: typed by the user, noticed during a call, or shared in from another app. */
enum class EventSource { MANUAL, CALL, SHARED }

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
)

data class LoopFull(
    @Embedded val loop: Loop,
    @Relation(parentColumn = "id", entityColumn = "loopId") val numbers: List<LoopNumber>,
    @Relation(parentColumn = "id", entityColumn = "loopId") val events: List<Event>,
)
