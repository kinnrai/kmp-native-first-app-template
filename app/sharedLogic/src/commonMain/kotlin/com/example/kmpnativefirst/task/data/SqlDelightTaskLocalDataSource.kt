package com.example.kmpnativefirst.task.data

import app.cash.sqldelight.db.SqlDriver
import com.example.kmpnativefirst.task.Task
import com.example.kmpnativefirst.task.TaskLabel
import com.example.kmpnativefirst.task.TaskLabelColor
import com.example.kmpnativefirst.task.TaskPriority
import com.example.kmpnativefirst.task.TaskProject
import com.example.kmpnativefirst.task.TaskProjectColor
import com.example.kmpnativefirst.task.data.local.CachedTask
import com.example.kmpnativefirst.task.data.local.CachedTaskLabel
import com.example.kmpnativefirst.task.data.local.CachedTaskProject
import com.example.kmpnativefirst.task.data.local.TaskConflict as DatabaseTaskConflict
import com.example.kmpnativefirst.task.data.local.TaskOutbox
import com.example.kmpnativefirst.task.data.local.TaskLabelConflict as DatabaseTaskLabelConflict
import com.example.kmpnativefirst.task.data.local.TaskLabelOutbox
import com.example.kmpnativefirst.task.data.local.TaskProjectConflict as DatabaseTaskProjectConflict
import com.example.kmpnativefirst.task.data.local.TaskProjectOutbox
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
    val local = SqlDelightTaskLocalDataSource(driver)
    OfflineFirstTaskRepository(
        local = local,
        remote = KtorTaskRemoteDataSource(baseUrl),
        projectLocal = local,
        projectRemote = KtorTaskProjectRemoteDataSource(baseUrl),
        labelLocal = local,
        labelRemote = KtorTaskLabelRemoteDataSource(baseUrl),
    ).initialize()
} catch (error: Throwable) {
    driver.close()
    throw error
}

internal class SqlDelightTaskLocalDataSource(
    private val driver: SqlDriver,
    private val dispatcher: CoroutineDispatcher = taskDatabaseDispatcher(),
) : TaskLocalDataSource, TaskProjectLocalDataSource, TaskLabelLocalDataSource {
    private val database = createTaskDatabase(driver)
    private val queries = database.taskCacheQueries
    private val mutex = Mutex()
    private val taskFlow = MutableStateFlow(loadVisibleTasks())
    private val conflictFlow = MutableStateFlow(loadConflicts())
    private val projectFlow = MutableStateFlow(loadVisibleProjects())
    private val projectConflictFlow = MutableStateFlow(loadProjectConflicts())
    private val labelFlow = MutableStateFlow(loadVisibleLabels())
    private val labelConflictFlow = MutableStateFlow(loadLabelConflicts())

    override fun observeTasks(): Flow<List<TaskItem>> = taskFlow.asStateFlow()

    override fun observeConflicts(): Flow<List<TaskConflict>> = conflictFlow.asStateFlow()

    override fun observeProjects(): Flow<List<TaskProjectItem>> = projectFlow.asStateFlow()

    override fun observeProjectConflicts(): Flow<List<TaskProjectConflict>> =
        projectConflictFlow.asStateFlow()

    override fun observeLabels(): Flow<List<TaskLabelItem>> = labelFlow.asStateFlow()

    override fun observeLabelConflicts(): Flow<List<TaskLabelConflict>> =
        labelConflictFlow.asStateFlow()

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

    override suspend fun findProject(id: String): TaskProjectItem? = read {
        queries.selectProjectById(id)
            .executeAsOneOrNull()
            ?.takeUnless(CachedTaskProject::isDeleted)
            ?.toProjectItem()
    }

    override suspend fun pendingProjectCount(): Int = read {
        queries.countProjectOutbox().executeAsOne().toInt()
    }

    override suspend fun projectConflictCount(): Int = read {
        queries.countProjectConflicts().executeAsOne().toInt()
    }

    override suspend fun findLabel(id: String): TaskLabelItem? = read {
        queries.selectLabelById(id)
            .executeAsOneOrNull()
            ?.takeUnless(CachedTaskLabel::isDeleted)
            ?.toLabelItem()
    }

    override suspend fun pendingLabelCount(): Int = read {
        queries.countLabelOutbox().executeAsOne().toInt()
    }

    override suspend fun labelConflictCount(): Int = read {
        queries.countLabelConflicts().executeAsOne().toInt()
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
        applyTaskUpdateLocked(task, operationId, enqueuedAt)
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

    override suspend fun applyProjectCreate(
        project: TaskProject,
        operationId: String,
        enqueuedAt: Instant,
    ) = mutate {
        if (queries.selectProjectById(project.id).executeAsOneOrNull() != null) {
            throw DuplicateCachedTaskProjectException(project.id)
        }
        upsertProject(project, TaskSyncState.PENDING, isDeleted = false)
        upsertProjectMutation(
            PendingTaskProjectMutation(
                operationId = operationId,
                projectId = project.id,
                kind = TaskMutationKind.CREATE,
                base = null,
                desired = project,
                enqueuedAt = enqueuedAt,
            ),
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
        val current = queries.selectProjectById(projectId)
            .executeAsOneOrNull()
            ?.takeUnless(CachedTaskProject::isDeleted)
            ?: throw CachedTaskProjectNotFoundException(projectId)
        if (current.syncState == TaskSyncState.CONFLICT.name) {
            throw UnresolvedTaskProjectConflictException(projectId)
        }
        queries.selectConflicts()
            .executeAsList()
            .map(DatabaseTaskConflict::toDomainConflict)
            .firstOrNull { conflict -> conflict.referencesProject(projectId) }
            ?.let { conflict -> throw UnresolvedTaskConflictException(conflict.taskId) }
        clearProjectReferences(
            projectIds = setOf(projectId),
            taskOperationId = taskOperationId,
            changedAt = enqueuedAt,
            enqueueSyncedTasks = true,
        )
        val currentMutation = mutationForProject(projectId)
        if (currentMutation?.kind == TaskMutationKind.CREATE) {
            queries.deleteProjectById(projectId)
            queries.deleteProjectOutboxByProjectId(projectId)
            queries.deleteProjectConflictByProjectId(projectId)
            return@mutate
        }
        upsertProject(
            project = current.toProject(),
            syncState = TaskSyncState.PENDING,
            isDeleted = true,
        )
        upsertProjectMutation(
            PendingTaskProjectMutation(
                operationId = operationId,
                projectId = projectId,
                kind = TaskMutationKind.DELETE,
                base = currentMutation?.base ?: current.toProject(),
                desired = null,
                enqueuedAt = enqueuedAt,
            ),
        )
    }

    override suspend fun nextProjectMutation(
        deletionsOnly: Boolean,
    ): PendingTaskProjectMutation? = read {
        if (deletionsOnly) {
            queries.selectNextProjectDeleteOutbox().executeAsOneOrNull()
        } else {
            queries.selectNextProjectUpsertOutbox().executeAsOneOrNull()
        }?.toProjectMutation()
    }

    override suspend fun acknowledgeProjectMutation(
        mutation: PendingTaskProjectMutation,
        remoteProject: TaskProject,
    ): Boolean = mutateWithResult {
        val current = mutationForProject(mutation.projectId)
            ?: return@mutateWithResult false
        if (current.operationId == mutation.operationId) {
            queries.deleteProjectOutboxByProjectId(mutation.projectId)
            queries.deleteProjectConflictByProjectId(mutation.projectId)
            upsertProject(remoteProject, TaskSyncState.SYNCED, isDeleted = false)
            return@mutateWithResult true
        }

        val rebasedDesired = current.desired?.copy(
            createdAt = remoteProject.createdAt,
            revision = remoteProject.revision,
        )
        upsertProjectMutation(
            current.copy(
                kind = if (current.kind == TaskMutationKind.DELETE) {
                    TaskMutationKind.DELETE
                } else {
                    TaskMutationKind.UPDATE
                },
                base = remoteProject,
                desired = rebasedDesired,
            ),
        )
        upsertProject(
            project = rebasedDesired ?: remoteProject,
            syncState = TaskSyncState.PENDING,
            isDeleted = current.kind == TaskMutationKind.DELETE,
        )
        true
    }

    override suspend fun acknowledgeProjectDelete(
        mutation: PendingTaskProjectMutation,
    ): Boolean = mutateWithResult {
        val current = mutationForProject(mutation.projectId)
            ?: return@mutateWithResult false
        if (current.operationId != mutation.operationId) {
            return@mutateWithResult false
        }
        queries.deleteProjectOutboxByProjectId(mutation.projectId)
        queries.deleteProjectConflictByProjectId(mutation.projectId)
        queries.deleteProjectById(mutation.projectId)
        true
    }

    override suspend fun rebaseProjectMutation(
        mutation: PendingTaskProjectMutation,
        remoteBase: TaskProject,
        mergedProject: TaskProject,
    ): Boolean = mutateWithResult {
        val current = mutationForProject(mutation.projectId)
            ?: return@mutateWithResult false
        if (current.operationId != mutation.operationId) {
            return@mutateWithResult false
        }
        val desired = mergedProject.copy(
            createdAt = remoteBase.createdAt,
            revision = remoteBase.revision,
        )
        upsertProjectMutation(
            current.copy(
                kind = TaskMutationKind.UPDATE,
                base = remoteBase,
                desired = desired,
            ),
        )
        upsertProject(desired, TaskSyncState.PENDING, isDeleted = false)
        true
    }

    override suspend fun recordProjectConflict(
        mutation: PendingTaskProjectMutation,
        conflict: TaskProjectConflict,
    ): Boolean = mutateWithResult {
        val current = mutationForProject(mutation.projectId)
            ?: return@mutateWithResult false
        if (current.operationId != mutation.operationId) {
            return@mutateWithResult false
        }
        queries.deleteProjectOutboxByProjectId(mutation.projectId)
        upsertProjectConflict(conflict)
        val visibleProject = conflict.local ?: conflict.remote
        if (visibleProject == null) {
            queries.deleteProjectById(mutation.projectId)
        } else {
            upsertProject(visibleProject, TaskSyncState.CONFLICT, isDeleted = false)
        }
        true
    }

    override suspend fun replaceRemoteProjectSnapshot(
        remoteProjects: List<TaskProject>,
        taskOperationId: () -> String,
        changedAt: Instant,
    ): Int = mutateWithResult {
        val remoteById = remoteProjects.associateBy(TaskProject::id)
        val conflictReferencedIds = queries.selectConflicts()
            .executeAsList()
            .map(DatabaseTaskConflict::toDomainConflict)
            .flatMapTo(mutableSetOf()) { conflict -> conflict.referencedProjectIds() }
        val mutationProtectedIds = queries.selectAllProjectOutbox()
            .executeAsList()
            .mapTo(mutableSetOf(), TaskProjectOutbox::projectId)
            .apply {
                addAll(
                    queries.selectProjectConflicts()
                        .executeAsList()
                        .map(DatabaseTaskProjectConflict::projectId),
                )
            }
        val removalProtectedIds = mutationProtectedIds + conflictReferencedIds
        val removableIds = queries.selectAllProjects()
            .executeAsList()
            .asSequence()
            .filter { project -> project.syncState == TaskSyncState.SYNCED.name }
            .map(CachedTaskProject::id)
            .filterNot(remoteById::containsKey)
            .filterNot(removalProtectedIds::contains)
            .toSet()
        clearProjectReferences(
            projectIds = removableIds,
            taskOperationId = taskOperationId,
            changedAt = changedAt,
            enqueueSyncedTasks = false,
        )
        removableIds.forEach(queries::deleteProjectById)

        remoteById.forEach { (id, project) ->
            if (id !in mutationProtectedIds) {
                upsertProject(project, TaskSyncState.SYNCED, isDeleted = false)
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
        val conflict = queries.selectProjectConflictByProjectId(projectId)
            .executeAsOneOrNull()
            ?.toDomainProjectConflict()
            ?: throw CachedTaskProjectNotFoundException(projectId)
        when (resolution) {
            TaskProjectConflictResolution.UseRemote -> {
                queries.deleteProjectOutboxByProjectId(projectId)
                val remote = conflict.remote
                if (remote == null) {
                    clearProjectReferences(
                        projectIds = setOf(projectId),
                        taskOperationId = taskOperationId,
                        changedAt = enqueuedAt,
                        enqueueSyncedTasks = true,
                    )
                    queries.deleteProjectById(projectId)
                } else {
                    upsertProject(remote, TaskSyncState.SYNCED, isDeleted = false)
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
        queries.deleteProjectConflictByProjectId(projectId)
    }

    override suspend fun applyLabelCreate(
        label: TaskLabel,
        operationId: String,
        enqueuedAt: Instant,
    ) = mutate {
        if (queries.selectLabelById(label.id).executeAsOneOrNull() != null) {
            throw DuplicateCachedTaskLabelException(label.id)
        }
        upsertLabel(label, TaskSyncState.PENDING, isDeleted = false)
        upsertLabelMutation(
            PendingTaskLabelMutation(
                operationId = operationId,
                labelId = label.id,
                kind = TaskMutationKind.CREATE,
                base = null,
                desired = label,
                enqueuedAt = enqueuedAt,
            ),
        )
    }

    override suspend fun applyLabelUpdate(
        label: TaskLabel,
        operationId: String,
        enqueuedAt: Instant,
    ) = mutate {
        applyLabelUpdateLocked(label, operationId, enqueuedAt)
    }

    override suspend fun applyLabelDelete(
        labelId: String,
        operationId: String,
        taskOperationId: () -> String,
        enqueuedAt: Instant,
    ) = mutate {
        val current = queries.selectLabelById(labelId)
            .executeAsOneOrNull()
            ?.takeUnless(CachedTaskLabel::isDeleted)
            ?: throw CachedTaskLabelNotFoundException(labelId)
        if (current.syncState == TaskSyncState.CONFLICT.name) {
            throw UnresolvedTaskLabelConflictException(labelId)
        }
        queries.selectConflicts()
            .executeAsList()
            .map(DatabaseTaskConflict::toDomainConflict)
            .firstOrNull { conflict -> conflict.referencesLabel(labelId) }
            ?.let { conflict -> throw UnresolvedTaskConflictException(conflict.taskId) }
        clearLabelReferences(
            labelIds = setOf(labelId),
            taskOperationId = taskOperationId,
            changedAt = enqueuedAt,
            enqueueSyncedTasks = true,
        )
        val currentMutation = mutationForLabel(labelId)
        if (currentMutation?.kind == TaskMutationKind.CREATE) {
            queries.deleteLabelById(labelId)
            queries.deleteLabelOutboxByLabelId(labelId)
            queries.deleteLabelConflictByLabelId(labelId)
            return@mutate
        }
        upsertLabel(
            label = current.toLabel(),
            syncState = TaskSyncState.PENDING,
            isDeleted = true,
        )
        upsertLabelMutation(
            PendingTaskLabelMutation(
                operationId = operationId,
                labelId = labelId,
                kind = TaskMutationKind.DELETE,
                base = currentMutation?.base ?: current.toLabel(),
                desired = null,
                enqueuedAt = enqueuedAt,
            ),
        )
    }

    override suspend fun nextLabelMutation(
        deletionsOnly: Boolean,
    ): PendingTaskLabelMutation? = read {
        if (deletionsOnly) {
            queries.selectNextLabelDeleteOutbox().executeAsOneOrNull()
        } else {
            queries.selectNextLabelUpsertOutbox().executeAsOneOrNull()
        }?.toLabelMutation()
    }

    override suspend fun acknowledgeLabelMutation(
        mutation: PendingTaskLabelMutation,
        remoteLabel: TaskLabel,
    ): Boolean = mutateWithResult {
        val current = mutationForLabel(mutation.labelId)
            ?: return@mutateWithResult false
        if (current.operationId == mutation.operationId) {
            queries.deleteLabelOutboxByLabelId(mutation.labelId)
            queries.deleteLabelConflictByLabelId(mutation.labelId)
            upsertLabel(remoteLabel, TaskSyncState.SYNCED, isDeleted = false)
            return@mutateWithResult true
        }

        val rebasedDesired = current.desired?.copy(
            createdAt = remoteLabel.createdAt,
            revision = remoteLabel.revision,
        )
        upsertLabelMutation(
            current.copy(
                kind = if (current.kind == TaskMutationKind.DELETE) {
                    TaskMutationKind.DELETE
                } else {
                    TaskMutationKind.UPDATE
                },
                base = remoteLabel,
                desired = rebasedDesired,
            ),
        )
        upsertLabel(
            label = rebasedDesired ?: remoteLabel,
            syncState = TaskSyncState.PENDING,
            isDeleted = current.kind == TaskMutationKind.DELETE,
        )
        true
    }

    override suspend fun acknowledgeLabelDelete(
        mutation: PendingTaskLabelMutation,
    ): Boolean = mutateWithResult {
        val current = mutationForLabel(mutation.labelId)
            ?: return@mutateWithResult false
        if (current.operationId != mutation.operationId) {
            return@mutateWithResult false
        }
        queries.deleteLabelOutboxByLabelId(mutation.labelId)
        queries.deleteLabelConflictByLabelId(mutation.labelId)
        queries.deleteLabelById(mutation.labelId)
        true
    }

    override suspend fun rebaseLabelMutation(
        mutation: PendingTaskLabelMutation,
        remoteBase: TaskLabel,
        mergedLabel: TaskLabel,
    ): Boolean = mutateWithResult {
        val current = mutationForLabel(mutation.labelId)
            ?: return@mutateWithResult false
        if (current.operationId != mutation.operationId) {
            return@mutateWithResult false
        }
        val desired = mergedLabel.copy(
            createdAt = remoteBase.createdAt,
            revision = remoteBase.revision,
        )
        upsertLabelMutation(
            current.copy(
                kind = TaskMutationKind.UPDATE,
                base = remoteBase,
                desired = desired,
            ),
        )
        upsertLabel(desired, TaskSyncState.PENDING, isDeleted = false)
        true
    }

    override suspend fun recordLabelConflict(
        mutation: PendingTaskLabelMutation,
        conflict: TaskLabelConflict,
    ): Boolean = mutateWithResult {
        val current = mutationForLabel(mutation.labelId)
            ?: return@mutateWithResult false
        if (current.operationId != mutation.operationId) {
            return@mutateWithResult false
        }
        queries.deleteLabelOutboxByLabelId(mutation.labelId)
        upsertLabelConflict(conflict)
        val visibleLabel = conflict.local ?: conflict.remote
        if (visibleLabel == null) {
            queries.deleteLabelById(mutation.labelId)
        } else {
            upsertLabel(visibleLabel, TaskSyncState.CONFLICT, isDeleted = false)
        }
        true
    }

    override suspend fun replaceRemoteLabelSnapshot(
        remoteLabels: List<TaskLabel>,
        taskOperationId: () -> String,
        changedAt: Instant,
    ): Int = mutateWithResult {
        val remoteById = remoteLabels.associateBy(TaskLabel::id)
        val conflictReferencedIds = queries.selectConflicts()
            .executeAsList()
            .map(DatabaseTaskConflict::toDomainConflict)
            .flatMapTo(mutableSetOf()) { conflict -> conflict.referencedLabelIds() }
        val mutationProtectedIds = queries.selectAllLabelOutbox()
            .executeAsList()
            .mapTo(mutableSetOf(), TaskLabelOutbox::labelId)
            .apply {
                addAll(
                    queries.selectLabelConflicts()
                        .executeAsList()
                        .map(DatabaseTaskLabelConflict::labelId),
                )
            }
        val removalProtectedIds = mutationProtectedIds + conflictReferencedIds
        val removableIds = queries.selectAllLabels()
            .executeAsList()
            .asSequence()
            .filter { label -> label.syncState == TaskSyncState.SYNCED.name }
            .map(CachedTaskLabel::id)
            .filterNot(remoteById::containsKey)
            .filterNot(removalProtectedIds::contains)
            .toSet()
        clearLabelReferences(
            labelIds = removableIds,
            taskOperationId = taskOperationId,
            changedAt = changedAt,
            enqueueSyncedTasks = false,
        )
        removableIds.forEach(queries::deleteLabelById)

        remoteById.forEach { (id, label) ->
            if (id !in mutationProtectedIds) {
                upsertLabel(label, TaskSyncState.SYNCED, isDeleted = false)
            }
        }
        remoteLabels.size
    }

    override suspend fun resolveLabelConflict(
        labelId: String,
        resolution: TaskLabelConflictResolution,
        operationId: String,
        taskOperationId: () -> String,
        enqueuedAt: Instant,
    ) = mutate {
        val conflict = queries.selectLabelConflictByLabelId(labelId)
            .executeAsOneOrNull()
            ?.toDomainLabelConflict()
            ?: throw CachedTaskLabelNotFoundException(labelId)
        when (resolution) {
            TaskLabelConflictResolution.UseRemote -> {
                queries.deleteLabelOutboxByLabelId(labelId)
                val remote = conflict.remote
                if (remote == null) {
                    clearLabelReferences(
                        labelIds = setOf(labelId),
                        taskOperationId = taskOperationId,
                        changedAt = enqueuedAt,
                        enqueueSyncedTasks = true,
                    )
                    queries.deleteLabelById(labelId)
                } else {
                    upsertLabel(remote, TaskSyncState.SYNCED, isDeleted = false)
                }
            }
            TaskLabelConflictResolution.KeepLocal -> enqueueLabelResolution(
                conflict = conflict,
                desired = conflict.local,
                operationId = operationId,
                enqueuedAt = enqueuedAt,
            )
            is TaskLabelConflictResolution.Merge -> {
                val source = conflict.local ?: conflict.remote
                    ?: throw CachedTaskLabelNotFoundException(labelId)
                enqueueLabelResolution(
                    conflict = conflict,
                    desired = source.withEdit(resolution.edit),
                    operationId = operationId,
                    enqueuedAt = enqueuedAt,
                )
            }
        }
        queries.deleteLabelConflictByLabelId(labelId)
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

    private fun enqueueProjectResolution(
        conflict: TaskProjectConflict,
        desired: TaskProject?,
        operationId: String,
        enqueuedAt: Instant,
    ) {
        val remote = conflict.remote
        when {
            remote == null && desired == null -> {
                queries.deleteProjectById(conflict.projectId)
                queries.deleteProjectOutboxByProjectId(conflict.projectId)
            }
            remote == null -> {
                val create = requireNotNull(desired).copy(revision = 0)
                upsertProject(create, TaskSyncState.PENDING, isDeleted = false)
                upsertProjectMutation(
                    PendingTaskProjectMutation(
                        operationId,
                        conflict.projectId,
                        TaskMutationKind.CREATE,
                        base = null,
                        desired = create,
                        enqueuedAt,
                    ),
                )
            }
            desired == null -> {
                upsertProject(remote, TaskSyncState.PENDING, isDeleted = true)
                upsertProjectMutation(
                    PendingTaskProjectMutation(
                        operationId,
                        conflict.projectId,
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
                upsertProject(update, TaskSyncState.PENDING, isDeleted = false)
                upsertProjectMutation(
                    PendingTaskProjectMutation(
                        operationId,
                        conflict.projectId,
                        TaskMutationKind.UPDATE,
                        base = remote,
                        desired = update,
                        enqueuedAt,
                    ),
                )
            }
        }
    }

    private fun enqueueLabelResolution(
        conflict: TaskLabelConflict,
        desired: TaskLabel?,
        operationId: String,
        enqueuedAt: Instant,
    ) {
        val remote = conflict.remote
        when {
            remote == null && desired == null -> {
                queries.deleteLabelById(conflict.labelId)
                queries.deleteLabelOutboxByLabelId(conflict.labelId)
            }
            remote == null -> {
                val create = requireNotNull(desired).copy(revision = 0)
                upsertLabel(create, TaskSyncState.PENDING, isDeleted = false)
                upsertLabelMutation(
                    PendingTaskLabelMutation(
                        operationId,
                        conflict.labelId,
                        TaskMutationKind.CREATE,
                        base = null,
                        desired = create,
                        enqueuedAt,
                    ),
                )
            }
            desired == null -> {
                upsertLabel(remote, TaskSyncState.PENDING, isDeleted = true)
                upsertLabelMutation(
                    PendingTaskLabelMutation(
                        operationId,
                        conflict.labelId,
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
                upsertLabel(update, TaskSyncState.PENDING, isDeleted = false)
                upsertLabelMutation(
                    PendingTaskLabelMutation(
                        operationId,
                        conflict.labelId,
                        TaskMutationKind.UPDATE,
                        base = remote,
                        desired = update,
                        enqueuedAt,
                    ),
                )
            }
        }
    }

    private fun applyTaskUpdateLocked(
        task: Task,
        operationId: String,
        enqueuedAt: Instant,
    ) {
        val current = queries.selectTaskById(task.id)
            .executeAsOneOrNull()
            ?.takeUnless(CachedTask::isDeleted)
            ?: throw CachedTaskNotFoundException(task.id)
        if (current.syncState == TaskSyncState.CONFLICT.name) {
            throw UnresolvedTaskConflictException(task.id)
        }
        val currentMutation = mutationForTask(task.id)
        upsertTask(task, TaskSyncState.PENDING, isDeleted = false)
        when (currentMutation?.kind) {
            TaskMutationKind.CREATE,
            TaskMutationKind.UPDATE,
            -> upsertMutation(
                currentMutation.copy(
                    operationId = operationId,
                    desired = task,
                    enqueuedAt = enqueuedAt,
                ),
            )
            TaskMutationKind.DELETE -> throw InvalidCachedTaskStateException(task.id)
            null -> upsertMutation(
                PendingTaskMutation(
                    operationId = operationId,
                    taskId = task.id,
                    kind = TaskMutationKind.UPDATE,
                    base = current.toTask(),
                    desired = task,
                    enqueuedAt = enqueuedAt,
                ),
            )
        }
    }

    private fun applyProjectUpdateLocked(
        project: TaskProject,
        operationId: String,
        enqueuedAt: Instant,
    ) {
        val current = queries.selectProjectById(project.id)
            .executeAsOneOrNull()
            ?.takeUnless(CachedTaskProject::isDeleted)
            ?: throw CachedTaskProjectNotFoundException(project.id)
        if (current.syncState == TaskSyncState.CONFLICT.name) {
            throw UnresolvedTaskProjectConflictException(project.id)
        }
        val currentMutation = mutationForProject(project.id)
        upsertProject(project, TaskSyncState.PENDING, isDeleted = false)
        when (currentMutation?.kind) {
            TaskMutationKind.CREATE,
            TaskMutationKind.UPDATE,
            -> upsertProjectMutation(
                currentMutation.copy(
                    operationId = operationId,
                    desired = project,
                    enqueuedAt = enqueuedAt,
                ),
            )
            TaskMutationKind.DELETE ->
                throw InvalidCachedTaskProjectStateException(project.id)
            null -> upsertProjectMutation(
                PendingTaskProjectMutation(
                    operationId = operationId,
                    projectId = project.id,
                    kind = TaskMutationKind.UPDATE,
                    base = current.toProject(),
                    desired = project,
                    enqueuedAt = enqueuedAt,
                ),
            )
        }
    }

    private fun applyLabelUpdateLocked(
        label: TaskLabel,
        operationId: String,
        enqueuedAt: Instant,
    ) {
        val current = queries.selectLabelById(label.id)
            .executeAsOneOrNull()
            ?.takeUnless(CachedTaskLabel::isDeleted)
            ?: throw CachedTaskLabelNotFoundException(label.id)
        if (current.syncState == TaskSyncState.CONFLICT.name) {
            throw UnresolvedTaskLabelConflictException(label.id)
        }
        val currentMutation = mutationForLabel(label.id)
        upsertLabel(label, TaskSyncState.PENDING, isDeleted = false)
        when (currentMutation?.kind) {
            TaskMutationKind.CREATE,
            TaskMutationKind.UPDATE,
            -> upsertLabelMutation(
                currentMutation.copy(
                    operationId = operationId,
                    desired = label,
                    enqueuedAt = enqueuedAt,
                ),
            )
            TaskMutationKind.DELETE ->
                throw InvalidCachedTaskLabelStateException(label.id)
            null -> upsertLabelMutation(
                PendingTaskLabelMutation(
                    operationId = operationId,
                    labelId = label.id,
                    kind = TaskMutationKind.UPDATE,
                    base = current.toLabel(),
                    desired = label,
                    enqueuedAt = enqueuedAt,
                ),
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
        queries.selectAllTasks()
            .executeAsList()
            .filter { task ->
                !task.isDeleted && task.projectId in projectIds
            }
            .forEach { record ->
                val updated = record.toTask().copy(
                    projectId = null,
                    updatedAt = changedAt,
                )
                if (
                    record.syncState == TaskSyncState.PENDING.name ||
                    enqueueSyncedTasks
                ) {
                    applyTaskUpdateLocked(
                        task = updated,
                        operationId = taskOperationId(),
                        enqueuedAt = changedAt,
                    )
                } else {
                    upsertTask(
                        task = updated,
                        syncState = TaskSyncState.valueOf(record.syncState),
                        isDeleted = false,
                    )
                }
            }
    }

    private fun clearLabelReferences(
        labelIds: Set<String>,
        taskOperationId: () -> String,
        changedAt: Instant,
        enqueueSyncedTasks: Boolean,
    ) {
        if (labelIds.isEmpty()) return
        queries.selectAllTasks()
            .executeAsList()
            .filter { task ->
                !task.isDeleted &&
                    taskJson.decodeFromString<List<String>>(task.labelIdsJson)
                        .any(labelIds::contains)
            }
            .forEach { record ->
                val task = record.toTask()
                val updated = task.copy(
                    labelIds = task.labelIds - labelIds,
                    updatedAt = changedAt,
                )
                if (
                    record.syncState == TaskSyncState.PENDING.name ||
                    enqueueSyncedTasks
                ) {
                    applyTaskUpdateLocked(
                        task = updated,
                        operationId = taskOperationId(),
                        enqueuedAt = changedAt,
                    )
                } else {
                    upsertTask(
                        task = updated,
                        syncState = TaskSyncState.valueOf(record.syncState),
                        isDeleted = false,
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
        projectFlow.value = loadVisibleProjects()
        projectConflictFlow.value = loadProjectConflicts()
        labelFlow.value = loadVisibleLabels()
        labelConflictFlow.value = loadLabelConflicts()
    }

    private fun loadVisibleTasks(): List<TaskItem> =
        queries.selectVisibleTasks()
            .executeAsList()
            .map(CachedTask::toTaskItem)

    private fun loadConflicts(): List<TaskConflict> =
        queries.selectConflicts()
            .executeAsList()
            .map(DatabaseTaskConflict::toDomainConflict)

    private fun loadVisibleProjects(): List<TaskProjectItem> =
        queries.selectVisibleProjects()
            .executeAsList()
            .map(CachedTaskProject::toProjectItem)

    private fun loadProjectConflicts(): List<TaskProjectConflict> =
        queries.selectProjectConflicts()
            .executeAsList()
            .map(DatabaseTaskProjectConflict::toDomainProjectConflict)

    private fun loadVisibleLabels(): List<TaskLabelItem> =
        queries.selectVisibleLabels()
            .executeAsList()
            .map(CachedTaskLabel::toLabelItem)

    private fun loadLabelConflicts(): List<TaskLabelConflict> =
        queries.selectLabelConflicts()
            .executeAsList()
            .map(DatabaseTaskLabelConflict::toDomainLabelConflict)

    private fun mutationForTask(taskId: String): PendingTaskMutation? =
        queries.selectOutboxByTaskId(taskId)
            .executeAsOneOrNull()
            ?.toMutation()

    private fun mutationForProject(projectId: String): PendingTaskProjectMutation? =
        queries.selectProjectOutboxByProjectId(projectId)
            .executeAsOneOrNull()
            ?.toProjectMutation()

    private fun mutationForLabel(labelId: String): PendingTaskLabelMutation? =
        queries.selectLabelOutboxByLabelId(labelId)
            .executeAsOneOrNull()
            ?.toLabelMutation()

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
            labelIdsJson = taskJson.encodeToString(task.labelIds),
            priority = task.priority.name,
            dueDate = task.dueDate?.toString(),
            dueAtEpochMillis = task.dueAt?.toEpochMilliseconds(),
            reminderAtEpochMillis = task.reminderAt?.toEpochMilliseconds(),
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

    private fun upsertProject(
        project: TaskProject,
        syncState: TaskSyncState,
        isDeleted: Boolean,
    ) {
        queries.upsertProject(
            id = project.id,
            name = project.name,
            color = project.color.name,
            createdAtEpochMillis = project.createdAt.toEpochMilliseconds(),
            updatedAtEpochMillis = project.updatedAt.toEpochMilliseconds(),
            revision = project.revision,
            syncState = syncState.name,
            isDeleted = isDeleted,
        )
    }

    private fun upsertProjectMutation(mutation: PendingTaskProjectMutation) {
        queries.upsertProjectOutbox(
            operationId = mutation.operationId,
            projectId = mutation.projectId,
            kind = mutation.kind.name,
            baseJson = mutation.base?.let(taskJson::encodeToString),
            desiredJson = mutation.desired?.let(taskJson::encodeToString),
            enqueuedAtEpochMillis = mutation.enqueuedAt.toEpochMilliseconds(),
        )
    }

    private fun upsertProjectConflict(conflict: TaskProjectConflict) {
        queries.upsertProjectConflict(
            projectId = conflict.projectId,
            kind = conflict.mutationKind.name,
            baseJson = conflict.base?.let(taskJson::encodeToString),
            localJson = conflict.local?.let(taskJson::encodeToString),
            remoteJson = conflict.remote?.let(taskJson::encodeToString),
            fields = conflict.conflictingFields
                .sortedBy(TaskProjectConflictField::ordinal)
                .joinToString(
                    separator = ",",
                    transform = TaskProjectConflictField::name,
                ),
            detectedAtEpochMillis = conflict.detectedAt.toEpochMilliseconds(),
        )
    }

    private fun upsertLabel(
        label: TaskLabel,
        syncState: TaskSyncState,
        isDeleted: Boolean,
    ) {
        queries.upsertLabel(
            id = label.id,
            name = label.name,
            color = label.color.name,
            createdAtEpochMillis = label.createdAt.toEpochMilliseconds(),
            updatedAtEpochMillis = label.updatedAt.toEpochMilliseconds(),
            revision = label.revision,
            syncState = syncState.name,
            isDeleted = isDeleted,
        )
    }

    private fun upsertLabelMutation(mutation: PendingTaskLabelMutation) {
        queries.upsertLabelOutbox(
            operationId = mutation.operationId,
            labelId = mutation.labelId,
            kind = mutation.kind.name,
            baseJson = mutation.base?.let(taskJson::encodeToString),
            desiredJson = mutation.desired?.let(taskJson::encodeToString),
            enqueuedAtEpochMillis = mutation.enqueuedAt.toEpochMilliseconds(),
        )
    }

    private fun upsertLabelConflict(conflict: TaskLabelConflict) {
        queries.upsertLabelConflict(
            labelId = conflict.labelId,
            kind = conflict.mutationKind.name,
            baseJson = conflict.base?.let(taskJson::encodeToString),
            localJson = conflict.local?.let(taskJson::encodeToString),
            remoteJson = conflict.remote?.let(taskJson::encodeToString),
            fields = conflict.conflictingFields
                .sortedBy(TaskLabelConflictField::ordinal)
                .joinToString(
                    separator = ",",
                    transform = TaskLabelConflictField::name,
                ),
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
    } catch (error: CachedTaskProjectNotFoundException) {
        throw error
    } catch (error: UnresolvedTaskProjectConflictException) {
        throw error
    } catch (error: DuplicateCachedTaskProjectException) {
        throw error
    } catch (error: InvalidCachedTaskProjectStateException) {
        throw error
    } catch (error: CachedTaskLabelNotFoundException) {
        throw error
    } catch (error: UnresolvedTaskLabelConflictException) {
        throw error
    } catch (error: DuplicateCachedTaskLabelException) {
        throw error
    } catch (error: InvalidCachedTaskLabelStateException) {
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
    labelIds = taskJson.decodeFromString(labelIdsJson),
    priority = TaskPriority.valueOf(priority),
    dueDate = dueDate?.let(LocalDate::parse),
    dueAt = dueAtEpochMillis?.let(Instant::fromEpochMilliseconds),
    reminderAt = reminderAtEpochMillis?.let(Instant::fromEpochMilliseconds),
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

private fun CachedTaskProject.toProject(): TaskProject = TaskProject(
    id = id,
    name = name,
    color = TaskProjectColor.valueOf(color),
    createdAt = Instant.fromEpochMilliseconds(createdAtEpochMillis),
    updatedAt = Instant.fromEpochMilliseconds(updatedAtEpochMillis),
    revision = revision,
)

private fun CachedTaskProject.toProjectItem(): TaskProjectItem = TaskProjectItem(
    project = toProject(),
    syncState = TaskSyncState.valueOf(syncState),
)

private fun TaskProjectOutbox.toProjectMutation(): PendingTaskProjectMutation =
    PendingTaskProjectMutation(
        operationId = operationId,
        projectId = projectId,
        kind = TaskMutationKind.valueOf(kind),
        base = baseJson?.let(taskJson::decodeFromString),
        desired = desiredJson?.let(taskJson::decodeFromString),
        enqueuedAt = Instant.fromEpochMilliseconds(enqueuedAtEpochMillis),
    )

private fun DatabaseTaskProjectConflict.toDomainProjectConflict(): TaskProjectConflict =
    TaskProjectConflict(
        projectId = projectId,
        mutationKind = TaskMutationKind.valueOf(kind),
        base = baseJson?.let(taskJson::decodeFromString),
        local = localJson?.let(taskJson::decodeFromString),
        remote = remoteJson?.let(taskJson::decodeFromString),
        conflictingFields = fields
            .split(',')
            .filter(String::isNotBlank)
            .mapTo(linkedSetOf(), TaskProjectConflictField::valueOf),
        detectedAt = Instant.fromEpochMilliseconds(detectedAtEpochMillis),
    )

private fun CachedTaskLabel.toLabel(): TaskLabel = TaskLabel(
    id = id,
    name = name,
    color = TaskLabelColor.valueOf(color),
    createdAt = Instant.fromEpochMilliseconds(createdAtEpochMillis),
    updatedAt = Instant.fromEpochMilliseconds(updatedAtEpochMillis),
    revision = revision,
)

private fun CachedTaskLabel.toLabelItem(): TaskLabelItem = TaskLabelItem(
    label = toLabel(),
    syncState = TaskSyncState.valueOf(syncState),
)

private fun TaskLabelOutbox.toLabelMutation(): PendingTaskLabelMutation =
    PendingTaskLabelMutation(
        operationId = operationId,
        labelId = labelId,
        kind = TaskMutationKind.valueOf(kind),
        base = baseJson?.let(taskJson::decodeFromString),
        desired = desiredJson?.let(taskJson::decodeFromString),
        enqueuedAt = Instant.fromEpochMilliseconds(enqueuedAtEpochMillis),
    )

private fun DatabaseTaskLabelConflict.toDomainLabelConflict(): TaskLabelConflict =
    TaskLabelConflict(
        labelId = labelId,
        mutationKind = TaskMutationKind.valueOf(kind),
        base = baseJson?.let(taskJson::decodeFromString),
        local = localJson?.let(taskJson::decodeFromString),
        remote = remoteJson?.let(taskJson::decodeFromString),
        conflictingFields = fields
            .split(',')
            .filter(String::isNotBlank)
            .mapTo(linkedSetOf(), TaskLabelConflictField::valueOf),
        detectedAt = Instant.fromEpochMilliseconds(detectedAtEpochMillis),
    )

private fun Task.withEdit(edit: TaskEdit): Task = copy(
    title = edit.title,
    notes = edit.notes,
    projectId = edit.projectId,
    labelIds = edit.labelIds ?: labelIds,
    priority = edit.priority,
    dueDate = edit.dueDate,
    dueAt = edit.dueAt,
    reminderAt = edit.reminderAt,
    isCompleted = edit.isCompleted,
)

private fun TaskProject.withEdit(edit: TaskProjectEdit): TaskProject = copy(
    name = edit.name,
    color = edit.color,
)

private fun TaskLabel.withEdit(edit: TaskLabelEdit): TaskLabel = copy(
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

private fun TaskConflict.referencesLabel(labelId: String): Boolean =
    labelId in referencedLabelIds()

private fun TaskConflict.referencedLabelIds(): Set<String> = buildSet {
    base?.labelIds?.let(::addAll)
    local?.labelIds?.let(::addAll)
    remote?.labelIds?.let(::addAll)
}
