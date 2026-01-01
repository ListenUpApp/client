package com.calypsan.listenup.client.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.data.remote.DailyListeningResponse
import com.calypsan.listenup.client.data.remote.GenreListeningResponse
import com.calypsan.listenup.client.data.remote.StatsApiContract
import com.calypsan.listenup.client.data.remote.StatsPeriod
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * ViewModel for the Home screen stats section.
 *
 * Manages:
 * - 7-day listening chart data
 * - Current/longest streak display
 * - Top 3 genres breakdown
 *
 * Always loads weekly stats (7 days) for the home section.
 *
 * @property statsApi API client for fetching stats
 */
class HomeStatsViewModel(
    private val statsApi: StatsApiContract,
) : ViewModel() {
    val state: StateFlow<HomeStatsUiState>
        field = MutableStateFlow(HomeStatsUiState())

    init {
        loadStats()
    }

    /**
     * Refresh stats.
     *
     * Called by pull-to-refresh on home screen.
     */
    fun refresh() {
        loadStats()
    }

    /**
     * Load weekly stats from the server.
     */
    private fun loadStats() {
        viewModelScope.launch {
            state.update { it.copy(isLoading = true, error = null) }

            try {
                val response = statsApi.getUserStats(StatsPeriod.WEEK)

                state.update {
                    it.copy(
                        isLoading = false,
                        error = null,
                        totalListenTimeMs = response.totalListenTimeMs,
                        currentStreakDays = response.currentStreakDays,
                        longestStreakDays = response.longestStreakDays,
                        dailyListening = response.dailyListening,
                        genreBreakdown = response.genreBreakdown.take(3),
                    )
                }
                logger.debug { "Home stats loaded: ${response.totalListenTimeMs}ms total, streak: ${response.currentStreakDays}" }
            } catch (e: Exception) {
                val errorMessage = "Failed to load stats: ${e.message}"
                state.update {
                    it.copy(
                        isLoading = false,
                        error = errorMessage,
                    )
                }
                logger.error(e) { "Failed to load stats - full exception: ${e.stackTraceToString()}" }
            }
        }
    }
}

/**
 * UI state for the Home stats section.
 *
 * Immutable data class that represents the stats section state.
 */
data class HomeStatsUiState(
    val isLoading: Boolean = true,
    val error: String? = null,

    // Stats data
    val totalListenTimeMs: Long = 0,
    val currentStreakDays: Int = 0,
    val longestStreakDays: Int = 0,

    // Chart data
    val dailyListening: List<DailyListeningResponse> = emptyList(),
    val genreBreakdown: List<GenreListeningResponse> = emptyList(),
) {
    /**
     * Total listening time formatted as human-readable string.
     *
     * Examples: "0m", "45m", "2h 30m", "15h 45m"
     */
    val formattedListenTime: String
        get() {
            val totalMinutes = totalListenTimeMs / 60_000
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60

            return when {
                hours == 0L -> "${minutes}m"
                minutes == 0L -> "${hours}h"
                else -> "${hours}h ${minutes}m"
            }
        }

    /**
     * Whether there is any data to display.
     */
    val hasData: Boolean
        get() = totalListenTimeMs > 0 || dailyListening.isNotEmpty() || currentStreakDays > 0

    /**
     * Whether there is genre data to display.
     */
    val hasGenreData: Boolean
        get() = genreBreakdown.isNotEmpty()

    /**
     * Maximum daily listening time in milliseconds for chart scaling.
     */
    val maxDailyListenTimeMs: Long
        get() = dailyListening.maxOfOrNull { it.listenTimeMs } ?: 0

    /**
     * Whether to show the streak (current streak > 0).
     */
    val hasStreak: Boolean
        get() = currentStreakDays > 0
}
