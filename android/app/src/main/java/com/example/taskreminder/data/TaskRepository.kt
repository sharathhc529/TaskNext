package com.example.taskreminder.data

import kotlinx.coroutines.flow.Flow

class TaskRepository(private val taskDao: TaskDao) {

    val allTasks: Flow<List<TaskEntity>> = taskDao.getAllTasks()

    suspend fun insert(task: TaskEntity): Long = taskDao.insertTask(task)

    suspend fun update(task: TaskEntity) = taskDao.updateTask(task)

    suspend fun delete(task: TaskEntity) = taskDao.deleteTask(task)

    suspend fun deleteById(taskId: Long) = taskDao.deleteTaskById(taskId)

    suspend fun getTaskById(taskId: Long): TaskEntity? = taskDao.getTaskById(taskId)

    suspend fun setTaskCompleted(taskId: Long, completed: Boolean) =
        taskDao.setTaskCompleted(taskId, completed)

    suspend fun getPendingTasksForReschedule(currentTime: Long): List<TaskEntity> =
        taskDao.getPendingTasksForReschedule(currentTime)
}
