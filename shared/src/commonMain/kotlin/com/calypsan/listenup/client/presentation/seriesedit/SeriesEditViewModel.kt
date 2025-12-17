package com.calypsan.listenup.client.presentation.seriesedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.data.local.db.SeriesDao
import com.calypsan.listenup.client.data.local.images.ImageStorage
import com.calypsan.listenup.client.data.remote.ImageApiContract
import com.calypsan.listenup.client.data.repository.SeriesEditRepositoryContract
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * UI state for series editing screen.
 */
data class SeriesEditUiState(
    // Loading states
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isUploadingCover: Boolean = false,
    val error: String? = null,
    // Series identity
    val seriesId: String = "",
    val name: String = "",
    val description: String = "",
    // Cover management
    val coverPath: String? = null,
    val stagingCoverPath: String? = null,
    val pendingCoverData: ByteArray? = null,
    val pendingCoverFilename: String? = null,
    // Display metadata
    val bookCount: Int = 0,
    // Track if changes have been made
    val hasChanges: Boolean = false,
) {
    /**
     * Returns the cover path to display - staging if available, otherwise original.
     */
    val displayCoverPath: String?
        get() = stagingCoverPath ?: coverPath
}

/**
 * Events from the series edit UI.
 */
sealed interface SeriesEditUiEvent {
    data class NameChanged(
        val name: String,
    ) : SeriesEditUiEvent

    data class DescriptionChanged(
        val description: String,
    ) : SeriesEditUiEvent

    data class CoverSelected(
        val imageData: ByteArray,
        val filename: String,
    ) : SeriesEditUiEvent

    data object CoverRemoved : SeriesEditUiEvent

    data object SaveClicked : SeriesEditUiEvent

    data object CancelClicked : SeriesEditUiEvent

    data object ErrorDismissed : SeriesEditUiEvent
}

/**
 * Navigation actions from series edit screen.
 */
sealed interface SeriesEditNavAction {
    data object NavigateBack : SeriesEditNavAction
}

/**
 * ViewModel for the series edit screen.
 *
 * Handles:
 * - Loading series data for editing
 * - Saving metadata changes
 * - Cover image staging and upload
 * - Tracking unsaved changes
 *
 * @property seriesDao DAO for loading series data
 * @property seriesEditRepository Repository for saving edits
 * @property imageStorage Local image storage for cover staging
 * @property imageApi API for uploading cover images
 */
class SeriesEditViewModel(
    private val seriesDao: SeriesDao,
    private val seriesEditRepository: SeriesEditRepositoryContract,
    private val imageStorage: ImageStorage,
    private val imageApi: ImageApiContract,
) : ViewModel() {
    private val _state = MutableStateFlow(SeriesEditUiState())
    val state: StateFlow<SeriesEditUiState> = _state.asStateFlow()

    private val _navActions = MutableStateFlow<SeriesEditNavAction?>(null)
    val navActions: StateFlow<SeriesEditNavAction?> = _navActions.asStateFlow()

    // Track original values for change detection
    private var originalName: String = ""
    private var originalDescription: String = ""
    private var originalCoverPath: String? = null

    /**
     * Load series data for editing.
     */
    fun loadSeries(seriesId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, seriesId = seriesId) }

            val seriesWithBooks = seriesDao.getByIdWithBooks(seriesId)
            if (seriesWithBooks == null) {
                _state.update { it.copy(isLoading = false, error = "Series not found") }
                return@launch
            }

            val series = seriesWithBooks.series
            val bookCount = seriesWithBooks.books.size

            // Get cover path if it exists
            val coverPath =
                if (imageStorage.seriesCoverExists(seriesId)) {
                    imageStorage.getSeriesCoverPath(seriesId)
                } else {
                    null
                }

            // Store original values
            originalName = series.name
            originalDescription = series.description ?: ""
            originalCoverPath = coverPath

            _state.update {
                it.copy(
                    isLoading = false,
                    name = series.name,
                    description = series.description ?: "",
                    coverPath = coverPath,
                    bookCount = bookCount,
                    hasChanges = false,
                )
            }

            logger.debug { "Loaded series for editing: ${series.name}, bookCount=$bookCount" }
        }
    }

    /**
     * Handle UI events.
     */
    fun onEvent(event: SeriesEditUiEvent) {
        when (event) {
            is SeriesEditUiEvent.NameChanged -> {
                _state.update { it.copy(name = event.name) }
                updateHasChanges()
            }

            is SeriesEditUiEvent.DescriptionChanged -> {
                _state.update { it.copy(description = event.description) }
                updateHasChanges()
            }

            is SeriesEditUiEvent.CoverSelected -> {
                handleCoverSelected(event.imageData, event.filename)
            }

            is SeriesEditUiEvent.CoverRemoved -> {
                handleCoverRemoved()
            }

            is SeriesEditUiEvent.SaveClicked -> {
                saveChanges()
            }

            is SeriesEditUiEvent.CancelClicked -> {
                cancelAndCleanup()
            }

            is SeriesEditUiEvent.ErrorDismissed -> {
                _state.update { it.copy(error = null) }
            }
        }
    }

    /**
     * Clear navigation action after handling.
     */
    fun consumeNavAction() {
        _navActions.value = null
    }

    /**
     * Update hasChanges flag based on current vs original values.
     */
    private fun updateHasChanges() {
        val current = _state.value
        val hasChanges =
            current.name != originalName ||
                current.description != originalDescription ||
                current.pendingCoverData != null // Cover changed if we have pending data

        _state.update { it.copy(hasChanges = hasChanges) }
    }

    /**
     * Handle cover selection.
     * Saves the image to a staging location for preview.
     * Does NOT overwrite the main cover until saveChanges() is called.
     */
    private fun handleCoverSelected(
        imageData: ByteArray,
        filename: String,
    ) {
        val seriesId = _state.value.seriesId
        if (seriesId.isBlank()) {
            logger.error { "Cannot set cover: series ID is empty" }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isUploadingCover = true, error = null) }

            // Save to staging location for preview (doesn't overwrite original)
            when (val saveResult = imageStorage.saveSeriesCoverStaging(seriesId, imageData)) {
                is Success -> {
                    val stagingPath = imageStorage.getSeriesCoverStagingPath(seriesId)
                    logger.info { "Cover saved to staging for preview: $stagingPath" }

                    // Store pending data for upload when Save Changes is clicked
                    _state.update {
                        it.copy(
                            isUploadingCover = false,
                            stagingCoverPath = stagingPath,
                            pendingCoverData = imageData,
                            pendingCoverFilename = filename,
                        )
                    }
                    updateHasChanges()
                }

                is Failure -> {
                    logger.error { "Failed to save cover to staging: ${saveResult.message}" }
                    _state.update {
                        it.copy(
                            isUploadingCover = false,
                            error = "Failed to save cover: ${saveResult.message}",
                        )
                    }
                }
            }
        }
    }

    /**
     * Handle cover removal.
     * Deletes the staging cover and clears pending data.
     */
    private fun handleCoverRemoved() {
        val seriesId = _state.value.seriesId
        if (seriesId.isBlank()) {
            logger.error { "Cannot remove cover: series ID is empty" }
            return
        }

        viewModelScope.launch {
            // Delete staging cover if it exists
            if (_state.value.stagingCoverPath != null) {
                imageStorage.deleteSeriesCoverStaging(seriesId)
            }

            _state.update {
                it.copy(
                    stagingCoverPath = null,
                    pendingCoverData = null,
                    pendingCoverFilename = null,
                )
            }
            updateHasChanges()

            logger.debug { "Staging cover removed" }
        }
    }

    /**
     * Save all changes to server and local database.
     */
    @Suppress("CognitiveComplexMethod")
    private fun saveChanges() {
        val current = _state.value
        if (!current.hasChanges) {
            _navActions.value = SeriesEditNavAction.NavigateBack
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }

            try {
                // Update metadata if changed
                val metadataChanged =
                    current.name != originalName ||
                        current.description != originalDescription

                if (metadataChanged) {
                    val name = if (current.name != originalName) current.name else null
                    val description =
                        if (current.description != originalDescription) {
                            current.description.ifBlank { null }
                        } else {
                            null
                        }

                    when (val result = seriesEditRepository.updateSeries(current.seriesId, name, description)) {
                        is Success -> {
                            logger.info { "Series metadata updated" }
                        }

                        is Failure -> {
                            _state.update { it.copy(isSaving = false, error = "Failed to save: ${result.message}") }
                            return@launch
                        }
                    }
                }

                // Commit staging cover and upload if changed
                val pendingCoverData = current.pendingCoverData
                val pendingCoverFilename = current.pendingCoverFilename
                if (pendingCoverData != null && pendingCoverFilename != null) {
                    // First, commit staging to main cover location
                    when (val commitResult = imageStorage.commitSeriesCoverStaging(current.seriesId)) {
                        is Success -> {
                            logger.info { "Staging cover committed to main location" }
                        }

                        is Failure -> {
                            logger.error { "Failed to commit staging cover: ${commitResult.message}" }
                            // Continue anyway - try to upload
                        }
                    }

                    // Then upload to server
                    when (
                        val result =
                            imageApi.uploadSeriesCover(
                                current.seriesId,
                                pendingCoverData,
                                pendingCoverFilename,
                            )
                    ) {
                        is Success -> {
                            logger.info { "Cover uploaded to server" }
                        }

                        is Failure -> {
                            logger.error { "Failed to upload cover: ${result.message}" }
                            // Cover is already saved locally, log but don't fail the save
                            logger.warn { "Continuing despite cover upload failure (local cover saved)" }
                        }
                    }
                }

                _state.update {
                    it.copy(
                        isSaving = false,
                        hasChanges = false,
                        pendingCoverData = null,
                        pendingCoverFilename = null,
                        stagingCoverPath = null,
                    )
                }
                _navActions.value = SeriesEditNavAction.NavigateBack
            } catch (e: Exception) {
                logger.error(e) { "Failed to save series changes" }
                _state.update { it.copy(isSaving = false, error = "Failed to save: ${e.message}") }
            }
        }
    }

    /**
     * Cancel editing and clean up any staging files.
     */
    private fun cancelAndCleanup() {
        val seriesId = _state.value.seriesId
        if (seriesId.isNotBlank() && _state.value.stagingCoverPath != null) {
            viewModelScope.launch {
                imageStorage.deleteSeriesCoverStaging(seriesId)
                logger.debug { "Staging cover cleaned up on cancel" }
            }
        }
        _navActions.value = SeriesEditNavAction.NavigateBack
    }

    /**
     * Clean up staging files when ViewModel is destroyed.
     * Handles cases where user navigates away without explicitly canceling or saving.
     */
    override fun onCleared() {
        super.onCleared()
        val seriesId = _state.value.seriesId
        if (seriesId.isNotBlank() && _state.value.stagingCoverPath != null) {
            // viewModelScope is cancelled by this point, use GlobalScope for cleanup
            @Suppress("OPT_IN_USAGE")
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                imageStorage.deleteSeriesCoverStaging(seriesId)
                logger.debug { "Staging cover cleaned up on ViewModel cleared" }
            }
        }
    }
}
