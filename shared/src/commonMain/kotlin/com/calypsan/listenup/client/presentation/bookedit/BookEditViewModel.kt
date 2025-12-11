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
 */
enum class ContributorRole(val displayName: String, val apiValue: String) {
    AUTHOR("Author", "AUTHOR"),
    NARRATOR("Narrator", "NARRATOR"),
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

    // Contributor search
    val contributorSearchQuery: String = "",
    val contributorSearchResults: List<ContributorSearchResult> = emptyList(),
    val isSearchingContributors: Boolean = false,
    val isOfflineSearchResult: Boolean = false,

    // Track if changes have been made
    val hasChanges: Boolean = false,
) {
    /**
     * Authors from contributor list.
     */
    val authors: List<EditableContributor>
        get() = contributors.filter { ContributorRole.AUTHOR in it.roles }

    /**
     * Narrators from contributor list.
     */
    val narrators: List<EditableContributor>
        get() = contributors.filter { ContributorRole.NARRATOR in it.roles }
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

    // Contributor management
    data class ContributorSearchQueryChanged(val query: String) : BookEditUiEvent
    data class AddContributor(val name: String, val roles: Set<ContributorRole>) : BookEditUiEvent
    data class SelectSearchResult(val result: ContributorSearchResult, val roles: Set<ContributorRole>) : BookEditUiEvent
    data class RemoveContributor(val contributor: EditableContributor) : BookEditUiEvent
    data class UpdateContributorRoles(val contributor: EditableContributor, val roles: Set<ContributorRole>) : BookEditUiEvent
    data object ClearContributorSearch : BookEditUiEvent

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

    // Internal query flow for debounced contributor search
    private val contributorQueryFlow = MutableStateFlow("")
    private var searchJob: Job? = null

    // Track original values for change detection
    private var originalTitle: String = ""
    private var originalSubtitle: String = ""
    private var originalDescription: String = ""
    private var originalSequence: String = ""
    private var originalPublishYear: String = ""
    private var originalContributors: List<EditableContributor> = emptyList()

    init {
        // Set up debounced contributor search
        contributorQueryFlow
            .debounce(300) // Wait 300ms after last keystroke
            .distinctUntilChanged()
            .filter { it.length >= 2 || it.isEmpty() } // Min 2 chars
            .onEach { query ->
                if (query.isBlank()) {
                    _state.update {
                        it.copy(
                            contributorSearchResults = emptyList(),
                            isSearchingContributors = false,
                        )
                    }
                } else {
                    performContributorSearch(query)
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
                    hasChanges = false,
                )
            }

            logger.debug { "Loaded book for editing: ${book.title}" }
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

            is BookEditUiEvent.ContributorSearchQueryChanged -> {
                _state.update { it.copy(contributorSearchQuery = event.query) }
                contributorQueryFlow.value = event.query
            }

            is BookEditUiEvent.AddContributor -> {
                addContributor(event.name, event.roles)
            }

            is BookEditUiEvent.SelectSearchResult -> {
                selectSearchResult(event.result, event.roles)
            }

            is BookEditUiEvent.RemoveContributor -> {
                _state.update { current ->
                    current.copy(contributors = current.contributors - event.contributor)
                }
                updateHasChanges()
            }

            is BookEditUiEvent.UpdateContributorRoles -> {
                updateContributorRoles(event.contributor, event.roles)
            }

            is BookEditUiEvent.ClearContributorSearch -> {
                _state.update {
                    it.copy(
                        contributorSearchQuery = "",
                        contributorSearchResults = emptyList(),
                    )
                }
                contributorQueryFlow.value = ""
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

    private fun performContributorSearch(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.update { it.copy(isSearchingContributors = true) }

            val response = contributorRepository.searchContributors(query, limit = 10)

            _state.update {
                it.copy(
                    contributorSearchResults = response.contributors,
                    isSearchingContributors = false,
                    isOfflineSearchResult = response.isOfflineResult,
                )
            }

            logger.debug { "Contributor search: ${response.contributors.size} results for '$query'" }
        }
    }

    private fun addContributor(name: String, roles: Set<ContributorRole>) {
        if (name.isBlank() || roles.isEmpty()) return

        val newContributor = EditableContributor(
            id = null, // New contributor
            name = name.trim(),
            roles = roles,
        )

        _state.update { current ->
            // Check if contributor already exists (by name)
            val existing = current.contributors.find {
                it.name.equals(name.trim(), ignoreCase = true)
            }

            if (existing != null) {
                // Merge roles
                val updated = existing.copy(roles = existing.roles + roles)
                current.copy(
                    contributors = current.contributors.map {
                        if (it == existing) updated else it
                    },
                    contributorSearchQuery = "",
                    contributorSearchResults = emptyList(),
                )
            } else {
                current.copy(
                    contributors = current.contributors + newContributor,
                    contributorSearchQuery = "",
                    contributorSearchResults = emptyList(),
                )
            }
        }

        contributorQueryFlow.value = ""
        updateHasChanges()
    }

    private fun selectSearchResult(result: ContributorSearchResult, roles: Set<ContributorRole>) {
        if (roles.isEmpty()) return

        val contributor = EditableContributor(
            id = result.id,
            name = result.name,
            roles = roles,
        )

        _state.update { current ->
            // Check if contributor already exists (by ID)
            val existing = current.contributors.find { it.id == result.id }

            if (existing != null) {
                // Merge roles
                val updated = existing.copy(roles = existing.roles + roles)
                current.copy(
                    contributors = current.contributors.map {
                        if (it == existing) updated else it
                    },
                    contributorSearchQuery = "",
                    contributorSearchResults = emptyList(),
                )
            } else {
                current.copy(
                    contributors = current.contributors + contributor,
                    contributorSearchQuery = "",
                    contributorSearchResults = emptyList(),
                )
            }
        }

        contributorQueryFlow.value = ""
        updateHasChanges()
    }

    private fun updateContributorRoles(contributor: EditableContributor, roles: Set<ContributorRole>) {
        if (roles.isEmpty()) {
            // Remove contributor if no roles
            _state.update { current ->
                current.copy(contributors = current.contributors - contributor)
            }
        } else {
            _state.update { current ->
                current.copy(
                    contributors = current.contributors.map {
                        if (it == contributor) it.copy(roles = roles) else it
                    },
                )
            }
        }
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
