package com.calypsan.listenup.client.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.data.repository.HomeRepositoryContract
import com.calypsan.listenup.client.domain.model.ContinueListeningBook
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val logger = KotlinLogging.logger {}

/**
 * ViewModel for the Home screen.
 *
 * Manages:
 * - Time-aware greeting with user's name
 * - Continue listening books list
 * - Loading and error states
 *
 * @property homeRepository Repository for home screen data
 */
class HomeViewModel(
    private val homeRepository: HomeRepositoryContract,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        observeUser()
        loadHomeData()
    }

    /**
     * Observe current user for greeting.
     *
     * Updates the greeting whenever user data changes.
     * Falls back to generic greeting if no user name available.
     */
    private fun observeUser() {
        viewModelScope.launch {
            homeRepository.observeCurrentUser().collect { user ->
                val firstName = extractFirstName(user?.displayName) ?: ""

                _state.update { it.copy(userName = firstName) }
                logger.debug { "User first name updated: $firstName" }
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
            _state.update { it.copy(isLoading = true, error = null) }

            when (val result = homeRepository.getContinueListening(10)) {
                is Success -> {
                    _state.update {
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
                    _state.update {
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
    fun refresh() = loadHomeData()

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
@OptIn(ExperimentalTime::class)
data class HomeUiState(
    val isLoading: Boolean = true,
    val userName: String = "",
    val continueListening: List<ContinueListeningBook> = emptyList(),
    val error: String? = null,
) {
    /**
     * Time-aware greeting based on current hour.
     *
     * - 5-11: "Good morning"
     * - 12-16: "Good afternoon"
     * - 17-20: "Good evening"
     * - 21-4: "Good night"
     */
    val greeting: String
        get() {
            val now = Clock.System.now()
            val localTime = now.toLocalDateTime(TimeZone.currentSystemDefault())
            val hour = localTime.hour

            val timeGreeting =
                when (hour) {
                    in 5..11 -> "Good morning"
                    in 12..16 -> "Good afternoon"
                    in 17..20 -> "Good evening"
                    else -> "Good night"
                }

            return if (userName.isNotBlank()) {
                "$timeGreeting, $userName"
            } else {
                timeGreeting
            }
        }

    /**
     * Whether there are books to continue listening to.
     */
    val hasContinueListening: Boolean
        get() = continueListening.isNotEmpty()
}
