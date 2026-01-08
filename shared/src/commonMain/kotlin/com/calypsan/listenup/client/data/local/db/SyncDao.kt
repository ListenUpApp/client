package com.calypsan.listenup.client.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.calypsan.listenup.client.core.Timestamp

/**
 * Room DAO for sync metadata operations.
 *
 * Manages key-value pairs tracking sync state across the application.
 * Primary use case is tracking last successful sync timestamp for
 * delta sync operations.
 *
 * Convenience methods (getLastSyncTime, setLastSyncTime) are provided
 * as extension functions for better Room compatibility.
 */
@Dao
interface SyncDao {
    /**
     * Get metadata value by key.
     *
     * @param key Metadata key (e.g., "last_sync_books")
     * @return String value or null if key doesn't exist
     */
    @Query("SELECT value FROM sync_metadata WHERE key = :key LIMIT 1")
    suspend fun getValue(key: String): String?

    /**
     * Set or update metadata value.
     *
     * @param metadata The key-value pair to store
     */
    @Upsert
    suspend fun upsert(metadata: SyncMetadataEntity)

    /**
     * Delete metadata by key.
     *
     * @param key The metadata key to delete
     */
    @Query("DELETE FROM sync_metadata WHERE key = :key")
    suspend fun delete(key: String)

    companion object {
        const val KEY_LAST_SYNC_BOOKS = "last_sync_books"
    }
}

/**
 * Get last successful sync timestamp for books.
 *
 * Extension function providing type-safe access to sync metadata.
 *
 * @return Type-safe Timestamp, or null if never synced
 */
suspend fun SyncDao.getLastSyncTime(): Timestamp? =
    getValue(SyncDao.KEY_LAST_SYNC_BOOKS)
        ?.toLongOrNull()
        ?.let { Timestamp(it) }

/**
 * Set last successful sync timestamp for books.
 *
 * Extension function providing type-safe updates to sync metadata.
 *
 * @param timestamp Type-safe Timestamp value
 */
suspend fun SyncDao.setLastSyncTime(timestamp: Timestamp) {
    upsert(SyncMetadataEntity(SyncDao.KEY_LAST_SYNC_BOOKS, timestamp.epochMillis.toString()))
}

/**
 * Clear last successful sync timestamp for books.
 *
 * This forces the next sync to be a full sync (not delta),
 * re-fetching all books from the server. Useful when:
 * - audioFilesJson is missing for existing books
 * - Database was corrupted or needs to be refreshed
 * - Migration requires re-population of data
 */
suspend fun SyncDao.clearLastSyncTime() {
    delete(SyncDao.KEY_LAST_SYNC_BOOKS)
}
