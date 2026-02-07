package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.data.local.db.GenreDao
import com.calypsan.listenup.client.data.local.db.GenreEntity
import com.calypsan.listenup.client.data.remote.GenreApiContract
import com.calypsan.listenup.client.domain.model.Genre
import com.calypsan.listenup.client.domain.repository.GenreRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Implementation of GenreRepository using Room and GenreApi.
 *
 * Handles genre operations with API calls and local Room updates
 * for immediate reactivity.
 *
 * @property dao Room DAO for genre operations
 * @property genreApi API client for server genre operations
 */
class GenreRepositoryImpl(
    private val dao: GenreDao,
    private val genreApi: GenreApiContract,
) : GenreRepository {
    override fun observeAll(): Flow<List<Genre>> =
        dao.observeAllGenres().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getAll(): List<Genre> = dao.getAllGenres().map { it.toDomain() }

    override suspend fun getById(id: String): Genre? = dao.getById(id)?.toDomain()

    override suspend fun getBySlug(slug: String): Genre? = dao.getBySlug(slug)?.toDomain()

    override fun observeGenresForBook(bookId: String): Flow<List<Genre>> =
        dao.observeGenresForBook(BookId(bookId)).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getGenresForBook(bookId: String): List<Genre> =
        dao.getGenresForBook(BookId(bookId)).map { it.toDomain() }

    override suspend fun getBookIdsForGenre(genreId: String): List<String> =
        dao.getBookIdsForGenre(genreId).map { it.value }

    override suspend fun setGenresForBook(
        bookId: String,
        genreIds: List<String>,
    ) {
        // Call API to update server
        genreApi.setBookGenres(bookId, genreIds)

        // Update local Room for immediate reactivity
        dao.replaceGenresForBook(BookId(bookId), genreIds)
    }

    override suspend fun createGenre(name: String, parentId: String?): Genre {
        return genreApi.createGenre(name, parentId)
    }

    override suspend fun updateGenre(id: String, name: String): Genre {
        return genreApi.updateGenre(id, name)
    }

    override suspend fun deleteGenre(id: String) {
        genreApi.deleteGenre(id)
        // Immediate local delete for responsiveness
        dao.deleteById(id)
    }

    override suspend fun moveGenre(id: String, newParentId: String?) {
        genreApi.moveGenre(id, newParentId)
    }
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
