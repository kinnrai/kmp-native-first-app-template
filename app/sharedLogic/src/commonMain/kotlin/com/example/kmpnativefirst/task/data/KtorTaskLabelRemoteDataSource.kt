package com.example.kmpnativefirst.task.data

import com.example.kmpnativefirst.task.CreateTaskLabelRequest
import com.example.kmpnativefirst.task.ReplaceTaskLabelRequest
import com.example.kmpnativefirst.task.TaskLabel
import com.example.kmpnativefirst.task.TaskLabelApi
import com.example.kmpnativefirst.task.TaskLabelApiErrorResponse
import com.example.kmpnativefirst.task.TaskLabelListResponse
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

internal class KtorTaskLabelRemoteDataSource(
    private val baseUrl: String,
    private val client: HttpClient = createPlatformTaskHttpClient(),
) : TaskLabelRemoteDataSource {
    init {
        require(baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) {
            "The task service base URL must use HTTP or HTTPS."
        }
    }

    private val labelsUrl = "${baseUrl.trimEnd('/')}${TaskLabelApi.BASE_PATH}"

    override suspend fun listLabels(): List<TaskLabel> {
        val response = client.get(labelsUrl)
        response.requireLabelSuccess()
        return response.body<TaskLabelListResponse>().items
    }

    override suspend fun findLabel(id: String): TaskLabel? {
        val response = client.get("$labelsUrl/$id")
        return when (response.status) {
            HttpStatusCode.NotFound -> null
            else -> {
                response.requireLabelSuccess(labelId = id)
                response.body()
            }
        }
    }

    override suspend fun createLabel(label: TaskLabel): TaskLabel {
        val response = client.post(labelsUrl) {
            contentType(ContentType.Application.Json)
            setBody(
                CreateTaskLabelRequest(
                    id = label.id,
                    name = label.name,
                    color = label.color,
                ),
            )
        }
        response.requireLabelSuccess(labelId = label.id)
        return response.body()
    }

    override suspend fun replaceLabel(label: TaskLabel): TaskLabel {
        val response = client.put("$labelsUrl/${label.id}") {
            contentType(ContentType.Application.Json)
            setBody(
                ReplaceTaskLabelRequest(
                    name = label.name,
                    color = label.color,
                    expectedRevision = label.revision,
                ),
            )
        }
        response.requireLabelSuccess(labelId = label.id)
        return response.body()
    }

    override suspend fun deleteLabel(
        id: String,
        expectedRevision: Long,
    ) {
        val response = client.delete("$labelsUrl/$id") {
            url {
                parameters.append("expectedRevision", expectedRevision.toString())
            }
        }
        response.requireLabelSuccess(labelId = id)
    }

    override fun close() {
        client.close()
    }

    private suspend fun HttpResponse.requireLabelSuccess(labelId: String? = null) {
        if (status.value in 200..299) {
            return
        }
        val message = runCatching { body<TaskLabelApiErrorResponse>().message }
            .getOrElse { "The task label service returned HTTP ${status.value}." }
        when (status) {
            HttpStatusCode.Conflict -> if (labelId != null) {
                throw RemoteTaskLabelConflictException(labelId)
            } else {
                throw RemoteTaskServerException(status.value, message)
            }
            HttpStatusCode.NotFound -> if (labelId != null) {
                throw RemoteTaskLabelNotFoundException(labelId)
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
