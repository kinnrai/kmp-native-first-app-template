package com.example.kmpnativefirst

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension

fun main() = application {
    val windowState = rememberWindowState(
        size = DpSize(width = 1180.dp, height = 780.dp),
    )
    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "Tasks — KMP Native First",
    ) {
        LaunchedEffect(Unit) {
            window.minimumSize = Dimension(720, 560)
        }
        DesktopApp()
    }
}
