package com.example.taskreminder.ui

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskreminder.TaskReminderApp
import com.example.taskreminder.data.TaskEntity
import com.example.taskreminder.util.AlarmScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PermissionState(
    val notificationsGranted: Boolean = true,
    val exactAlarmGranted: Boolean = true,
    val batteryOptimizationIgnored: Boolean = true,
    val overlayGranted: Boolean = true
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as TaskReminderApp).repository

    val allTasks: StateFlow<List<TaskEntity>> = repository.allTasks.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _permissionState = MutableStateFlow(PermissionState())
    val permissionState: StateFlow<PermissionState> = _permissionState.asStateFlow()

    init {
        checkPermissions()
    }

    fun checkPermissions() {
        val context = getApplication<Application>()
        
        // 1. Notification Permission (Android 13+)
        val notifGranted = NotificationManagerCompat.from(context).areNotificationsEnabled()

        // 2. Exact Alarm Permission (Android 12+)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val exactAlarmGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager?.canScheduleExactAlarms() ?: true
        } else {
            true
        }

        // 3. Battery Optimization
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val batteryIgnored = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
        } else {
            true
        }

        // 4. Overlay / Draw over other apps
        val overlayGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }

        _permissionState.value = PermissionState(
            notificationsGranted = notifGranted,
            exactAlarmGranted = exactAlarmGranted,
            batteryOptimizationIgnored = batteryIgnored,
            overlayGranted = overlayGranted
        )
    }

    fun addTask(
        title: String,
        description: String,
        scheduledTimestamp: Long,
        reminderOffsetMinutes: Int,
        alarmSoundUri: String?
    ) {
        viewModelScope.launch {
            val task = TaskEntity(
                title = title.trim(),
                description = description.trim(),
                scheduledTimestamp = scheduledTimestamp,
                reminderOffsetMinutes = reminderOffsetMinutes,
                alarmSoundUri = alarmSoundUri
            )
            val generatedId = repository.insert(task)
            val savedTask = task.copy(id = generatedId)
            
            // Schedule Alarm
            AlarmScheduler.scheduleTaskAlarm(getApplication(), savedTask)
        }
    }

    fun updateTask(task: TaskEntity) {
        viewModelScope.launch {
            repository.update(task)
            if (task.isPending) {
                AlarmScheduler.scheduleTaskAlarm(getApplication(), task)
            } else {
                AlarmScheduler.cancelTaskAlarm(getApplication(), task.id)
            }
        }
    }

    fun toggleTaskComplete(task: TaskEntity) {
        viewModelScope.launch {
            val updated = task.copy(isCompleted = !task.isCompleted)
            repository.update(updated)
            if (updated.isCompleted) {
                AlarmScheduler.cancelTaskAlarm(getApplication(), task.id)
            } else {
                AlarmScheduler.scheduleTaskAlarm(getApplication(), updated)
            }
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            AlarmScheduler.cancelTaskAlarm(getApplication(), task.id)
            repository.delete(task)
        }
    }
}
