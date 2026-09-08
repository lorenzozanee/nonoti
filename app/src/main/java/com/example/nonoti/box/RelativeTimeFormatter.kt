package com.example.nonoti.box

object RelativeTimeFormatter {
    fun format(timestampMillis: Long, nowMillis: Long = System.currentTimeMillis()): RelativeTime {
        val elapsed = (nowMillis - timestampMillis).coerceAtLeast(0L)
        val minutes = elapsed / 60_000L
        val hours = minutes / 60L
        val days = hours / 24L
        return when {
            days > 0 -> RelativeTime(days.toInt(), RelativeTimeUnit.Days)
            hours > 0 -> RelativeTime(hours.toInt(), RelativeTimeUnit.Hours)
            minutes > 0 -> RelativeTime(minutes.toInt(), RelativeTimeUnit.Minutes)
            else -> RelativeTime(0, RelativeTimeUnit.Now)
        }
    }
}

data class RelativeTime(val value: Int, val unit: RelativeTimeUnit)

enum class RelativeTimeUnit { Now, Minutes, Hours, Days }
