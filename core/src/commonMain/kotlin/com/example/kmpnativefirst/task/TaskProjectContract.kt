package com.example.kmpnativefirst.task

import kotlinx.serialization.Serializable

@Serializable
data class CreateTaskProjectRequest(
    val id: String,
    val name: String,
    val color: TaskProjectColor = TaskProjectColor.BLUE,
)

@Serializable
data class ReplaceTaskProjectRequest(
    val name: String,
    val color: TaskProjectColor = TaskProjectColor.BLUE,
    val expectedRevision: Long,
)

@Serializable
data class TaskProjectListResponse(
    val items: List<TaskProject>,
)

@Serializable
data class TaskProjectApiErrorResponse(
    val code: String,
    val message: String,
    val issues: List<TaskProjectApiFieldIssue> = emptyList(),
)

@Serializable
data class TaskProjectApiFieldIssue(
    val field: TaskProjectField,
    val code: TaskValidationCode,
    val message: String,
)

object TaskProjectApi {
    const val BASE_PATH = "/api/v1/projects"
}
