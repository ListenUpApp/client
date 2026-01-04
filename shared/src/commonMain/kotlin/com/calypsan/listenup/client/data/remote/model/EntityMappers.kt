package com.calypsan.listenup.client.data.remote.model

import com.calypsan.listenup.client.data.local.db.ContributorEntity
import com.calypsan.listenup.client.data.local.db.SeriesEntity
import com.calypsan.listenup.client.data.local.db.SyncState
import com.calypsan.listenup.client.data.local.db.Timestamp
import com.calypsan.listenup.client.util.parseToTimestampOrNow

fun SeriesResponse.toEntity(): SeriesEntity {
    val now = Timestamp.now()
    val serverUpdatedAt = updatedAt.parseToTimestampOrNow()
    val serverCreatedAt = createdAt.parseToTimestampOrNow()

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
    val serverUpdatedAt = updatedAt.parseToTimestampOrNow()
    val serverCreatedAt = createdAt.parseToTimestampOrNow()

    // Convert aliases list to comma-separated string for storage
    val aliasesString = aliases?.takeIf { it.isNotEmpty() }?.joinToString(", ")

    // Note: imagePath is intentionally null here. The server returns a relative URL
    // (e.g., "/api/v1/contributors/{id}/image") which is not a local file path.
    // Images must be downloaded separately and the local path set afterward.
    return ContributorEntity(
        id = id,
        name = name,
        description = biography,
        imagePath = null,
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
