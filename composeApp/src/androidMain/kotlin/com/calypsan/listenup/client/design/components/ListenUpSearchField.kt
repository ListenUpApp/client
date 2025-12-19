@file:Suppress("StringLiteralDuplication")

package com.calypsan.listenup.client.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
 * Search text field with leading search icon and trailing clear/loading indicator.
 *
 * Uses [MaterialTheme.shapes.medium] for consistent corner radius.
 * Handles Enter key for search submission.
 *
 * @param value Current search text
 * @param onValueChange Callback when text changes
 * @param onSubmit Callback when Enter key is pressed
 * @param placeholder Hint text shown when empty
 * @param modifier Optional modifier
 * @param enabled Whether the field is enabled for input
 * @param isLoading Whether to show loading indicator instead of clear button
 * @param onClear Callback when clear button is clicked (if null, no clear button shown)
 */
@Composable
fun ListenUpSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    onClear: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
        enabled = enabled,
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = {
            when {
                isLoading -> {
                    ListenUpLoadingIndicator(size = 20.dp)
                }

                value.isNotEmpty() && onClear != null -> {
                    IconButton(onClick = onClear) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear search",
                        )
                    }
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        modifier = modifier.fillMaxWidth(),
    )
}

@Preview(name = "Empty Search Field")
@Composable
private fun PreviewListenUpSearchFieldEmpty() {
    ListenUpTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ListenUpSearchField(
                value = "",
                onValueChange = {},
                onSubmit = {},
                placeholder = "Search contributors...",
            )
        }
    }
}

@Preview(name = "With Text")
@Composable
private fun PreviewListenUpSearchFieldWithText() {
    ListenUpTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ListenUpSearchField(
                value = "Stephen",
                onValueChange = {},
                onSubmit = {},
                placeholder = "Search contributors...",
                onClear = {},
            )
        }
    }
}

@Preview(name = "Loading State")
@Composable
private fun PreviewListenUpSearchFieldLoading() {
    ListenUpTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ListenUpSearchField(
                value = "Stephen",
                onValueChange = {},
                onSubmit = {},
                placeholder = "Search contributors...",
                isLoading = true,
            )
        }
    }
}

@Preview(name = "Disabled State")
@Composable
private fun PreviewListenUpSearchFieldDisabled() {
    ListenUpTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ListenUpSearchField(
                value = "",
                onValueChange = {},
                onSubmit = {},
                placeholder = "Search contributors...",
                enabled = false,
            )
        }
    }
}
