package com.rikkahub.deepseeklocal

import com.rikkahub.deepseeklocal.data.remote.deepseek.SseParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the DeepSeek web SSE parser.
 *
 * The parser mirrors the reference `stream.js` behaviour:
 *  - event-scoped patches (`event:` applies only to the NEXT `data:` line),
 *  - fragment snapshots (partial content by prefix-length delta),
 *  - `THINK` fragments routed to the reasoning callback,
 *  - status words (FINISHED, WIP, ...) filtered out.
 */
class SseParserTest {

    private class Collector {
        val chunks = StringBuilder()
        val reasons = StringBuilder()
    }

    private fun newParser(): Pair<SseParser, Collector> {
        val c = Collector()
        val parser = SseParser(
            onDelta = { c.chunks.append(it) },
            onReasoning = { c.reasons.append(it) },
        )
        return parser to c
    }

    @Test
    fun simpleDeltaChunksAreConcatenated() {
        val (p, c) = newParser()
        p.processLine("data: {\"v\":\"Hello \"}")
        p.processLine("data: {\"v\":\"world\"}")
        assertEquals("Hello world", c.chunks.toString())
        assertTrue(p.wroteAnything())
    }

    @Test
    fun statusWordsAreSkipped() {
        val (p, c) = newParser()
        p.processLine("data: {\"v\":\"FINISHED\"}")
        p.processLine("data: {\"v\":\"WIP\"}")
        p.processLine("data: {\"v\":\"ok\"}")
        assertEquals("ok", c.chunks.toString())
    }

    @Test
    fun thinkFragmentGoesToReasoningCallback() {
        val (p, c) = newParser()
        // Snapshot with fragments: one THINK, one RESPONSE.
        p.processLine("data: {\"v\":{\"response\":{\"message_id\":\"m1\",\"fragments\":[{\"id\":1,\"type\":\"THINK\",\"content\":\"reasoning...\"}]}}}")
        p.processLine("data: {\"v\":{\"response\":{\"message_id\":\"m1\",\"fragments\":[{\"id\":2,\"type\":\"RESPONSE\",\"content\":\"answer\"}]}}}")
        assertEquals("reasoning...", c.reasons.toString())
        assertEquals("answer", c.chunks.toString())
    }

    @Test
    fun fragmentSnapshotEmitsOnlyTail() {
        val (p, c) = newParser()
        // First snapshot: full content is "abc".
        p.processLine("data: {\"v\":{\"response\":{\"message_id\":\"m1\",\"fragments\":[{\"id\":1,\"type\":\"RESPONSE\",\"content\":\"abc\"}]}}}")
        // Second snapshot: same fragment grew to "abcdef" — only "def" is emitted.
        p.processLine("data: {\"v\":{\"response\":{\"message_id\":\"m1\",\"fragments\":[{\"id\":1,\"type\":\"RESPONSE\",\"content\":\"abcdef\"}]}}}")
        assertEquals("abcdef", c.chunks.toString())
    }

    @Test
    fun messageIdChangeResetsFragmentTracking() {
        val (p, c) = newParser()
        p.processLine("data: {\"v\":{\"response\":{\"message_id\":\"m1\",\"fragments\":[{\"id\":1,\"type\":\"RESPONSE\",\"content\":\"first\"}]}}}")
        // New message id: same fragment id 1 but different content -> emit full content.
        p.processLine("data: {\"v\":{\"response\":{\"message_id\":\"m2\",\"fragments\":[{\"id\":1,\"type\":\"RESPONSE\",\"content\":\"second\"}]}}}")
        assertEquals("firstsecond", c.chunks.toString())
    }

    @Test
    fun stringPatchOnFragmentContent() {
        val (p, c) = newParser()
        // Set up fragment 7 with an initial snapshot.
        p.processLine("data: {\"v\":{\"response\":{\"message_id\":\"m1\",\"fragments\":[{\"id\":7,\"type\":\"RESPONSE\",\"content\":\"foo\"}]}}}")
        // Delta patch on that fragment's content path.
        p.processLine("data: {\"p\":\"response/fragments/7/content\",\"v\":\"bar\"}")
        assertEquals("foobar", c.chunks.toString())
    }

    @Test
    fun readyEventRecordsResponseMessageId() {
        val (p, _) = newParser()
        p.processLine("event: ready")
        p.processLine("data: {\"response_message_id\":\"rm-1\"}")
        val r = p.finalize()
        assertEquals("rm-1", r.responseMessageId)
    }

    @Test
    fun doneAndEmptyPayloadsAreIgnored() {
        val (p, c) = newParser()
        p.processLine("data: [DONE]")
        p.processLine("data: ")
        p.processLine("")
        assertEquals("", c.chunks.toString())
        assertFalse(p.wroteAnything())
    }

    @Test
    fun malformedJsonIsSkippedSilently() {
        val (p, c) = newParser()
        p.processLine("data: {this is not json}")
        p.processLine("data: {\"v\":\"good\"}")
        assertEquals("good", c.chunks.toString())
    }

    @Test
    fun metaPathsAreFiltered() {
        val (p, c) = newParser()
        p.processLine("data: {\"p\":\"response/status\",\"v\":\"IN_PROGRESS\"}")
        p.processLine("data: {\"p\":\"response/accumulated_token_usage\",\"v\":\"123\"}")
        p.processLine("data: {\"v\":\"visible\"}")
        assertEquals("visible", c.chunks.toString())
    }

    @Test
    fun finalizeClosesThinkBlock() {
        val c = Collector()
        // No reasoning callback -> parser writes literal [thinking]/[/thinking] markers.
        val p = SseParser(onDelta = { c.chunks.append(it) }, onReasoning = null)
        p.processLine("data: {\"v\":{\"response\":{\"message_id\":\"m1\",\"fragments\":[{\"id\":1,\"type\":\"THINK\",\"content\":\"thinking\"}]}}}")
        p.finalize()
        val out = c.chunks.toString()
        assertTrue(out.contains("[thinking]"))
        assertTrue(out.contains("thinking"))
        assertTrue(out.contains("[/thinking]"))
    }
}
