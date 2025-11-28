package com.calypsan.listenup.client.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a user in the local database.
 *
 * Maps to the User domain model from the server.
 * Timestamps are stored as Unix epoch milliseconds for cross-platform compatibility.
 */
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val id: String,

    val email: String,

    val displayName: String,

    val isRoot: Boolean,

    /**
     * Creation timestamp in Unix epoch milliseconds.
     * Use kotlinx.datetime.Instant for domain model conversion.
     */
    val createdAt: Long,

    /**
     * Last update timestamp in Unix epoch milliseconds.
     * Use kotlinx.datetime.Instant for domain model conversion.
     */
    val updatedAt: Long
)

/**
 * Local database entity for audiobooks.
 *
 * Implements [Syncable] to support offline-first delta synchronization
 * with the ListenUp server. Changes are tracked locally and synced
 * when network is available.
 *
 * Uses type-safe value classes (BookId, Timestamp) for compile-time safety
 * with zero runtime overhead via inline classes.
 */
@Entity(
    tableName = "books",
    indices = [Index(value = ["syncState"])]
)
data class BookEntity(
    @PrimaryKey val id: BookId,

    // Core book metadata
    val title: String,
    val coverUrl: String?,           // URL to cover image (local or remote)
    val totalDuration: Long,         // Total audiobook duration in milliseconds
    val description: String? = null,
    val genres: String? = null,      // Comma-separated genres
    val seriesId: String? = null,
    val seriesName: String? = null,  // Denormalized series name
    val sequence: String? = null,    // Series sequence (e.g., "1", "1.5")
    val publishYear: Int? = null,

    // Sync fields (implements Syncable)
    override val syncState: SyncState,
    override val lastModified: Timestamp,
    override val serverVersion: Timestamp?,

    // Timestamps matching server Syncable pattern
    val createdAt: Timestamp,
    val updatedAt: Timestamp
) : Syncable

/**
 * Local database entity for book chapters.
 */
@Entity(
    tableName = "chapters",
    indices = [
        Index(value = ["bookId"]),
        Index(value = ["syncState"])
    ]
)
data class ChapterEntity(
    @PrimaryKey val id: ChapterId,
    val bookId: BookId,

    val title: String,
    val duration: Long, // Milliseconds
    val startTime: Long, // Milliseconds from start of book

    // Sync fields
    override val syncState: SyncState,
    override val lastModified: Timestamp,
    override val serverVersion: Timestamp?
) : Syncable

/**
 * Local database entity for series.
 */
@Entity(
    tableName = "series",
    indices = [Index(value = ["syncState"])]
)
data class SeriesEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String?,

    // Sync fields
    override val syncState: SyncState,
    override val lastModified: Timestamp,
    override val serverVersion: Timestamp?,

    val createdAt: Timestamp,
    val updatedAt: Timestamp
) : Syncable

/**
 * Local database entity for contributors (authors, narrators, etc.).
 */
@Entity(
    tableName = "contributors",
    indices = [Index(value = ["syncState"])]
)
data class ContributorEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String?,
    val imagePath: String?,

    // Sync fields
    override val syncState: SyncState,
    override val lastModified: Timestamp,
    override val serverVersion: Timestamp?,

    val createdAt: Timestamp,
    val updatedAt: Timestamp
) : Syncable

/**
 * Key-value store for sync metadata.
 *
 * Tracks sync state like last successful sync timestamp, library checkpoint,
 * and other sync-related configuration.
 *
 * Uses string values for flexibility - timestamps stored as Unix epoch milliseconds
 * converted to string.
 */
@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey val key: String,
    val value: String
)
