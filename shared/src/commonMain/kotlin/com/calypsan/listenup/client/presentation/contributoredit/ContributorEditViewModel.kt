package com.calypsan.listenup.client.presentation.contributoredit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.data.local.db.BookContributorCrossRef
import com.calypsan.listenup.client.data.local.db.BookContributorDao
import com.calypsan.listenup.client.data.local.db.ContributorDao
import com.calypsan.listenup.client.data.local.db.SyncState
import com.calypsan.listenup.client.data.local.db.Timestamp
import com.calypsan.listenup.client.data.remote.ContributorSearchResult
import com.calypsan.listenup.client.data.remote.ListenUpApiContract
import com.calypsan.listenup.client.data.remote.UpdateContributorRequest
import com.calypsan.listenup.client.data.repository.ContributorRepositoryContract
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    data class NameChanged(val name: String) : ContributorEditUiEvent
    data class DescriptionChanged(val description: String) : ContributorEditUiEvent
    data class WebsiteChanged(val website: String) : ContributorEditUiEvent
    data class BirthDateChanged(val date: String) : ContributorEditUiEvent
    data class DeathDateChanged(val date: String) : ContributorEditUiEvent

    // Alias management
    data class AliasSearchQueryChanged(val query: String) : ContributorEditUiEvent
    data class AliasSelected(val result: ContributorSearchResult) : ContributorEditUiEvent
    data class AliasEntered(val name: String) : ContributorEditUiEvent // Manual text entry
    data class RemoveAlias(val alias: String) : ContributorEditUiEvent

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
 * - Saving changes to local database
 * - Tracking unsaved changes
 *
 * Alias Merge Flow:
 * When user adds "Richard Bachman" as an alias to Stephen King:
 * 1. If a ContributorEntity named "Richard Bachman" exists:
 *    - Re-link all BookContributorCrossRef to Stephen King (with creditedAs = "Richard Bachman")
 *    - Delete the Richard Bachman entity
 * 2. Add "Richard Bachman" to Stephen King's aliases field
 *
 * @property contributorDao DAO for contributor operations
 * @property bookContributorDao DAO for book-contributor relationships
 * @property contributorRepository Repository for contributor search
 */
@OptIn(FlowPreview::class)
class ContributorEditViewModel(
    private val contributorDao: ContributorDao,
    private val bookContributorDao: BookContributorDao,
    private val contributorRepository: ContributorRepositoryContract,
    private val api: ListenUpApiContract,
) : ViewModel() {
    private val _state = MutableStateFlow(ContributorEditUiState())
    val state: StateFlow<ContributorEditUiState> = _state.asStateFlow()

    private val _navActions = MutableStateFlow<ContributorEditNavAction?>(null)
    val navActions: StateFlow<ContributorEditNavAction?> = _navActions.asStateFlow()

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
                    _state.update {
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
            _state.update { it.copy(isLoading = true, contributorId = contributorId) }

            val contributor = contributorDao.getById(contributorId)
            if (contributor == null) {
                _state.update { it.copy(isLoading = false, error = "Contributor not found") }
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

            _state.update {
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
                _state.update { it.copy(name = event.name) }
                updateHasChanges()
            }

            is ContributorEditUiEvent.DescriptionChanged -> {
                _state.update { it.copy(description = event.description) }
                updateHasChanges()
            }

            is ContributorEditUiEvent.WebsiteChanged -> {
                _state.update { it.copy(website = event.website) }
                updateHasChanges()
            }

            is ContributorEditUiEvent.BirthDateChanged -> {
                _state.update { it.copy(birthDate = event.date) }
                updateHasChanges()
            }

            is ContributorEditUiEvent.DeathDateChanged -> {
                _state.update { it.copy(deathDate = event.date) }
                updateHasChanges()
            }

            is ContributorEditUiEvent.AliasSearchQueryChanged -> {
                _state.update { it.copy(aliasSearchQuery = event.query) }
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

            is ContributorEditUiEvent.Save -> {
                saveChanges()
            }

            is ContributorEditUiEvent.Cancel -> {
                _navActions.value = ContributorEditNavAction.NavigateBack
            }

            is ContributorEditUiEvent.DismissError -> {
                _state.update { it.copy(error = null) }
            }
        }
    }

    /**
     * Clear navigation action after handling.
     */
    fun clearNavAction() {
        _navActions.value = null
    }

    private fun performAliasSearch(query: String) {
        aliasSearchJob?.cancel()
        aliasSearchJob = viewModelScope.launch {
            _state.update { it.copy(aliasSearchLoading = true) }

            val response = contributorRepository.searchContributors(query, limit = 10)

            // Filter out:
            // - The current contributor (can't be an alias of itself)
            // - Contributors already in aliases list
            val currentId = _state.value.contributorId
            val currentAliases = _state.value.aliases.map { it.lowercase() }.toSet()

            val filteredResults = response.contributors.filter { result ->
                result.id != currentId && result.name.lowercase() !in currentAliases
            }

            _state.update {
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

        _state.update { current ->
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

        _state.update { current ->
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

    private fun removeAlias(alias: String) {
        val isOriginalAlias = originalAliases.any { it.equals(alias, ignoreCase = true) }

        if (isOriginalAlias) {
            // This was an existing alias - need to call unmerge to split it back out
            viewModelScope.launch {
                unmergeAliasViaServer(alias)
            }
        } else {
            // This was a newly added alias - just remove it from the list
            _state.update { current ->
                current.copy(aliases = current.aliases.filter { !it.equals(alias, ignoreCase = true) })
            }
            // Remove from merge tracking
            contributorsToMerge.remove(alias.lowercase())
            updateHasChanges()
        }
    }

    /**
     * Unmerge an alias via the server API, creating a new contributor.
     *
     * Called when removing an original alias (one that existed when loading).
     * The server will:
     * 1. Create a new contributor with the alias name
     * 2. Re-link books that were credited to that alias to the new contributor
     * 3. Remove the alias from this contributor
     */
    private suspend fun unmergeAliasViaServer(aliasName: String) {
        val contributorId = _state.value.contributorId

        logger.info { "Unmerging alias '$aliasName' from contributor $contributorId via server" }

        _state.update { it.copy(isSaving = true) } // Show loading state

        when (val result = api.unmergeContributor(contributorId, aliasName)) {
            is Success -> {
                logger.info { "Server unmerge successful: created contributor ${result.data.id} with name '${result.data.name}'" }

                // Update local state to remove the alias
                _state.update { current ->
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
                logger.error(result.exception) { "Server unmerge failed for alias '$aliasName'" }
                _state.update { it.copy(isSaving = false, error = "Failed to remove alias: ${result.exception.message}") }
            }
        }
    }

    private fun updateHasChanges() {
        val current = _state.value
        val hasChanges = current.name != originalName ||
            current.description != originalDescription ||
            current.website != originalWebsite ||
            current.birthDate != originalBirthDate ||
            current.deathDate != originalDeathDate ||
            current.aliases.toSet() != originalAliases.toSet()

        _state.update { it.copy(hasChanges = hasChanges) }
    }

    private fun saveChanges() {
        val current = _state.value
        if (!current.hasChanges) {
            _navActions.value = ContributorEditNavAction.NavigateBack
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }

            try {
                // Get the existing contributor to preserve sync fields
                val existing = contributorDao.getById(current.contributorId)
                if (existing == null) {
                    _state.update { it.copy(isSaving = false, error = "Contributor not found") }
                    return@launch
                }

                // Handle new aliases - merge contributors via server API
                val newAliases = current.aliases.toSet() - originalAliases.toSet()
                var serverAliases: List<String>? = null

                for (newAlias in newAliases) {
                    val mergeResult = mergeContributorViaServer(newAlias, current.contributorId)
                    if (mergeResult != null) {
                        // Server returned updated aliases list
                        serverAliases = mergeResult
                    }
                }

                // Use server aliases if we got them, otherwise use local state
                val finalAliases = serverAliases ?: current.aliases

                // Update contributor on server
                val updateRequest = UpdateContributorRequest(
                    name = current.name,
                    biography = current.description.ifBlank { null },
                    website = current.website.ifBlank { null },
                    birthDate = current.birthDate.ifBlank { null },
                    deathDate = current.deathDate.ifBlank { null },
                    aliases = finalAliases,
                )

                val serverResponse = api.updateContributor(current.contributorId, updateRequest)

                val (finalSyncState, aliasesString) = when (serverResponse) {
                    is Success -> {
                        logger.info { "Server update successful" }
                        // Use server's response as source of truth
                        val responseAliases = serverResponse.data.aliases
                        SyncState.SYNCED to responseAliases.takeIf { it.isNotEmpty() }?.joinToString(", ")
                    }
                    is Failure -> {
                        logger.warn { "Server update failed, saving locally: ${serverResponse.exception.message}" }
                        // Save locally for later sync
                        SyncState.NOT_SYNCED to finalAliases.takeIf { it.isNotEmpty() }?.joinToString(", ")
                    }
                }

                // Update local database
                val updated = existing.copy(
                    name = current.name,
                    description = current.description.ifBlank { null },
                    website = current.website.ifBlank { null },
                    birthDate = current.birthDate.ifBlank { null },
                    deathDate = current.deathDate.ifBlank { null },
                    aliases = aliasesString,
                    syncState = finalSyncState,
                    lastModified = Timestamp.now(),
                    updatedAt = Timestamp.now(),
                )
                contributorDao.upsert(updated)

                logger.info { "Contributor saved: ${current.name}, aliases: $finalAliases" }

                _state.update { it.copy(isSaving = false, hasChanges = false) }
                _navActions.value = ContributorEditNavAction.SaveSuccess

            } catch (e: Exception) {
                logger.error(e) { "Failed to save contributor changes" }
                _state.update { it.copy(isSaving = false, error = "Failed to save: ${e.message}") }
            }
        }
    }

    /**
     * Merge a contributor via the server API.
     *
     * If a contributor named [aliasName] exists (tracked from autocomplete):
     * 1. Call server merge endpoint
     * 2. Server re-links all book relationships with creditedAs
     * 3. Server soft-deletes the merged contributor
     * 4. Delete merged contributor from local database
     *
     * @return The updated aliases list from server, or null if no merge was performed
     */
    private suspend fun mergeContributorViaServer(
        aliasName: String,
        targetContributorId: String,
    ): List<String>? {
        // Check if we tracked a contributor to merge from autocomplete
        val tracked = contributorsToMerge[aliasName.lowercase()] ?: return null

        if (tracked.id == targetContributorId) {
            return null // Can't merge into itself
        }

        logger.info { "Merging contributor '${tracked.name}' (${tracked.id}) into $targetContributorId via server" }

        // Call server merge endpoint
        return when (val result = api.mergeContributor(targetContributorId, tracked.id)) {
            is Success -> {
                logger.info { "Server merge successful: ${result.data.aliases}" }

                // Re-link local book-contributor relationships from source to target
                // Set creditedAs to preserve the original attribution display name
                val localRelations = bookContributorDao.getByContributorId(tracked.id)
                for (relation in localRelations) {
                    // Check if target already has a relationship for this book/role
                    val existingTargetRelation = bookContributorDao.get(
                        relation.bookId,
                        targetContributorId,
                        relation.role,
                    )

                    if (existingTargetRelation == null) {
                        // Create new relationship pointing to target, with creditedAs preserving original name
                        val newRelation = BookContributorCrossRef(
                            bookId = relation.bookId,
                            contributorId = targetContributorId,
                            role = relation.role,
                            creditedAs = relation.creditedAs ?: tracked.name,
                        )
                        bookContributorDao.insert(newRelation)
                    }
                    // Either way, delete the old relationship
                    bookContributorDao.delete(relation.bookId, relation.contributorId, relation.role)
                }

                // Delete the merged contributor from local database
                // (server soft-deleted it, we can hard-delete locally)
                contributorDao.deleteById(tracked.id)

                result.data.aliases
            }

            is Failure -> {
                logger.error(result.exception) { "Server merge failed for ${tracked.id}" }
                // Don't fail the entire save - the alias is still added locally
                // and will sync eventually
                null
            }
        }
    }
}
