package com.calypsan.listenup.client.presentation.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.data.remote.LeaderboardCategory
import com.calypsan.listenup.client.data.remote.StatsPeriod
import com.calypsan.listenup.client.data.repository.CommunityStats
import com.calypsan.listenup.client.data.repository.LeaderboardEntry
import com.calypsan.listenup.client.data.repository.LeaderboardRepositoryContract
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * ViewModel for the Discover screen leaderboard section.
 *
 * Offline-first implementation that observes Room database flows:
 * - Category selection re-sorts existing data (no refetch needed)
 * - Period selection changes which Room query to observe
 * - Automatic updates when listening events or activities change
 *
 * Data sources:
 * - Week/Month: Aggregated from local activities table
 * - All-time: From cached user_stats (populated via API/SSE)
 * - Current user stats: Always from listening_events (instant, authoritative)
 *
 * @property leaderboardRepository Repository for observing leaderboard data from Room
 */
class LeaderboardViewModel(
    private val leaderboardRepository: LeaderboardRepositoryContract,
) : ViewModel() {
    val state: StateFlow<LeaderboardUiState>
        field = MutableStateFlow(LeaderboardUiState())

    // Cache entries by category for instant tab switching
    private val entriesByCategory = mutableMapOf<LeaderboardCategory, List<LeaderboardEntry>>()

    // Active observation jobs (cancelled when period changes)
    private var entriesJob: Job? = null
    private var communityStatsJob: Job? = null

    init {
        observeLeaderboard()
    }

    /**
     * Select a new category (instant - just re-sort cached data).
     */
    fun selectCategory(category: LeaderboardCategory) {
        if (category == state.value.selectedCategory) return

        logger.debug { "Category changed to ${category.value}" }
        state.update { it.copy(selectedCategory = category) }
        resortEntries()
    }

    /**
     * Get entries for a specific category (for pager pages).
     */
    fun getEntriesForCategory(category: LeaderboardCategory): List<LeaderboardEntry> {
        val entries = state.value.allEntries
        return entries
            .sortedByDescending { it.valueFor(category) }
            .mapIndexed { index, entry -> entry.copy(rank = index + 1) }
    }

    /**
     * Select a new period (restarts Room observation).
     */
    fun selectPeriod(period: StatsPeriod) {
        if (period == state.value.selectedPeriod) return

        logger.info { "Period changed from ${state.value.selectedPeriod.value} to ${period.value}" }
        state.update { it.copy(selectedPeriod = period, isLoading = true) }
        entriesByCategory.clear()
        observeLeaderboard()
    }

    /**
     * Refresh leaderboard data.
     * For offline-first, this is a no-op since Room flows auto-update.
     * Kept for UI compatibility (pull-to-refresh gesture).
     */
    fun refresh() {
        logger.debug { "Refresh requested (no-op for offline-first)" }
        // Room flows auto-update, no manual refresh needed
        // But we can re-observe to ensure we have latest
        observeLeaderboard()
    }

    /**
     * Start observing leaderboard data from Room.
     * Called on init and when period changes.
     *
     * For All-time period, checks if cache is empty and fetches from API if needed.
     */
    private fun observeLeaderboard() {
        val period = state.value.selectedPeriod
        val category = state.value.selectedCategory

        logger.info { "Observing leaderboard: period=${period.value}, category=${category.value}" }

        // Cancel previous observations
        entriesJob?.cancel()
        communityStatsJob?.cancel()

        // Fetch user stats if cache is empty (needed for all periods since we LEFT JOIN with user_stats)
        viewModelScope.launch {
            if (leaderboardRepository.isUserStatsCacheEmpty()) {
                logger.info { "User stats cache empty, fetching from API" }
                leaderboardRepository.fetchAndCacheUserStats()
                // Room flow will automatically emit when cache is populated
            }
        }

        // Observe entries
        entriesJob =
            viewModelScope.launch {
                leaderboardRepository
                    .observeLeaderboard(period, category, limit = 10)
                    .collect { entries ->
                        logger.debug { "Received ${entries.size} leaderboard entries" }
                        state.update {
                            it.copy(
                                isLoading = false,
                                error = null,
                                allEntries = entries,
                                entries = sortEntriesForCategory(entries, it.selectedCategory),
                            )
                        }
                    }
            }

        // Observe community stats
        communityStatsJob =
            viewModelScope.launch {
                leaderboardRepository
                    .observeCommunityStats(period)
                    .collect { stats ->
                        logger.debug { "Received community stats: ${stats.totalTimeLabel}" }
                        state.update { it.copy(communityStats = stats) }
                    }
            }
    }

    /**
     * Re-sort entries when category changes.
     */
    private fun resortEntries() {
        val category = state.value.selectedCategory
        val entries = state.value.allEntries

        state.update {
            it.copy(entries = sortEntriesForCategory(entries, category))
        }
    }

    /**
     * Sort entries by category and assign ranks.
     */
    private fun sortEntriesForCategory(
        entries: List<LeaderboardEntry>,
        category: LeaderboardCategory,
    ): List<LeaderboardEntry> =
        entries
            .sortedByDescending { it.valueFor(category) }
            .mapIndexed { index, entry -> entry.copy(rank = index + 1) }
}

/**
 * UI state for the leaderboard section.
 *
 * Uses domain models from LeaderboardRepository for offline-first display.
 */
data class LeaderboardUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val selectedCategory: LeaderboardCategory = LeaderboardCategory.TIME,
    val selectedPeriod: StatsPeriod = StatsPeriod.WEEK,
    /** Entries sorted for current category */
    val entries: List<LeaderboardEntry> = emptyList(),
    /** All entries (unsorted) for re-sorting on category change */
    val allEntries: List<LeaderboardEntry> = emptyList(),
    val communityStats: CommunityStats? = null,
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
