package com.example.kmpnativefirst

import com.example.kmpnativefirst.task.ApiErrorResponse
import com.example.kmpnativefirst.task.ApiFieldIssue
import com.example.kmpnativefirst.task.CreateTaskRequest
import com.example.kmpnativefirst.task.ReplaceTaskRequest
import com.example.kmpnativefirst.task.TaskApi
import com.example.kmpnativefirst.task.TaskConflictException
import com.example.kmpnativefirst.task.TaskConstraints
import com.example.kmpnativefirst.task.TaskField
import com.example.kmpnativefirst.task.TaskFilter
import com.example.kmpnativefirst.task.TaskNotFoundException
import com.example.kmpnativefirst.task.TaskService
import com.example.kmpnativefirst.task.TaskValidationCode
import com.example.kmpnativefirst.task.TaskValidationException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.statuspages.exception
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

fun Application.configureApplication(taskService: TaskService) {
    install(ContentNegotiation) {
        json(
            Json {
                encodeDefaults = true
                explicitNulls = false
            },
        )
    }
    install(StatusPages) {
        exception<TaskValidationException> { call, cause ->
            call.respond(
                status = HttpStatusCode.BadRequest,
                message = ApiErrorResponse(
                    code = "validation_failed",
                    message = "One or more task fields are invalid.",
                    issues = cause.issues.map { issue ->
                        ApiFieldIssue(
                            field = issue.field,
                            code = issue.code,
                            message = validationMessage(issue.field, issue.code),
                        )
                    },
                ),
            )
        }
        exception<TaskNotFoundException> { call, cause ->
            call.respond(
                status = HttpStatusCode.NotFound,
                message = ApiErrorResponse(
                    code = "task_not_found",
                    message = "Task '${cause.taskId}' was not found.",
                ),
            )
        }
        exception<TaskConflictException> { call, cause ->
            call.respond(
                status = HttpStatusCode.Conflict,
                message = ApiErrorResponse(
                    code = "task_conflict",
                    message = "Task '${cause.taskId}' has changed. Refresh and try again.",
                ),
            )
        }
        exception<BadRequestException> { call, _ ->
            call.respondMalformedRequest()
        }
        exception<SerializationException> { call, _ ->
            call.respondMalformedRequest()
        }
    }

    routing {
        get("/") {
            call.respondText(sayHello("Ktor"))
        }
        get("/health") {
            call.respond(mapOf("status" to "ok"))
        }
        route(TaskApi.BASE_PATH) {
            get {
                val filter = call.request.queryParameters["filter"]
                    ?.let(::parseFilter)
                    ?: TaskFilter.ALL
                call.respond(
                    taskService.list(
                        filter = filter,
                        query = call.request.queryParameters["query"],
                    ),
                )
            }
            get("/{id}") {
                call.respond(taskService.find(call.taskId()))
            }
            post {
                val task = taskService.create(call.receive<CreateTaskRequest>())
                call.response.header(
                    HttpHeaders.Location,
                    "${TaskApi.BASE_PATH}/${task.id}",
                )
                call.respond(HttpStatusCode.Created, task)
            }
            put("/{id}") {
                call.respond(
                    taskService.replace(
                        id = call.taskId(),
                        request = call.receive<ReplaceTaskRequest>(),
                    ),
                )
            }
            delete("/completed") {
                val deletedCount = taskService.clearCompleted()
                call.respond(mapOf("deletedCount" to deletedCount))
            }
            delete("/{id}") {
                taskService.delete(
                    id = call.taskId(),
                    expectedRevision = call.expectedRevision(),
                )
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

private fun parseFilter(value: String): TaskFilter = TaskFilter.entries.firstOrNull {
    it.name.equals(value, ignoreCase = true)
} ?: throw BadRequestException("Unknown task filter.")

private fun io.ktor.server.application.ApplicationCall.taskId(): String =
    parameters["id"]?.takeIf(String::isNotBlank)
        ?: throw BadRequestException("A task ID is required.")

private fun io.ktor.server.application.ApplicationCall.expectedRevision(): Long =
    request.queryParameters["expectedRevision"]?.toLongOrNull()
        ?: throw BadRequestException("An expected revision is required.")

private suspend fun io.ktor.server.application.ApplicationCall.respondMalformedRequest() {
    respond(
        status = HttpStatusCode.BadRequest,
        message = ApiErrorResponse(
            code = "malformed_request",
            message = "The request could not be read.",
        ),
    )
}

private fun validationMessage(
    field: TaskField,
    code: TaskValidationCode,
): String = when (field to code) {
    TaskField.ID to TaskValidationCode.INVALID ->
        "ID must be a UUID."
    TaskField.TITLE to TaskValidationCode.REQUIRED -> "Title must not be blank."
    TaskField.TITLE to TaskValidationCode.TOO_LONG ->
        "Title must be ${TaskConstraints.MAX_TITLE_LENGTH} characters or fewer."
    TaskField.NOTES to TaskValidationCode.TOO_LONG ->
        "Notes must be ${TaskConstraints.MAX_NOTES_LENGTH} characters or fewer."
    TaskField.EXPECTED_REVISION to TaskValidationCode.INVALID ->
        "Expected revision must be at least 1."
    else -> "The value is invalid."
}
