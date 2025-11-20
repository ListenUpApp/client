package com.calypsan.listenup.client.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.calypsan.listenup.client.design.theme.ListenUpTheme

/**
 * Material 3 Expressive primary button with ListenUp theming.
 *
 * Features:
 * - 56.dp height for accessible touch target
 * - Full width by default (mobile-first design)
 * - Loading state with spinner
 * - 20.dp corner radius (medium shape)
 *
 * @param text Button text
 * @param onClick Callback when button is clicked
 * @param modifier Optional modifier
 * @param enabled Whether button is interactive
 * @param isLoading Whether to show loading spinner instead of text
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
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
        } else {
            Text(text)
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
