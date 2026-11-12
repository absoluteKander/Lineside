// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Absolute Candor

package app.lineside.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Loop::class, LoopNumber::class, Event::class], version = 1, exportSchema = true)
abstract class Db : RoomDatabase() {
    abstract fun loops(): LoopDao

    companion object {
        fun open(ctx: Context): Db =
            Room.databaseBuilder(ctx.applicationContext, Db::class.java, "lineside.db").build()
    }
}
