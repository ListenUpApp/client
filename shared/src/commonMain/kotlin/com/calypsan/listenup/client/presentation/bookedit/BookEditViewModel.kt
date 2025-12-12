package com.calypsan.listenup.client.presentation.bookedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.data.remote.BookUpdateRequest
import com.calypsan.listenup.client.data.remote.ContributorInput
import com.calypsan.listenup.client.data.remote.ContributorSearchResult
import com.calypsan.listenup.client.data.repository.BookEditRepositoryContract
import com.calypsan.listenup.client.data.repository.BookRepositoryContract
import com.calypsan.listenup.client.data.repository.ContributorRepositoryContract
import com.calypsan.listenup.client.domain.model.Contributor
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
 * Contributor with roles for editing.
 */
data class EditableContributor(
    val id: String? = null, // null for newly added contributors
    val name: String,
    val roles: Set<ContributorRole>,
)

/**
 * Role types for contributors.
 * Matches server-side roles in domain/contributor.go
 */
enum class ContributorRole(val displayName: String, val apiValue: String) {
    AUTHOR("Author", "author"),
    NARRATOR("Narrator", "narrator"),
    EDITOR("Editor", "editor"),
    TRANSLATOR("Translator", "translator"),
    FOREWORD("Foreword", "foreword"),
    INTRODUCTION("Introduction", "introduction"),
    AFTERWORD("Afterword", "afterword"),
    PRODUCER("Producer", "producer"),
    ADAPTER("Adapter", "adapter"),
    ILLUSTRATOR("Illustrator", "illustrator"),
    ;

    companion object {
        fun fromApiValue(value: String): ContributorRole? =
            entries.find { it.apiValue.equals(value, ignoreCase = true) }
    }
}

/**
 * UI state for book editing screen.
 */
data class BookEditUiState(
    // Loading states
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,

    // Book metadata fields
    val bookId: String = "",
    val title: String = "",
    val subtitle: String = "",
    val description: String = "",
    val seriesName: String? = null,
    val seriesSequence: String = "",
    val publishYear: String = "",

    // Contributors
    val contributors: List<EditableContributor> = emptyList(),

    // Per-role search state (replaces single search)
    val roleSearchQueries: Map<ContributorRole, String> = emptyMap(),
    val roleSearchResults: Map<ContributorRole, List<ContributorSearchResult>> = emptyMap(),
    val roleSearchLoading: Map<ContributorRole, Boolean> = emptyMap(),
    val roleOfflineResults: Map<ContributorRole, Boolean> = emptyMap(),

    // Visible role sections (prepopulated from existing contributors + user-added)
    val visibleRoles: Set<ContributorRole> = emptySet(),

    // Track if changes have been made
    val hasChanges: Boolean = false,
) {
    /**
     * Get contributors for a specific role.
     */
    fun contributorsForRole(role: ContributorRole): List<EditableContributor> =
        contributors.filter { role in it.roles }

    /**
     * Authors from contributor list.
     */
    val authors: List<EditableContributor>
        get() = contributorsForRole(ContributorRole.AUTHOR)

    /**
     * Narrators from contributor list.
     */
    val narrators: List<EditableContributor>
        get() = contributorsForRole(ContributorRole.NARRATOR)

    /**
     * Roles that are not yet visible (available to add).
     */
    val availableRolesToAdd: List<ContributorRole>
        get() = ContributorRole.entries.filter { it !in visibleRoles }
}

/**
 * Events from the book edit UI.
 */
sealed interface BookEditUiEvent {
    // Metadata changes
    data class TitleChanged(val title: String) : BookEditUiEvent
    data class SubtitleChanged(val subtitle: String) : BookEditUiEvent
    data class DescriptionChanged(val description: String) : BookEditUiEvent
    data class SeriesSequenceChanged(val sequence: String) : BookEditUiEvent
    data class PublishYearChanged(val year: String) : BookEditUiEvent

    // Per-role contributor management
    data class RoleSearchQueryChanged(val role: ContributorRole, val query: String) : BookEditUiEvent
    data class RoleContributorSelected(val role: ContributorRole, val result: ContributorSearchResult) : BookEditUiEvent
    data class RoleContributorEntered(val role: ContributorRole, val name: String) : BookEditUiEvent
    data class ClearRoleSearch(val role: ContributorRole) : BookEditUiEvent
    data class AddRoleSection(val role: ContributorRole) : BookEditUiEvent
    data class RemoveContributor(val contributor: EditableContributor, val role: ContributorRole) : BookEditUiEvent
    data class RemoveRoleSection(val role: ContributorRole) : BookEditUiEvent

    // Actions
    data object Save : BookEditUiEvent
    data object Cancel : BookEditUiEvent
    data object DismissError : BookEditUiEvent
}

/**
 * Navigation actions from book edit screen.
 */
sealed interface BookEditNavAction {
    data object NavigateBack : BookEditNavAction
    data class ShowSaveSuccess(val message: String) : BookEditNavAction
}

/**
 * ViewModel for the book edit screen.
 *
 * Handles:
 * - Loading book data for editing
 * - Debounced contributor search with offline fallback
 * - Saving metadata and contributor changes
 * - Tracking unsaved changes
 *
 * @property bookRepository Repository for loading book data
 * @property bookEditRepository Repository for saving edits
 * @property contributorRepository Repository for contributor search
 */
@OptIn(FlowPreview::class)
class BookEditViewModel(
    private val bookRepository: BookRepositoryContract,
    private val bookEditRepository: BookEditRepositoryContract,
    private val contributorRepository: ContributorRepositoryContract,
) : ViewModel() {
    private val _state = MutableStateFlow(BookEditUiState())
    val state: StateFlow<BookEditUiState> = _state.asStateFlow()

    private val _navActions = MutableStateFlow<BookEditNavAction?>(null)
    val navActions: StateFlow<BookEditNavAction?> = _navActions.asStateFlow()

    // Per-role query flows for debounced search
    private val roleQueryFlows = mutableMapOf<ContributorRole, MutableStateFlow<String>>()
    private val roleSearchJobs = mutableMapOf<ContributorRole, Job>()

    // Track original values for change detection
    private var originalTitle: String = ""
    private var originalSubtitle: String = ""
    private var originalDescription: String = ""
    private var originalSequence: String = ""
    private var originalPublishYear: String = ""
    private var originalContributors: List<EditableContributor> = emptyList()

    /**
     * Set up debounced search for a specific role.
     */
    private fun setupRoleSearch(role: ContributorRole) {
        if (roleQueryFlows.containsKey(role)) return

        val queryFlow = MutableStateFlow("")
        roleQueryFlows[role] = queryFlow

        queryFlow
            .debounce(300)
            .distinctUntilChanged()
            .filter { it.length >= 2 || it.isEmpty() }
            .onEach { query ->
                if (query.isBlank()) {
                    _state.update {
                        it.copy(
                            roleSearchResults = it.roleSearchResults - role,
                            roleSearchLoading = it.roleSearchLoading - role,
                        )
                    }
                } else {
                    performRoleSearch(role, query)
                }
            }.launchIn(viewModelScope)
    }

    /**
     * Load book data for editing.
     */
    fun loadBook(bookId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, bookId = bookId) }

            val book = bookRepository.getBook(bookId)
            if (book == null) {
                _state.update { it.copy(isLoading = false, error = "Book not found") }
                return@launch
            }

            // Convert domain contributors to editable format
            val editableContributors = book.allContributors.map { contributor ->
                EditableContributor(
                    id = contributor.id,
                    name = contributor.name,
                    roles = contributor.roles
                        .mapNotNull { ContributorRole.fromApiValue(it) }
                        .toSet(),
                )
            }

            // Determine visible roles from existing contributors
            val rolesFromContributors = editableContributors
                .flatMap { it.roles }
                .toSet()

            // Always show Author section, plus any roles that have contributors
            val initialVisibleRoles = rolesFromContributors + ContributorRole.AUTHOR

            // Set up search for each visible role
            initialVisibleRoles.forEach { role ->
                setupRoleSearch(role)
            }

            // Store original values
            originalTitle = book.title
            originalSubtitle = book.subtitle ?: ""
            originalDescription = book.description ?: ""
            originalSequence = book.seriesSequence ?: ""
            originalPublishYear = book.publishYear?.toString() ?: ""
            originalContributors = editableContributors

            _state.update {
                it.copy(
                    isLoading = false,
                    title = book.title,
                    subtitle = book.subtitle ?: "",
                    description = book.description ?: "",
                    seriesName = book.seriesName,
                    seriesSequence = book.seriesSequence ?: "",
                    publishYear = book.publishYear?.toString() ?: "",
                    contributors = editableContributors,
                    visibleRoles = initialVisibleRoles,
                    hasChanges = false,
                )
            }

            logger.debug { "Loaded book for editing: ${book.title}, description=${book.description?.take(50) ?: "null"}" }
        }
    }

    /**
     * Handle UI events.
     */
    fun onEvent(event: BookEditUiEvent) {
        when (event) {
            is BookEditUiEvent.TitleChanged -> {
                _state.update { it.copy(title = event.title) }
                updateHasChanges()
            }

            is BookEditUiEvent.SubtitleChanged -> {
                _state.update { it.copy(subtitle = event.subtitle) }
                updateHasChanges()
            }

            is BookEditUiEvent.DescriptionChanged -> {
                _state.update { it.copy(description = event.description) }
                updateHasChanges()
            }

            is BookEditUiEvent.SeriesSequenceChanged -> {
                _state.update { it.copy(seriesSequence = event.sequence) }
                updateHasChanges()
            }

            is BookEditUiEvent.PublishYearChanged -> {
                // Only allow numeric input
                val filtered = event.year.filter { it.isDigit() }.take(4)
                _state.update { it.copy(publishYear = filtered) }
                updateHasChanges()
            }

            is BookEditUiEvent.RoleSearchQueryChanged -> {
                _state.update {
                    it.copy(roleSearchQueries = it.roleSearchQueries + (event.role to event.query))
                }
                roleQueryFlows[event.role]?.value = event.query
            }

            is BookEditUiEvent.RoleContributorSelected -> {
                selectRoleContributor(event.role, event.result)
            }

            is BookEditUiEvent.RoleContributorEntered -> {
                addRoleContributor(event.role, event.name)
            }

            is BookEditUiEvent.ClearRoleSearch -> {
                _state.update {
                    it.copy(
                        roleSearchQueries = it.roleSearchQueries + (event.role to ""),
                        roleSearchResults = it.roleSearchResults - event.role,
                    )
                }
                roleQueryFlows[event.role]?.value = ""
            }

            is BookEditUiEvent.AddRoleSection -> {
                setupRoleSearch(event.role)
                _state.update {
                    it.copy(visibleRoles = it.visibleRoles + event.role)
                }
            }

            is BookEditUiEvent.RemoveContributor -> {
                removeContributorFromRole(event.contributor, event.role)
            }

            is BookEditUiEvent.RemoveRoleSection -> {
                removeRoleSection(event.role)
            }

            is BookEditUiEvent.Save -> {
                saveChanges()
            }

            is BookEditUiEvent.Cancel -> {
                _navActions.value = BookEditNavAction.NavigateBack
            }

            is BookEditUiEvent.DismissError -> {
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

    private fun performRoleSearch(role: ContributorRole, query: String) {
        roleSearchJobs[role]?.cancel()
        roleSearchJobs[role] = viewModelScope.launch {
            _state.update {
                it.copy(roleSearchLoading = it.roleSearchLoading + (role to true))
            }

            val response = contributorRepository.searchContributors(query, limit = 10)

            // Filter out contributors already added for this role
            val currentContributorIds = _state.value.contributorsForRole(role).mapNotNull { it.id }.toSet()
            val filteredResults = response.contributors.filter { it.id !in currentContributorIds }

            _state.update {
                it.copy(
                    roleSearchResults = it.roleSearchResults + (role to filteredResults),
                    roleSearchLoading = it.roleSearchLoading + (role to false),
                    roleOfflineResults = it.roleOfflineResults + (role to response.isOfflineResult),
                )
            }

            logger.debug { "Contributor search for $role: ${filteredResults.size} results for '$query'" }
        }
    }

    private fun addRoleContributor(role: ContributorRole, name: String) {
        if (name.isBlank()) return

        val trimmedName = name.trim()

        _state.update { current ->
            // Check if contributor already exists (by name)
            val existing = current.contributors.find {
                it.name.equals(trimmedName, ignoreCase = true)
            }

            if (existing != null) {
                // Check if already has this role (duplicate prevention)
                if (role in existing.roles) {
                    // Already has this role - just clear search, don't add duplicate
                    return@update current.copy(
                        roleSearchQueries = current.roleSearchQueries + (role to ""),
                        roleSearchResults = current.roleSearchResults - role,
                    )
                }
                // Add role to existing contributor
                val updated = existing.copy(roles = existing.roles + role)
                current.copy(
                    contributors = current.contributors.map {
                        if (it == existing) updated else it
                    },
                    roleSearchQueries = current.roleSearchQueries + (role to ""),
                    roleSearchResults = current.roleSearchResults - role,
                )
            } else {
                // Add new contributor with this role
                val newContributor = EditableContributor(
                    id = null,
                    name = trimmedName,
                    roles = setOf(role),
                )
                current.copy(
                    contributors = current.contributors + newContributor,
                    roleSearchQueries = current.roleSearchQueries + (role to ""),
                    roleSearchResults = current.roleSearchResults - role,
                )
            }
        }

        roleQueryFlows[role]?.value = ""
        updateHasChanges()
    }

    private fun selectRoleContributor(role: ContributorRole, result: ContributorSearchResult) {
        _state.update { current ->
            // Check if contributor already exists (by ID)
            val existing = current.contributors.find { it.id == result.id }

            if (existing != null) {
                // Check if already has this role (duplicate prevention)
                if (role in existing.roles) {
                    // Already has this role - just clear search, don't add duplicate
                    return@update current.copy(
                        roleSearchQueries = current.roleSearchQueries + (role to ""),
                        roleSearchResults = current.roleSearchResults - role,
                    )
                }
                // Add role to existing contributor
                val updated = existing.copy(roles = existing.roles + role)
                current.copy(
                    contributors = current.contributors.map {
                        if (it == existing) updated else it
                    },
                    roleSearchQueries = current.roleSearchQueries + (role to ""),
                    roleSearchResults = current.roleSearchResults - role,
                )
            } else {
                // Add new contributor with this role
                val newContributor = EditableContributor(
                    id = result.id,
                    name = result.name,
                    roles = setOf(role),
                )
                current.copy(
                    contributors = current.contributors + newContributor,
                    roleSearchQueries = current.roleSearchQueries + (role to ""),
                    roleSearchResults = current.roleSearchResults - role,
                )
            }
        }

        roleQueryFlows[role]?.value = ""
        updateHasChanges()
    }

    private fun removeContributorFromRole(contributor: EditableContributor, role: ContributorRole) {
        _state.update { current ->
            val updatedRoles = contributor.roles - role

            val updatedContributors = if (updatedRoles.isEmpty()) {
                // Remove contributor entirely if no roles left
                current.contributors - contributor
            } else {
                // Just remove this role from the contributor
                current.contributors.map {
                    if (it == contributor) it.copy(roles = updatedRoles) else it
                }
            }

            // Check if any contributors still have this role
            val roleStillHasContributors = updatedContributors.any { role in it.roles }

            // Auto-hide section if empty (unless it's AUTHOR which always shows)
            val updatedVisibleRoles = if (!roleStillHasContributors && role != ContributorRole.AUTHOR) {
                current.visibleRoles - role
            } else {
                current.visibleRoles
            }

            current.copy(
                contributors = updatedContributors,
                visibleRoles = updatedVisibleRoles,
            )
        }
        updateHasChanges()
    }

    private fun removeRoleSection(role: ContributorRole) {
        _state.update { current ->
            // Remove role from all contributors
            val updatedContributors = current.contributors.mapNotNull { contributor ->
                val updatedRoles = contributor.roles - role
                if (updatedRoles.isEmpty()) {
                    null // Remove contributor entirely if no roles left
                } else {
                    contributor.copy(roles = updatedRoles)
                }
            }

            // Remove from visible roles and clean up search state
            current.copy(
                contributors = updatedContributors,
                visibleRoles = current.visibleRoles - role,
                roleSearchQueries = current.roleSearchQueries - role,
                roleSearchResults = current.roleSearchResults - role,
                roleSearchLoading = current.roleSearchLoading - role,
                roleOfflineResults = current.roleOfflineResults - role,
            )
        }

        // Clean up the query flow
        roleQueryFlows.remove(role)
        roleSearchJobs[role]?.cancel()
        roleSearchJobs.remove(role)

        updateHasChanges()
    }

    private fun updateHasChanges() {
        val current = _state.value
        val hasChanges = current.title != originalTitle ||
            current.subtitle != originalSubtitle ||
            current.description != originalDescription ||
            current.seriesSequence != originalSequence ||
            current.publishYear != originalPublishYear ||
            current.contributors != originalContributors

        _state.update { it.copy(hasChanges = hasChanges) }
    }

    private fun saveChanges() {
        val current = _state.value
        if (!current.hasChanges) {
            _navActions.value = BookEditNavAction.NavigateBack
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }

            try {
                // Update metadata
                val metadataChanged = current.title != originalTitle ||
                    current.subtitle != originalSubtitle ||
                    current.description != originalDescription ||
                    current.seriesSequence != originalSequence ||
                    current.publishYear != originalPublishYear

                if (metadataChanged) {
                    val updateRequest = BookUpdateRequest(
                        title = if (current.title != originalTitle) current.title else null,
                        subtitle = if (current.subtitle != originalSubtitle) current.subtitle else null,
                        description = if (current.description != originalDescription) current.description else null,
                        sequence = if (current.seriesSequence != originalSequence) current.seriesSequence else null,
                        publishYear = if (current.publishYear != originalPublishYear) current.publishYear.ifBlank { null } else null,
                    )

                    when (val result = bookEditRepository.updateBook(current.bookId, updateRequest)) {
                        is Success -> logger.info { "Book metadata updated" }
                        is Failure -> {
                            _state.update { it.copy(isSaving = false, error = "Failed to save: ${result.message}") }
                            return@launch
                        }
                    }
                }

                // Update contributors
                val contributorsChanged = current.contributors != originalContributors
                if (contributorsChanged) {
                    val contributorInputs = current.contributors.map { editable ->
                        ContributorInput(
                            name = editable.name,
                            roles = editable.roles.map { it.apiValue },
                        )
                    }

                    when (val result = bookEditRepository.setBookContributors(current.bookId, contributorInputs)) {
                        is Success -> logger.info { "Book contributors updated" }
                        is Failure -> {
                            _state.update { it.copy(isSaving = false, error = "Failed to save contributors: ${result.message}") }
                            return@launch
                        }
                    }
                }

                _state.update { it.copy(isSaving = false) }
                _navActions.value = BookEditNavAction.ShowSaveSuccess("Changes saved")

            } catch (e: Exception) {
                logger.error(e) { "Failed to save book changes" }
                _state.update { it.copy(isSaving = false, error = "Failed to save: ${e.message}") }
            }
        }
    }
}
