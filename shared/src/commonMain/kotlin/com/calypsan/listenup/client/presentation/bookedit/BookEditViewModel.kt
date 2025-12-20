@file:Suppress("MagicNumber")

package com.calypsan.listenup.client.presentation.bookedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.local.images.ImageStorage
import com.calypsan.listenup.client.data.remote.BookUpdateRequest
import com.calypsan.listenup.client.data.remote.ContributorInput
import com.calypsan.listenup.client.data.remote.ContributorSearchResult
import com.calypsan.listenup.client.data.remote.GenreApiContract
import com.calypsan.listenup.client.data.remote.ImageApiContract
import com.calypsan.listenup.client.data.remote.SeriesInput
import com.calypsan.listenup.client.data.remote.SeriesSearchResult
import com.calypsan.listenup.client.data.remote.TagApiContract
import com.calypsan.listenup.client.data.repository.BookEditRepositoryContract
import com.calypsan.listenup.client.data.repository.BookRepositoryContract
import com.calypsan.listenup.client.data.repository.ContributorRepositoryContract
import com.calypsan.listenup.client.data.repository.SeriesRepositoryContract
import com.calypsan.listenup.client.domain.model.Genre
import com.calypsan.listenup.client.domain.model.Tag
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
enum class ContributorRole(
    val displayName: String,
    val apiValue: String,
) {
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
 * Series membership for editing.
 */
data class EditableSeries(
    val id: String? = null, // null for newly added series
    val name: String,
    val sequence: String? = null, // e.g., "1", "1.5"
)

/**
 * Genre for editing.
 */
data class EditableGenre(
    val id: String,
    val name: String,
    val path: String,
) {
    /**
     * Returns the parent path for display context.
     * "/fiction/fantasy/epic-fantasy" -> "Fiction > Fantasy"
     */
    val parentPath: String?
        get() {
            val segments = path.trim('/').split('/')
            if (segments.size <= 1) return null
            return segments
                .dropLast(1)
                .joinToString(" > ") { it.replaceFirstChar { c -> c.uppercase() } }
        }
}

/**
 * Tag for editing.
 */
data class EditableTag(
    val id: String,
    val name: String,
    val color: String? = null,
)

/**
 * UI state for book editing screen.
 */
data class BookEditUiState(
    // Loading states
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isUploadingCover: Boolean = false,
    val error: String? = null,
    // Book identity (for immersive header)
    val coverPath: String? = null,
    // Book metadata fields
    val bookId: String = "",
    val title: String = "",
    val subtitle: String = "",
    val description: String = "",
    val publishYear: String = "",
    val publisher: String = "",
    val language: String? = null, // ISO 639-1 code
    // Additional metadata (less commonly edited)
    val isbn: String = "",
    val asin: String = "",
    val abridged: Boolean = false,
    // Contributors
    val contributors: List<EditableContributor> = emptyList(),
    // Series (multi-series support)
    val series: List<EditableSeries> = emptyList(),
    val seriesSearchQuery: String = "",
    val seriesSearchResults: List<SeriesSearchResult> = emptyList(),
    val seriesSearchLoading: Boolean = false,
    val seriesOfflineResult: Boolean = false,
    // Per-role search state (replaces single search)
    val roleSearchQueries: Map<ContributorRole, String> = emptyMap(),
    val roleSearchResults: Map<ContributorRole, List<ContributorSearchResult>> = emptyMap(),
    val roleSearchLoading: Map<ContributorRole, Boolean> = emptyMap(),
    val roleOfflineResults: Map<ContributorRole, Boolean> = emptyMap(),
    // Visible role sections (prepopulated from existing contributors + user-added)
    val visibleRoles: Set<ContributorRole> = emptySet(),
    // Genres (system-controlled, select from existing)
    val genres: List<EditableGenre> = emptyList(),
    val allGenres: List<EditableGenre> = emptyList(), // Cached list from server
    val genreSearchQuery: String = "",
    val genreSearchResults: List<EditableGenre> = emptyList(), // Filtered locally
    // Tags (user-scoped, can create new)
    val tags: List<EditableTag> = emptyList(),
    val allTags: List<EditableTag> = emptyList(), // User's existing tags
    val tagSearchQuery: String = "",
    val tagSearchResults: List<EditableTag> = emptyList(),
    val tagSearchLoading: Boolean = false,
    val tagCreating: Boolean = false, // Creating a new tag
    // Track if changes have been made
    val hasChanges: Boolean = false,
    // Pending cover upload (stored until Save Changes)
    val pendingCoverData: ByteArray? = null,
    val pendingCoverFilename: String? = null,
    // Staging cover path for preview (separate from main cover)
    val stagingCoverPath: String? = null,
) {
    /**
     * Returns the cover path to display - staging if available, otherwise original.
     */
    val displayCoverPath: String?
        get() = stagingCoverPath ?: coverPath

    /**
     * Get contributors for a specific role.
     */
    fun contributorsForRole(role: ContributorRole): List<EditableContributor> = contributors.filter { role in it.roles }

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
    data class TitleChanged(
        val title: String,
    ) : BookEditUiEvent

    data class SubtitleChanged(
        val subtitle: String,
    ) : BookEditUiEvent

    data class DescriptionChanged(
        val description: String,
    ) : BookEditUiEvent

    data class PublishYearChanged(
        val year: String,
    ) : BookEditUiEvent

    data class PublisherChanged(
        val publisher: String,
    ) : BookEditUiEvent

    data class LanguageChanged(
        val code: String?,
    ) : BookEditUiEvent

    // Additional metadata
    data class IsbnChanged(
        val isbn: String,
    ) : BookEditUiEvent

    data class AsinChanged(
        val asin: String,
    ) : BookEditUiEvent

    data class AbridgedChanged(
        val abridged: Boolean,
    ) : BookEditUiEvent

    // Series management
    data class SeriesSearchQueryChanged(
        val query: String,
    ) : BookEditUiEvent

    data class SeriesSelected(
        val result: SeriesSearchResult,
    ) : BookEditUiEvent

    data class SeriesEntered(
        val name: String,
    ) : BookEditUiEvent

    data class SeriesSequenceChanged(
        val series: EditableSeries,
        val sequence: String,
    ) : BookEditUiEvent

    data class RemoveSeries(
        val series: EditableSeries,
    ) : BookEditUiEvent

    data object ClearSeriesSearch : BookEditUiEvent

    // Per-role contributor management
    data class RoleSearchQueryChanged(
        val role: ContributorRole,
        val query: String,
    ) : BookEditUiEvent

    data class RoleContributorSelected(
        val role: ContributorRole,
        val result: ContributorSearchResult,
    ) : BookEditUiEvent

    data class RoleContributorEntered(
        val role: ContributorRole,
        val name: String,
    ) : BookEditUiEvent

    data class ClearRoleSearch(
        val role: ContributorRole,
    ) : BookEditUiEvent

    data class AddRoleSection(
        val role: ContributorRole,
    ) : BookEditUiEvent

    data class RemoveContributor(
        val contributor: EditableContributor,
        val role: ContributorRole,
    ) : BookEditUiEvent

    data class RemoveRoleSection(
        val role: ContributorRole,
    ) : BookEditUiEvent

    // Genre management (select from existing only)
    data class GenreSearchQueryChanged(
        val query: String,
    ) : BookEditUiEvent

    data class GenreSelected(
        val genre: EditableGenre,
    ) : BookEditUiEvent

    data class RemoveGenre(
        val genre: EditableGenre,
    ) : BookEditUiEvent

    // Tag management (select existing or create new)
    data class TagSearchQueryChanged(
        val query: String,
    ) : BookEditUiEvent

    data class TagSelected(
        val tag: EditableTag,
    ) : BookEditUiEvent

    data class TagEntered(
        val name: String,
    ) : BookEditUiEvent // Create new tag inline

    data class RemoveTag(
        val tag: EditableTag,
    ) : BookEditUiEvent

    // Cover upload
    data class UploadCover(
        val imageData: ByteArray,
        val filename: String,
    ) : BookEditUiEvent

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

    data class ShowSaveSuccess(
        val message: String,
    ) : BookEditNavAction
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
@Suppress("LargeClass", "TooManyFunctions")
class BookEditViewModel(
    private val bookRepository: BookRepositoryContract,
    private val bookEditRepository: BookEditRepositoryContract,
    private val contributorRepository: ContributorRepositoryContract,
    private val seriesRepository: SeriesRepositoryContract,
    private val genreApi: GenreApiContract,
    private val tagApi: TagApiContract,
    private val imageApi: ImageApiContract,
    private val imageStorage: ImageStorage,
) : ViewModel() {
    val state: StateFlow<BookEditUiState>
        field = MutableStateFlow(BookEditUiState())

    val navActions: StateFlow<BookEditNavAction?>
        field = MutableStateFlow<BookEditNavAction?>(null)

    // Per-role query flows for debounced search
    private val roleQueryFlows = mutableMapOf<ContributorRole, MutableStateFlow<String>>()
    private val roleSearchJobs = mutableMapOf<ContributorRole, Job>()

    // Series search flow
    private val seriesQueryFlow = MutableStateFlow("")
    private var seriesSearchJob: Job? = null

    // Track original values for change detection
    private var originalTitle: String = ""
    private var originalSubtitle: String = ""
    private var originalDescription: String = ""
    private var originalPublishYear: String = ""
    private var originalPublisher: String = ""
    private var originalLanguage: String? = null
    private var originalIsbn: String = ""
    private var originalAsin: String = ""
    private var originalAbridged: Boolean = false
    private var originalContributors: List<EditableContributor> = emptyList()
    private var originalSeries: List<EditableSeries> = emptyList()
    private var originalGenres: List<EditableGenre> = emptyList()
    private var originalTags: List<EditableTag> = emptyList()
    private var originalCoverPath: String? = null

    // Tag search flow
    private val tagQueryFlow = MutableStateFlow("")
    private var tagSearchJob: Job? = null

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
                    state.update {
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

    init {
        setupSeriesSearch()
        setupTagSearch()
    }

    /**
     * Set up debounced search for tags.
     */
    private fun setupTagSearch() {
        tagQueryFlow
            .debounce(300)
            .distinctUntilChanged()
            .filter { it.length >= 2 || it.isEmpty() }
            .onEach { query ->
                if (query.isBlank()) {
                    state.update {
                        it.copy(
                            tagSearchResults = emptyList(),
                            tagSearchLoading = false,
                        )
                    }
                } else {
                    performTagSearch(query)
                }
            }.launchIn(viewModelScope)
    }

    /**
     * Set up debounced search for series.
     */
    private fun setupSeriesSearch() {
        seriesQueryFlow
            .debounce(300)
            .distinctUntilChanged()
            .filter { it.length >= 2 || it.isEmpty() }
            .onEach { query ->
                if (query.isBlank()) {
                    state.update {
                        it.copy(
                            seriesSearchResults = emptyList(),
                            seriesSearchLoading = false,
                        )
                    }
                } else {
                    performSeriesSearch(query)
                }
            }.launchIn(viewModelScope)
    }

    /**
     * Load book data for editing.
     */
    fun loadBook(bookId: String) {
        viewModelScope.launch {
            state.update { it.copy(isLoading = true, bookId = bookId) }

            val book = bookRepository.getBook(bookId)
            if (book == null) {
                state.update { it.copy(isLoading = false, error = "Book not found") }
                return@launch
            }

            // Convert domain contributors to editable format
            val editableContributors =
                book.allContributors.map { contributor ->
                    EditableContributor(
                        id = contributor.id,
                        name = contributor.name,
                        roles =
                            contributor.roles
                                .mapNotNull { ContributorRole.fromApiValue(it) }
                                .toSet(),
                    )
                }

            // Determine visible roles from existing contributors
            val rolesFromContributors =
                editableContributors
                    .flatMap { it.roles }
                    .toSet()

            // Always show Author section, plus any roles that have contributors
            val initialVisibleRoles = rolesFromContributors + ContributorRole.AUTHOR

            // Set up search for each visible role
            initialVisibleRoles.forEach { role ->
                setupRoleSearch(role)
            }

            // Convert domain series to editable format
            val editableSeries =
                book.series.map { s ->
                    EditableSeries(
                        id = s.seriesId,
                        name = s.seriesName,
                        sequence = s.sequence,
                    )
                }

            // Load genres and tags from server
            val (allGenres, bookGenres) = loadGenresForBook(bookId)
            val (allTags, bookTags) = loadTagsForBook(bookId)

            // Store original values
            originalTitle = book.title
            originalSubtitle = book.subtitle ?: ""
            originalDescription = book.description ?: ""
            originalPublishYear = book.publishYear?.toString() ?: ""
            originalPublisher = book.publisher ?: ""
            originalLanguage = book.language
            originalIsbn = book.isbn ?: ""
            originalAsin = book.asin ?: ""
            originalAbridged = book.abridged
            originalContributors = editableContributors
            originalSeries = editableSeries
            originalGenres = bookGenres
            originalTags = bookTags
            originalCoverPath = book.coverPath

            state.update {
                it.copy(
                    isLoading = false,
                    coverPath = book.coverPath,
                    title = book.title,
                    subtitle = book.subtitle ?: "",
                    description = book.description ?: "",
                    publishYear = book.publishYear?.toString() ?: "",
                    publisher = book.publisher ?: "",
                    language = book.language,
                    isbn = book.isbn ?: "",
                    asin = book.asin ?: "",
                    abridged = book.abridged,
                    contributors = editableContributors,
                    series = editableSeries,
                    visibleRoles = initialVisibleRoles,
                    genres = bookGenres,
                    allGenres = allGenres,
                    tags = bookTags,
                    allTags = allTags,
                    hasChanges = false,
                )
            }

            logger.debug {
                "Loaded book for editing: ${book.title}, description=${book.description?.take(
                    50,
                ) ?: "null"}"
            }
        }
    }

    /**
     * Load all genres and book's current genres.
     * Loads separately so a failure in one doesn't prevent the other from loading.
     */
    private suspend fun loadGenresForBook(bookId: String): Pair<List<EditableGenre>, List<EditableGenre>> {
        // Load all genres (public endpoint)
        val allGenres =
            try {
                genreApi.listGenres().map { it.toEditable() }
            } catch (e: Exception) {
                logger.error(e) { "Failed to load all genres" }
                emptyList()
            }

        // Load book's genres (requires auth)
        val bookGenres =
            try {
                genreApi.getBookGenres(bookId).map { it.toEditable() }
            } catch (e: Exception) {
                logger.error(e) { "Failed to load book genres" }
                emptyList()
            }

        return allGenres to bookGenres
    }

    /**
     * Load all user tags and book's current tags.
     * Loads separately so a failure in one doesn't prevent the other from loading.
     */
    private suspend fun loadTagsForBook(bookId: String): Pair<List<EditableTag>, List<EditableTag>> {
        // Load all user tags
        val allTags =
            try {
                tagApi.getUserTags().map { it.toEditable() }
            } catch (e: Exception) {
                logger.error(e) { "Failed to load all tags" }
                emptyList()
            }

        // Load book's tags
        val bookTags =
            try {
                tagApi.getBookTags(bookId).map { it.toEditable() }
            } catch (e: Exception) {
                logger.error(e) { "Failed to load book tags" }
                emptyList()
            }

        return allTags to bookTags
    }

    private fun Genre.toEditable() = EditableGenre(id = id, name = name, path = path)

    private fun Tag.toEditable() = EditableTag(id = id, name = name, color = color)

    /**
     * Handle UI events.
     */
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    fun onEvent(event: BookEditUiEvent) {
        when (event) {
            is BookEditUiEvent.TitleChanged -> {
                state.update { it.copy(title = event.title) }
                updateHasChanges()
            }

            is BookEditUiEvent.SubtitleChanged -> {
                state.update { it.copy(subtitle = event.subtitle) }
                updateHasChanges()
            }

            is BookEditUiEvent.DescriptionChanged -> {
                state.update { it.copy(description = event.description) }
                updateHasChanges()
            }

            is BookEditUiEvent.PublishYearChanged -> {
                // Only allow numeric input
                val filtered = event.year.filter { it.isDigit() }.take(4)
                state.update { it.copy(publishYear = filtered) }
                updateHasChanges()
            }

            is BookEditUiEvent.PublisherChanged -> {
                state.update { it.copy(publisher = event.publisher) }
                updateHasChanges()
            }

            is BookEditUiEvent.LanguageChanged -> {
                state.update { it.copy(language = event.code) }
                updateHasChanges()
            }

            is BookEditUiEvent.IsbnChanged -> {
                state.update { it.copy(isbn = event.isbn) }
                updateHasChanges()
            }

            is BookEditUiEvent.AsinChanged -> {
                state.update { it.copy(asin = event.asin) }
                updateHasChanges()
            }

            is BookEditUiEvent.AbridgedChanged -> {
                state.update { it.copy(abridged = event.abridged) }
                updateHasChanges()
            }

            // Series events
            is BookEditUiEvent.SeriesSearchQueryChanged -> {
                state.update { it.copy(seriesSearchQuery = event.query) }
                seriesQueryFlow.value = event.query
            }

            is BookEditUiEvent.SeriesSelected -> {
                selectSeries(event.result)
            }

            is BookEditUiEvent.SeriesEntered -> {
                addSeries(event.name)
            }

            is BookEditUiEvent.SeriesSequenceChanged -> {
                updateSeriesSequence(event.series, event.sequence)
            }

            is BookEditUiEvent.RemoveSeries -> {
                removeSeries(event.series)
            }

            is BookEditUiEvent.ClearSeriesSearch -> {
                state.update {
                    it.copy(
                        seriesSearchQuery = "",
                        seriesSearchResults = emptyList(),
                    )
                }
                seriesQueryFlow.value = ""
            }

            is BookEditUiEvent.RoleSearchQueryChanged -> {
                state.update {
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
                state.update {
                    it.copy(
                        roleSearchQueries = it.roleSearchQueries + (event.role to ""),
                        roleSearchResults = it.roleSearchResults - event.role,
                    )
                }
                roleQueryFlows[event.role]?.value = ""
            }

            is BookEditUiEvent.AddRoleSection -> {
                setupRoleSearch(event.role)
                state.update {
                    it.copy(visibleRoles = it.visibleRoles + event.role)
                }
            }

            is BookEditUiEvent.RemoveContributor -> {
                removeContributorFromRole(event.contributor, event.role)
            }

            is BookEditUiEvent.RemoveRoleSection -> {
                removeRoleSection(event.role)
            }

            // Genre events
            is BookEditUiEvent.GenreSearchQueryChanged -> {
                val query = event.query
                state.update { it.copy(genreSearchQuery = query) }
                // Filter locally from allGenres
                filterGenres(query)
            }

            is BookEditUiEvent.GenreSelected -> {
                selectGenre(event.genre)
            }

            is BookEditUiEvent.RemoveGenre -> {
                removeGenre(event.genre)
            }

            // Tag events
            is BookEditUiEvent.TagSearchQueryChanged -> {
                state.update { it.copy(tagSearchQuery = event.query) }
                tagQueryFlow.value = event.query
            }

            is BookEditUiEvent.TagSelected -> {
                selectTag(event.tag)
            }

            is BookEditUiEvent.TagEntered -> {
                createAndAddTag(event.name)
            }

            is BookEditUiEvent.RemoveTag -> {
                removeTag(event.tag)
            }

            is BookEditUiEvent.UploadCover -> {
                uploadCover(event.imageData, event.filename)
            }

            is BookEditUiEvent.Save -> {
                saveChanges()
            }

            is BookEditUiEvent.Cancel -> {
                cancelAndCleanup()
            }

            is BookEditUiEvent.DismissError -> {
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

    private fun performRoleSearch(
        role: ContributorRole,
        query: String,
    ) {
        roleSearchJobs[role]?.cancel()
        roleSearchJobs[role] =
            viewModelScope.launch {
                state.update {
                    it.copy(roleSearchLoading = it.roleSearchLoading + (role to true))
                }

                val response = contributorRepository.searchContributors(query, limit = 10)

                // Filter out contributors already added for this role
                val currentContributorIds =
                    state.value
                        .contributorsForRole(role)
                        .mapNotNull { it.id }
                        .toSet()
                val filteredResults = response.contributors.filter { it.id !in currentContributorIds }

                state.update {
                    it.copy(
                        roleSearchResults = it.roleSearchResults + (role to filteredResults),
                        roleSearchLoading = it.roleSearchLoading + (role to false),
                        roleOfflineResults = it.roleOfflineResults + (role to response.isOfflineResult),
                    )
                }

                logger.debug { "Contributor search for $role: ${filteredResults.size} results for '$query'" }
            }
    }

    private fun addRoleContributor(
        role: ContributorRole,
        name: String,
    ) {
        if (name.isBlank()) return

        val trimmedName = name.trim()

        state.update { current ->
            // Check if contributor already exists (by name)
            val existing =
                current.contributors.find {
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
                    contributors =
                        current.contributors.map {
                            if (it == existing) updated else it
                        },
                    roleSearchQueries = current.roleSearchQueries + (role to ""),
                    roleSearchResults = current.roleSearchResults - role,
                )
            } else {
                // Add new contributor with this role
                val newContributor =
                    EditableContributor(
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

    private fun selectRoleContributor(
        role: ContributorRole,
        result: ContributorSearchResult,
    ) {
        state.update { current ->
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
                    contributors =
                        current.contributors.map {
                            if (it == existing) updated else it
                        },
                    roleSearchQueries = current.roleSearchQueries + (role to ""),
                    roleSearchResults = current.roleSearchResults - role,
                )
            } else {
                // Add new contributor with this role
                val newContributor =
                    EditableContributor(
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

    private fun removeContributorFromRole(
        contributor: EditableContributor,
        role: ContributorRole,
    ) {
        state.update { current ->
            val updatedRoles = contributor.roles - role

            val updatedContributors =
                if (updatedRoles.isEmpty()) {
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
            val updatedVisibleRoles =
                if (!roleStillHasContributors && role != ContributorRole.AUTHOR) {
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
        state.update { current ->
            // Remove role from all contributors
            val updatedContributors =
                current.contributors.mapNotNull { contributor ->
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

    // ========== Series Helper Methods ==========

    private fun performSeriesSearch(query: String) {
        seriesSearchJob?.cancel()
        seriesSearchJob =
            viewModelScope.launch {
                state.update { it.copy(seriesSearchLoading = true) }

                val response = seriesRepository.searchSeries(query, limit = 10)

                // Filter out series already added to this book
                val currentSeriesIds =
                    state.value.series
                        .mapNotNull { it.id }
                        .toSet()
                val filteredResults = response.series.filter { it.id !in currentSeriesIds }

                state.update {
                    it.copy(
                        seriesSearchResults = filteredResults,
                        seriesSearchLoading = false,
                        seriesOfflineResult = response.isOfflineResult,
                    )
                }

                logger.debug { "Series search: ${filteredResults.size} results for '$query'" }
            }
    }

    private fun selectSeries(result: SeriesSearchResult) {
        state.update { current ->
            // Check if series already exists
            val existing = current.series.find { it.id == result.id }
            if (existing != null) {
                // Already added - just clear search
                return@update current.copy(
                    seriesSearchQuery = "",
                    seriesSearchResults = emptyList(),
                )
            }

            // Add new series
            val newSeries =
                EditableSeries(
                    id = result.id,
                    name = result.name,
                    sequence = null,
                )
            current.copy(
                series = current.series + newSeries,
                seriesSearchQuery = "",
                seriesSearchResults = emptyList(),
            )
        }

        seriesQueryFlow.value = ""
        updateHasChanges()
    }

    private fun addSeries(name: String) {
        if (name.isBlank()) return

        val trimmedName = name.trim()

        state.update { current ->
            // Check if series already exists (by name)
            val existing =
                current.series.find {
                    it.name.equals(trimmedName, ignoreCase = true)
                }
            if (existing != null) {
                // Already added - just clear search
                return@update current.copy(
                    seriesSearchQuery = "",
                    seriesSearchResults = emptyList(),
                )
            }

            // Add new series (no ID = will be created on server)
            val newSeries =
                EditableSeries(
                    id = null,
                    name = trimmedName,
                    sequence = null,
                )
            current.copy(
                series = current.series + newSeries,
                seriesSearchQuery = "",
                seriesSearchResults = emptyList(),
            )
        }

        seriesQueryFlow.value = ""
        updateHasChanges()
    }

    private fun updateSeriesSequence(
        targetSeries: EditableSeries,
        sequence: String,
    ) {
        state.update { current ->
            current.copy(
                series =
                    current.series.map {
                        if (it == targetSeries) it.copy(sequence = sequence.ifBlank { null }) else it
                    },
            )
        }
        updateHasChanges()
    }

    private fun removeSeries(targetSeries: EditableSeries) {
        state.update { current ->
            current.copy(series = current.series - targetSeries)
        }
        updateHasChanges()
    }

    // ========== Genre Helper Methods ==========

    /**
     * Filter genres locally based on search query.
     */
    private fun filterGenres(query: String) {
        if (query.isBlank()) {
            state.update { it.copy(genreSearchResults = emptyList()) }
            return
        }

        val lowerQuery = query.lowercase()
        val currentGenreIds =
            state.value.genres
                .map { it.id }
                .toSet()

        val filtered =
            state.value.allGenres
                .filter { genre ->
                    genre.id !in currentGenreIds &&
                        (
                            genre.name.lowercase().contains(lowerQuery) ||
                                genre.path.lowercase().contains(lowerQuery)
                        )
                }.take(10)

        state.update { it.copy(genreSearchResults = filtered) }
    }

    private fun selectGenre(genre: EditableGenre) {
        state.update { current ->
            // Check if already added
            if (current.genres.any { it.id == genre.id }) {
                return@update current.copy(
                    genreSearchQuery = "",
                    genreSearchResults = emptyList(),
                )
            }

            current.copy(
                genres = current.genres + genre,
                genreSearchQuery = "",
                genreSearchResults = emptyList(),
            )
        }
        updateHasChanges()
    }

    private fun removeGenre(genre: EditableGenre) {
        state.update { current ->
            current.copy(genres = current.genres.filter { it.id != genre.id })
        }
        updateHasChanges()
    }

    // ========== Tag Helper Methods ==========

    /**
     * Perform server-side tag search.
     */
    private fun performTagSearch(query: String) {
        tagSearchJob?.cancel()
        tagSearchJob =
            viewModelScope.launch {
                state.update { it.copy(tagSearchLoading = true) }

                // Filter from allTags (already loaded)
                val lowerQuery = query.lowercase()
                val currentTagIds =
                    state.value.tags
                        .map { it.id }
                        .toSet()

                val filtered =
                    state.value.allTags
                        .filter { tag ->
                            tag.id !in currentTagIds &&
                                tag.name.lowercase().contains(lowerQuery)
                        }.take(10)

                state.update {
                    it.copy(
                        tagSearchResults = filtered,
                        tagSearchLoading = false,
                    )
                }
            }
    }

    private fun selectTag(tag: EditableTag) {
        state.update { current ->
            // Check if already added
            if (current.tags.any { it.id == tag.id }) {
                return@update current.copy(
                    tagSearchQuery = "",
                    tagSearchResults = emptyList(),
                )
            }

            current.copy(
                tags = current.tags + tag,
                tagSearchQuery = "",
                tagSearchResults = emptyList(),
            )
        }
        tagQueryFlow.value = ""
        updateHasChanges()
    }

    /**
     * Create a new tag inline and add it to the book.
     */
    private fun createAndAddTag(name: String) {
        if (name.isBlank()) return

        val trimmedName = name.trim()

        // Check if tag with same name already exists in allTags
        val existingTag =
            state.value.allTags.find {
                it.name.equals(trimmedName, ignoreCase = true)
            }

        if (existingTag != null) {
            // Use existing tag
            selectTag(existingTag)
            return
        }

        // Create new tag via API
        viewModelScope.launch {
            state.update { it.copy(tagCreating = true) }

            try {
                val newTag = tagApi.createTag(trimmedName)
                val editableTag = newTag.toEditable()

                state.update { current ->
                    current.copy(
                        tags = current.tags + editableTag,
                        allTags = current.allTags + editableTag, // Add to available tags
                        tagSearchQuery = "",
                        tagSearchResults = emptyList(),
                        tagCreating = false,
                    )
                }
                tagQueryFlow.value = ""
                updateHasChanges()

                logger.info { "Created new tag: ${newTag.name}" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to create tag: $trimmedName" }
                state.update {
                    it.copy(
                        tagCreating = false,
                        error = "Failed to create tag: ${e.message}",
                    )
                }
            }
        }
    }

    private fun removeTag(tag: EditableTag) {
        state.update { current ->
            current.copy(tags = current.tags.filter { it.id != tag.id })
        }
        updateHasChanges()
    }

    // ========== Cover Upload ==========

    /**
     * Handle cover selection.
     * Saves the image to a staging location for preview.
     * Does NOT overwrite the main cover until saveChanges() is called.
     */
    private fun uploadCover(
        imageData: ByteArray,
        filename: String,
    ) {
        val bookId = state.value.bookId
        if (bookId.isBlank()) {
            logger.error { "Cannot set cover: book ID is empty" }
            return
        }

        viewModelScope.launch {
            state.update { it.copy(isUploadingCover = true, error = null) }

            // Save to staging location for preview (doesn't overwrite original)
            when (val saveResult = imageStorage.saveCoverStaging(BookId(bookId), imageData)) {
                is Success -> {
                    val stagingPath = imageStorage.getCoverStagingPath(BookId(bookId))
                    logger.info { "Cover saved to staging for preview: $stagingPath" }

                    // Store pending data for upload when Save Changes is clicked
                    state.update {
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
                    state.update {
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
     * Cancel editing and clean up any staging files.
     */
    private fun cancelAndCleanup() {
        val bookId = state.value.bookId
        if (bookId.isNotBlank() && state.value.stagingCoverPath != null) {
            viewModelScope.launch {
                imageStorage.deleteCoverStaging(BookId(bookId))
                logger.debug { "Staging cover cleaned up on cancel" }
            }
        }
        navActions.value = BookEditNavAction.NavigateBack
    }

    /**
     * Clean up staging files when ViewModel is destroyed.
     * Handles cases where user navigates away without explicitly canceling or saving.
     */
    override fun onCleared() {
        super.onCleared()
        val bookId = state.value.bookId
        if (bookId.isNotBlank() && state.value.stagingCoverPath != null) {
            // viewModelScope is cancelled by this point, use GlobalScope for cleanup
            @Suppress("OPT_IN_USAGE")
            kotlinx.coroutines.GlobalScope.launch(com.calypsan.listenup.client.core.IODispatcher) {
                imageStorage.deleteCoverStaging(BookId(bookId))
                logger.debug { "Staging cover cleaned up on ViewModel cleared" }
            }
        }
    }

    private fun updateHasChanges() {
        val current = state.value
        val hasChanges =
            current.title != originalTitle ||
                current.subtitle != originalSubtitle ||
                current.description != originalDescription ||
                current.publishYear != originalPublishYear ||
                current.publisher != originalPublisher ||
                current.language != originalLanguage ||
                current.isbn != originalIsbn ||
                current.asin != originalAsin ||
                current.abridged != originalAbridged ||
                current.contributors != originalContributors ||
                current.series != originalSeries ||
                current.genres != originalGenres ||
                current.tags != originalTags ||
                current.pendingCoverData != null // Cover changed if we have pending data

        state.update { it.copy(hasChanges = hasChanges) }
    }

    @Suppress("CyclomaticComplexMethod", "CognitiveComplexMethod", "LongMethod")
    private fun saveChanges() {
        val current = state.value
        if (!current.hasChanges) {
            navActions.value = BookEditNavAction.NavigateBack
            return
        }

        viewModelScope.launch {
            state.update { it.copy(isSaving = true, error = null) }

            try {
                // Update metadata
                val metadataChanged =
                    current.title != originalTitle ||
                        current.subtitle != originalSubtitle ||
                        current.description != originalDescription ||
                        current.publishYear != originalPublishYear ||
                        current.publisher != originalPublisher ||
                        current.language != originalLanguage ||
                        current.isbn != originalIsbn ||
                        current.asin != originalAsin ||
                        current.abridged != originalAbridged

                if (metadataChanged) {
                    val updateRequest =
                        BookUpdateRequest(
                            title = if (current.title != originalTitle) current.title else null,
                            subtitle = if (current.subtitle != originalSubtitle) current.subtitle else null,
                            description = if (current.description != originalDescription) current.description else null,
                            publishYear =
                                if (current.publishYear !=
                                    originalPublishYear
                                ) {
                                    current.publishYear.ifBlank { null }
                                } else {
                                    null
                                },
                            publisher =
                                if (current.publisher !=
                                    originalPublisher
                                ) {
                                    current.publisher.ifBlank { null }
                                } else {
                                    null
                                },
                            language = if (current.language != originalLanguage) current.language else null,
                            isbn = if (current.isbn != originalIsbn) current.isbn.ifBlank { null } else null,
                            asin = if (current.asin != originalAsin) current.asin.ifBlank { null } else null,
                            abridged = if (current.abridged != originalAbridged) current.abridged else null,
                        )

                    when (val result = bookEditRepository.updateBook(current.bookId, updateRequest)) {
                        is Success -> {
                            logger.info { "Book metadata updated" }
                        }

                        is Failure -> {
                            state.update { it.copy(isSaving = false, error = "Failed to save: ${result.message}") }
                            return@launch
                        }
                    }
                }

                // Update contributors
                val contributorsChanged = current.contributors != originalContributors
                if (contributorsChanged) {
                    val contributorInputs =
                        current.contributors.map { editable ->
                            ContributorInput(
                                name = editable.name,
                                roles = editable.roles.map { it.apiValue },
                            )
                        }

                    when (val result = bookEditRepository.setBookContributors(current.bookId, contributorInputs)) {
                        is Success -> {
                            logger.info { "Book contributors updated" }
                        }

                        is Failure -> {
                            state.update {
                                it.copy(
                                    isSaving = false,
                                    error = "Failed to save contributors: ${result.message}",
                                )
                            }
                            return@launch
                        }
                    }
                }

                // Update series
                val seriesChanged = current.series != originalSeries
                if (seriesChanged) {
                    val seriesInputs =
                        current.series.map { editable ->
                            SeriesInput(
                                name = editable.name,
                                sequence = editable.sequence,
                            )
                        }

                    when (val result = bookEditRepository.setBookSeries(current.bookId, seriesInputs)) {
                        is Success -> {
                            logger.info { "Book series updated" }
                        }

                        is Failure -> {
                            state.update {
                                it.copy(
                                    isSaving = false,
                                    error = "Failed to save series: ${result.message}",
                                )
                            }
                            return@launch
                        }
                    }
                }

                // Update genres
                val genresChanged = current.genres != originalGenres
                if (genresChanged) {
                    try {
                        genreApi.setBookGenres(current.bookId, current.genres.map { it.id })
                        logger.info { "Book genres updated" }
                    } catch (e: Exception) {
                        logger.error(e) { "Failed to save genres" }
                        state.update { it.copy(isSaving = false, error = "Failed to save genres: ${e.message}") }
                        return@launch
                    }
                }

                // Update tags (add/remove individually)
                val tagsChanged = current.tags != originalTags
                if (tagsChanged) {
                    try {
                        val currentTagIds = current.tags.map { it.id }.toSet()
                        val originalTagIds = originalTags.map { it.id }.toSet()

                        // Remove tags that were removed
                        val removedTagIds = originalTagIds - currentTagIds
                        for (tagId in removedTagIds) {
                            tagApi.removeTagFromBook(current.bookId, tagId)
                        }

                        // Add tags that were added
                        val addedTagIds = currentTagIds - originalTagIds
                        for (tagId in addedTagIds) {
                            tagApi.addTagToBook(current.bookId, tagId)
                        }

                        logger.info { "Book tags updated: +${addedTagIds.size}, -${removedTagIds.size}" }
                    } catch (e: Exception) {
                        logger.error(e) { "Failed to save tags" }
                        // Tags are less critical, log but don't fail the save
                        logger.warn { "Continuing despite tag save failure" }
                    }
                }

                // Commit staging cover and upload if changed
                val pendingCoverData = current.pendingCoverData
                val pendingCoverFilename = current.pendingCoverFilename
                if (pendingCoverData != null && pendingCoverFilename != null) {
                    // First, commit staging to main cover location
                    when (val commitResult = imageStorage.commitCoverStaging(BookId(current.bookId))) {
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
                            imageApi.uploadBookCover(
                                current.bookId,
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

                state.update {
                    it.copy(
                        isSaving = false,
                        hasChanges = false,
                        pendingCoverData = null,
                        pendingCoverFilename = null,
                        stagingCoverPath = null,
                    )
                }
                navActions.value = BookEditNavAction.NavigateBack
            } catch (e: Exception) {
                logger.error(e) { "Failed to save book changes" }
                state.update { it.copy(isSaving = false, error = "Failed to save: ${e.message}") }
            }
        }
    }
}
