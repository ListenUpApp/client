package com.calypsan.listenup.client.data.local.db

import androidx.room.TypeConverter

/**
 * Room type converters for complex types.
 *
 * Currently empty as we're using primitive types (Long for timestamps).
 * If we need to store kotlinx.datetime.Instant directly in the future,
 * we would add converters here.
 *
 * Example for future reference:
 * ```kotlin
 * @TypeConverter
 * fun fromTimestamp(value: Long?): Instant? {
 *     return value?.let { Instant.fromEpochMilliseconds(it) }
 * }
 *
 * @TypeConverter
 * fun toTimestamp(instant: Instant?): Long? {
 *     return instant?.toEpochMilliseconds()
 * }
 * ```
 */
class DateTimeConverters {
    // No converters needed currently - using Long for timestamps
    // This class exists for future extensibility and as documentation
}
