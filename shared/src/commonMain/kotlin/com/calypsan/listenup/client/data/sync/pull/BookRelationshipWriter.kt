package com.calypsan.listenup.client.data.sync.pull

import com.calypsan.listenup.client.data.local.db.AudioFileDao
import com.calypsan.listenup.client.data.local.db.BookContributorDao
import com.calypsan.listenup.client.data.local.db.BookSeriesDao
import com.calypsan.listenup.client.data.local.db.GenreDao
import com.calypsan.listenup.client.data.local.db.TagDao
import com.calypsan.listenup.client.data.local.db.TagEntity
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Writes per-book junction-table rows: contributors, series, tags, genres, audio files.
 * Also upserts the tag catalog so `BookTagCrossRef` foreign keys are satisfied before
 * junction inserts.
 *
 * Called by [BookPuller] after it has fetched a page, pre-collected entities into
 * [BookRelationshipBundle]s, and opened the outer write transaction.
 *
 * **Transaction contract:** MUST be called inside `TransactionRunner.atomically { ... }`.
 * This writer contributes no transactional wrapping of its own. Exceptions propagate
 * unwrapped so the caller's transaction rolls back cleanly.
 *
 * Fixes Finding 07 D11: the five per-book relationship syncers previously lived inline
 * in `BookPuller` as `flatMap`-with-side-effects patterns that entangled pure collection
 * with mutation. This unit receives already-collected entities and focuses solely on
 * the paired DELETE+INSERT for each relationship table.
 */
class BookRelationshipWriter(
    private val bookContributorDao: BookContributorDao,
    private val bookSeriesDao: BookSeriesDao,
    private val tagDao: TagDao,
    private val genreDao: GenreDao,
    private val audioFileDao: AudioFileDao,
) {
    suspend fun replaceAll(
        bundles: List<BookRelationshipBundle>,
        tagCatalog: List<TagEntity>,
    ) {
        if (bundles.isEmpty()) return

        logger.debug {
            "Replacing relationships for ${bundles.size} books, upserting ${tagCatalog.size} tag catalog entries"
        }

        if (tagCatalog.isNotEmpty()) tagDao.upsertAll(tagCatalog)

        bundles.forEach { bundle ->
            bookContributorDao.deleteContributorsForBook(bundle.bookId)
            bookSeriesDao.deleteSeriesForBook(bundle.bookId)
            tagDao.deleteTagsForBook(bundle.bookId)
            genreDao.deleteGenresForBook(bundle.bookId)
            audioFileDao.deleteForBook(bundle.bookId.value)

            if (bundle.contributors.isNotEmpty()) bookContributorDao.insertAll(bundle.contributors)
            if (bundle.series.isNotEmpty()) bookSeriesDao.insertAll(bundle.series)
            if (bundle.tags.isNotEmpty()) tagDao.insertAllBookTags(bundle.tags)
            if (bundle.genres.isNotEmpty()) genreDao.insertAllBookGenres(bundle.genres)
            if (bundle.audioFiles.isNotEmpty()) audioFileDao.upsertAll(bundle.audioFiles)
        }
    }
}
