package com.example.kmpnativefirst.task.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js

internal actual fun createPlatformTaskHttpClient(): HttpClient = HttpClient(Js) {
    configureTaskHttpClient()
}
