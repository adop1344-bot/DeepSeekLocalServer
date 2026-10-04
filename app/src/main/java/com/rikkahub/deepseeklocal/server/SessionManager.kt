package com.rikkahub.deepseeklocal.server

import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** In-memory session state keyed by a conversation hash. */
data class SessionState(
    @Volatile var sessionId: String,
    @Volatile var parentMessageId: String?,
)

/**
 * Tracks DeepSeek chat sessions per conversation hash, plus a small continuation
 * state cache. All maps are concurrent; no locking is needed at call sites.
 */
class SessionManager {

    private val sessions = ConcurrentHashMap<String, SessionState>()

    /** Returns the session for [hash], creating it lazily via [factory]. */
    suspend fun getOrCreate(hash: String, factory: suspend () -> String): SessionState {
        sessions[hash]?.let { return it }
        val sid = factory()
        val fresh = SessionState(sessionId = sid, parentMessageId = null)
        sessions[hash] = fresh
        return fresh
    }

    /** Drops the session for [hash] (forces a fresh one next time). */
    fun reset(hash: String) {
        sessions.remove(hash)
    }

    /** Clears every tracked session. */
    fun resetAll() {
        sessions.clear()
    }

    companion object {
        /** Stable 16-hex conversation hash over the first two messages. */
        fun hashConversation(messagesJson: String): String {
            val md = MessageDigest.getInstance("MD5")
            val bytes = md.digest(messagesJson.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }.take(16)
        }
    }
}
