package com.calypsan.listenup.client.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.domain.repository.SearchRepository
import com.calypsan.listenup.client.domain.model.SearchHit
import com.calypsan.listenup.client.domain.model.SearchHitType
import com.calypsan.listenup.client.domain.model.SearchResult
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

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
     * Books from results (deduplicated by ID).
     */
    val books: List<SearchHit>
        get() = (groupedHits[SearchHitType.BOOK] ?: emptyList()).distinctBy { it.id }

    /**
     * Contributors from results (deduplicated by ID).
     */
    val contributors: List<SearchHit>
        get() = (groupedHits[SearchHitType.CONTRIBUTOR] ?: emptyList()).distinctBy { it.id }

    /**
     * Series from results (deduplicated by ID).
     */
    val series: List<SearchHit>
        get() = (groupedHits[SearchHitType.SERIES] ?: emptyList()).distinctBy { it.id }

    /**
     * Tags from results (deduplicated by ID).
     */
    val tags: List<SearchHit>
        get() = (groupedHits[SearchHitType.TAG] ?: emptyList()).distinctBy { it.id }
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

    data class NavigateToTag(
        val tagId: String,
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
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val searchRepository: SearchRepository,
) : ViewModel() {
    val state: StateFlow<SearchUiState>
        field = MutableStateFlow(SearchUiState())

    val navActions: StateFlow<SearchNavAction?>
        field = MutableStateFlow<SearchNavAction?>(null)

    // Internal query flow for debouncing - flatMapLatest handles cancellation automatically
    private val queryFlow = MutableStateFlow("")

    // Version counter to force re-search when filters change (without changing query)
    private val searchVersion = MutableStateFlow(0)

    init {
        // Reactive search with automatic cancellation via flatMapLatest
        // Combines query changes (debounced) with filter changes (immediate via version bump)
        queryFlow
            .debounce(SEARCH_DEBOUNCE_MS)
            .distinctUntilChanged()
            .filter { it.length >= MIN_QUERY_LENGTH || it.isEmpty() }
            .combine(searchVersion) { query, _ -> query }
            .flatMapLatest { query ->
                if (query.isBlank()) {
                    flowOf<SearchFlowResult>(SearchFlowResult.Empty)
                } else {
                    flow<SearchFlowResult> {
                        emit(SearchFlowResult.Loading)
                        emit(performSearch(query))
                    }
                }
            }.onEach { result ->
                when (result) {
                    is SearchFlowResult.Empty -> {
                        state.update { it.copy(results = null, isSearching = false, error = null) }
                    }

                    is SearchFlowResult.Loading -> {
                        state.update { it.copy(isSearching = true, error = null) }
                    }

                    is SearchFlowResult.Success -> {
                        state.update { it.copy(results = result.data, isSearching = false) }
                        logger.info {
                            "Search completed: ${result.data.total} results for '${result.query}' " +
                                "(offline=${result.data.isOfflineResult})"
                        }
                    }

                    is SearchFlowResult.Error -> {
                        logger.error(result.exception) { "Search failed for '${result.query}'" }
                        state.update {
                            it.copy(error = "Search unavailable. Please try again.", isSearching = false)
                        }
                    }
                }
            }.launchIn(viewModelScope)
    }

    /**
     * Internal result type for the reactive search flow.
     */
    private sealed interface SearchFlowResult {
        data object Empty : SearchFlowResult

        data object Loading : SearchFlowResult

        data class Success(
            val query: String,
            val data: SearchResult,
        ) : SearchFlowResult

        data class Error(
            val query: String,
            val exception: Exception,
        ) : SearchFlowResult
    }

    companion object {
        /** Debounce delay in milliseconds before triggering search. */
        private const val SEARCH_DEBOUNCE_MS = 300L

        /** Minimum query length to trigger search. */
        private const val MIN_QUERY_LENGTH = 2

        /** Maximum number of results to return per search. */
        private const val DEFAULT_RESULT_LIMIT = 30
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
                // Bump version to trigger re-search with new filters via flatMapLatest
                if (state.value.query.isNotBlank()) {
                    searchVersion.value++
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

    /**
     * Perform a search and return the result.
     * Called from flatMapLatest flow - cancellation is handled automatically.
     */
    private suspend fun performSearch(query: String): SearchFlowResult =
        try {
            val types =
                state.value.selectedTypes
                    .takeIf { it.isNotEmpty() }
                    ?.toList()
            val result =
                searchRepository.search(
                    query = query,
                    types = types,
                    limit = DEFAULT_RESULT_LIMIT,
                )
            SearchFlowResult.Success(query, result)
        } catch (e: Exception) {
            SearchFlowResult.Error(query, e)
        }

    private fun handleResultClick(hit: SearchHit) {
        val action =
            when (hit.type) {
                SearchHitType.BOOK -> SearchNavAction.NavigateToBook(hit.id)
                SearchHitType.CONTRIBUTOR -> SearchNavAction.NavigateToContributor(hit.id)
                SearchHitType.SERIES -> SearchNavAction.NavigateToSeries(hit.id)
                SearchHitType.TAG -> SearchNavAction.NavigateToTag(hit.id)
            }
        navActions.value = action

        // Collapse search after navigation
        state.update { it.copy(isExpanded = false) }
    }
}
