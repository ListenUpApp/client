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
    val author: String,              // Denormalized primary author for quick display
    val coverUrl: String?,           // URL to cover image (local or remote)
    val totalDuration: Long,         // Total audiobook duration in milliseconds

    // Sync fields (implements Syncable)
    override val syncState: SyncState,
    override val lastModified: Timestamp,
    override val serverVersion: Timestamp?,

    // Timestamps matching server Syncable pattern
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
