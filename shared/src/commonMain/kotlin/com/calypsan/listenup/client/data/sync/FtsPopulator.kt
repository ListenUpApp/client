package com.calypsan.listenup.client.data.sync

import com.calypsan.listenup.client.data.local.db.BookDao
import com.calypsan.listenup.client.data.local.db.ContributorDao
import com.calypsan.listenup.client.data.local.db.SearchDao
import com.calypsan.listenup.client.data.local.db.SeriesDao
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlin.time.measureTime

private val logger = KotlinLogging.logger {}

/**
 * Populates FTS5 tables for offline full-text search.
 *
 * Called after sync operations to ensure FTS tables mirror the main tables.
 * Performs a full rebuild strategy:
 * 1. Clear all FTS entries
 * 2. Re-insert from main tables with denormalized data
 *
 * This approach is simple and correct. For large libraries (>10k books),
 * we could optimize with incremental updates, but full rebuild is fast enough
 * for typical library sizes (<5k books) and avoids complexity of tracking changes.
 *
 * @property bookDao DAO for reading books
 * @property contributorDao DAO for reading contributors
 * @property seriesDao DAO for reading series
 * @property searchDao DAO for FTS operations
 */
class FtsPopulator(
    private val bookDao: BookDao,
    private val contributorDao: ContributorDao,
    private val seriesDao: SeriesDao,
    private val searchDao: SearchDao
) {
    /**
     * Rebuild all FTS tables from main tables.
     *
     * This is a full rebuild that clears and repopulates all FTS tables.
     * Call after sync operations complete to ensure search is up-to-date.
     */
    suspend fun rebuildAll() = withContext(Dispatchers.IO) {
        logger.info { "Starting FTS rebuild..." }

        val duration = measureTime {
            rebuildBooks()
            rebuildContributors()
            rebuildSeries()
        }

        logger.info { "FTS rebuild completed in ${duration.inWholeMilliseconds}ms" }
    }

    /**
     * Rebuild book FTS entries.
     *
     * Denormalizes author, narrator, and series name into the FTS table
     * for rich search results.
     */
    private suspend fun rebuildBooks() {
        logger.debug { "Rebuilding books_fts..." }

        // Clear existing entries
        searchDao.clearBooksFts()

        // Get all books
        val books = bookDao.getAll()

        // Insert each book with denormalized data
        var insertCount = 0
        for (book in books) {
            try {
                // Get primary author and narrator for this book
                val authorName = searchDao.getPrimaryAuthorName(book.id.value)
                val narratorName = searchDao.getPrimaryNarratorName(book.id.value)

                searchDao.insertBookFts(
                    bookId = book.id.value,
                    title = book.title,
                    subtitle = book.subtitle,
                    description = book.description,
                    author = authorName,
                    narrator = narratorName,
                    seriesName = book.seriesName,
                    genres = book.genres
                )
                insertCount++
            } catch (e: Exception) {
                logger.warn(e) { "Failed to insert book ${book.id} into FTS" }
            }
        }

        logger.debug { "Rebuilt books_fts: $insertCount entries from ${books.size} books" }
    }

    /**
     * Rebuild contributor FTS entries.
     */
    private suspend fun rebuildContributors() {
        logger.debug { "Rebuilding contributors_fts..." }

        // Clear existing entries
        searchDao.clearContributorsFts()

        // Get all contributors
        val contributors = contributorDao.getAll()

        // Insert each contributor
        var insertCount = 0
        for (contributor in contributors) {
            try {
                searchDao.insertContributorFts(
                    contributorId = contributor.id,
                    name = contributor.name,
                    description = contributor.description
                )
                insertCount++
            } catch (e: Exception) {
                logger.warn(e) { "Failed to insert contributor ${contributor.id} into FTS" }
            }
        }

        logger.debug { "Rebuilt contributors_fts: $insertCount entries" }
    }

    /**
     * Rebuild series FTS entries.
     */
    private suspend fun rebuildSeries() {
        logger.debug { "Rebuilding series_fts..." }

        // Clear existing entries
        searchDao.clearSeriesFts()

        // Get all series
        val series = seriesDao.getAll()

        // Insert each series
        var insertCount = 0
        for (s in series) {
            try {
                searchDao.insertSeriesFts(
                    seriesId = s.id,
                    name = s.name,
                    description = s.description
                )
                insertCount++
            } catch (e: Exception) {
                logger.warn(e) { "Failed to insert series ${s.id} into FTS" }
            }
        }

        logger.debug { "Rebuilt series_fts: $insertCount entries" }
    }
}
