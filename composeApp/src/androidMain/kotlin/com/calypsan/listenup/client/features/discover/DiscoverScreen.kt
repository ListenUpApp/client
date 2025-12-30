@file:Suppress("MagicNumber")

package com.calypsan.listenup.client.features.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.calypsan.listenup.client.data.remote.LensResponse
import com.calypsan.listenup.client.data.remote.UserLensesResponse
import com.calypsan.listenup.client.design.components.ListenUpLoadingIndicator
import com.calypsan.listenup.client.presentation.discover.DiscoverViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Discover screen - browse lenses from other users.
 *
 * Features:
 * - Pull to refresh
 * - Users grouped with their lenses
 * - Click lens to view details
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    onLensClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiscoverViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh = { viewModel.refresh() },
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        when {
            state.isLoading && state.users.isEmpty() -> {
                ListenUpLoadingIndicator(modifier = Modifier.align(Alignment.Center))
            }

            state.isEmpty -> {
                EmptyDiscoverState(modifier = Modifier.align(Alignment.Center))
            }

            state.error != null -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = state.error ?: "Unknown error",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            else -> {
                DiscoverContent(
                    users = state.users,
                    onLensClick = onLensClick,
                )
            }
        }
    }
}

/**
 * Empty state when no lenses are discoverable.
 */
@Composable
private fun EmptyDiscoverState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Explore,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
        Text(
            text = "No Lenses to Discover",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "When other users create lenses with books you can access, they'll appear here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Main content showing users with their lenses.
 */
@Composable
private fun DiscoverContent(
    users: List<UserLensesResponse>,
    onLensClick: (String) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(
            items = users,
            key = { it.user.id },
        ) { userLenses ->
            UserLensesSection(
                userLenses = userLenses,
                onLensClick = onLensClick,
            )
        }
    }
}

/**
 * Section for a single user's lenses.
 */
@Composable
private fun UserLensesSection(
    userLenses: UserLensesResponse,
    onLensClick: (String) -> Unit,
) {
    val avatarColor = remember(userLenses.user.avatarColor) {
        try {
            Color(android.graphics.Color.parseColor(userLenses.user.avatarColor))
        } catch (_: Exception) {
            Color(0xFF6B7280)
        }
    }

    Column {
        // User header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(avatarColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = userLenses.user.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${userLenses.lenses.size} ${if (userLenses.lenses.size == 1) "lens" else "lenses"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Horizontal scroll of lenses
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                items = userLenses.lenses,
                key = { it.id },
            ) { lens ->
                DiscoverLensCard(
                    lens = lens,
                    avatarColor = avatarColor,
                    onClick = { onLensClick(lens.id) },
                )
            }
        }
    }
}

/**
 * Card for a discoverable lens.
 */
@Composable
private fun DiscoverLensCard(
    lens: LensResponse,
    avatarColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(140.dp)
            .clickable(onClick = onClick),
    ) {
        // Lens icon
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(avatarColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.FilterList,
                contentDescription = null,
                tint = avatarColor,
                modifier = Modifier.size(48.dp),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Lens name
        Text(
            text = lens.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        // Book count
        Text(
            text = "${lens.bookCount} ${if (lens.bookCount == 1) "book" else "books"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
