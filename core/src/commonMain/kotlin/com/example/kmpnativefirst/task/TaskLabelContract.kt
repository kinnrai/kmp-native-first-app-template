package com.example.kmpnativefirst.task

import kotlinx.serialization.Serializable

@Serializable
data class CreateTaskLabelRequest(
    val id: String,
    val name: String,
    val color: TaskLabelColor = TaskLabelColor.SLATE,
)

@Serializable
data class ReplaceTaskLabelRequest(
    val name: String,
    val color: TaskLabelColor = TaskLabelColor.SLATE,
    val expectedRevision: Long,
)

@Serializable
data class TaskLabelListResponse(
    val items: List<TaskLabel>,
)

@Serializable
data class TaskLabelApiErrorResponse(
    val code: String,
    val message: String,
    val issues: List<TaskLabelApiFieldIssue> = emptyList(),
)

@Serializable
data class TaskLabelApiFieldIssue(
    val field: TaskLabelField,
    val code: TaskValidationCode,
    val message: String,
)

object TaskLabelApi {
    const val BASE_PATH = "/api/v1/labels"
}
