package com.calypsan.listenup.client.presentation.contributor_detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.data.local.db.BookDao
import com.calypsan.listenup.client.data.local.db.ContributorDao
import com.calypsan.listenup.client.data.local.db.ContributorEntity
import com.calypsan.listenup.client.data.local.images.ImageStorage
import com.calypsan.listenup.client.domain.model.Book
import com.calypsan.listenup.client.domain.model.Contributor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
    private val imageStorage: ImageStorage
) : ViewModel() {

    private val _state = MutableStateFlow(ContributorDetailUiState())
    val state: StateFlow<ContributorDetailUiState> = _state.asStateFlow()

    /**
     * Load contributor details and their books grouped by role.
     *
     * @param contributorId The ID of the contributor to load
     */
    fun loadContributor(contributorId: String) {
        _state.value = _state.value.copy(isLoading = true)

        viewModelScope.launch {
            // Observe contributor and their roles together
            combine(
                contributorDao.observeById(contributorId).filterNotNull(),
                contributorDao.observeRolesWithCountForContributor(contributorId)
            ) { contributor, rolesWithCount ->
                Pair(contributor, rolesWithCount)
            }.collect { (contributor, rolesWithCount) ->
                // For each role, load a preview of books
                val roleSections = rolesWithCount.map { roleWithCount ->
                    val books = loadBooksForRole(contributorId, roleWithCount.role)
                    RoleSection(
                        role = roleWithCount.role,
                        displayName = roleToDisplayName(roleWithCount.role),
                        bookCount = roleWithCount.bookCount,
                        previewBooks = books.take(PREVIEW_BOOK_COUNT)
                    )
                }

                _state.value = ContributorDetailUiState(
                    isLoading = false,
                    contributor = contributor,
                    roleSections = roleSections,
                    error = null
                )
            }
        }
    }

    private suspend fun loadBooksForRole(contributorId: String, role: String): List<Book> {
        // Get the books once (not as a flow) for the preview
        return bookDao.observeByContributorAndRole(contributorId, role)
            .first()
            .map { bwc -> bwc.toDomain() }
    }

    private fun com.calypsan.listenup.client.data.local.db.BookWithContributors.toDomain(): Book {
        // Create a lookup map for contributor entities
        val contributorsById = contributors.associateBy { it.id }

        // Get authors by filtering cross-refs with role "author"
        val authors = contributorRoles
            .filter { it.role == "author" }
            .mapNotNull { crossRef -> contributorsById[crossRef.contributorId] }
            .distinctBy { it.id }
            .map { Contributor(it.id, it.name) }

        // Get narrators by filtering cross-refs with role "narrator"
        val narrators = contributorRoles
            .filter { it.role == "narrator" }
            .mapNotNull { crossRef -> contributorsById[crossRef.contributorId] }
            .distinctBy { it.id }
            .map { Contributor(it.id, it.name) }

        return Book(
            id = book.id,
            title = book.title,
            authors = authors,
            narrators = narrators,
            duration = book.totalDuration,
            coverPath = if (imageStorage.exists(book.id)) imageStorage.getCoverPath(book.id) else null,
            addedAt = book.createdAt,
            updatedAt = book.updatedAt,
            description = book.description,
            genres = book.genres,
            seriesId = book.seriesId,
            seriesName = book.seriesName,
            seriesSequence = book.sequence,
            publishYear = book.publishYear,
            rating = null
        )
    }

    companion object {
        /** Number of books to show in the horizontal preview */
        private const val PREVIEW_BOOK_COUNT = 10

        /** Threshold for showing "View All" button */
        const val VIEW_ALL_THRESHOLD = 6

        /**
         * Convert a role string to a user-friendly display name.
         */
        fun roleToDisplayName(role: String): String = when (role.lowercase()) {
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
    val isLoading: Boolean = false,
    val contributor: ContributorEntity? = null,
    val roleSections: List<RoleSection> = emptyList(),
    val error: String? = null
)

/**
 * A section displaying books for a specific role.
 */
data class RoleSection(
    val role: String,
    val displayName: String,
    val bookCount: Int,
    val previewBooks: List<Book>
) {
    /** Whether to show "View All" button */
    val showViewAll: Boolean
        get() = bookCount > ContributorDetailViewModel.VIEW_ALL_THRESHOLD
}
