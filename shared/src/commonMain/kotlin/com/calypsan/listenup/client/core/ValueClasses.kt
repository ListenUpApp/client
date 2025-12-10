package com.calypsan.listenup.client.core

// TODO: Consider migrating to value classes when @JvmInline becomes available in KMP commonMain
//  Currently @JvmInline annotation is JVM-specific and not available for Native/iOS targets
//  in commonMain (Kotlin 2.3.0-Beta2). Using data classes provides type safety with minimal overhead.
//  Track: https://youtrack.jetbrains.com/issue/KT-46768

/**
 * Type-safe wrapper for PASETO access tokens.
 * Prevents accidental logging or misuse of sensitive credentials.
 */
data class AccessToken(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "Access token cannot be blank" }
    }
}

/**
 * Type-safe wrapper for refresh tokens.
 * Prevents accidental logging or misuse of sensitive credentials.
 */
data class RefreshToken(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "Refresh token cannot be blank" }
    }
}

/**
 * Type-safe wrapper for server URLs with built-in validation and normalization.
 *
 * Features:
 * - Validates URL format (must start with http:// or https://)
 * - Normalizes by removing trailing slashes
 *
 * Examples:
 * ```kotlin
 * ServerUrl("https://api.example.com")     // Valid
 * ServerUrl("https://api.example.com/")    // Valid, trailing slash removed
 * ServerUrl("api.example.com")             // Invalid, throws exception
 * ServerUrl("")                            // Invalid, throws exception
 * ```
 */
data class ServerUrl(
    private val _value: String,
) {
    /**
     * The normalized URL value with trailing slash removed.
     */
    val value: String
        get() = _value.trimEnd('/')

    init {
        require(_value.isNotBlank()) { "Server URL cannot be blank" }
        require(_value.startsWith("http://") || _value.startsWith("https://")) {
            "Server URL must start with http:// or https://, got: $_value"
        }
    }

    override fun toString(): String = value
}
