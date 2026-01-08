package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.core.suspendRunCatching
import com.calypsan.listenup.client.data.remote.ProfileApiContract
import com.calypsan.listenup.client.data.remote.model.FullProfileResponse
import com.calypsan.listenup.client.data.remote.model.LensSummaryResponse
import com.calypsan.listenup.client.data.remote.model.RecentBookResponse
import com.calypsan.listenup.client.domain.model.ProfileLensSummary
import com.calypsan.listenup.client.domain.model.ProfileRecentBook
import com.calypsan.listenup.client.domain.model.UserProfile
import com.calypsan.listenup.client.domain.repository.ProfileRepository

/**
 * Implementation of ProfileRepository using ProfileApiContract.
 *
 * Wraps profile API calls and converts data layer types to domain models.
 *
 * @property profileApi API client for profile operations
 */
class ProfileRepositoryImpl(
    private val profileApi: ProfileApiContract,
) : ProfileRepository {
    override suspend fun getUserProfile(userId: String): Result<UserProfile> =
        suspendRunCatching {
            when (val result = profileApi.getUserProfile(userId)) {
                is Success -> result.data.toDomain()
                is Failure -> throw result.exception ?: Exception(result.message)
            }
        }
}

// ═══════════════════════════════════════════════════════════════════════════
// CONVERSION FUNCTIONS
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Convert FullProfileResponse API model to UserProfile domain model.
 */
private fun FullProfileResponse.toDomain(): UserProfile =
    UserProfile(
        userId = userId,
        displayName = displayName,
        avatarType = avatarType,
        avatarValue = avatarValue,
        avatarColor = avatarColor,
        tagline = tagline,
        totalListenTimeMs = totalListenTimeMs,
        booksFinished = booksFinished,
        currentStreak = currentStreak,
        longestStreak = longestStreak,
        recentBooks = recentBooks.map { it.toDomain() },
        publicLenses = publicLenses.map { it.toDomain() },
    )

/**
 * Convert RecentBookResponse to ProfileRecentBook domain model.
 */
private fun RecentBookResponse.toDomain(): ProfileRecentBook =
    ProfileRecentBook(
        bookId = bookId,
        title = title,
        coverPath = coverPath,
    )

/**
 * Convert LensSummaryResponse to ProfileLensSummary domain model.
 */
private fun LensSummaryResponse.toDomain(): ProfileLensSummary =
    ProfileLensSummary(
        id = id,
        name = name,
        bookCount = bookCount,
    )
