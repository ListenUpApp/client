package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.local.db.GenreDao
import com.calypsan.listenup.client.data.local.db.GenreEntity
import com.calypsan.listenup.client.domain.model.Genre
import com.calypsan.listenup.client.domain.repository.GenreRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Implementation of GenreRepository using Room.
 *
 * Wraps GenreDao and converts entities to domain models.
 *
 * @property dao Room DAO for genre operations
 */
class GenreRepositoryImpl(
    private val dao: GenreDao,
) : GenreRepository {
    override fun observeAll(): Flow<List<Genre>> =
        dao.observeAllGenres().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getAll(): List<Genre> =
        dao.getAllGenres().map { it.toDomain() }

    override suspend fun getById(id: String): Genre? =
        dao.getById(id)?.toDomain()

    override suspend fun getBySlug(slug: String): Genre? =
        dao.getBySlug(slug)?.toDomain()

    override fun observeGenresForBook(bookId: String): Flow<List<Genre>> =
        dao.observeGenresForBook(BookId(bookId)).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getGenresForBook(bookId: String): List<Genre> =
        dao.getGenresForBook(BookId(bookId)).map { it.toDomain() }

    override suspend fun getBookIdsForGenre(genreId: String): List<String> =
        dao.getBookIdsForGenre(genreId).map { it.value }
}

/**
 * Convert GenreEntity to Genre domain model.
 */
private fun GenreEntity.toDomain(): Genre =
    Genre(
        id = id,
        name = name,
        slug = slug,
        path = path,
        bookCount = bookCount,
    )
