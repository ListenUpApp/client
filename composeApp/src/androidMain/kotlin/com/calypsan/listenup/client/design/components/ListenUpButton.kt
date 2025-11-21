package com.calypsan.listenup.client.design.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.calypsan.listenup.client.design.theme.ListenUpTheme

/**
 * Material 3 primary button with expressive pill shape and animated states.
 *
 * Uses [CircleShape] for the fully-rounded M3 Expressive look.
 * Loading state transitions smoothly with crossfade animation.
 *
 * @param text Button text
 * @param onClick Callback when button is clicked
 * @param modifier Optional modifier
 * @param enabled Whether button is interactive
 * @param isLoading Whether to show loading spinner (animates transition)
 */
@Composable
fun ListenUpButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        shape = CircleShape,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        AnimatedContent(
            targetState = isLoading,
            transitionSpec = {
                fadeIn() togetherWith fadeOut() using SizeTransform(clip = false)
            },
            label = "ButtonContent"
        ) { loading ->
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Preview(name = "Normal State")
@Composable
private fun PreviewListenUpButtonNormal() {
    ListenUpTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ListenUpButton(
                text = "Connect to Server",
                onClick = {}
            )
        }
    }
}

@Preview(name = "Disabled State")
@Composable
private fun PreviewListenUpButtonDisabled() {
    ListenUpTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ListenUpButton(
                text = "Connect to Server",
                onClick = {},
                enabled = false
            )
        }
    }
}

@Preview(name = "Loading State")
@Composable
private fun PreviewListenUpButtonLoading() {
    ListenUpTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ListenUpButton(
                text = "Connect to Server",
                onClick = {},
                isLoading = true
            )
        }
    }
}

@Preview(name = "All States")
@Composable
private fun PreviewListenUpButtonStates() {
    ListenUpTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ListenUpButton(
                text = "Normal",
                onClick = {}
            )

            ListenUpButton(
                text = "Disabled",
                onClick = {},
                enabled = false
            )

            ListenUpButton(
                text = "Loading",
                onClick = {},
                isLoading = true
            )
        }
    }
}
