package com.example.taskreminder.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a scheduled task and its reminder settings.
 */
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val scheduledTimestamp: Long,          // When the task is actually due (epoch ms)
    val reminderOffsetMinutes: Int = 0,    // 0 = at exact time, 5 = 5m before, 30 = 30m, 60 = 1h, 180 = 3h, 1440 = 1d
    val isCompleted: Boolean = false,
    val isDismissed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * Timestamp at which the foreground alarm/popup should trigger.
     */
    val triggerTimestamp: Long
        get() = scheduledTimestamp - (reminderOffsetMinutes * 60 * 1000L)

    /**
     * Check if the task is still active and pending reminder
     */
    val isPending: Boolean
        get() = !isCompleted && !isDismissed
}
