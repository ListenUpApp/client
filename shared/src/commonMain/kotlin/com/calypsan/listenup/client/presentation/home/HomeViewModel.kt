package com.calypsan.listenup.client.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.core.currentHourOfDay
import com.calypsan.listenup.client.domain.model.ContinueListeningBook
import com.calypsan.listenup.client.domain.model.Lens
import com.calypsan.listenup.client.domain.repository.HomeRepository
import com.calypsan.listenup.client.domain.repository.LensRepository
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
 * - My lenses list
 * - Loading and error states
 *
 * @property homeRepository Repository for home screen data
 * @property userRepository Repository for current user data
 * @property lensRepository Repository for lens data
 */
class HomeViewModel(
    private val homeRepository: HomeRepository,
    private val userRepository: UserRepository,
    private val lensRepository: LensRepository,
    private val currentHour: () -> Int = { currentHourOfDay() },
) : ViewModel() {
    val state: StateFlow<HomeUiState>
        field = MutableStateFlow(HomeUiState(timeGreeting = computeTimeGreeting()))

    // Store user ID for lens observation
    private var currentUserId: String? = null

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
                }
                .collect { books ->
                    logger.debug { "Continue listening updated: ${books.size} books" }
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
     * Observe current user for greeting and lens loading.
     *
     * Updates the greeting whenever user data changes.
     * Falls back to generic greeting if no user name available.
     * Also starts observing lenses when user ID is available.
     */
    private fun observeUser() {
        viewModelScope.launch {
            userRepository.observeCurrentUser().collect { user ->
                val firstName = extractFirstName(user?.displayName) ?: ""

                state.update { it.copy(userName = firstName) }
                logger.debug { "User first name updated: $firstName" }

                // Start observing lenses when we have a user ID
                val userId = user?.id
                if (userId != null && userId != currentUserId) {
                    currentUserId = userId
                    observeMyLenses(userId)
                }
            }
        }
    }

    /**
     * Observe my lenses from the local database.
     */
    private fun observeMyLenses(userId: String) {
        viewModelScope.launch {
            lensRepository.observeMyLenses(userId).collect { lenses ->
                state.update { it.copy(myLenses = lenses) }
                logger.debug { "My lenses updated: ${lenses.size}" }
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
    val myLenses: List<Lens> = emptyList(),
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
     * Whether there are lenses to display.
     */
    val hasMyLenses: Boolean
        get() = myLenses.isNotEmpty()
}
