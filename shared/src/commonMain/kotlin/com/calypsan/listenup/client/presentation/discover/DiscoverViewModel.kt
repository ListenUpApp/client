package com.calypsan.listenup.client.presentation.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.data.remote.LensApiContract
import com.calypsan.listenup.client.data.remote.UserLensesResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * ViewModel for the Discover screen.
 *
 * Fetches and displays lenses from other users.
 */
class DiscoverViewModel(
    private val lensApi: LensApiContract,
) : ViewModel() {
    val state: StateFlow<DiscoverUiState>
        field = MutableStateFlow(DiscoverUiState())

    init {
        loadDiscoverLenses()
    }

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
     * Refresh the discover list.
     */
    fun refresh() = loadDiscoverLenses()
}

/**
 * UI state for the Discover screen.
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
