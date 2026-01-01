@file:OptIn(ExperimentalTime::class)

package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.data.local.db.PlaybackPositionDao
import com.calypsan.listenup.client.data.local.db.UserDao
import com.calypsan.listenup.client.data.local.db.UserEntity
import com.calypsan.listenup.client.data.remote.SyncApiContract
import com.calypsan.listenup.client.domain.model.ContinueListeningBook
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Contract interface for home repository operations.
 *
 * Extracted to enable mocking in tests. Production implementation
 * is [HomeRepository], test implementation can be a mock or fake.
 */
interface HomeRepositoryContract {
    /**
     * Fetch books the user is currently listening to.
     *
     * @param limit Maximum number of books to return
     * @return Result containing list of ContinueListeningBook on success
     */
    suspend fun getContinueListening(limit: Int = 10): Result<List<ContinueListeningBook>>

    /**
     * Observe current user for greeting display.
     *
     * @return Flow that emits UserEntity when available
     */
    fun observeCurrentUser(): Flow<UserEntity?>
}

/**
 * Repository for Home screen data.
 *
 * Handles fetching continue listening books and user data for the greeting.
 *
 * Architecture: Local-first when offline, server-first when online.
 * - Checks network status before attempting server call
 * - Server returns display-ready data with embedded book details
 * - Falls back to local data when offline or server unavailable
 *
 * @property bookRepository Repository for fetching book details (fallback only)
 * @property playbackPositionDao DAO for playback positions (fallback only)
 * @property syncApi API for fetching server progress
 * @property userDao DAO for user data
 * @property networkMonitor Monitor for checking network connectivity
 */
class HomeRepository(
    private val bookRepository: BookRepositoryContract,
    private val playbackPositionDao: PlaybackPositionDao,
    private val syncApi: SyncApiContract,
    private val userDao: UserDao,
    private val networkMonitor: NetworkMonitor,
) : HomeRepositoryContract {
    private val logger = KotlinLogging.logger {}

    /**
     * Fetch books the user is currently listening to.
     *
     * Network-aware approach:
     * 1. Check network status first - use local data immediately when offline
     * 2. When online, try server - returns display-ready data with book details embedded
     * 3. If server fails, fall back to local positions + local book lookup
     *
     * This ensures offline access works instantly without waiting for timeouts,
     * while still using fresh server data when available.
     *
     * @param limit Maximum number of books to return
     * @return Result containing list of ContinueListeningBook on success
     */
    override suspend fun getContinueListening(limit: Int): Result<List<ContinueListeningBook>> {
        logger.debug { "Fetching continue listening books, limit=$limit" }

        // Check network status first - go directly to local when offline
        if (!networkMonitor.isOnline()) {
            logger.debug { "Offline - using local data" }
            return fetchFromLocal(limit)
        }

        // Online - try server first, fall back to local on failure
        return when (val serverResult = fetchFromServer(limit)) {
            is Success -> {
                logger.debug { "Returning ${serverResult.data.size} books from server" }
                serverResult
            }

            is Failure -> {
                logger.debug { "Server unavailable, using local fallback: ${serverResult.exception.message}" }
                fetchFromLocal(limit)
            }
        }
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
            Failure(e)
        }

    /**
     * Fallback: fetch from local database when offline.
     * Requires client-side join with book details.
     */
    private suspend fun fetchFromLocal(limit: Int): Result<List<ContinueListeningBook>> {
        val positions = playbackPositionDao.getRecentPositions(limit)
        logger.debug { "Local fallback: found ${positions.size} playback positions" }

        if (positions.isEmpty()) {
            logger.debug { "Local fallback: no positions in database - user hasn't played any books yet" }
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

        logger.debug {
            "Local fallback: returning ${books.size} books " +
                "(positions=${positions.size}, notFound=$booksNotFound, filtered=$booksFiltered)"
        }
        return Success(books)
    }

    /**
     * Observe current user for greeting display.
     *
     * @return Flow that emits UserEntity when available
     */
    override fun observeCurrentUser(): Flow<UserEntity?> = userDao.observeCurrentUser()
}
