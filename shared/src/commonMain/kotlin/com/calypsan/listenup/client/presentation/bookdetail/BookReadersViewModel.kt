package com.calypsan.listenup.client.presentation.bookdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.data.remote.ReaderSummary
import com.calypsan.listenup.client.data.remote.SessionApiContract
import com.calypsan.listenup.client.data.remote.SessionSummary
import com.calypsan.listenup.client.data.sync.SSEEventType
import com.calypsan.listenup.client.data.sync.SSEManagerContract
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * ViewModel for the Book Readers screen.
 *
 * Displays who else has read or is reading a book, along with
 * the current user's own reading history for that book.
 *
 * Observes SSE events for real-time updates when other users
 * start or complete reading the same book.
 */
class BookReadersViewModel(
    private val sessionApi: SessionApiContract,
    private val sseManager: SSEManagerContract,
) : ViewModel() {
    val state: StateFlow<BookReadersUiState>
        field = MutableStateFlow(BookReadersUiState())

    // Track which book we're observing for SSE events
    private var currentBookId: String? = null

    /**
     * Load readers for a specific book.
     *
     * Also starts observing SSE events for real-time updates when
     * other users start or complete reading this book.
     *
     * @param bookId Book ID to load readers for
     */
    fun loadReaders(bookId: String) {
        // Track the current book for SSE filtering
        if (currentBookId != bookId) {
            currentBookId = bookId
            observeSSEEvents(bookId)
        }

        viewModelScope.launch {
            state.update { it.copy(isLoading = true, error = null) }

            when (val result = sessionApi.getBookReaders(bookId)) {
                is Result.Success -> {
                    state.update {
                        it.copy(
                            isLoading = false,
                            yourSessions = result.data.yourSessions,
                            otherReaders = result.data.otherReaders,
                            totalReaders = result.data.totalReaders,
                            totalCompletions = result.data.totalCompletions,
                            error = null,
                        )
                    }
                    logger.debug {
                        "Loaded ${result.data.otherReaders.size} readers and ${result.data.yourSessions.size} sessions"
                    }
                }

                is Result.Failure -> {
                    state.update {
                        it.copy(
                            isLoading = false,
                            error = result.message,
                        )
                    }
                    logger.error(result.exception) { "Failed to load book readers: ${result.message}" }
                }
            }
        }
    }

    /**
     * Observe SSE events for reading session updates.
     *
     * When another user starts or completes reading the same book,
     * automatically refresh the readers list.
     */
    private fun observeSSEEvents(bookId: String) {
        viewModelScope.launch {
            sseManager.eventFlow
                .filterIsInstance<SSEEventType.ReadingSessionUpdated>()
                .collect { event ->
                    // Only refresh if the event is for the current book
                    if (event.bookId == bookId) {
                        logger.debug { "SSE: Reading session updated for book $bookId, refreshing readers" }
                        refreshQuietly(bookId)
                    }
                }
        }
    }

    /**
     * Refresh readers without showing loading indicator.
     * Used for background updates from SSE events.
     */
    private fun refreshQuietly(bookId: String) {
        viewModelScope.launch {
            when (val result = sessionApi.getBookReaders(bookId)) {
                is Result.Success -> {
                    state.update {
                        it.copy(
                            yourSessions = result.data.yourSessions,
                            otherReaders = result.data.otherReaders,
                            totalReaders = result.data.totalReaders,
                            totalCompletions = result.data.totalCompletions,
                        )
                    }
                    logger.debug { "SSE refresh: Updated readers list" }
                }

                is Result.Failure -> {
                    // Silently ignore refresh failures - user still has existing data
                    logger.warn { "SSE refresh failed: ${result.message}" }
                }
            }
        }
    }

    /**
     * Refresh the readers list.
     *
     * @param bookId Book ID to refresh readers for
     */
    fun refresh(bookId: String) {
        loadReaders(bookId)
    }
}

/**
 * UI state for the Book Readers screen.
 */
data class BookReadersUiState(
    val isLoading: Boolean = true,
    val yourSessions: List<SessionSummary> = emptyList(),
    val otherReaders: List<ReaderSummary> = emptyList(),
    val totalReaders: Int = 0,
    val totalCompletions: Int = 0,
    val error: String? = null,
) {
    /**
     * True if there are no sessions or readers to display.
     */
    val isEmpty: Boolean
        get() = yourSessions.isEmpty() && otherReaders.isEmpty()

    /**
     * True if the current user has any reading history for this book.
     */
    val hasYourHistory: Boolean
        get() = yourSessions.isNotEmpty()

    /**
     * True if there are other readers besides the current user.
     */
    val hasOtherReaders: Boolean
        get() = otherReaders.isNotEmpty()

    /**
     * Number of other readers currently reading (not completed).
     */
    val currentlyReadingCount: Int
        get() = otherReaders.count { it.isCurrentlyReading }
}
