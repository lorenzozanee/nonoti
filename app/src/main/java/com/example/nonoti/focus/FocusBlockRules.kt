package com.example.nonoti.focus

data class DailyFocusBlock(
    val startMinute: Int,
    val endMinute: Int,
)

enum class FocusBlockError {
    EqualEndpoints,
    TooShort,
    OverlapsExisting,
    TouchesExisting,
}

object FocusBlockRules {
    private const val MinutesPerDay = 24 * 60
    private const val MinimumDurationMinutes = 5

    fun validate(
        candidate: DailyFocusBlock,
        existing: List<DailyFocusBlock>,
    ): FocusBlockError? {
        if (candidate.startMinute == candidate.endMinute) {
            return FocusBlockError.EqualEndpoints
        }
        if (durationMinutes(candidate) < MinimumDurationMinutes) {
            return FocusBlockError.TooShort
        }
        if (existing.any { overlaps(candidate, it) }) {
            return FocusBlockError.OverlapsExisting
        }
        if (existing.any { touches(candidate, it) }) {
            return FocusBlockError.TouchesExisting
        }
        return null
    }

    private fun durationMinutes(block: DailyFocusBlock): Int =
        (block.endMinute - block.startMinute + MinutesPerDay) % MinutesPerDay

    private fun overlaps(first: DailyFocusBlock, second: DailyFocusBlock): Boolean =
        intervals(first).any { firstInterval ->
            intervals(second).any { secondInterval ->
                maxOf(firstInterval.first, secondInterval.first) <
                    minOf(firstInterval.lastExclusive, secondInterval.lastExclusive)
            }
        }

    private fun touches(first: DailyFocusBlock, second: DailyFocusBlock): Boolean =
        first.endMinute == second.startMinute || second.endMinute == first.startMinute

    private fun intervals(block: DailyFocusBlock): List<MinuteInterval> =
        if (block.startMinute < block.endMinute) {
            listOf(MinuteInterval(block.startMinute, block.endMinute))
        } else {
            listOf(
                MinuteInterval(block.startMinute, MinutesPerDay),
                MinuteInterval(0, block.endMinute),
            )
        }

    private data class MinuteInterval(
        val first: Int,
        val lastExclusive: Int,
    )
}
