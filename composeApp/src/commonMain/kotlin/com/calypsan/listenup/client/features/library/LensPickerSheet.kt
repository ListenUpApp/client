package com.calypsan.listenup.client.features.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import com.calypsan.listenup.client.design.components.ListenUpLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.calypsan.listenup.client.design.util.parseHexColor
import com.calypsan.listenup.client.design.components.ListenUpTextField
import com.calypsan.listenup.client.domain.model.Lens

/**
 * Bottom sheet for selecting a lens to add books to.
 *
 * Used in the library multi-select flow for users to add selected books
 * to one of their personal lenses. Also supports creating a new lens inline.
 *
 * @param lenses List of user's lenses
 * @param selectedBookCount Number of books that will be added
 * @param onLensSelected Called when a lens is tapped
 * @param onCreateAndAddToLens Called to create a new lens and add books to it
 * @param onDismiss Called when the sheet is dismissed
 * @param isLoading Whether an add operation is in progress
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LensPickerSheet(
    lenses: List<Lens>,
    selectedBookCount: Int,
    onLensSelected: (String) -> Unit,
    onCreateAndAddToLens: (name: String) -> Unit,
    onDismiss: () -> Unit,
    isLoading: Boolean = false,
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = {
            // Standard drag handle with proper spacing
            Surface(
                modifier =
                    Modifier
                        .padding(vertical = 12.dp)
                        .width(32.dp)
                        .height(4.dp),
                shape = RoundedCornerShape(2.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            ) {}
        },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
        ) {
            // Header
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "Add to Lens",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text =
                        if (selectedBookCount == 1) {
                            "1 book selected"
                        } else {
                            "$selectedBookCount books selected"
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider()

            // Content
            Box(
                modifier = Modifier.weight(1f, fill = false),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // Create new lens option (always shown first)
                    item(key = "create_new") {
                        CreateNewLensRow(
                            onClick = { showCreateDialog = true },
                            enabled = !isLoading,
                        )
                        if (lenses.isNotEmpty()) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 24.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }

                    // Existing lenses
                    if (lenses.isEmpty()) {
                        item(key = "empty_hint") {
                            Text(
                                text = "You don't have any lenses yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                            )
                        }
                    } else {
                        items(
                            items = lenses,
                            key = { it.id },
                        ) { lens ->
                            LensRow(
                                lens = lens,
                                onClick = { onLensSelected(lens.id) },
                                enabled = !isLoading,
                            )
                        }
                    }
                }

                // Loading overlay
                if (isLoading) {
                    Surface(
                        modifier = Modifier.matchParentSize(),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                        ) {
                            ListenUpLoadingIndicator()
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // Create lens dialog
    if (showCreateDialog) {
        CreateLensDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                showCreateDialog = false
                onCreateAndAddToLens(name)
            },
        )
    }
}

/**
 * Row for creating a new lens.
 */
@Composable
private fun CreateNewLensRow(
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = Color.Transparent,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Plus icon
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(Modifier.width(16.dp))

            Text(
                text = "Create New Lens",
                style = MaterialTheme.typography.bodyLarge,
                color =
                    if (enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    },
            )
        }
    }
}

/**
 * A single lens row in the picker.
 */
@Composable
private fun LensRow(
    lens: Lens,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    // Parse the owner's avatar color for the lens icon background
    val iconColor =
        remember(lens.ownerAvatarColor) {
            parseHexColor(lens.ownerAvatarColor)
        }

    Surface(
        onClick = onClick,
        enabled = enabled,
        color = Color.Transparent,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Lens icon with avatar color background
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = lens.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color =
                        if (enabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text =
                        if (lens.bookCount == 1) {
                            "1 book"
                        } else {
                            "${lens.bookCount} books"
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Dialog for creating a new lens.
 */
@Composable
private fun CreateLensDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String) -> Unit,
) {
    var lensName by remember { mutableStateOf("") }
    val isValid = lensName.isNotBlank()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        // Short delay to allow dialog to fully render before requesting focus
        kotlinx.coroutines.delay(100)
        focusRequester.requestFocus()
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text("Create New Lens") },
        text = {
            ListenUpTextField(
                value = lensName,
                onValueChange = { lensName = it },
                label = "Lens name",
                placeholder = "e.g., To Read, Favorites",
                modifier = Modifier.focusRequester(focusRequester),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(lensName.trim()) },
                enabled = isValid,
            ) {
                Text("Create & Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

