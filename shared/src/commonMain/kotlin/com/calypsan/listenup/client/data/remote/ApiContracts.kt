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
}

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
}
