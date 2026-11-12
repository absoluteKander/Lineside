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
    @Transaction @Query("SELECT * FROM Loop ORDER BY lastActivityAt DESC")
    fun observeAll(): Flow<List<LoopFull>>

    @Transaction @Query("SELECT * FROM Loop WHERE id = :id")
    fun observe(id: Long): Flow<LoopFull?>

    @Transaction @Query("SELECT * FROM Loop WHERE id = :id")
    suspend fun get(id: Long): LoopFull?

    @Query("SELECT DISTINCT loopId FROM LoopNumber WHERE `key` = :key")
    suspend fun loopIdsForKey(key: String): List<Long>

    @Query("SELECT id FROM Loop") suspend fun allIds(): List<Long>

    @Insert suspend fun insert(loop: Loop): Long
    @Update suspend fun update(loop: Loop)
    @Delete suspend fun delete(loop: Loop)

    @Insert suspend fun insertNumbers(numbers: List<LoopNumber>)
    @Query("DELETE FROM LoopNumber WHERE loopId = :loopId") suspend fun clearNumbers(loopId: Long)

    @Insert suspend fun insertEvent(event: Event): Long
    @Update suspend fun updateEvent(event: Event)
    @Delete suspend fun deleteEvent(event: Event)

    /** Targeted updates: never write a whole stale Loop row over a concurrent change. */
    @Query("UPDATE Loop SET rawContactId = :rawId WHERE id = :id") suspend fun setRawContact(id: Long, rawId: Long?)
    @Query("UPDATE Loop SET keptAt = :at WHERE id = :id") suspend fun setKept(id: Long, at: Long)
    @Query("SELECT rawContactId FROM Loop WHERE rawContactId IS NOT NULL") suspend fun rawContactIds(): List<Long>

    @Query("UPDATE Loop SET lastActivityAt = MAX(lastActivityAt, :at) WHERE id = :loopId")
    suspend fun touch(loopId: Long, at: Long)
}
