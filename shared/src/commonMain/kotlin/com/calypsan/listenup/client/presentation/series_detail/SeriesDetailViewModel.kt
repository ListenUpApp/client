package com.calypsan.listenup.client.presentation.series_detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.data.local.db.SeriesDao
import com.calypsan.listenup.client.data.repository.BookRepository
import com.calypsan.listenup.client.domain.model.Book
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * ViewModel for the Series Detail screen.
 *
 * Loads and manages series information with its books for display.
 *
 * @property seriesDao DAO for series data
 * @property bookRepository Repository for book data with cover path resolution
 */
class SeriesDetailViewModel(
    private val seriesDao: SeriesDao,
    private val bookRepository: BookRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SeriesDetailUiState())
    val state: StateFlow<SeriesDetailUiState> = _state.asStateFlow()

    /**
     * Load series details by ID.
     *
     * Observes the series reactively so UI updates automatically
     * when series data changes (e.g., after sync).
     *
     * @param seriesId The ID of the series to load
     */
    fun loadSeries(seriesId: String) {
        _state.value = _state.value.copy(isLoading = true)

        viewModelScope.launch {
            seriesDao.observeByIdWithBooks(seriesId).collectLatest { seriesWithBooks ->
                if (seriesWithBooks != null) {
                    // Convert book entities to domain models with cover paths
                    val books = seriesWithBooks.books
                        .sortedBy { it.sequence?.toFloatOrNull() ?: Float.MAX_VALUE }
                        .map { bookEntity ->
                            bookRepository.getBook(bookEntity.id.value)
                        }
                        .filterNotNull()

                    _state.value = SeriesDetailUiState(
                        isLoading = false,
                        seriesName = seriesWithBooks.series.name,
                        seriesDescription = seriesWithBooks.series.description,
                        books = books,
                        error = null
                    )
                } else {
                    _state.value = SeriesDetailUiState(
                        isLoading = false,
                        error = "Series not found"
                    )
                }
            }
        }
    }
}

/**
 * UI state for the Series Detail screen.
 */
data class SeriesDetailUiState(
    val isLoading: Boolean = false,
    val seriesName: String = "",
    val seriesDescription: String? = null,
    val books: List<Book> = emptyList(),
    val error: String? = null
)
