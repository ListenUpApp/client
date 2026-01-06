package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.data.local.db.UserDao
import com.calypsan.listenup.client.data.local.db.UserEntity
import com.calypsan.listenup.client.domain.model.User
import com.calypsan.listenup.client.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Implementation of UserRepository that uses Room for persistence.
 *
 * Wraps UserDao and converts entities to domain models, keeping
 * persistence concerns in the data layer while exposing clean
 * domain types to ViewModels.
 *
 * @property userDao Room DAO for user operations
 */
class UserRepositoryImpl(
    private val userDao: UserDao,
) : UserRepository {
    override fun observeCurrentUser(): Flow<User?> =
        userDao.observeCurrentUser().map { it?.toDomain() }

    override fun observeIsAdmin(): Flow<Boolean> =
        userDao.observeCurrentUser().map { user ->
            user?.isRoot == true
        }

    override suspend fun getCurrentUser(): User? =
        userDao.getCurrentUser()?.toDomain()
}

/**
 * Convert UserEntity to User domain model.
 *
 * Maps isRoot to isAdmin for cleaner domain semantics.
 */
private fun UserEntity.toDomain(): User =
    User(
        id = id,
        email = email,
        displayName = displayName,
        firstName = firstName,
        lastName = lastName,
        isAdmin = isRoot,
        avatarType = avatarType,
        avatarValue = avatarValue,
        avatarColor = avatarColor,
        tagline = tagline,
        createdAtMs = createdAt,
        updatedAtMs = updatedAt,
    )
