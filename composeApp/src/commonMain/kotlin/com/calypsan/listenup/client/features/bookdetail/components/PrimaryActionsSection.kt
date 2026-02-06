package com.calypsan.listenup.client.features.bookdetail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.calypsan.listenup.client.domain.model.BookDownloadStatus
import com.calypsan.listenup.client.features.bookdetail.DownloadButton

/**
 * Primary action buttons - Play dominates, Download alongside.
 *
 * @param isWaitingForWifi True when download is queued but waiting for WiFi connection.
 *                         Passed to DownloadButton to show "Waiting for WiFi" state.
 */
@Composable
fun PrimaryActionsSection(
    downloadStatus: BookDownloadStatus,
    onPlayClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onCancelClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
    isWaitingForWifi: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Primary Play Button
        Button(
            onClick = onPlayClick,
            modifier =
                Modifier
                    .weight(1f)
                    .height(64.dp),
            shape = RoundedCornerShape(32.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            elevation =
                ButtonDefaults.buttonElevation(
                    defaultElevation = 4.dp,
                    pressedElevation = 8.dp,
                ),
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Play",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // Download Button - icon-only square
        DownloadButton(
            status = downloadStatus,
            onDownloadClick = onDownloadClick,
            onCancelClick = onCancelClick,
            onDeleteClick = onDeleteClick,
            modifier = Modifier.size(64.dp),
            isWaitingForWifi = isWaitingForWifi,
        )
    }
}
