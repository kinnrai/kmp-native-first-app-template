package com.example.kmpnativefirst.task.data

import com.example.kmpnativefirst.task.ApiErrorResponse
import com.example.kmpnativefirst.task.CreateTaskRequest
import com.example.kmpnativefirst.task.ReplaceTaskRequest
import com.example.kmpnativefirst.task.Task
import com.example.kmpnativefirst.task.TaskApi
import com.example.kmpnativefirst.task.TaskListResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

internal class KtorTaskRemoteDataSource(
    private val baseUrl: String,
    private val client: HttpClient = createPlatformTaskHttpClient(),
) : TaskRemoteDataSource {
    init {
        require(baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) {
            "The task service base URL must use HTTP or HTTPS."
        }
    }

    private val tasksUrl = "${baseUrl.trimEnd('/')}${TaskApi.BASE_PATH}"

    override suspend fun list(): List<Task> {
        val response = client.get(tasksUrl)
        response.requireSuccess()
        return response.body<TaskListResponse>().items
    }

    override suspend fun find(id: String): Task? {
        val response = client.get("$tasksUrl/$id")
        return when (response.status) {
            HttpStatusCode.NotFound -> null
            else -> {
                response.requireSuccess(taskId = id)
                response.body()
            }
        }
    }

    override suspend fun create(task: Task): Task {
        val response = client.post(tasksUrl) {
            contentType(ContentType.Application.Json)
            setBody(
                CreateTaskRequest(
                    id = task.id,
                    title = task.title,
                    notes = task.notes,
                    projectId = task.projectId,
                    priority = task.priority,
                    dueDate = task.dueDate,
                    dueAt = task.dueAt,
                ),
            )
        }
        response.requireSuccess(taskId = task.id)
        return response.body()
    }

    override suspend fun replace(task: Task): Task {
        val response = client.put("$tasksUrl/${task.id}") {
            contentType(ContentType.Application.Json)
            setBody(
                ReplaceTaskRequest(
                    title = task.title,
                    notes = task.notes,
                    projectId = task.projectId,
                    priority = task.priority,
                    dueDate = task.dueDate,
                    dueAt = task.dueAt,
                    isCompleted = task.isCompleted,
                    expectedRevision = task.revision,
                ),
            )
        }
        response.requireSuccess(taskId = task.id)
        return response.body()
    }

    override suspend fun delete(
        id: String,
        expectedRevision: Long,
    ) {
        val response = client.delete("$tasksUrl/$id") {
            url {
                parameters.append("expectedRevision", expectedRevision.toString())
            }
        }
        response.requireSuccess(taskId = id)
    }

    override fun close() {
        client.close()
    }

    private suspend fun HttpResponse.requireSuccess(taskId: String? = null) {
        if (status.value in 200..299) {
            return
        }
        val message = runCatching { body<ApiErrorResponse>().message }
            .getOrElse { "The task service returned HTTP ${status.value}." }
        when (status) {
            HttpStatusCode.Conflict -> if (taskId != null) {
                throw RemoteTaskConflictException(taskId)
            } else {
                throw RemoteTaskServerException(status.value, message)
            }
            HttpStatusCode.NotFound -> if (taskId != null) {
                throw RemoteTaskNotFoundException(taskId)
            } else {
                throw RemoteTaskRejectedException(status.value, message)
            }
            else -> if (status.value >= 500) {
                throw RemoteTaskServerException(status.value, message)
            } else {
                throw RemoteTaskRejectedException(status.value, message)
            }
        }
    }
}
