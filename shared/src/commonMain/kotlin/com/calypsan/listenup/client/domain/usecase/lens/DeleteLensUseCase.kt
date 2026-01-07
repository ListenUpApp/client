package com.calypsan.listenup.client.domain.usecase.lens

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.suspendRunCatching
import com.calypsan.listenup.client.domain.repository.LensRepository
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Deletes a lens by ID.
 *
 * Only the lens owner can delete a lens. The server will return an error
 * if the user doesn't have permission.
 *
 * Usage:
 * ```kotlin
 * val result = deleteLensUseCase(lensId = "lens-123")
 * ```
 */
open class DeleteLensUseCase(
    private val lensRepository: LensRepository,
) {
    /**
     * Delete a lens.
     *
     * @param lensId The ID of the lens to delete
     * @return Result indicating success or failure
     */
    open suspend operator fun invoke(lensId: String): Result<Unit> {
        logger.info { "Deleting lens $lensId" }

        return suspendRunCatching {
            lensRepository.deleteLens(lensId)
        }
    }
}
