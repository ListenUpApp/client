package com.calypsan.listenup.client.features.bookdetail

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
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.CircularProgressIndicator
import com.calypsan.listenup.client.design.components.ListenUpLoadingIndicatorSmall
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
import com.calypsan.listenup.client.domain.model.BookDownloadState
import com.calypsan.listenup.client.domain.model.BookDownloadStatus
import org.jetbrains.compose.resources.stringResource
import listenup.composeapp.generated.resources.Res
import listenup.composeapp.generated.resources.book_detail_cancel_download
import listenup.composeapp.generated.resources.book_detail_delete_download_2
import listenup.composeapp.generated.resources.book_detail_download
import listenup.composeapp.generated.resources.book_detail_download_book
import listenup.composeapp.generated.resources.book_detail_downloaded
import listenup.composeapp.generated.resources.book_detail_progresspercent
import listenup.composeapp.generated.resources.book_detail_queued
import listenup.composeapp.generated.resources.common_retry
import listenup.composeapp.generated.resources.book_detail_retry_download
import listenup.composeapp.generated.resources.book_detail_waiting_for_wifi

/**
 * Download button with visual state for book detail screen.
 *
 * States:
 * - Not downloaded: Download icon, tap to queue
 * - Queued (normal): Progress spinner, tap to cancel
 * - Queued (waiting for WiFi): WiFi-off icon, tap to cancel
 * - Downloading: Progress circle with %, tap to cancel
 * - Downloaded: Trash icon, tap to delete
 * - Failed/Partial: Retry icon, tap to resume
 *
 * @param isWaitingForWifi True when download is queued but waiting for WiFi connection
 *                         (wifiOnlyDownloads enabled + not on WiFi). Shows WiFi-off icon.
 */
@Composable
fun DownloadButton(
    status: BookDownloadStatus,
    onDownloadClick: () -> Unit,
    onCancelClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
    isWaitingForWifi: Boolean = false,
    enabled: Boolean = true,
) {
    val containerColor =
        if (enabled) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        }
    val contentColor =
        if (enabled) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        }

    Surface(
        modifier = modifier.size(56.dp),
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            when (status.state) {
                BookDownloadState.NOT_DOWNLOADED -> {
                    IconButton(onClick = onDownloadClick) {
                        Icon(
                            Icons.Outlined.Download,
                            contentDescription = stringResource(Res.string.book_detail_download_book),
                            tint = contentColor,
                        )
                    }
                }

                BookDownloadState.QUEUED -> {
                    IconButton(onClick = onCancelClick) {
                        if (isWaitingForWifi) {
                            // Waiting for WiFi - show WiFi-off icon
                            Icon(
                                Icons.Default.WifiOff,
                                contentDescription = stringResource(Res.string.book_detail_waiting_for_wifi),
                                tint = contentColor.copy(alpha = 0.7f),
                            )
                        } else {
                            // Normal queued state - show spinner
                            ListenUpLoadingIndicatorSmall()
                        }
                    }
                }

                BookDownloadState.DOWNLOADING -> {
                    IconButton(onClick = onCancelClick) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { status.progress },
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 3.dp,
                                color = contentColor,
                                trackColor = contentColor.copy(alpha = 0.3f),
                            )
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(Res.string.book_detail_cancel_download),
                                modifier = Modifier.size(12.dp),
                                tint = contentColor.copy(alpha = 0.6f),
                            )
                        }
                    }
                }

                BookDownloadState.COMPLETED -> {
                    IconButton(onClick = onDeleteClick) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = stringResource(Res.string.book_detail_delete_download_2),
                            tint = contentColor,
                        )
                    }
                }

                BookDownloadState.PARTIAL,
                BookDownloadState.FAILED,
                -> {
                    IconButton(onClick = onDownloadClick) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(Res.string.book_detail_retry_download),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Expanded download button with text label for wider layouts.
 *
 * @param isWaitingForWifi True when download is queued but waiting for WiFi connection
 *                         (wifiOnlyDownloads enabled + not on WiFi). Shows "Waiting for WiFi".
 */
@Composable
fun DownloadButtonExpanded(
    status: BookDownloadStatus,
    onDownloadClick: () -> Unit,
    onCancelClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
    isWaitingForWifi: Boolean = false,
) {
    val progressPercent = (status.progress * 100).toInt()

    OutlinedButton(
        onClick =
            when (status.state) {
                BookDownloadState.NOT_DOWNLOADED -> onDownloadClick
                BookDownloadState.QUEUED, BookDownloadState.DOWNLOADING -> onCancelClick
                BookDownloadState.COMPLETED -> onDeleteClick
                BookDownloadState.PARTIAL, BookDownloadState.FAILED -> onDownloadClick
            },
        modifier =
            modifier
                .fillMaxWidth()
                .height(52.dp),
        shape = RoundedCornerShape(26.dp),
    ) {
        when (status.state) {
            BookDownloadState.NOT_DOWNLOADED -> {
                Icon(Icons.Outlined.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(Res.string.book_detail_download))
            }

            BookDownloadState.QUEUED -> {
                if (isWaitingForWifi) {
                    // Waiting for WiFi - show WiFi-off icon with message
                    Icon(
                        Icons.Default.WifiOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(Res.string.book_detail_waiting_for_wifi))
                } else {
                    // Normal queued state
                    ListenUpLoadingIndicatorSmall()
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(Res.string.book_detail_queued))
                }
            }

            BookDownloadState.DOWNLOADING -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        progress = { status.progress },
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(Res.string.book_detail_progresspercent, progressPercent))
                }
            }

            BookDownloadState.COMPLETED -> {
                Icon(Icons.Outlined.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(Res.string.book_detail_downloaded))
            }

            BookDownloadState.PARTIAL,
            BookDownloadState.FAILED,
            -> {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(Res.string.common_retry), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
