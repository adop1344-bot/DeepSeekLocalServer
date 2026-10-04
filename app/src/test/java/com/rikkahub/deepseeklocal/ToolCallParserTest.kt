package com.rikkahub.deepseeklocal

import com.rikkahub.deepseeklocal.server.ToolCallParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Unit tests for the tool-call parser. */
class ToolCallParserTest {
    @Test fun parsesFencedJson() {
        val text = "Some prose\n```json\n{\"tool\":\"search\",\"args\":{\"q\":\"kotlin\"}}\n```\n"
        val c = ToolCallParser.parse(text)
        assertNotNull(c); assertEquals("search", c!!.tool); assertEquals("kotlin", c.args["q"])
    }
    @Test fun returnsNullForProse() { assertNull(ToolCallParser.parse("just words")) }
    @Test fun stripsToolBlock() { assertEquals("hello", ToolCallParser.strip("hello\n```json\n{}\n```")) }
}
