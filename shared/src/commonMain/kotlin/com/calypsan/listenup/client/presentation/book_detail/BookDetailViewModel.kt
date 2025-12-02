package com.calypsan.listenup.client.presentation.book_detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.data.repository.BookRepository
import com.calypsan.listenup.client.domain.model.Book
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Book Detail screen.
 */
class BookDetailViewModel(
    private val bookRepository: BookRepository
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

                _state.value = BookDetailUiState(
                    isLoading = false,
                    book = book,
                    subtitle = displaySubtitle,
                    series = book.fullSeriesTitle,
                    description = book.description ?: "",
                    narrators = book.narratorNames,
                    genres = book.genres ?: "",
                    year = book.publishYear,
                    rating = book.rating,
                    chapters = chapters
                )
            } else {
                _state.value = BookDetailUiState(
                    isLoading = false,
                    error = "Book not found"
                )
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
    val year: Int? = null,
    val rating: Double? = null,
    val chapters: List<ChapterUiModel> = emptyList()
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
