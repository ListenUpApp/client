@file:OptIn(FlowPreview::class)

package com.calypsan.listenup.client.presentation.bookedit.delegates

import com.calypsan.listenup.client.data.remote.SeriesSearchResult
import com.calypsan.listenup.client.data.repository.SeriesRepositoryContract
import com.calypsan.listenup.client.presentation.bookedit.BookEditUiState
import com.calypsan.listenup.client.presentation.bookedit.EditableSeries
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

private const val SEARCH_DEBOUNCE_MS = 300L
private const val MIN_QUERY_LENGTH = 2
private const val SEARCH_LIMIT = 10

/**
 * Delegate handling series editing operations.
 *
 * Responsibilities:
 * - Debounced series search
 * - Add/remove series
 * - Sequence editing
 *
 * @property state Shared state flow owned by ViewModel
 * @property seriesRepository Repository for series search
 * @property scope CoroutineScope for launching operations
 * @property onChangesMade Callback to notify ViewModel of changes
 */
class SeriesEditDelegate(
    private val state: MutableStateFlow<BookEditUiState>,
    private val seriesRepository: SeriesRepositoryContract,
    private val scope: CoroutineScope,
    private val onChangesMade: () -> Unit,
) {
    private val seriesQueryFlow = MutableStateFlow("")
    private var seriesSearchJob: Job? = null

    init {
        setupSeriesSearch()
    }

    /**
     * Update the series search query.
     */
    fun updateSearchQuery(query: String) {
        state.update { it.copy(seriesSearchQuery = query) }
        seriesQueryFlow.value = query
    }

    /**
     * Clear the series search.
     */
    fun clearSearch() {
        state.update {
            it.copy(
                seriesSearchQuery = "",
                seriesSearchResults = emptyList(),
            )
        }
        seriesQueryFlow.value = ""
    }

    /**
     * Select a series from search results.
     */
    fun selectSeries(result: SeriesSearchResult) {
        state.update { current ->
            // Check if series already exists
            val existing = current.series.find { it.id == result.id }
            if (existing != null) {
                // Already added - just clear search
                return@update current.copy(
                    seriesSearchQuery = "",
                    seriesSearchResults = emptyList(),
                )
            }

            // Add new series
            val newSeries =
                EditableSeries(
                    id = result.id,
                    name = result.name,
                    sequence = null,
                )
            current.copy(
                series = current.series + newSeries,
                seriesSearchQuery = "",
                seriesSearchResults = emptyList(),
            )
        }

        seriesQueryFlow.value = ""
        onChangesMade()
    }

    /**
     * Add a new series by name.
     */
    fun addSeries(name: String) {
        if (name.isBlank()) return

        val trimmedName = name.trim()

        state.update { current ->
            // Check if series already exists (by name)
            val existing =
                current.series.find {
                    it.name.equals(trimmedName, ignoreCase = true)
                }
            if (existing != null) {
                // Already added - just clear search
                return@update current.copy(
                    seriesSearchQuery = "",
                    seriesSearchResults = emptyList(),
                )
            }

            // Add new series (no ID = will be created on server)
            val newSeries =
                EditableSeries(
                    id = null,
                    name = trimmedName,
                    sequence = null,
                )
            current.copy(
                series = current.series + newSeries,
                seriesSearchQuery = "",
                seriesSearchResults = emptyList(),
            )
        }

        seriesQueryFlow.value = ""
        onChangesMade()
    }

    /**
     * Update the sequence for a series.
     */
    fun updateSeriesSequence(
        targetSeries: EditableSeries,
        sequence: String,
    ) {
        state.update { current ->
            current.copy(
                series =
                    current.series.map {
                        if (it == targetSeries) it.copy(sequence = sequence.ifBlank { null }) else it
                    },
            )
        }
        onChangesMade()
    }

    /**
     * Remove a series from the book.
     */
    fun removeSeries(targetSeries: EditableSeries) {
        state.update { current ->
            current.copy(series = current.series - targetSeries)
        }
        onChangesMade()
    }

    private fun setupSeriesSearch() {
        seriesQueryFlow
            .debounce(SEARCH_DEBOUNCE_MS)
            .distinctUntilChanged()
            .filter { it.length >= MIN_QUERY_LENGTH || it.isEmpty() }
            .onEach { query ->
                if (query.isBlank()) {
                    state.update {
                        it.copy(
                            seriesSearchResults = emptyList(),
                            seriesSearchLoading = false,
                        )
                    }
                } else {
                    performSeriesSearch(query)
                }
            }.launchIn(scope)
    }

    private fun performSeriesSearch(query: String) {
        seriesSearchJob?.cancel()
        seriesSearchJob =
            scope.launch {
                state.update { it.copy(seriesSearchLoading = true) }

                val response = seriesRepository.searchSeries(query, limit = SEARCH_LIMIT)

                // Filter out series already added to this book
                val currentSeriesIds =
                    state.value.series
                        .mapNotNull { it.id }
                        .toSet()
                val filteredResults = response.series.filter { it.id !in currentSeriesIds }

                state.update {
                    it.copy(
                        seriesSearchResults = filteredResults,
                        seriesSearchLoading = false,
                        seriesOfflineResult = response.isOfflineResult,
                    )
                }

                logger.debug { "Series search: ${filteredResults.size} results for '$query'" }
            }
    }
}
