package com.example.kmpnativefirst.task

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class TaskProject(
    val id: String,
    val name: String,
    val color: TaskProjectColor = TaskProjectColor.BLUE,
    val createdAt: Instant,
    val updatedAt: Instant,
    val revision: Long,
)

@Serializable
enum class TaskProjectColor {
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
