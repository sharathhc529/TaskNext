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
    val reminderOffsetMinutes: Int = 0,    // Minutes before due time to ring; 0 = at due time, default 5
    val isCompleted: Boolean = false,
    val isDismissed: Boolean = false,     // Legacy: no longer set or read; Dismiss only silences the alarm
    val snoozedUntil: Long? = null,       // When a snoozed alarm will re-fire (epoch ms), null if not snoozed
    val alarmSoundUri: String? = null,    // Custom alarm sound (content:// URI), null = system default alarm
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * Timestamp at which the foreground alarm/popup should trigger (snooze takes precedence).
     */
    val triggerTimestamp: Long
        get() = snoozedUntil ?: (scheduledTimestamp - (reminderOffsetMinutes * 60 * 1000L))

    /**
     * Check if the task is still active and pending reminder
     */
    val isPending: Boolean
        get() = !isCompleted
}
