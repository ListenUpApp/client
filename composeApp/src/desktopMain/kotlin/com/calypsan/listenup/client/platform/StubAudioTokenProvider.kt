package com.calypsan.listenup.client.platform

import com.calypsan.listenup.client.domain.repository.AuthSession
import com.calypsan.listenup.client.playback.AudioTokenProvider
import kotlinx.coroutines.runBlocking

/**
 * Desktop implementation of [AudioTokenProvider].
 *
 * Uses the existing auth session to provide tokens for authenticated audio streaming.
 * Desktop playback will be implemented in a future phase using VLCJ.
 */
class DesktopAudioTokenProvider(
    private val authSession: AuthSession,
) : AudioTokenProvider {
    override suspend fun prepareForPlayback() {
        // Token refresh is handled by AuthSession
        // No additional preparation needed
    }

    override fun getToken(): String? =
        runBlocking {
            authSession.getAccessToken()?.value
        }
}
