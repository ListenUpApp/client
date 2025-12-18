package com.calypsan.listenup.client.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.data.repository.SettingsRepositoryContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val spatialPlayback: Boolean = true,
    val ignoreTitleArticles: Boolean = true,
    val isLoading: Boolean = true,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepositoryContract,
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
                    spatialPlayback = settingsRepository.getSpatialPlayback(),
                    ignoreTitleArticles = settingsRepository.getIgnoreTitleArticles(),
                    isLoading = false,
                )
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
}
