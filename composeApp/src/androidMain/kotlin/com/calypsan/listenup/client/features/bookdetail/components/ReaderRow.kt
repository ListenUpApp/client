@file:Suppress("MagicNumber")

package com.calypsan.listenup.client.features.bookdetail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.calypsan.listenup.client.data.remote.ReaderSummary
import com.calypsan.listenup.client.design.components.getInitials
import com.calypsan.listenup.client.util.toRelativeOrMonthYear

/**
 * Row displaying another reader's status with a book.
 *
 * Shows:
 * - Avatar with generated color and initials
 * - Display name
 * - Reading status (currently reading or finished)
 * - Progress percentage if currently reading
 * - Completion count if multiple completions
 */
@Composable
fun ReaderRow(
    reader: ReaderSummary,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        // Avatar with generated color
        // Note: avatarColor is a hex string like "#FF5722"
        val avatarColorParsed =
            try {
                Color(android.graphics.Color.parseColor(reader.avatarColor))
            } catch (e: Exception) {
                MaterialTheme.colorScheme.primaryContainer
            }

        Surface(
            shape = CircleShape,
            color = avatarColorParsed,
            modifier = Modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = getInitials(reader.displayName),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Reader info
        Column(modifier = Modifier.weight(1f)) {
            // Display name
            Text(
                text = reader.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            // Status line
            val statusText =
                buildString {
                    if (reader.isCurrentlyReading) {
                        append("is currently reading")
                    } else {
                        val timeRef = reader.finishedAt ?: reader.startedAt
                        append("finished ${timeRef.toRelativeOrMonthYear()}")
                    }
                }

            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Additional info line: progress or completions
            val additionalText =
                buildString {
                    if (reader.isCurrentlyReading && reader.currentProgress > 0) {
                        val progressPercent = (reader.currentProgress * 100).toInt()
                        append("$progressPercent% complete")
                    } else if (reader.completionCount > 1) {
                        append("Read ${reader.completionCount} times")
                    }
                }

            if (additionalText.isNotEmpty()) {
                Text(
                    text = additionalText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
