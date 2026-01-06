package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.data.local.db.ActiveSessionDao
import com.calypsan.listenup.client.data.local.db.ActiveSessionWithDetails
import com.calypsan.listenup.client.domain.model.ActiveSession
import com.calypsan.listenup.client.domain.repository.ActiveSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Implementation of ActiveSessionRepository using Room.
 *
 * Wraps ActiveSessionDao and converts entities to domain models.
 *
 * @property dao Room DAO for active session operations
 */
class ActiveSessionRepositoryImpl(
    private val dao: ActiveSessionDao,
) : ActiveSessionRepository {
    override fun observeActiveSessions(currentUserId: String): Flow<List<ActiveSession>> =
        dao.observeActiveSessions(currentUserId).map { sessions ->
            sessions.map { it.toDomain() }
        }

    override fun observeActiveCount(currentUserId: String): Flow<Int> =
        dao.observeActiveCount(currentUserId)
}

/**
 * Convert ActiveSessionWithDetails to ActiveSession domain model.
 */
private fun ActiveSessionWithDetails.toDomain(): ActiveSession =
    ActiveSession(
        sessionId = sessionId,
        userId = userId,
        bookId = bookId,
        startedAtMs = startedAt,
        updatedAtMs = updatedAt,
        user = ActiveSession.SessionUser(
            displayName = displayName,
            avatarType = avatarType,
            avatarValue = avatarValue,
            avatarColor = avatarColor,
        ),
        book = ActiveSession.SessionBook(
            id = bookId,
            title = title,
            coverBlurHash = coverBlurHash,
            authorName = authorName,
        ),
    )
