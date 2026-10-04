package com.rikkahub.deepseeklocal

import com.rikkahub.deepseeklocal.server.CutHeuristics
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for cut-detection heuristics. */
class CutHeuristicsTest {
    @Test fun completeSentenceIsNotCut() = assertFalse(CutHeuristics.isCut("This is a complete sentence."))
    @Test fun trailingColonIsCut() = assertTrue(CutHeuristics.isCut("Here are the steps you should follow:"))
    @Test fun trailingCommaIsCut() = assertTrue(CutHeuristics.isCut("First you need to open the file,"))
    @Test fun shortTextIsIgnored() = assertFalse(CutHeuristics.isCut("Ok"))
    @Test fun unclosedFenceIsCut() = assertTrue(CutHeuristics.isCut("```kotlin\nval x = 1"))
}
