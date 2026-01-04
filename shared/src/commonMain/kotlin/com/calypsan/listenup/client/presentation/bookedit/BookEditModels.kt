@file:Suppress("MagicNumber")

package com.calypsan.listenup.client.presentation.bookedit

import com.calypsan.listenup.client.data.remote.ContributorSearchResult
import com.calypsan.listenup.client.data.remote.SeriesSearchResult

/**
 * Contributor with roles for editing.
 */
data class EditableContributor(
    val id: String? = null, // null for newly added contributors
    val name: String,
    val roles: Set<ContributorRole>,
)

/**
 * Role types for contributors.
 * Matches server-side roles in domain/contributor.go
 */
enum class ContributorRole(
    val displayName: String,
    val apiValue: String,
) {
    AUTHOR("Author", "author"),
    NARRATOR("Narrator", "narrator"),
    EDITOR("Editor", "editor"),
    TRANSLATOR("Translator", "translator"),
    FOREWORD("Foreword", "foreword"),
    INTRODUCTION("Introduction", "introduction"),
    AFTERWORD("Afterword", "afterword"),
    PRODUCER("Producer", "producer"),
    ADAPTER("Adapter", "adapter"),
    ILLUSTRATOR("Illustrator", "illustrator"),
    ;

    companion object {
        fun fromApiValue(value: String): ContributorRole? =
            entries.find { it.apiValue.equals(value, ignoreCase = true) }
    }
}

/**
 * Series membership for editing.
 */
data class EditableSeries(
    val id: String? = null, // null for newly added series
    val name: String,
    val sequence: String? = null, // e.g., "1", "1.5"
)

/**
 * Genre for editing.
 */
data class EditableGenre(
    val id: String,
    val name: String,
    val path: String,
) {
    /**
     * Returns the parent path for display context.
     * "/fiction/fantasy/epic-fantasy" -> "Fiction > Fantasy"
     */
    val parentPath: String?
        get() {
            val segments = path.trim('/').split('/')
            if (segments.size <= 1) return null
            return segments
                .dropLast(1)
                .joinToString(" > ") { it.replaceFirstChar { c -> c.uppercase() } }
        }
}

/**
 * Tag for editing.
 *
 * Tags are global community descriptors identified by slug.
 */
data class EditableTag(
    val id: String,
    val slug: String,
) {
    /**
     * Human-readable display name derived from slug.
     * "found-family" -> "Found Family"
     */
    fun displayName(): String =
        slug
            .split("-")
            .joinToString(" ") { word ->
                word.replaceFirstChar { it.titlecase() }
            }
}

/**
 * UI state for book editing screen.
 */
data class BookEditUiState(
    // Loading states
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isUploadingCover: Boolean = false,
    val error: String? = null,
    // Book identity (for immersive header)
    val coverPath: String? = null,
    // Book metadata fields
    val bookId: String = "",
    val title: String = "",
    val subtitle: String = "",
    val description: String = "",
    val publishYear: String = "",
    val publisher: String = "",
    val language: String? = null, // ISO 639-1 code
    // Additional metadata (less commonly edited)
    val isbn: String = "",
    val asin: String = "",
    val abridged: Boolean = false,
    // Library metadata
    val addedAt: Long? = null, // Epoch milliseconds when book was added to library
    // Contributors
    val contributors: List<EditableContributor> = emptyList(),
    // Series (multi-series support)
    val series: List<EditableSeries> = emptyList(),
    val seriesSearchQuery: String = "",
    val seriesSearchResults: List<SeriesSearchResult> = emptyList(),
    val seriesSearchLoading: Boolean = false,
    val seriesOfflineResult: Boolean = false,
    // Per-role search state (replaces single search)
    val roleSearchQueries: Map<ContributorRole, String> = emptyMap(),
    val roleSearchResults: Map<ContributorRole, List<ContributorSearchResult>> = emptyMap(),
    val roleSearchLoading: Map<ContributorRole, Boolean> = emptyMap(),
    val roleOfflineResults: Map<ContributorRole, Boolean> = emptyMap(),
    // Visible role sections (prepopulated from existing contributors + user-added)
    val visibleRoles: Set<ContributorRole> = emptySet(),
    // Genres (system-controlled, select from existing)
    val genres: List<EditableGenre> = emptyList(),
    val allGenres: List<EditableGenre> = emptyList(), // Cached list from server
    val genreSearchQuery: String = "",
    val genreSearchResults: List<EditableGenre> = emptyList(), // Filtered locally
    // Tags (global community descriptors)
    val tags: List<EditableTag> = emptyList(),
    val allTags: List<EditableTag> = emptyList(), // All available tags
    val tagSearchQuery: String = "",
    val tagSearchResults: List<EditableTag> = emptyList(),
    val tagSearchLoading: Boolean = false,
    val tagCreating: Boolean = false, // Creating a new tag
    // Track if changes have been made
    val hasChanges: Boolean = false,
    // Pending cover upload (stored until Save Changes)
    val pendingCoverData: ByteArray? = null,
    val pendingCoverFilename: String? = null,
    // Staging cover path for preview (separate from main cover)
    val stagingCoverPath: String? = null,
) {
    /**
     * Returns the cover path to display - staging if available, otherwise original.
     */
    val displayCoverPath: String?
        get() = stagingCoverPath ?: coverPath

    /**
     * Get contributors for a specific role.
     */
    fun contributorsForRole(role: ContributorRole): List<EditableContributor> = contributors.filter { role in it.roles }

    /**
     * Authors from contributor list.
     */
    val authors: List<EditableContributor>
        get() = contributorsForRole(ContributorRole.AUTHOR)

    /**
     * Narrators from contributor list.
     */
    val narrators: List<EditableContributor>
        get() = contributorsForRole(ContributorRole.NARRATOR)

    /**
     * Roles that are not yet visible (available to add).
     */
    val availableRolesToAdd: List<ContributorRole>
        get() = ContributorRole.entries.filter { it !in visibleRoles }
}

/**
 * Events from the book edit UI.
 */
sealed interface BookEditUiEvent {
    // Metadata changes
    data class TitleChanged(
        val title: String,
    ) : BookEditUiEvent

    data class SubtitleChanged(
        val subtitle: String,
    ) : BookEditUiEvent

    data class DescriptionChanged(
        val description: String,
    ) : BookEditUiEvent

    data class PublishYearChanged(
        val year: String,
    ) : BookEditUiEvent

    data class PublisherChanged(
        val publisher: String,
    ) : BookEditUiEvent

    data class LanguageChanged(
        val code: String?,
    ) : BookEditUiEvent

    // Additional metadata
    data class IsbnChanged(
        val isbn: String,
    ) : BookEditUiEvent

    data class AsinChanged(
        val asin: String,
    ) : BookEditUiEvent

    data class AbridgedChanged(
        val abridged: Boolean,
    ) : BookEditUiEvent

    // Library metadata
    data class AddedAtChanged(
        val epochMillis: Long?,
    ) : BookEditUiEvent

    // Series management
    data class SeriesSearchQueryChanged(
        val query: String,
    ) : BookEditUiEvent

    data class SeriesSelected(
        val result: SeriesSearchResult,
    ) : BookEditUiEvent

    data class SeriesEntered(
        val name: String,
    ) : BookEditUiEvent

    data class SeriesSequenceChanged(
        val series: EditableSeries,
        val sequence: String,
    ) : BookEditUiEvent

    data class RemoveSeries(
        val series: EditableSeries,
    ) : BookEditUiEvent

    data object ClearSeriesSearch : BookEditUiEvent

    // Per-role contributor management
    data class RoleSearchQueryChanged(
        val role: ContributorRole,
        val query: String,
    ) : BookEditUiEvent

    data class RoleContributorSelected(
        val role: ContributorRole,
        val result: ContributorSearchResult,
    ) : BookEditUiEvent

    data class RoleContributorEntered(
        val role: ContributorRole,
        val name: String,
    ) : BookEditUiEvent

    data class ClearRoleSearch(
        val role: ContributorRole,
    ) : BookEditUiEvent

    data class AddRoleSection(
        val role: ContributorRole,
    ) : BookEditUiEvent

    data class RemoveContributor(
        val contributor: EditableContributor,
        val role: ContributorRole,
    ) : BookEditUiEvent

    data class RemoveRoleSection(
        val role: ContributorRole,
    ) : BookEditUiEvent

    // Genre management (select from existing only)
    data class GenreSearchQueryChanged(
        val query: String,
    ) : BookEditUiEvent

    data class GenreSelected(
        val genre: EditableGenre,
    ) : BookEditUiEvent

    data class RemoveGenre(
        val genre: EditableGenre,
    ) : BookEditUiEvent

    // Tag management (select existing or create new)
    data class TagSearchQueryChanged(
        val query: String,
    ) : BookEditUiEvent

    data class TagSelected(
        val tag: EditableTag,
    ) : BookEditUiEvent

    data class TagEntered(
        val name: String,
    ) : BookEditUiEvent // Create new tag inline

    data class RemoveTag(
        val tag: EditableTag,
    ) : BookEditUiEvent

    // Cover upload
    data class UploadCover(
        val imageData: ByteArray,
        val filename: String,
    ) : BookEditUiEvent

    // Actions
    data object Save : BookEditUiEvent

    data object Cancel : BookEditUiEvent

    data object DismissError : BookEditUiEvent
}

/**
 * Navigation actions from book edit screen.
 */
sealed interface BookEditNavAction {
    data object NavigateBack : BookEditNavAction

    data class ShowSaveSuccess(
        val message: String,
    ) : BookEditNavAction
}
