package com.example.kmpnativefirst.task.data

import com.example.kmpnativefirst.task.TaskLabelListResponse
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

class KtorTaskLabelRemoteDataSourceTest {
    @Test
    fun listsLabelsFromTheVersionedEndpoint() = runTest {
        val expected = taskLabel()
        val remote = remote { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/api/v1/labels", request.url.encodedPath)
            respondJson(
                taskJson.encodeToString(
                    TaskLabelListResponse(items = listOf(expected)),
                ),
            )
        }

        assertEquals(listOf(expected), remote.listLabels())
    }

    @Test
    fun createsLabelsAndDecodesTheCanonicalServerVersion() = runTest {
        val created = taskLabel(revision = 1)
        val remote = remote { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/v1/labels", request.url.encodedPath)
            respondJson(
                content = taskJson.encodeToString(created),
                status = HttpStatusCode.Created,
            )
        }

        assertEquals(created, remote.createLabel(created.copy(revision = 0)))
    }

    @Test
    fun returnsNullForAMissingLabel() = runTest {
        val remote = remote {
            respondJson(
                content = """{"code":"not_found","message":"Missing"}""",
                status = HttpStatusCode.NotFound,
            )
        }

        assertNull(remote.findLabel(LABEL_ID_1))
    }

    @Test
    fun mapsOptimisticConcurrencyFailuresToALabelConflict() = runTest {
        val remote = remote {
            respondJson(
                content = """{"code":"conflict","message":"Changed"}""",
                status = HttpStatusCode.Conflict,
            )
        }

        val error = assertFailsWith<RemoteTaskLabelConflictException> {
            remote.replaceLabel(taskLabel())
        }

        assertEquals(LABEL_ID_1, error.labelId)
    }

    @Test
    fun sendsTheExpectedRevisionWhenDeletingALabel() = runTest {
        val remote = remote { request ->
            assertEquals(HttpMethod.Delete, request.method)
            assertEquals(
                "/api/v1/labels/$LABEL_ID_1",
                request.url.encodedPath,
            )
            assertEquals("7", request.url.parameters["expectedRevision"])
            respond(content = "", status = HttpStatusCode.NoContent)
        }

        remote.deleteLabel(LABEL_ID_1, expectedRevision = 7)
    }

    private fun remote(
        handler: suspend MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) ->
            io.ktor.client.request.HttpResponseData,
    ): KtorTaskLabelRemoteDataSource {
        val client = HttpClient(MockEngine(handler)) {
            configureTaskHttpClient()
        }
        return KtorTaskLabelRemoteDataSource(
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
