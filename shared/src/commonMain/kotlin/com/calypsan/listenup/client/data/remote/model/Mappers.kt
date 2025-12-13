package com.calypsan.listenup.client.data.remote.model

import com.calypsan.listenup.client.data.local.db.BookEntity
import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.local.db.SyncState
import com.calypsan.listenup.client.data.local.db.Timestamp
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/*
 * Extension functions for mapping DTOs from server to local database entities.
 *
 * Converts raw types from JSON (String IDs, String timestamps) into
 * type-safe value classes (BookId, Timestamp) for compile-time safety.
 */

/**
 * Convert BookResponse from server to BookEntity for local storage.
 *
 * Newly synced books are marked as SYNCED. The serverVersion is set to
 * the server's updatedAt timestamp for future conflict detection.
 *
 * Maps raw String ID to type-safe BookId and ISO 8601 timestamps to
 * type-safe Timestamp value classes.
 *
 * @receiver BookResponse from API
 * @return BookEntity ready for Room insertion
 */
fun BookResponse.toEntity(): BookEntity =
    BookEntity(
        id = BookId(id),
        title = title,
        subtitle = subtitle,
        coverUrl = coverImage?.path,
        totalDuration = totalDuration,
        description = description,
        genres = genres?.joinToString(", "),
        // Series is now stored via book_series junction table (many-to-many)
        publishYear = publishYear?.toIntOrNull(),
        publisher = publisher,
        language = language,
        isbn = isbn,
        asin = asin,
        abridged = abridged,
        // Audio files serialized as JSON for runtime parsing during playback
        audioFilesJson = audioFiles.takeIf { it.isNotEmpty() }?.let { Json.encodeToString(it) },
        // Sync fields - newly synced book is clean
        syncState = SyncState.SYNCED,
        lastModified = updatedAt.toTimestamp(),
        serverVersion = updatedAt.toTimestamp(),
        // Timestamps from server
        createdAt = createdAt.toTimestamp(),
        updatedAt = updatedAt.toTimestamp(),
    )

fun ChapterResponse.toEntity(
    bookId: BookId,
    index: Int,
): com.calypsan.listenup.client.data.local.db.ChapterEntity {
    val now =
        com.calypsan.listenup.client.data.local.db.Timestamp
            .now()
    return com.calypsan.listenup.client.data.local.db.ChapterEntity(
        // ID is composite of bookId and index since server doesn't provide stable chapter IDs yet
        id =
            com.calypsan.listenup.client.data.local.db
                .ChapterId("${bookId.value}_$index"),
        bookId = bookId,
        title = title,
        duration = endTime - startTime,
        startTime = startTime,
        syncState = SyncState.SYNCED,
        lastModified = now,
        serverVersion = now, // Chapters don't have individual timestamps yet, assume synced with book
    )
}

fun BookContributorResponse.toEntity(
    bookId: BookId,
    role: String,
): com.calypsan.listenup.client.data.local.db.BookContributorCrossRef =
    com.calypsan.listenup.client.data.local.db.BookContributorCrossRef(
        bookId = bookId,
        contributorId = contributorId,
        role = role,
    )

fun BookSeriesInfoResponse.toEntity(bookId: BookId): com.calypsan.listenup.client.data.local.db.BookSeriesCrossRef =
    com.calypsan.listenup.client.data.local.db.BookSeriesCrossRef(
        bookId = bookId,
        seriesId = seriesId,
        sequence = sequence,
    )

/**
 * Parse ISO 8601 timestamp string to type-safe Timestamp.
 *
 * Converts server timestamp format (RFC 3339) to our type-safe Timestamp value class.
 * Provides detailed error messages for debugging malformed timestamps.
 *
 * @receiver ISO 8601 timestamp string (e.g., "2025-11-22T14:30:45Z")
 * @return Type-safe Timestamp value class
 * @throws IllegalArgumentException if timestamp format is invalid, with details about the parsing failure
 */
@OptIn(ExperimentalTime::class)
fun String.toTimestamp(): Timestamp =
    try {
        Timestamp(Instant.parse(this).toEpochMilliseconds())
    } catch (e: IllegalArgumentException) {
        // Provide context for debugging - which timestamp failed and why
        throw IllegalArgumentException(
            "Failed to parse timestamp '$this'. Expected ISO 8601 format (e.g., '2025-11-22T14:30:45Z'). " +
                "Original error: ${e.message}",
            e,
        )
    } catch (e: Exception) {
        // Catch any other parsing errors
        throw IllegalArgumentException(
            "Unexpected error parsing timestamp '$this': ${e.message}",
            e,
        )
    }

/**
 * Convert type-safe Timestamp to ISO 8601 string for API requests.
 *
 * Converts our type-safe Timestamp value class to server timestamp format for API requests.
 *
 * @receiver Type-safe Timestamp
 * @return ISO 8601 timestamp string (e.g., "2025-11-22T14:30:45Z")
 */
@OptIn(ExperimentalTime::class)
fun Timestamp.toIso8601(): String = Instant.fromEpochMilliseconds(this.epochMillis).toString()
