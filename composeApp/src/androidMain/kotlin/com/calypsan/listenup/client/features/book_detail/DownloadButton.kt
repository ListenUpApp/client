package com.calypsan.listenup.client.features.book_detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.calypsan.listenup.client.data.model.BookDownloadState
import com.calypsan.listenup.client.data.model.BookDownloadStatus

/**
 * Download button with visual state for book detail screen.
 *
 * States:
 * - Not downloaded: Download icon, tap to queue
 * - Downloading: Progress circle, tap to cancel
 * - Downloaded: Trash icon, tap to delete
 * - Failed/Partial: Retry icon, tap to resume
 */
@Composable
fun DownloadButton(
    status: BookDownloadStatus,
    onDownloadClick: () -> Unit,
    onCancelClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = MaterialTheme.colorScheme.secondaryContainer
    val contentColor = MaterialTheme.colorScheme.onSecondaryContainer

    Surface(
        modifier = modifier.size(56.dp),
        shape = MaterialTheme.shapes.medium,
        color = containerColor
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            when (status.state) {
                BookDownloadState.NOT_DOWNLOADED -> {
                    IconButton(onClick = onDownloadClick) {
                        Icon(
                            Icons.Outlined.Download,
                            contentDescription = "Download book",
                            tint = contentColor
                        )
                    }
                }

                BookDownloadState.QUEUED,
                BookDownloadState.DOWNLOADING -> {
                    IconButton(onClick = onCancelClick) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { status.progress },
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 3.dp,
                                color = contentColor,
                                trackColor = contentColor.copy(alpha = 0.3f)
                            )
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Cancel download",
                                modifier = Modifier.size(12.dp),
                                tint = contentColor.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                BookDownloadState.COMPLETED -> {
                    IconButton(onClick = onDeleteClick) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "Delete download",
                            tint = contentColor
                        )
                    }
                }

                BookDownloadState.PARTIAL,
                BookDownloadState.FAILED -> {
                    IconButton(onClick = onDownloadClick) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Retry download",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

/**
 * Expanded download button with text label for wider layouts.
 */
@Composable
fun DownloadButtonExpanded(
    status: BookDownloadStatus,
    onDownloadClick: () -> Unit,
    onCancelClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progressPercent = (status.progress * 100).toInt()

    OutlinedButton(
        onClick = when (status.state) {
            BookDownloadState.NOT_DOWNLOADED -> onDownloadClick
            BookDownloadState.QUEUED, BookDownloadState.DOWNLOADING -> onCancelClick
            BookDownloadState.COMPLETED -> onDeleteClick
            BookDownloadState.PARTIAL, BookDownloadState.FAILED -> onDownloadClick
        },
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(26.dp)
    ) {
        when (status.state) {
            BookDownloadState.NOT_DOWNLOADED -> {
                Icon(Icons.Outlined.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Download")
            }

            BookDownloadState.QUEUED -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Queued...")
            }

            BookDownloadState.DOWNLOADING -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        progress = { status.progress },
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("${progressPercent}%")
                }
            }

            BookDownloadState.COMPLETED -> {
                Icon(Icons.Outlined.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Downloaded")
            }

            BookDownloadState.PARTIAL,
            BookDownloadState.FAILED -> {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Retry", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
