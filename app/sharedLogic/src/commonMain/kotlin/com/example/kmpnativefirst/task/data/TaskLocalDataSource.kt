package com.example.kmpnativefirst.task.data

import com.example.kmpnativefirst.task.Task
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

internal interface TaskLocalDataSource {
    fun observeTasks(): Flow<List<TaskItem>>

    fun observeConflicts(): Flow<List<TaskConflict>>

    suspend fun findTask(id: String): TaskItem?

    suspend fun pendingCount(): Int

    suspend fun conflictCount(): Int

    suspend fun applyCreate(
        task: Task,
        operationId: String,
        enqueuedAt: Instant,
    )

    suspend fun applyUpdate(
        task: Task,
        operationId: String,
        enqueuedAt: Instant,
    )

    suspend fun applyDelete(
        taskId: String,
        operationId: String,
        enqueuedAt: Instant,
    )

    suspend fun nextMutation(): PendingTaskMutation?

    suspend fun acknowledgeMutation(
        mutation: PendingTaskMutation,
        remoteTask: Task,
    ): Boolean

    suspend fun acknowledgeDelete(mutation: PendingTaskMutation): Boolean

    suspend fun rebaseMutation(
        mutation: PendingTaskMutation,
        remoteBase: Task,
        mergedTask: Task,
    ): Boolean

    suspend fun recordConflict(
        mutation: PendingTaskMutation,
        conflict: TaskConflict,
    ): Boolean

    suspend fun replaceRemoteSnapshot(remoteTasks: List<Task>): Int

    suspend fun resolveConflict(
        taskId: String,
        resolution: TaskConflictResolution,
        operationId: String,
        enqueuedAt: Instant,
    )

    fun close()
}
