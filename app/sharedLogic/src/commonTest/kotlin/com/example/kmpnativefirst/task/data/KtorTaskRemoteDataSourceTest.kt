package com.example.kmpnativefirst.task.data

import com.example.kmpnativefirst.task.TaskListResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class KtorTaskRemoteDataSourceTest {
    @Test
    fun listsTasksFromTheVersionedEndpoint() = runTest {
        val expected = task()
        val remote = remote { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/api/v1/tasks", request.url.encodedPath)
            respondJson(
                taskJson.encodeToString(
                    TaskListResponse(
                        items = listOf(expected),
                        activeCount = 1,
                        completedCount = 0,
                    ),
                ),
            )
        }

        assertEquals(listOf(expected), remote.list())
    }

    @Test
    fun createsTasksAndDecodesTheCanonicalServerVersion() = runTest {
        val created = task(revision = 1)
        val remote = remote { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/v1/tasks", request.url.encodedPath)
            respondJson(
                content = taskJson.encodeToString(created),
                status = HttpStatusCode.Created,
            )
        }

        assertEquals(created, remote.create(created.copy(revision = 0)))
    }

    @Test
    fun returnsNullForAMissingTask() = runTest {
        val remote = remote {
            respondJson(
                content = """{"code":"not_found","message":"Missing"}""",
                status = HttpStatusCode.NotFound,
            )
        }

        assertNull(remote.find(TASK_ID_1))
    }

    @Test
    fun mapsOptimisticConcurrencyFailuresToAConflict() = runTest {
        val remote = remote {
            respondJson(
                content = """{"code":"conflict","message":"Changed"}""",
                status = HttpStatusCode.Conflict,
            )
        }

        val error = assertFailsWith<RemoteTaskConflictException> {
            remote.replace(task())
        }

        assertEquals(TASK_ID_1, error.taskId)
    }

    @Test
    fun sendsTheExpectedRevisionWhenDeleting() = runTest {
        val remote = remote { request ->
            assertEquals(HttpMethod.Delete, request.method)
            assertEquals("/api/v1/tasks/$TASK_ID_1", request.url.encodedPath)
            assertEquals("7", request.url.parameters["expectedRevision"])
            respond(
                content = "",
                status = HttpStatusCode.NoContent,
            )
        }

        remote.delete(TASK_ID_1, expectedRevision = 7)
    }

    @Test
    fun preservesServerErrorsForSynchronizationStatus() = runTest {
        val remote = remote {
            respondJson(
                content = """{"code":"unavailable","message":"Try again later"}""",
                status = HttpStatusCode.ServiceUnavailable,
            )
        }

        val error = assertFailsWith<RemoteTaskServerException> {
            remote.list()
        }

        assertEquals(503, error.statusCode)
        assertEquals("Try again later", error.message)
    }

    private fun remote(
        handler: suspend MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) ->
            io.ktor.client.request.HttpResponseData,
    ): KtorTaskRemoteDataSource {
        val client = HttpClient(MockEngine(handler)) {
            configureTaskHttpClient()
        }
        return KtorTaskRemoteDataSource(
            baseUrl = "https://example.test/",
            client = client,
        )
    }

    private fun MockRequestHandleScope.respondJson(
        content: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = respond(
        content = content,
        status = status,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )
}
