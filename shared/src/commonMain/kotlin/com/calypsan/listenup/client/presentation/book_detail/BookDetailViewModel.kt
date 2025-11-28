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

                _state.value = BookDetailUiState(
                    isLoading = false,
                    book = book,
                    series = book.fullSeriesTitle,
                    description = book.description ?: "",
                    narrators = book.narratorNames,
                    genres = book.genres ?: "",
                    year = book.publishYear ?: 0,
                    rating = book.rating ?: 0.0,
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
    val series: String? = null,
    val description: String = "",
    val narrators: String = "",
    val genres: String = "",
    val year: Int = 0,
    val rating: Double = 0.0,
    val chapters: List<ChapterUiModel> = emptyList()
)

data class ChapterUiModel(
    val id: String,
    val title: String,
    val duration: String,
    val imageUrl: String?
)
