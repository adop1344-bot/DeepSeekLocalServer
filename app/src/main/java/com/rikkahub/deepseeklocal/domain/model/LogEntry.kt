package com.rikkahub.deepseeklocal.domain.model

/** Severity of a captured log line. */
enum class LogLevel { DEBUG, INFO, WARN, ERROR }

/**
 * A single log record, mapped 1:1 to the Room table.
 *
 * @property id auto-generated row id (0 when not yet persisted)
 * @property timestamp epoch millis
 * @property level severity
 * @property tag short subsystem tag
 * @property message log text
 */
data class LogEntry(
    val id: Long = 0L,
    val timestamp: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
)
