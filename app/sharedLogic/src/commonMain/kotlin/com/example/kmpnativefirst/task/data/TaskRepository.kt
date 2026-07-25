package com.example.kmpnativefirst.task.data

import com.example.kmpnativefirst.task.Task
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

@OptIn(ExperimentalObjCRefinement::class)
interface TaskRepository {
    @HiddenFromObjC
    val tasks: Flow<List<TaskItem>>

    @HiddenFromObjC
    val conflicts: Flow<List<TaskConflict>>

    @HiddenFromObjC
    val syncStatus: StateFlow<TaskSyncStatus>

    @Throws(Exception::class)
    suspend fun create(draft: TaskDraft): Task

    @Throws(Exception::class)
    suspend fun update(
        taskId: String,
        edit: TaskEdit,
    ): Task

    @Throws(Exception::class)
    suspend fun toggleCompleted(taskId: String): Task

    @Throws(Exception::class)
    suspend fun delete(taskId: String)

    @Throws(Exception::class)
    suspend fun clearCompleted()

    @Throws(Exception::class)
    suspend fun resolveConflict(
        taskId: String,
        resolution: TaskConflictResolution,
    )

    @Throws(Exception::class)
    suspend fun sync(): TaskSyncResult

    @Throws(Exception::class)
    fun close()
}
