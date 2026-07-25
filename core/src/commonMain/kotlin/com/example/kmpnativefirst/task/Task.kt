package com.example.kmpnativefirst.task

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class Task(
    val id: String,
    val title: String,
    val notes: String? = null,
    val projectId: String? = null,
    val priority: TaskPriority = TaskPriority.NONE,
    val dueDate: LocalDate? = null,
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

@Serializable
enum class TaskSmartView {
    @SerialName("all")
    ALL,

    @SerialName("inbox")
    INBOX,

    @SerialName("today")
    TODAY,

    @SerialName("upcoming")
    UPCOMING,

    @SerialName("completed")
    COMPLETED,
}
