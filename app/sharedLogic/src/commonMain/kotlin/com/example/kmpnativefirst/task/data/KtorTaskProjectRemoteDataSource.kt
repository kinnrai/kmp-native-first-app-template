package com.example.kmpnativefirst.task.data

import com.example.kmpnativefirst.task.CreateTaskProjectRequest
import com.example.kmpnativefirst.task.ReplaceTaskProjectRequest
import com.example.kmpnativefirst.task.TaskProject
import com.example.kmpnativefirst.task.TaskProjectApi
import com.example.kmpnativefirst.task.TaskProjectApiErrorResponse
import com.example.kmpnativefirst.task.TaskProjectListResponse
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

internal class KtorTaskProjectRemoteDataSource(
    private val baseUrl: String,
    private val client: HttpClient = createPlatformTaskHttpClient(),
) : TaskProjectRemoteDataSource {
    init {
        require(baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) {
            "The task service base URL must use HTTP or HTTPS."
        }
    }

    private val projectsUrl = "${baseUrl.trimEnd('/')}${TaskProjectApi.BASE_PATH}"

    override suspend fun listProjects(): List<TaskProject> {
        val response = client.get(projectsUrl)
        response.requireProjectSuccess()
        return response.body<TaskProjectListResponse>().items
    }

    override suspend fun findProject(id: String): TaskProject? {
        val response = client.get("$projectsUrl/$id")
        return when (response.status) {
            HttpStatusCode.NotFound -> null
            else -> {
                response.requireProjectSuccess(projectId = id)
                response.body()
            }
        }
    }

    override suspend fun createProject(project: TaskProject): TaskProject {
        val response = client.post(projectsUrl) {
            contentType(ContentType.Application.Json)
            setBody(
                CreateTaskProjectRequest(
                    id = project.id,
                    name = project.name,
                    color = project.color,
                ),
            )
        }
        response.requireProjectSuccess(projectId = project.id)
        return response.body()
    }

    override suspend fun replaceProject(project: TaskProject): TaskProject {
        val response = client.put("$projectsUrl/${project.id}") {
            contentType(ContentType.Application.Json)
            setBody(
                ReplaceTaskProjectRequest(
                    name = project.name,
                    color = project.color,
                    expectedRevision = project.revision,
                ),
            )
        }
        response.requireProjectSuccess(projectId = project.id)
        return response.body()
    }

    override suspend fun deleteProject(
        id: String,
        expectedRevision: Long,
    ) {
        val response = client.delete("$projectsUrl/$id") {
            url {
                parameters.append("expectedRevision", expectedRevision.toString())
            }
        }
        response.requireProjectSuccess(projectId = id)
    }

    override fun close() {
        client.close()
    }

    private suspend fun HttpResponse.requireProjectSuccess(projectId: String? = null) {
        if (status.value in 200..299) {
            return
        }
        val message = runCatching { body<TaskProjectApiErrorResponse>().message }
            .getOrElse { "The task project service returned HTTP ${status.value}." }
        when (status) {
            HttpStatusCode.Conflict -> if (projectId != null) {
                throw RemoteTaskProjectConflictException(projectId)
            } else {
                throw RemoteTaskServerException(status.value, message)
            }
            HttpStatusCode.NotFound -> if (projectId != null) {
                throw RemoteTaskProjectNotFoundException(projectId)
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
