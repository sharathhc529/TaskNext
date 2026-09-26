package com.example.taskreminder.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity): Long

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun deleteTaskById(taskId: Long)

    @Query("SELECT * FROM tasks WHERE id = :taskId LIMIT 1")
    suspend fun getTaskById(taskId: Long): TaskEntity?

    @Query("SELECT * FROM tasks ORDER BY scheduledTimestamp ASC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isCompleted = 0 AND scheduledTimestamp >= :currentTime ORDER BY scheduledTimestamp ASC")
    fun getActiveUpcomingTasks(currentTime: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isCompleted = 0 AND COALESCE(snoozedUntil, scheduledTimestamp - (reminderOffsetMinutes * 60000)) > :currentTime")
    suspend fun getPendingTasksForReschedule(currentTime: Long): List<TaskEntity>

    @Query("UPDATE tasks SET isCompleted = :completed WHERE id = :taskId")
    suspend fun setTaskCompleted(taskId: Long, completed: Boolean)

    @Query("UPDATE tasks SET snoozedUntil = :snoozedUntil WHERE id = :taskId")
    suspend fun setTaskSnoozed(taskId: Long, snoozedUntil: Long)
}
