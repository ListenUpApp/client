package com.calypsan.listenup.client.data.remote

import com.calypsan.listenup.client.core.AccessToken
import com.calypsan.listenup.client.core.RefreshToken
import com.calypsan.listenup.client.core.ServerUrl
import com.calypsan.listenup.client.data.repository.SettingsRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

/**
 * Android implementation of streaming HTTP client factory.
 *
 * Configures OkHttp engine with infinite timeouts for SSE/WebSocket connections:
 * - connectTimeout: 0 (infinite)
 * - readTimeout: 0 (infinite)
 * - writeTimeout: 0 (infinite)
 *
 * This prevents the default 10-second OkHttp timeouts from killing long-lived connections.
 */
internal actual suspend fun createStreamingHttpClient(
    serverUrl: ServerUrl,
    settingsRepository: SettingsRepository,
    authApi: AuthApiContract,
): HttpClient =
    HttpClient(OkHttp) {
        // Configure OkHttp engine with infinite timeouts for streaming
        engine {
            config {
                // 0 = infinite timeout in OkHttp
                connectTimeout(0, TimeUnit.SECONDS)
                readTimeout(0, TimeUnit.SECONDS)
                writeTimeout(0, TimeUnit.SECONDS)
            }
        }

        install(ContentNegotiation) {
            json(
                Json {
                    prettyPrint = false
                    isLenient = false
                    ignoreUnknownKeys = true
                },
            )
        }

        // NO HttpTimeout plugin - we're controlling timeouts at engine level

        install(Auth) {
            bearer {
                loadTokens {
                    val access = settingsRepository.getAccessToken()?.value
                    val refresh = settingsRepository.getRefreshToken()?.value

                    if (access != null && refresh != null) {
                        BearerTokens(
                            accessToken = access,
                            refreshToken = refresh,
                        )
                    } else {
                        null
                    }
                }

                refreshTokens {
                    val currentRefreshToken =
                        settingsRepository.getRefreshToken()
                            ?: error("No refresh token available")

                    try {
                        val response = authApi.refresh(currentRefreshToken)

                        settingsRepository.saveAuthTokens(
                            access = AccessToken(response.accessToken),
                            refresh = RefreshToken(response.refreshToken),
                            sessionId = response.sessionId,
                            userId = response.userId,
                        )

                        BearerTokens(
                            accessToken = response.accessToken,
                            refreshToken = response.refreshToken,
                        )
                    } catch (e: Exception) {
                        logger.warn(e) { "Token refresh failed, clearing auth state" }
                        settingsRepository.clearAuthTokens()
                        null
                    }
                }

                sendWithoutRequest { request ->
                    val urlString = request.url.toString()
                    !urlString.contains("/api/auth/")
                }
            }
        }

        defaultRequest {
            url(serverUrl.value)
            contentType(ContentType.Application.Json)
        }
    }
