package com.example.kmpnativefirst.task

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class CreateTaskRequest(
    val id: String,
    val title: String,
    val notes: String? = null,
    val priority: TaskPriority = TaskPriority.NONE,
    val dueAt: Instant? = null,
)

@Serializable
data class ReplaceTaskRequest(
    val title: String,
    val notes: String? = null,
    val priority: TaskPriority = TaskPriority.NONE,
    val dueAt: Instant? = null,
    val isCompleted: Boolean = false,
    val expectedRevision: Long,
)

@Serializable
data class TaskListResponse(
    val items: List<Task>,
    val activeCount: Int,
    val completedCount: Int,
)

@Serializable
data class ApiErrorResponse(
    val code: String,
    val message: String,
    val issues: List<ApiFieldIssue> = emptyList(),
)

@Serializable
data class ApiFieldIssue(
    val field: TaskField,
    val code: TaskValidationCode,
    val message: String,
)

object TaskApi {
    const val BASE_PATH = "/api/v1/tasks"
}
