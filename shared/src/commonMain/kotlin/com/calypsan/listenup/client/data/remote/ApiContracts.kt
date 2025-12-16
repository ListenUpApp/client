package com.calypsan.listenup.client.data.remote

import com.calypsan.listenup.client.core.RefreshToken
import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.remote.model.ContributorResponse
import com.calypsan.listenup.client.data.remote.model.PlaybackProgressResponse
import com.calypsan.listenup.client.data.remote.model.SeriesResponse
import com.calypsan.listenup.client.data.remote.model.SyncBooksResponse
import com.calypsan.listenup.client.data.remote.model.SyncContributorsResponse
import com.calypsan.listenup.client.data.remote.model.SyncManifestResponse
import com.calypsan.listenup.client.data.remote.model.SyncSeriesResponse
import com.calypsan.listenup.client.domain.model.Instance
import com.calypsan.listenup.client.domain.model.Tag

/**
 * Contract interface for search API operations.
 *
 * Extracted to enable mocking in tests. Production implementation
 * is [SearchApi], test implementation can be a mock or fake.
 */
interface SearchApiContract {
    /**
     * Search across books, contributors, and series.
     *
     * @param query Search query string
     * @param types Comma-separated types to search (book,contributor,series)
     * @param genres Comma-separated genre slugs to filter by
     * @param genrePath Genre path prefix for hierarchical filtering
     * @param minDuration Minimum duration in hours
     * @param maxDuration Maximum duration in hours
     * @param limit Max results to return
     * @param offset Pagination offset
     * @return SearchResponse with hits and facets
     * @throws SearchException on search failure
     */
    suspend fun search(
        query: String,
        types: String? = null,
        genres: String? = null,
        genrePath: String? = null,
        minDuration: Float? = null,
        maxDuration: Float? = null,
        limit: Int = 20,
        offset: Int = 0,
    ): SearchResponse
}

/**
 * Contract interface for sync API operations.
 *
 * Extracted to enable mocking in tests. Production implementation
 * is [SyncApi], test implementation can be a mock or fake.
 */
interface SyncApiContract {
    /**
     * Fetch sync manifest with library overview.
     */
    suspend fun getManifest(): Result<SyncManifestResponse>

    /**
     * Fetch paginated books for syncing.
     *
     * @param limit Number of books per page
     * @param cursor Pagination cursor (null for first page)
     * @param updatedAfter ISO 8601 timestamp for delta sync
     */
    suspend fun getBooks(
        limit: Int = 100,
        cursor: String? = null,
        updatedAfter: String? = null,
    ): Result<SyncBooksResponse>

    /**
     * Fetch all books across all pages.
     */
    suspend fun getAllBooks(
        limit: Int = 100,
        updatedAfter: String? = null,
    ): Result<SyncBooksResponse>

    /**
     * Fetch paginated series for syncing.
     */
    suspend fun getSeries(
        limit: Int = 100,
        cursor: String? = null,
        updatedAfter: String? = null,
    ): Result<SyncSeriesResponse>

    /**
     * Fetch all series across all pages.
     */
    suspend fun getAllSeries(
        limit: Int = 100,
        updatedAfter: String? = null,
    ): Result<List<SeriesResponse>>

    /**
     * Fetch paginated contributors for syncing.
     */
    suspend fun getContributors(
        limit: Int = 100,
        cursor: String? = null,
        updatedAfter: String? = null,
    ): Result<SyncContributorsResponse>

    /**
     * Fetch all contributors across all pages.
     */
    suspend fun getAllContributors(
        limit: Int = 100,
        updatedAfter: String? = null,
    ): Result<List<ContributorResponse>>

    /**
     * Submit listening events to the server.
     */
    suspend fun submitListeningEvents(events: List<ListeningEventRequest>): Result<ListeningEventsResponse>
}

/**
 * Contract interface for authentication API operations.
 *
 * Extracted to enable mocking in tests. Production implementation
 * is [AuthApi], test implementation can be a mock or fake.
 */
interface AuthApiContract {
    /**
     * Create the root/admin user during initial server setup.
     */
    suspend fun setup(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
    ): AuthResponse

    /**
     * Login with email and password.
     */
    suspend fun login(
        email: String,
        password: String,
    ): AuthResponse

    /**
     * Refresh access token using refresh token.
     */
    suspend fun refresh(refreshToken: RefreshToken): AuthResponse

    /**
     * Logout and invalidate current session.
     */
    suspend fun logout(sessionId: String)
}

/**
 * Contract interface for tag API operations.
 *
 * Extracted to enable mocking in tests. Production implementation
 * is [TagApi], test implementation can be a mock or fake.
 */
interface TagApiContract {
    /**
     * Get all tags for the current user.
     */
    suspend fun getUserTags(): List<Tag>

    /**
     * Get tags for a specific book.
     */
    suspend fun getBookTags(bookId: String): List<Tag>

    /**
     * Add a tag to a book.
     */
    suspend fun addTagToBook(
        bookId: String,
        tagId: String,
    )

    /**
     * Remove a tag from a book.
     */
    suspend fun removeTagFromBook(
        bookId: String,
        tagId: String,
    )

    /**
     * Create a new tag.
     */
    suspend fun createTag(
        name: String,
        color: String? = null,
    ): Tag

    /**
     * Delete a tag.
     */
    suspend fun deleteTag(tagId: String)
}

/**
 * Contract interface for image API operations.
 *
 * Extracted to enable mocking in tests. Production implementation
 * is [ImageApi], test implementation can be a mock or fake.
 */
interface ImageApiContract {
    /**
     * Download cover image for a book.
     *
     * @param bookId Unique identifier for the book
     * @return Result containing image bytes or error
     */
    suspend fun downloadCover(bookId: BookId): Result<ByteArray>

    /**
     * Download profile image for a contributor.
     *
     * @param contributorId Unique identifier for the contributor
     * @return Result containing image bytes or error
     */
    suspend fun downloadContributorImage(contributorId: String): Result<ByteArray>

    /**
     * Upload cover image for a book.
     *
     * @param bookId Unique identifier for the book
     * @param imageData Raw image bytes (JPEG, PNG, WebP, or GIF)
     * @param filename Original filename for the image
     * @return Result containing the image URL or error
     */
    suspend fun uploadBookCover(
        bookId: String,
        imageData: ByteArray,
        filename: String,
    ): Result<ImageUploadResponse>

    /**
     * Upload profile image for a contributor.
     *
     * @param contributorId Unique identifier for the contributor
     * @param imageData Raw image bytes (JPEG, PNG, WebP, or GIF)
     * @param filename Original filename for the image
     * @return Result containing the image URL or error
     */
    suspend fun uploadContributorImage(
        contributorId: String,
        imageData: ByteArray,
        filename: String,
    ): Result<ImageUploadResponse>

    /**
     * Download cover image for a series.
     *
     * @param seriesId Unique identifier for the series
     * @return Result containing image bytes or error
     */
    suspend fun downloadSeriesCover(seriesId: String): Result<ByteArray>

    /**
     * Upload cover image for a series.
     *
     * @param seriesId Unique identifier for the series
     * @param imageData Raw image bytes (JPEG, PNG, WebP, or GIF)
     * @param filename Original filename for the image
     * @return Result containing the image URL or error
     */
    suspend fun uploadSeriesCover(
        seriesId: String,
        imageData: ByteArray,
        filename: String,
    ): Result<ImageUploadResponse>

    /**
     * Delete cover image for a series.
     *
     * @param seriesId Unique identifier for the series
     * @return Result with Unit on success or error
     */
    suspend fun deleteSeriesCover(seriesId: String): Result<Unit>
}

/**
 * Response from image upload operations.
 */
data class ImageUploadResponse(
    val imageUrl: String,
)

/**
 * Contract interface for ListenUp API operations.
 *
 * Extracted to enable mocking in tests. Production implementation
 * is [ListenUpApi], test implementation can be a mock or fake.
 */
interface ListenUpApiContract {
    /**
     * Fetch the server instance information.
     *
     * This is a public endpoint - no authentication required.
     *
     * @return Result containing the Instance on success, or an error on failure
     */
    suspend fun getInstance(): Result<Instance>

    /**
     * Fetch books the user is currently listening to.
     *
     * This is an authenticated endpoint - requires valid access token.
     * Returns playback progress for books sorted by last played time.
     *
     * @param limit Maximum number of books to return (default 10)
     * @return Result containing list of PlaybackProgressResponse on success
     */
    suspend fun getContinueListening(limit: Int = 10): Result<List<PlaybackProgressResponse>>

    /**
     * Search contributors for autocomplete during book editing.
     *
     * Uses server-side Bleve search for O(log n) performance with:
     * - Prefix matching ("bran" → "Brandon Sanderson")
     * - Word matching ("sanderson" in "Brandon Sanderson")
     * - Fuzzy matching for typo tolerance
     *
     * @param query Search query (min 2 characters recommended)
     * @param limit Maximum results to return (default 10, max 50)
     * @return Result containing list of matching contributors
     */
    suspend fun searchContributors(
        query: String,
        limit: Int = 10,
    ): Result<List<ContributorSearchResult>>

    /**
     * Update book metadata (PATCH semantics).
     *
     * Only fields present in the request are updated:
     * - null field = don't change
     * - empty string = clear the field
     *
     * @param bookId Book to update
     * @param update Fields to update
     * @return Result containing the updated book
     */
    suspend fun updateBook(
        bookId: String,
        update: BookUpdateRequest,
    ): Result<BookEditResponse>

    /**
     * Set book contributors (replaces all existing contributors).
     *
     * Contributors are matched by name:
     * - Existing contributor with same name → linked
     * - New name → contributor created automatically
     *
     * Orphaned contributors (no books) are automatically cleaned up.
     *
     * @param bookId Book to update
     * @param contributors New list of contributors with roles
     * @return Result containing the updated book
     */
    suspend fun setBookContributors(
        bookId: String,
        contributors: List<ContributorInput>,
    ): Result<BookEditResponse>

    /**
     * Search series for autocomplete during book editing.
     *
     * Uses server-side Bleve search for O(log n) performance with:
     * - Prefix matching ("mist" → "Mistborn")
     * - Word matching
     * - Fuzzy matching for typo tolerance
     *
     * @param query Search query (min 2 characters recommended)
     * @param limit Maximum results to return (default 10, max 50)
     * @return Result containing list of matching series
     */
    suspend fun searchSeries(
        query: String,
        limit: Int = 10,
    ): Result<List<SeriesSearchResult>>

    /**
     * Set book series (replaces all existing series relationships).
     *
     * Series are matched by name:
     * - Existing series with same name → linked
     * - New name → series created automatically
     *
     * Orphaned series (no books) are automatically cleaned up.
     *
     * @param bookId Book to update
     * @param series New list of series with sequence numbers
     * @return Result containing the updated book
     */
    suspend fun setBookSeries(
        bookId: String,
        series: List<SeriesInput>,
    ): Result<BookEditResponse>

    /**
     * Merge a source contributor into a target contributor.
     *
     * The merge operation:
     * - Re-links all books from source to target, preserving original attribution via creditedAs
     * - Adds source's name to target's aliases field
     * - Soft-deletes the source contributor
     *
     * Use this when a user identifies that two contributors are actually the same person
     * (e.g., "Richard Bachman" is a pen name for "Stephen King").
     *
     * @param targetContributorId The contributor to merge into (absorbs the source)
     * @param sourceContributorId The contributor to merge from (will be soft-deleted)
     * @return Result containing the updated target contributor
     */
    suspend fun mergeContributor(
        targetContributorId: String,
        sourceContributorId: String,
    ): Result<MergeContributorResponse>

    /**
     * Unmerge an alias from a contributor, creating a new separate contributor.
     *
     * POST /api/v1/contributors/{contributorId}/unmerge
     *
     * The unmerge operation:
     * - Creates a new contributor with the alias name
     * - Re-links books that were credited to that alias to the new contributor
     * - Removes the alias from the source contributor
     *
     * Use this when a user decides an alias should be a separate contributor after all.
     *
     * @param contributorId The contributor to unmerge from
     * @param aliasName The alias to split off as a new contributor
     * @return Result containing the newly created contributor
     */
    suspend fun unmergeContributor(
        contributorId: String,
        aliasName: String,
    ): Result<UnmergeContributorResponse>

    /**
     * Update a contributor's metadata.
     *
     * PUT /api/v1/contributors/{contributorId}
     *
     * Updates name, biography, website, birth_date, death_date, and aliases.
     *
     * @param contributorId The contributor to update
     * @param request The update request containing new field values
     * @return Result containing the updated contributor
     */
    suspend fun updateContributor(
        contributorId: String,
        request: UpdateContributorRequest,
    ): Result<UpdateContributorResponse>

    /**
     * Update series metadata (PATCH semantics).
     *
     * Only fields present in the request are updated:
     * - null field = don't change
     * - empty string = clear the field
     *
     * @param seriesId Series to update
     * @param request Fields to update
     * @return Result containing the updated series
     */
    suspend fun updateSeries(
        seriesId: String,
        request: SeriesUpdateRequest,
    ): Result<SeriesEditResponse>
}

/**
 * Contributor search result for autocomplete.
 *
 * Lightweight representation returned by contributor search endpoint.
 * Used when editing book contributors to find existing contributors to link.
 */
data class ContributorSearchResult(
    val id: String,
    val name: String,
    val bookCount: Int,
)

/**
 * Series search result for autocomplete.
 *
 * Lightweight representation returned by series search endpoint.
 * Used when editing book series to find existing series to link.
 */
data class SeriesSearchResult(
    val id: String,
    val name: String,
    val bookCount: Int,
)

/**
 * Request for updating book metadata (PATCH semantics).
 *
 * Only non-null fields are sent to the server:
 * - null = don't change this field
 * - empty string = clear this field
 */
data class BookUpdateRequest(
    val title: String? = null,
    val subtitle: String? = null,
    val description: String? = null,
    val publisher: String? = null,
    val publishYear: String? = null,
    val language: String? = null,
    val isbn: String? = null,
    val asin: String? = null,
    val abridged: Boolean? = null,
    val seriesId: String? = null,
    val sequence: String? = null,
)

/**
 * Contributor with roles for setting book contributors.
 */
data class ContributorInput(
    val name: String,
    val roles: List<String>,
)

/**
 * Series with sequence for setting book series.
 */
data class SeriesInput(
    val name: String,
    val sequence: String?,
)

/**
 * Book response for edit operations.
 *
 * Contains fields needed after editing. Separate from SyncModels.BookResponse
 * which has additional sync-specific fields (chapters, audio files, etc.).
 */
data class BookEditResponse(
    val id: String,
    val title: String,
    val subtitle: String?,
    val description: String?,
    val publisher: String?,
    val publishYear: String?,
    val language: String?,
    val isbn: String?,
    val asin: String?,
    val abridged: Boolean,
    val seriesId: String?,
    val seriesName: String?,
    val sequence: String?,
    val updatedAt: String,
)

/**
 * Response from contributor merge operation.
 *
 * Contains the updated target contributor with all merged aliases.
 */
data class MergeContributorResponse(
    val id: String,
    val name: String,
    val sortName: String?,
    val biography: String?,
    val imageUrl: String?,
    val asin: String?,
    val aliases: List<String>,
    val updatedAt: String,
)

/**
 * Response from contributor unmerge operation.
 *
 * Contains the newly created contributor that was split off from the alias.
 */
data class UnmergeContributorResponse(
    val id: String,
    val name: String,
    val sortName: String?,
    val biography: String?,
    val imageUrl: String?,
    val asin: String?,
    val aliases: List<String>,
    val updatedAt: String,
)

/**
 * Request to update a contributor's metadata.
 */
data class UpdateContributorRequest(
    val name: String,
    val biography: String?,
    val website: String?,
    val birthDate: String?,
    val deathDate: String?,
    val aliases: List<String>,
)

/**
 * Response from updating a contributor.
 */
data class UpdateContributorResponse(
    val id: String,
    val name: String,
    val biography: String?,
    val imageUrl: String?,
    val website: String?,
    val birthDate: String?,
    val deathDate: String?,
    val aliases: List<String>,
    val updatedAt: String,
)

/**
 * Request to update a series' metadata (PATCH semantics).
 */
data class SeriesUpdateRequest(
    val name: String? = null,
    val description: String? = null,
)

/**
 * Response from series edit operations.
 */
data class SeriesEditResponse(
    val id: String,
    val name: String,
    val description: String? = null,
    val updatedAt: String,
)
