@file:Suppress("MagicNumber")

package com.calypsan.listenup.client.features.discover.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calypsan.listenup.client.data.remote.ActivityResponse
import com.calypsan.listenup.client.presentation.discover.ActivityFeedViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Activity Feed section for the Discover screen.
 *
 * Displays recent community activities:
 * - Started/finished books
 * - Streak milestones
 * - Listening hour milestones
 * - Created lenses
 *
 * @param onBookClick Callback when a book is clicked
 * @param onLensClick Callback when a lens is clicked
 * @param modifier Modifier from parent
 * @param viewModel ActivityFeedViewModel injected via Koin
 */
@Composable
fun ActivityFeedSection(
    onBookClick: (String) -> Unit,
    onLensClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ActivityFeedViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header
            Text(
                text = "Activity Feed",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }

                state.error != null -> {
                    Text(
                        text = state.error ?: "Unknown error",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                state.isEmpty -> {
                    Text(
                        text = "No activity yet. Start listening to see what your community is reading!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                state.hasData -> {
                    // Show activities
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.activities.take(5).forEach { activity ->
                            ActivityItem(
                                activity = activity,
                                onBookClick = onBookClick,
                                onLensClick = onLensClick,
                            )
                        }
                    }

                    // Load more button
                    if (state.hasMore) {
                        TextButton(
                            onClick = { viewModel.loadMore() },
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) {
                            if (state.isLoadingMore) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text("Load more")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Single activity item in the feed.
 */
@Composable
private fun ActivityItem(
    activity: ActivityResponse,
    onBookClick: (String) -> Unit,
    onLensClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val avatarColor = remember(activity.userAvatarColor) {
        try {
            Color(android.graphics.Color.parseColor(activity.userAvatarColor))
        } catch (_: Exception) {
            Color(0xFF6B7280)
        }
    }

    val (icon, description) = remember(activity) {
        getActivityIconAndDescription(activity)
    }

    val bookId = activity.bookId
    val lensId = activity.lensId
    val isClickable = bookId != null || lensId != null

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (isClickable) {
                    Modifier.clickable {
                        when {
                            bookId != null -> onBookClick(bookId)
                            lensId != null -> onLensClick(lensId)
                        }
                    }
                } else {
                    Modifier
                }
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // User avatar
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(avatarColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Activity content
        Column(
            modifier = Modifier.weight(1f),
        ) {
            // User name
            Text(
                text = activity.userDisplayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // Activity description
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Activity icon
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * Get the icon and description for an activity.
 */
private fun getActivityIconAndDescription(activity: ActivityResponse): Pair<ImageVector, String> {
    return when (activity.type) {
        "started_book" -> {
            val prefix = if (activity.isReread) "Started re-reading" else "Started reading"
            val bookInfo = activity.bookTitle ?: "a book"
            val authorInfo = if (activity.bookAuthorName != null) " by ${activity.bookAuthorName}" else ""
            Icons.AutoMirrored.Filled.MenuBook to "$prefix $bookInfo$authorInfo"
        }

        "finished_book" -> {
            val bookInfo = activity.bookTitle ?: "a book"
            val authorInfo = if (activity.bookAuthorName != null) " by ${activity.bookAuthorName}" else ""
            Icons.Default.AutoStories to "Finished $bookInfo$authorInfo"
        }

        "listening_session" -> {
            val bookInfo = activity.bookTitle ?: "a book"
            val durationText = formatDurationMinutes(activity.durationMs)
            Icons.Default.Headphones to "Listened to $durationText of $bookInfo"
        }

        "streak_milestone" -> {
            val days = activity.milestoneValue
            Icons.Default.LocalFireDepartment to "Reached a $days day listening streak!"
        }

        "listening_milestone" -> {
            val hours = activity.milestoneValue
            Icons.Default.Headphones to "Listened for $hours hours total!"
        }

        "lens_created" -> {
            val lensName = activity.lensName ?: "a lens"
            Icons.Default.FilterList to "Created lens \"$lensName\""
        }

        else -> {
            Icons.Default.Celebration to "Did something awesome!"
        }
    }
}

/**
 * Format duration in milliseconds to a human-readable string.
 * Examples: "30 seconds", "5 minutes", "1 hour", "1 hour 30 minutes"
 */
private fun formatDurationMinutes(durationMs: Long): String {
    val totalSeconds = (durationMs / 1_000).toInt()
    val totalMinutes = totalSeconds / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val seconds = totalSeconds % 60

    return when {
        totalMinutes == 0 -> "$totalSeconds second${if (totalSeconds != 1) "s" else ""}"
        hours == 0 -> "$minutes minute${if (minutes != 1) "s" else ""}"
        minutes == 0 -> "$hours hour${if (hours != 1) "s" else ""}"
        else -> "$hours hour${if (hours != 1) "s" else ""} $minutes minute${if (minutes != 1) "s" else ""}"
    }
}
