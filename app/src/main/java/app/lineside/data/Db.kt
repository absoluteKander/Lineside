// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Loop::class, LoopNumber::class, Event::class, TextLog::class],
    version = 3, exportSchema = true,
    // 2: texts switches on loops and numbers, smsId on events, the text access log.
    // 3: archivedAt on loops.
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class Db : RoomDatabase() {
    abstract fun loops(): LoopDao

    companion object {
        fun open(ctx: Context): Db =
            Room.databaseBuilder(ctx.applicationContext, Db::class.java, "lineside.db").build()
    }
}
