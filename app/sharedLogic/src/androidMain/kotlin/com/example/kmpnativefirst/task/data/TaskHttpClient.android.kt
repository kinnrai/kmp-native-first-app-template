package com.example.kmpnativefirst.task.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

internal actual fun createPlatformTaskHttpClient(): HttpClient = HttpClient(OkHttp) {
    configureTaskHttpClient()
}
