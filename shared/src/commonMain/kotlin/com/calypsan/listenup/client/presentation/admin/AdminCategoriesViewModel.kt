package com.calypsan.listenup.client.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.domain.model.Genre
import com.calypsan.listenup.client.domain.repository.GenreRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * ViewModel for the admin categories (genres) tree screen.
 *
 * Manages the hierarchical display of system genres with expand/collapse state.
 * Genres form a tree structure based on their materialized path (e.g., /fiction/fantasy).
 */
class AdminCategoriesViewModel(
    private val genreRepository: GenreRepository,
) : ViewModel() {
    val state: StateFlow<AdminCategoriesUiState>
        field = MutableStateFlow(AdminCategoriesUiState())

    init {
        observeGenres()
    }

    /**
     * Observe genres from local database.
     * Updates are pushed via SSE events and processed by SSEEventProcessor.
     */
    private fun observeGenres() {
        viewModelScope.launch {
            state.value = state.value.copy(isLoading = true)

            genreRepository.observeAll().collect { genres ->
                logger.debug { "Genres updated: ${genres.size}" }
                val tree = buildGenreTree(genres)
                state.value =
                    state.value.copy(
                        isLoading = false,
                        genres = genres,
                        tree = tree,
                        totalBookCount = genres.sumOf { it.bookCount },
                    )
            }
        }
    }

    /**
     * Toggle expand/collapse state for a genre node.
     */
    fun toggleExpanded(genreId: String) {
        val currentExpanded = state.value.expandedIds.toMutableSet()
        if (currentExpanded.contains(genreId)) {
            currentExpanded.remove(genreId)
        } else {
            currentExpanded.add(genreId)
        }
        state.value = state.value.copy(expandedIds = currentExpanded)
    }

    /**
     * Expand all nodes in the tree.
     */
    fun expandAll() {
        val allIds =
            state.value.genres
                .map { it.id }
                .toSet()
        state.value = state.value.copy(expandedIds = allIds)
    }

    /**
     * Collapse all nodes in the tree.
     */
    fun collapseAll() {
        state.value = state.value.copy(expandedIds = emptySet())
    }

    /**
     * Create a new genre, optionally under a parent.
     */
    fun createGenre(
        name: String,
        parentId: String?,
    ) {
        viewModelScope.launch {
            state.value = state.value.copy(isSaving = true, error = null)
            try {
                genreRepository.createGenre(name, parentId)
                // Auto-expand parent so user sees the new child
                if (parentId != null) {
                    val expanded = state.value.expandedIds.toMutableSet()
                    expanded.add(parentId)
                    state.value = state.value.copy(expandedIds = expanded)
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to create genre" }
                state.value = state.value.copy(error = e.message ?: "Failed to create genre")
            } finally {
                state.value = state.value.copy(isSaving = false)
            }
        }
    }

    /**
     * Rename an existing genre.
     */
    fun renameGenre(
        id: String,
        name: String,
    ) {
        viewModelScope.launch {
            state.value = state.value.copy(isSaving = true, error = null)
            try {
                genreRepository.updateGenre(id, name)
            } catch (e: Exception) {
                logger.error(e) { "Failed to rename genre" }
                state.value = state.value.copy(error = e.message ?: "Failed to rename genre")
            } finally {
                state.value = state.value.copy(isSaving = false)
            }
        }
    }

    /**
     * Delete a genre.
     */
    fun deleteGenre(id: String) {
        viewModelScope.launch {
            state.value = state.value.copy(isSaving = true, error = null)
            try {
                genreRepository.deleteGenre(id)
            } catch (e: Exception) {
                logger.error(e) { "Failed to delete genre" }
                state.value = state.value.copy(error = e.message ?: "Failed to delete genre")
            } finally {
                state.value = state.value.copy(isSaving = false)
            }
        }
    }

    /**
     * Move a genre to a new parent.
     */
    fun moveGenre(
        id: String,
        newParentId: String?,
    ) {
        viewModelScope.launch {
            state.value = state.value.copy(isSaving = true, error = null)
            try {
                genreRepository.moveGenre(id, newParentId)
                // Auto-expand new parent so user sees the moved genre
                if (newParentId != null) {
                    val expanded = state.value.expandedIds.toMutableSet()
                    expanded.add(newParentId)
                    state.value = state.value.copy(expandedIds = expanded)
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to move genre" }
                state.value = state.value.copy(error = e.message ?: "Failed to move genre")
            } finally {
                state.value = state.value.copy(isSaving = false)
            }
        }
    }

    /**
     * Clear the error state.
     */
    fun clearError() {
        state.value = state.value.copy(error = null)
    }

    /**
     * Build a tree structure from flat genre list.
     * Uses materialized path to determine hierarchy.
     */
    private fun buildGenreTree(genres: List<Genre>): List<GenreTreeNode> {
        // Group genres by parent path
        val byPath = genres.associateBy { it.path }
        val childrenByParentPath = mutableMapOf<String, MutableList<Genre>>()

        genres.forEach { genre ->
            val parentPath = genre.path.substringBeforeLast('/', "")
            if (parentPath.isNotEmpty()) {
                childrenByParentPath.getOrPut(parentPath) { mutableListOf() }.add(genre)
            }
        }

        // Find root genres (those with single-segment paths like "/fiction")
        val roots =
            genres
                .filter { genre ->
                    val segments = genre.path.trim('/').split('/')
                    segments.size == 1
                }.sortedBy { it.name }

        // Build tree recursively
        fun buildNode(
            genre: Genre,
            depth: Int,
        ): GenreTreeNode {
            val children =
                childrenByParentPath[genre.path]
                    ?.sortedBy { it.name }
                    ?.map { buildNode(it, depth + 1) }
                    ?: emptyList()

            return GenreTreeNode(
                genre = genre,
                children = children,
                depth = depth,
            )
        }

        return roots.map { buildNode(it, 0) }
    }
}

/**
 * A node in the genre tree structure.
 */
data class GenreTreeNode(
    val genre: Genre,
    val children: List<GenreTreeNode>,
    val depth: Int,
)

/**
 * UI state for the admin categories screen.
 */
data class AdminCategoriesUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val genres: List<Genre> = emptyList(),
    val tree: List<GenreTreeNode> = emptyList(),
    val expandedIds: Set<String> = emptySet(),
    val totalBookCount: Int = 0,
    val error: String? = null,
)
