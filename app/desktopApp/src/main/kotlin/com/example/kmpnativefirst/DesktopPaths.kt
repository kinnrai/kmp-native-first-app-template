package com.example.kmpnativefirst

import java.io.File

internal fun desktopDatabasePath(
    osName: String = System.getProperty("os.name"),
    userHome: String = System.getProperty("user.home"),
    environment: Map<String, String> = System.getenv(),
): String {
    val dataDirectory = when {
        osName.startsWith("Windows", ignoreCase = true) -> {
            File(environment["LOCALAPPDATA"] ?: userHome, APPLICATION_DIRECTORY)
        }

        osName.startsWith("Mac", ignoreCase = true) -> {
            File(userHome, "Library/Application Support/$APPLICATION_DIRECTORY")
        }

        else -> {
            val dataHome = environment["XDG_DATA_HOME"]
                ?.takeIf(String::isNotBlank)
                ?.let(::File)
                ?: File(userHome, ".local/share")
            File(dataHome, APPLICATION_DIRECTORY)
        }
    }
    return File(dataDirectory, DATABASE_FILE_NAME).path
}

private const val APPLICATION_DIRECTORY = "KmpNativeFirst"
private const val DATABASE_FILE_NAME = "tasks.db"
