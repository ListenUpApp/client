@file:Suppress("MagicNumber")

package com.calypsan.listenup.client.presentation.contributoredit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.data.local.db.ContributorDao
import com.calypsan.listenup.client.data.local.images.ImageStorage
import com.calypsan.listenup.client.data.remote.ContributorSearchResult
import com.calypsan.listenup.client.data.remote.ImageApiContract
import com.calypsan.listenup.client.data.repository.ContributorEditRepositoryContract
import com.calypsan.listenup.client.data.repository.ContributorRepositoryContract
import com.calypsan.listenup.client.data.repository.ContributorUpdateRequest
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * UI state for contributor editing screen.
 */
data class ContributorEditUiState(
    // Loading states
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isUploadingImage: Boolean = false,
    val error: String? = null,
    // Contributor identity
    val contributorId: String = "",
    val imagePath: String? = null,
    // Editable fields
    val name: String = "",
    val description: String = "",
    val website: String = "",
    val birthDate: String = "", // ISO 8601 format (YYYY-MM-DD)
    val deathDate: String = "", // ISO 8601 format (YYYY-MM-DD)
    // Aliases - pen names merged into this contributor
    val aliases: List<String> = emptyList(),
    val aliasSearchQuery: String = "",
    val aliasSearchResults: List<ContributorSearchResult> = emptyList(),
    val aliasSearchLoading: Boolean = false,
    // Track if changes have been made
    val hasChanges: Boolean = false,
)

/**
 * Events from the contributor edit UI.
 */
sealed interface ContributorEditUiEvent {
    // Field changes
    data class NameChanged(
        val name: String,
    ) : ContributorEditUiEvent

    data class DescriptionChanged(
        val description: String,
    ) : ContributorEditUiEvent

    data class WebsiteChanged(
        val website: String,
    ) : ContributorEditUiEvent

    data class BirthDateChanged(
        val date: String,
    ) : ContributorEditUiEvent

    data class DeathDateChanged(
        val date: String,
    ) : ContributorEditUiEvent

    // Alias management
    data class AliasSearchQueryChanged(
        val query: String,
    ) : ContributorEditUiEvent

    data class AliasSelected(
        val result: ContributorSearchResult,
    ) : ContributorEditUiEvent

    data class AliasEntered(
        val name: String,
    ) : ContributorEditUiEvent // Manual text entry

    data class RemoveAlias(
        val alias: String,
    ) : ContributorEditUiEvent

    // Image upload
    data class UploadImage(
        val imageData: ByteArray,
        val filename: String,
    ) : ContributorEditUiEvent

    // Actions
    data object Save : ContributorEditUiEvent

    data object Cancel : ContributorEditUiEvent

    data object DismissError : ContributorEditUiEvent
}

/**
 * Navigation actions from contributor edit screen.
 */
sealed interface ContributorEditNavAction {
    data object NavigateBack : ContributorEditNavAction

    data object SaveSuccess : ContributorEditNavAction
}

/**
 * ViewModel for the contributor edit screen.
 *
 * Handles:
 * - Loading contributor data for editing
 * - Managing aliases with merge logic (absorbing other contributors)
 * - Saving changes via offline-first repository
 * - Tracking unsaved changes
 *
 * Alias Merge Flow (offline-first):
 * When user adds "Richard Bachman" as an alias to Stephen King:
 * 1. If a ContributorEntity named "Richard Bachman" exists:
 *    - Calls ContributorEditRepository.mergeContributor()
 *    - Repository re-links book relationships and deletes source locally
 *    - Operation is queued for server sync
 * 2. Alias is added to the UI state
 *
 * @property contributorDao DAO for reading contributor data
 * @property contributorRepository Repository for contributor search
 * @property contributorEditRepository Repository for editing (offline-first)
 * @property imageApi API for image upload
 * @property imageStorage Local image storage
 */
@OptIn(FlowPreview::class)
class ContributorEditViewModel(
    private val contributorDao: ContributorDao,
    private val contributorRepository: ContributorRepositoryContract,
    private val contributorEditRepository: ContributorEditRepositoryContract,
    private val imageApi: ImageApiContract,
    private val imageStorage: ImageStorage,
) : ViewModel() {
    val state: StateFlow<ContributorEditUiState>
        field = MutableStateFlow(ContributorEditUiState())

    val navActions: StateFlow<ContributorEditNavAction?>
        field = MutableStateFlow<ContributorEditNavAction?>(null)

    // Alias search
    private val aliasQueryFlow = MutableStateFlow("")
    private var aliasSearchJob: Job? = null

    // Track original values for change detection
    private var originalName: String = ""
    private var originalDescription: String = ""
    private var originalWebsite: String = ""
    private var originalBirthDate: String = ""
    private var originalDeathDate: String = ""
    private var originalAliases: List<String> = emptyList()
    private var originalImagePath: String? = null

    // Track contributors to merge on save (selected from autocomplete)
    private val contributorsToMerge = mutableMapOf<String, ContributorSearchResult>()

    init {
        setupAliasSearch()
    }

    /**
     * Set up debounced search for aliases.
     */
    private fun setupAliasSearch() {
        aliasQueryFlow
            .debounce(300)
            .distinctUntilChanged()
            .filter { it.length >= 2 || it.isEmpty() }
            .onEach { query ->
                if (query.isBlank()) {
                    state.update {
                        it.copy(
                            aliasSearchResults = emptyList(),
                            aliasSearchLoading = false,
                        )
                    }
                } else {
                    performAliasSearch(query)
                }
            }.launchIn(viewModelScope)
    }

    /**
     * Load contributor data for editing.
     */
    fun loadContributor(contributorId: String) {
        viewModelScope.launch {
            state.update { it.copy(isLoading = true, contributorId = contributorId) }

            val contributor = contributorDao.getById(contributorId)
            if (contributor == null) {
                state.update { it.copy(isLoading = false, error = "Contributor not found") }
                return@launch
            }

            // Parse aliases from comma-separated string
            val aliases = contributor.aliasList()

            // Store original values
            originalName = contributor.name
            originalDescription = contributor.description ?: ""
            originalWebsite = contributor.website ?: ""
            originalBirthDate = contributor.birthDate ?: ""
            originalDeathDate = contributor.deathDate ?: ""
            originalAliases = aliases
            originalImagePath = contributor.imagePath

            state.update {
                it.copy(
                    isLoading = false,
                    imagePath = contributor.imagePath,
                    name = contributor.name,
                    description = contributor.description ?: "",
                    website = contributor.website ?: "",
                    birthDate = contributor.birthDate ?: "",
                    deathDate = contributor.deathDate ?: "",
                    aliases = aliases,
                    hasChanges = false,
                )
            }

            logger.debug { "Loaded contributor for editing: ${contributor.name}" }
        }
    }

    /**
     * Handle UI events.
     */
    fun onEvent(event: ContributorEditUiEvent) {
        when (event) {
            is ContributorEditUiEvent.NameChanged -> {
                state.update { it.copy(name = event.name) }
                updateHasChanges()
            }

            is ContributorEditUiEvent.DescriptionChanged -> {
                state.update { it.copy(description = event.description) }
                updateHasChanges()
            }

            is ContributorEditUiEvent.WebsiteChanged -> {
                state.update { it.copy(website = event.website) }
                updateHasChanges()
            }

            is ContributorEditUiEvent.BirthDateChanged -> {
                state.update { it.copy(birthDate = event.date) }
                updateHasChanges()
            }

            is ContributorEditUiEvent.DeathDateChanged -> {
                state.update { it.copy(deathDate = event.date) }
                updateHasChanges()
            }

            is ContributorEditUiEvent.AliasSearchQueryChanged -> {
                state.update { it.copy(aliasSearchQuery = event.query) }
                aliasQueryFlow.value = event.query
            }

            is ContributorEditUiEvent.AliasSelected -> {
                selectAlias(event.result)
            }

            is ContributorEditUiEvent.AliasEntered -> {
                addManualAlias(event.name)
            }

            is ContributorEditUiEvent.RemoveAlias -> {
                removeAlias(event.alias)
            }

            is ContributorEditUiEvent.UploadImage -> {
                uploadImage(event.imageData, event.filename)
            }

            is ContributorEditUiEvent.Save -> {
                saveChanges()
            }

            is ContributorEditUiEvent.Cancel -> {
                navActions.value = ContributorEditNavAction.NavigateBack
            }

            is ContributorEditUiEvent.DismissError -> {
                state.update { it.copy(error = null) }
            }
        }
    }

    /**
     * Clear navigation action after handling.
     */
    fun clearNavAction() {
        navActions.value = null
    }

    private fun performAliasSearch(query: String) {
        aliasSearchJob?.cancel()
        aliasSearchJob =
            viewModelScope.launch {
                state.update { it.copy(aliasSearchLoading = true) }

                val response = contributorRepository.searchContributors(query, limit = 10)

                // Filter out:
                // - The current contributor (can't be an alias of itself)
                // - Contributors already in aliases list
                val currentId = state.value.contributorId
                val currentAliases =
                    state.value.aliases
                        .map { it.lowercase() }
                        .toSet()

                val filteredResults =
                    response.contributors.filter { result ->
                        result.id != currentId && result.name.lowercase() !in currentAliases
                    }

                state.update {
                    it.copy(
                        aliasSearchResults = filteredResults,
                        aliasSearchLoading = false,
                    )
                }

                logger.debug { "Alias search: ${filteredResults.size} results for '$query'" }
            }
    }

    /**
     * Add alias from autocomplete selection (existing contributor to merge).
     */
    private fun selectAlias(result: ContributorSearchResult) {
        val aliasName = result.name

        state.update { current ->
            // Check if already added
            if (current.aliases.any { it.equals(aliasName, ignoreCase = true) }) {
                return@update current.copy(
                    aliasSearchQuery = "",
                    aliasSearchResults = emptyList(),
                )
            }

            current.copy(
                aliases = current.aliases + aliasName,
                aliasSearchQuery = "",
                aliasSearchResults = emptyList(),
            )
        }

        // Track this contributor for merging on save
        contributorsToMerge[aliasName.lowercase()] = result

        aliasQueryFlow.value = ""
        updateHasChanges()
    }

    /**
     * Add alias from manual text entry (pen name that might not exist yet).
     */
    private fun addManualAlias(name: String) {
        if (name.isBlank()) return

        val trimmedName = name.trim()

        state.update { current ->
            // Check if already added
            if (current.aliases.any { it.equals(trimmedName, ignoreCase = true) }) {
                return@update current.copy(
                    aliasSearchQuery = "",
                    aliasSearchResults = emptyList(),
                )
            }

            current.copy(
                aliases = current.aliases + trimmedName,
                aliasSearchQuery = "",
                aliasSearchResults = emptyList(),
            )
        }

        aliasQueryFlow.value = ""
        updateHasChanges()
    }

    // ========== Image Upload ==========

    private fun uploadImage(
        imageData: ByteArray,
        filename: String,
    ) {
        val contributorId = state.value.contributorId
        if (contributorId.isBlank()) {
            logger.error { "Cannot upload image: contributor ID is empty" }
            return
        }

        viewModelScope.launch {
            state.update { it.copy(isUploadingImage = true, error = null) }

            when (val result = imageApi.uploadContributorImage(contributorId, imageData, filename)) {
                is Success -> {
                    logger.info { "Contributor image uploaded successfully to server" }

                    // Save image locally for offline-first access
                    when (val saveResult = imageStorage.saveContributorImage(contributorId, imageData)) {
                        is Success -> {
                            val localPath = imageStorage.getContributorImagePath(contributorId)
                            logger.info { "Contributor image saved locally: $localPath" }
                            state.update {
                                it.copy(
                                    isUploadingImage = false,
                                    imagePath = localPath,
                                )
                            }
                            updateHasChanges()
                        }

                        is Failure -> {
                            logger.error { "Failed to save contributor image locally: ${saveResult.message}" }
                            // Still mark upload as successful since server has the image
                            state.update {
                                it.copy(
                                    isUploadingImage = false,
                                    error = "Image uploaded but failed to save locally",
                                )
                            }
                        }
                    }
                }

                is Failure -> {
                    logger.error { "Failed to upload contributor image: ${result.message}" }
                    state.update {
                        it.copy(
                            isUploadingImage = false,
                            error = "Failed to upload image: ${result.message}",
                        )
                    }
                }
            }
        }
    }

    /**
     * Remove an alias from the contributor.
     *
     * If this was an original alias (existed when loading), calls unmerge to split it out.
     * If it was newly added, just removes from the local list.
     */
    private fun removeAlias(alias: String) {
        val isOriginalAlias = originalAliases.any { it.equals(alias, ignoreCase = true) }

        if (isOriginalAlias) {
            // This was an existing alias - call unmerge via repository
            viewModelScope.launch {
                unmergeAlias(alias)
            }
        } else {
            // This was a newly added alias - just remove it from the list
            state.update { current ->
                current.copy(aliases = current.aliases.filter { !it.equals(alias, ignoreCase = true) })
            }
            // Remove from merge tracking
            contributorsToMerge.remove(alias.lowercase())
            updateHasChanges()
        }
    }

    /**
     * Unmerge an alias via the offline-first repository.
     */
    private suspend fun unmergeAlias(aliasName: String) {
        val contributorId = state.value.contributorId

        logger.info { "Unmerging alias '$aliasName' from contributor $contributorId" }

        state.update { it.copy(isSaving = true) }

        when (val result = contributorEditRepository.unmergeContributor(contributorId, aliasName)) {
            is Success -> {
                logger.info { "Unmerge queued successfully" }

                // Update local state to remove the alias
                state.update { current ->
                    current.copy(
                        aliases = current.aliases.filter { !it.equals(aliasName, ignoreCase = true) },
                        isSaving = false,
                    )
                }

                // Update original aliases list so we don't think it's still there
                originalAliases = originalAliases.filter { !it.equals(aliasName, ignoreCase = true) }

                updateHasChanges()
            }

            is Failure -> {
                logger.error(result.exception) { "Unmerge failed for alias '$aliasName'" }
                state.update {
                    it.copy(
                        isSaving = false,
                        error = "Failed to remove alias: ${result.exception.message}",
                    )
                }
            }
        }
    }

    private fun updateHasChanges() {
        val current = state.value
        val hasChanges =
            current.name != originalName ||
                current.description != originalDescription ||
                current.website != originalWebsite ||
                current.birthDate != originalBirthDate ||
                current.deathDate != originalDeathDate ||
                current.aliases.toSet() != originalAliases.toSet() ||
                current.imagePath != originalImagePath

        state.update { it.copy(hasChanges = hasChanges) }
    }

    /**
     * Save all changes via the offline-first repository.
     *
     * Flow:
     * 1. Handle new aliases by merging contributors
     * 2. Update contributor metadata
     * 3. Changes are applied locally and queued for sync
     */
    private fun saveChanges() {
        val current = state.value
        if (!current.hasChanges) {
            navActions.value = ContributorEditNavAction.NavigateBack
            return
        }

        viewModelScope.launch {
            state.update { it.copy(isSaving = true, error = null) }

            try {
                // 1. Handle new aliases - merge contributors
                val newAliases = current.aliases.toSet() - originalAliases.toSet()
                for (newAlias in newAliases) {
                    val tracked = contributorsToMerge[newAlias.lowercase()]
                    if (tracked != null && tracked.id != current.contributorId) {
                        // This alias corresponds to an existing contributor - merge it
                        when (
                            val result =
                                contributorEditRepository.mergeContributor(
                                    targetId = current.contributorId,
                                    sourceId = tracked.id,
                                )
                        ) {
                            is Success -> {
                                logger.info { "Merged contributor ${tracked.id} into ${current.contributorId}" }
                            }

                            is Failure -> {
                                logger.warn { "Merge failed for ${tracked.id}: ${result.exception.message}" }
                                // Continue - alias will still be added via update
                            }
                        }
                    }
                }

                // 2. Update contributor metadata
                val updateRequest =
                    ContributorUpdateRequest(
                        name = current.name,
                        biography = current.description.ifBlank { null },
                        website = current.website.ifBlank { null },
                        birthDate = current.birthDate.ifBlank { null },
                        deathDate = current.deathDate.ifBlank { null },
                        aliases = current.aliases,
                        imagePath = current.imagePath,
                    )

                when (
                    val result =
                        contributorEditRepository.updateContributor(
                            current.contributorId,
                            updateRequest,
                        )
                ) {
                    is Success -> {
                        logger.info { "Contributor update queued: ${current.name}" }
                        state.update { it.copy(isSaving = false, hasChanges = false) }
                        navActions.value = ContributorEditNavAction.SaveSuccess
                    }

                    is Failure -> {
                        logger.error(result.exception) { "Failed to update contributor" }
                        state.update {
                            it.copy(
                                isSaving = false,
                                error = "Failed to save: ${result.exception.message}",
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to save contributor changes" }
                state.update { it.copy(isSaving = false, error = "Failed to save: ${e.message}") }
            }
        }
    }
}
