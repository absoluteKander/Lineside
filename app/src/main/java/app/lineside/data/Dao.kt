// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LoopDao {
    // Archived loops are left out of everything except the archive itself.
    @Transaction @Query("SELECT * FROM Loop WHERE archivedAt IS NULL ORDER BY lastActivityAt DESC")
    fun observeAll(): Flow<List<LoopFull>>

    @Transaction @Query("SELECT * FROM Loop WHERE archivedAt IS NOT NULL ORDER BY archivedAt DESC")
    fun observeArchived(): Flow<List<LoopFull>>

    @Query("UPDATE Loop SET archivedAt = :at, rawContactId = NULL, textsOn = 0 WHERE id = :id") suspend fun archive(id: Long, at: Long)
    @Query("UPDATE Loop SET archivedAt = NULL, keptAt = :at WHERE id = :id") suspend fun unarchive(id: Long, at: Long)

    @Transaction @Query("SELECT * FROM Loop WHERE id = :id")
    fun observe(id: Long): Flow<LoopFull?>

    @Transaction @Query("SELECT * FROM Loop WHERE id = :id")
    suspend fun get(id: Long): LoopFull?

    @Transaction @Query("SELECT * FROM Loop WHERE archivedAt IS NULL")
    suspend fun all(): List<LoopFull>

    @Query("SELECT DISTINCT n.loopId FROM LoopNumber n JOIN Loop l ON l.id = n.loopId WHERE n.`key` = :key AND l.archivedAt IS NULL")
    suspend fun loopIdsForKey(key: String): List<Long>

    @Query("SELECT id FROM Loop WHERE archivedAt IS NULL") suspend fun allIds(): List<Long>

    /** Archived loops that had this number: a caller the user may have forgotten. */
    @Query("SELECT DISTINCT n.loopId FROM LoopNumber n JOIN Loop l ON l.id = n.loopId WHERE n.`key` = :key AND l.archivedAt IS NOT NULL")
    suspend fun archivedLoopIdsForKey(key: String): List<Long>

    @Insert suspend fun insert(loop: Loop): Long
    @Update suspend fun update(loop: Loop)
    @Delete suspend fun delete(loop: Loop)

    @Insert suspend fun insertNumbers(numbers: List<LoopNumber>)
    @Query("DELETE FROM LoopNumber WHERE loopId = :loopId") suspend fun clearNumbers(loopId: Long)
    @Query("DELETE FROM LoopNumber WHERE loopId = :loopId AND `key` = :key") suspend fun removeNumber(loopId: Long, key: String)
    @Query("UPDATE LoopNumber SET label = :label WHERE loopId = :loopId AND `key` = :key") suspend fun setNumberLabel(loopId: Long, key: String, label: String)
    @Query("SELECT COUNT(*) FROM LoopNumber WHERE loopId = :loopId AND `key` = :key") suspend fun hasNumber(loopId: Long, key: String): Int
    @Query("DELETE FROM Event WHERE loopId = :loopId AND number = :number AND at = :at AND source = 'CALL'")
    suspend fun deleteCallEvent(loopId: Long, number: String, at: Long)

    @Insert suspend fun insertEvent(event: Event): Long
    @Update suspend fun updateEvent(event: Event)
    @Delete suspend fun deleteEvent(event: Event)

    /** Targeted updates: never write a whole stale Loop row over a concurrent change. */
    @Query("UPDATE Loop SET rawContactId = :rawId WHERE id = :id") suspend fun setRawContact(id: Long, rawId: Long?)
    @Query("UPDATE Loop SET keptAt = :at WHERE id = :id") suspend fun setKept(id: Long, at: Long)
    @Query("SELECT rawContactId FROM Loop WHERE rawContactId IS NOT NULL") suspend fun rawContactIds(): List<Long>

    // ---- Texts ----
    @Query("UPDATE Loop SET textsOn = :on WHERE id = :id") suspend fun setTextsOn(id: Long, on: Boolean)
    @Query("UPDATE LoopNumber SET textsSince = :since WHERE loopId = :loopId AND `key` = :key")
    suspend fun setTextsSince(loopId: Long, key: String, since: Long?)
    @Query("UPDATE LoopNumber SET textsSince = NULL WHERE loopId = :loopId") suspend fun clearTexts(loopId: Long)
    @Query("SELECT smsId FROM Event WHERE loopId = :loopId AND smsId IS NOT NULL") suspend fun smsIds(loopId: Long): List<Long>
    @Insert suspend fun insertEvents(events: List<Event>)
    @Insert suspend fun insertLog(line: TextLog)
    @Query("SELECT * FROM TextLog ORDER BY at DESC, id DESC LIMIT 200") fun observeLog(): Flow<List<TextLog>>
    @Query("DELETE FROM TextLog WHERE at < :before") suspend fun pruneLog(before: Long)

    @Query("UPDATE Loop SET lastActivityAt = MAX(lastActivityAt, :at) WHERE id = :loopId")
    suspend fun touch(loopId: Long, at: Long)

    // ---- Export, import, and moving to another phone ----
    /** Every loop, archived ones too: the export leaves nothing out. */
    @Transaction @Query("SELECT * FROM Loop ORDER BY createdAt, id") suspend fun everything(): List<LoopFull>
    @Query("SELECT * FROM TextLog ORDER BY at, id") suspend fun wholeLog(): List<TextLog>
    /** The same loop, already here: an import of an export made on this phone, or imported twice. */
    @Query("SELECT COUNT(*) FROM Loop WHERE createdAt = :createdAt AND name = :name") suspend fun countSame(createdAt: Long, name: String): Int
    @Query("SELECT COUNT(*) FROM TextLog WHERE loopId IS NULL AND at = :at AND what = :what") suspend fun countSameLog(at: Long, what: String): Int
    @Insert suspend fun insertLogs(lines: List<TextLog>)

    /**
     * After a restore onto a phone: contact ids and message ids belong to the old phone, and texts switches are
     * consent given there, for that phone's messages. All of it starts over here.
     */
    @Query("UPDATE Loop SET rawContactId = NULL, textsOn = 0") suspend fun forgetPhoneLoops()
    @Query("UPDATE LoopNumber SET textsSince = NULL") suspend fun forgetPhoneNumbers()
    @Query("UPDATE Event SET smsId = NULL") suspend fun forgetPhoneEvents()
}
