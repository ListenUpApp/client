package com.calypsan.listenup.client.data.local.db

import androidx.room.TypeConverter

/**
 * Room type converters for value classes.
 *
 * Converts inline value classes (BookId, Timestamp) to/from their underlying
 * primitive types for database storage. These converters enable type-safe
 * value classes with zero runtime overhead while maintaining Room compatibility.
 */
class ValueClassConverters {

    /**
     * Convert BookId value class to String for database storage.
     */
    @TypeConverter
    fun fromBookId(value: BookId): String {
        return value.value
    }

    /**
     * Convert String from database to BookId value class.
     */
    @TypeConverter
    fun toBookId(value: String): BookId {
        return BookId(value)
    }

    /**
     * Convert Timestamp value class to Long for database storage.
     */
    @TypeConverter
    fun fromTimestamp(value: Timestamp): Long {
        return value.epochMillis
    }

    /**
     * Convert Long from database to Timestamp value class.
     */
    @TypeConverter
    fun toTimestamp(value: Long): Timestamp {
        return Timestamp(value)
    }

    /**
     * Convert nullable Timestamp value class to nullable Long for database storage.
     */
    @TypeConverter
    fun fromNullableTimestamp(value: Timestamp?): Long? {
        return value?.epochMillis
    }

    /**
     * Convert nullable Long from database to nullable Timestamp value class.
     */
    @TypeConverter
    fun toNullableTimestamp(value: Long?): Timestamp? {
        return value?.let { Timestamp(it) }
    }
}

/**
 * Room type converters for custom types used in database entities.
 *
 * Room cannot persist enum classes directly, so we convert them to
 * their integer ordinal values for storage and back to enum instances
 * when reading from the database.
 */
class Converters {

    /**
     * Converts [SyncState] enum to integer ordinal for database storage.
     *
     * @param value The SyncState enum value to convert
     * @return Integer ordinal (0-3) representing the sync state
     */
    @TypeConverter
    fun fromSyncState(value: SyncState): Int {
        return value.ordinal
    }

    /**
     * Converts integer ordinal from database to [SyncState] enum.
     *
     * @param value Integer ordinal (0-3) from database
     * @return Corresponding SyncState enum value
     */
    @TypeConverter
    fun toSyncState(value: Int): SyncState {
        return SyncState.entries[value]
    }

    @TypeConverter
    fun fromDownloadState(state: DownloadState): Int = state.ordinal

    @TypeConverter
    fun toDownloadState(ordinal: Int): DownloadState = DownloadState.entries[ordinal]
}

/**
 * Download state for tracking individual audio file downloads.
 *
 * Ordinals: QUEUED=0, DOWNLOADING=1, PAUSED=2, COMPLETED=3, FAILED=4, DELETED=5
 */
enum class DownloadState {
    QUEUED,      // Waiting to start
    DOWNLOADING, // In progress
    PAUSED,      // User paused or interrupted
    COMPLETED,   // Successfully downloaded
    FAILED,      // Error occurred
    DELETED      // User explicitly deleted - files removed, don't auto-download
}
