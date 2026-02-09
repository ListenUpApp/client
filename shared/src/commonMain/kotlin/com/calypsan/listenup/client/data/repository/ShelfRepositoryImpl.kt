@file:Suppress("SwallowedException")

package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.core.Timestamp
import com.calypsan.listenup.client.core.currentEpochMilliseconds
import com.calypsan.listenup.client.data.local.db.ShelfDao
import com.calypsan.listenup.client.data.local.db.ShelfEntity
import com.calypsan.listenup.client.data.remote.ShelfApiContract
import com.calypsan.listenup.client.data.remote.ShelfDetailResponse
import com.calypsan.listenup.client.data.remote.ShelfResponse
import com.calypsan.listenup.client.domain.model.Shelf
import com.calypsan.listenup.client.domain.model.ShelfBook
import com.calypsan.listenup.client.domain.model.ShelfDetail
import com.calypsan.listenup.client.domain.model.ShelfOwner
import com.calypsan.listenup.client.domain.repository.ShelfRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Implementation of ShelfRepository using Room.
 *
 * Wraps ShelfDao and converts entities to domain models.
 * Also handles fetching shelves from API and caching locally.
 *
 * @property dao Room DAO for shelf operations
 * @property shelfApi API client for fetching shelves from server
 */
class ShelfRepositoryImpl(
    private val dao: ShelfDao,
    private val shelfApi: ShelfApiContract,
) : ShelfRepository {
    override fun observeMyShelves(userId: String): Flow<List<Shelf>> =
        dao.observeMyShelves(userId).map { entities ->
            entities.map { it.toDomain() }
        }

    override fun observeDiscoverShelves(currentUserId: String): Flow<List<Shelf>> =
        dao.observeDiscoverShelves(currentUserId).map { entities ->
            entities.map { it.toDomain() }
        }

    override fun observeById(id: String): Flow<Shelf?> = dao.observeById(id).map { it?.toDomain() }

    override suspend fun getById(id: String): Shelf? = dao.getById(id)?.toDomain()

    override suspend fun countDiscoverShelves(currentUserId: String): Int = dao.countDiscoverShelves(currentUserId)

    override suspend fun fetchAndCacheMyShelves() {
        logger.debug { "Fetching my shelves from API" }
        val shelves = shelfApi.getMyShelves()
        val entities = shelves.map { it.toEntity() }
        dao.upsertAll(entities)
        logger.info { "Fetched and cached ${entities.size} my shelves" }
    }

    /**
     * Fetch discover shelves from API and cache locally.
     *
     * Fetches shelves from other users via API and stores them in the local database.
     * This is used for initial population when Room is empty and for manual refresh.
     *
     * @return Number of shelves fetched and cached
     */
    override suspend fun fetchAndCacheDiscoverShelves(): Int {
        logger.debug { "Fetching discover shelves from API" }
        val userShelves = shelfApi.discoverShelves()
        val entities =
            userShelves.flatMap { userShelvesResponse ->
                userShelvesResponse.shelves.map { shelf ->
                    shelf.toEntity()
                }
            }
        dao.upsertAll(entities)
        logger.info { "Fetched and cached ${entities.size} discover shelves" }
        return entities.size
    }

    override suspend fun getShelfDetail(shelfId: String): ShelfDetail {
        logger.debug { "Fetching shelf detail from API: $shelfId" }
        val response = shelfApi.getShelf(shelfId)

        // Update local cache with latest book count and duration
        dao.getById(shelfId)?.let { cached ->
            dao.upsert(
                cached.copy(
                    bookCount = response.bookCount,
                    totalDurationSeconds = response.totalDuration,
                ),
            )
        }

        return response.toDomain()
    }

    override suspend fun removeBookFromShelf(
        shelfId: String,
        bookId: String,
    ) {
        logger.info { "Removing book $bookId from shelf $shelfId" }
        shelfApi.removeBook(shelfId, bookId)
    }

    override suspend fun addBooksToShelf(
        shelfId: String,
        bookIds: List<String>,
    ) {
        logger.info { "Adding ${bookIds.size} books to shelf $shelfId" }
        shelfApi.addBooks(shelfId, bookIds)
    }

    override suspend fun createShelf(
        name: String,
        description: String?,
    ): Shelf {
        logger.info { "Creating shelf: $name" }
        val response = shelfApi.createShelf(name, description)
        // Cache the new shelf locally
        dao.upsert(response.toEntity())
        return response.toDomain()
    }

    override suspend fun updateShelf(
        shelfId: String,
        name: String,
        description: String?,
    ): Shelf {
        logger.info { "Updating shelf $shelfId: $name" }
        val response = shelfApi.updateShelf(shelfId, name, description)
        // Update local cache
        dao.getById(shelfId)?.let { cached ->
            dao.upsert(
                cached.copy(
                    name = response.name,
                    description = response.description.ifEmpty { null },
                ),
            )
        }
        return response.toDomain()
    }

    override suspend fun deleteShelf(shelfId: String) {
        logger.info { "Deleting shelf $shelfId" }
        shelfApi.deleteShelf(shelfId)
        // Remove from local cache
        dao.deleteById(shelfId)
    }
}

/**
 * Convert ShelfResponse API model to Shelf domain model.
 */
fun ShelfResponse.toDomain(): Shelf {
    val createdAtMs =
        try {
            Instant.parse(createdAt).toEpochMilliseconds()
        } catch (e: Exception) {
            currentEpochMilliseconds()
        }
    val updatedAtMs =
        try {
            Instant.parse(updatedAt).toEpochMilliseconds()
        } catch (e: Exception) {
            currentEpochMilliseconds()
        }

    return Shelf(
        id = id,
        name = name,
        description = description.ifEmpty { null },
        ownerId = owner.id,
        ownerDisplayName = owner.displayName,
        ownerAvatarColor = owner.avatarColor,
        bookCount = bookCount,
        totalDurationSeconds = totalDuration,
        createdAtMs = createdAtMs,
        updatedAtMs = updatedAtMs,
    )
}

/**
 * Convert API response to Room entity.
 */
private fun ShelfResponse.toEntity(): ShelfEntity {
    val createdAtMs =
        try {
            Instant.parse(createdAt).toEpochMilliseconds()
        } catch (e: Exception) {
            currentEpochMilliseconds()
        }
    val updatedAtMs =
        try {
            Instant.parse(updatedAt).toEpochMilliseconds()
        } catch (e: Exception) {
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
    )
}

/**
 * Convert ShelfEntity to Shelf domain model.
 */
private fun ShelfEntity.toDomain(): Shelf =
    Shelf(
        id = id,
        name = name,
        description = description,
        ownerId = ownerId,
        ownerDisplayName = ownerDisplayName,
        ownerAvatarColor = ownerAvatarColor,
        bookCount = bookCount,
        totalDurationSeconds = totalDurationSeconds,
        createdAtMs = createdAt.epochMillis,
        updatedAtMs = updatedAt.epochMillis,
        coverPaths = coverPaths,
    )

/**
 * Convert ShelfDetailResponse API model to ShelfDetail domain model.
 */
private fun ShelfDetailResponse.toDomain(): ShelfDetail =
    ShelfDetail(
        id = id,
        name = name,
        description = description,
        owner =
            ShelfOwner(
                id = owner.id,
                displayName = owner.displayName,
                avatarColor = owner.avatarColor,
            ),
        bookCount = bookCount,
        totalDurationSeconds = totalDuration,
        books =
            books.map { book ->
                ShelfBook(
                    id = book.id,
                    title = book.title,
                    authorNames = book.authorNames,
                    coverPath = book.coverPath,
                    durationSeconds = book.durationSeconds,
                )
            },
    )
