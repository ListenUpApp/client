package com.calypsan.listenup.client.presentation.bookdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.local.db.PlaybackPositionDao
import com.calypsan.listenup.client.data.local.db.TagDao
import com.calypsan.listenup.client.data.local.db.UserDao
import com.calypsan.listenup.client.data.remote.GenreApiContract
import com.calypsan.listenup.client.data.remote.TagApiContract
import com.calypsan.listenup.client.data.repository.BookRepositoryContract
import com.calypsan.listenup.client.domain.model.Book
import com.calypsan.listenup.client.domain.model.Genre
import com.calypsan.listenup.client.domain.model.Tag
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * ViewModel for the Book Detail screen.
 */
class BookDetailViewModel(
    private val bookRepository: BookRepositoryContract,
    private val genreApi: GenreApiContract,
    private val tagApi: TagApiContract,
    private val tagDao: TagDao,
    private val playbackPositionDao: PlaybackPositionDao,
    private val userDao: UserDao,
) : ViewModel() {
    val state: StateFlow<BookDetailUiState>
        field = MutableStateFlow(BookDetailUiState())

    private var tagObserverJob: Job? = null

    init {
        // Observe admin status
        viewModelScope.launch {
            userDao.observeCurrentUser().collect { user ->
                state.update { it.copy(isAdmin = user?.isRoot == true) }
            }
        }
    }

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
                val position = playbackPositionDao.get(BookId(bookId))
                val progress =
                    if (position != null && book.duration > 0) {
                        (position.positionMs.toFloat() / book.duration).coerceIn(0f, 1f)
                    } else {
                        null
                    }

                // Check if book is complete (99%+ progress)
                val isComplete = progress != null && progress >= 0.99f

                // Calculate time remaining if there's progress (but not complete)
                val timeRemaining =
                    if (progress != null && progress > 0f && !isComplete) {
                        val remainingMs = book.duration - (position?.positionMs ?: 0L)
                        formatTimeRemaining(remainingMs)
                    } else {
                        null
                    }

                state.value =
                    BookDetailUiState(
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
                        progress = if (progress != null && progress > 0f && !isComplete) progress else null,
                        timeRemainingFormatted = timeRemaining,
                        addedAt = book.addedAt.epochMillis,
                    )

                // Load genres and tags for this book (non-blocking, optional features)
                loadGenres(bookId)
                loadTags(bookId)
            } else {
                state.value =
                    BookDetailUiState(
                        isLoading = false,
                        error = "Book not found",
                    )
            }
        }
    }

    /**
     * Load genres for the current book.
     * Genres are optional - failures don't affect the main screen.
     */
    private fun loadGenres(bookId: String) {
        viewModelScope.launch {
            try {
                val bookGenres = genreApi.getBookGenres(bookId)
                state.update {
                    it.copy(
                        genres = bookGenres,
                        genresList = bookGenres.map { g -> g.name },
                    )
                }
            } catch (e: Exception) {
                // Genres are optional - don't fail the whole screen
                logger.warn(e) { "Failed to load genres" }
            }
        }
    }

    /**
     * Load tags for the current book.
     * Tags are optional - failures don't affect the main screen.
     *
     * This method:
     * 1. Fetches initial tags from the API
     * 2. Starts observing local storage for real-time SSE updates
     */
    private fun loadTags(bookId: String) {
        // Cancel any previous observer
        tagObserverJob?.cancel()

        // Start observing local database for real-time updates
        tagObserverJob =
            viewModelScope.launch {
                tagDao.observeTagsForBook(BookId(bookId)).collect { localTags ->
                    val domainTags =
                        localTags.map { entity ->
                            Tag(
                                id = entity.id,
                                slug = entity.slug,
                                bookCount = entity.bookCount,
                                createdAt = Instant.fromEpochMilliseconds(entity.createdAt.epochMillis),
                            )
                        }
                    if (domainTags.isNotEmpty()) {
                        state.update {
                            it.copy(
                                tags = domainTags,
                                isLoadingTags = false,
                            )
                        }
                    }
                }
            }

        // Also fetch from API to get latest data and allTags
        viewModelScope.launch {
            state.update { it.copy(isLoadingTags = true) }
            try {
                val bookTags = tagApi.getBookTags(bookId)
                val allTags = tagApi.listTags()
                state.update {
                    it.copy(
                        tags = bookTags,
                        allTags = allTags,
                        isLoadingTags = false,
                    )
                }
            } catch (e: Exception) {
                // Tags are optional - don't fail the whole screen
                logger.warn(e) { "Failed to load tags from API" }
                state.update { it.copy(isLoadingTags = false) }
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
                tagApi.addTagToBook(bookId, slug)
                loadTags(bookId) // Refresh
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
        viewModelScope.launch {
            try {
                tagApi.removeTagFromBook(bookId, slug)
                loadTags(bookId) // Refresh
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
                tagApi.addTagToBook(bookId, rawInput)
                loadTags(bookId) // Refresh
                hideTagPicker()
            } catch (e: Exception) {
                logger.error(e) { "Failed to add tag '$rawInput' to book $bookId" }
            }
        }
    }
}

data class BookDetailUiState(
    val isLoading: Boolean = true,
    val book: Book? = null,
    val error: String? = null,
    val isAdmin: Boolean = false,
    val isComplete: Boolean = false,
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
