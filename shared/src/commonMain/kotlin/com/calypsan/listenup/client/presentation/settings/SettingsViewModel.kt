package com.calypsan.listenup.client.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.data.remote.UserPreferencesApiContract
import com.calypsan.listenup.client.data.remote.UserPreferencesRequest
import com.calypsan.listenup.client.data.repository.SettingsRepository
import com.calypsan.listenup.client.data.repository.SettingsRepositoryContract
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

data class SettingsUiState(
    val defaultPlaybackSpeed: Float = SettingsRepository.DEFAULT_PLAYBACK_SPEED,
    val spatialPlayback: Boolean = true,
    val ignoreTitleArticles: Boolean = true,
    val hideSingleBookSeries: Boolean = true,
    val isLoading: Boolean = true,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepositoryContract,
    private val userPreferencesApi: UserPreferencesApiContract,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    defaultPlaybackSpeed = settingsRepository.getDefaultPlaybackSpeed(),
                    spatialPlayback = settingsRepository.getSpatialPlayback(),
                    ignoreTitleArticles = settingsRepository.getIgnoreTitleArticles(),
                    hideSingleBookSeries = settingsRepository.getHideSingleBookSeries(),
                    isLoading = false,
                )
            }
        }
    }

    /**
     * Set the default playback speed for new books.
     * Updates locally immediately (optimistic), syncs to server in background.
     */
    fun setDefaultPlaybackSpeed(speed: Float) {
        viewModelScope.launch {
            // Update local cache immediately (optimistic)
            settingsRepository.setDefaultPlaybackSpeed(speed)
            _state.update { it.copy(defaultPlaybackSpeed = speed) }

            // Sync to server in background (fire-and-forget)
            val result =
                userPreferencesApi.updatePreferences(
                    UserPreferencesRequest(defaultPlaybackSpeed = speed),
                )
            if (result is com.calypsan.listenup.client.core.Failure) {
                // Log but don't revert - server sync will retry on next app launch
                logger.warn { "Failed to sync default playback speed: ${result.exception.message}" }
            }
        }
    }

    fun setSpatialPlayback(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSpatialPlayback(enabled)
            _state.update { it.copy(spatialPlayback = enabled) }
        }
    }

    fun setIgnoreTitleArticles(ignore: Boolean) {
        viewModelScope.launch {
            settingsRepository.setIgnoreTitleArticles(ignore)
            _state.update { it.copy(ignoreTitleArticles = ignore) }
        }
    }

    fun setHideSingleBookSeries(hide: Boolean) {
        viewModelScope.launch {
            settingsRepository.setHideSingleBookSeries(hide)
            _state.update { it.copy(hideSingleBookSeries = hide) }
        }
    }
}
