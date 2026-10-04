package com.rikkahub.deepseeklocal.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

/** Room database holding captured log lines. */
@Database(entities = [LogEntity::class], version = 1, exportSchema = false)
abstract class LogDatabase : RoomDatabase() {
    abstract fun logDao(): LogDao
}
