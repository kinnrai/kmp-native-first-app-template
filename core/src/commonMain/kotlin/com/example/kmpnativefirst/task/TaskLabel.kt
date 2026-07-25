package com.example.kmpnativefirst.task

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class TaskLabel(
    val id: String,
    val name: String,
    val color: TaskLabelColor = TaskLabelColor.SLATE,
    val createdAt: Instant,
    val updatedAt: Instant,
    val revision: Long,
)

@Serializable
enum class TaskLabelColor {
    @SerialName("blue")
    BLUE,

    @SerialName("green")
    GREEN,

    @SerialName("orange")
    ORANGE,

    @SerialName("purple")
    PURPLE,

    @SerialName("rose")
    ROSE,

    @SerialName("slate")
    SLATE,
}
