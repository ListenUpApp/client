package com.calypsan.listenup.client.presentation.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.local.images.ImageStorage
import com.calypsan.listenup.client.data.remote.CurrentlyListeningBookResponse
import com.calypsan.listenup.client.data.remote.CurrentlyListeningReaderResponse
import com.calypsan.listenup.client.data.remote.DiscoverBookResponse
import com.calypsan.listenup.client.data.remote.DiscoveryApiContract
import com.calypsan.listenup.client.data.remote.LensApiContract
import com.calypsan.listenup.client.data.remote.UserLensesResponse
import com.calypsan.listenup.client.data.sync.SSEEventType
import com.calypsan.listenup.client.data.sync.SSEManagerContract
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * ViewModel for the Discover screen.
 *
 * Manages multiple discovery features:
 * - What others are listening to (social proof) with real-time SSE updates
 * - Random book discovery
 * - Lenses from other users
 */
class DiscoverViewModel(
    private val lensApi: LensApiContract,
    private val discoveryApi: DiscoveryApiContract,
    private val sseManager: SSEManagerContract,
    private val imageStorage: ImageStorage,
) : ViewModel() {
    val state: StateFlow<DiscoverUiState>
        field = MutableStateFlow(DiscoverUiState())

    val currentlyListeningState: StateFlow<CurrentlyListeningUiState>
        field = MutableStateFlow(CurrentlyListeningUiState())

    val discoverBooksState: StateFlow<DiscoverBooksUiState>
        field = MutableStateFlow(DiscoverBooksUiState())

    init {
        loadAll()
        observeSseEvents()
    }

    /**
     * Load all discovery content.
     */
    private fun loadAll() {
        loadCurrentlyListening()
        loadDiscoverBooks()
        loadDiscoverLenses()
    }

    /**
     * Observe SSE events for real-time updates to "What Others Are Listening To".
     *
     * When a reading session is updated (started or ended), the section is refreshed
     * to show the current state of who's listening to what.
     */
    private fun observeSseEvents() {
        viewModelScope.launch {
            sseManager.eventFlow.collect { event ->
                when (event) {
                    is SSEEventType.ReadingSessionUpdated -> {
                        logger.debug {
                            "SSE: Reading session updated - book=${event.bookId}, completed=${event.isCompleted}"
                        }
                        // Refresh the "What Others Are Listening To" section
                        loadCurrentlyListening()
                    }
                    else -> {
                        // Ignore other events
                    }
                }
            }
        }
    }

    /**
     * Load books that others are currently listening to.
     */
    fun loadCurrentlyListening() {
        viewModelScope.launch {
            currentlyListeningState.update { it.copy(isLoading = true, error = null) }

            try {
                val response = discoveryApi.getCurrentlyListening(limit = 10)
                logger.info { "Currently listening API returned ${response.books.size} books" }
                response.books.forEach { book ->
                    logger.debug { "  - ${book.title} with ${book.readers.size} readers (total: ${book.totalReaderCount})" }
                }
                // Resolve local cover paths for each book
                val booksWithLocalCovers = response.books.map { book ->
                    val bookId = BookId(book.id)
                    val localCoverPath = if (imageStorage.exists(bookId)) {
                        imageStorage.getCoverPath(bookId)
                    } else {
                        null
                    }
                    CurrentlyListeningUiBook(
                        id = book.id,
                        title = book.title,
                        authorName = book.authorName,
                        coverPath = localCoverPath,
                        coverBlurHash = book.coverBlurHash,
                        readers = book.readers,
                        totalReaderCount = book.totalReaderCount,
                    )
                }
                currentlyListeningState.update {
                    it.copy(
                        isLoading = false,
                        books = booksWithLocalCovers,
                        error = null,
                    )
                }
                logger.debug { "Loaded ${response.books.size} books others are listening to" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to load currently listening" }
                currentlyListeningState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load: ${e.message}",
                    )
                }
            }
        }
    }

    /**
     * Load random books for discovery.
     */
    fun loadDiscoverBooks() {
        viewModelScope.launch {
            discoverBooksState.update { it.copy(isLoading = true, error = null) }

            try {
                val response = discoveryApi.getDiscoverBooks(limit = 10)
                // Resolve local cover paths for each book
                val booksWithLocalCovers = response.books.map { book ->
                    val bookId = BookId(book.id)
                    val localCoverPath = if (imageStorage.exists(bookId)) {
                        imageStorage.getCoverPath(bookId)
                    } else {
                        null
                    }
                    DiscoverUiBook(
                        id = book.id,
                        title = book.title,
                        authorName = book.authorName,
                        coverPath = localCoverPath,
                        coverBlurHash = book.coverBlurHash,
                        seriesName = book.seriesName,
                    )
                }
                discoverBooksState.update {
                    it.copy(
                        isLoading = false,
                        books = booksWithLocalCovers,
                        error = null,
                    )
                }
                logger.debug { "Loaded ${response.books.size} discovery books" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to load discover books" }
                discoverBooksState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load: ${e.message}",
                    )
                }
            }
        }
    }

    /**
     * Refresh discover books with a new random selection.
     */
    fun refreshDiscoverBooks() = loadDiscoverBooks()

    /**
     * Load discovered lenses from other users.
     */
    fun loadDiscoverLenses() {
        viewModelScope.launch {
            state.update { it.copy(isLoading = true, error = null) }

            try {
                val users = lensApi.discoverLenses()
                state.update {
                    it.copy(
                        isLoading = false,
                        users = users,
                        error = null,
                    )
                }
                logger.debug { "Loaded ${users.size} users with discoverable lenses" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to load discover lenses" }
                state.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load: ${e.message}",
                    )
                }
            }
        }
    }

    /**
     * Refresh all discovery content.
     */
    fun refresh() = loadAll()
}

/**
 * UI state for the Discover screen (lenses section).
 */
data class DiscoverUiState(
    val isLoading: Boolean = true,
    val users: List<UserLensesResponse> = emptyList(),
    val error: String? = null,
) {
    val isEmpty: Boolean
        get() = users.isEmpty() && !isLoading

    val totalLensCount: Int
        get() = users.sumOf { it.lenses.size }
}

/**
 * UI state for the "What Others Are Listening To" section.
 */
data class CurrentlyListeningUiState(
    val isLoading: Boolean = false,
    val books: List<CurrentlyListeningUiBook> = emptyList(),
    val error: String? = null,
) {
    val isEmpty: Boolean
        get() = books.isEmpty() && !isLoading
}

/**
 * UI state for the "Discover Something New" section.
 */
data class DiscoverBooksUiState(
    val isLoading: Boolean = false,
    val books: List<DiscoverUiBook> = emptyList(),
    val error: String? = null,
) {
    val isEmpty: Boolean
        get() = books.isEmpty() && !isLoading
}

// === UI Model Types ===

/**
 * Book for "What Others Are Listening To" with resolved local cover path.
 */
data class CurrentlyListeningUiBook(
    val id: String,
    val title: String,
    val authorName: String?,
    val coverPath: String?,
    val coverBlurHash: String?,
    val readers: List<CurrentlyListeningReaderResponse>,
    val totalReaderCount: Int,
)

/**
 * Book for "Discover Something New" with resolved local cover path.
 */
data class DiscoverUiBook(
    val id: String,
    val title: String,
    val authorName: String?,
    val coverPath: String?,
    val coverBlurHash: String?,
    val seriesName: String?,
)
