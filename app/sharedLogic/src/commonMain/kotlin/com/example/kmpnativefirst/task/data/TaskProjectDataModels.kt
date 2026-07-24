package com.example.kmpnativefirst.task.data

import com.example.kmpnativefirst.task.TaskProject
import com.example.kmpnativefirst.task.TaskProjectColor
import com.example.kmpnativefirst.task.TaskProjectValidationIssue
import kotlinx.serialization.Serializable
import kotlin.time.Instant

data class TaskProjectDraft(
    val name: String,
    val color: TaskProjectColor = TaskProjectColor.BLUE,
)

data class TaskProjectEdit(
    val name: String,
    val color: TaskProjectColor = TaskProjectColor.BLUE,
)

fun TaskProject.toEdit(): TaskProjectEdit = TaskProjectEdit(
    name = name,
    color = color,
)

data class TaskProjectItem(
    val project: TaskProject,
    val syncState: TaskSyncState,
)

@Serializable
enum class TaskProjectConflictField {
    CREATION,
    DELETION,
    NAME,
    COLOR,
}

@Serializable
data class TaskProjectConflict(
    val projectId: String,
    val mutationKind: TaskMutationKind,
    val base: TaskProject?,
    val local: TaskProject?,
    val remote: TaskProject?,
    val conflictingFields: Set<TaskProjectConflictField>,
    val detectedAt: Instant,
)

sealed interface TaskProjectConflictResolution {
    data object UseRemote : TaskProjectConflictResolution

    data object KeepLocal : TaskProjectConflictResolution

    data class Merge(val edit: TaskProjectEdit) : TaskProjectConflictResolution
}

@Serializable
internal data class PendingTaskProjectMutation(
    val operationId: String,
    val projectId: String,
    val kind: TaskMutationKind,
    val base: TaskProject?,
    val desired: TaskProject?,
    val enqueuedAt: Instant,
)

class InvalidTaskProjectInputException(
    val issues: List<TaskProjectValidationIssue>,
) : IllegalArgumentException("Task project input is invalid.")

class CachedTaskProjectNotFoundException(
    projectId: String,
) : NoSuchElementException("Task project '$projectId' was not found in the local cache.")

class UnresolvedTaskProjectConflictException(
    projectId: String,
) : IllegalStateException("Task project '$projectId' has an unresolved synchronization conflict.")

internal class DuplicateCachedTaskProjectException(
    projectId: String,
) : IllegalStateException("Task project '$projectId' already exists locally.")

internal class InvalidCachedTaskProjectStateException(
    projectId: String,
) : IllegalStateException("Task project '$projectId' is in an invalid local state.")
