package com.example.taskreminder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.taskreminder.data.TaskDatabase
import com.example.taskreminder.util.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "onReceive boot/package action: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = TaskDatabase.getDatabase(context)
                    val currentTime = System.currentTimeMillis()
                    val tasksToReschedule = db.taskDao().getPendingTasksForReschedule(currentTime)

                    Log.d(TAG, "Rescheduling ${tasksToReschedule.size} pending tasks after boot.")
                    for (task in tasksToReschedule) {
                        AlarmScheduler.scheduleTaskAlarm(context, task)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error rescheduling tasks on boot: ${e.message}", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
