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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.calypsan.listenup.client.design.theme.ListenUpTheme

/**
 * Multi-line text area using the theme's expressive shape system.
 *
 * Uses [MaterialTheme.shapes.medium] for consistent corner radius across the app.
 * Inherits dynamic color support from the theme.
 *
 * @param value Current text value
 * @param onValueChange Callback when text changes
 * @param label Floating label text
 * @param modifier Optional modifier
 * @param placeholder Hint text shown when empty
 * @param minLines Minimum number of visible lines
 * @param maxLines Maximum number of visible lines
 * @param enabled Whether the field is enabled for input
 * @param isError Whether to show error styling
 * @param supportingText Helper or error text below field
 * @param keyboardOptions Keyboard type and IME action configuration
 * @param keyboardActions Keyboard action handlers
 */
@Composable
fun ListenUpTextArea(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    minLines: Int = 3,
    maxLines: Int = 6,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
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
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        minLines = minLines,
        maxLines = maxLines,
        singleLine = false,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    )
}

@Preview(name = "Empty TextArea")
@Composable
private fun PreviewListenUpTextAreaEmpty() {
    ListenUpTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ListenUpTextArea(
                value = "",
                onValueChange = {},
                label = "Description",
                placeholder = "Enter a description...",
            )
        }
    }
}

@Preview(name = "With Text")
@Composable
private fun PreviewListenUpTextAreaWithText() {
    ListenUpTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ListenUpTextArea(
                value = "This is a multi-line description that spans several lines. " +
                    "It demonstrates how the text area handles longer content with proper wrapping.",
                onValueChange = {},
                label = "Description",
            )
        }
    }
}

@Preview(name = "Error State")
@Composable
private fun PreviewListenUpTextAreaError() {
    ListenUpTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ListenUpTextArea(
                value = "",
                onValueChange = {},
                label = "Description",
                isError = true,
                supportingText = "Description is required",
            )
        }
    }
}
