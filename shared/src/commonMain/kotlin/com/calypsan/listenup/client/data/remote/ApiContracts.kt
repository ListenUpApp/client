package com.calypsan.listenup.client.data.remote

import com.calypsan.listenup.client.core.RefreshToken
import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.remote.model.ContinueListeningItemResponse
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

    /**
     * Get playback progress for a specific book.
     *
     * Used for cross-device sync: checks if another device has newer progress.
     * Returns null if no progress exists for this book.
     *
     * Endpoint: GET /api/v1/listening/progress/{bookId}
     * Auth: Required
     *
     * @param bookId Book to get progress for
     * @return Result containing PlaybackProgressResponse or null if not found
     */
    suspend fun getProgress(bookId: String): Result<PlaybackProgressResponse?>

    /**
     * Get list of books with playback progress (Continue Listening).
     *
     * Returns display-ready items with embedded book details.
     * No client-side joins required - ready for immediate display.
     *
     * Endpoint: GET /api/v1/listening/continue
     * Auth: Required
     *
     * @param limit Maximum number of books to return (default 10)
     * @return Result containing list of ContinueListeningItemResponse
     */
    suspend fun getContinueListening(limit: Int = 10): Result<List<ContinueListeningItemResponse>>
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
     * Register a new user account (when open registration is enabled).
     *
     * Creates a user with pending status that requires admin approval.
     * Only available when open_registration is enabled on the server.
     *
     * @param email User's email address
     * @param password User's password (min 8 characters)
     * @param firstName User's first name
     * @param lastName User's last name
     * @return RegisterResponse with success message
     * @throws Exception on network errors or if registration is disabled
     */
    suspend fun register(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
    ): RegisterResponse

    /**
     * Refresh access token using refresh token.
     */
    suspend fun refresh(refreshToken: RefreshToken): AuthResponse

    /**
     * Logout and invalidate current session.
     */
    suspend fun logout(sessionId: String)

    /**
     * Check the approval status of a pending registration.
     *
     * Used to poll for approval after registering. Once approved,
     * the client can proceed with login.
     *
     * @param userId User ID from registration response
     * @return RegistrationStatusResponse with current status
     */
    suspend fun checkRegistrationStatus(userId: String): RegistrationStatusResponse
}

/**
 * Contract interface for global tag API operations.
 *
 * Tags are community-wide content descriptors (e.g., "found-family", "slow-burn").
 * Any user can add/remove tags from books they have access to.
 *
 * Extracted to enable mocking in tests. Production implementation
 * is [TagApi], test implementation can be a mock or fake.
 */
interface TagApiContract {
    /**
     * Get all global tags ordered by popularity (book count).
     */
    suspend fun listTags(): List<Tag>

    /**
     * Get a specific tag by its slug.
     *
     * @param slug The tag slug (e.g., "found-family")
     * @return The tag, or null if not found
     */
    suspend fun getTagBySlug(slug: String): Tag?

    /**
     * Get tags for a specific book.
     *
     * @param bookId The book ID
     */
    suspend fun getBookTags(bookId: String): List<Tag>

    /**
     * Add a tag to a book. Creates the tag if it doesn't exist.
     *
     * The raw input will be normalized to a slug by the server
     * (e.g., "Found Family" -> "found-family").
     *
     * @param bookId The book ID
     * @param rawInput The tag text (will be normalized to slug)
     * @return The tag that was added (or created)
     */
    suspend fun addTagToBook(
        bookId: String,
        rawInput: String,
    ): Tag

    /**
     * Remove a tag from a book.
     *
     * @param bookId The book ID
     * @param slug The tag slug to remove
     */
    suspend fun removeTagFromBook(
        bookId: String,
        slug: String,
    )
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

    /**
     * Download multiple covers in a single request.
     *
     * Server returns a TAR stream containing all requested covers.
     * Missing covers are silently skipped by the server.
     *
     * Endpoint: GET /api/v1/covers/batch?ids=book_1,book_2
     * Auth: Not required (public access)
     * Response: application/x-tar (TAR archive)
     *
     * @param bookIds List of book IDs to download covers for (max 100)
     * @return Result containing map of bookId to cover bytes for successfully downloaded covers
     */
    suspend fun downloadCoverBatch(bookIds: List<String>): Result<Map<String, ByteArray>>

    /**
     * Download multiple contributor images in a single request.
     *
     * Server returns a TAR stream containing all requested images.
     * Missing images are silently skipped by the server.
     *
     * Endpoint: GET /api/v1/contributors/images/batch?ids=contrib_1,contrib_2
     * Auth: Required (Bearer token)
     * Response: application/x-tar (TAR archive)
     *
     * @param contributorIds List of contributor IDs to download images for (max 100)
     * @return Result containing map of contributorId to image bytes for successfully downloaded images
     */
    suspend fun downloadContributorImageBatch(contributorIds: List<String>): Result<Map<String, ByteArray>>
}

/**
 * Response from image upload operations.
 */
data class ImageUploadResponse(
    val imageUrl: String,
)

// =============================================================================
// Segregated API Interfaces (ISP - Interface Segregation Principle)
// =============================================================================

/**
 * Contract interface for instance-level API operations.
 *
 * Handles server instance information and user-specific listening data.
 */
interface InstanceApiContract {
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
}

/**
 * Contract interface for book editing API operations.
 *
 * Handles book metadata updates and relationship management.
 */
interface BookApiContract {
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
}

/**
 * Contract interface for contributor API operations.
 *
 * Handles contributor search, updates, and merge/unmerge operations.
 */
interface ContributorApiContract {
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
     * Delete a contributor.
     *
     * DELETE /api/v1/contributors/{contributorId}
     *
     * Soft-deletes the contributor. Books associated with this contributor
     * will have their contributor links removed.
     *
     * @param contributorId The contributor to delete
     * @return Result indicating success or failure
     */
    suspend fun deleteContributor(contributorId: String): Result<Unit>
}

/**
 * Contract interface for series API operations.
 *
 * Handles series search and updates.
 */
interface SeriesApiContract {
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

// =============================================================================
// Aggregate Interface (for backward compatibility)
// =============================================================================

/**
 * Aggregate contract interface for ListenUp API operations.
 *
 * Extends all domain-specific API contracts for backward compatibility.
 * New code should prefer using the specific contracts (BookApiContract,
 * ContributorApiContract, etc.) following ISP.
 *
 * Production implementation is [ListenUpApi].
 */
interface ListenUpApiContract :
    InstanceApiContract,
    BookApiContract,
    ContributorApiContract,
    SeriesApiContract

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

/**
 * Contract interface for user settings API operations.
 *
 * Handles syncing user playback settings across devices.
 * Server endpoint: /api/v1/settings (GET/PATCH)
 */
interface UserPreferencesApiContract {
    /**
     * Get user settings from the server.
     *
     * Endpoint: GET /api/v1/settings
     * Auth: Required
     *
     * @return Result containing user settings or error
     */
    suspend fun getPreferences(): Result<UserPreferencesResponse>

    /**
     * Update user settings on the server.
     *
     * Endpoint: PATCH /api/v1/settings
     * Auth: Required
     *
     * Uses PATCH semantics - only non-null fields are updated.
     *
     * @param request The settings to update (only non-null fields are sent)
     * @return Result containing updated settings or error
     */
    suspend fun updatePreferences(request: UserPreferencesRequest): Result<UserPreferencesResponse>
}

/**
 * Response from user settings endpoint.
 *
 * Contains all user-level playback settings that sync across devices.
 * These apply to all books unless overridden per-book.
 */
data class UserPreferencesResponse(
    /** Default playback speed for new books (0.5 - 3.0) */
    val defaultPlaybackSpeed: Float,
    /** Default skip forward duration in seconds (10, 15, 30, 45, 60) */
    val defaultSkipForwardSec: Int = 30,
    /** Default skip backward duration in seconds (5, 10, 15, 30) */
    val defaultSkipBackwardSec: Int = 10,
    /** Default sleep timer duration in minutes (null = disabled) */
    val defaultSleepTimerMin: Int? = null,
    /** Whether shaking device resets sleep timer */
    val shakeToResetSleepTimer: Boolean = false,
)

/**
 * Request to update user settings.
 *
 * All fields are optional - only non-null fields are updated (PATCH semantics).
 */
data class UserPreferencesRequest(
    /** Default playback speed for new books (0.5 - 3.0) */
    val defaultPlaybackSpeed: Float? = null,
    /** Default skip forward duration in seconds */
    val defaultSkipForwardSec: Int? = null,
    /** Default skip backward duration in seconds */
    val defaultSkipBackwardSec: Int? = null,
    /** Default sleep timer duration in minutes (null to disable) */
    val defaultSleepTimerMin: Int? = null,
    /** Whether shaking device resets sleep timer */
    val shakeToResetSleepTimer: Boolean? = null,
)

// =============================================================================
// Admin API Contracts (Admin-only endpoints)
// =============================================================================

/**
 * Contract interface for admin server settings API operations.
 *
 * Handles server-wide settings like inbox workflow.
 * Server endpoints: /api/v1/admin/settings (GET/PATCH)
 */
interface AdminSettingsApiContract {
    /**
     * Get server settings (admin only).
     *
     * Endpoint: GET /api/v1/admin/settings
     * Auth: Required (admin)
     *
     * @return Result containing server settings
     */
    suspend fun getServerSettings(): Result<ServerSettingsResponse>

    /**
     * Update server settings (admin only).
     *
     * Endpoint: PATCH /api/v1/admin/settings
     * Auth: Required (admin)
     *
     * @param request Fields to update (only non-null fields are sent)
     * @return Result containing updated settings
     */
    suspend fun updateServerSettings(request: ServerSettingsRequest): Result<ServerSettingsResponse>
}

/**
 * Response from server settings endpoint.
 *
 * Contains server-wide configuration managed by admins.
 */
data class ServerSettingsResponse(
    /** Whether inbox workflow is enabled */
    val inboxEnabled: Boolean,
    /** Number of books currently in inbox */
    val inboxCount: Int,
)

/**
 * Request to update server settings (PATCH semantics).
 */
data class ServerSettingsRequest(
    /** Enable or disable inbox workflow */
    val inboxEnabled: Boolean? = null,
)

/**
 * Contract interface for admin inbox API operations.
 *
 * Handles the inbox staging workflow where newly scanned books
 * land for admin review before becoming visible to users.
 */
interface AdminInboxApiContract {
    /**
     * List all books in the inbox (admin only).
     *
     * Endpoint: GET /api/v1/admin/inbox
     * Auth: Required (admin)
     *
     * @return Result containing list of inbox books with staging info
     */
    suspend fun listInboxBooks(): Result<InboxBooksResponse>

    /**
     * Release books from inbox to the library (admin only).
     *
     * Books are moved out of inbox and become visible to users.
     * If staged collections are set, the book is assigned to those collections.
     * If no collections are staged, the book becomes public.
     *
     * Endpoint: POST /api/v1/admin/inbox/release
     * Auth: Required (admin)
     *
     * @param bookIds List of book IDs to release
     * @return Result containing release summary
     */
    suspend fun releaseBooks(bookIds: List<String>): Result<ReleaseInboxBooksResponse>

    /**
     * Stage a collection assignment for an inbox book (admin only).
     *
     * When the book is released, it will be assigned to this collection.
     * Multiple collections can be staged.
     *
     * Endpoint: POST /api/v1/admin/inbox/{bookId}/stage
     * Auth: Required (admin)
     *
     * @param bookId Book ID in inbox
     * @param collectionId Collection ID to stage
     * @return Result with Unit on success
     */
    suspend fun stageCollection(
        bookId: String,
        collectionId: String,
    ): Result<Unit>

    /**
     * Remove a staged collection from an inbox book (admin only).
     *
     * Endpoint: DELETE /api/v1/admin/inbox/{bookId}/stage/{collectionId}
     * Auth: Required (admin)
     *
     * @param bookId Book ID in inbox
     * @param collectionId Collection ID to unstage
     * @return Result with Unit on success
     */
    suspend fun unstageCollection(
        bookId: String,
        collectionId: String,
    ): Result<Unit>
}

/**
 * Response from inbox list endpoint.
 */
data class InboxBooksResponse(
    val books: List<InboxBookResponse>,
    val total: Int,
)

/**
 * A book in the inbox with staging information.
 */
data class InboxBookResponse(
    /** Book ID */
    val id: String,
    /** Book title */
    val title: String,
    /** Primary author name */
    val author: String?,
    /** Cover image URL (relative path) */
    val coverUrl: String?,
    /** Total duration in milliseconds */
    val duration: Long,
    /** Collection IDs staged for assignment on release */
    val stagedCollectionIds: List<String>,
    /** Staged collections with names for display */
    val stagedCollections: List<CollectionRef>,
    /** When the book was scanned */
    val scannedAt: String,
)

/**
 * Collection reference with ID and name for display.
 */
data class CollectionRef(
    val id: String,
    val name: String,
)

/**
 * Response from releasing inbox books.
 */
data class ReleaseInboxBooksResponse(
    /** Number of books released */
    val released: Int,
    /** Number of books made public (no collections) */
    val public: Int,
    /** Number of collection assignments made */
    val toCollections: Int,
)
