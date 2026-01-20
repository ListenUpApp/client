package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.IODispatcher
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.core.exceptionOrFromMessage
import com.calypsan.listenup.client.data.local.db.SearchDao
import com.calypsan.listenup.client.data.local.db.SeriesDao
import com.calypsan.listenup.client.data.local.db.SeriesEntity
import com.calypsan.listenup.client.data.local.db.toDomain
import com.calypsan.listenup.client.data.remote.SeriesApiContract
import com.calypsan.listenup.client.data.repository.common.QueryUtils
import com.calypsan.listenup.client.domain.model.Book
import com.calypsan.listenup.client.domain.model.Series
import com.calypsan.listenup.client.domain.model.SeriesSearchResponse
import com.calypsan.listenup.client.domain.model.SeriesSearchResult
import com.calypsan.listenup.client.domain.model.SeriesWithBooks
import com.calypsan.listenup.client.domain.repository.ImageStorage
import com.calypsan.listenup.client.domain.repository.NetworkMonitor
import com.calypsan.listenup.client.domain.repository.SeriesRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.measureTimedValue

private val logger = KotlinLogging.logger {}

/**
 * Implementation of the domain SeriesRepository using Room.
 *
 * Provides:
 * - Reactive (Flow-based) and one-shot queries for series
 * - Library view methods (series with their books)
 * - Series detail methods
 * - Search with "never stranded" pattern (server with local fallback)
 *
 * @property seriesDao Room DAO for series operations
 * @property searchDao Room DAO for FTS search
 * @property api Server API client for series search
 * @property networkMonitor For checking online/offline status
 * @property imageStorage For resolving cover image paths
 */
class SeriesRepositoryImpl(
    private val seriesDao: SeriesDao,
    private val searchDao: SearchDao,
    private val api: SeriesApiContract,
    private val networkMonitor: NetworkMonitor,
    private val imageStorage: ImageStorage,
) : SeriesRepository {
    // ========== Basic Observation Methods ==========

    override fun observeAll(): Flow<List<Series>> =
        seriesDao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }

    override fun observeById(id: String): Flow<Series?> =
        seriesDao.observeById(id).map { entity ->
            entity?.toDomain()
        }

    override suspend fun getById(id: String): Series? = seriesDao.getById(id)?.toDomain()

    override fun observeByBookId(bookId: String): Flow<Series?> =
        seriesDao.observeByBookId(bookId).map { entity ->
            entity?.toDomain()
        }

    override suspend fun getBookIdsForSeries(seriesId: String): List<String> = seriesDao.getBookIdsForSeries(seriesId)

    override fun observeBookIdsForSeries(seriesId: String): Flow<List<String>> =
        seriesDao.observeBookIdsForSeries(seriesId)

    // ========== Library View Methods ==========

    override fun observeAllWithBooks(): Flow<List<SeriesWithBooks>> =
        seriesDao.observeAllWithBooks().map { entities ->
            entities.map { entity ->
                val books =
                    entity.books.map { bookEntity ->
                        // Use bookDao to get full BookWithContributors if needed
                        // For library view, basic book info is sufficient
                        bookEntity.toDomain(imageStorage)
                    }
                val sequences =
                    entity.bookSequences.associate {
                        it.bookId.value to it.sequence
                    }
                SeriesWithBooks(
                    series = entity.series.toDomain(),
                    books = books,
                    bookSequences = sequences,
                )
            }
        }

    // ========== Series Detail Methods ==========

    override fun observeSeriesWithBooks(seriesId: String): Flow<SeriesWithBooks?> =
        seriesDao.observeByIdWithBooks(seriesId).map { entity ->
            entity?.let {
                val books =
                    it.books.map { bookEntity ->
                        bookEntity.toDomain(imageStorage)
                    }
                val sequences =
                    it.bookSequences.associate { seq ->
                        seq.bookId.value to seq.sequence
                    }
                SeriesWithBooks(
                    series = it.series.toDomain(),
                    books = books,
                    bookSequences = sequences,
                )
            }
        }

    // ========== Search Methods ==========

    override suspend fun searchSeries(
        query: String,
        limit: Int,
    ): SeriesSearchResponse {
        val sanitizedQuery = QueryUtils.sanitize(query)
        if (sanitizedQuery.isBlank() || sanitizedQuery.length < 2) {
            return SeriesSearchResponse(
                series = emptyList(),
                isOfflineResult = false,
                tookMs = 0,
            )
        }

        // Try server search if online
        if (networkMonitor.isOnline()) {
            try {
                return searchServer(sanitizedQuery, limit)
            } catch (e: Exception) {
                logger.warn(e) { "Server series search failed, falling back to local FTS" }
            }
        }

        // Offline or server failed - use local FTS
        return searchLocal(sanitizedQuery, limit)
    }

    private suspend fun searchServer(
        query: String,
        limit: Int,
    ): SeriesSearchResponse =
        withContext(IODispatcher) {
            val (series, duration) =
                measureTimedValue {
                    when (val result = api.searchSeries(query, limit)) {
                        is Success -> result.data.map { it.toDomain() }
                        is Failure -> throw result.exceptionOrFromMessage()
                    }
                }

            logger.debug {
                "Server series search: query='$query', results=${series.size}, took=${duration.inWholeMilliseconds}ms"
            }

            SeriesSearchResponse(
                series = series,
                isOfflineResult = false,
                tookMs = duration.inWholeMilliseconds,
            )
        }

    private suspend fun searchLocal(
        query: String,
        limit: Int,
    ): SeriesSearchResponse =
        withContext(IODispatcher) {
            val (entities, duration) =
                measureTimedValue {
                    val ftsQuery = QueryUtils.toFtsQuery(query)
                    try {
                        searchDao.searchSeries(ftsQuery, limit)
                    } catch (e: Exception) {
                        logger.warn(e) { "Series FTS search failed" }
                        emptyList()
                    }
                }

            val series = entities.map { it.toSearchResult() }

            logger.debug {
                "Local series search: query='$query', results=${series.size}, took=${duration.inWholeMilliseconds}ms"
            }

            SeriesSearchResponse(
                series = series,
                isOfflineResult = true,
                tookMs = duration.inWholeMilliseconds,
            )
        }
}

// ========== Entity to Domain Mappers ==========

private fun SeriesEntity.toDomain(): Series =
    Series(
        id = id,
        name = name,
        description = description,
        createdAt = createdAt,
    )

private fun SeriesEntity.toSearchResult(): SeriesSearchResult =
    SeriesSearchResult(
        id = id.value,
        name = name,
        bookCount = 0, // Not available in offline mode
    )

private fun com.calypsan.listenup.client.data.remote.SeriesSearchResult.toDomain(): SeriesSearchResult =
    SeriesSearchResult(
        id = id,
        name = name,
        bookCount = bookCount,
    )

/**
 * Convert BookEntity to domain Book model for series listing.
 * Simplified version without full contributor details.
 */
private fun com.calypsan.listenup.client.data.local.db.BookEntity.toDomain(imageStorage: ImageStorage): Book {
    val coverPath = if (imageStorage.exists(id)) imageStorage.getCoverPath(id) else null
    return Book(
        id = id,
        title = title,
        subtitle = subtitle,
        authors = emptyList(), // Not needed for series view
        narrators = emptyList(), // Not needed for series view
        duration = totalDuration,
        coverPath = coverPath,
        coverBlurHash = coverBlurHash,
        dominantColor = dominantColor,
        darkMutedColor = darkMutedColor,
        vibrantColor = vibrantColor,
        addedAt = createdAt,
        updatedAt = updatedAt,
        description = description,
        series = emptyList(), // Loaded separately
        publishYear = publishYear,
        publisher = publisher,
        language = language,
        isbn = isbn,
        asin = asin,
        abridged = abridged,
    )
}
