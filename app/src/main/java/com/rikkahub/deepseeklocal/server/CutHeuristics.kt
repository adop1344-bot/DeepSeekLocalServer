package com.rikkahub.deepseeklocal.server

/**
 * Heuristics that decide whether a streamed answer was cut mid-sentence and
 * should trigger an auto-continue pass. Ports the reference `isCut`.
 */
object CutHeuristics {

    /** True if [text] looks truncated and worth continuing. */
    fun isCut(text: String): Boolean {
        if (text.isBlank()) return false
        val t = text.trim()
        if (t.length < 30) return false

        // Explicit terminators — accept unless there are unbalanced constructs.
        if (t.last() in TERMINATORS) {
            if (count(t, "```") % 2 != 0) return true
            if (count(t, "`") % 2 != 0) return true
            if (count(t, "(") > count(t, ")")) return true
            return false
        }

        // Trailing markdown markers are not cuts.
        if (t.endsWith("**")) return false
        if (Regex("-\\s*$").containsMatchIn(t)) return false

        val last = t.last()
        if (last == ':' || last == ',' || last == '-' || last == '\u2014') {
            val lastLine = t.split('\n').last().trim()
            if (Regex("^\\d+\\.\\s*$").matches(lastLine)) return false
            if (lastLine == "*" || lastLine == "-") return false
            return true
        }

        if (count(t, "```") % 2 != 0) return true
        if (count(t, "(") > count(t, ")")) return true

        val words = t.split(Regex("\\s+"))
        val lastWord = words.lastOrNull().orEmpty()
        if (lastWord.length in 1..2 && lastWord.any { it.isLetter() } &&
            lastWord.lowercase() !in SAFE_SHORT_WORDS
        ) {
            return true
        }
        return false
    }

    private fun count(s: String, sub: String): Int {
        var c = 0
        var i = s.indexOf(sub)
        while (i >= 0) { c++; i = s.indexOf(sub, i + sub.length) }
        return c
    }

    private val TERMINATORS = setOf('.', '!', '?', ')', '"', '\u00bb', ']', '`')
    private val SAFE_SHORT_WORDS = setOf("и", "в", "а", "но", "или", "to", "of", "in", "is", "a", "an")
}
