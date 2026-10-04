package com.rikkahub.deepseeklocal.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Data-access object for the log table. */
@Dao
interface LogDao {

    @Query("SELECT * FROM logs ORDER BY timestamp DESC LIMIT :limit")
    fun observeLatest(limit: Int = 2000): Flow<List<LogEntity>>

    @Insert
    suspend fun insert(entry: LogEntity)

    @Insert
    suspend fun insertAll(entries: List<LogEntity>)

    @Query("DELETE FROM logs WHERE timestamp < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long): Int

    @Query("DELETE FROM logs")
    suspend fun clear()
}
