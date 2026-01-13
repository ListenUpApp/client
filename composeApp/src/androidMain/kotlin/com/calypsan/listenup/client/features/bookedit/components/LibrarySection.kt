package com.calypsan.listenup.client.features.bookedit.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Library information section: Date Added.
 *
 * Contains fields related to the book's presence in the library,
 * distinct from publishing metadata or identifiers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrarySection(
    addedAt: Long?,
    onAddedAtChange: (Long?) -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Date Added picker
        DatePickerField(
            label = "Date Added",
            value = addedAt,
            onClick = { showDatePicker = true },
        )
    }

    if (showDatePicker) {
        val datePickerState =
            rememberDatePickerState(
                initialSelectedDateMillis = addedAt,
            )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { onAddedAtChange(it) }
                        showDatePicker = false
                    },
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            },
            shape = MaterialTheme.shapes.large,
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/**
 * Read-only date field that opens a date picker when clicked.
 * Styled consistently with [ListenUpTextField] using OutlinedTextField.
 */
@Composable
private fun DatePickerField(
    label: String,
    value: Long?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Handle clicks via interaction source since readOnly fields don't propagate onClick
    val interactionSource = remember { MutableInteractionSource() }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release) {
                onClick()
            }
        }
    }

    OutlinedTextField(
        value = value?.let { formatDate(it) } ?: "",
        onValueChange = {},
        label = { Text(label) },
        placeholder = { Text("Not set") },
        readOnly = true,
        singleLine = true,
        trailingIcon = {
            Icon(
                imageVector = Icons.Default.CalendarMonth,
                contentDescription = "Pick date",
            )
        },
        interactionSource = interactionSource,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * Format epoch milliseconds to a readable date string.
 */
private fun formatDate(epochMillis: Long): String {
    val formatter = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
    return formatter.format(Date(epochMillis))
}
