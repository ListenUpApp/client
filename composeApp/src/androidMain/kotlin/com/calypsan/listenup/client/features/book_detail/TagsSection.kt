package com.calypsan.listenup.client.features.book_detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.calypsan.listenup.client.domain.model.Tag

/**
 * Read-only section displaying tags for a book.
 *
 * Tags are displayed as simple chips without edit capability.
 * Editing tags will be available on the Edit Book page.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagsSection(
    tags: List<Tag>,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    if (tags.isEmpty() && !isLoading) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.LocalOffer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Tags",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // Tags
        if (tags.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tags.forEach { tag ->
                    TagChip(tag = tag)
                }
            }
        }
    }
}

/**
 * A read-only tag chip.
 *
 * Uses the tag's color if available, otherwise falls back to theme colors.
 */
@Composable
private fun TagChip(
    tag: Tag,
    modifier: Modifier = Modifier
) {
    // Parse tag color if available
    val tagColor = tag.color?.let { hex ->
        try {
            Color(android.graphics.Color.parseColor(hex))
        } catch (e: Exception) {
            null
        }
    }

    Text(
        text = tag.name,
        style = MaterialTheme.typography.labelMedium,
        color = tagColor ?: MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (tagColor != null) {
                    Modifier.background(tagColor.copy(alpha = 0.15f))
                } else {
                    Modifier
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                }
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}
