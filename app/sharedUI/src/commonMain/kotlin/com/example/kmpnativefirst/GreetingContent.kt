package com.example.kmpnativefirst

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

import kmpnativefirstapptemplate.app.sharedui.generated.resources.Res
import kmpnativefirstapptemplate.app.sharedui.generated.resources.collapsed
import kmpnativefirstapptemplate.app.sharedui.generated.resources.compose_multiplatform
import kmpnativefirstapptemplate.app.sharedui.generated.resources.compose_greeting
import kmpnativefirstapptemplate.app.sharedui.generated.resources.expanded
import kmpnativefirstapptemplate.app.sharedui.generated.resources.toggle_content

@Composable
fun GreetingContent(
    showContent: Boolean,
    greeting: String,
    onToggleContent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentStateDescription = stringResource(
        if (showContent) Res.string.expanded else Res.string.collapsed,
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Button(
            onClick = onToggleContent,
            modifier = Modifier.semantics {
                stateDescription = contentStateDescription
            },
        ) {
            Text(stringResource(Res.string.toggle_content))
        }
        AnimatedVisibility(showContent) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(painterResource(Res.drawable.compose_multiplatform), null)
                Text(stringResource(Res.string.compose_greeting, greeting))
            }
        }
    }
}

@Preview
@Composable
private fun GreetingContentPreview() {
    MaterialTheme {
        GreetingContent(
            showContent = true,
            greeting = "Hello from Kotlin Multiplatform",
            onToggleContent = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}
