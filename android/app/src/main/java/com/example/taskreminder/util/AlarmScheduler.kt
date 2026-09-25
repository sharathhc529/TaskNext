package com.example.taskreminder.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.taskreminder.data.TaskEntity
import com.example.taskreminder.receiver.AlarmReceiver

object AlarmScheduler {
    private const val TAG = "AlarmScheduler"

    const val EXTRA_TASK_ID = "EXTRA_TASK_ID"
    const val EXTRA_TASK_TITLE = "EXTRA_TASK_TITLE"
    const val EXTRA_TASK_DESC = "EXTRA_TASK_DESC"
    const val EXTRA_TASK_SCHEDULED_TIME = "EXTRA_TASK_SCHEDULED_TIME"
    const val EXTRA_TASK_OFFSET_MINS = "EXTRA_TASK_OFFSET_MINS"

    /**
     * Schedule exact alarm for a task considering its pre-reminder offset
     */
    fun scheduleTaskAlarm(context: Context, task: TaskEntity) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val triggerTime = task.triggerTimestamp
        val currentTime = System.currentTimeMillis()

        if (triggerTime <= currentTime) {
            Log.w(TAG, "Task ${task.id} trigger time is in the past ($triggerTime <= $currentTime). Skipping scheduling.")
            return
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TASK_REMINDER
            putExtra(EXTRA_TASK_ID, task.id)
            putExtra(EXTRA_TASK_TITLE, task.title)
            putExtra(EXTRA_TASK_DESC, task.description)
            putExtra(EXTRA_TASK_SCHEDULED_TIME, task.scheduledTimestamp)
            putExtra(EXTRA_TASK_OFFSET_MINS, task.reminderOffsetMinutes)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                    Log.d(TAG, "Exact alarm scheduled for task ${task.id} at $triggerTime")
                } else {
                    // Fallback if permission not yet granted
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                    Log.w(TAG, "Inexact alarm scheduled for task ${task.id} (Exact permission not granted)")
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
                Log.d(TAG, "Exact alarm scheduled for task ${task.id} at $triggerTime")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while scheduling exact alarm for task ${task.id}: ${e.message}")
        }
    }

    /**
     * Cancel an active alarm for a task
     */
    fun cancelTaskAlarm(context: Context, taskId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TASK_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "Canceled alarm for task $taskId")
    }

    /**
     * Snooze an alarm by specified minutes
     */
    fun snoozeTaskAlarm(
        context: Context,
        taskId: Long,
        taskTitle: String,
        taskDesc: String,
        originalScheduledTime: Long,
        snoozeMinutes: Int = 5
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val snoozeTriggerTime = System.currentTimeMillis() + (snoozeMinutes * 60 * 1000L)

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TASK_REMINDER
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TASK_TITLE, taskTitle)
            putExtra(EXTRA_TASK_DESC, taskDesc)
            putExtra(EXTRA_TASK_SCHEDULED_TIME, originalScheduledTime)
            putExtra(EXTRA_TASK_OFFSET_MINS, 0) // snoozed fires directly
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                snoozeTriggerTime,
                pendingIntent
            )
            Log.d(TAG, "Task $taskId snoozed for $snoozeMinutes mins (trigger at $snoozeTriggerTime)")
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to set snooze exact alarm: ${e.message}")
        }
    }
}
