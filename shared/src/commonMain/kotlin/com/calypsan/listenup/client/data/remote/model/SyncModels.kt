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
    @SerialName("library_version")
    val libraryVersion: String,      // ISO 8601 timestamp of last library change

    @SerialName("checkpoint")
    val checkpoint: String,          // ISO 8601 timestamp for this sync checkpoint

    @SerialName("book_ids")
    val bookIds: List<String>,       // All book IDs in library

    @SerialName("counts")
    val counts: LibraryCounts        // Summary counts
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
    val series: Int
)

/**
 * Response from GET /api/v1/sync/books endpoint.
 *
 * Paginated list of books with cursor for fetching next page.
 */
@Serializable
data class SyncBooksResponse(
    @SerialName("next_cursor")
    val nextCursor: String? = null,  // Base64-encoded cursor, null if no more pages

    @SerialName("books")
    val books: List<BookResponse>,   // Array of book objects

    @SerialName("deleted_book_ids")
    val deletedBookIds: List<String> = emptyList(), // IDs of books deleted since requested timestamp

    @SerialName("has_more")
    val hasMore: Boolean             // True if more pages available
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

    @SerialName("created_at")
    val createdAt: String,           // ISO 8601 timestamp

    @SerialName("updated_at")
    val updatedAt: String,           // ISO 8601 timestamp

    @SerialName("deleted_at")
    val deletedAt: String? = null,   // ISO 8601 timestamp, null if not deleted

    // Core metadata (minimal for v1)
    @SerialName("title")
    val title: String,

    @SerialName("subtitle")
    val subtitle: String? = null,

    @SerialName("description")
    val description: String? = null,

    @SerialName("publisher")
    val publisher: String? = null,

    @SerialName("publish_year")
    val publishYear: String? = null, // Server sends this as a string

    @SerialName("language")
    val language: String? = null,

    @SerialName("genres")
    val genres: List<String>? = null, // List of strings

    @SerialName("contributors")
    val contributors: List<BookContributorResponse> = emptyList(),

    @SerialName("cover_image")
    val coverImage: ImageFileInfoResponse? = null,

    @SerialName("total_duration")
    val totalDuration: Long,         // Milliseconds

    @SerialName("series_id")
    val seriesId: String? = null,

    @SerialName("series_name")
    val seriesName: String? = null, // Denormalized series name

    @SerialName("sequence")
    val sequence: String? = null, // Series sequence (e.g., "1", "1.5")

    @SerialName("chapters")
    val chapters: List<ChapterResponse> = emptyList(),

    @SerialName("audio_files")
    val audioFiles: List<AudioFileResponse> = emptyList()
)

@Serializable
data class BookContributorResponse(
    @SerialName("contributor_id")
    val contributorId: String,
    @SerialName("name")
    val name: String,
    @SerialName("roles")
    val roles: List<String>
)

@Serializable
data class ChapterResponse(
    @SerialName("title")
    val title: String,
    @SerialName("start_time")
    val startTime: Long,
    @SerialName("end_time")
    val endTime: Long
)

@Serializable
data class AudioFileResponse(
    @SerialName("id")
    val id: String,
    @SerialName("filename")
    val filename: String,
    @SerialName("format")
    val format: String,
    @SerialName("duration")
    val duration: Long,
    @SerialName("size")
    val size: Long
)

/**
 * Image file metadata from server.
 */
@Serializable
data class ImageFileInfoResponse(
    @SerialName("path")
    val path: String,                // File path on server

    @SerialName("filename")
    val filename: String,            // e.g., "cover.jpg"

    @SerialName("format")
    val format: String,              // e.g., "jpeg", "png"

    @SerialName("size")
    val size: Long,                  // Bytes

    @SerialName("inode")
    val inode: Long,                 // File inode for change detection

    @SerialName("mod_time")
    val modTime: Long                // Unix timestamp in milliseconds
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
    val hasMore: Boolean
)

@Serializable
data class SeriesResponse(
    @SerialName("id")
    val id: String,
    @SerialName("name")
    val name: String,
    @SerialName("description")
    val description: String? = null,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String
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
    val hasMore: Boolean
)

@Serializable
data class ContributorResponse(
    @SerialName("id")
    val id: String,
    @SerialName("name")
    val name: String,
    @SerialName("description")
    val description: String? = null,
    @SerialName("image_path")
    val imagePath: String? = null,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String
)

/**
 * SSE event from GET /api/v1/sync/stream.
 *
 * All SSE events follow this envelope structure with type and data fields.
 */
@Serializable
data class SSEEvent(
    @SerialName("timestamp")
    val timestamp: String,           // ISO 8601 timestamp

    @SerialName("type")
    val type: String,                // Event type (e.g., "book.created", "heartbeat")

    @SerialName("data")
    val data: kotlinx.serialization.json.JsonElement  // Event-specific data as JSON object
                                                       // Deserialized based on event type
)

/**
 * SSE book event data for book.created, book.updated events.
 */
@Serializable
data class SSEBookEvent(
    @SerialName("book")
    val book: BookResponse
)

/**
 * SSE book deleted event data.
 */
@Serializable
data class SSEBookDeletedEvent(
    @SerialName("book_id")
    val bookId: String,

    @SerialName("deleted_at")
    val deletedAt: String            // ISO 8601 timestamp
)

/**
 * SSE library scan started event data.
 */
@Serializable
data class SSELibraryScanStartedEvent(
    @SerialName("library_id")
    val libraryId: String,

    @SerialName("started_at")
    val startedAt: String            // ISO 8601 timestamp
)

/**
 * SSE library scan completed event data.
 */
@Serializable
data class SSELibraryScanCompletedEvent(
    @SerialName("library_id")
    val libraryId: String,

    @SerialName("completed_at")
    val completedAt: String,         // ISO 8601 timestamp

    @SerialName("books_added")
    val booksAdded: Int,

    @SerialName("books_updated")
    val booksUpdated: Int,

    @SerialName("books_removed")
    val booksRemoved: Int
)
