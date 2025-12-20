@file:Suppress("MagicNumber")

package com.calypsan.listenup.client.presentation.contributordetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.data.local.db.BookDao
import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.local.db.BookWithContributors
import com.calypsan.listenup.client.data.local.db.ContributorDao
import com.calypsan.listenup.client.data.local.db.PlaybackPositionDao
import com.calypsan.listenup.client.data.local.images.ImageStorage
import com.calypsan.listenup.client.domain.model.Book
import com.calypsan.listenup.client.domain.model.BookSeries
import com.calypsan.listenup.client.domain.model.Contributor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * ViewModel for the Contributor Books screen (View All).
 *
 * Shows all books for a contributor in a specific role, grouped by series.
 * Series appear first (alphabetically), followed by standalone books.
 */
class ContributorBooksViewModel(
    private val contributorDao: ContributorDao,
    private val bookDao: BookDao,
    private val imageStorage: ImageStorage,
    private val playbackPositionDao: PlaybackPositionDao,
) : ViewModel() {
    val state: StateFlow<ContributorBooksUiState>
        field = MutableStateFlow(ContributorBooksUiState())

    /**
     * Load all books for a contributor in a specific role.
     *
     * @param contributorId The ID of the contributor
     * @param role The role to filter by (e.g., "author", "narrator")
     */
    fun loadBooks(
        contributorId: String,
        role: String,
    ) {
        state.value = state.value.copy(isLoading = true)

        viewModelScope.launch {
            // Load contributor name for the title
            contributorDao.observeById(contributorId).filterNotNull().collectLatest { contributor ->
                state.value = state.value.copy(contributorName = contributor.name)
            }
        }

        viewModelScope.launch {
            bookDao.observeByContributorAndRole(contributorId, role).collectLatest { booksWithContributors ->
                val books = booksWithContributors.map { it.toDomain() }
                val contributorName = state.value.contributorName

                // Load progress for all books
                val bookProgress = loadProgressForBooks(books)

                // Extract creditedAs for books where the attribution differs
                val bookCreditedAs =
                    booksWithContributors
                        .mapNotNull { bwc ->
                            val crossRef =
                                bwc.contributorRoles.find {
                                    it.contributorId == contributorId && it.role == role
                                }
                            val creditedAs = crossRef?.creditedAs
                            if (creditedAs != null && !creditedAs.equals(contributorName, ignoreCase = true)) {
                                bwc.book.id.value to creditedAs
                            } else {
                                null
                            }
                        }.toMap()

                // Group books by series
                val seriesGroups =
                    books
                        .filter { it.seriesName != null }
                        .groupBy { it.seriesName!! }
                        .map { (seriesName, seriesBooks) ->
                            SeriesGroup(
                                seriesName = seriesName,
                                books =
                                    seriesBooks.sortedBy {
                                        it.seriesSequence?.toFloatOrNull() ?: Float.MAX_VALUE
                                    },
                            )
                        }.sortedBy { it.seriesName }

                // Standalone books (no series)
                val standaloneBooks =
                    books
                        .filter { it.seriesName == null }
                        .sortedBy { it.title }

                state.value =
                    ContributorBooksUiState(
                        isLoading = false,
                        contributorName = contributorName,
                        roleDisplayName = ContributorDetailViewModel.roleToDisplayName(role),
                        seriesGroups = seriesGroups,
                        standaloneBooks = standaloneBooks,
                        bookProgress = bookProgress,
                        bookCreditedAs = bookCreditedAs,
                        error = null,
                    )
            }
        }
    }

    /**
     * Load progress for a list of books.
     * Returns a map of bookId -> progress (0.0-1.0).
     */
    private suspend fun loadProgressForBooks(books: List<Book>): Map<String, Float> =
        books
            .mapNotNull { book ->
                val position = playbackPositionDao.get(BookId(book.id.value))
                if (position != null && book.duration > 0) {
                    val progress = (position.positionMs.toFloat() / book.duration).coerceIn(0f, 1f)
                    if (progress > 0f && progress < 0.99f) {
                        book.id.value to progress
                    } else {
                        null
                    }
                } else {
                    null
                }
            }.toMap()

    private fun BookWithContributors.toDomain(): Book {
        // Create a lookup map for contributor entities
        val contributorsById = contributors.associateBy { it.id }

        // Get authors by filtering cross-refs with role "author"
        // Use creditedAs for display name when available (preserves original attribution after merge)
        val authors =
            contributorRoles
                .filter { it.role == "author" }
                .mapNotNull { crossRef ->
                    contributorsById[crossRef.contributorId]?.let { entity ->
                        Contributor(entity.id, crossRef.creditedAs ?: entity.name)
                    }
                }.distinctBy { it.id }

        // Get narrators by filtering cross-refs with role "narrator"
        // Use creditedAs for display name when available (preserves original attribution after merge)
        val narrators =
            contributorRoles
                .filter { it.role == "narrator" }
                .mapNotNull { crossRef ->
                    contributorsById[crossRef.contributorId]?.let { entity ->
                        Contributor(entity.id, crossRef.creditedAs ?: entity.name)
                    }
                }.distinctBy { it.id }

        // Build series list from junction table data
        val seriesById = series.associateBy { it.id }
        val bookSeriesList =
            seriesSequences.mapNotNull { seq ->
                seriesById[seq.seriesId]?.let { seriesEntity ->
                    BookSeries(
                        seriesId = seriesEntity.id,
                        seriesName = seriesEntity.name,
                        sequence = seq.sequence,
                    )
                }
            }

        return Book(
            id = book.id,
            title = book.title,
            authors = authors,
            narrators = narrators,
            duration = book.totalDuration,
            coverPath = if (imageStorage.exists(book.id)) imageStorage.getCoverPath(book.id) else null,
            coverBlurHash = book.coverBlurHash,
            addedAt = book.createdAt,
            updatedAt = book.updatedAt,
            description = book.description,
            genres = emptyList(), // Loaded on-demand when editing
            tags = emptyList(), // Loaded on-demand when editing
            series = bookSeriesList,
            publishYear = book.publishYear,
            rating = null,
        )
    }
}

/**
 * UI state for the Contributor Books screen.
 */
data class ContributorBooksUiState(
    /** Start with loading=true to avoid briefly showing empty content before data loads */
    val isLoading: Boolean = true,
    val contributorName: String = "",
    val roleDisplayName: String = "",
    val seriesGroups: List<SeriesGroup> = emptyList(),
    val standaloneBooks: List<Book> = emptyList(),
    val bookProgress: Map<String, Float> = emptyMap(),
    /** Maps bookId to creditedAs name when different from contributor's name */
    val bookCreditedAs: Map<String, String> = emptyMap(),
    val error: String? = null,
) {
    /** Total number of books */
    val totalBooks: Int
        get() = seriesGroups.sumOf { it.books.size } + standaloneBooks.size

    /** Whether there are any standalone books */
    val hasStandaloneBooks: Boolean
        get() = standaloneBooks.isNotEmpty()
}

/**
 * A group of books belonging to the same series.
 */
data class SeriesGroup(
    val seriesName: String,
    val books: List<Book>,
)
