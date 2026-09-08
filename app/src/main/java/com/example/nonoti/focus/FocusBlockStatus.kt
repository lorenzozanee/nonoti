package com.example.nonoti.focus

import java.time.ZonedDateTime

enum class FocusBlockStatus {
    Upcoming,
    Active,
    Finished,
}

object FocusBlockStatuses {
    fun at(block: DailyFocusBlock, now: ZonedDateTime): FocusBlockStatus {
        val minute = now.hour * 60 + now.minute
        return if (block.startMinute < block.endMinute) {
            when {
                minute < block.startMinute -> FocusBlockStatus.Upcoming
                minute < block.endMinute -> FocusBlockStatus.Active
                else -> FocusBlockStatus.Finished
            }
        } else {
            if (minute >= block.startMinute || minute < block.endMinute) {
                FocusBlockStatus.Active
            } else {
                FocusBlockStatus.Upcoming
            }
        }
    }
}
