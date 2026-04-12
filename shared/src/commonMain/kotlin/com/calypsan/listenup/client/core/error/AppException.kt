package com.calypsan.listenup.client.core.error

/**
 * Typed marker exception carrying an already-mapped [AppError].
 *
 * Thrown exclusively by the `HttpResponseValidator` boundary (see
 * `installListenUpErrorHandling`) so that downstream code can catch a single exception
 * type and read `exception.error` to get the categorised [AppError] — no re-derivation,
 * no repeated [ErrorMapper] calls, no lossy round-trip through message strings.
 *
 * Catch shape:
 * ```kotlin
 * try { api.fetch() } catch (e: AppException) { return AppResult.Failure(e.error) }
 * ```
 *
 * Source: Ktor `handleResponseExceptionWithRequest` pattern — the validator maps once
 * at the boundary, and everyone downstream consumes the already-typed error.
 */
class AppException(
    val error: AppError,
    cause: Throwable? = null,
) : RuntimeException(error.message, cause)
