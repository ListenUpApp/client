package com.calypsan.listenup.client.domain.repository

/**
 * Repository for downloading user avatar images from the server and persisting them locally.
 *
 * Owns its own [kotlinx.coroutines.CoroutineScope] so callers can invoke [queueAvatarDownload]
 * from suspend contexts without launching unstructured child coroutines on the caller's scope
 * — the repository is the single structured-concurrency boundary for this work.
 *
 * Mirrors [CoverDownloadRepository] for avatars. No `touchUpdatedAt` analogue — avatars paint
 * from a stable file path; no Room-invalidation signal is needed.
 */
interface AvatarDownloadRepository {
    /**
     * Request that the avatar for [userId] be downloaded.
     *
     * Returns immediately; the download happens on the repository's internal scope.
     * If the avatar is already present locally, no work is done. On failure, the error
     * is logged and dropped — the next SSE session-started event or manual refresh will retry.
     *
     * @param userId the user whose avatar should be fetched.
     */
    fun queueAvatarDownload(userId: String)
}
