package com.calypsan.listenup.client.features.bookdetail

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import com.calypsan.listenup.client.design.components.ListenUpDestructiveDialog

/**
 * Confirmation dialog for deleting downloaded audiobook files.
 */
@Composable
fun DeleteDownloadDialog(
    bookTitle: String,
    downloadSize: Long,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ListenUpDestructiveDialog(
        onDismissRequest = onDismiss,
        title = "Delete Download?",
        text =
            "Remove the downloaded files for \"$bookTitle\"? " +
                "This will free up ${formatFileSize(downloadSize)}. " +
                "You can re-download anytime by playing the book.",
        confirmText = "Delete",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        icon = Icons.Default.Delete,
    )
}

/**
 * Format bytes to human-readable size.
 */
@Suppress("MagicNumber")
fun formatFileSize(bytes: Long): String =
    when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${(bytes / (1024.0 * 1024.0) * 10).toInt() / 10.0} MB"
        else -> "${(bytes / (1024.0 * 1024.0 * 1024.0) * 100).toInt() / 100.0} GB"
    }
