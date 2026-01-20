package com.calypsan.listenup.client.presentation.bookdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.domain.model.BookEvent
import com.calypsan.listenup.client.domain.model.ReaderInfo
import com.calypsan.listenup.client.domain.model.SessionSummary
import com.calypsan.listenup.client.domain.repository.EventStreamRepository
import com.calypsan.listenup.client.domain.repository.SessionRepository
import com.calypsan.listenup.client.domain.repository.UserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * ViewModel for the Book Readers screen.
 *
 * Displays who else has read or is reading a book, along with
 * the current user's own reading history for that book.
 *
 * Uses offline-first architecture:
 * - Observes local Room cache for instant display
 * - Repository triggers background API refresh automatically
 * - SSE events update the cache for real-time updates
 */
class BookReadersViewModel(
    private val sessionRepository: SessionRepository,
    private val eventStreamRepository: EventStreamRepository,
    private val userRepository: UserRepository,
) : ViewModel() {
    val state: StateFlow<BookReadersUiState>
        field = MutableStateFlow(BookReadersUiState())

    // Track which book we're observing
    private var currentBookId: String? = null
    private var observeJob: Job? = null
    private var sseJob: Job? = null

    /**
     * Start observing readers for a specific book.
     *
     * Observes the local Room cache via Flow for instant updates.
     * The repository automatically triggers a background refresh
     * and SSE events update the cache in real-time.
     *
     * @param bookId Book ID to observe readers for
     */
    fun observeReaders(bookId: String) {
        // Don't restart if already observing this book
        if (currentBookId == bookId && observeJob?.isActive == true) {
            return
        }

        // Cancel previous observations
        observeJob?.cancel()
        sseJob?.cancel()

        currentBookId = bookId

        // Observe local cache (repository triggers background refresh)
        observeJob =
            viewModelScope.launch {
                sessionRepository
                    .observeBookReaders(bookId)
                    .onStart {
                        state.update { it.copy(isLoading = true, error = null) }
                    }.catch { e ->
                        logger.error(e) { "Error observing book readers" }
                        state.update { it.copy(isLoading = false, error = e.message) }
                    }.collect { result ->
                        // Build current user's ReaderInfo from their sessions and profile
                        val currentUserReaderInfo = buildCurrentUserReaderInfo(result.yourSessions)

                        state.update {
                            it.copy(
                                isLoading = false,
                                yourSessions = result.yourSessions,
                                currentUserReaderInfo = currentUserReaderInfo,
                                otherReaders = result.otherReaders,
                                totalReaders = result.totalReaders,
                                totalCompletions = result.totalCompletions,
                                error = null,
                            )
                        }
                        logger.debug {
                            "Readers updated: ${result.otherReaders.size} readers, ${result.yourSessions.size} sessions"
                        }
                    }
            }

        // Also observe SSE events to trigger refresh
        observeSSEEvents(bookId)
    }

    /**
     * Load readers for a specific book (legacy method).
     *
     * Calls [observeReaders] for backwards compatibility.
     *
     * @param bookId Book ID to load readers for
     */
    fun loadReaders(bookId: String) {
        observeReaders(bookId)
    }

    /**
     * Observe book events for reading session updates.
     *
     * When another user starts or completes reading the same book,
     * trigger a cache refresh via the repository.
     */
    private fun observeSSEEvents(bookId: String) {
        sseJob =
            viewModelScope.launch {
                eventStreamRepository.bookEvents.collect { event ->
                    when (event) {
                        is BookEvent.ReadingSessionUpdated -> {
                            // Only refresh if the event is for the current book
                            if (event.bookId == bookId) {
                                logger.debug { "SSE: Reading session updated for book $bookId, refreshing cache" }
                                // Repository refresh will update Room, which will emit new data
                                sessionRepository.refreshBookReaders(bookId)
                            }
                        }
                    }
                }
            }
    }

    /**
     * Manually refresh the readers list.
     *
     * Triggers a background API fetch to update the local cache.
     * The Flow observation will automatically emit the updated data.
     *
     * @param bookId Book ID to refresh readers for
     */
    fun refresh(bookId: String) {
        viewModelScope.launch {
            sessionRepository.refreshBookReaders(bookId)
        }
    }

    /**
     * Build a ReaderInfo for the current user from their sessions.
     *
     * Combines session data with current user profile to create a unified
     * reader representation that can be displayed like any other reader.
     */
    private suspend fun buildCurrentUserReaderInfo(sessions: List<SessionSummary>): ReaderInfo? {
        if (sessions.isEmpty()) return null

        val user = userRepository.getCurrentUser() ?: return null

        // Find the most recent session
        val mostRecent = sessions.maxByOrNull { it.startedAt } ?: return null
        val completionCount = sessions.count { it.isCompleted }
        val isCurrentlyReading = mostRecent.finishedAt == null

        // Compute last activity (most recent of startedAt or finishedAt)
        val lastActivity = mostRecent.finishedAt ?: mostRecent.startedAt

        return ReaderInfo(
            userId = user.id.value,
            displayName = user.displayName,
            avatarType = user.avatarType,
            avatarValue = user.avatarValue,
            avatarColor = user.avatarColor,
            isCurrentlyReading = isCurrentlyReading,
            currentProgress = 0.0, // Progress would need to come from PlaybackState
            startedAt = mostRecent.startedAt,
            finishedAt = mostRecent.finishedAt,
            lastActivityAt = lastActivity,
            completionCount = completionCount,
            isCurrentUser = true,
        )
    }
}

/**
 * UI state for the Book Readers screen.
 */
data class BookReadersUiState(
    val isLoading: Boolean = true,
    val yourSessions: List<SessionSummary> = emptyList(),
    val currentUserReaderInfo: ReaderInfo? = null,
    val otherReaders: List<ReaderInfo> = emptyList(),
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
        get() = currentUserReaderInfo != null

    /**
     * True if there are other readers besides the current user.
     */
    val hasOtherReaders: Boolean
        get() = otherReaders.isNotEmpty()

    /**
     * All readers including current user, sorted by last activity.
     */
    val allReaders: List<ReaderInfo>
        get() =
            buildList {
                currentUserReaderInfo?.let { add(it) }
                addAll(otherReaders)
            }.sortedByDescending { it.lastActivityAt }

    /**
     * Number of other readers currently reading (not completed).
     */
    val currentlyReadingCount: Int
        get() = otherReaders.count { it.isCurrentlyReading }
}
