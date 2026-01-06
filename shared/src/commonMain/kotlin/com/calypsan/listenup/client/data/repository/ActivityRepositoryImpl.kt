package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.data.local.db.ActivityDao
import com.calypsan.listenup.client.data.local.db.ActivityEntity
import com.calypsan.listenup.client.domain.model.Activity
import com.calypsan.listenup.client.domain.repository.ActivityRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Implementation of ActivityRepository using Room.
 *
 * Wraps ActivityDao and converts entities to domain models.
 *
 * @property dao Room DAO for activity operations
 */
class ActivityRepositoryImpl(
    private val dao: ActivityDao,
) : ActivityRepository {
    override fun observeRecent(limit: Int): Flow<List<Activity>> =
        dao.observeRecent(limit).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getOlderThan(beforeMs: Long, limit: Int): List<Activity> =
        dao.getOlderThan(beforeMs, limit).map { it.toDomain() }

    override suspend fun getNewestTimestamp(): Long? =
        dao.getNewestTimestamp()

    override suspend fun count(): Int =
        dao.count()

    override suspend fun upsertAll(activities: List<Activity>) {
        dao.upsertAll(activities.map { it.toEntity() })
    }
}

/**
 * Convert ActivityEntity to Activity domain model.
 */
private fun ActivityEntity.toDomain(): Activity =
    Activity(
        id = id,
        type = type,
        userId = userId,
        createdAtMs = createdAt,
        user = Activity.ActivityUser(
            displayName = userDisplayName,
            avatarColor = userAvatarColor,
            avatarType = userAvatarType,
            avatarValue = userAvatarValue,
        ),
        book = if (bookId != null && bookTitle != null) {
            Activity.ActivityBook(
                id = bookId,
                title = bookTitle,
                authorName = bookAuthorName,
                coverPath = bookCoverPath,
            )
        } else {
            null
        },
        isReread = isReread,
        durationMs = durationMs,
        milestoneValue = milestoneValue,
        milestoneUnit = milestoneUnit,
        lensId = lensId,
        lensName = lensName,
    )

/**
 * Convert Activity domain model to ActivityEntity for persistence.
 */
private fun Activity.toEntity(): ActivityEntity =
    ActivityEntity(
        id = id,
        userId = userId,
        type = type,
        createdAt = createdAtMs,
        userDisplayName = user.displayName,
        userAvatarColor = user.avatarColor,
        userAvatarType = user.avatarType,
        userAvatarValue = user.avatarValue,
        bookId = book?.id,
        bookTitle = book?.title,
        bookAuthorName = book?.authorName,
        bookCoverPath = book?.coverPath,
        isReread = isReread,
        durationMs = durationMs,
        milestoneValue = milestoneValue,
        milestoneUnit = milestoneUnit,
        lensId = lensId,
        lensName = lensName,
    )
