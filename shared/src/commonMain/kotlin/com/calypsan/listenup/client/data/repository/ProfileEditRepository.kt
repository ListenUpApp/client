package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.IODispatcher
import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.core.currentEpochMilliseconds
import com.calypsan.listenup.client.data.local.db.EntityType
import com.calypsan.listenup.client.data.local.db.OperationType
import com.calypsan.listenup.client.data.local.db.UserDao
import com.calypsan.listenup.client.data.remote.ProfileApiContract
import com.calypsan.listenup.client.data.sync.push.PendingOperationRepositoryContract
import com.calypsan.listenup.client.data.sync.push.ProfileAvatarHandler
import com.calypsan.listenup.client.data.sync.push.ProfileAvatarPayload
import com.calypsan.listenup.client.data.sync.push.ProfileUpdateHandler
import com.calypsan.listenup.client.data.sync.push.ProfileUpdatePayload
import com.calypsan.listenup.client.domain.repository.ProfileEditRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.withContext
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private val logger = KotlinLogging.logger {}

/**
 * Repository for profile editing operations using offline-first pattern.
 *
 * Handles the edit flow:
 * 1. Apply optimistic update to local database
 * 2. Queue operation for server sync via PendingOperationRepository
 * 3. Return success immediately
 *
 * The PushSyncOrchestrator will later:
 * - Send changes to server
 * - Handle conflicts if server version is newer
 */
class ProfileEditRepositoryImpl(
    private val userDao: UserDao,
    private val pendingOperationRepository: PendingOperationRepositoryContract,
    private val profileUpdateHandler: ProfileUpdateHandler,
    private val profileAvatarHandler: ProfileAvatarHandler,
    private val profileApi: ProfileApiContract,
) : ProfileEditRepository {
    /**
     * Update the user's tagline.
     */
    override suspend fun updateTagline(tagline: String?): Result<Unit> =
        withContext(IODispatcher) {
            logger.debug { "Updating tagline (offline-first)" }

            // Get current user
            val user = userDao.getCurrentUser()
            if (user == null) {
                logger.error { "No current user found" }
                return@withContext Failure(Exception("No current user"))
            }

            // Apply optimistic update
            userDao.updateTagline(
                userId = user.id,
                tagline = tagline,
                updatedAt = currentEpochMilliseconds(),
            )

            // Queue operation (coalesces with any pending tagline update)
            val payload = ProfileUpdatePayload(tagline = tagline)
            pendingOperationRepository.queue(
                type = OperationType.PROFILE_UPDATE,
                entityType = EntityType.USER,
                entityId = user.id,
                payload = payload,
                handler = profileUpdateHandler,
            )

            logger.info { "Tagline update queued" }
            Success(Unit)
        }

    /**
     * Upload a new avatar image.
     *
     * Note: Unlike tagline updates, we do NOT apply an optimistic local update here.
     * The new avatar path isn't known until the server processes the upload.
     * The current avatar continues to display until sync completes and the
     * ProfileAvatarHandler updates UserEntity with the server response.
     */
    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun uploadAvatar(
        imageData: ByteArray,
        contentType: String,
    ): Result<Unit> =
        withContext(IODispatcher) {
            logger.debug { "Uploading avatar (offline-first), size=${imageData.size}" }

            // Get current user
            val user = userDao.getCurrentUser()
            if (user == null) {
                logger.error { "No current user found" }
                return@withContext Failure(Exception("No current user"))
            }

            // Do NOT update avatar locally - we don't have the server path yet.
            // The current avatar will continue to display until:
            // 1. ProfileAvatarHandler successfully uploads the image
            // 2. Handler updates UserEntity with the server's response (including the new path)

            // Encode image as Base64 for storage in pending operation
            val imageDataBase64 = Base64.encode(imageData)

            // Queue operation (replaces any pending avatar upload)
            val payload =
                ProfileAvatarPayload(
                    imageDataBase64 = imageDataBase64,
                    contentType = contentType,
                )
            pendingOperationRepository.queue(
                type = OperationType.PROFILE_AVATAR,
                entityType = EntityType.USER,
                entityId = user.id,
                payload = payload,
                handler = profileAvatarHandler,
            )

            logger.info { "Avatar upload queued" }
            Success(Unit)
        }

    /**
     * Revert to auto-generated avatar.
     */
    override suspend fun revertToAutoAvatar(): Result<Unit> =
        withContext(IODispatcher) {
            logger.debug { "Reverting to auto avatar (offline-first)" }

            // Get current user
            val user = userDao.getCurrentUser()
            if (user == null) {
                logger.error { "No current user found" }
                return@withContext Failure(Exception("No current user"))
            }

            // Apply optimistic update
            userDao.updateAvatar(
                userId = user.id,
                avatarType = "auto",
                avatarValue = null,
                avatarColor = user.avatarColor, // Keep existing color
                updatedAt = currentEpochMilliseconds(),
            )

            // Queue operation - use profile update with avatarType = "auto"
            val payload = ProfileUpdatePayload(avatarType = "auto")
            pendingOperationRepository.queue(
                type = OperationType.PROFILE_UPDATE,
                entityType = EntityType.USER,
                entityId = user.id,
                payload = payload,
                handler = profileUpdateHandler,
            )

            logger.info { "Revert to auto avatar queued" }
            Success(Unit)
        }

    /**
     * Update the user's name.
     *
     * Applies optimistic update locally, then queues for server sync.
     */
    override suspend fun updateName(
        firstName: String,
        lastName: String,
    ): Result<Unit> =
        withContext(IODispatcher) {
            logger.debug { "Updating name (offline-first)" }

            // Get current user
            val user = userDao.getCurrentUser()
            if (user == null) {
                logger.error { "No current user found" }
                return@withContext Failure(Exception("No current user"))
            }

            // Apply optimistic update - compute displayName locally
            val displayName = "$firstName $lastName".trim()
            userDao.updateName(
                userId = user.id,
                firstName = firstName,
                lastName = lastName,
                displayName = displayName,
                updatedAt = currentEpochMilliseconds(),
            )

            // Queue operation (coalesces with any pending name update)
            val payload =
                ProfileUpdatePayload(
                    firstName = firstName,
                    lastName = lastName,
                )
            pendingOperationRepository.queue(
                type = OperationType.PROFILE_UPDATE,
                entityType = EntityType.USER,
                entityId = user.id,
                payload = payload,
                handler = profileUpdateHandler,
            )

            logger.info { "Name update queued" }
            Success(Unit)
        }

    /**
     * Change the user's password.
     *
     * This is NOT an offline-first operation - requires immediate server confirmation.
     */
    override suspend fun changePassword(newPassword: String): Result<Unit> =
        withContext(IODispatcher) {
            logger.debug { "Changing password (requires server)" }

            // Password change requires immediate server confirmation
            when (
                val result =
                    profileApi.updateMyProfile(
                        newPassword = newPassword,
                    )
            ) {
                is Success -> {
                    logger.info { "Password changed successfully" }
                    Success(Unit)
                }

                is Failure -> {
                    logger.error { "Password change failed: ${result.message}" }
                    result
                }
            }
        }
}
