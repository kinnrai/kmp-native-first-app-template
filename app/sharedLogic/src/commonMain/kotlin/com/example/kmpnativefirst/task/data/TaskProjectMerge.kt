package com.example.kmpnativefirst.task.data

import com.example.kmpnativefirst.task.TaskProject

internal object TaskProjectMerge {
    fun merge(
        base: TaskProject,
        local: TaskProject,
        remote: TaskProject,
    ): TaskProjectMergeResult {
        val conflicts = linkedSetOf<TaskProjectConflictField>()
        val name = mergeValue(
            field = TaskProjectConflictField.NAME,
            base = base.name,
            local = local.name,
            remote = remote.name,
            conflicts = conflicts,
        )
        val color = mergeValue(
            field = TaskProjectConflictField.COLOR,
            base = base.color,
            local = local.color,
            remote = remote.color,
            conflicts = conflicts,
        )
        return if (conflicts.isEmpty()) {
            TaskProjectMergeResult.Merged(
                remote.copy(
                    name = name,
                    color = color,
                    updatedAt = local.updatedAt,
                ),
            )
        } else {
            TaskProjectMergeResult.Conflict(conflicts)
        }
    }

    fun sameEditableContent(
        first: TaskProject,
        second: TaskProject,
    ): Boolean =
        first.name == second.name &&
            first.color == second.color

    private fun <T> mergeValue(
        field: TaskProjectConflictField,
        base: T,
        local: T,
        remote: T,
        conflicts: MutableSet<TaskProjectConflictField>,
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
internal sealed interface TaskProjectMergeResult {
    data class Merged(val project: TaskProject) : TaskProjectMergeResult

    data class Conflict(
        val fields: Set<TaskProjectConflictField>,
    ) : TaskProjectMergeResult
}
