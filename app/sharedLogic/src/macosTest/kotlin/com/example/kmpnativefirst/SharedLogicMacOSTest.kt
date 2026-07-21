package com.example.kmpnativefirst

import kotlin.test.Test
import kotlin.test.assertContains

class SharedLogicMacOSTest {

    @Test
    fun greetingUsesMacOSPlatform() {
        assertContains(Greeting().greet(), "macOS")
    }
}
