package com.calypsan.listenup.client.features.discover.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calypsan.listenup.client.data.remote.LeaderboardEntryResponse

/**
 * Leaderboard list showing ranked users.
 *
 * @param entries List of leaderboard entries
 * @param modifier Modifier from parent
 */
@Composable
fun LeaderboardList(
    entries: List<LeaderboardEntryResponse>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        entries.forEach { entry ->
            LeaderboardEntry(entry = entry)
        }
    }
}

@Composable
private fun LeaderboardEntry(
    entry: LeaderboardEntryResponse,
    modifier: Modifier = Modifier,
) {
    val backgroundColor =
        if (entry.isCurrentUser) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(backgroundColor)
                .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Rank badge
        RankBadge(rank = entry.rank)

        Spacer(Modifier.width(12.dp))

        // User info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (entry.isCurrentUser) FontWeight.Bold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        // Value
        Text(
            text = entry.valueLabel,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color =
                if (entry.isCurrentUser) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
        )
    }
}

@Composable
private fun RankBadge(
    rank: Int,
    modifier: Modifier = Modifier,
) {
    val (backgroundColor, textColor, emoji) =
        when (rank) {
            1 -> {
                Triple(
                    MaterialTheme.colorScheme.tertiaryContainer,
                    MaterialTheme.colorScheme.onTertiaryContainer,
                    "\uD83E\uDD47", // Gold medal
                )
            }

            2 -> {
                Triple(
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.onSecondaryContainer,
                    "\uD83E\uDD48", // Silver medal
                )
            }

            3 -> {
                Triple(
                    MaterialTheme.colorScheme.surfaceContainerHighest,
                    MaterialTheme.colorScheme.onSurface,
                    "\uD83E\uDD49", // Bronze medal
                )
            }

            else -> {
                Triple(
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    null,
                )
            }
        }

    if (emoji != null) {
        Text(
            text = emoji,
            fontSize = 20.sp,
            modifier = modifier.size(32.dp),
        )
    } else {
        androidx.compose.foundation.layout.Box(
            modifier =
                modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(backgroundColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = rank.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = textColor,
            )
        }
    }
}
