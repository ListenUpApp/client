@file:OptIn(FlowPreview::class)

package com.calypsan.listenup.client.presentation.bookedit.delegates

import com.calypsan.listenup.client.data.remote.TagApiContract
import com.calypsan.listenup.client.domain.model.Tag
import com.calypsan.listenup.client.presentation.bookedit.BookEditUiState
import com.calypsan.listenup.client.presentation.bookedit.EditableGenre
import com.calypsan.listenup.client.presentation.bookedit.EditableTag
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

private const val SEARCH_DEBOUNCE_MS = 300L
private const val MIN_QUERY_LENGTH = 2
private const val SEARCH_LIMIT = 10

/**
 * Delegate handling genre and tag editing operations.
 *
 * Responsibilities:
 * - Genre filtering (local, from pre-loaded list)
 * - Tag search and creation
 * - Add/remove genres and tags
 *
 * @property state Shared state flow owned by ViewModel
 * @property tagApi API for tag creation
 * @property scope CoroutineScope for launching operations
 * @property onChangesMade Callback to notify ViewModel of changes
 */
class GenreTagEditDelegate(
    private val state: MutableStateFlow<BookEditUiState>,
    private val tagApi: TagApiContract,
    private val scope: CoroutineScope,
    private val onChangesMade: () -> Unit,
) {
    private val tagQueryFlow = MutableStateFlow("")
    private var tagSearchJob: Job? = null

    init {
        setupTagSearch()
    }

    // ========== Genre Methods ==========

    /**
     * Update the genre search query and filter results locally.
     */
    fun updateGenreSearchQuery(query: String) {
        state.update { it.copy(genreSearchQuery = query) }
        filterGenres(query)
    }

    /**
     * Select a genre to add to the book.
     */
    fun selectGenre(genre: EditableGenre) {
        state.update { current ->
            // Check if already added
            if (current.genres.any { it.id == genre.id }) {
                return@update current.copy(
                    genreSearchQuery = "",
                    genreSearchResults = emptyList(),
                )
            }

            current.copy(
                genres = current.genres + genre,
                genreSearchQuery = "",
                genreSearchResults = emptyList(),
            )
        }
        onChangesMade()
    }

    /**
     * Remove a genre from the book.
     */
    fun removeGenre(genre: EditableGenre) {
        state.update { current ->
            current.copy(genres = current.genres.filter { it.id != genre.id })
        }
        onChangesMade()
    }

    // ========== Tag Methods ==========

    /**
     * Update the tag search query.
     */
    fun updateTagSearchQuery(query: String) {
        state.update { it.copy(tagSearchQuery = query) }
        tagQueryFlow.value = query
    }

    /**
     * Select a tag to add to the book.
     */
    fun selectTag(tag: EditableTag) {
        state.update { current ->
            // Check if already added
            if (current.tags.any { it.id == tag.id }) {
                return@update current.copy(
                    tagSearchQuery = "",
                    tagSearchResults = emptyList(),
                )
            }

            current.copy(
                tags = current.tags + tag,
                tagSearchQuery = "",
                tagSearchResults = emptyList(),
            )
        }
        tagQueryFlow.value = ""
        onChangesMade()
    }

    /**
     * Create a new tag inline and add it to the book.
     */
    fun createAndAddTag(name: String) {
        if (name.isBlank()) return

        val trimmedName = name.trim()

        // Check if tag with same name already exists in allTags
        val existingTag =
            state.value.allTags.find {
                it.name.equals(trimmedName, ignoreCase = true)
            }

        if (existingTag != null) {
            // Use existing tag
            selectTag(existingTag)
            return
        }

        // Create new tag via API
        scope.launch {
            state.update { it.copy(tagCreating = true) }

            try {
                val newTag = tagApi.createTag(trimmedName)
                val editableTag = newTag.toEditable()

                state.update { current ->
                    current.copy(
                        tags = current.tags + editableTag,
                        allTags = current.allTags + editableTag, // Add to available tags
                        tagSearchQuery = "",
                        tagSearchResults = emptyList(),
                        tagCreating = false,
                    )
                }
                tagQueryFlow.value = ""
                onChangesMade()

                logger.info { "Created new tag: ${newTag.name}" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to create tag: $trimmedName" }
                state.update {
                    it.copy(
                        tagCreating = false,
                        error = "Failed to create tag: ${e.message}",
                    )
                }
            }
        }
    }

    /**
     * Remove a tag from the book.
     */
    fun removeTag(tag: EditableTag) {
        state.update { current ->
            current.copy(tags = current.tags.filter { it.id != tag.id })
        }
        onChangesMade()
    }

    // ========== Private Methods ==========

    private fun filterGenres(query: String) {
        if (query.isBlank()) {
            state.update { it.copy(genreSearchResults = emptyList()) }
            return
        }

        val lowerQuery = query.lowercase()
        val currentGenreIds =
            state.value.genres
                .map { it.id }
                .toSet()

        val filtered =
            state.value.allGenres
                .filter { genre ->
                    genre.id !in currentGenreIds &&
                        (
                            genre.name.lowercase().contains(lowerQuery) ||
                                genre.path.lowercase().contains(lowerQuery)
                        )
                }.take(SEARCH_LIMIT)

        state.update { it.copy(genreSearchResults = filtered) }
    }

    private fun setupTagSearch() {
        tagQueryFlow
            .debounce(SEARCH_DEBOUNCE_MS)
            .distinctUntilChanged()
            .filter { it.length >= MIN_QUERY_LENGTH || it.isEmpty() }
            .onEach { query ->
                if (query.isBlank()) {
                    state.update {
                        it.copy(
                            tagSearchResults = emptyList(),
                            tagSearchLoading = false,
                        )
                    }
                } else {
                    performTagSearch(query)
                }
            }.launchIn(scope)
    }

    private fun performTagSearch(query: String) {
        tagSearchJob?.cancel()
        tagSearchJob =
            scope.launch {
                state.update { it.copy(tagSearchLoading = true) }

                // Filter from allTags (already loaded)
                val lowerQuery = query.lowercase()
                val currentTagIds =
                    state.value.tags
                        .map { it.id }
                        .toSet()

                val filtered =
                    state.value.allTags
                        .filter { tag ->
                            tag.id !in currentTagIds &&
                                tag.name.lowercase().contains(lowerQuery)
                        }.take(SEARCH_LIMIT)

                state.update {
                    it.copy(
                        tagSearchResults = filtered,
                        tagSearchLoading = false,
                    )
                }
            }
    }

    private fun Tag.toEditable() = EditableTag(id = id, name = name, color = color)
}
