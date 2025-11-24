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

    // Denormalized author for quick display
    // TODO: Replace with contributors array in future version
    @SerialName("author")
    val author: String? = null,      // Will be derived from contributors[0].name, nullable as backend may not send it

    @SerialName("cover_image")
    val coverImage: ImageFileInfoResponse? = null,

    @SerialName("total_duration")
    val totalDuration: Long,         // Milliseconds

    // Future fields (ignored for now via ignoreUnknownKeys)
    // - isbn, description, publisher, publish_year, language, asin
    // - genres, tags, contributors, series_id, sequence
    // - audio_files, chapters, total_size, explicit, abridged
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
