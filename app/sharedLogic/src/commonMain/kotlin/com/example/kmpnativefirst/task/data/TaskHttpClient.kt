package com.example.kmpnativefirst.task.data

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

internal val taskJson = Json {
    encodeDefaults = true
    explicitNulls = false
    ignoreUnknownKeys = true
}

internal fun HttpClientConfig<*>.configureTaskHttpClient() {
    expectSuccess = false
    install(HttpTimeout) {
        requestTimeoutMillis = 15_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 15_000
    }
    install(ContentNegotiation) {
        json(taskJson)
    }
}

internal expect fun createPlatformTaskHttpClient(): HttpClient
