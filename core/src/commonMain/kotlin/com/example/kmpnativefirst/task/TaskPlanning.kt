package com.example.kmpnativefirst.task

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Pure planning rules shared by every presentation layer.
 *
 * Date-only deadlines remain calendar dates. Timed deadlines are interpreted in
 * the caller's time zone only while selecting a smart view.
 */
object TaskPlanning {
    fun dueDate(
        task: Task,
        timeZone: TimeZone,
    ): LocalDate? = task.dueDate ?: task.dueAt
        ?.toLocalDateTime(timeZone)
        ?.date

    fun matches(
        task: Task,
        view: TaskSmartView,
        today: LocalDate,
        timeZone: TimeZone,
    ): Boolean = when (view) {
        TaskSmartView.ALL -> true
        TaskSmartView.INBOX ->
            !task.isCompleted && task.dueDate == null && task.dueAt == null
        TaskSmartView.TODAY ->
            !task.isCompleted && dueDate(task, timeZone)?.let { it <= today } == true
        TaskSmartView.UPCOMING ->
            !task.isCompleted && dueDate(task, timeZone)?.let { it > today } == true
        TaskSmartView.COMPLETED -> task.isCompleted
    }

    fun select(
        tasks: Iterable<Task>,
        view: TaskSmartView,
        today: LocalDate,
        timeZone: TimeZone,
    ): List<Task> = tasks
        .filter { matches(it, view, today, timeZone) }
        .sortedWith(
            compareBy<Task>(
                Task::isCompleted,
                { dueDate(it, timeZone) == null },
                { dueDate(it, timeZone) },
                { it.dueAt },
            )
                .thenByDescending { it.priority.planningRank }
                .thenByDescending(Task::updatedAt),
        )
}

private val TaskPriority.planningRank: Int
    get() = when (this) {
        TaskPriority.NONE -> 0
        TaskPriority.LOW -> 1
        TaskPriority.MEDIUM -> 2
        TaskPriority.HIGH -> 3
    }
