package com.example.taskreminder.receiver

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.taskreminder.R
import com.example.taskreminder.data.TaskDatabase
import com.example.taskreminder.ui.ReminderPopupActivity
import com.example.taskreminder.util.AlarmScheduler
import com.example.taskreminder.util.ReminderSoundPlayer
import com.example.taskreminder.util.formatReminderOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "AlarmReceiver"
        // v2: channel is silent — ReminderSoundPlayer plays the (per-task) alarm sound itself.
        // Channel sound can't be changed after creation, so the old channel is replaced.
        const val CHANNEL_ID = "task_foreground_alarms_v2"
        const val LEGACY_CHANNEL_ID = "task_foreground_alarms"
        const val ACTION_TASK_REMINDER = "com.example.taskreminder.ACTION_TASK_REMINDER"
        const val ACTION_DISMISS_ALARM = "com.example.taskreminder.ACTION_DISMISS_ALARM"
        const val ACTION_SNOOZE_ALARM = "com.example.taskreminder.ACTION_SNOOZE_ALARM"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(AlarmScheduler.EXTRA_TASK_ID, -1L)
        val taskTitle = intent.getStringExtra(AlarmScheduler.EXTRA_TASK_TITLE) ?: "Upcoming Task"
        val taskDesc = intent.getStringExtra(AlarmScheduler.EXTRA_TASK_DESC) ?: ""
        val scheduledTime = intent.getLongExtra(AlarmScheduler.EXTRA_TASK_SCHEDULED_TIME, System.currentTimeMillis())
        val offsetMins = intent.getIntExtra(AlarmScheduler.EXTRA_TASK_OFFSET_MINS, 0)

        Log.d(TAG, "onReceive: action=${intent.action}, taskId=$taskId")

        when (intent.action) {
            ACTION_TASK_REMINDER -> {
                // Look up the task's chosen sound off the main thread before ringing
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val soundUri = if (taskId != -1L) {
                            TaskDatabase.getDatabase(context).taskDao().getTaskById(taskId)?.alarmSoundUri
                        } else null
                        handleAlarmTrigger(context, taskId, taskTitle, taskDesc, scheduledTime, offsetMins, soundUri)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            ACTION_DISMISS_ALARM -> {
                handleDismiss(context, taskId)
            }
            ACTION_SNOOZE_ALARM -> {
                handleSnooze(context, taskId, taskTitle, taskDesc, scheduledTime)
            }
        }
    }

    private fun handleAlarmTrigger(
        context: Context,
        taskId: Long,
        title: String,
        desc: String,
        scheduledTime: Long,
        offsetMins: Int,
        soundUri: String?
    ) {
        // 1. Acquire WakeLock
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
            "TaskReminder:AlarmWakeLock"
        )
        wakeLock?.acquire(15000) // 15 seconds wake lock

        // 2. Start Sound and Vibration
        ReminderSoundPlayer.startAlarmSoundAndVibration(context, soundUri)

        // 3. When the app is already on screen the popup opens directly on top, so a heads-up
        //    notification would only cover it. Otherwise post it: its full-screen intent is what
        //    brings the popup up over the lock screen, and the popup removes it once visible.
        if (!isAppInForeground()) {
            showAlarmNotification(context, taskId, title, desc, scheduledTime, offsetMins, fullScreen = true)
        }

        // 4. Directly attempt to launch popup activity
        try {
            context.startActivity(buildReminderPopupIntent(context, taskId, title, desc, scheduledTime, offsetMins))
        } catch (e: Exception) {
            Log.e(TAG, "Direct startActivity failed (will rely on fullScreenIntent): ${e.message}")
            // Launch failed while foregrounded: fall back to the notification so the alarm isn't invisible
            showAlarmNotification(context, taskId, title, desc, scheduledTime, offsetMins, fullScreen = true)
        }
    }

    /** Silences the alarm only; the task stays pending and shows as due until marked done. */
    private fun handleDismiss(context: Context, taskId: Long) {
        ReminderSoundPlayer.stop()
        cancelAlarmNotification(context, taskId)
    }

    private fun handleSnooze(
        context: Context,
        taskId: Long,
        title: String,
        desc: String,
        scheduledTime: Long
    ) {
        ReminderSoundPlayer.stop()
        cancelAlarmNotification(context, taskId)

        val snoozedUntil = AlarmScheduler.snoozeTaskAlarm(context, taskId, title, desc, scheduledTime, snoozeMinutes = 5)

        if (taskId != -1L) {
            CoroutineScope(Dispatchers.IO).launch {
                val db = TaskDatabase.getDatabase(context)
                db.taskDao().setTaskSnoozed(taskId, snoozedUntil)
            }
        }
    }
}

/** True while one of this app's activities is visible to the user. */
private fun isAppInForeground(): Boolean {
    val info = ActivityManager.RunningAppProcessInfo()
    ActivityManager.getMyMemoryState(info)
    return info.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
}

fun buildReminderPopupIntent(
    context: Context,
    taskId: Long,
    title: String,
    desc: String,
    scheduledTime: Long,
    offsetMins: Int
): Intent = Intent(context, ReminderPopupActivity::class.java).apply {
    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
            Intent.FLAG_ACTIVITY_SINGLE_TOP
    putExtra(AlarmScheduler.EXTRA_TASK_ID, taskId)
    putExtra(AlarmScheduler.EXTRA_TASK_TITLE, title)
    putExtra(AlarmScheduler.EXTRA_TASK_DESC, desc)
    putExtra(AlarmScheduler.EXTRA_TASK_SCHEDULED_TIME, scheduledTime)
    putExtra(AlarmScheduler.EXTRA_TASK_OFFSET_MINS, offsetMins)
}

/**
 * Ongoing alarm notification with Dismiss / Snooze actions.
 * [fullScreen] attaches the full-screen intent that launches the popup over the lock screen;
 * leave it off when re-posting for a popup that already exists, or it would be relaunched.
 */
fun showAlarmNotification(
    context: Context,
    taskId: Long,
    title: String,
    desc: String,
    scheduledTime: Long,
    offsetMins: Int,
    fullScreen: Boolean
) {
    createNotificationChannel(context)

    val popupPendingIntent = PendingIntent.getActivity(
        context,
        taskId.toInt(),
        buildReminderPopupIntent(context, taskId, title, desc, scheduledTime, offsetMins),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val dismissIntent = Intent(context, AlarmReceiver::class.java).apply {
        action = AlarmReceiver.ACTION_DISMISS_ALARM
        putExtra(AlarmScheduler.EXTRA_TASK_ID, taskId)
    }
    val dismissPendingIntent = PendingIntent.getBroadcast(
        context,
        (taskId * 10 + 1).toInt(),
        dismissIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val snoozeIntent = Intent(context, AlarmReceiver::class.java).apply {
        action = AlarmReceiver.ACTION_SNOOZE_ALARM
        putExtra(AlarmScheduler.EXTRA_TASK_ID, taskId)
        putExtra(AlarmScheduler.EXTRA_TASK_TITLE, title)
        putExtra(AlarmScheduler.EXTRA_TASK_DESC, desc)
        putExtra(AlarmScheduler.EXTRA_TASK_SCHEDULED_TIME, scheduledTime)
    }
    val snoozePendingIntent = PendingIntent.getBroadcast(
        context,
        (taskId * 10 + 2).toInt(),
        snoozeIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val reminderText = if (offsetMins > 0) {
        "Reminder (${formatReminderOffset(offsetMins)} before due time): $desc"
    } else {
        "Due now: $desc"
    }

    val builder = NotificationCompat.Builder(context, AlarmReceiver.CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(title)
        .setContentText(reminderText)
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setAutoCancel(false)
        .setOngoing(true)
        .setContentIntent(popupPendingIntent)
        .addAction(0, "Dismiss", dismissPendingIntent)
        .addAction(0, "Snooze 5m", snoozePendingIntent)
    if (fullScreen) {
        builder.setFullScreenIntent(popupPendingIntent, true)
    }

    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.notify(taskId.toInt(), builder.build())
}

fun cancelAlarmNotification(context: Context, taskId: Long) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.cancel(taskId.toInt())
}

fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val name = context.getString(R.string.channel_name)
        val descriptionText = context.getString(R.string.channel_desc)
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(AlarmReceiver.CHANNEL_ID, name, importance).apply {
            description = descriptionText
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 600, 300, 600)
            setSound(null, null)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.deleteNotificationChannel(AlarmReceiver.LEGACY_CHANNEL_ID)
        notificationManager.createNotificationChannel(channel)
    }
}
