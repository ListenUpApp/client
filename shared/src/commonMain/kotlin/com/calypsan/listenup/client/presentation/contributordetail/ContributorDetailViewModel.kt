@file:Suppress("MagicNumber")

package com.calypsan.listenup.client.presentation.contributordetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.data.local.db.BookDao
import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.local.db.ContributorDao
import com.calypsan.listenup.client.data.local.db.ContributorEntity
import com.calypsan.listenup.client.data.local.db.PlaybackPositionDao
import com.calypsan.listenup.client.data.local.images.ImageStorage
import com.calypsan.listenup.client.data.repository.ContributorRepositoryContract
import com.calypsan.listenup.client.domain.model.Book
import com.calypsan.listenup.client.domain.model.Contributor
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * ViewModel for the Contributor Detail screen.
 *
 * Loads contributor information and their books grouped by role.
 * Each role (author, narrator, etc.) becomes a section with a horizontal
 * preview of books and an optional "View All" action.
 */
class ContributorDetailViewModel(
    private val contributorDao: ContributorDao,
    private val bookDao: BookDao,
    private val imageStorage: ImageStorage,
    private val playbackPositionDao: PlaybackPositionDao,
    private val contributorRepository: ContributorRepositoryContract,
) : ViewModel() {
    val state: StateFlow<ContributorDetailUiState>
        field = MutableStateFlow(ContributorDetailUiState())

    private var currentContributorId: String? = null

    /**
     * Load contributor details and their books grouped by role.
     *
     * @param contributorId The ID of the contributor to load
     */
    fun loadContributor(contributorId: String) {
        currentContributorId = contributorId
        state.value = state.value.copy(isLoading = true)

        viewModelScope.launch {
            // Observe contributor and their roles together
            combine(
                contributorDao.observeById(contributorId).filterNotNull(),
                contributorDao.observeRolesWithCountForContributor(contributorId),
            ) { contributor, rolesWithCount ->
                Pair(contributor, rolesWithCount)
            }.collect { (contributor, rolesWithCount) ->
                // Aggregate creditedAs mappings across all roles
                val allCreditedAs = mutableMapOf<String, String>()

                // For each role, load a preview of books
                val roleSections =
                    rolesWithCount.map { roleWithCount ->
                        val result = loadBooksForRole(contributorId, contributor.name, roleWithCount.role)
                        allCreditedAs.putAll(result.creditedAsMap)
                        RoleSection(
                            role = roleWithCount.role,
                            displayName = roleToDisplayName(roleWithCount.role),
                            bookCount = roleWithCount.bookCount,
                            previewBooks = result.books.take(PREVIEW_BOOK_COUNT),
                        )
                    }

                // Load progress for all preview books across all sections
                val allPreviewBooks = roleSections.flatMap { it.previewBooks }
                val bookProgress = loadProgressForBooks(allPreviewBooks)

                state.value =
                    ContributorDetailUiState(
                        isLoading = false,
                        contributor = contributor,
                        roleSections = roleSections,
                        bookProgress = bookProgress,
                        bookCreditedAs = allCreditedAs,
                        error = null,
                    )
            }
        }
    }

    /**
     * Result of loading books for a role, including creditedAs attribution.
     */
    private data class BooksForRoleResult(
        val books: List<Book>,
        /** Map of bookId to creditedAs name (when different from contributor's name) */
        val creditedAsMap: Map<String, String>,
    )

    private suspend fun loadBooksForRole(
        contributorId: String,
        contributorName: String,
        role: String,
    ): BooksForRoleResult {
        // Get the books once (not as a flow) for the preview
        val booksWithContributors =
            bookDao
                .observeByContributorAndRole(contributorId, role)
                .first()

        val books = booksWithContributors.map { bwc -> bwc.toDomain() }

        // Extract creditedAs for books where the attribution differs from contributor name
        val creditedAsMap =
            booksWithContributors
                .mapNotNull { bwc ->
                    val crossRef =
                        bwc.contributorRoles.find {
                            it.contributorId == contributorId && it.role == role
                        }
                    val creditedAs = crossRef?.creditedAs
                    // Only include if creditedAs is set and differs from the contributor's actual name
                    if (creditedAs != null && !creditedAs.equals(contributorName, ignoreCase = true)) {
                        bwc.book.id.value to creditedAs
                    } else {
                        null
                    }
                }.toMap()

        return BooksForRoleResult(books, creditedAsMap)
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

    private fun com.calypsan.listenup.client.data.local.db.BookWithContributors.toDomain(): Book {
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
            // Series loaded via junction table - not available in this simple mapper
            series = emptyList(),
            publishYear = book.publishYear,
            rating = null,
        )
    }

    // === Delete Operations ===

    /**
     * Request to delete this contributor. Shows confirmation dialog.
     */
    fun onDeleteContributor() {
        state.value = state.value.copy(showDeleteConfirmation = true)
    }

    /**
     * Confirm deletion of the contributor.
     *
     * @return true if deletion was initiated (caller should navigate away)
     */
    fun onConfirmDelete(onDeleted: () -> Unit) {
        val contributorId = currentContributorId ?: return
        state.value = state.value.copy(
            showDeleteConfirmation = false,
            isDeleting = true,
        )

        viewModelScope.launch {
            when (val result = contributorRepository.deleteContributor(contributorId)) {
                is Success -> {
                    logger.info { "Contributor deleted successfully" }
                    state.value = state.value.copy(isDeleting = false)
                    onDeleted()
                }

                is Failure -> {
                    logger.warn { "Failed to delete contributor: ${result.message}" }
                    state.value = state.value.copy(
                        isDeleting = false,
                        error = result.message,
                    )
                }
            }
        }
    }

    /**
     * Cancel deletion.
     */
    fun onDismissDelete() {
        state.value = state.value.copy(showDeleteConfirmation = false)
    }

    /**
     * Clear any displayed error.
     */
    fun onClearError() {
        state.value = state.value.copy(error = null)
    }

    companion object {
        /** Number of books to show in the horizontal preview */
        private const val PREVIEW_BOOK_COUNT = 10

        /** Threshold for showing "View All" button */
        const val VIEW_ALL_THRESHOLD = 6

        /**
         * Convert a role string to a user-friendly display name.
         */
        fun roleToDisplayName(role: String): String =
            when (role.lowercase()) {
                "author" -> "Written By"
                "narrator" -> "Narrated By"
                "translator" -> "Translated By"
                "editor" -> "Edited By"
                else -> role.replaceFirstChar { it.uppercase() }
            }
    }
}
/**
 * UI state for the Contributor Detail screen.
 */
data class ContributorDetailUiState(
    /** Start with loading=true to avoid briefly showing empty content before data loads */
    val isLoading: Boolean = true,
    val contributor: ContributorEntity? = null,
    val roleSections: List<RoleSection> = emptyList(),
    val bookProgress: Map<String, Float> = emptyMap(),
    /** Maps bookId to creditedAs name when different from contributor's name */
    val bookCreditedAs: Map<String, String> = emptyMap(),
    val error: String? = null,
    // Delete state
    val showDeleteConfirmation: Boolean = false,
    val isDeleting: Boolean = false,
)

/**
 * A section displaying books for a specific role.
 */
data class RoleSection(
    val role: String,
    val displayName: String,
    val bookCount: Int,
    val previewBooks: List<Book>,
) {
    /** Whether to show "View All" button */
    val showViewAll: Boolean
        get() = bookCount > ContributorDetailViewModel.VIEW_ALL_THRESHOLD
}
