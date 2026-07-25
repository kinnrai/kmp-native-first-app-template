package com.example.kmpnativefirst.task.data

import com.example.kmpnativefirst.task.Task
import com.example.kmpnativefirst.task.TaskProject
import com.example.kmpnativefirst.task.TaskProjectValidator
import com.example.kmpnativefirst.task.TaskValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class OfflineFirstTaskRepository(
    private val local: TaskLocalDataSource,
    private val remote: TaskRemoteDataSource,
    private val projectLocal: TaskProjectLocalDataSource =
        requireNotNull(local as? TaskProjectLocalDataSource) {
            "The task cache must also provide task project storage."
        },
    private val projectRemote: TaskProjectRemoteDataSource =
        EmptyTaskProjectRemoteDataSource,
    private val clock: Clock = Clock.System,
    private val idGenerator: () -> String = { Uuid.random().toString() },
) : TaskRepository {
    override val tasks = local.observeTasks()
    override val conflicts = local.observeConflicts()
    override val projects = projectLocal.observeProjects()
    override val projectConflicts = projectLocal.observeProjectConflicts()

    private val syncMutex = Mutex()
    private val mutableSyncStatus = MutableStateFlow(TaskSyncStatus())
    override val syncStatus: StateFlow<TaskSyncStatus> = mutableSyncStatus.asStateFlow()

    internal suspend fun initialize(): OfflineFirstTaskRepository {
        refreshStatus()
        return this
    }

    override suspend fun create(draft: TaskDraft): Task {
        val input = validateAndNormalize(
            title = draft.title,
            notes = draft.notes,
            projectId = draft.projectId,
            dueDate = draft.dueDate,
            dueAt = draft.dueAt,
        )
        ensureProjectAvailable(draft.projectId)
        val now = clock.now()
        val task = Task(
            id = idGenerator(),
            title = input.title,
            notes = input.notes,
            projectId = draft.projectId,
            priority = draft.priority,
            dueDate = draft.dueDate,
            dueAt = draft.dueAt,
            createdAt = now,
            updatedAt = now,
            revision = 0,
        )
        local.applyCreate(
            task = task,
            operationId = idGenerator(),
            enqueuedAt = now,
        )
        refreshStatus()
        return task
    }

    override suspend fun update(
        taskId: String,
        edit: TaskEdit,
    ): Task {
        val current = local.findTask(taskId)?.task
            ?: throw CachedTaskNotFoundException(taskId)
        val input = validateAndNormalize(
            title = edit.title,
            notes = edit.notes,
            projectId = edit.projectId,
            dueDate = edit.dueDate,
            dueAt = edit.dueAt,
        )
        ensureProjectAvailable(edit.projectId)
        val updated = current.copy(
            title = input.title,
            notes = input.notes,
            projectId = edit.projectId,
            priority = edit.priority,
            dueDate = edit.dueDate,
            dueAt = edit.dueAt,
            isCompleted = edit.isCompleted,
            updatedAt = clock.now(),
        )
        local.applyUpdate(
            task = updated,
            operationId = idGenerator(),
            enqueuedAt = updated.updatedAt,
        )
        refreshStatus()
        return updated
    }

    override suspend fun toggleCompleted(taskId: String): Task {
        val current = local.findTask(taskId)?.task
            ?: throw CachedTaskNotFoundException(taskId)
        return update(
            taskId = taskId,
            edit = current.toEdit().copy(isCompleted = !current.isCompleted),
        )
    }

    override suspend fun delete(taskId: String) {
        val now = clock.now()
        local.applyDelete(
            taskId = taskId,
            operationId = idGenerator(),
            enqueuedAt = now,
        )
        refreshStatus()
    }

    override suspend fun clearCompleted() {
        tasks.first()
            .asSequence()
            .filter { it.syncState != TaskSyncState.CONFLICT }
            .map(TaskItem::task)
            .filter(Task::isCompleted)
            .map(Task::id)
            .toList()
            .forEach { delete(it) }
    }

    override suspend fun resolveConflict(
        taskId: String,
        resolution: TaskConflictResolution,
    ) {
        val normalizedResolution = if (resolution is TaskConflictResolution.Merge) {
            val input = validateAndNormalize(
                title = resolution.edit.title,
                notes = resolution.edit.notes,
                projectId = resolution.edit.projectId,
                dueDate = resolution.edit.dueDate,
                dueAt = resolution.edit.dueAt,
            )
            ensureProjectAvailable(resolution.edit.projectId)
            resolution.copy(
                edit = resolution.edit.copy(
                    title = input.title,
                    notes = input.notes,
                ),
            )
        } else {
            resolution
        }
        local.resolveConflict(
            taskId = taskId,
            resolution = normalizedResolution,
            operationId = idGenerator(),
            enqueuedAt = clock.now(),
        )
        refreshStatus()
    }

    override suspend fun createProject(draft: TaskProjectDraft): TaskProject {
        val name = validateAndNormalizeProject(draft.name)
        val now = clock.now()
        val project = TaskProject(
            id = idGenerator(),
            name = name,
            color = draft.color,
            createdAt = now,
            updatedAt = now,
            revision = 0,
        )
        projectLocal.applyProjectCreate(
            project = project,
            operationId = idGenerator(),
            enqueuedAt = now,
        )
        refreshStatus()
        return project
    }

    override suspend fun updateProject(
        projectId: String,
        edit: TaskProjectEdit,
    ): TaskProject {
        val current = projectLocal.findProject(projectId)?.project
            ?: throw CachedTaskProjectNotFoundException(projectId)
        val updated = current.copy(
            name = validateAndNormalizeProject(edit.name),
            color = edit.color,
            updatedAt = clock.now(),
        )
        projectLocal.applyProjectUpdate(
            project = updated,
            operationId = idGenerator(),
            enqueuedAt = updated.updatedAt,
        )
        refreshStatus()
        return updated
    }

    override suspend fun deleteProject(projectId: String) {
        val now = clock.now()
        projectLocal.applyProjectDelete(
            projectId = projectId,
            operationId = idGenerator(),
            taskOperationId = idGenerator,
            enqueuedAt = now,
        )
        refreshStatus()
    }

    override suspend fun resolveProjectConflict(
        projectId: String,
        resolution: TaskProjectConflictResolution,
    ) {
        val normalizedResolution = if (resolution is TaskProjectConflictResolution.Merge) {
            resolution.copy(
                edit = resolution.edit.copy(
                    name = validateAndNormalizeProject(resolution.edit.name),
                ),
            )
        } else {
            resolution
        }
        projectLocal.resolveProjectConflict(
            projectId = projectId,
            resolution = normalizedResolution,
            operationId = idGenerator(),
            taskOperationId = idGenerator,
            enqueuedAt = clock.now(),
        )
        refreshStatus()
    }

    override suspend fun sync(): TaskSyncResult = syncMutex.withLock {
        var pushedCount = 0
        try {
            mutableSyncStatus.value = status(TaskSyncPhase.SYNCING)
            while (true) {
                val mutation = projectLocal.nextProjectMutation(
                    deletionsOnly = false,
                ) ?: break
                pushedCount += synchronizeProjectUpsert(mutation)
            }

            projectLocal.replaceRemoteProjectSnapshot(
                remoteProjects = projectRemote.listProjects(),
                taskOperationId = idGenerator,
                changedAt = clock.now(),
            )

            while (true) {
                val mutation = local.nextMutation() ?: break
                val referencedProject = mutation.desired?.projectId
                    ?.let { projectId -> projectLocal.findProject(projectId) }
                if (referencedProject?.syncState == TaskSyncState.CONFLICT) {
                    break
                }
                when (mutation.kind) {
                    TaskMutationKind.CREATE -> {
                        val desired = requireNotNull(mutation.desired)
                        val created = try {
                            remote.create(desired)
                        } catch (_: RemoteTaskConflictException) {
                            null
                        }
                        if (created != null) {
                            val reconciled = if (
                                TaskMerge.sameEditableContent(desired, created)
                            ) {
                                local.acknowledgeMutation(mutation, created)
                            } else {
                                local.rebaseMutation(
                                    mutation = mutation,
                                    remoteBase = created,
                                    mergedTask = desired,
                                ) || local.acknowledgeMutation(mutation, created)
                            }
                            pushedCount += 1
                            if (!reconciled) {
                                remote.delete(
                                    id = created.id,
                                    expectedRevision = created.revision,
                                )
                                pushedCount += 1
                            }
                        } else {
                            val remoteTask = remote.find(mutation.taskId)
                                ?: throw RemoteTaskServerException(
                                    statusCode = 409,
                                    message = "The conflicting task disappeared before it could be read.",
                                )
                            if (TaskMerge.sameEditableContent(desired, remoteTask)) {
                                if (local.acknowledgeMutation(mutation, remoteTask)) {
                                    pushedCount += 1
                                } else if (local.findTask(mutation.taskId) == null) {
                                    remote.delete(
                                        id = remoteTask.id,
                                        expectedRevision = remoteTask.revision,
                                    )
                                    pushedCount += 1
                                }
                            } else {
                                val recorded = local.recordConflict(
                                    mutation = mutation,
                                    conflict = TaskConflict(
                                        taskId = mutation.taskId,
                                        mutationKind = mutation.kind,
                                        base = null,
                                        local = desired,
                                        remote = remoteTask,
                                        conflictingFields = setOf(TaskConflictField.CREATION),
                                        detectedAt = clock.now(),
                                    ),
                                )
                                if (!recorded && local.findTask(mutation.taskId) == null) {
                                    remote.delete(
                                        id = remoteTask.id,
                                        expectedRevision = remoteTask.revision,
                                    )
                                    pushedCount += 1
                                }
                            }
                        }
                    }
                    TaskMutationKind.UPDATE -> {
                        val base = requireNotNull(mutation.base)
                        val desired = requireNotNull(mutation.desired)
                        try {
                            val updated = remote.replace(desired)
                            if (local.acknowledgeMutation(mutation, updated)) {
                                pushedCount += 1
                            }
                        } catch (_: RemoteTaskConflictException) {
                            val remoteTask = remote.find(mutation.taskId)
                            if (remoteTask == null) {
                                recordDeletedRemoteConflict(mutation, base, desired)
                            } else {
                                when (val merge = TaskMerge.merge(base, desired, remoteTask)) {
                                    is TaskMergeResult.Merged -> {
                                        if (TaskMerge.sameEditableContent(merge.task, remoteTask)) {
                                            if (local.acknowledgeMutation(mutation, remoteTask)) {
                                                pushedCount += 1
                                            }
                                        } else {
                                            local.rebaseMutation(
                                                mutation = mutation,
                                                remoteBase = remoteTask,
                                                mergedTask = merge.task,
                                            )
                                        }
                                    }
                                    is TaskMergeResult.Conflict -> {
                                        local.recordConflict(
                                            mutation = mutation,
                                            conflict = TaskConflict(
                                                taskId = mutation.taskId,
                                                mutationKind = mutation.kind,
                                                base = base,
                                                local = desired,
                                                remote = remoteTask,
                                                conflictingFields = merge.fields,
                                                detectedAt = clock.now(),
                                            ),
                                        )
                                    }
                                }
                            }
                        } catch (_: RemoteTaskNotFoundException) {
                            recordDeletedRemoteConflict(mutation, base, desired)
                        }
                    }
                    TaskMutationKind.DELETE -> {
                        val base = requireNotNull(mutation.base)
                        try {
                            remote.delete(
                                id = mutation.taskId,
                                expectedRevision = base.revision,
                            )
                            if (local.acknowledgeDelete(mutation)) {
                                pushedCount += 1
                            }
                        } catch (_: RemoteTaskNotFoundException) {
                            if (local.acknowledgeDelete(mutation)) {
                                pushedCount += 1
                            }
                        } catch (_: RemoteTaskConflictException) {
                            val remoteTask = remote.find(mutation.taskId)
                            if (remoteTask == null) {
                                if (local.acknowledgeDelete(mutation)) {
                                    pushedCount += 1
                                }
                            } else {
                                local.recordConflict(
                                    mutation = mutation,
                                    conflict = TaskConflict(
                                        taskId = mutation.taskId,
                                        mutationKind = mutation.kind,
                                        base = base,
                                        local = null,
                                        remote = remoteTask,
                                        conflictingFields = setOf(TaskConflictField.DELETION),
                                        detectedAt = clock.now(),
                                    ),
                                )
                            }
                        }
                    }
                }
            }

            if (local.nextMutation() == null) {
                while (true) {
                    val mutation = projectLocal.nextProjectMutation(
                        deletionsOnly = true,
                    ) ?: break
                    pushedCount += synchronizeProjectDelete(mutation)
                }
            }

            val remoteProjects = projectRemote.listProjects()
            projectLocal.replaceRemoteProjectSnapshot(
                remoteProjects = remoteProjects,
                taskOperationId = idGenerator,
                changedAt = clock.now(),
            )
            val remoteTasks = remote.list()
            local.replaceRemoteSnapshot(remoteTasks)
            val pulledCount = remoteProjects.size + remoteTasks.size
            val completedAt = clock.now()
            val conflictCount = local.conflictCount() +
                projectLocal.projectConflictCount()
            mutableSyncStatus.value = status(
                phase = TaskSyncPhase.IDLE,
                lastSyncedAt = completedAt,
            )
            TaskSyncResult.Success(
                pushedCount = pushedCount,
                pulledCount = pulledCount,
                conflictCount = conflictCount,
            )
        } catch (cancellation: CancellationException) {
            mutableSyncStatus.value = mutableSyncStatus.value.copy(
                phase = TaskSyncPhase.IDLE,
                lastError = null,
            )
            throw cancellation
        } catch (error: Throwable) {
            val failure = error.toSyncFailure()
            mutableSyncStatus.value = failureStatus(failure)
            TaskSyncResult.Failed(failure)
        }
    }

    override fun close() {
        try {
            projectRemote.close()
        } finally {
            try {
                remote.close()
            } finally {
                local.close()
            }
        }
    }

    private suspend fun synchronizeProjectUpsert(
        mutation: PendingTaskProjectMutation,
    ): Int = when (mutation.kind) {
        TaskMutationKind.CREATE -> {
            val desired = requireNotNull(mutation.desired)
            val created = try {
                projectRemote.createProject(desired)
            } catch (_: RemoteTaskProjectConflictException) {
                null
            }
            if (created != null) {
                val reconciled = if (
                    TaskProjectMerge.sameEditableContent(desired, created)
                ) {
                    projectLocal.acknowledgeProjectMutation(mutation, created)
                } else {
                    projectLocal.rebaseProjectMutation(
                        mutation = mutation,
                        remoteBase = created,
                        mergedProject = desired,
                    ) || projectLocal.acknowledgeProjectMutation(mutation, created)
                }
                if (reconciled) {
                    1
                } else {
                    projectRemote.deleteProject(
                        id = created.id,
                        expectedRevision = created.revision,
                    )
                    2
                }
            } else {
                val remoteProject = projectRemote.findProject(mutation.projectId)
                    ?: throw RemoteTaskServerException(
                        statusCode = 409,
                        message = "The conflicting task project disappeared before it could be read.",
                    )
                if (TaskProjectMerge.sameEditableContent(desired, remoteProject)) {
                    if (
                        projectLocal.acknowledgeProjectMutation(
                            mutation,
                            remoteProject,
                        )
                    ) {
                        1
                    } else if (projectLocal.findProject(mutation.projectId) == null) {
                        projectRemote.deleteProject(
                            id = remoteProject.id,
                            expectedRevision = remoteProject.revision,
                        )
                        1
                    } else {
                        0
                    }
                } else {
                    val recorded = projectLocal.recordProjectConflict(
                        mutation = mutation,
                        conflict = TaskProjectConflict(
                            projectId = mutation.projectId,
                            mutationKind = mutation.kind,
                            base = null,
                            local = desired,
                            remote = remoteProject,
                            conflictingFields = setOf(
                                TaskProjectConflictField.CREATION,
                            ),
                            detectedAt = clock.now(),
                        ),
                    )
                    if (
                        !recorded &&
                        projectLocal.findProject(mutation.projectId) == null
                    ) {
                        projectRemote.deleteProject(
                            id = remoteProject.id,
                            expectedRevision = remoteProject.revision,
                        )
                        1
                    } else {
                        0
                    }
                }
            }
        }
        TaskMutationKind.UPDATE -> {
            val base = requireNotNull(mutation.base)
            val desired = requireNotNull(mutation.desired)
            try {
                val updated = projectRemote.replaceProject(desired)
                if (projectLocal.acknowledgeProjectMutation(mutation, updated)) {
                    1
                } else {
                    0
                }
            } catch (_: RemoteTaskProjectConflictException) {
                val remoteProject = projectRemote.findProject(mutation.projectId)
                if (remoteProject == null) {
                    recordDeletedRemoteProjectConflict(mutation, base, desired)
                } else {
                    when (
                        val merge = TaskProjectMerge.merge(
                            base,
                            desired,
                            remoteProject,
                        )
                    ) {
                        is TaskProjectMergeResult.Merged -> {
                            if (
                                TaskProjectMerge.sameEditableContent(
                                    merge.project,
                                    remoteProject,
                                )
                            ) {
                                projectLocal.acknowledgeProjectMutation(
                                    mutation,
                                    remoteProject,
                                )
                            } else {
                                projectLocal.rebaseProjectMutation(
                                    mutation = mutation,
                                    remoteBase = remoteProject,
                                    mergedProject = merge.project,
                                )
                            }
                        }
                        is TaskProjectMergeResult.Conflict ->
                            projectLocal.recordProjectConflict(
                                mutation = mutation,
                                conflict = TaskProjectConflict(
                                    projectId = mutation.projectId,
                                    mutationKind = mutation.kind,
                                    base = base,
                                    local = desired,
                                    remote = remoteProject,
                                    conflictingFields = merge.fields,
                                    detectedAt = clock.now(),
                                ),
                            )
                    }
                }
                0
            } catch (_: RemoteTaskProjectNotFoundException) {
                recordDeletedRemoteProjectConflict(mutation, base, desired)
                0
            }
        }
        TaskMutationKind.DELETE ->
            error("Project deletions must be synchronized after task references.")
    }

    private suspend fun synchronizeProjectDelete(
        mutation: PendingTaskProjectMutation,
    ): Int {
        check(mutation.kind == TaskMutationKind.DELETE)
        val base = requireNotNull(mutation.base)
        return try {
            projectRemote.deleteProject(
                id = mutation.projectId,
                expectedRevision = base.revision,
            )
            if (projectLocal.acknowledgeProjectDelete(mutation)) 1 else 0
        } catch (_: RemoteTaskProjectNotFoundException) {
            if (projectLocal.acknowledgeProjectDelete(mutation)) 1 else 0
        } catch (_: RemoteTaskProjectConflictException) {
            val remoteProject = projectRemote.findProject(mutation.projectId)
            if (remoteProject == null) {
                if (projectLocal.acknowledgeProjectDelete(mutation)) 1 else 0
            } else {
                projectLocal.recordProjectConflict(
                    mutation = mutation,
                    conflict = TaskProjectConflict(
                        projectId = mutation.projectId,
                        mutationKind = mutation.kind,
                        base = base,
                        local = null,
                        remote = remoteProject,
                        conflictingFields = setOf(
                            TaskProjectConflictField.DELETION,
                        ),
                        detectedAt = clock.now(),
                    ),
                )
                0
            }
        }
    }

    private suspend fun recordDeletedRemoteProjectConflict(
        mutation: PendingTaskProjectMutation,
        base: TaskProject,
        desired: TaskProject,
    ) {
        projectLocal.recordProjectConflict(
            mutation = mutation,
            conflict = TaskProjectConflict(
                projectId = mutation.projectId,
                mutationKind = mutation.kind,
                base = base,
                local = desired,
                remote = null,
                conflictingFields = setOf(TaskProjectConflictField.DELETION),
                detectedAt = clock.now(),
            ),
        )
    }

    private suspend fun recordDeletedRemoteConflict(
        mutation: PendingTaskMutation,
        base: Task,
        desired: Task,
    ) {
        local.recordConflict(
            mutation = mutation,
            conflict = TaskConflict(
                taskId = mutation.taskId,
                mutationKind = mutation.kind,
                base = base,
                local = desired,
                remote = null,
                conflictingFields = setOf(TaskConflictField.DELETION),
                detectedAt = clock.now(),
            ),
        )
    }

    private suspend fun refreshStatus() {
        val current = mutableSyncStatus.value
        mutableSyncStatus.value = status(
            phase = current.phase,
            lastSyncedAt = current.lastSyncedAt,
            failure = current.lastError,
        )
    }

    private suspend fun status(
        phase: TaskSyncPhase,
        lastSyncedAt: Instant? = mutableSyncStatus.value.lastSyncedAt,
        failure: TaskSyncFailure? = null,
    ): TaskSyncStatus = TaskSyncStatus(
        phase = phase,
        pendingCount = local.pendingCount() + projectLocal.pendingProjectCount(),
        conflictCount = local.conflictCount() +
            projectLocal.projectConflictCount(),
        lastSyncedAt = lastSyncedAt,
        lastError = failure,
    )

    private suspend fun failureStatus(failure: TaskSyncFailure): TaskSyncStatus {
        val current = mutableSyncStatus.value
        return current.copy(
            phase = TaskSyncPhase.FAILED,
            pendingCount = localCountOrElse(current.pendingCount) {
                local.pendingCount() + projectLocal.pendingProjectCount()
            },
            conflictCount = localCountOrElse(current.conflictCount) {
                local.conflictCount() + projectLocal.projectConflictCount()
            },
            lastError = failure,
        )
    }

    private suspend fun localCountOrElse(
        fallback: Int,
        count: suspend () -> Int,
    ): Int = try {
        count()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        fallback
    }

    private fun validateAndNormalize(
        title: String,
        notes: String?,
        projectId: String?,
        dueDate: LocalDate?,
        dueAt: Instant?,
    ) = TaskValidator.normalize(title, notes).also {
        val issues = TaskValidator.validate(
            title = title,
            notes = notes,
            projectId = projectId,
            dueDate = dueDate,
            dueAt = dueAt,
        )
        if (issues.isNotEmpty()) {
            throw InvalidTaskInputException(issues)
        }
    }

    private suspend fun ensureProjectAvailable(projectId: String?) {
        if (projectId != null && projectLocal.findProject(projectId) == null) {
            throw CachedTaskProjectNotFoundException(projectId)
        }
    }

    private fun validateAndNormalizeProject(name: String): String {
        val issues = TaskProjectValidator.validate(name)
        if (issues.isNotEmpty()) {
            throw InvalidTaskProjectInputException(issues)
        }
        return TaskProjectValidator.normalizeName(name)
    }

    private fun Throwable.toSyncFailure(): TaskSyncFailure = when (this) {
        is RemoteTaskServerException,
        is RemoteTaskRejectedException,
        is RemoteTaskConflictException,
        is RemoteTaskNotFoundException,
        is RemoteTaskProjectConflictException,
        is RemoteTaskProjectNotFoundException,
        -> TaskSyncFailure(
            kind = TaskSyncFailureKind.SERVER,
            message = message ?: "The task service rejected the synchronization request.",
        )
        is TaskLocalStorageException -> TaskSyncFailure(
            kind = TaskSyncFailureKind.LOCAL,
            message = message ?: "The local task database could not be updated.",
        )
        else -> TaskSyncFailure(
            kind = TaskSyncFailureKind.NETWORK,
            message = message ?: "The task service could not be reached.",
        )
    }
}

internal class TaskLocalStorageException(
    message: String,
    cause: Throwable,
) : RuntimeException(message, cause)

internal class DuplicateCachedTaskException(
    taskId: String,
) : IllegalStateException("Task '$taskId' already exists locally.")

internal class InvalidCachedTaskStateException(
    taskId: String,
) : IllegalStateException("Task '$taskId' is in an invalid local state.")
