package com.rikkahub.deepseeklocal

import com.rikkahub.deepseeklocal.data.remote.deepseek.PowSolver
import org.junit.Assert.assertNull
import org.junit.Test

/** Smoke tests for PoW solver edge cases. */
class PowSolverTest {
    @Test fun returnsNullForBadInput() { assertNull(PowSolver().solve("not json")) }
}
