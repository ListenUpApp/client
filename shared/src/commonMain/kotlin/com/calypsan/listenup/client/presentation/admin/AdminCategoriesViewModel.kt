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
                state.value = state.value.copy(
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
        val allIds = state.value.genres.map { it.id }.toSet()
        state.value = state.value.copy(expandedIds = allIds)
    }

    /**
     * Collapse all nodes in the tree.
     */
    fun collapseAll() {
        state.value = state.value.copy(expandedIds = emptySet())
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
        val roots = genres.filter { genre ->
            val segments = genre.path.trim('/').split('/')
            segments.size == 1
        }.sortedBy { it.name }

        // Build tree recursively
        fun buildNode(genre: Genre, depth: Int): GenreTreeNode {
            val children = childrenByParentPath[genre.path]
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
    val genres: List<Genre> = emptyList(),
    val tree: List<GenreTreeNode> = emptyList(),
    val expandedIds: Set<String> = emptySet(),
    val totalBookCount: Int = 0,
    val error: String? = null,
)
