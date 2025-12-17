@file:Suppress("StringLiteralDuplication")

package com.calypsan.listenup.client.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.calypsan.listenup.client.design.theme.ListenUpTheme

/**
 * Material 3 text field using the theme's expressive shape system.
 *
 * Uses [MaterialTheme.shapes.medium] for consistent corner radius across the app.
 * Inherits dynamic color support from the theme.
 *
 * @param value Current text value
 * @param onValueChange Callback when text changes
 * @param label Floating label text
 * @param modifier Optional modifier
 * @param placeholder Hint text shown when empty
 * @param enabled Whether the field is enabled for input
 * @param isError Whether to show error styling
 * @param supportingText Helper or error text below field
 * @param visualTransformation Visual transformation applied to text (e.g., password masking)
 * @param keyboardOptions Keyboard type and IME action configuration
 * @param keyboardActions Keyboard action handlers
 */
@Composable
fun ListenUpTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        enabled = enabled,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    )
}

@Preview(name = "Empty Field")
@Composable
private fun PreviewListenUpTextFieldEmpty() {
    ListenUpTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ListenUpTextField(
                value = "",
                onValueChange = {},
                label = "Server URL",
                placeholder = "https://example.com",
            )
        }
    }
}

@Preview(name = "With Text")
@Composable
private fun PreviewListenUpTextFieldWithText() {
    ListenUpTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ListenUpTextField(
                value = "https://listenup.example.com",
                onValueChange = {},
                label = "Server URL",
                supportingText = "Enter your ListenUp server address",
            )
        }
    }
}

@Preview(name = "Error State")
@Composable
private fun PreviewListenUpTextFieldError() {
    ListenUpTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ListenUpTextField(
                value = "invalid-url",
                onValueChange = {},
                label = "Server URL",
                isError = true,
                supportingText = "Invalid URL format. Use https://example.com",
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
            )
        }
    }
}

@Preview(name = "Multiple States")
@Composable
private fun PreviewListenUpTextFieldStates() {
    ListenUpTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ListenUpTextField(
                value = "",
                onValueChange = {},
                label = "Empty",
            )

            ListenUpTextField(
                value = "https://listenup.example.com",
                onValueChange = {},
                label = "Filled",
                supportingText = "Valid URL",
            )

            ListenUpTextField(
                value = "invalid",
                onValueChange = {},
                label = "Error",
                isError = true,
                supportingText = "Invalid URL format",
            )
        }
    }
}
