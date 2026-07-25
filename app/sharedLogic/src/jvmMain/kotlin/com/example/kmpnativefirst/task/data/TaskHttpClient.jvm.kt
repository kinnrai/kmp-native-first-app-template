package com.example.kmpnativefirst.task.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO

internal actual fun createPlatformTaskHttpClient(): HttpClient = HttpClient(CIO) {
    configureTaskHttpClient()
}
