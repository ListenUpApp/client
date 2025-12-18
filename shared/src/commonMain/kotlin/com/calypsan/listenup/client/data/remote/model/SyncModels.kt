package com.calypsan.listenup.client.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response from GET /api/v1/sync/manifest endpoint.
 *
 * Provides library overview with checkpoint timestamp for sync tracking
 * and book IDs for determining which books need to be fetched.
 */
@Serializable
data class SyncManifestResponse(
    // ISO 8601 timestamp of last library change
    @SerialName("library_version")
    val libraryVersion: String,
    // ISO 8601 timestamp for this sync checkpoint
    @SerialName("checkpoint")
    val checkpoint: String,
    // All book IDs in library
    @SerialName("book_ids")
    val bookIds: List<String>,
    // Summary counts
    @SerialName("counts")
    val counts: LibraryCounts,
)

/**
 * Library entity counts from sync manifest.
 */
@Serializable
data class LibraryCounts(
    @SerialName("books")
    val books: Int,
    @SerialName("contributors")
    val contributors: Int,
    @SerialName("series")
    val series: Int,
)

/**
 * Response from GET /api/v1/sync/books endpoint.
 *
 * Paginated list of books with cursor for fetching next page.
 */
@Serializable
data class SyncBooksResponse(
    // Base64-encoded cursor, null if no more pages
    @SerialName("next_cursor")
    val nextCursor: String? = null,
    // Array of book objects
    @SerialName("books")
    val books: List<BookResponse>,
    // IDs of books deleted since requested timestamp
    @SerialName("deleted_book_ids")
    val deletedBookIds: List<String> = emptyList(),
    // True if more pages available
    @SerialName("has_more")
    val hasMore: Boolean,
)

/**
 * Book object from server.
 *
 * Matches backend Book model structure. Contains all book metadata
 * and embedded Syncable fields (id, created_at, updated_at, deleted_at).
 *
 * For initial implementation, we only map minimal fields to BookEntity.
 * Full model with audio files, chapters, contributors, etc. will be
 * added in future iterations.
 */
@Serializable
data class BookResponse(
    // Syncable fields
    @SerialName("id")
    val id: String,
    // ISO 8601 timestamp
    @SerialName("created_at")
    val createdAt: String,
    // ISO 8601 timestamp
    @SerialName("updated_at")
    val updatedAt: String,
    // ISO 8601 timestamp, null if not deleted
    @SerialName("deleted_at")
    val deletedAt: String? = null,
    // Core metadata (minimal for v1)
    @SerialName("title")
    val title: String,
    @SerialName("subtitle")
    val subtitle: String? = null,
    @SerialName("description")
    val description: String? = null,
    @SerialName("publisher")
    val publisher: String? = null,
    // Server sends this as a string
    @SerialName("publish_year")
    val publishYear: String? = null,
    @SerialName("language")
    val language: String? = null,
    @SerialName("isbn")
    val isbn: String? = null,
    @SerialName("asin")
    val asin: String? = null,
    @SerialName("abridged")
    val abridged: Boolean = false,
    // List of strings
    @SerialName("genres")
    val genres: List<String>? = null,
    @SerialName("contributors")
    val contributors: List<BookContributorResponse> = emptyList(),
    @SerialName("cover_image")
    val coverImage: ImageFileInfoResponse? = null,
    // Milliseconds
    @SerialName("total_duration")
    val totalDuration: Long,
    // Multiple series (many-to-many with sequence per series)
    @SerialName("series_info")
    val seriesInfo: List<BookSeriesInfoResponse> = emptyList(),
    // Legacy single series fields (backward compat, will be deprecated)
    @SerialName("series_id")
    val seriesId: String? = null,
    @SerialName("series_name")
    val seriesName: String? = null,
    @SerialName("sequence")
    val sequence: String? = null,
    @SerialName("chapters")
    val chapters: List<ChapterResponse> = emptyList(),
    @SerialName("audio_files")
    val audioFiles: List<AudioFileResponse> = emptyList(),
)

@Serializable
data class BookContributorResponse(
    @SerialName("contributor_id")
    val contributorId: String,
    @SerialName("name")
    val name: String,
    @SerialName("roles")
    val roles: List<String>,
    // Original attribution name (e.g., "Richard Bachman" when contributor is Stephen King)
    @SerialName("credited_as")
    val creditedAs: String? = null,
)

/**
 * Series relationship for a book (many-to-many).
 * A book can belong to multiple series with different sequences.
 */
@Serializable
data class BookSeriesInfoResponse(
    @SerialName("series_id")
    val seriesId: String,
    @SerialName("name")
    val name: String,
    @SerialName("sequence")
    val sequence: String? = null,
)

@Serializable
data class ChapterResponse(
    @SerialName("title")
    val title: String,
    @SerialName("start_time")
    val startTime: Long,
    @SerialName("end_time")
    val endTime: Long,
)

@Serializable
data class AudioFileResponse(
    @SerialName("id")
    val id: String,
    @SerialName("filename")
    val filename: String,
    @SerialName("format")
    val format: String,
    @SerialName("codec")
    val codec: String = "", // May be empty for older server versions
    @SerialName("duration")
    val duration: Long,
    @SerialName("size")
    val size: Long,
)

/**
 * Image file metadata from server.
 */
@Serializable
data class ImageFileInfoResponse(
    // File path on server
    @SerialName("path")
    val path: String,
    // e.g., "cover.jpg"
    @SerialName("filename")
    val filename: String,
    // e.g., "jpeg", "png"
    @SerialName("format")
    val format: String,
    // Bytes
    @SerialName("size")
    val size: Long,
    // File inode for change detection
    @SerialName("inode")
    val inode: Long,
    // Unix timestamp in milliseconds
    @SerialName("mod_time")
    val modTime: Long,
    // BlurHash for placeholder display
    @SerialName("blur_hash")
    val blurHash: String? = null,
)

@Serializable
data class SyncSeriesResponse(
    @SerialName("next_cursor")
    val nextCursor: String? = null,
    @SerialName("series")
    val series: List<SeriesResponse>,
    @SerialName("deleted_series_ids")
    val deletedSeriesIds: List<String> = emptyList(),
    @SerialName("has_more")
    val hasMore: Boolean,
)

@Serializable
data class SeriesResponse(
    @SerialName("id")
    val id: String,
    @SerialName("name")
    val name: String,
    @SerialName("description")
    val description: String? = null,
    @SerialName("cover_image")
    val coverImage: ImageFileInfoResponse? = null,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String,
)

@Serializable
data class SyncContributorsResponse(
    @SerialName("next_cursor")
    val nextCursor: String? = null,
    @SerialName("contributors")
    val contributors: List<ContributorResponse>,
    @SerialName("deleted_contributor_ids")
    val deletedContributorIds: List<String> = emptyList(),
    @SerialName("has_more")
    val hasMore: Boolean,
)

@Serializable
data class ContributorResponse(
    @SerialName("id")
    val id: String,
    @SerialName("name")
    val name: String,
    @SerialName("biography")
    val biography: String? = null,
    @SerialName("image_url")
    val imageUrl: String? = null,
    @SerialName("image_blur_hash")
    val imageBlurHash: String? = null,
    @SerialName("aliases")
    val aliases: List<String>? = null,
    @SerialName("website")
    val website: String? = null,
    @SerialName("birth_date")
    val birthDate: String? = null,
    @SerialName("death_date")
    val deathDate: String? = null,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String,
)

/**
 * SSE event from GET /api/v1/sync/stream.
 *
 * All SSE events follow this envelope structure with type and data fields.
 */
@Serializable
data class SSEEvent(
    // ISO 8601 timestamp
    @SerialName("timestamp")
    val timestamp: String,
    // Event type (e.g., "book.created", "heartbeat")
    @SerialName("type")
    val type: String,
    // Event-specific data as JSON object
    // Deserialized based on event type
    @SerialName("data")
    val data: kotlinx.serialization.json.JsonElement,
)

/**
 * SSE book event data for book.created, book.updated events.
 */
@Serializable
data class SSEBookEvent(
    @SerialName("book")
    val book: BookResponse,
)

/**
 * SSE book deleted event data.
 */
@Serializable
data class SSEBookDeletedEvent(
    @SerialName("book_id")
    val bookId: String,
    // ISO 8601 timestamp
    @SerialName("deleted_at")
    val deletedAt: String,
)

/**
 * SSE library scan started event data.
 */
@Serializable
data class SSELibraryScanStartedEvent(
    @SerialName("library_id")
    val libraryId: String,
    // ISO 8601 timestamp
    @SerialName("started_at")
    val startedAt: String,
)

/**
 * SSE library scan completed event data.
 */
@Serializable
data class SSELibraryScanCompletedEvent(
    @SerialName("library_id")
    val libraryId: String,
    // ISO 8601 timestamp
    @SerialName("completed_at")
    val completedAt: String,
    @SerialName("books_added")
    val booksAdded: Int,
    @SerialName("books_updated")
    val booksUpdated: Int,
    @SerialName("books_removed")
    val booksRemoved: Int,
)
