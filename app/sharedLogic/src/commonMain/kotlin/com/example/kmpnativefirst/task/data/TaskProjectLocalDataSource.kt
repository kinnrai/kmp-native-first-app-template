package com.example.kmpnativefirst.task.data

import com.example.kmpnativefirst.task.TaskProject
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

internal interface TaskProjectLocalDataSource {
    fun observeProjects(): Flow<List<TaskProjectItem>>

    fun observeProjectConflicts(): Flow<List<TaskProjectConflict>>

    suspend fun findProject(id: String): TaskProjectItem?

    suspend fun pendingProjectCount(): Int

    suspend fun projectConflictCount(): Int

    suspend fun applyProjectCreate(
        project: TaskProject,
        operationId: String,
        enqueuedAt: Instant,
    )

    suspend fun applyProjectUpdate(
        project: TaskProject,
        operationId: String,
        enqueuedAt: Instant,
    )

    suspend fun applyProjectDelete(
        projectId: String,
        operationId: String,
        taskOperationId: () -> String,
        enqueuedAt: Instant,
    )

    suspend fun nextProjectMutation(deletionsOnly: Boolean): PendingTaskProjectMutation?

    suspend fun acknowledgeProjectMutation(
        mutation: PendingTaskProjectMutation,
        remoteProject: TaskProject,
    ): Boolean

    suspend fun acknowledgeProjectDelete(mutation: PendingTaskProjectMutation): Boolean

    suspend fun rebaseProjectMutation(
        mutation: PendingTaskProjectMutation,
        remoteBase: TaskProject,
        mergedProject: TaskProject,
    ): Boolean

    suspend fun recordProjectConflict(
        mutation: PendingTaskProjectMutation,
        conflict: TaskProjectConflict,
    ): Boolean

    suspend fun replaceRemoteProjectSnapshot(
        remoteProjects: List<TaskProject>,
        taskOperationId: () -> String,
        changedAt: Instant,
    ): Int

    suspend fun resolveProjectConflict(
        projectId: String,
        resolution: TaskProjectConflictResolution,
        operationId: String,
        taskOperationId: () -> String,
        enqueuedAt: Instant,
    )
}
