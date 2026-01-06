package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.local.db.TagDao
import com.calypsan.listenup.client.data.local.db.TagEntity
import com.calypsan.listenup.client.domain.model.Tag
import com.calypsan.listenup.client.domain.repository.TagRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

/**
 * Implementation of TagRepository using Room.
 *
 * Wraps TagDao and converts entities to domain models.
 *
 * @property dao Room DAO for tag operations
 */
class TagRepositoryImpl(
    private val dao: TagDao,
) : TagRepository {
    override fun observeAll(): Flow<List<Tag>> =
        dao.observeAllTags().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getAll(): List<Tag> =
        dao.getAllTags().map { it.toDomain() }

    override suspend fun getById(id: String): Tag? =
        dao.getById(id)?.toDomain()

    override fun observeById(id: String): Flow<Tag?> =
        dao.observeById(id).map { it?.toDomain() }

    override suspend fun getBySlug(slug: String): Tag? =
        dao.getBySlug(slug)?.toDomain()

    override fun observeTagsForBook(bookId: String): Flow<List<Tag>> =
        dao.observeTagsForBook(BookId(bookId)).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getTagsForBook(bookId: String): List<Tag> =
        dao.getTagsForBook(BookId(bookId)).map { it.toDomain() }

    override suspend fun getBookIdsForTag(tagId: String): List<String> =
        dao.getBookIdsForTag(tagId).map { it.value }

    override fun observeBookIdsForTag(tagId: String): Flow<List<String>> =
        dao.observeBookIdsForTag(tagId).map { bookIds ->
            bookIds.map { it.value }
        }
}

/**
 * Convert TagEntity to Tag domain model.
 */
private fun TagEntity.toDomain(): Tag =
    Tag(
        id = id,
        slug = slug,
        bookCount = bookCount,
        createdAt = Instant.fromEpochMilliseconds(createdAt.epochMillis),
    )
