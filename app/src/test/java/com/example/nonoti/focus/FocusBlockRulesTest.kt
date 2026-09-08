package com.example.nonoti.focus

import org.junit.Assert.assertEquals
import org.junit.Test

class FocusBlockRulesTest {
    @Test
    fun equalEndpointsAreRejected() {
        val result = FocusBlockRules.validate(candidate = DailyFocusBlock(540, 540), existing = emptyList())

        assertEquals(FocusBlockError.EqualEndpoints, result)
    }

    @Test
    fun blockShorterThanFiveMinutesIsRejectedAcrossMidnight() {
        val result = FocusBlockRules.validate(candidate = DailyFocusBlock(1438, 2), existing = emptyList())

        assertEquals(FocusBlockError.TooShort, result)
    }

    @Test
    fun fiveMinuteBlockAcrossMidnightIsAccepted() {
        val result = FocusBlockRules.validate(candidate = DailyFocusBlock(1438, 3), existing = emptyList())

        assertEquals(null, result)
    }

    @Test
    fun overlappingCrossMidnightBlocksAreRejected() {
        val result =
            FocusBlockRules.validate(
                candidate = DailyFocusBlock(30, 120),
                existing = listOf(DailyFocusBlock(1380, 60)),
            )

        assertEquals(FocusBlockError.OverlapsExisting, result)
    }

    @Test
    fun blocksTouchingAtEitherBoundaryAreRejected() {
        val after =
            FocusBlockRules.validate(
                candidate = DailyFocusBlock(660, 720),
                existing = listOf(DailyFocusBlock(600, 660)),
            )
        val before =
            FocusBlockRules.validate(
                candidate = DailyFocusBlock(540, 600),
                existing = listOf(DailyFocusBlock(600, 660)),
            )

        assertEquals(FocusBlockError.TouchesExisting, after)
        assertEquals(FocusBlockError.TouchesExisting, before)
    }
}
