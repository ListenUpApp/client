package com.calypsan.listenup.client.presentation.seriesdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.domain.model.Book
import com.calypsan.listenup.client.domain.repository.ImageRepository
import com.calypsan.listenup.client.domain.repository.SeriesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * ViewModel for the Series Detail screen.
 *
 * Loads and manages series information with its books for display.
 *
 * @property seriesRepository Repository for series data
 * @property imageRepository Repository for cover image path operations
 */
class SeriesDetailViewModel(
    private val seriesRepository: SeriesRepository,
    private val imageRepository: ImageRepository,
) : ViewModel() {
    val state: StateFlow<SeriesDetailUiState>
        field = MutableStateFlow(SeriesDetailUiState())

    /**
     * Load series details by ID.
     *
     * Observes the series reactively so UI updates automatically
     * when series data changes (e.g., after sync).
     *
     * @param seriesId The ID of the series to load
     */
    fun loadSeries(seriesId: String) {
        state.value = state.value.copy(isLoading = true)

        viewModelScope.launch {
            seriesRepository.observeSeriesWithBooks(seriesId).collectLatest { seriesWithBooks ->
                if (seriesWithBooks != null) {
                    // Domain model already has books sorted by sequence
                    val books = seriesWithBooks.booksSortedBySequence()

                    // Calculate total duration from all books
                    val totalDuration = books.sumOf { it.duration }.milliseconds

                    // Resolve cover path: series cover first, then fallback to first book's cover
                    val coverPath = resolveCoverPath(seriesId, books)

                    state.value =
                        SeriesDetailUiState(
                            isLoading = false,
                            seriesId = seriesId,
                            seriesName = seriesWithBooks.series.name,
                            seriesDescription = seriesWithBooks.series.description,
                            coverPath = coverPath,
                            featuredBookId = books.firstOrNull()?.id?.value,
                            totalDuration = totalDuration,
                            books = books,
                            error = null,
                        )
                } else {
                    state.value =
                        SeriesDetailUiState(
                            isLoading = false,
                            error = "Series not found",
                        )
                }
            }
        }
    }

    /**
     * Resolve the cover path for the series.
     *
     * Priority:
     * 1. Series-specific cover (if exists)
     * 2. First book's cover (fallback)
     * 3. null (no cover available)
     */
    private fun resolveCoverPath(
        seriesId: String,
        books: List<Book>,
    ): String? {
        // Check for series-specific cover
        if (imageRepository.seriesCoverExists(seriesId)) {
            return imageRepository.getSeriesCoverPath(seriesId)
        }

        // Fallback to first book's cover
        return books.firstOrNull()?.coverPath
    }
}

/**
 * UI state for the Series Detail screen.
 */
data class SeriesDetailUiState(
    val isLoading: Boolean = true,
    val seriesId: String = "",
    val seriesName: String = "",
    val seriesDescription: String? = null,
    val coverPath: String? = null,
    val featuredBookId: String? = null,
    val totalDuration: Duration = Duration.ZERO,
    val books: List<Book> = emptyList(),
    val error: String? = null,
) {
    /**
     * Format the total duration for display.
     * E.g., "87h 5m" or "12h 30m"
     */
    fun formatTotalDuration(): String {
        val totalHours = totalDuration.inWholeHours
        val minutes = totalDuration.inWholeMinutes % 60
        return if (totalHours > 0) {
            "${totalHours}h ${minutes}m"
        } else {
            "${minutes}m"
        }
    }
}
