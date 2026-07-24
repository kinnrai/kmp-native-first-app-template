package com.example.kmpnativefirst.task.data

import com.example.kmpnativefirst.task.TaskProjectListResponse
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

class KtorTaskProjectRemoteDataSourceTest {
    @Test
    fun listsProjectsFromTheVersionedEndpoint() = runTest {
        val expected = taskProject()
        val remote = remote { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/api/v1/projects", request.url.encodedPath)
            respondJson(
                taskJson.encodeToString(
                    TaskProjectListResponse(items = listOf(expected)),
                ),
            )
        }

        assertEquals(listOf(expected), remote.listProjects())
    }

    @Test
    fun createsProjectsAndDecodesTheCanonicalServerVersion() = runTest {
        val created = taskProject(revision = 1)
        val remote = remote { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/v1/projects", request.url.encodedPath)
            respondJson(
                content = taskJson.encodeToString(created),
                status = HttpStatusCode.Created,
            )
        }

        assertEquals(created, remote.createProject(created.copy(revision = 0)))
    }

    @Test
    fun returnsNullForAMissingProject() = runTest {
        val remote = remote {
            respondJson(
                content = """{"code":"not_found","message":"Missing"}""",
                status = HttpStatusCode.NotFound,
            )
        }

        assertNull(remote.findProject(PROJECT_ID_1))
    }

    @Test
    fun mapsOptimisticConcurrencyFailuresToAProjectConflict() = runTest {
        val remote = remote {
            respondJson(
                content = """{"code":"conflict","message":"Changed"}""",
                status = HttpStatusCode.Conflict,
            )
        }

        val error = assertFailsWith<RemoteTaskProjectConflictException> {
            remote.replaceProject(taskProject())
        }

        assertEquals(PROJECT_ID_1, error.projectId)
    }

    @Test
    fun sendsTheExpectedRevisionWhenDeletingAProject() = runTest {
        val remote = remote { request ->
            assertEquals(HttpMethod.Delete, request.method)
            assertEquals(
                "/api/v1/projects/$PROJECT_ID_1",
                request.url.encodedPath,
            )
            assertEquals("7", request.url.parameters["expectedRevision"])
            respond(content = "", status = HttpStatusCode.NoContent)
        }

        remote.deleteProject(PROJECT_ID_1, expectedRevision = 7)
    }

    private fun remote(
        handler: suspend MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) ->
            io.ktor.client.request.HttpResponseData,
    ): KtorTaskProjectRemoteDataSource {
        val client = HttpClient(MockEngine(handler)) {
            configureTaskHttpClient()
        }
        return KtorTaskProjectRemoteDataSource(
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
