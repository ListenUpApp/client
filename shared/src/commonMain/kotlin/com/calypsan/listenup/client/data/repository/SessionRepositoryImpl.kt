@file:Suppress("SwallowedException")

package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.core.AppResult
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.core.currentEpochMilliseconds
import com.calypsan.listenup.client.core.map
import com.calypsan.listenup.client.data.local.db.ReaderSessionCacheDao
import com.calypsan.listenup.client.data.local.db.ReaderSessionCacheEntity
import com.calypsan.listenup.client.data.local.db.TransactionRunner
import com.calypsan.listenup.client.data.local.db.UserReadingSessionDao
import com.calypsan.listenup.client.data.local.db.UserReadingSessionEntity
import com.calypsan.listenup.client.data.remote.ReaderSummary
import com.calypsan.listenup.client.data.remote.SessionApiContract
import com.calypsan.listenup.client.domain.model.BookReadersResult
import com.calypsan.listenup.client.domain.model.ReaderInfo
import com.calypsan.listenup.client.domain.model.SessionSummary
import com.calypsan.listenup.client.domain.repository.AuthSession
import com.calypsan.listenup.client.domain.repository.SessionRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * Implementation of SessionRepository with offline-first architecture.
 *
 * Data flow:
 * 1. ReadingSessionPuller populates the split tables during initial sync.
 * 2. UI observes Room via [observeBookReaders], which combines per-session user rows
 *    with per-(book,user) cache rows for other readers.
 * 3. SSE events trigger refreshBookReaders which re-fetches per-book.
 *
 * Writes to [userReadingSessionDao] and [readerSessionCacheDao] are wrapped in a
 * single atomically block so a mid-refresh failure doesn't leave one table stale.
 *
 * Bug 1's fixes (persist API's authoritative totalReaders; drop synthetic-self entirely;
 * ViewModel migration) live in W6 — this implementation still derives totalReaders
 * locally to match W4 scope.
 */
class SessionRepositoryImpl(
    private val sessionApi: SessionApiContract,
    private val userReadingSessionDao: UserReadingSessionDao,
    private val readerSessionCacheDao: ReaderSessionCacheDao,
    private val transactionRunner: TransactionRunner,
    private val authSession: AuthSession,
) : SessionRepository {
    // ═══════════════════════════════════════════════════════════════════════════
    // LEGACY ONE-SHOT METHODS (kept for backwards compatibility)
    // ═══════════════════════════════════════════════════════════════════════════

    override suspend fun getBookReaders(bookId: String): List<ReaderInfo> {
        val result = sessionApi.getBookReaders(bookId)
        return if (result is Success) {
            result.data.otherReaders.map { it.toDomain() }
        } else {
            emptyList()
        }
    }

    override suspend fun getBookReadersResult(bookId: String): AppResult<BookReadersResult> =
        sessionApi.getBookReaders(bookId).map { response ->
            BookReadersResult(
                yourSessions = response.yourSessions.map { it.toDomain() },
                otherReaders = response.otherReaders.map { it.toDomain() },
                totalReaders = response.totalReaders,
                totalCompletions = response.totalCompletions,
            )
        }

    // ═══════════════════════════════════════════════════════════════════════════
    // OFFLINE-FIRST REACTIVE METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    override fun observeBookReaders(bookId: String): Flow<BookReadersResult> =
        channelFlow {
            // Fire-and-forget background refresh on subscription — runs concurrent with collection
            // so cached data is emitted immediately. W6 will migrate to .onStart { } with an
            // injected CoroutineScope.
            launch {
                try {
                    refreshBookReaders(bookId)
                } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.warn(e) { "Background refresh of readers for book $bookId failed" }
                }
            }

            val currentUserId = authSession.getUserId()
            logger.debug { "Observing readers for book $bookId (currentUserId=$currentUserId)" }

            val userFlow =
                currentUserId
                    ?.let { userReadingSessionDao.observeForBook(bookId = bookId, userId = it) }
                    ?: flowOf(emptyList<UserReadingSessionEntity>())
            val cacheFlow =
                readerSessionCacheDao.observeForBook(
                    bookId = bookId,
                    excludingUserId = currentUserId ?: "",
                )

            combine(userFlow, cacheFlow) { userSessions, otherEntries ->
                BookReadersResult(
                    yourSessions = userSessions.map { it.toDomain() },
                    otherReaders = otherEntries.map { it.toDomain() },
                    totalReaders = otherEntries.size + if (userSessions.isNotEmpty()) 1 else 0,
                    totalCompletions =
                        otherEntries.sumOf { it.completionCount } +
                            userSessions.count { it.isCompleted },
                )
            }.collect { send(it) }
        }

    override suspend fun refreshBookReaders(bookId: String) {
        logger.info { "Refreshing readers for book $bookId..." }

        when (val result = sessionApi.getBookReaders(bookId)) {
            is Success -> {
                val response = result.data
                val now = currentEpochMilliseconds()
                val currentUserId = authSession.getUserId()

                val userSessions =
                    currentUserId?.let { uid ->
                        response.yourSessions.map { it.toUserSessionEntity(bookId, uid, now) }
                    } ?: emptyList()
                val cacheEntries = response.otherReaders.map { it.toCacheEntity(bookId, now) }

                transactionRunner.atomically {
                    if (currentUserId != null) {
                        userReadingSessionDao.deleteForBook(bookId = bookId, userId = currentUserId)
                        if (userSessions.isNotEmpty()) {
                            userReadingSessionDao.upsertAll(userSessions)
                        }
                    }
                    readerSessionCacheDao.deleteForBook(bookId)
                    if (cacheEntries.isNotEmpty()) {
                        readerSessionCacheDao.upsertAll(cacheEntries)
                    }
                }

                logger.debug {
                    "Cached ${userSessions.size} user sessions + ${cacheEntries.size} readers for book $bookId"
                }
            }

            is com.calypsan.listenup.client.core.Failure -> {
                logger.error { "Failed to refresh readers for book $bookId: ${result.message}" }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// CONVERSION FUNCTIONS
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Convert ReaderSummary API model to ReaderInfo domain model.
 */
private fun ReaderSummary.toDomain(): ReaderInfo =
    ReaderInfo(
        userId = userId,
        displayName = displayName,
        avatarType = avatarType,
        avatarValue = avatarValue,
        avatarColor = avatarColor,
        isCurrentlyReading = isCurrentlyReading,
        currentProgress = currentProgress,
        startedAt = startedAt,
        finishedAt = finishedAt,
        lastActivityAt = lastActivityAt,
        completionCount = completionCount,
    )

/**
 * Convert ReaderSessionCacheEntity to ReaderInfo domain model.
 */
private fun ReaderSessionCacheEntity.toDomain(): ReaderInfo {
    val lastActivity = finishedAt?.takeIf { it > startedAt } ?: startedAt
    return ReaderInfo(
        userId = userId,
        displayName = userDisplayName,
        avatarType = userAvatarType,
        avatarValue = userAvatarValue,
        avatarColor = userAvatarColor,
        isCurrentlyReading = isCurrentlyReading,
        currentProgress = currentProgress,
        startedAt = Instant.fromEpochMilliseconds(startedAt).toString(),
        finishedAt = finishedAt?.let { Instant.fromEpochMilliseconds(it).toString() },
        lastActivityAt = Instant.fromEpochMilliseconds(lastActivity).toString(),
        completionCount = completionCount,
    )
}

/**
 * Convert UserReadingSessionEntity to domain SessionSummary.
 *
 * Compared to the old synthetic-self flow, this mapper preserves `listenTimeMs`
 * and per-session timestamps verbatim — no fabricated zeros.
 */
private fun UserReadingSessionEntity.toDomain(): SessionSummary =
    SessionSummary(
        id = id,
        startedAt = Instant.fromEpochMilliseconds(startedAt).toString(),
        finishedAt = finishedAt?.let { Instant.fromEpochMilliseconds(it).toString() },
        isCompleted = isCompleted,
        listenTimeMs = listenTimeMs,
    )

/**
 * Convert ReaderSummary API model to ReaderSessionCacheEntity for caching.
 */
private fun ReaderSummary.toCacheEntity(
    bookId: String,
    updatedAt: Long,
): ReaderSessionCacheEntity =
    ReaderSessionCacheEntity(
        bookId = bookId,
        userId = userId,
        userDisplayName = displayName,
        userAvatarColor = avatarColor,
        userAvatarType = avatarType,
        userAvatarValue = avatarValue,
        isCurrentlyReading = isCurrentlyReading,
        currentProgress = currentProgress,
        startedAt = parseTimestampToEpoch(startedAt),
        finishedAt = finishedAt?.let { parseTimestampToEpoch(it) },
        completionCount = completionCount,
        updatedAt = updatedAt,
    )

/**
 * Convert API SessionSummary to UserReadingSessionEntity.
 */
private fun com.calypsan.listenup.client.data.remote.SessionSummary.toUserSessionEntity(
    bookId: String,
    userId: String,
    updatedAt: Long,
): UserReadingSessionEntity =
    UserReadingSessionEntity(
        id = id,
        bookId = bookId,
        userId = userId,
        startedAt = parseTimestampToEpoch(startedAt),
        finishedAt = finishedAt?.let { parseTimestampToEpoch(it) },
        isCompleted = isCompleted,
        listenTimeMs = listenTimeMs,
        updatedAt = updatedAt,
    )

/**
 * Parse ISO timestamp string to epoch milliseconds.
 */
private fun parseTimestampToEpoch(iso: String): Long =
    try {
        Instant.parse(iso).toEpochMilliseconds()
    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
        throw e
    } catch (e: Exception) {
        currentEpochMilliseconds()
    }

/**
 * Convert SessionSummary API model to domain model.
 */
private fun com.calypsan.listenup.client.data.remote.SessionSummary.toDomain(): SessionSummary =
    SessionSummary(
        id = id,
        startedAt = startedAt,
        finishedAt = finishedAt,
        isCompleted = isCompleted,
        listenTimeMs = listenTimeMs,
    )
