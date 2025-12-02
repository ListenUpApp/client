@file:OptIn(ExperimentalTime::class)

package com.calypsan.listenup.client.data.local.db

import kotlin.jvm.JvmInline
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import com.calypsan.listenup.client.core.currentEpochMilliseconds

/**
 * Type-safe wrapper for Book IDs.
 *
 * Provides compile-time type safety to prevent accidentally passing wrong ID types
 * (e.g., user IDs, instance IDs) where book IDs are expected.
 *
 * Value class compiles to primitive String with zero runtime overhead while
 * maintaining compile-time type checking.
 *
 * @property value The underlying book ID string (e.g., "book-abc123")
 */
@JvmInline
value class BookId(val value: String) {
    init {
        require(value.isNotBlank()) { "Book ID cannot be blank" }
    }

    override fun toString(): String = value

    companion object {
        /**
         * Create BookId from string value.
         * Validates that value is not blank.
         */
        fun fromString(value: String): BookId = BookId(value)
    }
}

/**
 * Type-safe wrapper for Chapter IDs.
 */
@JvmInline
value class ChapterId(val value: String) {
    init {
        require(value.isNotBlank()) { "Chapter ID cannot be blank" }
    }
    override fun toString(): String = value
}

/**
 * Type-safe wrapper for Unix epoch millisecond timestamps.
 *
 * Prevents accidentally comparing timestamps with durations or other numeric values.
 * Provides rich API for timestamp operations while compiling to primitive Long
 * with zero runtime overhead.
 *
 * @property epochMillis Unix epoch milliseconds
 */
@JvmInline
value class Timestamp(val epochMillis: Long) : Comparable<Timestamp> {
    override fun compareTo(other: Timestamp): Int =
        epochMillis.compareTo(other.epochMillis)

    /**
     * Calculate duration between two timestamps.
     */
    operator fun minus(other: Timestamp): Duration =
        (epochMillis - other.epochMillis).milliseconds

    /**
     * Add duration to timestamp.
     */
    operator fun plus(duration: Duration): Timestamp =
        Timestamp(epochMillis + duration.inWholeMilliseconds)

    override fun toString(): String = epochMillis.toString()

    /**
     * Convert to ISO 8601 date time string.
     * e.g. "2023-11-22T14:30:45.123Z"
     */
    fun toIsoString(): String = Instant.fromEpochMilliseconds(epochMillis).toString()

    companion object {
        /**
         * Get current system time as Timestamp.
         */
        fun now(): Timestamp = Timestamp(currentEpochMilliseconds())

        /**
         * Create Timestamp from epoch milliseconds.
         */
        fun fromEpochMillis(value: Long): Timestamp = Timestamp(value)
    }
}

/**
 * Marker interface for entities that support synchronization with the server.
 *
 * Implementing entities must track their sync state, modification time, and
 * server version for conflict detection during delta sync operations.
 */
interface Syncable {
    /**
     * Current synchronization state of this entity.
     * See [SyncState] for possible values.
     */
    val syncState: SyncState

    /**
     * Last modification timestamp.
     * Updated whenever local changes are made to the entity.
     * Type-safe wrapper around Unix epoch milliseconds.
     */
    val lastModified: Timestamp

    /**
     * Server-side version timestamp.
     * Null if entity has never been synced with server.
     * Used for conflict detection - if server version is newer than
     * our lastModified, a conflict exists.
     * Type-safe wrapper around Unix epoch milliseconds.
     */
    val serverVersion: Timestamp?
}

/**
 * Synchronization state for entities implementing [Syncable].
 *
 * State transitions:
 * - New entities start as NOT_SYNCED
 * - Local modifications transition SYNCED -> NOT_SYNCED
 * - Sync operations transition NOT_SYNCED -> SYNCING -> SYNCED
 * - Conflicts transition to CONFLICT when server has newer version
 */
enum class SyncState {
    /**
     * Entity is clean and matches server state.
     * No pending local changes or server updates.
     */
    SYNCED,

    /**
     * Entity has local modifications not yet uploaded to server.
     * Will be included in next sync push operation.
     */
    NOT_SYNCED,

    /**
     * Upload operation is currently in progress for this entity.
     * Used to prevent duplicate uploads during concurrent sync attempts.
     */
    SYNCING,

    /**
     * Server has a newer version than our local modifications.
     * Requires conflict resolution (currently last-write-wins by timestamp).
     * Marked for user review in future versions.
     */
    CONFLICT;

    companion object {
        /**
         * Ordinal constants for Room @Query annotations.
         *
         * Room requires compile-time constants in query strings, so these
         * constants ensure single source of truth while enabling type-safe queries.
         *
         * Example: `UPDATE books SET syncState = ${SyncState.SYNCED_ORDINAL} WHERE id = :id`
         */
        const val SYNCED_ORDINAL = 0
        const val NOT_SYNCED_ORDINAL = 1
        const val SYNCING_ORDINAL = 2
        const val CONFLICT_ORDINAL = 3
    }
}
