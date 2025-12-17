package com.calypsan.listenup.client.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.calypsan.listenup.client.core.currentEpochMilliseconds

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
    val updatedAt: Long,
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
    indices = [Index(value = ["syncState"])],
)
data class BookEntity(
    @PrimaryKey val id: BookId,
    // Core book metadata
    val title: String,
    val subtitle: String? = null, // Book subtitle
    val coverUrl: String?, // URL to cover image (local or remote)
    val totalDuration: Long, // Total audiobook duration in milliseconds
    val description: String? = null,
    val genres: String? = null, // Comma-separated genres
    // Series is now managed via book_series junction table (many-to-many)
    val publishYear: Int? = null,
    val publisher: String? = null, // Publisher name
    val language: String? = null, // ISO 639-1 language code (e.g., "en", "es")
    val isbn: String? = null, // ISBN for metadata lookup
    val asin: String? = null, // Amazon ASIN for metadata lookup
    val abridged: Boolean = false, // Whether this is an abridged version
    // Audio files as JSON (parsed at runtime for playback)
    val audioFilesJson: String? = null,
    // Sync fields (implements Syncable)
    override val syncState: SyncState,
    override val lastModified: Timestamp,
    override val serverVersion: Timestamp?,
    // Timestamps matching server Syncable pattern
    val createdAt: Timestamp,
    val updatedAt: Timestamp,
) : Syncable

/**
 * Local database entity for book chapters.
 */
@Entity(
    tableName = "chapters",
    indices = [
        Index(value = ["bookId"]),
        Index(value = ["syncState"]),
    ],
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
    override val serverVersion: Timestamp?,
) : Syncable

/**
 * Local database entity for series.
 */
@Entity(
    tableName = "series",
    indices = [Index(value = ["syncState"])],
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
    val updatedAt: Timestamp,
) : Syncable

/**
 * Local database entity for contributors (authors, narrators, etc.).
 *
 * Aliases: Pen names that have been merged into this contributor.
 * When "Richard Bachman" is added as an alias of "Stephen King":
 * - All books by Richard Bachman get re-linked to Stephen King
 * - Richard Bachman contributor is deleted
 * - "Richard Bachman" is added to Stephen King's aliases field
 *
 * Future sync: When a book arrives with author "Richard Bachman",
 * the system checks if any contributor has this in their aliases
 * and links to that contributor instead.
 */
@Entity(
    tableName = "contributors",
    indices = [
        Index(value = ["syncState"]),
    ],
)
data class ContributorEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String?,
    val imagePath: String?,
    // Optional fields - will sync when server supports them
    val website: String? = null,
    val birthDate: String? = null, // ISO 8601 date (e.g., "1947-09-21")
    val deathDate: String? = null, // ISO 8601 date (e.g., "2024-01-15")
    // Merged pen names, comma-separated (e.g., "Richard Bachman, John Swithen")
    val aliases: String? = null,
    // Sync fields
    override val syncState: SyncState,
    override val lastModified: Timestamp,
    override val serverVersion: Timestamp?,
    val createdAt: Timestamp,
    val updatedAt: Timestamp,
) : Syncable {
    /**
     * Parse aliases into a list.
     */
    fun aliasList(): List<String> = aliases?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    /**
     * Check if a name matches this contributor (either primary name or alias).
     */
    fun matchesName(searchName: String): Boolean =
        name.equals(searchName, ignoreCase = true) ||
            aliasList().any { it.equals(searchName, ignoreCase = true) }
}

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
    val value: String,
)

/**
 * Local playback position persistence.
 *
 * Stores the user's current position in each book for instant resume.
 * This is local-first - saves immediately on every pause/seek, syncs to server eventually.
 *
 * Position is sacred: never lose the user's place.
 */
@Entity(tableName = "playback_positions")
data class PlaybackPositionEntity(
    @PrimaryKey val bookId: BookId,
    // Current position in book (book-relative)
    val positionMs: Long,
    // Last used speed for this book
    val playbackSpeed: Float,
    // Local timestamp (epoch ms)
    val updatedAt: Long,
    // When last synced to server (null if not synced)
    val syncedAt: Long? = null,
)

/**
 * Pending listening event for eventual sync to server.
 *
 * Events are append-only facts: "User listened to book X from position A to B".
 * Queued locally, synced when network is available. Fire-and-forget with retries.
 *
 * Only created for meaningful sessions (≥10 seconds of listening).
 */
@Entity(
    tableName = "pending_listening_events",
    indices = [Index(value = ["bookId"])],
)
data class PendingListeningEventEntity(
    @PrimaryKey val id: String, // Client-generated UUID
    val bookId: BookId,
    // Book-relative start position
    val startPositionMs: Long,
    // Book-relative end position
    val endPositionMs: Long,
    // Epoch ms when playback started
    val startedAt: Long,
    // Epoch ms when playback ended
    val endedAt: Long,
    val playbackSpeed: Float,
    val deviceId: String,
    // Retry count
    val attempts: Int = 0,
    // For exponential backoff
    val lastAttemptAt: Long? = null,
)

/**
 * Local database entity for ListenUp servers.
 *
 * Client-only data - stores discovered servers and their authentication state.
 * Each server maintains its own auth tokens, enabling instant context switching
 * between servers without re-authentication.
 *
 * Discovery flow:
 * 1. mDNS discovers server on local network → creates/updates ServerEntity
 * 2. User selects server → sets isActive = true
 * 3. User authenticates → stores tokens in this entity
 * 4. Server goes offline → lastSeenAt becomes stale, but entity persists
 * 5. Server comes back → localUrl updated via discovery
 */
@Entity(
    tableName = "servers",
    indices = [Index(value = ["isActive"])],
)
data class ServerEntity(
    /** Server's unique ID from mDNS TXT record (id=srv_xxx) */
    @PrimaryKey val id: String,
    /** Human-readable server name from mDNS TXT record */
    val name: String,
    /** API version from mDNS TXT record (e.g., "v1") */
    val apiVersion: String,
    /** Server version from mDNS TXT record (e.g., "1.0.0") */
    val serverVersion: String,
    /** Local network URL discovered via mDNS (e.g., "http://192.168.1.50:8080") */
    val localUrl: String? = null,
    /** Remote/public URL from mDNS TXT record or manual entry */
    val remoteUrl: String? = null,
    /** PASETO access token for this server */
    val accessToken: String? = null,
    /** Refresh token for this server */
    val refreshToken: String? = null,
    /** Session ID for this server */
    val sessionId: String? = null,
    /** Authenticated user ID on this server */
    val userId: String? = null,
    /** Whether this is the currently active server */
    val isActive: Boolean = false,
    /** Last time server was seen on local network (epoch ms), 0 if never discovered */
    val lastSeenAt: Long = 0,
    /** Last successful connection (epoch ms), null if never connected */
    val lastConnectedAt: Long? = null,
) {
    /** Check if server has valid authentication tokens */
    fun isAuthenticated(): Boolean = accessToken != null && refreshToken != null && userId != null

    /** Check if local URL is fresh (seen within threshold) */
    fun isLocalUrlFresh(staleThresholdMs: Long = STALE_THRESHOLD_MS): Boolean =
        localUrl != null && currentEpochMilliseconds() - lastSeenAt < staleThresholdMs

    /** Get best available URL (prefers fresh local, falls back to remote) */
    fun getBestUrl(staleThresholdMs: Long = STALE_THRESHOLD_MS): String? =
        when {
            isLocalUrlFresh(staleThresholdMs) -> localUrl

            remoteUrl != null -> remoteUrl

            localUrl != null -> localUrl

            // Stale local better than nothing
            else -> null
        }

    companion object {
        /** Threshold for considering local URL stale (5 minutes) */
        const val STALE_THRESHOLD_MS = 5 * 60 * 1000L
    }
}
