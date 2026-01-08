package com.calypsan.listenup.client.data.sync

import com.calypsan.listenup.client.data.local.db.BookContributorDao
import com.calypsan.listenup.client.data.local.db.BookDao
import com.calypsan.listenup.client.data.local.db.BookSeriesDao
import com.calypsan.listenup.client.data.local.db.ChapterDao
import com.calypsan.listenup.client.data.local.db.ContributorDao
import com.calypsan.listenup.client.data.local.db.PendingOperationDao
import com.calypsan.listenup.client.data.local.db.PlaybackPositionDao
import com.calypsan.listenup.client.data.local.db.SeriesDao
import com.calypsan.listenup.client.data.local.db.SyncDao
import com.calypsan.listenup.client.data.local.db.UserDao
import com.calypsan.listenup.client.data.local.db.clearLastSyncTime
import com.calypsan.listenup.client.domain.repository.LibrarySync
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Contract for resetting library data.
 *
 * Used when detecting library mismatch (server reset) or switching servers.
 */
interface LibraryResetHelperContract {
    /**
     * Clear all library data from local storage.
     *
     * This removes:
     * - All books, series, contributors
     * - All chapters and playback positions
     * - All junction tables (book-series, book-contributor)
     * - All pending sync operations (if discarding local changes)
     * - User data
     * - Sync timestamps
     *
     * Does NOT remove:
     * - Downloaded audio files (managed separately by DownloadDao)
     * - Server connection settings
     * - User preferences (theme, playback speed, etc.)
     *
     * @param discardPendingOperations If true, also clears pending sync operations.
     *        Set to false if you want to preserve unsync'd edits.
     */
    suspend fun clearLibraryData(discardPendingOperations: Boolean = true)

    /**
     * Clear library data and prepare for resync with a new library ID.
     *
     * This is the typical flow when handling a library mismatch:
     * 1. Clear all library data
     * 2. Clear the stored library ID
     * 3. Caller then triggers a fresh sync
     *
     * @param newLibraryId The new library ID to store after clearing
     */
    suspend fun resetForNewLibrary(newLibraryId: String)
}

/**
 * Implementation of library reset helper.
 *
 * Coordinates clearing data across multiple DAOs while preserving
 * non-library data (downloads, preferences).
 */
class LibraryResetHelper(
    private val bookDao: BookDao,
    private val seriesDao: SeriesDao,
    private val contributorDao: ContributorDao,
    private val chapterDao: ChapterDao,
    private val bookContributorDao: BookContributorDao,
    private val bookSeriesDao: BookSeriesDao,
    private val playbackPositionDao: PlaybackPositionDao,
    private val pendingOperationDao: PendingOperationDao,
    private val userDao: UserDao,
    private val syncDao: SyncDao,
    private val librarySyncContract: LibrarySync,
) : LibraryResetHelperContract {
    override suspend fun clearLibraryData(discardPendingOperations: Boolean) {
        logger.info { "Clearing library data (discardPendingOperations=$discardPendingOperations)" }

        // Clear junction tables first (foreign key constraints)
        bookContributorDao.deleteAll()
        bookSeriesDao.deleteAll()
        logger.debug { "Cleared junction tables" }

        // Clear chapters
        chapterDao.deleteAll()
        logger.debug { "Cleared chapters" }

        // Clear playback positions
        playbackPositionDao.deleteAll()
        logger.debug { "Cleared playback positions" }

        // Clear main entities
        bookDao.deleteAll()
        seriesDao.deleteAll()
        contributorDao.deleteAll()
        logger.debug { "Cleared books, series, contributors" }

        // Clear user data
        userDao.clear()
        logger.debug { "Cleared users" }

        // Optionally clear pending operations
        if (discardPendingOperations) {
            pendingOperationDao.deleteAll()
            logger.debug { "Cleared pending operations" }
        }

        // Clear sync timestamp to trigger full sync
        syncDao.clearLastSyncTime()
        logger.debug { "Cleared sync timestamp" }

        logger.info { "Library data cleared successfully" }
    }

    override suspend fun resetForNewLibrary(newLibraryId: String) {
        logger.info { "Resetting for new library: $newLibraryId" }

        // Clear all library data including pending operations
        clearLibraryData(discardPendingOperations = true)

        // Store the new library ID
        librarySyncContract.setConnectedLibraryId(newLibraryId)

        logger.info { "Ready for sync with new library" }
    }
}
