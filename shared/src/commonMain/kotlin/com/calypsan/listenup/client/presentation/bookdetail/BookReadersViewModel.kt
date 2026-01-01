package com.calypsan.listenup.client.presentation.bookdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.data.remote.ReaderSummary
import com.calypsan.listenup.client.data.remote.SessionApiContract
import com.calypsan.listenup.client.data.remote.SessionSummary
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * ViewModel for the Book Readers screen.
 *
 * Displays who else has read or is reading a book, along with
 * the current user's own reading history for that book.
 */
class BookReadersViewModel(
    private val sessionApi: SessionApiContract,
) : ViewModel() {
    val state: StateFlow<BookReadersUiState>
        field = MutableStateFlow(BookReadersUiState())

    /**
     * Load readers for a specific book.
     *
     * @param bookId Book ID to load readers for
     */
    fun loadReaders(bookId: String) {
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
