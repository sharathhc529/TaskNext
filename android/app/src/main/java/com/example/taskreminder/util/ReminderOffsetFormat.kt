package com.example.taskreminder.util

/** "5 min", "2 hours 30 min", "1 day", or "At due time" for a reminder offset in minutes. */
fun formatReminderOffset(minutes: Int): String {
    if (minutes <= 0) return "At due time"
    return listOfNotNull(
        (minutes / 1440).takeIf { it > 0 }?.let { plural(it, "day") },
        (minutes % 1440 / 60).takeIf { it > 0 }?.let { plural(it, "hour") },
        (minutes % 60).takeIf { it > 0 }?.let { "$it min" }
    ).joinToString(" ")
}

private fun plural(count: Int, unit: String) = if (count == 1) "1 $unit" else "$count ${unit}s"
