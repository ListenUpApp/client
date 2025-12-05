package com.calypsan.listenup.client.presentation.book_detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.data.remote.TagApi
import com.calypsan.listenup.client.data.repository.BookRepository
import com.calypsan.listenup.client.domain.model.Book
import com.calypsan.listenup.client.domain.model.Tag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the Book Detail screen.
 */
class BookDetailViewModel(
    private val bookRepository: BookRepository,
    private val tagApi: TagApi
) : ViewModel() {

    private val _state = MutableStateFlow(BookDetailUiState())
    val state: StateFlow<BookDetailUiState> = _state.asStateFlow()

    fun loadBook(bookId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val book = bookRepository.getBook(bookId)
            
            if (book != null) {
                val chapters = bookRepository.getChapters(bookId).map { domainChapter ->
                    ChapterUiModel(
                        id = domainChapter.id,
                        title = domainChapter.title,
                        duration = domainChapter.formatDuration(),
                        imageUrl = null // Placeholder
                    )
                }

                // Filter out subtitles that are just series name + book number
                val displaySubtitle = book.subtitle?.let { subtitle ->
                    if (isSubtitleRedundant(subtitle, book.seriesName, book.seriesSequence)) {
                        null
                    } else {
                        subtitle
                    }
                }

                // Parse comma-separated genres into list for chip display
                val genresList = book.genres
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() }
                    ?: emptyList()

                _state.value = BookDetailUiState(
                    isLoading = false,
                    book = book,
                    subtitle = displaySubtitle,
                    series = book.fullSeriesTitle,
                    description = book.description ?: "",
                    narrators = book.narratorNames,
                    genres = book.genres ?: "",
                    genresList = genresList,
                    year = book.publishYear,
                    rating = book.rating,
                    chapters = chapters
                )

                // Load tags for this book (non-blocking, optional feature)
                loadTags(bookId)
            } else {
                _state.value = BookDetailUiState(
                    isLoading = false,
                    error = "Book not found"
                )
            }
        }
    }

    /**
     * Load tags for the current book.
     * Tags are optional - failures don't affect the main screen.
     */
    private fun loadTags(bookId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingTags = true) }
            try {
                val bookTags = tagApi.getBookTags(bookId)
                val allTags = tagApi.getUserTags()
                _state.update {
                    it.copy(
                        tags = bookTags,
                        allUserTags = allTags,
                        isLoadingTags = false
                    )
                }
            } catch (e: Exception) {
                // Tags are optional - don't fail the whole screen
                _state.update { it.copy(isLoadingTags = false) }
            }
        }
    }

    /**
     * Show the tag picker sheet.
     */
    fun showTagPicker() {
        _state.update { it.copy(showTagPicker = true) }
    }

    /**
     * Hide the tag picker sheet.
     */
    fun hideTagPicker() {
        _state.update { it.copy(showTagPicker = false) }
    }

    /**
     * Add a tag to the current book.
     */
    fun addTag(tagId: String) {
        val bookId = _state.value.book?.id?.value ?: return
        viewModelScope.launch {
            try {
                tagApi.addTagToBook(bookId, tagId)
                loadTags(bookId) // Refresh
            } catch (e: Exception) {
                // TODO: Show error to user
            }
        }
    }

    /**
     * Remove a tag from the current book.
     */
    fun removeTag(tagId: String) {
        val bookId = _state.value.book?.id?.value ?: return
        viewModelScope.launch {
            try {
                tagApi.removeTagFromBook(bookId, tagId)
                loadTags(bookId) // Refresh
            } catch (e: Exception) {
                // TODO: Show error to user
            }
        }
    }

    /**
     * Create a new tag and add it to the current book.
     */
    fun createAndAddTag(name: String) {
        val bookId = _state.value.book?.id?.value ?: return
        viewModelScope.launch {
            try {
                val newTag = tagApi.createTag(name)
                tagApi.addTagToBook(bookId, newTag.id)
                loadTags(bookId) // Refresh
                hideTagPicker()
            } catch (e: Exception) {
                // TODO: Show error to user
            }
        }
    }
}

data class BookDetailUiState(
    val isLoading: Boolean = true,
    val book: Book? = null,
    val error: String? = null,

    // Extended metadata for UI prototype (now in DB)
    val subtitle: String? = null,
    val series: String? = null,
    val description: String = "",
    val narrators: String = "",
    val genres: String = "",
    val genresList: List<String> = emptyList(),
    val year: Int? = null,
    val rating: Double? = null,
    val chapters: List<ChapterUiModel> = emptyList(),

    // Tags
    val tags: List<Tag> = emptyList(),
    val allUserTags: List<Tag> = emptyList(),
    val isLoadingTags: Boolean = false,
    val showTagPicker: Boolean = false
)

data class ChapterUiModel(
    val id: String,
    val title: String,
    val duration: String,
    val imageUrl: String?
)

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
    seriesSequence: String?
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
    val bookNumberPatterns = listOf(
        // "Book 1", "Book One", "Book I"
        Regex("""book\s*[#]?\s*(\d+|one|two|three|four|five|six|seven|eight|nine|ten|i{1,3}|iv|v|vi{0,3}|ix|x)""", RegexOption.IGNORE_CASE),
        // "#1", "# 1"
        Regex("""#\s*\d+"""),
        // "Part 1", "Part One"
        Regex("""part\s*[#]?\s*(\d+|one|two|three|four|five|six|seven|eight|nine|ten)""", RegexOption.IGNORE_CASE),
        // "Volume 1", "Vol. 1", "Vol 1"
        Regex("""vol(ume|\.?)?\s*[#]?\s*\d+""", RegexOption.IGNORE_CASE),
        // Just a number (if sequence matches)
        seriesSequence?.let { Regex("""\b${Regex.escape(it)}\b""") }
    ).filterNotNull()

    for (pattern in bookNumberPatterns) {
        remaining = remaining.replace(pattern, "")
    }

    // Remove common separators and punctuation
    remaining = remaining
        .replace(Regex("""[,.:;|\-–—/\\()\[\]{}]"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()

    // If very little meaningful content remains (less than 3 chars), it's redundant
    return remaining.length < 3
}
