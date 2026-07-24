package com.example.kmpnativefirst.task.data

import com.example.kmpnativefirst.task.Task
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Instant

internal class InMemoryTaskLocalDataSource(
    initialTasks: List<Task> = emptyList(),
    restoredState: TaskLocalState? = null,
    private val persistState: suspend (TaskLocalState) -> Unit = {},
    private val closeState: () -> Unit = {},
) : TaskLocalDataSource {
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
    private val taskFlow = MutableStateFlow(visibleTasks())
    private val conflictFlow = MutableStateFlow(emptyList<TaskConflict>())

    override fun observeTasks(): Flow<List<TaskItem>> = taskFlow.asStateFlow()

    override fun observeConflicts(): Flow<List<TaskConflict>> = conflictFlow.asStateFlow()

    override suspend fun findTask(id: String): TaskItem? = mutex.withLock {
        records[id]
            ?.takeUnless(LocalTaskRecord::isDeleted)
            ?.toTaskItem()
    }

    override suspend fun pendingCount(): Int = mutex.withLock { outbox.size }

    override suspend fun conflictCount(): Int = mutex.withLock { conflicts.size }

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
        val current = records[task.id]
            ?.takeUnless(LocalTaskRecord::isDeleted)
            ?: throw CachedTaskNotFoundException(task.id)
        if (current.syncState == TaskSyncState.CONFLICT) {
            throw UnresolvedTaskConflictException(task.id)
        }
        val currentMutation = outbox[task.id]
        records[task.id] = LocalTaskRecord(task, TaskSyncState.PENDING, isDeleted = false)
        outbox[task.id] = when (currentMutation?.kind) {
            TaskMutationKind.CREATE -> currentMutation.copy(
                operationId = operationId,
                desired = task,
                enqueuedAt = enqueuedAt,
            )
            TaskMutationKind.UPDATE -> currentMutation.copy(
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

    override fun close() {
        closeState()
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
    )

    private fun restore(state: TaskLocalState) {
        records.clear()
        records.putAll(state.records)
        outbox.clear()
        outbox.putAll(state.outbox)
        conflicts.clear()
        conflicts.putAll(state.conflicts)
    }

    private fun publish() {
        taskFlow.value = visibleTasks()
        conflictFlow.value = conflicts.values.sortedByDescending(TaskConflict::detectedAt)
    }

    private fun visibleTasks(): List<TaskItem> = records.values
        .asSequence()
        .filterNot(LocalTaskRecord::isDeleted)
        .map(LocalTaskRecord::toTaskItem)
        .sortedWith(taskItemComparator)
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
internal data class TaskLocalState(
    val records: Map<String, LocalTaskRecord> = emptyMap(),
    val outbox: Map<String, PendingTaskMutation> = emptyMap(),
    val conflicts: Map<String, TaskConflict> = emptyMap(),
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

private val taskItemComparator = compareBy<TaskItem>(
    { it.task.isCompleted },
    { it.task.dueDate == null && it.task.dueAt == null },
    { it.task.dueDate },
    { it.task.dueAt },
).thenByDescending { it.task.updatedAt }
