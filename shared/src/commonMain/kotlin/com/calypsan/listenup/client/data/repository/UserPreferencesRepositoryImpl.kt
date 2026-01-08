package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.data.remote.UserPreferencesApiContract
import com.calypsan.listenup.client.data.remote.UserPreferencesRequest
import com.calypsan.listenup.client.domain.repository.UserPreferences
import com.calypsan.listenup.client.domain.repository.UserPreferencesRepository
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Implementation of UserPreferencesRepository that wraps UserPreferencesApiContract.
 *
 * Maps data layer responses to domain types.
 *
 * @property userPreferencesApi Data layer API for user preferences operations
 */
class UserPreferencesRepositoryImpl(
    private val userPreferencesApi: UserPreferencesApiContract,
) : UserPreferencesRepository {
    override suspend fun getPreferences(): Result<UserPreferences> =
        when (val result = userPreferencesApi.getPreferences()) {
            is com.calypsan.listenup.client.core.Result.Success -> {
                Success(
                    UserPreferences(
                        defaultPlaybackSpeed = result.data.defaultPlaybackSpeed,
                        defaultSkipForwardSec = result.data.defaultSkipForwardSec,
                        defaultSkipBackwardSec = result.data.defaultSkipBackwardSec,
                        defaultSleepTimerMin = result.data.defaultSleepTimerMin,
                        shakeToResetSleepTimer = result.data.shakeToResetSleepTimer,
                    ),
                )
            }

            is Failure -> {
                Failure(exception = result.exception, message = result.message, errorCode = result.errorCode)
            }
        }

    override suspend fun setDefaultPlaybackSpeed(speed: Float): Result<Unit> =
        syncSetting(UserPreferencesRequest(defaultPlaybackSpeed = speed))

    override suspend fun setDefaultSkipForwardSec(seconds: Int): Result<Unit> =
        syncSetting(UserPreferencesRequest(defaultSkipForwardSec = seconds))

    override suspend fun setDefaultSkipBackwardSec(seconds: Int): Result<Unit> =
        syncSetting(UserPreferencesRequest(defaultSkipBackwardSec = seconds))

    override suspend fun setDefaultSleepTimerMin(minutes: Int?): Result<Unit> =
        syncSetting(UserPreferencesRequest(defaultSleepTimerMin = minutes))

    override suspend fun setShakeToResetSleepTimer(enabled: Boolean): Result<Unit> =
        syncSetting(UserPreferencesRequest(shakeToResetSleepTimer = enabled))

    private suspend fun syncSetting(request: UserPreferencesRequest): Result<Unit> =
        when (val result = userPreferencesApi.updatePreferences(request)) {
            is com.calypsan.listenup.client.core.Result.Success -> {
                Success(Unit)
            }

            is Failure -> {
                logger.warn { "Failed to sync preference: ${result.message}" }
                Failure(exception = result.exception, message = result.message, errorCode = result.errorCode)
            }
        }
}
