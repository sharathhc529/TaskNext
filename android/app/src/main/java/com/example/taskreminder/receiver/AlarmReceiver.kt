package com.example.taskreminder.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.taskreminder.R
import com.example.taskreminder.data.TaskDatabase
import com.example.taskreminder.ui.ReminderPopupActivity
import com.example.taskreminder.util.AlarmScheduler
import com.example.taskreminder.util.ReminderSoundPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "AlarmReceiver"
        const val CHANNEL_ID = "task_foreground_alarms"
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

        Log.d(TAG, "onReceive: action=${intent.action}, taskId=$taskId, title=$taskTitle")

        when (intent.action) {
            ACTION_TASK_REMINDER -> {
                handleAlarmTrigger(context, taskId, taskTitle, taskDesc, scheduledTime, offsetMins)
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
        offsetMins: Int
    ) {
        // 1. Acquire WakeLock
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
            "TaskReminder:AlarmWakeLock"
        )
        wakeLock?.acquire(15000) // 15 seconds wake lock

        // 2. Start Sound and Vibration
        ReminderSoundPlayer.startAlarmSoundAndVibration(context)

        // 3. Ensure Notification Channel
        createNotificationChannel(context)

        // 4. Create FullScreen Intent to launch ReminderPopupActivity
        val popupIntent = Intent(context, ReminderPopupActivity::class.java).apply {
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

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            taskId.toInt(),
            popupIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 5. Action Pending Intents for Notification
        val dismissIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_DISMISS_ALARM
            putExtra(AlarmScheduler.EXTRA_TASK_ID, taskId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            (taskId * 10 + 1).toInt(),
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_SNOOZE_ALARM
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
            "Reminder ($offsetMins mins before due time): $desc"
        } else {
            "Due now: $desc"
        }

        // 6. Build High-Priority Notification
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(reminderText)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setOngoing(true)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .addAction(0, "Dismiss", dismissPendingIntent)
            .addAction(0, "Snooze 5m", snoozePendingIntent)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(taskId.toInt(), notification)

        // 7. Directly attempt to launch popup activity
        try {
            context.startActivity(popupIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Direct startActivity failed (will rely on fullScreenIntent): ${e.message}")
        }
    }

    private fun handleDismiss(context: Context, taskId: Long) {
        ReminderSoundPlayer.stop()
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(taskId.toInt())

        if (taskId != -1L) {
            CoroutineScope(Dispatchers.IO).launch {
                val db = TaskDatabase.getDatabase(context)
                db.taskDao().setTaskDismissed(taskId)
            }
        }
    }

    private fun handleSnooze(
        context: Context,
        taskId: Long,
        title: String,
        desc: String,
        scheduledTime: Long
    ) {
        ReminderSoundPlayer.stop()
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(taskId.toInt())

        AlarmScheduler.snoozeTaskAlarm(context, taskId, title, desc, scheduledTime, snoozeMinutes = 5)
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.channel_name)
            val descriptionText = context.getString(R.string.channel_desc)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 600, 300, 600)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
