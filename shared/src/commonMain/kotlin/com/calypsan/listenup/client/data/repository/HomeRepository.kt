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
 * Architecture: Server-first with local fallback.
 * - Server returns display-ready data with embedded book details
 * - No client-side joins required (works on fresh installs)
 * - Falls back to local data when offline
 *
 * @property bookRepository Repository for fetching book details (fallback only)
 * @property playbackPositionDao DAO for playback positions (fallback only)
 * @property syncApi API for fetching server progress
 * @property userDao DAO for user data
 */
class HomeRepository(
    private val bookRepository: BookRepositoryContract,
    private val playbackPositionDao: PlaybackPositionDao,
    private val syncApi: SyncApiContract,
    private val userDao: UserDao,
) : HomeRepositoryContract {
    private val logger = KotlinLogging.logger {}

    /**
     * Fetch books the user is currently listening to.
     *
     * Server-first approach:
     * 1. Try server - returns display-ready data with book details embedded
     * 2. If offline, fall back to local positions + local book lookup
     *
     * This ensures fresh installs work (server has all data) while
     * maintaining offline capability.
     *
     * @param limit Maximum number of books to return
     * @return Result containing list of ContinueListeningBook on success
     */
    override suspend fun getContinueListening(limit: Int): Result<List<ContinueListeningBook>> {
        logger.debug { "Fetching continue listening books, limit=$limit" }

        // Try server first - returns display-ready data
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
     * No client-side joins needed.
     */
    private suspend fun fetchFromServer(limit: Int): Result<List<ContinueListeningBook>> =
        try {
            when (val result = syncApi.getContinueListening(limit)) {
                is Success -> {
                    val books = result.data
                        .filter { it.progress < 0.99 } // Skip finished books
                        .map { it.toDomain() }
                    Success(books)
                }
                is Failure -> result
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
        logger.debug { "Found ${positions.size} local playback positions" }

        val books = positions.mapNotNull { position ->
            val bookIdStr = position.bookId.value
            val book = bookRepository.getBook(bookIdStr) ?: run {
                logger.warn { "Book not found locally: $bookIdStr" }
                return@mapNotNull null
            }

            val progress = if (book.duration > 0) {
                (position.positionMs.toFloat() / book.duration).coerceIn(0f, 1f)
            } else {
                0f
            }

            // Skip finished books
            if (progress >= 0.99f) return@mapNotNull null

            ContinueListeningBook(
                bookId = bookIdStr,
                title = book.title,
                authorNames = book.authorNames,
                coverPath = book.coverPath,
                coverBlurHash = book.coverBlurHash,
                progress = progress,
                currentPositionMs = position.positionMs,
                totalDurationMs = book.duration,
                lastPlayedAt = position.updatedAt.toString(),
            )
        }

        logger.debug { "Returning ${books.size} continue listening books from local" }
        return Success(books)
    }

    /**
     * Observe current user for greeting display.
     *
     * @return Flow that emits UserEntity when available
     */
    override fun observeCurrentUser(): Flow<UserEntity?> = userDao.observeCurrentUser()
}
