package com.example.kmpnativefirst

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopPathsTest {
    @Test
    fun usesLocalAppDataOnWindows() {
        assertEquals(
            File("/local", "KmpNativeFirst/tasks.db").path,
            desktopDatabasePath(
                osName = "Windows 11",
                userHome = "/home",
                environment = mapOf("LOCALAPPDATA" to "/local"),
            ),
        )
    }

    @Test
    fun usesXdgDataHomeOnLinux() {
        assertEquals(
            File("/data", "KmpNativeFirst/tasks.db").path,
            desktopDatabasePath(
                osName = "Linux",
                userHome = "/home",
                environment = mapOf("XDG_DATA_HOME" to "/data"),
            ),
        )
    }

    @Test
    fun fallsBackToThePlatformUserDataDirectory() {
        assertEquals(
            File("/home/Library/Application Support", "KmpNativeFirst/tasks.db").path,
            desktopDatabasePath(
                osName = "Mac OS X",
                userHome = "/home",
                environment = emptyMap(),
            ),
        )
        assertEquals(
            File("/home/.local/share", "KmpNativeFirst/tasks.db").path,
            desktopDatabasePath(
                osName = "FreeBSD",
                userHome = "/home",
                environment = emptyMap(),
            ),
        )
    }
}
