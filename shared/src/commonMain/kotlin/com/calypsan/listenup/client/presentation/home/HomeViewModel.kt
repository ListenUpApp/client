package com.calypsan.listenup.client.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.core.currentHourOfDay
import com.calypsan.listenup.client.data.local.db.BookDao
import com.calypsan.listenup.client.data.local.db.LensDao
import com.calypsan.listenup.client.data.local.db.LensEntity
import com.calypsan.listenup.client.data.repository.HomeRepositoryContract
import com.calypsan.listenup.client.domain.model.ContinueListeningBook
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * ViewModel for the Home screen.
 *
 * Manages:
 * - Time-aware greeting with user's name
 * - Continue listening books list
 * - My lenses list
 * - Loading and error states
 *
 * @property homeRepository Repository for home screen data
 * @property bookDao DAO for observing book changes (cover downloads)
 * @property lensDao DAO for lens data
 */
class HomeViewModel(
    private val homeRepository: HomeRepositoryContract,
    private val bookDao: BookDao,
    private val lensDao: LensDao,
    private val currentHour: () -> Int = { currentHourOfDay() },
) : ViewModel() {
    val state: StateFlow<HomeUiState>
        field = MutableStateFlow(HomeUiState(timeGreeting = computeTimeGreeting()))

    // Store user ID for lens observation
    private var currentUserId: String? = null

    init {
        observeUser()
        loadHomeData()
        observeBookChanges()
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
            homeRepository.observeCurrentUser().collect { user ->
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
            lensDao.observeMyLenses(userId).collect { lenses ->
                state.update { it.copy(myLenses = lenses) }
                logger.debug { "My lenses updated: ${lenses.size}" }
            }
        }
    }

    /**
     * Observe book changes to refresh Continue Listening when covers download.
     *
     * When cover images are downloaded, bookDao.touchUpdatedAt() is called,
     * which triggers this observer. We reload Continue Listening to pick up
     * the new cover paths.
     */
    private fun observeBookChanges() {
        viewModelScope.launch {
            // Observe book count changes (crude but effective trigger)
            // This detects when books are added/modified
            bookDao.observeAll()
                .map { books -> books.maxOfOrNull { it.updatedAt } ?: 0L }
                .distinctUntilChanged()
                .collect { latestUpdate ->
                    // Skip initial load (handled by loadHomeData)
                    if (state.value.continueListening.isNotEmpty()) {
                        logger.debug { "Book data changed, reloading Continue Listening" }
                        loadHomeData()
                    }
                }
        }
    }

    /**
     * Load home screen data.
     *
     * Fetches continue listening books from the server.
     */
    fun loadHomeData() {
        viewModelScope.launch {
            state.update { it.copy(isLoading = true, error = null) }

            when (val result = homeRepository.getContinueListening(10)) {
                is Success -> {
                    state.update {
                        it.copy(
                            isLoading = false,
                            continueListening = result.data,
                            error = null,
                        )
                    }
                    logger.debug { "Loaded ${result.data.size} continue listening books" }
                }

                else -> {
                    val errorMessage = "Failed to load continue listening"
                    state.update {
                        it.copy(
                            isLoading = false,
                            error = errorMessage,
                        )
                    }
                    logger.error { errorMessage }
                }
            }
        }
    }

    /**
     * Refresh home screen data.
     *
     * Called by pull-to-refresh.
     */
    fun refresh() {
        loadHomeData()
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
    val myLenses: List<LensEntity> = emptyList(),
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
