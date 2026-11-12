// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside.data

import app.lineside.contacts.ContactSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class NumberInput(val number: String, val label: String = "")

/**
 * Every change to a loop goes through here, so its system contact (the call-screen surface) stays in step.
 * The database is the source of truth; the contact is a display copy that can always be rebuilt.
 */
class LoopRepo(private val db: Db, private val contacts: ContactSurface, private val clock: () -> Long = System::currentTimeMillis) {
    private val dao = db.loops()
    /** One contact write at a time. Two concurrent first syncs once created a duplicate contact for a loop. */
    private val contactLock = Mutex()

    fun observeAll(): Flow<List<LoopFull>> = dao.observeAll()
    fun observe(id: Long): Flow<LoopFull?> = dao.observe(id)
    suspend fun get(id: Long): LoopFull? = io { dao.get(id) }

    suspend fun create(name: String, topic: String, numbers: List<NumberInput>, firstNote: String = ""): Long = io {
        val now = clock()
        val id = dao.insert(Loop(name = name.trim(), topic = topic.trim(), createdAt = now, lastActivityAt = now))
        dao.insertNumbers(clean(id, numbers))
        if (firstNote.isNotBlank()) dao.insertEvent(Event(loopId = id, kind = EventKind.NOTE, at = now, text = firstNote.trim()))
        resync(id)
        id
    }

    suspend fun edit(id: Long, name: String, topic: String, numbers: List<NumberInput>) = io {
        val full = dao.get(id) ?: return@io
        dao.update(full.loop.copy(name = name.trim(), topic = topic.trim()))
        dao.clearNumbers(id)
        dao.insertNumbers(clean(id, numbers))
        resync(id)
    }

    suspend fun addEvent(event: Event): Long = io {
        val eid = dao.insertEvent(event.copy(text = event.text.trim()))
        android.util.Log.d("Lineside", "event saved id=$eid loop=${event.loopId} kind=${event.kind}")
        dao.touch(event.loopId, event.at)
        resync(event.loopId)
        eid
    }

    suspend fun deleteEvent(event: Event) = io { dao.deleteEvent(event); resync(event.loopId) }

    /** "Keep": the user still cares about a quiet loop; it leaves the quiet list for another three weeks. */
    suspend fun keep(id: Long) = io { dao.setKept(id, clock()) }

    /**
     * Close the loop: removes the loop, its numbers, calls, notes and attachments, and its contact.
     * The phone's own call history is never touched (Lineside can't even read it).
     */
    suspend fun close(id: Long) = io {
        contactLock.withLock {
            val full = dao.get(id) ?: return@withLock
            contacts.delete(full.loop.rawContactId)
            dao.delete(full.loop)
        }
    }

    /** Loops that own this number, for matching calls (M3). */
    suspend fun loopsForNumber(number: String): List<Long> = io {
        val key = NumberKey.of(number)
        if (key.isEmpty()) emptyList() else dao.loopIdsForKey(key)
    }

    /** Rewrites the contact (name, numbers, status line). Safe to call any time; a no-op without Contacts access. */
    suspend fun resync(id: Long) = io {
        contactLock.withLock {
            val full = dao.get(id) ?: return@withLock
            val rawId = contacts.sync(full, clock())
            if (rawId != null && rawId != full.loop.rawContactId) dao.setRawContact(id, rawId)
        }
    }

    /**
     * After Contacts access is granted (or the status lines have aged), bring every loop's contact up to date,
     * then remove any Lineside contact that no loop owns (leftovers from a crash or an old bug).
     */
    suspend fun resyncAll() = io {
        dao.allIds().forEach { resync(it) }
        contactLock.withLock {
            val owned = dao.rawContactIds().toSet()
            contacts.ourRawContactIds().filter { it !in owned }.forEach { contacts.delete(it) }
        }
    }

    private fun clean(loopId: Long, numbers: List<NumberInput>) = numbers
        .filter { NumberKey.of(it.number).isNotEmpty() }
        .distinctBy { NumberKey.of(it.number) }
        .map { LoopNumber(loopId = loopId, number = it.number.trim(), key = NumberKey.of(it.number), label = it.label.trim()) }

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }
}
