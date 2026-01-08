@file:OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)

package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.data.local.db.PlaybackPositionDao
import com.calypsan.listenup.client.data.remote.SyncApiContract
import com.calypsan.listenup.client.domain.model.ContinueListeningBook
import com.calypsan.listenup.client.domain.repository.HomeRepository
import com.calypsan.listenup.client.domain.repository.NetworkMonitor
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Repository for Home screen data.
 *
 * Handles fetching continue listening books.
 *
 * Architecture: Local-first when offline, server-first when online.
 * - Checks network status before attempting server call
 * - Server returns display-ready data with embedded book details
 * - Falls back to local data when offline or server unavailable
 *
 * @property bookRepository Repository for fetching book details (fallback only)
 * @property playbackPositionDao DAO for playback positions (fallback only)
 * @property syncApi API for fetching server progress
 * @property networkMonitor Monitor for checking network connectivity
 */
class HomeRepositoryImpl(
    private val bookRepository: com.calypsan.listenup.client.domain.repository.BookRepository,
    private val playbackPositionDao: PlaybackPositionDao,
    private val syncApi: SyncApiContract,
    private val networkMonitor: NetworkMonitor,
) : HomeRepository {
    private val logger = KotlinLogging.logger {}

    /**
     * Fetch books the user is currently listening to.
     *
     * Local-first approach:
     * - Always use local data as primary source (most up-to-date for this device)
     * - Local positions are updated immediately during playback
     * - Server sync happens in background and updates local DB
     *
     * This ensures instant updates after playback without waiting for sync.
     *
     * @param limit Maximum number of books to return
     * @return Result containing list of ContinueListeningBook on success
     */
    override suspend fun getContinueListening(limit: Int): Result<List<ContinueListeningBook>> {
        logger.debug { "getContinueListening: using local-first approach" }
        return fetchFromLocal(limit)
    }

    /**
     * Fetch from server - returns display-ready data with book details embedded.
     *
     * Server provides progress data + blurHash, but we need to look up the
     * local cover path from downloaded covers (server path isn't usable on client).
     */
    private suspend fun fetchFromServer(limit: Int): Result<List<ContinueListeningBook>> =
        try {
            when (val result = syncApi.getContinueListening(limit)) {
                is Success -> {
                    val books =
                        result.data
                            .filter { it.progress < 0.99 } // Skip finished books
                            .map { item ->
                                // Server gives us progress + blurHash, but we need local cover path
                                val localBook = bookRepository.getBook(item.bookId)
                                item.toDomain().copy(
                                    coverPath = localBook?.coverPath,
                                    coverBlurHash = item.coverBlurHash ?: localBook?.coverBlurHash,
                                )
                            }
                    Success(books)
                }

                is Failure -> {
                    result
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "fetchFromServer failed" }
            Failure(e)
        }

    /**
     * Fallback: fetch from local database when offline.
     * Requires client-side join with book details.
     */
    private suspend fun fetchFromLocal(limit: Int): Result<List<ContinueListeningBook>> {
        val positions = playbackPositionDao.getRecentPositions(limit)
        logger.info { "fetchFromLocal: found ${positions.size} playback positions" }
        positions.forEachIndexed { index, pos ->
            logger.debug {
                "  position[$index]: bookId=${pos.bookId.value}, positionMs=${pos.positionMs}, lastPlayedAt=${pos.lastPlayedAt}"
            }
        }

        if (positions.isEmpty()) {
            return Success(emptyList())
        }

        var booksNotFound = 0
        var booksFiltered = 0

        val books =
            positions.mapNotNull { position ->
                val bookIdStr = position.bookId.value
                val book =
                    bookRepository.getBook(bookIdStr) ?: run {
                        booksNotFound++
                        logger.warn { "Local fallback: book not found - id=$bookIdStr" }
                        return@mapNotNull null
                    }

                val progress =
                    if (book.duration > 0) {
                        (position.positionMs.toFloat() / book.duration).coerceIn(0f, 1f)
                    } else {
                        0f
                    }

                // Skip finished books
                if (progress >= 0.99f) {
                    booksFiltered++
                    logger.debug { "Local fallback: skipping finished book - id=$bookIdStr, progress=$progress" }
                    return@mapNotNull null
                }

                // Use lastPlayedAt if available, fall back to updatedAt for legacy data
                val lastPlayedAtMs = position.lastPlayedAt ?: position.updatedAt
                val lastPlayedAtIso = Instant.fromEpochMilliseconds(lastPlayedAtMs).toString()

                ContinueListeningBook(
                    bookId = bookIdStr,
                    title = book.title,
                    authorNames = book.authorNames,
                    coverPath = book.coverPath,
                    coverBlurHash = book.coverBlurHash,
                    progress = progress,
                    currentPositionMs = position.positionMs,
                    totalDurationMs = book.duration,
                    lastPlayedAt = lastPlayedAtIso,
                )
            }

        logger.info {
            "fetchFromLocal: returning ${books.size} books " +
                "(positions=${positions.size}, notFound=$booksNotFound, filtered=$booksFiltered)"
        }
        return Success(books)
    }

    /**
     * Observe continue listening books from local database.
     *
     * Transforms playback positions into ContinueListeningBook objects
     * by joining with book details. Provides real-time updates when
     * positions change locally (instant, no sync delay).
     *
     * @param limit Maximum number of books to return
     * @return Flow emitting list of ContinueListeningBook whenever positions change
     */
    override fun observeContinueListening(limit: Int): Flow<List<ContinueListeningBook>> =
        playbackPositionDao
            .observeAll()
            .mapLatest { positions ->
                val sortedPositions =
                    positions
                        .sortedByDescending { it.lastPlayedAt ?: it.updatedAt }
                        .take(limit)

                val result = mutableListOf<ContinueListeningBook>()
                for (position in sortedPositions) {
                    val bookIdStr = position.bookId.value
                    val book = bookRepository.getBook(bookIdStr) ?: continue

                    val progress =
                        if (book.duration > 0) {
                            (position.positionMs.toFloat() / book.duration).coerceIn(0f, 1f)
                        } else {
                            0f
                        }

                    // Skip finished books
                    if (progress >= 0.99f) continue

                    val lastPlayedAtMs = position.lastPlayedAt ?: position.updatedAt
                    val lastPlayedAtIso = Instant.fromEpochMilliseconds(lastPlayedAtMs).toString()

                    result.add(
                        ContinueListeningBook(
                            bookId = bookIdStr,
                            title = book.title,
                            authorNames = book.authorNames,
                            coverPath = book.coverPath,
                            coverBlurHash = book.coverBlurHash,
                            progress = progress,
                            currentPositionMs = position.positionMs,
                            totalDurationMs = book.duration,
                            lastPlayedAt = lastPlayedAtIso,
                        ),
                    )
                }
                result
            }
}
