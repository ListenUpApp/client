@file:Suppress("MagicNumber")

package com.calypsan.listenup.client.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.data.repository.SearchRepositoryContract
import com.calypsan.listenup.client.domain.model.SearchHit
import com.calypsan.listenup.client.domain.model.SearchHitType
import com.calypsan.listenup.client.domain.model.SearchResult
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * UI state for search.
 */
data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val results: SearchResult? = null,
    val error: String? = null,
    val isExpanded: Boolean = false,
    // Empty = all types
    val selectedTypes: Set<SearchHitType> = emptySet(),
) {
    val hasResults: Boolean
        get() = results != null && results.hits.isNotEmpty()

    val isEmpty: Boolean
        get() = results != null && results.hits.isEmpty() && query.isNotBlank()

    val showOfflineIndicator: Boolean
        get() = results?.isOfflineResult == true

    /**
     * Group hits by type for sectioned display.
     */
    val groupedHits: Map<SearchHitType, List<SearchHit>>
        get() = results?.hits?.groupBy { it.type } ?: emptyMap()

    /**
     * Books from results.
     */
    val books: List<SearchHit>
        get() = groupedHits[SearchHitType.BOOK] ?: emptyList()

    /**
     * Contributors from results.
     */
    val contributors: List<SearchHit>
        get() = groupedHits[SearchHitType.CONTRIBUTOR] ?: emptyList()

    /**
     * Series from results.
     */
    val series: List<SearchHit>
        get() = groupedHits[SearchHitType.SERIES] ?: emptyList()
}

/**
 * Search events from UI.
 */
sealed interface SearchUiEvent {
    data class QueryChanged(
        val query: String,
    ) : SearchUiEvent

    data object ExpandSearch : SearchUiEvent

    data object CollapseSearch : SearchUiEvent

    data object ClearQuery : SearchUiEvent

    data class ToggleTypeFilter(
        val type: SearchHitType,
    ) : SearchUiEvent

    data class ResultClicked(
        val hit: SearchHit,
    ) : SearchUiEvent
}

/**
 * Navigation actions from search.
 */
sealed interface SearchNavAction {
    data class NavigateToBook(
        val bookId: String,
    ) : SearchNavAction

    data class NavigateToContributor(
        val contributorId: String,
    ) : SearchNavAction

    data class NavigateToSeries(
        val seriesId: String,
    ) : SearchNavAction
}

/**
 * ViewModel for search functionality.
 *
 * Handles:
 * - Debounced search as user types
 * - Server search with offline fallback
 * - Type filtering
 * - Navigation on result click
 *
 * @property searchRepository Repository for search operations
 */
@OptIn(FlowPreview::class)
class SearchViewModel(
    private val searchRepository: SearchRepositoryContract,
) : ViewModel() {
    val state: StateFlow<SearchUiState>
        field = MutableStateFlow(SearchUiState())

    val navActions: StateFlow<SearchNavAction?>
        field = MutableStateFlow<SearchNavAction?>(null)

    private var searchJob: Job? = null

    // Internal query flow for debouncing
    private val queryFlow = MutableStateFlow("")

    init {
        // Set up debounced search
        queryFlow
            .debounce(300) // Wait 300ms after last keystroke
            .distinctUntilChanged()
            .filter { it.length >= 2 || it.isEmpty() } // Min 2 chars
            .onEach { query ->
                if (query.isBlank()) {
                    state.update { it.copy(results = null, isSearching = false, error = null) }
                } else {
                    performSearch(query)
                }
            }.launchIn(viewModelScope)
    }

    /**
     * Handle UI events.
     */
    fun onEvent(event: SearchUiEvent) {
        when (event) {
            is SearchUiEvent.QueryChanged -> {
                state.update { it.copy(query = event.query, error = null) }
                queryFlow.value = event.query
            }

            is SearchUiEvent.ExpandSearch -> {
                state.update { it.copy(isExpanded = true) }
            }

            is SearchUiEvent.CollapseSearch -> {
                state.update {
                    it.copy(
                        isExpanded = false,
                        query = "",
                        results = null,
                        error = null,
                    )
                }
                queryFlow.value = ""
            }

            is SearchUiEvent.ClearQuery -> {
                state.update { it.copy(query = "", results = null, error = null) }
                queryFlow.value = ""
            }

            is SearchUiEvent.ToggleTypeFilter -> {
                state.update { current ->
                    val newTypes =
                        if (event.type in current.selectedTypes) {
                            current.selectedTypes - event.type
                        } else {
                            current.selectedTypes + event.type
                        }
                    current.copy(selectedTypes = newTypes)
                }
                // Re-search with new filters
                if (state.value.query.isNotBlank()) {
                    performSearch(state.value.query)
                }
            }

            is SearchUiEvent.ResultClicked -> {
                handleResultClick(event.hit)
            }
        }
    }

    /**
     * Clear navigation action after handling.
     */
    fun clearNavAction() {
        navActions.value = null
    }

    private fun performSearch(query: String) {
        searchJob?.cancel()
        searchJob =
            viewModelScope.launch {
                state.update { it.copy(isSearching = true, error = null) }

                try {
                    val types =
                        state.value.selectedTypes
                            .takeIf { it.isNotEmpty() }
                            ?.toList()
                    val result =
                        searchRepository.search(
                            query = query,
                            types = types,
                            limit = 30,
                        )

                    state.update {
                        it.copy(
                            results = result,
                            isSearching = false,
                        )
                    }
                    logger.debug { "Search completed: ${result.total} results for '$query'" }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // Job was cancelled (e.g., new search started) - don't show error
                    logger.debug { "Search cancelled for '$query'" }
                    throw e
                } catch (e: Exception) {
                    logger.error(e) { "Search failed for '$query'" }
                    state.update {
                        it.copy(
                            error = "Search unavailable. Please try again.",
                            isSearching = false,
                        )
                    }
                }
            }
    }

    private fun handleResultClick(hit: SearchHit) {
        val action =
            when (hit.type) {
                SearchHitType.BOOK -> SearchNavAction.NavigateToBook(hit.id)
                SearchHitType.CONTRIBUTOR -> SearchNavAction.NavigateToContributor(hit.id)
                SearchHitType.SERIES -> SearchNavAction.NavigateToSeries(hit.id)
            }
        navActions.value = action

        // Collapse search after navigation
        state.update { it.copy(isExpanded = false) }
    }
}
