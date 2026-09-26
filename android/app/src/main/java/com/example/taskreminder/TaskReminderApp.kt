package com.example.taskreminder

import android.app.Application
import com.example.taskreminder.data.TaskDatabase
import com.example.taskreminder.data.TaskRepository
import com.example.taskreminder.receiver.createNotificationChannel

class TaskReminderApp : Application() {

    val database by lazy { TaskDatabase.getDatabase(this) }
    val repository by lazy { TaskRepository(database.taskDao()) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel(this)
    }
}
