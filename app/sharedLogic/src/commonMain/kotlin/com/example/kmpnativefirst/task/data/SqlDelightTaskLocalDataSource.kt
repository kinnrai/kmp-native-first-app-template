package com.example.kmpnativefirst.task.data

import app.cash.sqldelight.db.SqlDriver
import com.example.kmpnativefirst.task.Task
import com.example.kmpnativefirst.task.TaskPriority
import com.example.kmpnativefirst.task.data.local.CachedTask
import com.example.kmpnativefirst.task.data.local.TaskConflict as DatabaseTaskConflict
import com.example.kmpnativefirst.task.data.local.TaskOutbox
import com.example.kmpnativefirst.task.data.local.db.TaskDatabase
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.time.Instant

internal fun createTaskDatabase(driver: SqlDriver): TaskDatabase = TaskDatabase(driver)

internal suspend fun createPersistentTaskRepository(
    driver: SqlDriver,
    baseUrl: String,
): TaskRepository = try {
    OfflineFirstTaskRepository(
        local = SqlDelightTaskLocalDataSource(driver),
        remote = KtorTaskRemoteDataSource(baseUrl),
    ).initialize()
} catch (error: Throwable) {
    driver.close()
    throw error
}

internal class SqlDelightTaskLocalDataSource(
    private val driver: SqlDriver,
    private val dispatcher: CoroutineDispatcher = taskDatabaseDispatcher(),
) : TaskLocalDataSource {
    private val database = createTaskDatabase(driver)
    private val queries = database.taskCacheQueries
    private val mutex = Mutex()
    private val taskFlow = MutableStateFlow(loadVisibleTasks())
    private val conflictFlow = MutableStateFlow(loadConflicts())

    override fun observeTasks(): Flow<List<TaskItem>> = taskFlow.asStateFlow()

    override fun observeConflicts(): Flow<List<TaskConflict>> = conflictFlow.asStateFlow()

    override suspend fun findTask(id: String): TaskItem? = read {
        queries.selectTaskById(id)
            .executeAsOneOrNull()
            ?.takeUnless(CachedTask::isDeleted)
            ?.toTaskItem()
    }

    override suspend fun pendingCount(): Int = read {
        queries.countOutbox().executeAsOne().toInt()
    }

    override suspend fun conflictCount(): Int = read {
        queries.countConflicts().executeAsOne().toInt()
    }

    override suspend fun applyCreate(
        task: Task,
        operationId: String,
        enqueuedAt: Instant,
    ) = mutate {
        if (queries.selectTaskById(task.id).executeAsOneOrNull() != null) {
            throw DuplicateCachedTaskException(task.id)
        }
        upsertTask(task, TaskSyncState.PENDING, isDeleted = false)
        upsertMutation(
            PendingTaskMutation(
                operationId = operationId,
                taskId = task.id,
                kind = TaskMutationKind.CREATE,
                base = null,
                desired = task,
                enqueuedAt = enqueuedAt,
            ),
        )
    }

    override suspend fun applyUpdate(
        task: Task,
        operationId: String,
        enqueuedAt: Instant,
    ) = mutate {
        val current = queries.selectTaskById(task.id)
            .executeAsOneOrNull()
            ?.takeUnless(CachedTask::isDeleted)
            ?: throw CachedTaskNotFoundException(task.id)
        if (current.syncState == TaskSyncState.CONFLICT.name) {
            throw UnresolvedTaskConflictException(task.id)
        }
        val currentMutation = mutationForTask(task.id)
        upsertTask(task, TaskSyncState.PENDING, isDeleted = false)
        upsertMutation(
            when (currentMutation?.kind) {
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
                    base = current.toTask(),
                    desired = task,
                    enqueuedAt = enqueuedAt,
                )
            },
        )
    }

    override suspend fun applyDelete(
        taskId: String,
        operationId: String,
        enqueuedAt: Instant,
    ) = mutate {
        val current = queries.selectTaskById(taskId)
            .executeAsOneOrNull()
            ?.takeUnless(CachedTask::isDeleted)
            ?: throw CachedTaskNotFoundException(taskId)
        if (current.syncState == TaskSyncState.CONFLICT.name) {
            throw UnresolvedTaskConflictException(taskId)
        }
        val currentMutation = mutationForTask(taskId)
        if (currentMutation?.kind == TaskMutationKind.CREATE) {
            queries.deleteTaskById(taskId)
            queries.deleteOutboxByTaskId(taskId)
            queries.deleteConflictByTaskId(taskId)
            return@mutate
        }
        upsertTask(
            task = current.toTask(),
            syncState = TaskSyncState.PENDING,
            isDeleted = true,
        )
        upsertMutation(
            PendingTaskMutation(
                operationId = operationId,
                taskId = taskId,
                kind = TaskMutationKind.DELETE,
                base = currentMutation?.base ?: current.toTask(),
                desired = null,
                enqueuedAt = enqueuedAt,
            ),
        )
    }

    override suspend fun nextMutation(): PendingTaskMutation? = read {
        queries.selectNextOutbox().executeAsOneOrNull()?.toMutation()
    }

    override suspend fun acknowledgeMutation(
        mutation: PendingTaskMutation,
        remoteTask: Task,
    ): Boolean = mutateWithResult {
        val current = mutationForTask(mutation.taskId) ?: return@mutateWithResult false
        if (current.operationId == mutation.operationId) {
            queries.deleteOutboxByTaskId(mutation.taskId)
            queries.deleteConflictByTaskId(mutation.taskId)
            upsertTask(remoteTask, TaskSyncState.SYNCED, isDeleted = false)
            return@mutateWithResult true
        }

        val rebasedDesired = current.desired?.copy(
            createdAt = remoteTask.createdAt,
            revision = remoteTask.revision,
        )
        upsertMutation(
            current.copy(
                kind = if (current.kind == TaskMutationKind.DELETE) {
                    TaskMutationKind.DELETE
                } else {
                    TaskMutationKind.UPDATE
                },
                base = remoteTask,
                desired = rebasedDesired,
            ),
        )
        upsertTask(
            task = rebasedDesired ?: remoteTask,
            syncState = TaskSyncState.PENDING,
            isDeleted = current.kind == TaskMutationKind.DELETE,
        )
        true
    }

    override suspend fun acknowledgeDelete(mutation: PendingTaskMutation): Boolean =
        mutateWithResult {
            val current = mutationForTask(mutation.taskId)
                ?: return@mutateWithResult false
            if (current.operationId != mutation.operationId) {
                return@mutateWithResult false
            }
            queries.deleteOutboxByTaskId(mutation.taskId)
            queries.deleteConflictByTaskId(mutation.taskId)
            queries.deleteTaskById(mutation.taskId)
            true
        }

    override suspend fun rebaseMutation(
        mutation: PendingTaskMutation,
        remoteBase: Task,
        mergedTask: Task,
    ): Boolean = mutateWithResult {
        val current = mutationForTask(mutation.taskId)
            ?: return@mutateWithResult false
        if (current.operationId != mutation.operationId) {
            return@mutateWithResult false
        }
        val desired = mergedTask.copy(
            createdAt = remoteBase.createdAt,
            revision = remoteBase.revision,
        )
        upsertMutation(
            current.copy(
                kind = TaskMutationKind.UPDATE,
                base = remoteBase,
                desired = desired,
            ),
        )
        upsertTask(desired, TaskSyncState.PENDING, isDeleted = false)
        true
    }

    override suspend fun recordConflict(
        mutation: PendingTaskMutation,
        conflict: TaskConflict,
    ): Boolean = mutateWithResult {
        val current = mutationForTask(mutation.taskId)
            ?: return@mutateWithResult false
        if (current.operationId != mutation.operationId) {
            return@mutateWithResult false
        }
        queries.deleteOutboxByTaskId(mutation.taskId)
        upsertConflict(conflict)
        val visibleTask = conflict.local ?: conflict.remote
        if (visibleTask == null) {
            queries.deleteTaskById(mutation.taskId)
        } else {
            upsertTask(visibleTask, TaskSyncState.CONFLICT, isDeleted = false)
        }
        true
    }

    override suspend fun replaceRemoteSnapshot(remoteTasks: List<Task>): Int =
        mutateWithResult {
            val remoteById = remoteTasks.associateBy(Task::id)
            val protectedIds = queries.selectAllOutbox()
                .executeAsList()
                .mapTo(mutableSetOf(), TaskOutbox::taskId)
                .apply {
                    addAll(
                        queries.selectConflicts()
                            .executeAsList()
                            .map(DatabaseTaskConflict::taskId),
                    )
                }
            queries.selectAllTasks()
                .executeAsList()
                .asSequence()
                .filter { it.syncState == TaskSyncState.SYNCED.name }
                .map(CachedTask::id)
                .filterNot(remoteById::containsKey)
                .toList()
                .forEach(queries::deleteTaskById)

            remoteById.forEach { (id, task) ->
                if (id !in protectedIds) {
                    upsertTask(task, TaskSyncState.SYNCED, isDeleted = false)
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
        val conflict = queries.selectConflictByTaskId(taskId)
            .executeAsOneOrNull()
            ?.toDomainConflict()
            ?: throw CachedTaskNotFoundException(taskId)
        when (resolution) {
            TaskConflictResolution.UseRemote -> {
                queries.deleteOutboxByTaskId(taskId)
                val remote = conflict.remote
                if (remote == null) {
                    queries.deleteTaskById(taskId)
                } else {
                    upsertTask(remote, TaskSyncState.SYNCED, isDeleted = false)
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
        queries.deleteConflictByTaskId(taskId)
    }

    override fun close() {
        driver.close()
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
                queries.deleteTaskById(conflict.taskId)
                queries.deleteOutboxByTaskId(conflict.taskId)
            }
            remote == null -> {
                val create = requireNotNull(desired).copy(revision = 0)
                upsertTask(create, TaskSyncState.PENDING, isDeleted = false)
                upsertMutation(
                    PendingTaskMutation(
                        operationId,
                        conflict.taskId,
                        TaskMutationKind.CREATE,
                        base = null,
                        desired = create,
                        enqueuedAt,
                    ),
                )
            }
            desired == null -> {
                upsertTask(remote, TaskSyncState.PENDING, isDeleted = true)
                upsertMutation(
                    PendingTaskMutation(
                        operationId,
                        conflict.taskId,
                        TaskMutationKind.DELETE,
                        base = remote,
                        desired = null,
                        enqueuedAt,
                    ),
                )
            }
            else -> {
                val update = desired.copy(
                    createdAt = remote.createdAt,
                    revision = remote.revision,
                )
                upsertTask(update, TaskSyncState.PENDING, isDeleted = false)
                upsertMutation(
                    PendingTaskMutation(
                        operationId,
                        conflict.taskId,
                        TaskMutationKind.UPDATE,
                        base = remote,
                        desired = update,
                        enqueuedAt,
                    ),
                )
            }
        }
    }

    private suspend fun <T> read(block: () -> T): T = withContext(dispatcher) {
        mutex.withLock {
            storageCall(block)
        }
    }

    private suspend fun mutate(block: () -> Unit) {
        mutateWithResult(block)
    }

    private suspend fun <T> mutateWithResult(block: () -> T): T = withContext(dispatcher) {
        mutex.withLock {
            storageCall {
                database.transactionWithResult(bodyWithReturn = { block() }).also {
                    publish()
                }
            }
        }
    }

    private fun publish() {
        taskFlow.value = loadVisibleTasks()
        conflictFlow.value = loadConflicts()
    }

    private fun loadVisibleTasks(): List<TaskItem> =
        queries.selectVisibleTasks()
            .executeAsList()
            .map(CachedTask::toTaskItem)

    private fun loadConflicts(): List<TaskConflict> =
        queries.selectConflicts()
            .executeAsList()
            .map(DatabaseTaskConflict::toDomainConflict)

    private fun mutationForTask(taskId: String): PendingTaskMutation? =
        queries.selectOutboxByTaskId(taskId)
            .executeAsOneOrNull()
            ?.toMutation()

    private fun upsertTask(
        task: Task,
        syncState: TaskSyncState,
        isDeleted: Boolean,
    ) {
        queries.upsertTask(
            id = task.id,
            title = task.title,
            notes = task.notes,
            projectId = task.projectId,
            priority = task.priority.name,
            dueDate = task.dueDate?.toString(),
            dueAtEpochMillis = task.dueAt?.toEpochMilliseconds(),
            isCompleted = task.isCompleted,
            createdAtEpochMillis = task.createdAt.toEpochMilliseconds(),
            updatedAtEpochMillis = task.updatedAt.toEpochMilliseconds(),
            revision = task.revision,
            syncState = syncState.name,
            isDeleted = isDeleted,
        )
    }

    private fun upsertMutation(mutation: PendingTaskMutation) {
        queries.upsertOutbox(
            operationId = mutation.operationId,
            taskId = mutation.taskId,
            kind = mutation.kind.name,
            baseJson = mutation.base?.let(taskJson::encodeToString),
            desiredJson = mutation.desired?.let(taskJson::encodeToString),
            enqueuedAtEpochMillis = mutation.enqueuedAt.toEpochMilliseconds(),
        )
    }

    private fun upsertConflict(conflict: TaskConflict) {
        queries.upsertConflict(
            taskId = conflict.taskId,
            kind = conflict.mutationKind.name,
            baseJson = conflict.base?.let(taskJson::encodeToString),
            localJson = conflict.local?.let(taskJson::encodeToString),
            remoteJson = conflict.remote?.let(taskJson::encodeToString),
            fields = conflict.conflictingFields
                .sortedBy(TaskConflictField::ordinal)
                .joinToString(separator = ",", transform = TaskConflictField::name),
            detectedAtEpochMillis = conflict.detectedAt.toEpochMilliseconds(),
        )
    }

    private fun <T> storageCall(block: () -> T): T = try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: CachedTaskNotFoundException) {
        throw error
    } catch (error: UnresolvedTaskConflictException) {
        throw error
    } catch (error: DuplicateCachedTaskException) {
        throw error
    } catch (error: InvalidCachedTaskStateException) {
        throw error
    } catch (error: Throwable) {
        throw TaskLocalStorageException(
            message = "The local task database operation failed.",
            cause = error,
        )
    }
}

private fun CachedTask.toTask(): Task = Task(
    id = id,
    title = title,
    notes = notes,
    projectId = projectId,
    priority = TaskPriority.valueOf(priority),
    dueDate = dueDate?.let(LocalDate::parse),
    dueAt = dueAtEpochMillis?.let(Instant::fromEpochMilliseconds),
    isCompleted = isCompleted,
    createdAt = Instant.fromEpochMilliseconds(createdAtEpochMillis),
    updatedAt = Instant.fromEpochMilliseconds(updatedAtEpochMillis),
    revision = revision,
)

private fun CachedTask.toTaskItem(): TaskItem = TaskItem(
    task = toTask(),
    syncState = TaskSyncState.valueOf(syncState),
)

private fun TaskOutbox.toMutation(): PendingTaskMutation = PendingTaskMutation(
    operationId = operationId,
    taskId = taskId,
    kind = TaskMutationKind.valueOf(kind),
    base = baseJson?.let(taskJson::decodeFromString),
    desired = desiredJson?.let(taskJson::decodeFromString),
    enqueuedAt = Instant.fromEpochMilliseconds(enqueuedAtEpochMillis),
)

private fun DatabaseTaskConflict.toDomainConflict(): TaskConflict = TaskConflict(
    taskId = taskId,
    mutationKind = TaskMutationKind.valueOf(kind),
    base = baseJson?.let(taskJson::decodeFromString),
    local = localJson?.let(taskJson::decodeFromString),
    remote = remoteJson?.let(taskJson::decodeFromString),
    conflictingFields = fields
        .split(',')
        .filter(String::isNotBlank)
        .mapTo(linkedSetOf(), TaskConflictField::valueOf),
    detectedAt = Instant.fromEpochMilliseconds(detectedAtEpochMillis),
)

private fun Task.withEdit(edit: TaskEdit): Task = copy(
    title = edit.title,
    notes = edit.notes,
    projectId = edit.projectId,
    priority = edit.priority,
    dueDate = edit.dueDate,
    dueAt = edit.dueAt,
    isCompleted = edit.isCompleted,
)
