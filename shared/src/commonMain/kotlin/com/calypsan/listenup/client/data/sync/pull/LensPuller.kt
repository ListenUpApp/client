package com.calypsan.listenup.client.data.sync.pull

import com.calypsan.listenup.client.core.Timestamp
import com.calypsan.listenup.client.core.currentEpochMilliseconds
import com.calypsan.listenup.client.data.local.db.LensDao
import com.calypsan.listenup.client.data.local.db.LensEntity
import com.calypsan.listenup.client.data.remote.LensApiContract
import com.calypsan.listenup.client.data.remote.LensResponse
import com.calypsan.listenup.client.data.sync.model.SyncStatus
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Syncs the current user's lenses from server.
 *
 * Fetches all lenses owned by the authenticated user and caches them
 * in Room for offline access. Non-critical — failures are logged but
 * don't block the rest of the sync.
 */
class LensPuller(
    private val lensApi: LensApiContract,
    private val lensDao: LensDao,
) : Puller {
    override suspend fun pull(
        updatedAfter: String?,
        onProgress: (SyncStatus) -> Unit,
    ) {
        logger.debug { "Starting lens sync..." }

        try {
            val lenses = lensApi.getMyLenses()
            logger.info { "Fetched ${lenses.size} lenses" }

            val entities = lenses.map { it.toEntity() }
            lensDao.upsertAll(entities)
            logger.info { "Lens sync complete: ${entities.size} lenses cached" }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to fetch lenses" }
            // Non-critical — don't throw
        }
    }
}

private fun LensResponse.toEntity(): LensEntity {
    val createdAtMs =
        try {
            Instant.parse(createdAt).toEpochMilliseconds()
        } catch (_: Exception) {
            currentEpochMilliseconds()
        }
    val updatedAtMs =
        try {
            Instant.parse(updatedAt).toEpochMilliseconds()
        } catch (_: Exception) {
            currentEpochMilliseconds()
        }

    return LensEntity(
        id = id,
        name = name,
        description = description.ifEmpty { null },
        ownerId = owner.id,
        ownerDisplayName = owner.displayName,
        ownerAvatarColor = owner.avatarColor,
        bookCount = bookCount,
        totalDurationSeconds = totalDuration,
        createdAt = Timestamp(createdAtMs),
        updatedAt = Timestamp(updatedAtMs),
    )
}
