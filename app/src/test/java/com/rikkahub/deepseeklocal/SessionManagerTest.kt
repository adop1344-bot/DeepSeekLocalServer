package com.rikkahub.deepseeklocal

import com.rikkahub.deepseeklocal.server.SessionManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the in-memory [SessionManager].
 *
 * Covers the hash stability contract (same first two messages -> same hash),
 * lazy session creation, and reset semantics.
 */
class SessionManagerTest {

    @Test
    fun getOrCreateCreatesSessionOnce() = runBlocking {
        val mgr = SessionManager()
        var calls = 0
        val factory: suspend () -> String = {
            calls++
            "session-$calls"
        }

        val s1 = mgr.getOrCreate("hash-a", factory)
        val s2 = mgr.getOrCreate("hash-a", factory)

        assertEquals(1, calls)
        assertSame(s1, s2)
        assertEquals("session-1", s1.sessionId)
        assertNull(s1.parentMessageId)
    }

    @Test
    fun differentHashesGetSeparateSessions() = runBlocking {
        val mgr = SessionManager()
        var calls = 0
        val factory: suspend () -> String = { "session-${++calls}" }

        val a = mgr.getOrCreate("hash-a", factory)
        val b = mgr.getOrCreate("hash-b", factory)

        assertEquals(2, calls)
        assertNotEquals(a.sessionId, b.sessionId)
        assertEquals("session-1", a.sessionId)
        assertEquals("session-2", b.sessionId)
    }

    @Test
    fun resetDropsSessionForcingFreshCreation() = runBlocking {
        val mgr = SessionManager()
        var calls = 0
        val factory: suspend () -> String = { "session-${++calls}" }

        val first = mgr.getOrCreate("hash-a", factory)
        mgr.reset("hash-a")
        val second = mgr.getOrCreate("hash-a", factory)

        assertEquals(2, calls)
        assertEquals("session-1", first.sessionId)
        assertEquals("session-2", second.sessionId)
    }

    @Test
    fun resetAllClearsEverySession() = runBlocking {
        val mgr = SessionManager()
        var calls = 0
        val factory: suspend () -> String = { "session-${++calls}" }

        mgr.getOrCreate("h1", factory)
        mgr.getOrCreate("h2", factory)
        mgr.getOrCreate("h3", factory)
        assertEquals(3, calls)

        mgr.resetAll()
        mgr.getOrCreate("h1", factory)
        assertEquals(4, calls)
    }

    @Test
    fun parentMessageIdIsMutable() = runBlocking {
        val mgr = SessionManager()
        val s = mgr.getOrCreate("h", { "sid" })
        s.parentMessageId = "parent-1"
        assertEquals("parent-1", s.parentMessageId)
        s.parentMessageId = null
        assertNull(s.parentMessageId)
    }

    @Test
    fun hashConversationIsDeterministic() {
        val json = "[{\"role\":\"user\",\"content\":\"hi\"}]"
        val a = SessionManager.hashConversation(json)
        val b = SessionManager.hashConversation(json)
        assertEquals(a, b)
    }

    @Test
    fun hashConversationIs16HexChars() {
        val h = SessionManager.hashConversation("anything")
        assertEquals(16, h.length)
        assertTrue(h.all { it in "0123456789abcdef" })
    }

    @Test
    fun hashConversationDiffersForDifferentInput() {
        val a = SessionManager.hashConversation("one")
        val b = SessionManager.hashConversation("two")
        assertNotEquals(a, b)
    }

    @Test
    fun hashConversationHandlesEmptyInput() {
        val h = SessionManager.hashConversation("")
        assertNotNull(h)
        assertEquals(16, h.length)
    }
}
