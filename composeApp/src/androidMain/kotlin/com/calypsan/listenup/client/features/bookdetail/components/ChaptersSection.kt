package com.calypsan.listenup.client.features.bookdetail.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.calypsan.listenup.client.design.theme.GoogleSansDisplay
import com.calypsan.listenup.client.presentation.bookdetail.ChapterUiModel

/**
 * Chapter list header showing chapter count.
 */
@Composable
fun ChaptersHeader(
    chapterCount: Int,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "Chapters ($chapterCount)",
        style =
            MaterialTheme.typography.titleMedium.copy(
                fontFamily = GoogleSansDisplay,
                fontWeight = FontWeight.Bold,
            ),
        modifier = modifier,
    )
}

/**
 * Individual chapter list item showing title and duration.
 */
@Composable
fun ChapterListItem(chapter: ChapterUiModel) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = chapter.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = chapter.duration,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
