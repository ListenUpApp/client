package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.data.local.db.PlaybackPositionDao
import com.calypsan.listenup.client.data.local.db.UserDao
import com.calypsan.listenup.client.data.local.db.UserEntity
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
 * Uses local-first approach: reads from local database for instant display.
 *
 * @property bookRepository Repository for fetching book details
 * @property playbackPositionDao DAO for playback positions
 * @property userDao DAO for user data
 */
class HomeRepository(
    private val bookRepository: BookRepositoryContract,
    private val playbackPositionDao: PlaybackPositionDao,
    private val userDao: UserDao,
) : HomeRepositoryContract {
    private val logger = KotlinLogging.logger {}

    /**
     * Fetch books the user is currently listening to.
     *
     * Uses local-first approach: reads playback positions from local database
     * and enriches with book details. No network call required.
     *
     * @param limit Maximum number of books to return
     * @return Result containing list of ContinueListeningBook on success
     */
    override suspend fun getContinueListening(limit: Int): Result<List<ContinueListeningBook>> {
        logger.debug { "Fetching continue listening books from local DB, limit=$limit" }

        val positions = playbackPositionDao.getRecentPositions(limit)
        logger.debug { "Found ${positions.size} local playback positions" }

        val books =
            positions.mapNotNull { position ->
                val bookIdStr = position.bookId.value

                // Look up book details from local database
                val book = bookRepository.getBook(bookIdStr)
                if (book == null) {
                    logger.warn { "Book not found locally: $bookIdStr" }
                    return@mapNotNull null
                }

                // Calculate progress percentage
                val progress =
                    if (book.duration > 0) {
                        (position.positionMs.toFloat() / book.duration).coerceIn(0f, 1f)
                    } else {
                        0f
                    }

                // Skip if essentially complete (99%+)
                if (progress >= 0.99f) {
                    logger.debug { "Skipping finished book: $bookIdStr" }
                    return@mapNotNull null
                }

                ContinueListeningBook(
                    bookId = bookIdStr,
                    title = book.title,
                    authorNames = book.authorNames,
                    coverPath = book.coverPath,
                    progress = progress,
                    currentPositionMs = position.positionMs,
                    totalDurationMs = book.duration,
                    lastPlayedAt = position.updatedAt.toString(),
                )
            }

        logger.debug { "Returning ${books.size} continue listening books" }
        return Success(books)
    }

    /**
     * Observe current user for greeting display.
     *
     * @return Flow that emits UserEntity when available
     */
    override fun observeCurrentUser(): Flow<UserEntity?> = userDao.observeCurrentUser()
}
