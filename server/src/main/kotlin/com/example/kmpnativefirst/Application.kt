package com.example.kmpnativefirst

import com.example.kmpnativefirst.task.SqliteTaskRepository
import com.example.kmpnativefirst.task.TaskLabelService
import com.example.kmpnativefirst.task.TaskProjectService
import com.example.kmpnativefirst.task.TaskService
import io.ktor.server.application.Application

fun Application.module() {
    val jdbcUrl = environment.config.property("storage.jdbcUrl").getString()
    val repository = SqliteTaskRepository.open(jdbcUrl)
    configureApplication(
        taskService = TaskService(repository),
        taskProjectService = TaskProjectService(repository),
        taskLabelService = TaskLabelService(repository),
    )
}
