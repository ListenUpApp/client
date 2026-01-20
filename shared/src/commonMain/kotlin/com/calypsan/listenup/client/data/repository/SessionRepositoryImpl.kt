package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.core.currentEpochMilliseconds
import com.calypsan.listenup.client.core.map
import com.calypsan.listenup.client.data.local.db.ReadingSessionDao
import com.calypsan.listenup.client.data.local.db.ReadingSessionEntity
import com.calypsan.listenup.client.data.remote.ReaderSummary
import com.calypsan.listenup.client.data.remote.SessionApiContract
import com.calypsan.listenup.client.domain.model.BookReadersResult
import com.calypsan.listenup.client.domain.model.ReaderInfo
import com.calypsan.listenup.client.domain.model.SessionSummary
import com.calypsan.listenup.client.domain.repository.AuthSession
import com.calypsan.listenup.client.domain.repository.SessionRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * Implementation of SessionRepository with offline-first architecture.
 *
 * Data flow:
 * 1. UI observes Room database via [observeBookReaders]
 * 2. Repository triggers background API refresh on observation start
 * 3. API response updates Room, which notifies observers
 * 4. SSE events also update Room for real-time updates
 *
 * @property sessionApi API client for session operations
 * @property readingSessionDao Room DAO for local cache
 * @property authSession Provider for current user ID
 * @property repositoryScope Coroutine scope for background operations
 */
class SessionRepositoryImpl(
    private val sessionApi: SessionApiContract,
    private val readingSessionDao: ReadingSessionDao,
    private val authSession: AuthSession,
    private val repositoryScope: CoroutineScope,
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

    override suspend fun getBookReadersResult(bookId: String): Result<BookReadersResult> =
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

    override fun observeBookReaders(bookId: String): Flow<BookReadersResult> {
        logger.debug { "Starting observation of readers for book $bookId" }

        // Trigger background refresh
        repositoryScope.launch {
            refreshBookReaders(bookId)
        }

        // Return Flow from Room, transformed to domain model
        // Use flow builder to handle suspend getUserId() call
        return kotlinx.coroutines.flow.flow {
            val currentUserId = authSession.getUserId()
            logger.debug { "Observing Room for book $bookId (currentUserId=$currentUserId)" }

            readingSessionDao.observeByBookId(bookId).collect { entities ->
                logger.debug { "Room emitted ${entities.size} reading sessions for book $bookId" }
                emit(entitiesToBookReadersResult(entities, currentUserId))
            }
        }
    }

    override suspend fun refreshBookReaders(bookId: String) {
        logger.info { "Refreshing readers for book $bookId..." }

        when (val result = sessionApi.getBookReaders(bookId)) {
            is Success -> {
                val response = result.data
                val now = currentEpochMilliseconds()

                logger.info {
                    "API returned: ${response.yourSessions.size} your sessions, " +
                        "${response.otherReaders.size} other readers, " +
                        "total=${response.totalReaders}, completions=${response.totalCompletions}"
                }

                // Convert API response to entities
                val entities = mutableListOf<ReadingSessionEntity>()

                // Add other readers
                response.otherReaders.forEach { reader ->
                    entities.add(reader.toEntity(bookId, now))
                }

                // Add user's own sessions (represented as a single aggregated entry)
                val currentUserId = authSession.getUserId()
                if (currentUserId != null && response.yourSessions.isNotEmpty()) {
                    // Create a summary entity from user's sessions
                    val mostRecent = response.yourSessions.maxByOrNull {
                        it.finishedAt ?: it.startedAt
                    }
                    if (mostRecent != null) {
                        entities.add(
                            ReadingSessionEntity(
                                id = "self-$bookId-$currentUserId",
                                bookId = bookId,
                                oduserId = currentUserId,
                                userDisplayName = "You", // Placeholder, will be filtered in UI
                                userAvatarColor = "",
                                userAvatarType = "auto",
                                userAvatarValue = null,
                                isCurrentlyReading = mostRecent.finishedAt == null,
                                currentProgress = 0.0, // Not tracked for self
                                startedAt = parseTimestamp(response.yourSessions.minOf { it.startedAt }),
                                finishedAt = mostRecent.finishedAt?.let { parseTimestamp(it) },
                                completionCount = response.yourSessions.count { it.isCompleted },
                                updatedAt = now,
                            ),
                        )
                    }
                }

                // Upsert all entities
                if (entities.isNotEmpty()) {
                    readingSessionDao.upsertAll(entities)
                    logger.debug { "Cached ${entities.size} reading sessions for book $bookId" }
                }
            }

            is com.calypsan.listenup.client.core.Failure -> {
                logger.error(result.exception) {
                    "Failed to refresh readers for book $bookId: ${result.message}"
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Convert list of entities to BookReadersResult, separating user's own sessions.
     */
    private fun entitiesToBookReadersResult(
        entities: List<ReadingSessionEntity>,
        currentUserId: String?,
    ): BookReadersResult {
        val (userEntities, otherEntities) = entities.partition { it.userId == currentUserId }

        // Convert user's entity to SessionSummary list
        val yourSessions = userEntities.firstOrNull()?.let { entity ->
            // We store aggregated data, create summary from it
            listOf(
                SessionSummary(
                    id = entity.id,
                    startedAt = formatTimestamp(entity.startedAt),
                    finishedAt = entity.finishedAt?.let { formatTimestamp(it) },
                    isCompleted = entity.completionCount > 0,
                    listenTimeMs = 0L, // Not tracked in cache
                ),
            )
        } ?: emptyList()

        // Convert other entities to ReaderInfo
        val otherReaders = otherEntities.map { it.toDomain() }

        return BookReadersResult(
            yourSessions = yourSessions,
            otherReaders = otherReaders,
            totalReaders = entities.size,
            totalCompletions = entities.sumOf { it.completionCount },
        )
    }

    private fun parseTimestamp(iso: String): Long {
        return try {
            kotlin.time.Instant.parse(iso).toEpochMilliseconds()
        } catch (e: Exception) {
            currentEpochMilliseconds()
        }
    }

    private fun formatTimestamp(epochMs: Long): String {
        // Convert epoch ms to ISO 8601 string
        return kotlin.time.Instant.fromEpochMilliseconds(epochMs).toString()
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
        completionCount = completionCount,
    )

/**
 * Convert ReadingSessionEntity to ReaderInfo domain model.
 */
private fun ReadingSessionEntity.toDomain(): ReaderInfo =
    ReaderInfo(
        userId = userId,
        displayName = userDisplayName,
        avatarType = userAvatarType,
        avatarValue = userAvatarValue,
        avatarColor = userAvatarColor,
        isCurrentlyReading = isCurrentlyReading,
        currentProgress = currentProgress,
        startedAt = kotlin.time.Instant.fromEpochMilliseconds(startedAt).toString(),
        finishedAt = finishedAt?.let { kotlin.time.Instant.fromEpochMilliseconds(it).toString() },
        completionCount = completionCount,
    )

/**
 * Convert ReaderSummary API model to ReadingSessionEntity for caching.
 */
private fun ReaderSummary.toEntity(bookId: String, updatedAt: Long): ReadingSessionEntity =
    ReadingSessionEntity(
        id = "$bookId-$userId",
        bookId = bookId,
        oduserId = userId,
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
 * Parse ISO timestamp string to epoch milliseconds.
 */
private fun parseTimestampToEpoch(iso: String): Long {
    return try {
        kotlin.time.Instant.parse(iso).toEpochMilliseconds()
    } catch (e: Exception) {
        currentEpochMilliseconds()
    }
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
