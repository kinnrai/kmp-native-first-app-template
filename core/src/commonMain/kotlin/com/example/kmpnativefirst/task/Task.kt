package com.example.kmpnativefirst.task

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class Task(
    val id: String,
    val title: String,
    val notes: String? = null,
    val priority: TaskPriority = TaskPriority.NONE,
    val dueAt: Instant? = null,
    val isCompleted: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
    val revision: Long,
)

@Serializable
enum class TaskPriority {
    @SerialName("none")
    NONE,

    @SerialName("low")
    LOW,

    @SerialName("medium")
    MEDIUM,

    @SerialName("high")
    HIGH,
}

@Serializable
enum class TaskFilter {
    @SerialName("all")
    ALL,

    @SerialName("active")
    ACTIVE,

    @SerialName("completed")
    COMPLETED,
}
