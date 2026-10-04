package com.rikkahub.deepseeklocal.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Room row for a single captured log line. */
@Entity(tableName = "logs")
data class LogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val timestamp: Long,
    val level: String,
    val tag: String,
    val message: String,
)
