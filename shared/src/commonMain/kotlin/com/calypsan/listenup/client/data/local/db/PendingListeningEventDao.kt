package com.calypsan.listenup.client.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

/**
 * Room DAO for [PendingListeningEventEntity] operations.
 *
 * Manages the event queue for eventual sync to server.
 * Events are append-only facts, queued locally and synced when network is available.
 */
@Dao
interface PendingListeningEventDao {

    /**
     * Queue a new listening event for sync.
     *
     * @param event The event to queue
     */
    @Insert
    suspend fun insert(event: PendingListeningEventEntity)

    /**
     * Get pending events ordered by creation time.
     * Limited to batch size for efficient processing.
     *
     * @param limit Maximum events to return
     * @return List of oldest pending events
     */
    @Query("SELECT * FROM pending_listening_events ORDER BY startedAt ASC LIMIT :limit")
    suspend fun getPending(limit: Int = 50): List<PendingListeningEventEntity>

    /**
     * Get all pending events for a specific book.
     * Used for debugging/display purposes.
     *
     * @param bookId The book to get events for
     * @return List of pending events for the book
     */
    @Query("SELECT * FROM pending_listening_events WHERE bookId = :bookId ORDER BY startedAt ASC")
    suspend fun getForBook(bookId: BookId): List<PendingListeningEventEntity>

    /**
     * Delete an event after successful sync.
     *
     * @param event The event to delete
     */
    @Delete
    suspend fun delete(event: PendingListeningEventEntity)

    /**
     * Delete event by ID after successful sync.
     *
     * @param id The event ID to delete
     */
    @Query("DELETE FROM pending_listening_events WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Update retry count and last attempt timestamp for failed sync.
     * Used for exponential backoff.
     *
     * @param id Event ID
     * @param now Current timestamp for backoff calculation
     */
    @Query("UPDATE pending_listening_events SET attempts = attempts + 1, lastAttemptAt = :now WHERE id = :id")
    suspend fun markAttempt(id: String, now: Long)

    /**
     * Get count of pending events.
     * Used for UI badges/indicators.
     *
     * @return Number of events waiting to sync
     */
    @Query("SELECT COUNT(*) FROM pending_listening_events")
    suspend fun count(): Int

    /**
     * Delete all pending events.
     * Used for testing and account logout.
     */
    @Query("DELETE FROM pending_listening_events")
    suspend fun deleteAll()
}
