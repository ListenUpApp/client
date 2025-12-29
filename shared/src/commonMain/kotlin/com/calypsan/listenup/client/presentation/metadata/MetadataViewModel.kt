package com.calypsan.listenup.client.presentation.metadata

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.remote.model.ApplyMatchRequest
import com.calypsan.listenup.client.data.remote.model.CoverOption
import com.calypsan.listenup.client.data.remote.model.MatchFields
import com.calypsan.listenup.client.data.remote.model.MetadataBook
import com.calypsan.listenup.client.data.remote.model.MetadataSearchResult
import com.calypsan.listenup.client.data.remote.model.SeriesMatchEntry
import com.calypsan.listenup.client.data.repository.MetadataRepositoryContract
import com.calypsan.listenup.client.data.sync.ImageDownloaderContract
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * Available Audible regions for metadata lookup.
 */
enum class AudibleRegion(
    val code: String,
    val displayName: String,
) {
    US("us", "United States"),
    UK("uk", "United Kingdom"),
    DE("de", "Germany"),
    FR("fr", "France"),
    AU("au", "Australia"),
    CA("ca", "Canada"),
    IT("it", "Italy"),
    IN("in", "India"),
    ES("es", "Spain"),
    JP("jp", "Japan"),
}

/**
 * Tracks which metadata fields the user has selected to apply.
 */
data class MetadataSelections(
    // Simple fields
    val cover: Boolean = true,
    val title: Boolean = true,
    val subtitle: Boolean = true,
    val description: Boolean = true,
    val publisher: Boolean = true,
    val releaseDate: Boolean = true,
    val language: Boolean = true,
    // List fields - store selected ASINs/values
    val selectedAuthors: Set<String> = emptySet(), // ASINs
    val selectedNarrators: Set<String> = emptySet(), // ASINs
    val selectedSeries: Set<String> = emptySet(), // ASINs
    val selectedGenres: Set<String> = emptySet(), // Genre names
)

/**
 * UI state for the metadata search and match flow.
 */
data class MetadataUiState(
    // Book context
    val bookId: String = "",
    val currentTitle: String = "",
    val currentAuthor: String = "",
    val existingAsin: String? = null, // Pre-existing ASIN from embedded metadata
    // Region selection
    val selectedRegion: AudibleRegion = AudibleRegion.US,
    // Search state
    val searchQuery: String = "",
    val searchResults: List<MetadataSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val searchError: String? = null,
    // Preview state
    val selectedMatch: MetadataSearchResult? = null,
    val previewBook: MetadataBook? = null,
    val isLoadingPreview: Boolean = false,
    val previewError: String? = null, // API/network error - book not found
    val previewNotFound: Boolean = false, // Book exists but has no/minimal data
    // Field selections - what to apply
    val selections: MetadataSelections = MetadataSelections(),
    // Cover selection - multi-source covers
    val coverOptions: List<CoverOption> = emptyList(),
    val isLoadingCovers: Boolean = false,
    val selectedCoverUrl: String? = null, // null = use Audible default from preview
    // Apply state
    val isApplying: Boolean = false,
    val applySuccess: Boolean = false,
    val applyError: String? = null,
)

/**
 * ViewModel for the metadata search and match flow.
 *
 * Manages the full flow:
 * 1. Search Audible for matches
 * 2. Select a match and preview the changes
 * 3. Apply the match to update the book
 */
class MetadataViewModel(
    private val metadataRepository: MetadataRepositoryContract,
    private val imageDownloader: ImageDownloaderContract,
) : ViewModel() {
    val state: StateFlow<MetadataUiState>
        field = MutableStateFlow(MetadataUiState())

    /**
     * Initialize the ViewModel for a specific book.
     *
     * If the book has an existing ASIN, stores it for direct lookup.
     * Otherwise, pre-fills the search query with the book's title and first author.
     *
     * @param bookId The book's ID
     * @param title The book's title (for search query)
     * @param author The book's first author (for search query)
     * @param asin Optional existing ASIN from embedded metadata
     */
    fun initForBook(
        bookId: String,
        title: String,
        author: String,
        asin: String? = null,
    ) {
        val query =
            buildString {
                append(title)
                if (author.isNotBlank()) {
                    append(" ")
                    append(author)
                }
            }

        state.update {
            it.copy(
                bookId = bookId,
                currentTitle = title,
                currentAuthor = author,
                existingAsin = asin,
                searchQuery = query,
                // Reset other state
                searchResults = emptyList(),
                searchError = null,
                selectedMatch = null,
                previewBook = null,
                previewError = null,
                previewNotFound = false,
                applySuccess = false,
                applyError = null,
            )
        }
    }

    /**
     * Change the Audible region and re-fetch metadata for the current ASIN.
     */
    fun changeRegion(region: AudibleRegion) {
        val currentAsin = state.value.selectedMatch?.asin ?: return

        state.update { it.copy(selectedRegion = region) }

        // Re-fetch with new region
        val result =
            MetadataSearchResult(
                asin = currentAsin,
                title = state.value.previewBook?.title ?: "",
            )
        selectMatch(result)
    }

    /**
     * Update the search query.
     */
    fun updateQuery(query: String) {
        state.update { it.copy(searchQuery = query) }
    }

    /**
     * Execute an Audible search with the current query.
     */
    fun search() {
        val query = state.value.searchQuery.trim()
        if (query.isBlank()) return

        viewModelScope.launch {
            state.update {
                it.copy(
                    isSearching = true,
                    searchError = null,
                )
            }

            try {
                val results = metadataRepository.searchAudible(query)
                state.update {
                    it.copy(
                        searchResults = results,
                        isSearching = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error(e) { "Metadata search failed" }
                state.update {
                    it.copy(
                        isSearching = false,
                        searchError = e.message ?: "Search failed",
                    )
                }
            }
        }
    }

    /**
     * Select a match from the search results and load its full preview.
     */
    fun selectMatch(result: MetadataSearchResult) {
        state.update {
            it.copy(
                selectedMatch = result,
                isLoadingPreview = true,
                previewBook = null,
                previewError = null,
                previewNotFound = false,
                // Reset cover state
                coverOptions = emptyList(),
                isLoadingCovers = true,
                selectedCoverUrl = null,
            )
        }

        // Load covers in parallel with preview
        loadCoverOptions()

        viewModelScope.launch {
            try {
                val region = state.value.selectedRegion.code
                val preview = metadataRepository.getMetadataPreview(result.asin, region)

                // Check if the response has essentially no data
                val hasNoData =
                    preview.authors.isEmpty() &&
                        preview.narrators.isEmpty() &&
                        preview.series.isEmpty() &&
                        preview.genres.isEmpty() &&
                        preview.coverUrl == null &&
                        preview.description == null

                // Initialize selections with all available data selected
                val selections = initializeSelections(preview)

                state.update {
                    it.copy(
                        previewBook = preview,
                        isLoadingPreview = false,
                        previewNotFound = hasNoData,
                        selections = selections,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error(e) { "Failed to load metadata preview" }

                // Only use fallback if we have meaningful search result data
                // (i.e., came from an actual search, not a direct ASIN lookup)
                if (result.title.isNotBlank()) {
                    logger.info { "Using search result data as fallback" }
                    val fallbackBook =
                        MetadataBook(
                            asin = result.asin,
                            title = result.title,
                            subtitle = result.subtitle,
                            authors = result.authors,
                            narrators = result.narrators,
                            series = result.series,
                            coverUrl = result.coverUrl,
                            runtimeMinutes = result.runtimeMinutes,
                            releaseDate = result.releaseDate,
                            rating = result.rating,
                            ratingCount = result.ratingCount,
                            language = result.language,
                        )
                    state.update {
                        it.copy(
                            previewBook = fallbackBook,
                            isLoadingPreview = false,
                            previewError = null,
                        )
                    }
                } else {
                    // No fallback data available, show error
                    state.update {
                        it.copy(
                            isLoadingPreview = false,
                            previewError = e.message ?: "Failed to load metadata",
                        )
                    }
                }
            }
        }
    }

    /**
     * Clear the selected match and return to search results.
     */
    fun clearSelection() {
        state.update {
            it.copy(
                selectedMatch = null,
                previewBook = null,
            )
        }
    }

    /**
     * Toggle a simple field selection.
     */
    fun toggleField(field: MetadataField) {
        state.update { currentState ->
            val selections = currentState.selections
            val newSelections =
                when (field) {
                    MetadataField.COVER -> selections.copy(cover = !selections.cover)
                    MetadataField.TITLE -> selections.copy(title = !selections.title)
                    MetadataField.SUBTITLE -> selections.copy(subtitle = !selections.subtitle)
                    MetadataField.DESCRIPTION -> selections.copy(description = !selections.description)
                    MetadataField.PUBLISHER -> selections.copy(publisher = !selections.publisher)
                    MetadataField.RELEASE_DATE -> selections.copy(releaseDate = !selections.releaseDate)
                    MetadataField.LANGUAGE -> selections.copy(language = !selections.language)
                }
            currentState.copy(selections = newSelections)
        }
    }

    /**
     * Toggle an author selection by ASIN.
     */
    fun toggleAuthor(asin: String) {
        state.update { currentState ->
            val current = currentState.selections.selectedAuthors
            val newAuthors = if (asin in current) current - asin else current + asin
            currentState.copy(
                selections = currentState.selections.copy(selectedAuthors = newAuthors),
            )
        }
    }

    /**
     * Toggle a narrator selection by ASIN.
     */
    fun toggleNarrator(asin: String) {
        state.update { currentState ->
            val current = currentState.selections.selectedNarrators
            val newNarrators = if (asin in current) current - asin else current + asin
            currentState.copy(
                selections = currentState.selections.copy(selectedNarrators = newNarrators),
            )
        }
    }

    /**
     * Toggle a series selection by ASIN.
     */
    fun toggleSeries(asin: String) {
        state.update { currentState ->
            val current = currentState.selections.selectedSeries
            val newSeries = if (asin in current) current - asin else current + asin
            currentState.copy(
                selections = currentState.selections.copy(selectedSeries = newSeries),
            )
        }
    }

    /**
     * Toggle a genre selection.
     */
    fun toggleGenre(genre: String) {
        state.update { currentState ->
            val current = currentState.selections.selectedGenres
            val newGenres = if (genre in current) current - genre else current + genre
            currentState.copy(
                selections = currentState.selections.copy(selectedGenres = newGenres),
            )
        }
    }

    /**
     * Select a cover from the available options.
     *
     * @param coverUrl URL of the selected cover, or null to use the current/default cover
     */
    fun selectCover(coverUrl: String?) {
        state.update { it.copy(selectedCoverUrl = coverUrl) }
    }

    /**
     * Load cover options from multiple sources (iTunes, Audible).
     * Called in parallel with preview loading.
     */
    private fun loadCoverOptions() {
        val currentState = state.value
        val title = currentState.currentTitle
        val author = currentState.currentAuthor

        viewModelScope.launch {
            try {
                val covers = metadataRepository.searchCovers(title, author)
                state.update {
                    it.copy(
                        coverOptions = covers,
                        isLoadingCovers = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Non-fatal - just show Audible cover from preview only
                logger.warn(e) { "Cover search failed, will use Audible cover only" }
                state.update { it.copy(isLoadingCovers = false) }
            }
        }
    }

    /**
     * Apply the selected match to the book.
     *
     * This sends the match request to the server with the user's field selections.
     * The server will:
     * 1. Download the cover art from Audible (if selected)
     * 2. Update selected book metadata fields
     *
     * On success, sets [MetadataUiState.applySuccess] to true. The caller
     * should then refresh the book data and dismiss the metadata flow.
     */
    fun applyMatch() {
        val currentState = state.value
        val asin = currentState.selectedMatch?.asin ?: return
        val bookId = currentState.bookId
        val previewBook = currentState.previewBook ?: return
        val selections = currentState.selections

        viewModelScope.launch {
            state.update {
                it.copy(
                    isApplying = true,
                    applyError = null,
                )
            }

            try {
                val request =
                    buildMatchRequest(
                        asin = asin,
                        region = currentState.selectedRegion.code,
                        selections = selections,
                        previewBook = previewBook,
                        coverUrl = currentState.selectedCoverUrl,
                    )
                metadataRepository.applyMatch(bookId, request)

                // Download cover from server if cover was selected
                if (selections.cover) {
                    val bookIdObj = BookId(bookId)
                    // Delete existing cover to ensure we download the new one
                    imageDownloader.deleteCover(bookIdObj)
                    // Download the new cover from server
                    val downloadResult = imageDownloader.downloadCover(bookIdObj)
                    when (downloadResult) {
                        is com.calypsan.listenup.client.core.Result.Success -> {
                            if (downloadResult.data) {
                                logger.info { "Downloaded new cover for book $bookId" }
                            } else {
                                logger.info { "No cover available on server for book $bookId" }
                            }
                        }

                        is com.calypsan.listenup.client.core.Result.Failure -> {
                            logger.warn {
                                "Failed to download cover for book $bookId: ${downloadResult.exception.message}"
                            }
                        }
                    }
                }

                state.update {
                    it.copy(
                        isApplying = false,
                        applySuccess = true,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error(e) { "Failed to apply metadata match" }
                state.update {
                    it.copy(
                        isApplying = false,
                        applyError = e.message ?: "Failed to apply metadata",
                    )
                }
            }
        }
    }

    /**
     * Reset all state. Call when dismissing the metadata flow.
     */
    fun reset() {
        state.update { MetadataUiState() }
    }

    /**
     * Initialize selections with all available metadata fields selected.
     */
    private fun initializeSelections(preview: MetadataBook): MetadataSelections =
        MetadataSelections(
            cover = preview.coverUrl != null,
            title = preview.title.isNotBlank(),
            subtitle = !preview.subtitle.isNullOrBlank(),
            description = !preview.description.isNullOrBlank(),
            publisher = !preview.publisher.isNullOrBlank(),
            releaseDate = !preview.releaseDate.isNullOrBlank(),
            language = !preview.language.isNullOrBlank(),
            selectedAuthors = preview.authors.mapNotNull { it.asin }.toSet(),
            selectedNarrators = preview.narrators.mapNotNull { it.asin }.toSet(),
            selectedSeries = preview.series.mapNotNull { it.asin }.toSet(),
            selectedGenres = preview.genres.toSet(),
        )

    /**
     * Build the match request from current selections.
     */
    private fun buildMatchRequest(
        asin: String,
        region: String,
        selections: MetadataSelections,
        previewBook: MetadataBook,
        coverUrl: String?,
    ): ApplyMatchRequest =
        ApplyMatchRequest(
            asin = asin,
            region = region,
            fields =
                MatchFields(
                    title = selections.title,
                    subtitle = selections.subtitle,
                    description = selections.description,
                    publisher = selections.publisher,
                    releaseDate = selections.releaseDate,
                    language = selections.language,
                    cover = selections.cover,
                ),
            authors = selections.selectedAuthors.toList(),
            narrators = selections.selectedNarrators.toList(),
            series =
                previewBook.series
                    .filter { it.asin in selections.selectedSeries }
                    .mapNotNull { series ->
                        series.asin?.let { asin ->
                            SeriesMatchEntry(
                                asin = asin,
                                applyName = true,
                                applySequence = true,
                            )
                        }
                    },
            genres = selections.selectedGenres.toList(),
            coverUrl = coverUrl, // Explicit cover URL (overrides Audible if provided)
        )
}

/**
 * Enum for simple metadata fields that can be toggled.
 */
enum class MetadataField {
    COVER,
    TITLE,
    SUBTITLE,
    DESCRIPTION,
    PUBLISHER,
    RELEASE_DATE,
    LANGUAGE,
}
