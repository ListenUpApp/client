package com.calypsan.listenup.client.features.bookdetail.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/**
 * Dropdown menu for book actions.
 * Shows Edit, Find Metadata, Mark Complete, Add to Collection.
 * Delete is shown only for admin users.
 *
 * @param expanded Whether the menu is currently showing
 * @param onDismiss Called when the menu should be dismissed
 * @param isComplete Whether the book is marked as complete
 * @param isAdmin Whether the current user is an admin
 * @param onEditClick Called when Edit Book is clicked
 * @param onFindMetadataClick Called when Find Metadata is clicked
 * @param onMarkCompleteClick Called when Mark as Complete/Not Started is clicked
 * @param onAddToCollectionClick Called when Add to Collection is clicked
 * @param onDeleteClick Called when Delete Book is clicked (admin only)
 */
@Suppress("LongParameterList")
@Composable
fun BookActionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    isComplete: Boolean,
    isAdmin: Boolean,
    onEditClick: () -> Unit,
    onFindMetadataClick: () -> Unit,
    onMarkCompleteClick: () -> Unit,
    onAddToCollectionClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        // Edit Book
        DropdownMenuItem(
            text = { Text("Edit Book") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                )
            },
            onClick = onEditClick,
        )

        // Find Metadata
        DropdownMenuItem(
            text = { Text("Find Metadata") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                )
            },
            onClick = onFindMetadataClick,
        )

        HorizontalDivider()

        // Mark as Complete / Not Started (stubbed for now)
        DropdownMenuItem(
            text = {
                Text(
                    text = if (isComplete) "Mark as Not Started" else "Mark as Complete",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = if (isComplete) {
                        Icons.Default.RadioButtonUnchecked
                    } else {
                        Icons.Default.CheckCircle
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            },
            onClick = onMarkCompleteClick,
            enabled = false, // Stubbed for now
        )

        // Add to Collection (stubbed for now)
        DropdownMenuItem(
            text = {
                Text(
                    text = "Add to Collection",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            },
            onClick = onAddToCollectionClick,
            enabled = false, // Stubbed for now
        )

        // Delete Book (admin only, stubbed for now)
        if (isAdmin) {
            HorizontalDivider()

            DropdownMenuItem(
                text = {
                    Text(
                        text = "Delete Book",
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                    )
                },
                colors = MenuDefaults.itemColors(
                    textColor = MaterialTheme.colorScheme.error,
                    leadingIconColor = MaterialTheme.colorScheme.error,
                ),
                onClick = onDeleteClick,
                enabled = false, // Stubbed for now
            )
        }
    }
}
