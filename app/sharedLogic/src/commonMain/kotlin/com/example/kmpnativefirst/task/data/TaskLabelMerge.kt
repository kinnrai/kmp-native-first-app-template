package com.example.kmpnativefirst.task.data

import com.example.kmpnativefirst.task.TaskLabel

internal object TaskLabelMerge {
    fun merge(
        base: TaskLabel,
        local: TaskLabel,
        remote: TaskLabel,
    ): TaskLabelMergeResult {
        val conflicts = linkedSetOf<TaskLabelConflictField>()
        val name = mergeValue(
            field = TaskLabelConflictField.NAME,
            base = base.name,
            local = local.name,
            remote = remote.name,
            conflicts = conflicts,
        )
        val color = mergeValue(
            field = TaskLabelConflictField.COLOR,
            base = base.color,
            local = local.color,
            remote = remote.color,
            conflicts = conflicts,
        )
        return if (conflicts.isEmpty()) {
            TaskLabelMergeResult.Merged(
                remote.copy(
                    name = name,
                    color = color,
                    updatedAt = local.updatedAt,
                ),
            )
        } else {
            TaskLabelMergeResult.Conflict(conflicts)
        }
    }

    fun sameEditableContent(
        first: TaskLabel,
        second: TaskLabel,
    ): Boolean =
        first.name == second.name &&
            first.color == second.color

    private fun <T> mergeValue(
        field: TaskLabelConflictField,
        base: T,
        local: T,
        remote: T,
        conflicts: MutableSet<TaskLabelConflictField>,
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

internal sealed interface TaskLabelMergeResult {
    data class Merged(val label: TaskLabel) : TaskLabelMergeResult

    data class Conflict(
        val fields: Set<TaskLabelConflictField>,
    ) : TaskLabelMergeResult
}
