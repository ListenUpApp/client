package com.calypsan.listenup.client.features.library.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Toolbar shown when in multi-select mode.
 * Displays the selected count and actions for the selection.
 *
 * Actions:
 * - "Add to Lens" - available to all users
 * - "Add to Collection" - available to admin users only
 *
 * @param selectedCount Number of currently selected books
 * @param onAddToLens Called when "Add to Lens" is tapped
 * @param onAddToCollection Called when "Add to Collection" is tapped (null = hide button)
 * @param onClose Called when the close button is tapped
 * @param modifier Optional modifier
 */
@Composable
fun SelectionToolbar(
    selectedCount: Int,
    onAddToLens: () -> Unit,
    onAddToCollection: (() -> Unit)?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Close button
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Exit selection mode",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            // Selected count
            Text(
                text = if (selectedCount == 1) {
                    "1 selected"
                } else {
                    "$selectedCount selected"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            Spacer(modifier = Modifier.weight(1f))

            // Add to lens button (all users)
            TextButton(
                onClick = onAddToLens,
                enabled = selectedCount > 0,
            ) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (selectedCount > 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Lens",
                    color = if (selectedCount > 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            // Add to collection button (admin only)
            if (onAddToCollection != null) {
                TextButton(
                    onClick = onAddToCollection,
                    enabled = selectedCount > 0,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (selectedCount > 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Collection",
                        color = if (selectedCount > 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}
