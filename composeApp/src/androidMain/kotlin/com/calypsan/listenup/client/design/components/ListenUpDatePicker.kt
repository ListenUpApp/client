@file:Suppress("StringLiteralDuplication")

package com.calypsan.listenup.client.design.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.calypsan.listenup.client.design.theme.ListenUpTheme
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/**
 * Material 3 date picker field with calendar dialog.
 *
 * Displays a read-only text field that opens a date picker dialog when tapped.
 * Stores dates in ISO 8601 format (YYYY-MM-DD) and displays them in a localized format.
 *
 * @param value Current date value in ISO 8601 format (YYYY-MM-DD), or empty string
 * @param onValueChange Callback with the selected date in ISO 8601 format
 * @param label Floating label text
 * @param modifier Optional modifier
 * @param placeholder Hint text shown when empty
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListenUpDatePicker(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
) {
    var showDialog by remember { mutableStateOf(false) }

    // Parse the current value to display and for initial picker state
    val currentDate = parseIsoDate(value)
    val displayValue = currentDate?.let { formatForDisplay(it) } ?: ""

    // Initial selection for the date picker (in millis since epoch)
    val initialSelectionMillis =
        currentDate?.let {
            it.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        }

    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = displayValue,
            onValueChange = { }, // Read-only, changes come from picker
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it) } },
            readOnly = true,
            singleLine = true,
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                if (value.isNotEmpty()) {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear date",
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = "Select date",
                    )
                }
            },
        )
        // Invisible overlay to intercept clicks and open dialog
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null, // No ripple, the text field handles that
                    ) { showDialog = true },
        )
    }

    if (showDialog) {
        val datePickerState =
            rememberDatePickerState(
                initialSelectedDateMillis = initialSelectionMillis,
            )

        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val selectedDate = epochMillisToLocalDate(millis)
                            onValueChange(formatIsoDate(selectedDate))
                        }
                        showDialog = false
                    },
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(28.dp), // Standard M3 dialog shape
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/**
 * Convert epoch milliseconds (UTC) to LocalDate.
 */
private fun epochMillisToLocalDate(millis: Long): LocalDate =
    Instant
        .fromEpochMilliseconds(millis)
        .toLocalDateTime(TimeZone.UTC)
        .date

/**
 * Parse an ISO 8601 date string (YYYY-MM-DD) to LocalDate.
 * Returns null if the string is empty or invalid.
 */
private fun parseIsoDate(isoDate: String): LocalDate? {
    if (isoDate.isBlank()) return null
    return try {
        LocalDate.parse(isoDate)
    } catch (e: IllegalArgumentException) {
        null
    }
}

/**
 * Format a LocalDate to ISO 8601 format (YYYY-MM-DD).
 */
private fun formatIsoDate(date: LocalDate): String = date.toString()

// Month names for display formatting
private val monthNames = arrayOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
)

/**
 * Format a LocalDate for user-friendly display.
 * Uses a readable format like "September 21, 1947".
 */
@Suppress("DEPRECATION")
private fun formatForDisplay(date: LocalDate): String {
    val monthName = monthNames[date.monthNumber - 1]
    return "$monthName ${date.dayOfMonth}, ${date.year}"
}

@Preview(name = "Empty Date Picker")
@Composable
private fun PreviewListenUpDatePickerEmpty() {
    ListenUpTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ListenUpDatePicker(
                value = "",
                onValueChange = {},
                label = "Birth Date",
                placeholder = "Select a date",
            )
        }
    }
}

@Preview(name = "With Date Selected")
@Composable
private fun PreviewListenUpDatePickerWithDate() {
    ListenUpTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ListenUpDatePicker(
                value = "1947-09-21",
                onValueChange = {},
                label = "Birth Date",
            )
        }
    }
}

@Preview(name = "Multiple States")
@Composable
private fun PreviewListenUpDatePickerStates() {
    ListenUpTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ListenUpDatePicker(
                value = "",
                onValueChange = {},
                label = "Birth Date",
                placeholder = "Select birth date",
            )

            ListenUpDatePicker(
                value = "1947-09-21",
                onValueChange = {},
                label = "Birth Date",
            )

            ListenUpDatePicker(
                value = "2024-01-15",
                onValueChange = {},
                label = "Death Date",
            )
        }
    }
}
