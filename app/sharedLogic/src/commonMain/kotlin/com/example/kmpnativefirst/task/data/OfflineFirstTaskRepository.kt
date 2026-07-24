package com.example.kmpnativefirst.task.data

import com.example.kmpnativefirst.task.Task
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
    private val clock: Clock = Clock.System,
    private val idGenerator: () -> String = { Uuid.random().toString() },
) : TaskRepository {
    override val tasks = local.observeTasks()
    override val conflicts = local.observeConflicts()

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
        local.resolveConflict(
            taskId = taskId,
            resolution = resolution,
            operationId = idGenerator(),
            enqueuedAt = clock.now(),
        )
        refreshStatus()
    }

    override suspend fun sync(): TaskSyncResult = syncMutex.withLock {
        var pushedCount = 0
        try {
            mutableSyncStatus.value = status(TaskSyncPhase.SYNCING)
            while (true) {
                val mutation = local.nextMutation() ?: break
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

            val remoteTasks = remote.list()
            val pulledCount = local.replaceRemoteSnapshot(remoteTasks)
            val completedAt = clock.now()
            val conflictCount = local.conflictCount()
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
            remote.close()
        } finally {
            local.close()
        }
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
        pendingCount = local.pendingCount(),
        conflictCount = local.conflictCount(),
        lastSyncedAt = lastSyncedAt,
        lastError = failure,
    )

    private suspend fun failureStatus(failure: TaskSyncFailure): TaskSyncStatus {
        val current = mutableSyncStatus.value
        return current.copy(
            phase = TaskSyncPhase.FAILED,
            pendingCount = localCountOrElse(current.pendingCount) {
                local.pendingCount()
            },
            conflictCount = localCountOrElse(current.conflictCount) {
                local.conflictCount()
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

    private fun Throwable.toSyncFailure(): TaskSyncFailure = when (this) {
        is RemoteTaskServerException,
        is RemoteTaskRejectedException,
        is RemoteTaskConflictException,
        is RemoteTaskNotFoundException,
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
