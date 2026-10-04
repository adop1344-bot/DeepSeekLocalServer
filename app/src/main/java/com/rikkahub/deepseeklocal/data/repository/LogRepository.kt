package com.rikkahub.deepseeklocal.data.repository

import com.rikkahub.deepseeklocal.data.local.db.LogDao
import com.rikkahub.deepseeklocal.data.local.db.LogEntity
import com.rikkahub.deepseeklocal.domain.model.LogEntry
import com.rikkahub.deepseeklocal.domain.model.LogLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Persists and streams log records. */
@Singleton
class LogRepository @Inject constructor(private val dao: LogDao) {

    /** Newest-first stream of logs, mapped to domain models. */
    fun observe(limit: Int = 2000): Flow<List<LogEntry>> =
        dao.observeLatest(limit).map { rows -> rows.map { it.toDomain() } }

    /** Appends a single log line. */
    suspend fun append(level: LogLevel, tag: String, message: String) {
        dao.insert(LogEntity(timestamp = System.currentTimeMillis(), level = level.name, tag = tag, message = message))
    }

    /** Rotates logs older than [days] days. */
    suspend fun rotate(days: Long = 7): Int {
        val cutoff = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L
        return dao.deleteOlderThan(cutoff)
    }

    /** Deletes all logs. */
    suspend fun clear() = dao.clear()

    private fun LogEntity.toDomain() = LogEntry(
        id = id,
        timestamp = timestamp,
        level = runCatching { LogLevel.valueOf(level) }.getOrDefault(LogLevel.INFO),
        tag = tag,
        message = message,
    )
}
