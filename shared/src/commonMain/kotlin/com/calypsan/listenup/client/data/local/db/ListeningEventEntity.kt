package com.calypsan.listenup.client.data.local.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Room entity representing a listening event.
 *
 * Listening events are append-only and immutable - they record a segment
 * of time spent listening to a book. Stats are computed locally from
 * these events.
 *
 * Synced bidirectionally:
 * - Local events pushed to server
 * - Events from other devices received via SSE
 */
@Entity(
    tableName = "listening_events",
    indices = [
        Index(value = ["bookId"]),
        Index(value = ["endedAt"]),
        Index(value = ["syncState"]),
    ],
)
data class ListeningEventEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val startPositionMs: Long,
    val endPositionMs: Long,
    /** When listening started (epoch ms) */
    val startedAt: Long,
    /** When listening ended (epoch ms) */
    val endedAt: Long,
    val playbackSpeed: Float,
    val deviceId: String,
    val syncState: SyncState,
    val createdAt: Long,
) {
    /** Duration of this listening segment in milliseconds */
    val durationMs: Long get() = endPositionMs - startPositionMs
}

/**
 * DAO for listening event operations.
 *
 * Provides queries for:
 * - Stats computation (events in date range)
 * - Sync operations (pending events)
 */
@Dao
interface ListeningEventDao {
    /**
     * Get all events in a date range for stats computation.
     * Returns Flow for automatic UI updates when events are added.
     */
    @Query("SELECT * FROM listening_events WHERE endedAt >= :startMs AND endedAt < :endMs ORDER BY endedAt DESC")
    fun observeEventsInRange(
        startMs: Long,
        endMs: Long,
    ): Flow<List<ListeningEventEntity>>

    /**
     * Get all events since a timestamp for stats computation.
     * No upper bound so new events are always included.
     * Returns Flow for automatic UI updates when events are added.
     */
    @Query("SELECT * FROM listening_events WHERE endedAt >= :startMs ORDER BY endedAt DESC")
    fun observeEventsSince(startMs: Long): Flow<List<ListeningEventEntity>>

    /**
     * Get all events for a specific book.
     */
    @Query("SELECT * FROM listening_events WHERE bookId = :bookId ORDER BY endedAt DESC")
    fun observeEventsForBook(bookId: String): Flow<List<ListeningEventEntity>>

    /**
     * Get total duration for events since a timestamp.
     */
    @Query("SELECT COALESCE(SUM(endPositionMs - startPositionMs), 0) FROM listening_events WHERE endedAt >= :startMs")
    suspend fun getTotalDurationSince(startMs: Long): Long

    /**
     * Get events pending sync to server.
     */
    @Query("SELECT * FROM listening_events WHERE syncState = :state")
    suspend fun getByState(state: SyncState): List<ListeningEventEntity>

    /**
     * Get an event by ID.
     */
    @Query("SELECT * FROM listening_events WHERE id = :id")
    suspend fun getById(id: String): ListeningEventEntity?

    /**
     * Insert or update an event.
     */
    @Upsert
    suspend fun upsert(event: ListeningEventEntity)

    /**
     * Insert multiple events (for initial sync).
     */
    @Upsert
    suspend fun upsertAll(events: List<ListeningEventEntity>)

    /**
     * Update sync state for an event.
     */
    @Query("UPDATE listening_events SET syncState = :state WHERE id = :id")
    suspend fun updateSyncState(
        id: String,
        state: SyncState,
    )

    /**
     * Get the most recent event timestamp for sync cursor.
     */
    @Query("SELECT MAX(endedAt) FROM listening_events")
    suspend fun getLatestEventTimestamp(): Long?

    /**
     * Get distinct dates with listening activity for streak calculation.
     * Returns dates as epoch milliseconds (start of day).
     */
    @Query(
        """
        SELECT DISTINCT (endedAt / 86400000) * 86400000 as dayStart
        FROM listening_events
        WHERE endedAt >= :startMs
        ORDER BY dayStart DESC
    """,
    )
    suspend fun getDistinctDaysWithActivity(startMs: Long): List<Long>

    /**
     * Get total duration grouped by book for a date range.
     */
    @Query(
        """
        SELECT bookId, SUM(endPositionMs - startPositionMs) as totalMs
        FROM listening_events
        WHERE endedAt >= :startMs AND endedAt < :endMs
        GROUP BY bookId
        ORDER BY totalMs DESC
    """,
    )
    suspend fun getDurationByBook(
        startMs: Long,
        endMs: Long,
    ): List<BookDuration>
}

/**
 * Result class for duration-by-book query.
 */
data class BookDuration(
    val bookId: String,
    val totalMs: Long,
)
