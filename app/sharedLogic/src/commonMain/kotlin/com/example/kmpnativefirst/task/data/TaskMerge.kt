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
        val labelIds = mergeMembershipSet(
            base = base.labelIds,
            local = local.labelIds,
            remote = remote.labelIds,
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
        val reminderAt = mergeValue(
            field = TaskConflictField.REMINDER_AT,
            base = base.reminderAt,
            local = local.reminderAt,
            remote = remote.reminderAt,
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
                    labelIds = labelIds,
                    priority = priority,
                    dueDate = dueDate,
                    dueAt = dueAt,
                    reminderAt = reminderAt,
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
            first.labelIds == second.labelIds &&
            first.priority == second.priority &&
            first.dueDate == second.dueDate &&
            first.dueAt == second.dueAt &&
            first.reminderAt == second.reminderAt &&
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

    private fun <T : Comparable<T>> mergeMembershipSet(
        base: Collection<T>,
        local: Collection<T>,
        remote: Collection<T>,
    ): List<T> {
        val baseSet = base.toSet()
        val localSet = local.toSet()
        val remoteSet = remote.toSet()
        val additions = (localSet - baseSet) + (remoteSet - baseSet)
        val removals = (baseSet - localSet) + (baseSet - remoteSet)
        return ((baseSet + additions) - removals).sorted()
    }
}
