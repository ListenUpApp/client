@file:OptIn(ExperimentalTime::class)

package com.calypsan.listenup.client.data.remote.model

import com.calypsan.listenup.client.data.local.db.ContributorEntity
import com.calypsan.listenup.client.data.local.db.SeriesEntity
import com.calypsan.listenup.client.data.local.db.SyncState
import com.calypsan.listenup.client.data.local.db.Timestamp
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

fun SeriesResponse.toEntity(): SeriesEntity {
    val now = Timestamp.now()
    val serverUpdatedAt =
        try {
            Timestamp.fromEpochMillis(Instant.parse(updatedAt).toEpochMilliseconds())
        } catch (_: Exception) {
            now // Fallback to current time if parsing fails
        }
    val serverCreatedAt =
        try {
            Timestamp.fromEpochMillis(Instant.parse(createdAt).toEpochMilliseconds())
        } catch (_: Exception) {
            now // Fallback to current time if parsing fails
        }

    return SeriesEntity(
        id = id,
        name = name,
        description = description,
        syncState = SyncState.SYNCED,
        lastModified = now,
        serverVersion = serverUpdatedAt,
        createdAt = serverCreatedAt,
        updatedAt = serverUpdatedAt,
    )
}

fun ContributorResponse.toEntity(): ContributorEntity {
    val now = Timestamp.now()
    val serverUpdatedAt =
        try {
            Timestamp.fromEpochMillis(Instant.parse(updatedAt).toEpochMilliseconds())
        } catch (_: Exception) {
            now // Fallback to current time if parsing fails
        }
    val serverCreatedAt =
        try {
            Timestamp.fromEpochMillis(Instant.parse(createdAt).toEpochMilliseconds())
        } catch (_: Exception) {
            now // Fallback to current time if parsing fails
        }

    // Convert aliases list to comma-separated string for storage
    val aliasesString = aliases?.takeIf { it.isNotEmpty() }?.joinToString(", ")

    return ContributorEntity(
        id = id,
        name = name,
        description = biography,
        imagePath = imageUrl,
        imageBlurHash = imageBlurHash,
        aliases = aliasesString,
        website = website,
        birthDate = birthDate,
        deathDate = deathDate,
        syncState = SyncState.SYNCED,
        lastModified = now,
        serverVersion = serverUpdatedAt,
        createdAt = serverCreatedAt,
        updatedAt = serverUpdatedAt,
    )
}
