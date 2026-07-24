package com.example.kmpnativefirst.task.data

import com.example.kmpnativefirst.task.Task
import com.example.kmpnativefirst.task.TaskProject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Instant

internal class InMemoryTaskLocalDataSource(
    initialTasks: List<Task> = emptyList(),
    initialProjects: List<TaskProject> = emptyList(),
    restoredState: TaskLocalState? = null,
    private val persistState: suspend (TaskLocalState) -> Unit = {},
    private val closeState: () -> Unit = {},
) : TaskLocalDataSource, TaskProjectLocalDataSource {
    private val mutex = Mutex()
    private val records = restoredState?.records?.toMutableMap()
        ?: initialTasks.associate { task ->
            task.id to LocalTaskRecord(
                task = task,
                syncState = TaskSyncState.SYNCED,
                isDeleted = false,
            )
        }.toMutableMap()
    private val outbox = restoredState?.outbox?.toMutableMap()
        ?: mutableMapOf()
    private val conflicts = restoredState?.conflicts?.toMutableMap()
        ?: mutableMapOf()
    private val projectRecords = restoredState?.projectRecords?.toMutableMap()
        ?: initialProjects.associate { project ->
            project.id to LocalTaskProjectRecord(
                project = project,
                syncState = TaskSyncState.SYNCED,
                isDeleted = false,
            )
        }.toMutableMap()
    private val projectOutbox = restoredState?.projectOutbox?.toMutableMap()
        ?: mutableMapOf()
    private val projectConflicts = restoredState?.projectConflicts?.toMutableMap()
        ?: mutableMapOf()
    private val taskFlow = MutableStateFlow(visibleTasks())
    private val conflictFlow = MutableStateFlow(
        conflicts.values.sortedByDescending(TaskConflict::detectedAt),
    )
    private val projectFlow = MutableStateFlow(visibleProjects())
    private val projectConflictFlow = MutableStateFlow(
        projectConflicts.values.sortedByDescending(TaskProjectConflict::detectedAt),
    )

    override fun observeTasks(): Flow<List<TaskItem>> = taskFlow.asStateFlow()

    override fun observeConflicts(): Flow<List<TaskConflict>> = conflictFlow.asStateFlow()

    override fun observeProjects(): Flow<List<TaskProjectItem>> = projectFlow.asStateFlow()

    override fun observeProjectConflicts(): Flow<List<TaskProjectConflict>> =
        projectConflictFlow.asStateFlow()

    override suspend fun findTask(id: String): TaskItem? = mutex.withLock {
        records[id]
            ?.takeUnless(LocalTaskRecord::isDeleted)
            ?.toTaskItem()
    }

    override suspend fun pendingCount(): Int = mutex.withLock { outbox.size }

    override suspend fun conflictCount(): Int = mutex.withLock { conflicts.size }

    override suspend fun findProject(id: String): TaskProjectItem? = mutex.withLock {
        projectRecords[id]
            ?.takeUnless(LocalTaskProjectRecord::isDeleted)
            ?.toProjectItem()
    }

    override suspend fun pendingProjectCount(): Int = mutex.withLock { projectOutbox.size }

    override suspend fun projectConflictCount(): Int = mutex.withLock {
        projectConflicts.size
    }

    override suspend fun applyCreate(
        task: Task,
        operationId: String,
        enqueuedAt: Instant,
    ) = mutate {
        if (task.id in records) {
            throw DuplicateCachedTaskException(task.id)
        }
        records[task.id] = LocalTaskRecord(task, TaskSyncState.PENDING, isDeleted = false)
        outbox[task.id] = PendingTaskMutation(
            operationId = operationId,
            taskId = task.id,
            kind = TaskMutationKind.CREATE,
            base = null,
            desired = task,
            enqueuedAt = enqueuedAt,
        )
    }

    override suspend fun applyUpdate(
        task: Task,
        operationId: String,
        enqueuedAt: Instant,
    ) = mutate {
        applyTaskUpdateLocked(task, operationId, enqueuedAt)
    }

    override suspend fun applyDelete(
        taskId: String,
        operationId: String,
        enqueuedAt: Instant,
    ) = mutate {
        val current = records[taskId]
            ?.takeUnless(LocalTaskRecord::isDeleted)
            ?: throw CachedTaskNotFoundException(taskId)
        if (current.syncState == TaskSyncState.CONFLICT) {
            throw UnresolvedTaskConflictException(taskId)
        }
        val currentMutation = outbox[taskId]
        if (currentMutation?.kind == TaskMutationKind.CREATE) {
            records.remove(taskId)
            outbox.remove(taskId)
            conflicts.remove(taskId)
            return@mutate
        }
        records[taskId] = current.copy(
            syncState = TaskSyncState.PENDING,
            isDeleted = true,
        )
        outbox[taskId] = PendingTaskMutation(
            operationId = operationId,
            taskId = taskId,
            kind = TaskMutationKind.DELETE,
            base = currentMutation?.base ?: current.task,
            desired = null,
            enqueuedAt = enqueuedAt,
        )
    }

    override suspend fun nextMutation(): PendingTaskMutation? = mutex.withLock {
        outbox.values.minWithOrNull(
            compareBy(PendingTaskMutation::enqueuedAt, PendingTaskMutation::operationId),
        )
    }

    override suspend fun acknowledgeMutation(
        mutation: PendingTaskMutation,
        remoteTask: Task,
    ): Boolean = mutateWithResult {
        val current = outbox[mutation.taskId] ?: return@mutateWithResult false
        if (current.operationId == mutation.operationId) {
            outbox.remove(mutation.taskId)
            conflicts.remove(mutation.taskId)
            records[mutation.taskId] = LocalTaskRecord(
                task = remoteTask,
                syncState = TaskSyncState.SYNCED,
                isDeleted = false,
            )
            return@mutateWithResult true
        }

        val rebasedDesired = current.desired?.let { desired ->
            desired.copy(
                createdAt = remoteTask.createdAt,
                revision = remoteTask.revision,
            )
        }
        outbox[mutation.taskId] = current.copy(
            kind = if (current.kind == TaskMutationKind.DELETE) {
                TaskMutationKind.DELETE
            } else {
                TaskMutationKind.UPDATE
            },
            base = remoteTask,
            desired = rebasedDesired,
        )
        records[mutation.taskId] = LocalTaskRecord(
            task = rebasedDesired ?: remoteTask,
            syncState = TaskSyncState.PENDING,
            isDeleted = current.kind == TaskMutationKind.DELETE,
        )
        true
    }

    override suspend fun acknowledgeDelete(mutation: PendingTaskMutation): Boolean =
        mutateWithResult {
            val current = outbox[mutation.taskId] ?: return@mutateWithResult false
            if (current.operationId != mutation.operationId) {
                return@mutateWithResult false
            }
            outbox.remove(mutation.taskId)
            conflicts.remove(mutation.taskId)
            records.remove(mutation.taskId)
            true
        }

    override suspend fun rebaseMutation(
        mutation: PendingTaskMutation,
        remoteBase: Task,
        mergedTask: Task,
    ): Boolean = mutateWithResult {
        val current = outbox[mutation.taskId] ?: return@mutateWithResult false
        if (current.operationId != mutation.operationId) {
            return@mutateWithResult false
        }
        outbox[mutation.taskId] = current.copy(
            kind = TaskMutationKind.UPDATE,
            base = remoteBase,
            desired = mergedTask.copy(
                createdAt = remoteBase.createdAt,
                revision = remoteBase.revision,
            ),
        )
        records[mutation.taskId] = LocalTaskRecord(
            task = requireNotNull(outbox[mutation.taskId]?.desired),
            syncState = TaskSyncState.PENDING,
            isDeleted = false,
        )
        true
    }

    override suspend fun recordConflict(
        mutation: PendingTaskMutation,
        conflict: TaskConflict,
    ): Boolean = mutateWithResult {
        val current = outbox[mutation.taskId] ?: return@mutateWithResult false
        if (current.operationId != mutation.operationId) {
            return@mutateWithResult false
        }
        outbox.remove(mutation.taskId)
        conflicts[mutation.taskId] = conflict
        val visibleTask = conflict.local ?: conflict.remote
        if (visibleTask == null) {
            records.remove(mutation.taskId)
        } else {
            records[mutation.taskId] = LocalTaskRecord(
                task = visibleTask,
                syncState = TaskSyncState.CONFLICT,
                isDeleted = false,
            )
        }
        true
    }

    override suspend fun replaceRemoteSnapshot(remoteTasks: List<Task>): Int =
        mutateWithResult {
            val remoteById = remoteTasks.associateBy(Task::id)
            val protectedIds = outbox.keys + conflicts.keys
            val removableIds = records
                .filterValues { record -> record.syncState == TaskSyncState.SYNCED }
                .keys - remoteById.keys
            removableIds.forEach(records::remove)

            remoteById.forEach { (id, task) ->
                if (id !in protectedIds) {
                    records[id] = LocalTaskRecord(
                        task = task,
                        syncState = TaskSyncState.SYNCED,
                        isDeleted = false,
                    )
                }
            }
            remoteTasks.size
        }

    override suspend fun resolveConflict(
        taskId: String,
        resolution: TaskConflictResolution,
        operationId: String,
        enqueuedAt: Instant,
    ) = mutate {
        val conflict = conflicts[taskId]
            ?: throw CachedTaskNotFoundException(taskId)
        when (resolution) {
            TaskConflictResolution.UseRemote -> {
                outbox.remove(taskId)
                val remote = conflict.remote
                if (remote == null) {
                    records.remove(taskId)
                } else {
                    records[taskId] = LocalTaskRecord(
                        task = remote,
                        syncState = TaskSyncState.SYNCED,
                        isDeleted = false,
                    )
                }
            }
            TaskConflictResolution.KeepLocal -> enqueueResolution(
                conflict = conflict,
                desired = conflict.local,
                operationId = operationId,
                enqueuedAt = enqueuedAt,
            )
            is TaskConflictResolution.Merge -> {
                val source = conflict.local ?: conflict.remote
                    ?: throw CachedTaskNotFoundException(taskId)
                enqueueResolution(
                    conflict = conflict,
                    desired = source.withEdit(resolution.edit),
                    operationId = operationId,
                    enqueuedAt = enqueuedAt,
                )
            }
        }
        conflicts.remove(taskId)
    }

    override suspend fun applyProjectCreate(
        project: TaskProject,
        operationId: String,
        enqueuedAt: Instant,
    ) = mutate {
        if (project.id in projectRecords) {
            throw DuplicateCachedTaskProjectException(project.id)
        }
        projectRecords[project.id] = LocalTaskProjectRecord(
            project,
            TaskSyncState.PENDING,
            isDeleted = false,
        )
        projectOutbox[project.id] = PendingTaskProjectMutation(
            operationId = operationId,
            projectId = project.id,
            kind = TaskMutationKind.CREATE,
            base = null,
            desired = project,
            enqueuedAt = enqueuedAt,
        )
    }

    override suspend fun applyProjectUpdate(
        project: TaskProject,
        operationId: String,
        enqueuedAt: Instant,
    ) = mutate {
        applyProjectUpdateLocked(project, operationId, enqueuedAt)
    }

    override suspend fun applyProjectDelete(
        projectId: String,
        operationId: String,
        taskOperationId: () -> String,
        enqueuedAt: Instant,
    ) = mutate {
        val current = projectRecords[projectId]
            ?.takeUnless(LocalTaskProjectRecord::isDeleted)
            ?: throw CachedTaskProjectNotFoundException(projectId)
        if (current.syncState == TaskSyncState.CONFLICT) {
            throw UnresolvedTaskProjectConflictException(projectId)
        }
        conflicts.values
            .firstOrNull { conflict -> conflict.referencesProject(projectId) }
            ?.let { throw UnresolvedTaskConflictException(it.taskId) }
        clearProjectReferences(
            projectIds = setOf(projectId),
            taskOperationId = taskOperationId,
            changedAt = enqueuedAt,
            enqueueSyncedTasks = true,
        )
        val currentMutation = projectOutbox[projectId]
        if (currentMutation?.kind == TaskMutationKind.CREATE) {
            projectRecords.remove(projectId)
            projectOutbox.remove(projectId)
            projectConflicts.remove(projectId)
            return@mutate
        }
        projectRecords[projectId] = current.copy(
            syncState = TaskSyncState.PENDING,
            isDeleted = true,
        )
        projectOutbox[projectId] = PendingTaskProjectMutation(
            operationId = operationId,
            projectId = projectId,
            kind = TaskMutationKind.DELETE,
            base = currentMutation?.base ?: current.project,
            desired = null,
            enqueuedAt = enqueuedAt,
        )
    }

    override suspend fun nextProjectMutation(
        deletionsOnly: Boolean,
    ): PendingTaskProjectMutation? = mutex.withLock {
        projectOutbox.values
            .asSequence()
            .filter { mutation ->
                (mutation.kind == TaskMutationKind.DELETE) == deletionsOnly
            }
            .minWithOrNull(
                compareBy(
                    PendingTaskProjectMutation::enqueuedAt,
                    PendingTaskProjectMutation::operationId,
                ),
            )
    }

    override suspend fun acknowledgeProjectMutation(
        mutation: PendingTaskProjectMutation,
        remoteProject: TaskProject,
    ): Boolean = mutateWithResult {
        val current = projectOutbox[mutation.projectId]
            ?: return@mutateWithResult false
        if (current.operationId == mutation.operationId) {
            projectOutbox.remove(mutation.projectId)
            projectConflicts.remove(mutation.projectId)
            projectRecords[mutation.projectId] = LocalTaskProjectRecord(
                project = remoteProject,
                syncState = TaskSyncState.SYNCED,
                isDeleted = false,
            )
            return@mutateWithResult true
        }

        val rebasedDesired = current.desired?.let { desired ->
            desired.copy(
                createdAt = remoteProject.createdAt,
                revision = remoteProject.revision,
            )
        }
        projectOutbox[mutation.projectId] = current.copy(
            kind = if (current.kind == TaskMutationKind.DELETE) {
                TaskMutationKind.DELETE
            } else {
                TaskMutationKind.UPDATE
            },
            base = remoteProject,
            desired = rebasedDesired,
        )
        projectRecords[mutation.projectId] = LocalTaskProjectRecord(
            project = rebasedDesired ?: remoteProject,
            syncState = TaskSyncState.PENDING,
            isDeleted = current.kind == TaskMutationKind.DELETE,
        )
        true
    }

    override suspend fun acknowledgeProjectDelete(
        mutation: PendingTaskProjectMutation,
    ): Boolean = mutateWithResult {
        val current = projectOutbox[mutation.projectId]
            ?: return@mutateWithResult false
        if (current.operationId != mutation.operationId) {
            return@mutateWithResult false
        }
        projectOutbox.remove(mutation.projectId)
        projectConflicts.remove(mutation.projectId)
        projectRecords.remove(mutation.projectId)
        true
    }

    override suspend fun rebaseProjectMutation(
        mutation: PendingTaskProjectMutation,
        remoteBase: TaskProject,
        mergedProject: TaskProject,
    ): Boolean = mutateWithResult {
        val current = projectOutbox[mutation.projectId]
            ?: return@mutateWithResult false
        if (current.operationId != mutation.operationId) {
            return@mutateWithResult false
        }
        val desired = mergedProject.copy(
            createdAt = remoteBase.createdAt,
            revision = remoteBase.revision,
        )
        projectOutbox[mutation.projectId] = current.copy(
            kind = TaskMutationKind.UPDATE,
            base = remoteBase,
            desired = desired,
        )
        projectRecords[mutation.projectId] = LocalTaskProjectRecord(
            project = desired,
            syncState = TaskSyncState.PENDING,
            isDeleted = false,
        )
        true
    }

    override suspend fun recordProjectConflict(
        mutation: PendingTaskProjectMutation,
        conflict: TaskProjectConflict,
    ): Boolean = mutateWithResult {
        val current = projectOutbox[mutation.projectId]
            ?: return@mutateWithResult false
        if (current.operationId != mutation.operationId) {
            return@mutateWithResult false
        }
        projectOutbox.remove(mutation.projectId)
        projectConflicts[mutation.projectId] = conflict
        val visibleProject = conflict.local ?: conflict.remote
        if (visibleProject == null) {
            projectRecords.remove(mutation.projectId)
        } else {
            projectRecords[mutation.projectId] = LocalTaskProjectRecord(
                project = visibleProject,
                syncState = TaskSyncState.CONFLICT,
                isDeleted = false,
            )
        }
        true
    }

    override suspend fun replaceRemoteProjectSnapshot(
        remoteProjects: List<TaskProject>,
        taskOperationId: () -> String,
        changedAt: Instant,
    ): Int = mutateWithResult {
        val remoteById = remoteProjects.associateBy(TaskProject::id)
        val conflictReferencedIds = conflicts.values
            .flatMapTo(mutableSetOf()) { it.referencedProjectIds() }
        val mutationProtectedIds = projectOutbox.keys + projectConflicts.keys
        val removalProtectedIds = mutationProtectedIds + conflictReferencedIds
        val removableIds = projectRecords
            .filterValues { record -> record.syncState == TaskSyncState.SYNCED }
            .keys - remoteById.keys - removalProtectedIds
        clearProjectReferences(
            projectIds = removableIds,
            taskOperationId = taskOperationId,
            changedAt = changedAt,
            enqueueSyncedTasks = false,
        )
        removableIds.forEach(projectRecords::remove)

        remoteById.forEach { (id, project) ->
            if (id !in mutationProtectedIds) {
                projectRecords[id] = LocalTaskProjectRecord(
                    project = project,
                    syncState = TaskSyncState.SYNCED,
                    isDeleted = false,
                )
            }
        }
        remoteProjects.size
    }

    override suspend fun resolveProjectConflict(
        projectId: String,
        resolution: TaskProjectConflictResolution,
        operationId: String,
        taskOperationId: () -> String,
        enqueuedAt: Instant,
    ) = mutate {
        val conflict = projectConflicts[projectId]
            ?: throw CachedTaskProjectNotFoundException(projectId)
        when (resolution) {
            TaskProjectConflictResolution.UseRemote -> {
                projectOutbox.remove(projectId)
                val remote = conflict.remote
                if (remote == null) {
                    clearProjectReferences(
                        projectIds = setOf(projectId),
                        taskOperationId = taskOperationId,
                        changedAt = enqueuedAt,
                        enqueueSyncedTasks = true,
                    )
                    projectRecords.remove(projectId)
                } else {
                    projectRecords[projectId] = LocalTaskProjectRecord(
                        project = remote,
                        syncState = TaskSyncState.SYNCED,
                        isDeleted = false,
                    )
                }
            }
            TaskProjectConflictResolution.KeepLocal -> enqueueProjectResolution(
                conflict = conflict,
                desired = conflict.local,
                operationId = operationId,
                enqueuedAt = enqueuedAt,
            )
            is TaskProjectConflictResolution.Merge -> {
                val source = conflict.local ?: conflict.remote
                    ?: throw CachedTaskProjectNotFoundException(projectId)
                enqueueProjectResolution(
                    conflict = conflict,
                    desired = source.withEdit(resolution.edit),
                    operationId = operationId,
                    enqueuedAt = enqueuedAt,
                )
            }
        }
        projectConflicts.remove(projectId)
    }

    override fun close() {
        closeState()
    }

    private fun applyTaskUpdateLocked(
        task: Task,
        operationId: String,
        enqueuedAt: Instant,
    ) {
        val current = records[task.id]
            ?.takeUnless(LocalTaskRecord::isDeleted)
            ?: throw CachedTaskNotFoundException(task.id)
        if (current.syncState == TaskSyncState.CONFLICT) {
            throw UnresolvedTaskConflictException(task.id)
        }
        val currentMutation = outbox[task.id]
        records[task.id] = LocalTaskRecord(
            task,
            TaskSyncState.PENDING,
            isDeleted = false,
        )
        outbox[task.id] = when (currentMutation?.kind) {
            TaskMutationKind.CREATE,
            TaskMutationKind.UPDATE,
            -> currentMutation.copy(
                operationId = operationId,
                desired = task,
                enqueuedAt = enqueuedAt,
            )
            TaskMutationKind.DELETE -> throw InvalidCachedTaskStateException(task.id)
            null -> PendingTaskMutation(
                operationId = operationId,
                taskId = task.id,
                kind = TaskMutationKind.UPDATE,
                base = current.task,
                desired = task,
                enqueuedAt = enqueuedAt,
            )
        }
    }

    private fun applyProjectUpdateLocked(
        project: TaskProject,
        operationId: String,
        enqueuedAt: Instant,
    ) {
        val current = projectRecords[project.id]
            ?.takeUnless(LocalTaskProjectRecord::isDeleted)
            ?: throw CachedTaskProjectNotFoundException(project.id)
        if (current.syncState == TaskSyncState.CONFLICT) {
            throw UnresolvedTaskProjectConflictException(project.id)
        }
        val currentMutation = projectOutbox[project.id]
        projectRecords[project.id] = LocalTaskProjectRecord(
            project,
            TaskSyncState.PENDING,
            isDeleted = false,
        )
        projectOutbox[project.id] = when (currentMutation?.kind) {
            TaskMutationKind.CREATE,
            TaskMutationKind.UPDATE,
            -> currentMutation.copy(
                operationId = operationId,
                desired = project,
                enqueuedAt = enqueuedAt,
            )
            TaskMutationKind.DELETE ->
                throw InvalidCachedTaskProjectStateException(project.id)
            null -> PendingTaskProjectMutation(
                operationId = operationId,
                projectId = project.id,
                kind = TaskMutationKind.UPDATE,
                base = current.project,
                desired = project,
                enqueuedAt = enqueuedAt,
            )
        }
    }

    private fun clearProjectReferences(
        projectIds: Set<String>,
        taskOperationId: () -> String,
        changedAt: Instant,
        enqueueSyncedTasks: Boolean,
    ) {
        if (projectIds.isEmpty()) return
        records.values
            .filter { record ->
                !record.isDeleted && record.task.projectId in projectIds
            }
            .forEach { record ->
                val updated = record.task.copy(
                    projectId = null,
                    updatedAt = changedAt,
                )
                if (record.syncState == TaskSyncState.PENDING || enqueueSyncedTasks) {
                    applyTaskUpdateLocked(
                        task = updated,
                        operationId = taskOperationId(),
                        enqueuedAt = changedAt,
                    )
                } else {
                    records[record.task.id] = record.copy(task = updated)
                }
            }
    }

    private fun enqueueProjectResolution(
        conflict: TaskProjectConflict,
        desired: TaskProject?,
        operationId: String,
        enqueuedAt: Instant,
    ) {
        val remote = conflict.remote
        when {
            remote == null && desired == null -> {
                projectRecords.remove(conflict.projectId)
                projectOutbox.remove(conflict.projectId)
            }
            remote == null -> {
                val create = requireNotNull(desired).copy(revision = 0)
                projectRecords[conflict.projectId] = LocalTaskProjectRecord(
                    create,
                    TaskSyncState.PENDING,
                    isDeleted = false,
                )
                projectOutbox[conflict.projectId] = PendingTaskProjectMutation(
                    operationId,
                    conflict.projectId,
                    TaskMutationKind.CREATE,
                    base = null,
                    desired = create,
                    enqueuedAt,
                )
            }
            desired == null -> {
                projectRecords[conflict.projectId] = LocalTaskProjectRecord(
                    remote,
                    TaskSyncState.PENDING,
                    isDeleted = true,
                )
                projectOutbox[conflict.projectId] = PendingTaskProjectMutation(
                    operationId,
                    conflict.projectId,
                    TaskMutationKind.DELETE,
                    base = remote,
                    desired = null,
                    enqueuedAt,
                )
            }
            else -> {
                val update = desired.copy(
                    createdAt = remote.createdAt,
                    revision = remote.revision,
                )
                projectRecords[conflict.projectId] = LocalTaskProjectRecord(
                    update,
                    TaskSyncState.PENDING,
                    isDeleted = false,
                )
                projectOutbox[conflict.projectId] = PendingTaskProjectMutation(
                    operationId,
                    conflict.projectId,
                    TaskMutationKind.UPDATE,
                    base = remote,
                    desired = update,
                    enqueuedAt,
                )
            }
        }
    }

    private fun enqueueResolution(
        conflict: TaskConflict,
        desired: Task?,
        operationId: String,
        enqueuedAt: Instant,
    ) {
        val remote = conflict.remote
        when {
            remote == null && desired == null -> {
                records.remove(conflict.taskId)
                outbox.remove(conflict.taskId)
            }
            remote == null -> {
                val create = requireNotNull(desired).copy(revision = 0)
                records[conflict.taskId] = LocalTaskRecord(
                    create,
                    TaskSyncState.PENDING,
                    isDeleted = false,
                )
                outbox[conflict.taskId] = PendingTaskMutation(
                    operationId,
                    conflict.taskId,
                    TaskMutationKind.CREATE,
                    base = null,
                    desired = create,
                    enqueuedAt,
                )
            }
            desired == null -> {
                records[conflict.taskId] = LocalTaskRecord(
                    remote,
                    TaskSyncState.PENDING,
                    isDeleted = true,
                )
                outbox[conflict.taskId] = PendingTaskMutation(
                    operationId,
                    conflict.taskId,
                    TaskMutationKind.DELETE,
                    base = remote,
                    desired = null,
                    enqueuedAt,
                )
            }
            else -> {
                val update = desired.copy(
                    createdAt = remote.createdAt,
                    revision = remote.revision,
                )
                records[conflict.taskId] = LocalTaskRecord(
                    update,
                    TaskSyncState.PENDING,
                    isDeleted = false,
                )
                outbox[conflict.taskId] = PendingTaskMutation(
                    operationId,
                    conflict.taskId,
                    TaskMutationKind.UPDATE,
                    base = remote,
                    desired = update,
                    enqueuedAt,
                )
            }
        }
    }

    private suspend fun mutate(block: () -> Unit) {
        mutex.withLock {
            mutateAndPersist(block)
            publish()
        }
    }

    private suspend fun <T> mutateWithResult(block: () -> T): T = mutex.withLock {
        mutateAndPersist(block).also { publish() }
    }

    private suspend fun <T> mutateAndPersist(block: () -> T): T {
        val before = currentState()
        return try {
            block().also {
                persistState(currentState())
            }
        } catch (error: Throwable) {
            restore(before)
            throw error
        }
    }

    private fun currentState(): TaskLocalState = TaskLocalState(
        records = records.toMap(),
        outbox = outbox.toMap(),
        conflicts = conflicts.toMap(),
        projectRecords = projectRecords.toMap(),
        projectOutbox = projectOutbox.toMap(),
        projectConflicts = projectConflicts.toMap(),
    )

    private fun restore(state: TaskLocalState) {
        records.clear()
        records.putAll(state.records)
        outbox.clear()
        outbox.putAll(state.outbox)
        conflicts.clear()
        conflicts.putAll(state.conflicts)
        projectRecords.clear()
        projectRecords.putAll(state.projectRecords)
        projectOutbox.clear()
        projectOutbox.putAll(state.projectOutbox)
        projectConflicts.clear()
        projectConflicts.putAll(state.projectConflicts)
    }

    private fun publish() {
        taskFlow.value = visibleTasks()
        conflictFlow.value = conflicts.values.sortedByDescending(TaskConflict::detectedAt)
        projectFlow.value = visibleProjects()
        projectConflictFlow.value =
            projectConflicts.values.sortedByDescending(TaskProjectConflict::detectedAt)
    }

    private fun visibleTasks(): List<TaskItem> = records.values
        .asSequence()
        .filterNot(LocalTaskRecord::isDeleted)
        .map(LocalTaskRecord::toTaskItem)
        .sortedWith(taskItemComparator)
        .toList()

    private fun visibleProjects(): List<TaskProjectItem> = projectRecords.values
        .asSequence()
        .filterNot(LocalTaskProjectRecord::isDeleted)
        .map(LocalTaskProjectRecord::toProjectItem)
        .sortedBy { it.project.name.lowercase() }
        .toList()
}

@Serializable
internal data class LocalTaskRecord(
    val task: Task,
    val syncState: TaskSyncState,
    val isDeleted: Boolean,
) {
    fun toTaskItem(): TaskItem = TaskItem(task, syncState)
}

@Serializable
internal data class LocalTaskProjectRecord(
    val project: TaskProject,
    val syncState: TaskSyncState,
    val isDeleted: Boolean,
) {
    fun toProjectItem(): TaskProjectItem = TaskProjectItem(project, syncState)
}

@Serializable
internal data class TaskLocalState(
    val records: Map<String, LocalTaskRecord> = emptyMap(),
    val outbox: Map<String, PendingTaskMutation> = emptyMap(),
    val conflicts: Map<String, TaskConflict> = emptyMap(),
    val projectRecords: Map<String, LocalTaskProjectRecord> = emptyMap(),
    val projectOutbox: Map<String, PendingTaskProjectMutation> = emptyMap(),
    val projectConflicts: Map<String, TaskProjectConflict> = emptyMap(),
)

private fun Task.withEdit(edit: TaskEdit): Task = copy(
    title = edit.title,
    notes = edit.notes,
    projectId = edit.projectId,
    labelIds = edit.labelIds ?: labelIds,
    priority = edit.priority,
    dueDate = edit.dueDate,
    dueAt = edit.dueAt,
    isCompleted = edit.isCompleted,
)

private fun TaskProject.withEdit(edit: TaskProjectEdit): TaskProject = copy(
    name = edit.name,
    color = edit.color,
)

private fun TaskConflict.referencesProject(projectId: String): Boolean =
    projectId in referencedProjectIds()

private fun TaskConflict.referencedProjectIds(): Set<String> = buildSet {
    base?.projectId?.let(::add)
    local?.projectId?.let(::add)
    remote?.projectId?.let(::add)
}

private val taskItemComparator = compareBy<TaskItem>(
    { it.task.isCompleted },
    { it.task.dueDate == null && it.task.dueAt == null },
    { it.task.dueDate },
    { it.task.dueAt },
).thenByDescending { it.task.updatedAt }
