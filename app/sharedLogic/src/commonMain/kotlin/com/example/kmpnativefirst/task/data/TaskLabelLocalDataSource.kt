package com.example.kmpnativefirst.task.data

import com.example.kmpnativefirst.task.TaskLabel
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

internal interface TaskLabelLocalDataSource {
    fun observeLabels(): Flow<List<TaskLabelItem>>

    fun observeLabelConflicts(): Flow<List<TaskLabelConflict>>

    suspend fun findLabel(id: String): TaskLabelItem?

    suspend fun pendingLabelCount(): Int

    suspend fun labelConflictCount(): Int

    suspend fun applyLabelCreate(
        label: TaskLabel,
        operationId: String,
        enqueuedAt: Instant,
    )

    suspend fun applyLabelUpdate(
        label: TaskLabel,
        operationId: String,
        enqueuedAt: Instant,
    )

    suspend fun applyLabelDelete(
        labelId: String,
        operationId: String,
        taskOperationId: () -> String,
        enqueuedAt: Instant,
    )

    suspend fun nextLabelMutation(deletionsOnly: Boolean): PendingTaskLabelMutation?

    suspend fun acknowledgeLabelMutation(
        mutation: PendingTaskLabelMutation,
        remoteLabel: TaskLabel,
    ): Boolean

    suspend fun acknowledgeLabelDelete(mutation: PendingTaskLabelMutation): Boolean

    suspend fun rebaseLabelMutation(
        mutation: PendingTaskLabelMutation,
        remoteBase: TaskLabel,
        mergedLabel: TaskLabel,
    ): Boolean

    suspend fun recordLabelConflict(
        mutation: PendingTaskLabelMutation,
        conflict: TaskLabelConflict,
    ): Boolean

    suspend fun replaceRemoteLabelSnapshot(
        remoteLabels: List<TaskLabel>,
        taskOperationId: () -> String,
        changedAt: Instant,
    ): Int

    suspend fun resolveLabelConflict(
        labelId: String,
        resolution: TaskLabelConflictResolution,
        operationId: String,
        taskOperationId: () -> String,
        enqueuedAt: Instant,
    )
}
