package com.calypsan.listenup.client.data.remote

import com.calypsan.listenup.client.core.AccessToken
import com.calypsan.listenup.client.core.RefreshToken
import com.calypsan.listenup.client.core.ServerUrl
import com.calypsan.listenup.client.data.repository.SettingsRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

private val logger = KotlinLogging.logger {}

/**
 * iOS implementation of streaming HTTP client factory.
 *
 * Configures Darwin (URLSession) engine with infinite timeouts for SSE/WebSocket connections.
 *
 * This prevents URLSession's default timeouts from killing long-lived connections.
 */
internal actual suspend fun createStreamingHttpClient(
    serverUrl: ServerUrl,
    settingsRepository: SettingsRepository,
    authApi: AuthApiContract,
): HttpClient =
    HttpClient(Darwin) {
        // Configure Darwin engine with infinite timeouts for streaming
        engine {
            configureRequest {
                // Disable timeout for streaming requests
                setTimeoutInterval(Double.POSITIVE_INFINITY)
            }

            configureSession {
                // Use background session configuration for long-lived connections
                timeoutIntervalForRequest = Double.POSITIVE_INFINITY
                timeoutIntervalForResource = Double.POSITIVE_INFINITY
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

/**
 * iOS implementation of unauthenticated streaming HTTP client factory.
 *
 * Configures Darwin (URLSession) engine with infinite timeouts for SSE connections,
 * without authentication. Used for endpoints like registration status
 * that don't require auth tokens.
 */
internal actual fun createUnauthenticatedStreamingHttpClient(
    serverUrl: ServerUrl,
): HttpClient =
    HttpClient(Darwin) {
        // Configure Darwin engine with infinite timeouts for streaming
        engine {
            configureRequest {
                // Disable timeout for streaming requests
                setTimeoutInterval(Double.POSITIVE_INFINITY)
            }

            configureSession {
                // Use background session configuration for long-lived connections
                timeoutIntervalForRequest = Double.POSITIVE_INFINITY
                timeoutIntervalForResource = Double.POSITIVE_INFINITY
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

        // NO Auth plugin - this is intentionally unauthenticated

        defaultRequest {
            url(serverUrl.value)
            contentType(ContentType.Application.Json)
        }
    }
