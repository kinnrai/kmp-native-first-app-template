package com.example.kmpnativefirst

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform