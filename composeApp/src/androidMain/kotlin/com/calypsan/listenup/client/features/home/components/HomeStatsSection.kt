package com.calypsan.listenup.client.features.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calypsan.listenup.client.presentation.home.HomeStatsViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Home screen stats section.
 *
 * Displays a card with the user's listening stats:
 * - 7-day bar chart showing daily listening time
 * - Current streak indicator with fire emoji
 * - Top 3 genres breakdown
 *
 * Shows skeleton loading state while data is being fetched.
 * Hides completely when there's no data to show.
 *
 * @param modifier Modifier from parent
 * @param viewModel HomeStatsViewModel injected via Koin
 */
@Composable
fun HomeStatsSection(
    modifier: Modifier = Modifier,
    viewModel: HomeStatsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Always show the card - different content based on state
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Section title
            Text(
                text = "This Week",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            when {
                state.isLoading -> {
                    // Loading state
                    Text(
                        text = "Loading stats...",
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
                    // Stats content
                    HomeStatsContent(state = state)
                }

                else -> {
                    // Empty state
                    Text(
                        text = "Start listening to see your stats here",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Stats content when data is available.
 */
@Composable
private fun HomeStatsContent(state: com.calypsan.listenup.client.presentation.home.HomeStatsUiState) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 7-day listening chart
        if (state.dailyListening.isNotEmpty()) {
            DailyListeningChart(
                dailyListening = state.dailyListening,
                maxListenTimeMs = state.maxDailyListenTimeMs,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Streak indicator
        if (state.hasStreak) {
            StreakIndicator(
                currentStreak = state.currentStreakDays,
                longestStreak = state.longestStreakDays,
            )
        }

        // Genre breakdown
        if (state.hasGenreData) {
            GenreBreakdownBars(
                genres = state.genreBreakdown,
            )
        }
    }
}
