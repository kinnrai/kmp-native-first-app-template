package com.example.kmpnativefirst

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
fun AndroidApp() {
    var showContent by rememberSaveable { mutableStateOf(false) }
    val greeting = remember { Greeting().greet() }

    AndroidTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            GreetingContent(
                showContent = showContent,
                greeting = greeting,
                onToggleContent = { showContent = !showContent },
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding(),
            )
        }
    }
}
