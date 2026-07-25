package com.example.kmpnativefirst.task.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

internal actual fun createPlatformTaskHttpClient(): HttpClient = HttpClient(Darwin) {
    configureTaskHttpClient()
}
