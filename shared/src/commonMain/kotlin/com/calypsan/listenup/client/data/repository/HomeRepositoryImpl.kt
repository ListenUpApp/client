@file:OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)

package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.core.ProgressRefreshBus
import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.data.local.db.PlaybackPositionDao
import com.calypsan.listenup.client.domain.model.ContinueListeningBook
import com.calypsan.listenup.client.domain.repository.HomeRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Repository for Home screen data.
 *
 * Handles fetching continue listening books.
 *
 * Architecture: Local-first approach.
 * - Always use local data as primary source (most up-to-date for this device)
 * - Local positions are updated immediately during playback
 * - Server sync happens in background and updates local DB
 *
 * @property bookRepository Repository for fetching book details
 * @property playbackPositionDao DAO for playback positions
 */
class HomeRepositoryImpl(
    private val bookRepository: com.calypsan.listenup.client.domain.repository.BookRepository,
    private val playbackPositionDao: PlaybackPositionDao,
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

                // Skip finished books (use server's authoritative isFinished flag)
                if (position.isFinished) {
                    booksFiltered++
                    logger.debug {
                        "Local fallback: skipping finished book - id=$bookIdStr, isFinished=${position.isFinished}"
                    }
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
     * Combines Room's reactive query with ProgressRefreshBus to ensure
     * the shelf updates immediately when playback is paused/stopped,
     * even if Room's change notification is delayed.
     *
     * @param limit Maximum number of books to return
     * @return Flow emitting list of ContinueListeningBook whenever positions change
     */
    override fun observeContinueListening(limit: Int): Flow<List<ContinueListeningBook>> =
        combine(
            playbackPositionDao.observeAll(),
            ProgressRefreshBus.refreshTrigger.onStart { emit(Unit) },
        ) { positions, _ -> positions }
            .onEach { positions ->
                val finishedCount = positions.count { it.isFinished }
                logger.info {
                    "Room EMITTED: ${positions.size} positions (finished=$finishedCount)"
                }
            }.mapLatest { positions ->
                val finishedFromRoom = positions.count { it.isFinished }
                logger.info {
                    "observeContinueListening: Room emitted ${positions.size} positions " +
                        "(finished=$finishedFromRoom, unfinished=${positions.size - finishedFromRoom})"
                }

                // Filter out finished books and unstarted books BEFORE applying limit
                val inProgress = positions.filter { !it.isFinished && it.positionMs > 0 }
                logger.info { "observeContinueListening: after filter=${inProgress.size}" }

                val sortedPositions =
                    inProgress
                        .sortedByDescending { it.lastPlayedAt ?: it.updatedAt }
                        .take(limit)
                logger.info { "observeContinueListening: after sort/take=${ sortedPositions.size}" }

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
                logger.info { "observeContinueListening: returning ${result.size} books" }
                result
            }
}
