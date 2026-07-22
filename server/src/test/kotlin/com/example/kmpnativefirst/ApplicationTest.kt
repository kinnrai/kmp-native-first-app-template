package com.example.kmpnativefirst

import com.example.kmpnativefirst.task.ApiErrorResponse
import com.example.kmpnativefirst.task.CreateTaskRequest
import com.example.kmpnativefirst.task.InMemoryTaskRepository
import com.example.kmpnativefirst.task.ReplaceTaskRequest
import com.example.kmpnativefirst.task.Task
import com.example.kmpnativefirst.task.TaskApi
import com.example.kmpnativefirst.task.TaskListResponse
import com.example.kmpnativefirst.task.TaskPriority
import com.example.kmpnativefirst.task.TaskService
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class ApplicationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun keepsRootGreetingAndExposesHealthCheck() = testApplication {
        application { configureApplication(taskService()) }

        assertEquals("Hello, Ktor!", client.get("/").bodyAsText())
        val health = client.get("/health")
        assertEquals(HttpStatusCode.OK, health.status)
        assertEquals("{\"status\":\"ok\"}", health.bodyAsText())
    }

    @Test
    fun supportsTaskCrudFilteringAndCompletedCleanup() = testApplication {
        application { configureApplication(taskService()) }

        val createdResponse = client.post(TaskApi.BASE_PATH) {
            jsonBody(
                CreateTaskRequest(
                    id = TASK_ID,
                    title = "  Plan release  ",
                    notes = "Prepare notes",
                    priority = TaskPriority.HIGH,
                ),
            )
        }
        assertEquals(HttpStatusCode.Created, createdResponse.status)
        assertEquals("${TaskApi.BASE_PATH}/$TASK_ID", createdResponse.headers[HttpHeaders.Location])
        val created = createdResponse.decode<Task>()
        assertEquals("Plan release", created.title)

        val fetched = client.get("${TaskApi.BASE_PATH}/${created.id}").decode<Task>()
        assertEquals(created, fetched)

        val updatedResponse = client.put("${TaskApi.BASE_PATH}/${created.id}") {
            jsonBody(
                ReplaceTaskRequest(
                    title = created.title,
                    notes = created.notes,
                    priority = created.priority,
                    isCompleted = true,
                    expectedRevision = created.revision,
                ),
            )
        }
        assertEquals(HttpStatusCode.OK, updatedResponse.status)
        val updated = updatedResponse.decode<Task>()
        assertTrue(updated.isCompleted)
        assertEquals(2, updated.revision)

        val completed = client.get("${TaskApi.BASE_PATH}?filter=completed&query=release")
            .decode<TaskListResponse>()
        assertEquals(listOf(updated), completed.items)
        assertEquals(0, completed.activeCount)
        assertEquals(1, completed.completedCount)

        val cleanup = client.delete("${TaskApi.BASE_PATH}/completed")
        assertEquals(HttpStatusCode.OK, cleanup.status)
        assertEquals("{\"deletedCount\":1}", cleanup.bodyAsText())
        assertEquals(
            HttpStatusCode.NotFound,
            client.get("${TaskApi.BASE_PATH}/${created.id}").status,
        )
    }

    @Test
    fun reportsValidationMalformedRequestsAndUnknownFilters() = testApplication {
        application { configureApplication(taskService()) }

        val invalid = client.post(TaskApi.BASE_PATH) {
            jsonBody(CreateTaskRequest(id = "not-a-uuid", title = " "))
        }
        assertEquals(HttpStatusCode.BadRequest, invalid.status)
        val validationError = invalid.decode<ApiErrorResponse>()
        assertEquals("validation_failed", validationError.code)
        assertEquals(2, validationError.issues.size)

        val malformed = client.post(TaskApi.BASE_PATH) {
            contentType(ContentType.Application.Json)
            setBody("not-json")
        }
        assertEquals(HttpStatusCode.BadRequest, malformed.status)
        assertEquals("malformed_request", malformed.decode<ApiErrorResponse>().code)

        val unknownFilter = client.get("${TaskApi.BASE_PATH}?filter=unknown")
        assertEquals(HttpStatusCode.BadRequest, unknownFilter.status)
        assertEquals("malformed_request", unknownFilter.decode<ApiErrorResponse>().code)
    }

    @Test
    fun reportsRevisionConflictsAndMissingTasks() = testApplication {
        application { configureApplication(taskService()) }
        val created = client.post(TaskApi.BASE_PATH) {
            jsonBody(CreateTaskRequest(id = TASK_ID, title = "Original"))
        }.decode<Task>()
        val firstUpdate = ReplaceTaskRequest(
            title = "Updated",
            expectedRevision = created.revision,
        )
        val updated = client.put("${TaskApi.BASE_PATH}/${created.id}") {
            jsonBody(firstUpdate)
        }.decode<Task>()

        val conflict = client.put("${TaskApi.BASE_PATH}/${created.id}") {
            jsonBody(firstUpdate.copy(title = "Stale"))
        }
        assertEquals(HttpStatusCode.Conflict, conflict.status)
        assertEquals("task_conflict", conflict.decode<ApiErrorResponse>().code)

        val staleDelete = client.delete(
            "${TaskApi.BASE_PATH}/${created.id}?expectedRevision=${created.revision}",
        )
        assertEquals(HttpStatusCode.Conflict, staleDelete.status)

        val deleted = client.delete(
            "${TaskApi.BASE_PATH}/${created.id}?expectedRevision=${updated.revision}",
        )
        assertEquals(HttpStatusCode.NoContent, deleted.status)

        val missing = client.delete("${TaskApi.BASE_PATH}/missing?expectedRevision=1")
        assertEquals(HttpStatusCode.NotFound, missing.status)
        assertEquals("task_not_found", missing.decode<ApiErrorResponse>().code)
    }

    private fun taskService(): TaskService = TaskService(
        repository = InMemoryTaskRepository(),
        clock = object : Clock {
            override fun now(): Instant = Instant.parse("2026-07-23T10:00:00Z")
        },
    )

    private inline fun <reified T> io.ktor.client.request.HttpRequestBuilder.jsonBody(body: T) {
        contentType(ContentType.Application.Json)
        setBody(json.encodeToString(body))
    }

    private suspend inline fun <reified T> HttpResponse.decode(): T {
        val body = bodyAsText()
        assertNotNull(body)
        return json.decodeFromString(body)
    }

    private companion object {
        const val TASK_ID = "11111111-1111-4111-8111-111111111111"
    }
}
