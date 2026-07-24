package com.example.kmpnativefirst.task.data

import com.example.kmpnativefirst.task.Task

internal sealed interface TaskMergeResult {
    data class Merged(val task: Task) : TaskMergeResult

    data class Conflict(val fields: Set<TaskConflictField>) : TaskMergeResult
}

internal object TaskMerge {
    fun merge(
        base: Task,
        local: Task,
        remote: Task,
    ): TaskMergeResult {
        val conflicts = linkedSetOf<TaskConflictField>()
        val title = mergeValue(
            field = TaskConflictField.TITLE,
            base = base.title,
            local = local.title,
            remote = remote.title,
            conflicts = conflicts,
        )
        val notes = mergeValue(
            field = TaskConflictField.NOTES,
            base = base.notes,
            local = local.notes,
            remote = remote.notes,
            conflicts = conflicts,
        )
        val projectId = mergeValue(
            field = TaskConflictField.PROJECT,
            base = base.projectId,
            local = local.projectId,
            remote = remote.projectId,
            conflicts = conflicts,
        )
        val priority = mergeValue(
            field = TaskConflictField.PRIORITY,
            base = base.priority,
            local = local.priority,
            remote = remote.priority,
            conflicts = conflicts,
        )
        val dueDate = mergeValue(
            field = TaskConflictField.DUE_DATE,
            base = base.dueDate,
            local = local.dueDate,
            remote = remote.dueDate,
            conflicts = conflicts,
        )
        val dueAt = mergeValue(
            field = TaskConflictField.DUE_AT,
            base = base.dueAt,
            local = local.dueAt,
            remote = remote.dueAt,
            conflicts = conflicts,
        )
        val isCompleted = mergeValue(
            field = TaskConflictField.COMPLETION,
            base = base.isCompleted,
            local = local.isCompleted,
            remote = remote.isCompleted,
            conflicts = conflicts,
        )

        return if (conflicts.isEmpty()) {
            TaskMergeResult.Merged(
                remote.copy(
                    title = title,
                    notes = notes,
                    projectId = projectId,
                    priority = priority,
                    dueDate = dueDate,
                    dueAt = dueAt,
                    isCompleted = isCompleted,
                    updatedAt = maxOf(local.updatedAt, remote.updatedAt),
                ),
            )
        } else {
            TaskMergeResult.Conflict(conflicts)
        }
    }

    fun sameEditableContent(
        first: Task,
        second: Task,
    ): Boolean =
        first.title == second.title &&
            first.notes == second.notes &&
            first.projectId == second.projectId &&
            first.priority == second.priority &&
            first.dueDate == second.dueDate &&
            first.dueAt == second.dueAt &&
            first.isCompleted == second.isCompleted

    private fun <T> mergeValue(
        field: TaskConflictField,
        base: T,
        local: T,
        remote: T,
        conflicts: MutableSet<TaskConflictField>,
    ): T = when {
        local == remote -> local
        local == base -> remote
        remote == base -> local
        else -> {
            conflicts += field
            local
        }
    }
}
