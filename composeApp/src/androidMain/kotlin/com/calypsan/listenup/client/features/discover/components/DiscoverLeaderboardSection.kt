package com.calypsan.listenup.client.features.discover.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calypsan.listenup.client.data.remote.LeaderboardCategory
import com.calypsan.listenup.client.presentation.discover.LeaderboardViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Discover screen leaderboard section.
 *
 * Displays a gamified leaderboard with:
 * - Period selector (Week/Month/Year/All Time)
 * - Category tabs (Time/Books/Streak)
 * - Ranked list of users
 * - Community aggregate stats
 *
 * @param modifier Modifier from parent
 * @param viewModel LeaderboardViewModel injected via Koin
 */
@Composable
fun DiscoverLeaderboardSection(
    modifier: Modifier = Modifier,
    viewModel: LeaderboardViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Always show the card - different content based on state
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header with title and period selector
            LeaderboardHeader(
                selectedPeriod = state.selectedPeriod,
                onPeriodSelected = { viewModel.selectPeriod(it) },
            )

            when {
                state.isLoading -> {
                    // Loading state
                    Text(
                        text = "Loading leaderboard...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.error != null -> {
                    // Error state - SHOW the error so we can debug
                    Text(
                        text = state.error ?: "Unknown error",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                state.hasData -> {
                    val categories = listOf(
                        LeaderboardCategory.TIME,
                        LeaderboardCategory.BOOKS,
                        LeaderboardCategory.STREAK,
                    )

                    val pagerState = rememberPagerState(
                        initialPage = categories.indexOf(state.selectedCategory).coerceAtLeast(0),
                        pageCount = { categories.size },
                    )

                    // Sync pager with tab selection
                    LaunchedEffect(state.selectedCategory) {
                        val targetPage = categories.indexOf(state.selectedCategory)
                        if (targetPage >= 0 && pagerState.currentPage != targetPage) {
                            pagerState.animateScrollToPage(targetPage)
                        }
                    }

                    // Sync tab selection with pager swipes
                    LaunchedEffect(pagerState) {
                        snapshotFlow { pagerState.settledPage }.collect { page ->
                            val category = categories.getOrNull(page)
                            if (category != null && category != state.selectedCategory) {
                                viewModel.selectCategory(category)
                            }
                        }
                    }

                    // Category tabs
                    LeaderboardCategoryTabs(
                        selectedCategory = state.selectedCategory,
                        onCategorySelected = { viewModel.selectCategory(it) },
                    )

                    // Swipeable leaderboard pager
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxWidth(),
                    ) { page ->
                        val category = categories[page]
                        val entries = viewModel.getEntriesForCategory(category)
                        LeaderboardList(entries = entries)
                    }

                    // Community stats
                    if (state.hasCommunityStats) {
                        CommunityStatsRow(stats = state.communityStats!!)
                    }
                }
                else -> {
                    // Empty state
                    Text(
                        text = "Start listening to join the community rankings",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
