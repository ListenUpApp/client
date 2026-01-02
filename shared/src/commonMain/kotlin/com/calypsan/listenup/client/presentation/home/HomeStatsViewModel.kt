package com.calypsan.listenup.client.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.data.remote.DailyListeningResponse
import com.calypsan.listenup.client.data.remote.GenreListeningResponse
import com.calypsan.listenup.client.data.repository.StatsRepositoryContract
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
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
 * Stats are computed locally from ListeningEventEntity records stored in Room.
 * Updates automatically when new listening events are added (local or via SSE).
 *
 * @property statsRepository Repository for computing local stats
 */
class HomeStatsViewModel(
    private val statsRepository: StatsRepositoryContract,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeStatsUiState())
    val state: StateFlow<HomeStatsUiState> = _state

    init {
        observeStats()
    }

    /**
     * Observe stats from the repository.
     *
     * Stats update automatically when listening events change in Room.
     * No need for manual refresh or SSE observation - the Flow handles it.
     */
    private fun observeStats() {
        viewModelScope.launch {
            statsRepository.observeWeeklyStats()
                .catch { e ->
                    logger.error(e) { "Error observing stats" }
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = "Failed to load stats: ${e.message}",
                        )
                    }
                }
                .collect { stats ->
                    logger.debug {
                        "Stats updated: ${stats.totalListenTimeMs}ms total, streak=${stats.currentStreakDays}"
                    }
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = null,
                            totalListenTimeMs = stats.totalListenTimeMs,
                            currentStreakDays = stats.currentStreakDays,
                            longestStreakDays = stats.longestStreakDays,
                            dailyListening = stats.dailyListening,
                            genreBreakdown = stats.genreBreakdown,
                        )
                    }
                }
        }
    }

    /**
     * Refresh stats.
     *
     * Since stats are computed from local Room data, this is a no-op.
     * The Flow automatically updates when events change.
     * Pull-to-refresh on home screen triggers sync which adds new events.
     */
    fun refresh() {
        // No-op - stats auto-update from Room Flow
        // Pull-to-refresh triggers sync elsewhere, which adds events to Room,
        // causing this Flow to emit new computed stats
        logger.debug { "Refresh requested - stats will update automatically from Room" }
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
        get() = totalListenTimeMs > 0 || dailyListening.isNotEmpty() || currentStreakDays > 0 || longestStreakDays > 0

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
     * Whether to show the streak section (current or longest streak > 0).
     */
    val hasStreak: Boolean
        get() = currentStreakDays > 0 || longestStreakDays > 0
}
