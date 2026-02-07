package com.calypsan.listenup.client.presentation.bookdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.domain.model.Book
import com.calypsan.listenup.client.domain.model.Genre
import com.calypsan.listenup.client.domain.model.Shelf
import com.calypsan.listenup.client.domain.model.Tag
import com.calypsan.listenup.client.domain.repository.BookRepository
import com.calypsan.listenup.client.domain.repository.GenreRepository
import com.calypsan.listenup.client.domain.repository.ShelfRepository
import com.calypsan.listenup.client.domain.repository.PlaybackPositionRepository
import com.calypsan.listenup.client.domain.repository.TagRepository
import com.calypsan.listenup.client.domain.repository.UserRepository
import com.calypsan.listenup.client.domain.usecase.shelf.AddBooksToShelfUseCase
import com.calypsan.listenup.client.domain.usecase.shelf.CreateShelfUseCase
import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Success
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * ViewModel for the Book Detail screen.
 *
 * Uses reactive flows with [flatMapLatest] to automatically switch observers
 * when navigating between books, eliminating manual Job cancellation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BookDetailViewModel(
    private val bookRepository: BookRepository,
    private val genreRepository: GenreRepository,
    private val tagRepository: TagRepository,
    private val playbackPositionRepository: PlaybackPositionRepository,
    private val userRepository: UserRepository,
    private val shelfRepository: ShelfRepository,
    private val addBooksToShelfUseCase: AddBooksToShelfUseCase,
    private val createShelfUseCase: CreateShelfUseCase,
) : ViewModel() {
    val state: StateFlow<BookDetailUiState>
        field = MutableStateFlow(BookDetailUiState())

    /**
     * The currently displayed book ID.
     * Setting this triggers automatic observer switching via [flatMapLatest].
     */
    private val currentBookId = MutableStateFlow<String?>(null)

    init {
        // Observe admin status
        viewModelScope.launch {
            userRepository.observeIsAdmin().collect { isAdmin ->
                state.update { it.copy(isAdmin = isAdmin) }
            }
        }

        // Reactive genre observer - automatically switches when book changes
        viewModelScope.launch {
            currentBookId
                .flatMapLatest { bookId ->
                    if (bookId != null) {
                        genreRepository.observeGenresForBook(bookId)
                    } else {
                        flowOf(emptyList())
                    }
                }.collect { genres ->
                    state.update {
                        it.copy(
                            genres = genres,
                            genresList = genres.map { g -> g.name },
                        )
                    }
                }
        }

        // Reactive book-specific tags observer - automatically switches when book changes
        viewModelScope.launch {
            currentBookId
                .flatMapLatest { bookId ->
                    if (bookId != null) {
                        tagRepository.observeTagsForBook(bookId)
                    } else {
                        flowOf(emptyList())
                    }
                }.collect { tags ->
                    state.update {
                        it.copy(
                            tags = tags,
                            isLoadingTags = false,
                        )
                    }
                }
        }

        // All tags observer (doesn't depend on current book - for tag picker)
        viewModelScope.launch {
            tagRepository
                .observeAll()
                .collect { allTags ->
                    state.update { it.copy(allTags = allTags) }
                }
        }
    }

    /**
     * User's shelves for the shelf picker sheet.
     */
    val myShelves: StateFlow<List<Shelf>> =
        userRepository
            .observeCurrentUser()
            .flatMapLatest { user ->
                if (user != null) {
                    shelfRepository.observeMyShelves(user.id.value)
                } else {
                    flowOf(emptyList())
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun loadBook(bookId: String) {
        viewModelScope.launch {
            state.value = state.value.copy(isLoading = true)
            val book = bookRepository.getBook(bookId)

            if (book != null) {
                val chapters =
                    bookRepository.getChapters(bookId).map { domainChapter ->
                        ChapterUiModel(
                            id = domainChapter.id,
                            title = domainChapter.title,
                            duration = domainChapter.formatDuration(),
                            imageUrl = null, // Placeholder
                        )
                    }

                // Filter out subtitles that are just series name + book number
                val displaySubtitle =
                    book.subtitle?.let { subtitle ->
                        if (isSubtitleRedundant(subtitle, book.seriesName, book.seriesSequence)) {
                            null
                        } else {
                            subtitle
                        }
                    }

                // Load progress for this book
                val position = playbackPositionRepository.get(bookId)
                val progress =
                    if (position != null && book.duration > 0) {
                        (position.positionMs.toFloat() / book.duration).coerceIn(0f, 1f)
                    } else {
                        null
                    }

                // Check if book is marked as finished (authoritative from isFinished flag)
                val isComplete = position?.isFinished ?: false

                // Calculate time remaining if there's progress (but not complete)
                val timeRemaining =
                    if (progress != null && progress > 0f && !isComplete) {
                        val remainingMs = book.duration - (position?.positionMs ?: 0L)
                        formatTimeRemaining(remainingMs)
                    } else {
                        null
                    }

                // Trigger reactive observers for genres and tags via flatMapLatest
                currentBookId.value = bookId

                // Update state, preserving flow-managed values (isAdmin, allTags)
                state.update { currentState ->
                    currentState.copy(
                        isLoading = false,
                        book = book,
                        subtitle = displaySubtitle,
                        series = book.fullSeriesTitle,
                        description = book.description ?: "",
                        narrators = book.narratorNames,
                        year = book.publishYear,
                        rating = book.rating,
                        chapters = chapters,
                        isComplete = isComplete,
                        startedAtMs = position?.startedAtMs,
                        progress = if (progress != null && progress > 0f && !isComplete) progress else null,
                        timeRemainingFormatted = timeRemaining,
                        addedAt = book.addedAt.epochMillis,
                        // Reset book-specific values (will be populated by flatMapLatest)
                        genres = emptyList(),
                        genresList = emptyList(),
                        tags = emptyList(),
                        isLoadingTags = true,
                        error = null,
                    )
                }
            } else {
                state.update { currentState ->
                    currentState.copy(
                        isLoading = false,
                        error = "Book not found",
                    )
                }
            }
        }
    }

    /**
     * Show the tag picker sheet.
     */
    fun showTagPicker() {
        state.update { it.copy(showTagPicker = true) }
    }

    /**
     * Hide the tag picker sheet.
     */
    fun hideTagPicker() {
        state.update { it.copy(showTagPicker = false) }
    }

    /**
     * Add a tag to the current book.
     *
     * @param slug The tag slug to add
     */
    fun addTag(slug: String) {
        val bookId =
            state.value.book
                ?.id
                ?.value ?: return
        viewModelScope.launch {
            try {
                tagRepository.addTagToBook(bookId, slug)
                // Observer will update UI automatically
            } catch (e: Exception) {
                logger.error(e) { "Failed to add tag '$slug' to book $bookId" }
            }
        }
    }

    /**
     * Remove a tag from the current book.
     *
     * @param slug The tag slug to remove
     */
    fun removeTag(slug: String) {
        val bookId =
            state.value.book
                ?.id
                ?.value ?: return
        val tag = state.value.tags.find { it.slug == slug } ?: return
        viewModelScope.launch {
            try {
                tagRepository.removeTagFromBook(bookId, slug, tag.id)
                // Observer will update UI automatically
            } catch (e: Exception) {
                logger.error(e) { "Failed to remove tag '$slug' from book $bookId" }
            }
        }
    }

    /**
     * Add a new tag to the current book.
     *
     * The raw input will be normalized to a slug by the server.
     * If the tag doesn't exist, it will be created.
     *
     * @param rawInput The tag text to add (will be normalized)
     */
    fun addNewTag(rawInput: String) {
        val bookId =
            state.value.book
                ?.id
                ?.value ?: return
        viewModelScope.launch {
            try {
                tagRepository.addTagToBook(bookId, rawInput)
                // Observer will update UI automatically
                hideTagPicker()
            } catch (e: Exception) {
                logger.error(e) { "Failed to add tag '$rawInput' to book $bookId" }
            }
        }
    }

    /**
     * Mark the current book as complete with optional date overrides.
     *
     * @param startedAt Optional start date in epoch milliseconds
     * @param finishedAt Optional finish date in epoch milliseconds
     */
    fun markComplete(
        startedAt: Long? = null,
        finishedAt: Long? = null,
    ) {
        val bookId =
            state.value.book
                ?.id
                ?.value ?: return
        viewModelScope.launch {
            state.update { it.copy(isMarkingComplete = true) }
            when (playbackPositionRepository.markComplete(bookId, startedAt, finishedAt)) {
                is Success -> {
                    state.update { it.copy(isMarkingComplete = false, isComplete = true) }
                    logger.info { "Marked book $bookId as complete" }
                }

                is Failure -> {
                    state.update { it.copy(isMarkingComplete = false) }
                    logger.error { "Failed to mark book $bookId as complete" }
                }
            }
        }
    }

    /**
     * Discard progress for the current book (start over / DNF).
     */
    fun discardProgress() {
        val bookId =
            state.value.book
                ?.id
                ?.value ?: return
        viewModelScope.launch {
            state.update { it.copy(isDiscardingProgress = true) }
            when (playbackPositionRepository.discardProgress(bookId)) {
                is Success -> {
                    state.update {
                        it.copy(
                            isDiscardingProgress = false,
                            isComplete = false,
                            progress = null,
                            timeRemainingFormatted = null,
                        )
                    }
                    logger.info { "Discarded progress for book $bookId" }
                }

                is Failure -> {
                    state.update { it.copy(isDiscardingProgress = false) }
                    logger.error { "Failed to discard progress for book $bookId" }
                }
            }
        }
    }

    /**
     * Restart the current book from the beginning.
     */
    fun restartBook() {
        val bookId =
            state.value.book
                ?.id
                ?.value ?: return
        viewModelScope.launch {
            state.update { it.copy(isRestarting = true) }
            when (playbackPositionRepository.restartBook(bookId)) {
                is Success -> {
                    state.update {
                        it.copy(
                            isRestarting = false,
                            isComplete = false,
                            progress = 0f,
                        )
                    }
                    logger.info { "Restarted book $bookId" }
                }

                is Failure -> {
                    state.update { it.copy(isRestarting = false) }
                    logger.error { "Failed to restart book $bookId" }
                }
            }
        }
    }

    /**
     * Add the current book to an existing shelf.
     */
    fun addBookToShelf(shelfId: String) {
        val bookId =
            state.value.book
                ?.id
                ?.value ?: return
        viewModelScope.launch {
            state.update { it.copy(isAddingToShelf = true) }
            when (val result = addBooksToShelfUseCase(shelfId, listOf(bookId))) {
                is Success -> {
                    state.update { it.copy(isAddingToShelf = false, showShelfPicker = false) }
                    logger.info { "Added book $bookId to shelf $shelfId" }
                }

                is Failure -> {
                    state.update { it.copy(isAddingToShelf = false, shelfError = result.message) }
                    logger.error { "Failed to add book $bookId to shelf $shelfId: ${result.message}" }
                }
            }
        }
    }

    /**
     * Create a new shelf and add the current book to it.
     */
    fun createShelfAndAddBook(name: String) {
        val bookId =
            state.value.book
                ?.id
                ?.value ?: return
        viewModelScope.launch {
            state.update { it.copy(isAddingToShelf = true) }
            when (val result = createShelfUseCase(name, null)) {
                is Success -> {
                    val shelf = result.data
                    when (val addResult = addBooksToShelfUseCase(shelf.id, listOf(bookId))) {
                        is Success -> {
                            state.update { it.copy(isAddingToShelf = false, showShelfPicker = false) }
                            logger.info { "Created shelf '${shelf.name}' and added book $bookId" }
                        }

                        is Failure -> {
                            state.update { it.copy(isAddingToShelf = false, shelfError = addResult.message) }
                            logger.error { "Created shelf but failed to add book $bookId: ${addResult.message}" }
                        }
                    }
                }

                is Failure -> {
                    state.update { it.copy(isAddingToShelf = false, shelfError = result.message) }
                    logger.error { "Failed to create shelf '$name': ${result.message}" }
                }
            }
        }
    }

    fun clearShelfError() {
        state.update { it.copy(shelfError = null) }
    }

    fun showShelfPicker() {
        state.update { it.copy(showShelfPicker = true) }
    }

    fun hideShelfPicker() {
        state.update { it.copy(showShelfPicker = false) }
    }
}

data class BookDetailUiState(
    val isLoading: Boolean = true,
    val book: Book? = null,
    val error: String? = null,
    val isAdmin: Boolean = false,
    val isComplete: Boolean = false,
    // Existing started-at for pre-populating the mark-complete dialog
    val startedAtMs: Long? = null,
    // Loading states for progress management actions
    val isMarkingComplete: Boolean = false,
    val isDiscardingProgress: Boolean = false,
    val isRestarting: Boolean = false,
    // Extended metadata for UI prototype (now in DB)
    val subtitle: String? = null,
    val series: String? = null,
    val description: String = "",
    val narrators: String = "",
    val year: Int? = null,
    val rating: Double? = null,
    val addedAt: Long? = null, // Epoch milliseconds when book was added to library
    val chapters: List<ChapterUiModel> = emptyList(),
    // Progress (for overlay display)
    val progress: Float? = null,
    val timeRemainingFormatted: String? = null,
    // Genres (loaded from API)
    val genres: List<Genre> = emptyList(),
    val genresList: List<String> = emptyList(),
    // Tags (global community descriptors)
    val tags: List<Tag> = emptyList(),
    val allTags: List<Tag> = emptyList(),
    val isLoadingTags: Boolean = false,
    val showTagPicker: Boolean = false,
    // Shelf picker
    val showShelfPicker: Boolean = false,
    val isAddingToShelf: Boolean = false,
    val shelfError: String? = null,
)

data class ChapterUiModel(
    val id: String,
    val title: String,
    val duration: String,
    val imageUrl: String?,
)

/**
 * Format milliseconds as human-readable time remaining.
 * E.g., "2h 15m left" or "45m left"
 */
private fun formatTimeRemaining(ms: Long): String {
    val totalMinutes = ms / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60

    return when {
        hours > 0 -> "${hours}h ${minutes}m left"
        minutes > 0 -> "${minutes}m left"
        else -> "< 1m left"
    }
}

/**
 * Checks if a subtitle is redundant because it's just the series name and book number.
 *
 * Examples of redundant subtitles:
 * - "The Stormlight Archive, Book 1"
 * - "Mistborn #3"
 * - "Book 2 of The Wheel of Time"
 *
 * The heuristic removes the series name and common book number patterns,
 * then checks if there's any meaningful content left.
 */
private fun isSubtitleRedundant(
    subtitle: String,
    seriesName: String?,
    seriesSequence: String?,
): Boolean {
    // If no series info, subtitle is not redundant
    if (seriesName.isNullOrBlank()) return false

    val normalizedSubtitle = subtitle.lowercase().trim()
    val normalizedSeriesName = seriesName.lowercase().trim()

    // Check if subtitle contains the series name
    if (!normalizedSubtitle.contains(normalizedSeriesName)) return false

    // Remove series name from subtitle
    var remaining = normalizedSubtitle.replace(normalizedSeriesName, "")

    // Remove common book number patterns
    val bookNumberPatterns =
        listOf(
            // "Book 1", "Book One", "Book I"
            Regex(
                """book\s*[#]?\s*(\d+|one|two|three|four|five|six|seven|eight|nine|ten|i{1,3}|iv|v|vi{0,3}|ix|x)""",
                RegexOption.IGNORE_CASE,
            ),
            // "#1", "# 1"
            Regex("""#\s*\d+"""),
            // "Part 1", "Part One"
            Regex("""part\s*[#]?\s*(\d+|one|two|three|four|five|six|seven|eight|nine|ten)""", RegexOption.IGNORE_CASE),
            // "Volume 1", "Vol. 1", "Vol 1"
            Regex("""vol(ume|\.?)?\s*[#]?\s*\d+""", RegexOption.IGNORE_CASE),
            // Just a number (if sequence matches)
            seriesSequence?.let { Regex("""\b${Regex.escape(it)}\b""") },
        ).filterNotNull()

    for (pattern in bookNumberPatterns) {
        remaining = remaining.replace(pattern, "")
    }

    // Remove common separators and punctuation
    remaining =
        remaining
            .replace(Regex("""[,.:;|\-–—/\\()\[\]{}]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    // If very little meaningful content remains (less than 3 chars), it's redundant
    return remaining.length < 3
}
