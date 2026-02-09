package com.calypsan.listenup.client.data.sync.pull

import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.core.Timestamp
import com.calypsan.listenup.client.core.currentEpochMilliseconds
import com.calypsan.listenup.client.data.local.db.ShelfBookCrossRef
import com.calypsan.listenup.client.data.local.db.ShelfBookDao
import com.calypsan.listenup.client.data.local.db.ShelfDao
import com.calypsan.listenup.client.data.local.db.ShelfEntity
import com.calypsan.listenup.client.data.remote.ShelfApiContract
import com.calypsan.listenup.client.data.remote.ShelfResponse
import com.calypsan.listenup.client.data.sync.model.SyncStatus
import com.calypsan.listenup.client.domain.repository.ImageStorage
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Syncs the current user's shelves from server.
 *
 * Fetches all shelves owned by the authenticated user and caches them
 * in Room for offline access. Also fetches individual shelf details to
 * populate the shelf-book relationships for offline-first shelf content.
 *
 * Non-critical — failures are logged but don't block the rest of the sync.
 */
class ShelfPuller(
    private val shelfApi: ShelfApiContract,
    private val shelfDao: ShelfDao,
    private val shelfBookDao: ShelfBookDao,
    private val imageStorage: ImageStorage,
) : Puller {
    override suspend fun pull(
        updatedAfter: String?,
        onProgress: (SyncStatus) -> Unit,
    ) {
        logger.debug { "Starting shelf sync..." }

        try {
            // 1. Fetch basic shelf list
            val shelves = shelfApi.getMyShelves()
            logger.info { "Fetched ${shelves.size} shelves" }

            // 2. Fetch individual shelf details for book relationships
            val allShelfBooks = mutableListOf<ShelfBookCrossRef>()
            val entitiesWithCovers = mutableListOf<ShelfEntity>()

            shelves.forEach { shelfResponse ->
                try {
                    val shelfDetail = shelfApi.getShelf(shelfResponse.id)
                    logger.debug {
                        "Fetched details for shelf '${shelfDetail.name}' with ${shelfDetail.books.size} books"
                    }

                    // Create shelf-book relationships
                    val shelfBooks =
                        shelfDetail.books.mapIndexed { index, book ->
                            ShelfBookCrossRef(
                                shelfId = shelfDetail.id,
                                bookId = book.id,
                                // Use reverse order to maintain newest-first when ordering by addedAt DESC
                                addedAt = currentEpochMilliseconds() - index,
                            )
                        }

                    // Resolve cover paths from LOCAL image storage (not server paths)
                    val coverPaths =
                        shelfDetail.books
                            .map { BookId(it.id) }
                            .filter { imageStorage.exists(it) }
                            .take(4)
                            .map { imageStorage.getCoverPath(it) }

                    logger.debug {
                        "Shelf '${shelfDetail.name}': resolved ${coverPaths.size} local cover paths from ${shelfDetail.books.size} books"
                    }

                    // Create entity with locally-resolved cover paths
                    val entity = shelfResponse.toEntity().copy(coverPaths = coverPaths)
                    entitiesWithCovers.add(entity)

                    allShelfBooks.addAll(shelfBooks)
                } catch (e: Exception) {
                    logger.warn(e) { "Failed to fetch details for shelf ${shelfResponse.id}, using basic info" }
                    // Fall back to basic entity without cover paths
                    entitiesWithCovers.add(shelfResponse.toEntity())
                }
            }

            // 3. Update database
            shelfDao.upsertAll(entitiesWithCovers)
            logger.info { "Cached ${entitiesWithCovers.size} shelf entities" }

            // 4. Update shelf-book relationships (clear and repopulate to ensure freshness)
            shelfBookDao.deleteAll()
            shelfBookDao.upsertAll(allShelfBooks)

            logger.info {
                "Shelf sync complete: ${entitiesWithCovers.size} shelves, ${allShelfBooks.size} shelf-book relationships cached"
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to fetch shelves" }
            // Non-critical — don't throw
        }
    }
}

private fun ShelfResponse.toEntity(): ShelfEntity {
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

    return ShelfEntity(
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
        coverPaths = emptyList(),
    )
}
