package com.calypsan.listenup.client.presentation.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.data.remote.CommunityStatsResponse
import com.calypsan.listenup.client.data.remote.LeaderboardApiContract
import com.calypsan.listenup.client.data.remote.LeaderboardCategory
import com.calypsan.listenup.client.data.remote.LeaderboardEntryResponse
import com.calypsan.listenup.client.data.remote.StatsPeriod
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * ViewModel for the Discover screen leaderboard section.
 *
 * Manages:
 * - Category selection (time/books/streak)
 * - Period selection (week/month/year/all)
 * - Leaderboard entries (cached per category)
 * - Community aggregate stats
 *
 * Caches entries by category so switching tabs doesn't trigger a refresh.
 * Only refetches when the period changes or on explicit refresh.
 *
 * @property leaderboardApi API client for fetching leaderboard data
 */
class LeaderboardViewModel(
    private val leaderboardApi: LeaderboardApiContract,
) : ViewModel() {
    val state: StateFlow<LeaderboardUiState>
        field = MutableStateFlow(LeaderboardUiState())

    // Cache entries by category to avoid refetching when switching tabs
    private val entriesByCategory = mutableMapOf<LeaderboardCategory, List<LeaderboardEntryResponse>>()

    init {
        loadAllCategories()
    }

    /**
     * Select a new category (no network call - uses cached data).
     */
    fun selectCategory(category: LeaderboardCategory) {
        if (category == state.value.selectedCategory) return

        val cachedEntries = entriesByCategory[category] ?: emptyList()
        state.update {
            it.copy(
                selectedCategory = category,
                entries = cachedEntries,
            )
        }
    }

    /**
     * Get entries for a specific category (for pager pages).
     */
    fun getEntriesForCategory(category: LeaderboardCategory): List<LeaderboardEntryResponse> =
        entriesByCategory[category] ?: emptyList()

    /**
     * Select a new period (refetches all categories).
     */
    fun selectPeriod(period: StatsPeriod) {
        if (period == state.value.selectedPeriod) return

        state.update { it.copy(selectedPeriod = period) }
        entriesByCategory.clear()
        loadAllCategories()
    }

    /**
     * Refresh leaderboard data (refetches all categories).
     */
    fun refresh() {
        entriesByCategory.clear()
        loadAllCategories()
    }

    /**
     * Load leaderboard data for all categories.
     * This pre-fetches all categories so tab switching is instant.
     */
    private fun loadAllCategories() {
        viewModelScope.launch {
            state.update { it.copy(isLoading = true, error = null) }

            try {
                val period = state.value.selectedPeriod
                val currentCategory = state.value.selectedCategory

                // Fetch all categories in parallel
                val categories =
                    listOf(
                        LeaderboardCategory.TIME,
                        LeaderboardCategory.BOOKS,
                        LeaderboardCategory.STREAK,
                    )

                var communityStats: CommunityStatsResponse? = null

                categories.forEach { category ->
                    try {
                        val response =
                            leaderboardApi.getLeaderboard(
                                period = period,
                                category = category,
                                limit = 10,
                            )
                        entriesByCategory[category] = response.entries
                        // Use community stats from any category (they should be the same)
                        if (communityStats == null) {
                            communityStats = response.communityStats
                        }
                    } catch (e: Exception) {
                        logger.warn(e) { "Failed to load $category leaderboard" }
                        entriesByCategory[category] = emptyList()
                    }
                }

                state.update {
                    it.copy(
                        isLoading = false,
                        error = null,
                        entries = entriesByCategory[currentCategory] ?: emptyList(),
                        communityStats = communityStats,
                    )
                }
                logger.debug { "Loaded all leaderboard categories" }
            } catch (e: Exception) {
                val errorMessage = "Failed to load leaderboard: ${e.message}"
                state.update {
                    it.copy(
                        isLoading = false,
                        error = errorMessage,
                    )
                }
                logger.error(e) { "Failed to load leaderboard - full exception: ${e.stackTraceToString()}" }
            }
        }
    }
}

/**
 * UI state for the leaderboard section.
 */
data class LeaderboardUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val selectedCategory: LeaderboardCategory = LeaderboardCategory.TIME,
    val selectedPeriod: StatsPeriod = StatsPeriod.WEEK,
    val entries: List<LeaderboardEntryResponse> = emptyList(),
    val communityStats: CommunityStatsResponse? = null,
) {
    /**
     * Whether there is data to display.
     */
    val hasData: Boolean
        get() = entries.isNotEmpty()

    /**
     * Whether there are community stats.
     */
    val hasCommunityStats: Boolean
        get() = communityStats != null
}
