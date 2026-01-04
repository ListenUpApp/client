@file:Suppress("MagicNumber")

package com.calypsan.listenup.client.presentation.bookedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.IODispatcher
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.local.images.ImageStorage
import com.calypsan.listenup.client.data.remote.BookUpdateRequest
import com.calypsan.listenup.client.data.remote.ContributorInput
import com.calypsan.listenup.client.data.remote.GenreApiContract
import com.calypsan.listenup.client.data.remote.ImageApiContract
import com.calypsan.listenup.client.data.remote.SeriesInput
import com.calypsan.listenup.client.data.remote.TagApiContract
import com.calypsan.listenup.client.data.repository.BookEditRepositoryContract
import com.calypsan.listenup.client.data.repository.BookRepositoryContract
import com.calypsan.listenup.client.data.repository.ContributorRepositoryContract
import com.calypsan.listenup.client.data.repository.SeriesRepositoryContract
import com.calypsan.listenup.client.domain.model.Genre
import com.calypsan.listenup.client.domain.model.Tag
import com.calypsan.listenup.client.presentation.bookedit.delegates.ContributorEditDelegate
import com.calypsan.listenup.client.presentation.bookedit.delegates.CoverUploadDelegate
import com.calypsan.listenup.client.presentation.bookedit.delegates.GenreTagEditDelegate
import com.calypsan.listenup.client.presentation.bookedit.delegates.SeriesEditDelegate
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * ViewModel for the book edit screen.
 *
 * Orchestrates editing operations through focused delegates:
 * - ContributorEditDelegate: Per-role contributor search and management
 * - SeriesEditDelegate: Series search and management
 * - GenreTagEditDelegate: Genre filtering and tag creation
 * - CoverUploadDelegate: Cover staging and upload
 *
 * @property bookRepository Repository for loading book data
 * @property bookEditRepository Repository for saving edits
 * @property contributorRepository Repository for contributor search
 * @property seriesRepository Repository for series search
 * @property genreApi API for genre operations
 * @property tagApi API for tag operations
 * @property imageApi API for cover upload
 * @property imageStorage Local storage for cover images
 */
@Suppress("TooManyFunctions")
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
    // Use traditional pattern for mutable state shared with delegates
    private val _state = MutableStateFlow(BookEditUiState())
    val state: StateFlow<BookEditUiState> = _state

    private val _navActions = MutableStateFlow<BookEditNavAction?>(null)
    val navActions: StateFlow<BookEditNavAction?> = _navActions

    // Delegates for focused editing operations
    private val contributorDelegate =
        ContributorEditDelegate(
            state = _state,
            contributorRepository = contributorRepository,
            scope = viewModelScope,
            onChangesMade = ::updateHasChanges,
        )

    private val seriesDelegate =
        SeriesEditDelegate(
            state = _state,
            seriesRepository = seriesRepository,
            scope = viewModelScope,
            onChangesMade = ::updateHasChanges,
        )

    private val genreTagDelegate =
        GenreTagEditDelegate(
            state = _state,
            tagApi = tagApi,
            scope = viewModelScope,
            onChangesMade = ::updateHasChanges,
        )

    private val coverDelegate =
        CoverUploadDelegate(
            state = _state,
            imageStorage = imageStorage,
            scope = viewModelScope,
            onChangesMade = ::updateHasChanges,
        )

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
    private var originalAddedAt: Long? = null
    private var originalContributors: List<EditableContributor> = emptyList()
    private var originalSeries: List<EditableSeries> = emptyList()
    private var originalGenres: List<EditableGenre> = emptyList()
    private var originalTags: List<EditableTag> = emptyList()
    private var originalCoverPath: String? = null

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
                contributorDelegate.setupRoleSearch(role)
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
            originalAddedAt = book.addedAt.epochMillis
            originalContributors = editableContributors
            originalSeries = editableSeries
            originalGenres = bookGenres
            originalTags = bookTags
            originalCoverPath = book.coverPath

            _state.update {
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
                    addedAt = book.addedAt.epochMillis,
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
                "Loaded book for editing: ${book.title}, description=${book.description?.take(50) ?: "null"}"
            }
        }
    }

    /**
     * Handle UI events by routing to appropriate delegates.
     */
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    fun onEvent(event: BookEditUiEvent) {
        when (event) {
            // Metadata changes - handled directly
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

            is BookEditUiEvent.PublishYearChanged -> {
                val filtered = event.year.filter { it.isDigit() }.take(4)
                _state.update { it.copy(publishYear = filtered) }
                updateHasChanges()
            }

            is BookEditUiEvent.PublisherChanged -> {
                _state.update { it.copy(publisher = event.publisher) }
                updateHasChanges()
            }

            is BookEditUiEvent.LanguageChanged -> {
                _state.update { it.copy(language = event.code) }
                updateHasChanges()
            }

            is BookEditUiEvent.IsbnChanged -> {
                _state.update { it.copy(isbn = event.isbn) }
                updateHasChanges()
            }

            is BookEditUiEvent.AsinChanged -> {
                _state.update { it.copy(asin = event.asin) }
                updateHasChanges()
            }

            is BookEditUiEvent.AbridgedChanged -> {
                _state.update { it.copy(abridged = event.abridged) }
                updateHasChanges()
            }

            is BookEditUiEvent.AddedAtChanged -> {
                _state.update { it.copy(addedAt = event.epochMillis) }
                updateHasChanges()
            }

            // Series events - delegate to SeriesEditDelegate
            is BookEditUiEvent.SeriesSearchQueryChanged -> {
                seriesDelegate.updateSearchQuery(event.query)
            }

            is BookEditUiEvent.SeriesSelected -> {
                seriesDelegate.selectSeries(event.result)
            }

            is BookEditUiEvent.SeriesEntered -> {
                seriesDelegate.addSeries(event.name)
            }

            is BookEditUiEvent.SeriesSequenceChanged -> {
                seriesDelegate.updateSeriesSequence(event.series, event.sequence)
            }

            is BookEditUiEvent.RemoveSeries -> {
                seriesDelegate.removeSeries(event.series)
            }

            is BookEditUiEvent.ClearSeriesSearch -> {
                seriesDelegate.clearSearch()
            }

            // Contributor events - delegate to ContributorEditDelegate
            is BookEditUiEvent.RoleSearchQueryChanged -> {
                contributorDelegate.updateSearchQuery(event.role, event.query)
            }

            is BookEditUiEvent.RoleContributorSelected -> {
                contributorDelegate.selectContributor(event.role, event.result)
            }

            is BookEditUiEvent.RoleContributorEntered -> {
                contributorDelegate.addContributor(event.role, event.name)
            }

            is BookEditUiEvent.ClearRoleSearch -> {
                contributorDelegate.clearSearch(event.role)
            }

            is BookEditUiEvent.AddRoleSection -> {
                contributorDelegate.addRoleSection(event.role)
            }

            is BookEditUiEvent.RemoveContributor -> {
                contributorDelegate.removeContributorFromRole(event.contributor, event.role)
            }

            is BookEditUiEvent.RemoveRoleSection -> {
                contributorDelegate.removeRoleSection(event.role)
            }

            // Genre events - delegate to GenreTagEditDelegate
            is BookEditUiEvent.GenreSearchQueryChanged -> {
                genreTagDelegate.updateGenreSearchQuery(event.query)
            }

            is BookEditUiEvent.GenreSelected -> {
                genreTagDelegate.selectGenre(event.genre)
            }

            is BookEditUiEvent.RemoveGenre -> {
                genreTagDelegate.removeGenre(event.genre)
            }

            // Tag events - delegate to GenreTagEditDelegate
            is BookEditUiEvent.TagSearchQueryChanged -> {
                genreTagDelegate.updateTagSearchQuery(event.query)
            }

            is BookEditUiEvent.TagSelected -> {
                genreTagDelegate.selectTag(event.tag)
            }

            is BookEditUiEvent.TagEntered -> {
                genreTagDelegate.createAndAddTag(event.name)
            }

            is BookEditUiEvent.RemoveTag -> {
                genreTagDelegate.removeTag(event.tag)
            }

            // Cover events - delegate to CoverUploadDelegate
            is BookEditUiEvent.UploadCover -> {
                coverDelegate.uploadCover(event.imageData, event.filename)
            }

            // Actions - handled directly
            is BookEditUiEvent.Save -> {
                saveChanges()
            }

            is BookEditUiEvent.Cancel -> {
                cancelAndCleanup()
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

    /**
     * Clean up staging files when ViewModel is destroyed.
     */
    override fun onCleared() {
        super.onCleared()
        coverDelegate.cleanupStagingOnClear(IODispatcher)
    }

    // ========== Private Methods ==========

    private suspend fun loadGenresForBook(bookId: String): Pair<List<EditableGenre>, List<EditableGenre>> {
        val allGenres =
            try {
                genreApi.listGenres().map { it.toEditable() }
            } catch (e: Exception) {
                logger.error(e) { "Failed to load all genres" }
                emptyList()
            }

        val bookGenres =
            try {
                genreApi.getBookGenres(bookId).map { it.toEditable() }
            } catch (e: Exception) {
                logger.error(e) { "Failed to load book genres" }
                emptyList()
            }

        return allGenres to bookGenres
    }

    private suspend fun loadTagsForBook(bookId: String): Pair<List<EditableTag>, List<EditableTag>> {
        val allTags =
            try {
                tagApi.listTags().map { it.toEditable() }
            } catch (e: Exception) {
                logger.error(e) { "Failed to load all tags" }
                emptyList()
            }

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

    private fun Tag.toEditable() = EditableTag(id = id, slug = slug)

    private fun cancelAndCleanup() {
        coverDelegate.cleanupStagingOnCancel()
        _navActions.value = BookEditNavAction.NavigateBack
    }

    private fun updateHasChanges() {
        val current = _state.value
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
                current.addedAt != originalAddedAt ||
                current.contributors != originalContributors ||
                current.series != originalSeries ||
                current.genres != originalGenres ||
                current.tags != originalTags ||
                current.pendingCoverData != null

        _state.update { it.copy(hasChanges = hasChanges) }
    }

    @Suppress("CyclomaticComplexMethod", "CognitiveComplexMethod", "LongMethod")
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
                val metadataChanged =
                    current.title != originalTitle ||
                        current.subtitle != originalSubtitle ||
                        current.description != originalDescription ||
                        current.publishYear != originalPublishYear ||
                        current.publisher != originalPublisher ||
                        current.language != originalLanguage ||
                        current.isbn != originalIsbn ||
                        current.asin != originalAsin ||
                        current.abridged != originalAbridged ||
                        current.addedAt != originalAddedAt

                if (metadataChanged) {
                    val updateRequest =
                        BookUpdateRequest(
                            title = if (current.title != originalTitle) current.title else null,
                            subtitle = if (current.subtitle != originalSubtitle) current.subtitle else null,
                            description = if (current.description != originalDescription) current.description else null,
                            publishYear =
                                if (current.publishYear != originalPublishYear) {
                                    current.publishYear.ifBlank { null }
                                } else {
                                    null
                                },
                            publisher =
                                if (current.publisher != originalPublisher) {
                                    current.publisher.ifBlank { null }
                                } else {
                                    null
                                },
                            language = if (current.language != originalLanguage) current.language else null,
                            isbn = if (current.isbn != originalIsbn) current.isbn.ifBlank { null } else null,
                            asin = if (current.asin != originalAsin) current.asin.ifBlank { null } else null,
                            abridged = if (current.abridged != originalAbridged) current.abridged else null,
                            createdAt =
                                if (current.addedAt != originalAddedAt && current.addedAt != null) {
                                    kotlinx.datetime.Instant
                                        .fromEpochMilliseconds(current.addedAt)
                                        .toString()
                                } else {
                                    null
                                },
                        )

                    when (val result = bookEditRepository.updateBook(current.bookId, updateRequest)) {
                        is Success -> {
                            logger.info { "Book metadata updated" }
                        }

                        is Failure -> {
                            _state.update { it.copy(isSaving = false, error = "Failed to save: ${result.message}") }
                            return@launch
                        }
                    }
                }

                // Update contributors
                if (current.contributors != originalContributors) {
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
                            _state.update {
                                it.copy(isSaving = false, error = "Failed to save contributors: ${result.message}")
                            }
                            return@launch
                        }
                    }
                }

                // Update series
                if (current.series != originalSeries) {
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
                            _state.update {
                                it.copy(isSaving = false, error = "Failed to save series: ${result.message}")
                            }
                            return@launch
                        }
                    }
                }

                // Update genres
                if (current.genres != originalGenres) {
                    try {
                        genreApi.setBookGenres(current.bookId, current.genres.map { it.id })
                        logger.info { "Book genres updated" }
                    } catch (e: Exception) {
                        logger.error(e) { "Failed to save genres" }
                        _state.update { it.copy(isSaving = false, error = "Failed to save genres: ${e.message}") }
                        return@launch
                    }
                }

                // Update tags (using slugs for add/remove)
                if (current.tags != originalTags) {
                    try {
                        val currentSlugs = current.tags.map { it.slug }.toSet()
                        val originalSlugs = originalTags.map { it.slug }.toSet()

                        val removedSlugs = originalSlugs - currentSlugs
                        for (slug in removedSlugs) {
                            tagApi.removeTagFromBook(current.bookId, slug)
                        }

                        val addedSlugs = currentSlugs - originalSlugs
                        for (slug in addedSlugs) {
                            tagApi.addTagToBook(current.bookId, slug)
                        }

                        logger.info { "Book tags updated: +${addedSlugs.size}, -${removedSlugs.size}" }
                    } catch (e: Exception) {
                        logger.error(e) { "Failed to save tags" }
                        logger.warn { "Continuing despite tag save failure" }
                    }
                }

                // Commit staging cover and upload if changed
                val pendingCoverData = current.pendingCoverData
                val pendingCoverFilename = current.pendingCoverFilename
                if (pendingCoverData != null && pendingCoverFilename != null) {
                    when (val commitResult = imageStorage.commitCoverStaging(BookId(current.bookId))) {
                        is Success -> logger.info { "Staging cover committed to main location" }
                        is Failure -> logger.error { "Failed to commit staging cover: ${commitResult.message}" }
                    }

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
                _navActions.value = BookEditNavAction.NavigateBack
            } catch (e: Exception) {
                logger.error(e) { "Failed to save book changes" }
                _state.update { it.copy(isSaving = false, error = "Failed to save: ${e.message}") }
            }
        }
    }
}
