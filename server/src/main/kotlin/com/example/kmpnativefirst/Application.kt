package com.example.kmpnativefirst

import com.example.kmpnativefirst.task.SqliteTaskRepository
import com.example.kmpnativefirst.task.TaskService
import io.ktor.server.application.Application

fun Application.module() {
    val jdbcUrl = environment.config.property("storage.jdbcUrl").getString()
    configureApplication(
        taskService = TaskService(SqliteTaskRepository.open(jdbcUrl)),
    )
}
