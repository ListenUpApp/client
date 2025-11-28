package com.calypsan.listenup.client.data.remote.model

import com.calypsan.listenup.client.data.local.db.ContributorEntity
import com.calypsan.listenup.client.data.local.db.SeriesEntity
import com.calypsan.listenup.client.data.local.db.SyncState
import com.calypsan.listenup.client.data.local.db.Timestamp
import kotlin.time.ExperimentalTime
import kotlinx.datetime.Instant

@OptIn(ExperimentalTime::class)
fun SeriesResponse.toEntity(): SeriesEntity {
    val now = Timestamp.now()
    val serverUpdatedAt = try {
        Timestamp.fromEpochMillis(Instant.parse(updatedAt).toEpochMilliseconds())
    } catch (e: Exception) {
        now
    }
    val serverCreatedAt = try {
        Timestamp.fromEpochMillis(Instant.parse(createdAt).toEpochMilliseconds())
    } catch (e: Exception) {
        now
    }

    return SeriesEntity(
        id = id,
        name = name,
        description = description,
        syncState = SyncState.SYNCED,
        lastModified = now,
        serverVersion = serverUpdatedAt,
        createdAt = serverCreatedAt,
        updatedAt = serverUpdatedAt
    )
}

@OptIn(ExperimentalTime::class)
fun ContributorResponse.toEntity(): ContributorEntity {
    val now = Timestamp.now()
    val serverUpdatedAt = try {
        Timestamp.fromEpochMillis(Instant.parse(updatedAt).toEpochMilliseconds())
    } catch (e: Exception) {
        now
    }
    val serverCreatedAt = try {
        Timestamp.fromEpochMillis(Instant.parse(createdAt).toEpochMilliseconds())
    } catch (e: Exception) {
        now
    }

    return ContributorEntity(
        id = id,
        name = name,
        description = description,
        imagePath = imagePath,
        syncState = SyncState.SYNCED,
        lastModified = now,
        serverVersion = serverUpdatedAt,
        createdAt = serverCreatedAt,
        updatedAt = serverUpdatedAt
    )
}
