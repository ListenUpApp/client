package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.core.Timestamp
import com.calypsan.listenup.client.core.currentEpochMilliseconds
import com.calypsan.listenup.client.data.local.db.LensDao
import com.calypsan.listenup.client.data.local.db.LensEntity
import com.calypsan.listenup.client.data.remote.LensApiContract
import com.calypsan.listenup.client.data.remote.LensDetailResponse
import com.calypsan.listenup.client.data.remote.LensResponse
import com.calypsan.listenup.client.domain.model.Lens
import com.calypsan.listenup.client.domain.model.LensBook
import com.calypsan.listenup.client.domain.model.LensDetail
import com.calypsan.listenup.client.domain.model.LensOwner
import com.calypsan.listenup.client.domain.repository.LensRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Implementation of LensRepository using Room.
 *
 * Wraps LensDao and converts entities to domain models.
 * Also handles fetching lenses from API and caching locally.
 *
 * @property dao Room DAO for lens operations
 * @property lensApi API client for fetching lenses from server
 */
class LensRepositoryImpl(
    private val dao: LensDao,
    private val lensApi: LensApiContract,
) : LensRepository {
    override fun observeMyLenses(userId: String): Flow<List<Lens>> =
        dao.observeMyLenses(userId).map { entities ->
            entities.map { it.toDomain() }
        }

    override fun observeDiscoverLenses(currentUserId: String): Flow<List<Lens>> =
        dao.observeDiscoverLenses(currentUserId).map { entities ->
            entities.map { it.toDomain() }
        }

    override fun observeById(id: String): Flow<Lens?> =
        dao.observeById(id).map { it?.toDomain() }

    override suspend fun getById(id: String): Lens? =
        dao.getById(id)?.toDomain()

    override suspend fun countDiscoverLenses(currentUserId: String): Int =
        dao.countDiscoverLenses(currentUserId)

    /**
     * Fetch discover lenses from API and cache locally.
     *
     * Fetches lenses from other users via API and stores them in the local database.
     * This is used for initial population when Room is empty and for manual refresh.
     *
     * @return Number of lenses fetched and cached
     */
    override suspend fun fetchAndCacheDiscoverLenses(): Int {
        logger.debug { "Fetching discover lenses from API" }
        val userLenses = lensApi.discoverLenses()
        val entities = userLenses.flatMap { userLensesResponse ->
            userLensesResponse.lenses.map { lens ->
                lens.toEntity()
            }
        }
        dao.upsertAll(entities)
        logger.info { "Fetched and cached ${entities.size} discover lenses" }
        return entities.size
    }

    override suspend fun getLensDetail(lensId: String): LensDetail {
        logger.debug { "Fetching lens detail from API: $lensId" }
        val response = lensApi.getLens(lensId)

        // Update local cache with latest book count and duration
        dao.getById(lensId)?.let { cached ->
            dao.upsert(
                cached.copy(
                    bookCount = response.bookCount,
                    totalDurationSeconds = response.totalDuration,
                ),
            )
        }

        return response.toDomain()
    }

    override suspend fun removeBookFromLens(lensId: String, bookId: String) {
        logger.info { "Removing book $bookId from lens $lensId" }
        lensApi.removeBook(lensId, bookId)
    }

    override suspend fun addBooksToLens(lensId: String, bookIds: List<String>) {
        logger.info { "Adding ${bookIds.size} books to lens $lensId" }
        lensApi.addBooks(lensId, bookIds)
    }

    override suspend fun createLens(name: String, description: String?): Lens {
        logger.info { "Creating lens: $name" }
        val response = lensApi.createLens(name, description)
        // Cache the new lens locally
        dao.upsert(response.toEntity())
        return response.toDomain()
    }

    override suspend fun updateLens(lensId: String, name: String, description: String?): Lens {
        logger.info { "Updating lens $lensId: $name" }
        val response = lensApi.updateLens(lensId, name, description)
        // Update local cache
        dao.getById(lensId)?.let { cached ->
            dao.upsert(
                cached.copy(
                    name = response.name,
                    description = response.description.ifEmpty { null },
                ),
            )
        }
        return response.toDomain()
    }

    override suspend fun deleteLens(lensId: String) {
        logger.info { "Deleting lens $lensId" }
        lensApi.deleteLens(lensId)
        // Remove from local cache
        dao.deleteById(lensId)
    }
}

/**
 * Convert LensResponse API model to Lens domain model.
 */
fun LensResponse.toDomain(): Lens {
    val createdAtMs = try {
        Instant.parse(createdAt).toEpochMilliseconds()
    } catch (e: Exception) {
        currentEpochMilliseconds()
    }
    val updatedAtMs = try {
        Instant.parse(updatedAt).toEpochMilliseconds()
    } catch (e: Exception) {
        currentEpochMilliseconds()
    }

    return Lens(
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
private fun LensResponse.toEntity(): LensEntity {
    val createdAtMs = try {
        Instant.parse(createdAt).toEpochMilliseconds()
    } catch (e: Exception) {
        currentEpochMilliseconds()
    }
    val updatedAtMs = try {
        Instant.parse(updatedAt).toEpochMilliseconds()
    } catch (e: Exception) {
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

/**
 * Convert LensEntity to Lens domain model.
 */
private fun LensEntity.toDomain(): Lens =
    Lens(
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
    )

/**
 * Convert LensDetailResponse API model to LensDetail domain model.
 */
private fun LensDetailResponse.toDomain(): LensDetail =
    LensDetail(
        id = id,
        name = name,
        description = description,
        owner = LensOwner(
            id = owner.id,
            displayName = owner.displayName,
            avatarColor = owner.avatarColor,
        ),
        bookCount = bookCount,
        totalDurationSeconds = totalDuration,
        books = books.map { book ->
            LensBook(
                id = book.id,
                title = book.title,
                authorNames = book.authorNames,
                coverPath = book.coverPath,
                durationSeconds = book.durationSeconds,
            )
        },
    )
