@file:Suppress("MagicNumber")

package com.calypsan.listenup.client.presentation.bookedit

import com.calypsan.listenup.client.domain.model.ContributorSearchResult
import com.calypsan.listenup.client.domain.model.SeriesSearchResult
import com.calypsan.listenup.client.domain.model.ContributorRole as DomainContributorRole
import com.calypsan.listenup.client.domain.model.EditableContributor as DomainEditableContributor
import com.calypsan.listenup.client.domain.model.EditableGenre as DomainEditableGenre
import com.calypsan.listenup.client.domain.model.EditableSeries as DomainEditableSeries
import com.calypsan.listenup.client.domain.model.EditableTag as DomainEditableTag

// Type aliases to domain types - presentation layer uses domain models directly
typealias EditableContributor = DomainEditableContributor
typealias EditableSeries = DomainEditableSeries
typealias EditableGenre = DomainEditableGenre
typealias EditableTag = DomainEditableTag
typealias ContributorRole = DomainContributorRole

/**
 * Extension property for ContributorRole display name.
 * Presentation-layer concern for UI display.
 */
val ContributorRole.displayName: String
    get() =
        when (this) {
            ContributorRole.AUTHOR -> "Author"
            ContributorRole.NARRATOR -> "Narrator"
            ContributorRole.EDITOR -> "Editor"
            ContributorRole.TRANSLATOR -> "Translator"
            ContributorRole.FOREWORD -> "Foreword"
            ContributorRole.INTRODUCTION -> "Introduction"
            ContributorRole.AFTERWORD -> "Afterword"
            ContributorRole.PRODUCER -> "Producer"
            ContributorRole.ADAPTER -> "Adapter"
            ContributorRole.ILLUSTRATOR -> "Illustrator"
        }

/**
 * Extension property for EditableGenre parent path display.
 * Returns the parent path for display context.
 * "/fiction/fantasy/epic-fantasy" -> "Fiction > Fantasy"
 */
val EditableGenre.parentPath: String?
    get() {
        val segments = path.trim('/').split('/')
        if (segments.size <= 1) return null
        return segments
            .dropLast(1)
            .joinToString(" > ") { it.replaceFirstChar { c -> c.uppercase() } }
    }

/**
 * Extension function for EditableTag display name.
 * Human-readable display name derived from slug.
 * "found-family" -> "Found Family"
 */
fun EditableTag.displayName(): String =
    slug
        .split("-")
        .joinToString(" ") { word ->
            word.replaceFirstChar { it.titlecase() }
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
