package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.core.map
import com.calypsan.listenup.client.data.remote.ReaderSummary
import com.calypsan.listenup.client.data.remote.SessionApiContract
import com.calypsan.listenup.client.domain.model.BookReadersResult
import com.calypsan.listenup.client.domain.model.ReaderInfo
import com.calypsan.listenup.client.domain.model.SessionSummary
import com.calypsan.listenup.client.domain.repository.SessionRepository

/**
 * Implementation of SessionRepository using SessionApiContract.
 *
 * Wraps session API calls and converts data layer types to domain models.
 *
 * @property sessionApi API client for session operations
 */
class SessionRepositoryImpl(
    private val sessionApi: SessionApiContract,
) : SessionRepository {
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
