package com.calypsan.listenup.client.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.core.currentHourOfDay
import com.calypsan.listenup.client.domain.model.ContinueListeningBook
import com.calypsan.listenup.client.domain.model.Shelf
import com.calypsan.listenup.client.domain.repository.HomeRepository
import com.calypsan.listenup.client.domain.repository.ShelfRepository
import com.calypsan.listenup.client.domain.repository.UserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * ViewModel for the Home screen.
 *
 * Manages:
 * - Time-aware greeting with user's name
 * - Continue listening books list (real-time via local observation)
 * - My shelves list
 * - Loading and error states
 *
 * @property homeRepository Repository for home screen data
 * @property userRepository Repository for current user data
 * @property shelfRepository Repository for shelf data
 */
class HomeViewModel(
    private val homeRepository: HomeRepository,
    private val userRepository: UserRepository,
    private val shelfRepository: ShelfRepository,
    private val currentHour: () -> Int = { currentHourOfDay() },
) : ViewModel() {
    val state: StateFlow<HomeUiState>
        field = MutableStateFlow(HomeUiState(timeGreeting = computeTimeGreeting()))

    // Store user ID for shelf observation
    private var currentUserId: String? = null
    private var hasFetchedShelves = false

    init {
        observeUser()
        observeContinueListening()
    }

    /**
     * Observe continue listening books from local database.
     *
     * This is reactive - whenever a playback position changes in the database,
     * the UI updates automatically. No manual refresh needed.
     */
    private fun observeContinueListening() {
        viewModelScope.launch {
            homeRepository
                .observeContinueListening(10)
                .catch { e ->
                    logger.error(e) { "Error observing continue listening" }
                    state.update {
                        it.copy(
                            isLoading = false,
                            error = "Failed to load continue listening",
                        )
                    }
                }.collect { books ->
                    val finishedCount = books.count { it.progress >= 0.99f }
                    logger.info {
                        "HomeViewModel.collect: ${books.size} books received " +
                            "(finishedCount=$finishedCount, titles=${books.take(3).map { it.title }})"
                    }
                    state.update {
                        it.copy(
                            isLoading = false,
                            continueListening = books,
                            error = null,
                        )
                    }
                }
        }
    }

    /**
     * Compute time-based greeting from current hour.
     *
     * Separated from state to enable testing with mocked time.
     */
    private fun computeTimeGreeting(): String =
        when (currentHour()) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..20 -> "Good evening"
            else -> "Good night"
        }

    /**
     * Observe current user for greeting and shelf loading.
     *
     * Updates the greeting whenever user data changes.
     * Falls back to generic greeting if no user name available.
     * Also starts observing shelves when user ID is available.
     */
    private fun observeUser() {
        viewModelScope.launch {
            userRepository.observeCurrentUser().collect { user ->
                val firstName = extractFirstName(user?.displayName) ?: ""

                state.update { it.copy(userName = firstName) }
                logger.debug { "User first name updated: $firstName" }

                // Start observing shelves when we have a user ID
                val userId = user?.id?.value
                if (userId != null && userId != currentUserId) {
                    currentUserId = userId
                    observeMyShelves(userId)
                }
            }
        }
    }

    /**
     * Observe my shelves from the local database.
     *
     * If the first emission is empty and we haven't fetched yet,
     * triggers a network fetch to populate Room.
     */
    private fun observeMyShelves(userId: String) {
        viewModelScope.launch {
            shelfRepository.observeMyShelves(userId).collect { shelves ->
                state.update { it.copy(myShelves = shelves) }
                logger.debug { "My shelves updated: ${shelves.size}" }

                if (shelves.isEmpty() && !hasFetchedShelves) {
                    hasFetchedShelves = true
                    try {
                        shelfRepository.fetchAndCacheMyShelves()
                    } catch (e: Exception) {
                        logger.warn(e) { "Failed to fetch shelves from network" }
                    }
                }
            }
        }
    }

    /**
     * Refresh home screen data.
     *
     * Since continue listening is observed from local Room data, this is a no-op.
     * The Flow automatically updates when playback positions change.
     * Pull-to-refresh triggers sync elsewhere, which updates Room,
     * causing the Flow to emit new data.
     */
    fun refresh() {
        // No-op - data auto-updates from Room Flow
        logger.debug { "Refresh requested - data will update automatically from Room" }
    }

    /**
     * Extract first name from a full display name.
     *
     * @param displayName Full display name (e.g., "John Smith")
     * @return First name or null
     */
    private fun extractFirstName(displayName: String?): String? {
        if (displayName.isNullOrBlank()) return null
        return displayName.split(" ").firstOrNull()?.takeIf { it.isNotBlank() }
    }
}

/**
 * UI state for the Home screen.
 *
 * Immutable data class that represents the complete UI state.
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val userName: String = "",
    val timeGreeting: String = "Good morning",
    val continueListening: List<ContinueListeningBook> = emptyList(),
    val myShelves: List<Shelf> = emptyList(),
    val error: String? = null,
) {
    /**
     * Full greeting combining time-based greeting with user name.
     *
     * Time greeting is computed by ViewModel (enables testing with mocked time).
     * - 5-11: "Good morning"
     * - 12-16: "Good afternoon"
     * - 17-20: "Good evening"
     * - 21-4: "Good night"
     */
    val greeting: String
        get() =
            if (userName.isNotBlank()) {
                "$timeGreeting, $userName"
            } else {
                timeGreeting
            }

    /**
     * Whether there are books to continue listening to.
     */
    val hasContinueListening: Boolean
        get() = continueListening.isNotEmpty()

    /**
     * Whether there are shelves to display.
     */
    val hasMyShelves: Boolean
        get() = myShelves.isNotEmpty()
}
