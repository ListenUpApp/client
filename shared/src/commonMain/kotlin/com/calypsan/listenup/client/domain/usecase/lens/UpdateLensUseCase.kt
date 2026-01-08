package com.calypsan.listenup.client.domain.usecase.lens

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.suspendRunCatching
import com.calypsan.listenup.client.core.validationError
import com.calypsan.listenup.client.domain.model.Lens
import com.calypsan.listenup.client.domain.repository.LensRepository
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Updates an existing lens's name and description.
 *
 * Validates that the name is not blank before calling the repository.
 * Empty descriptions are converted to null.
 *
 * Usage:
 * ```kotlin
 * val result = updateLensUseCase(
 *     lensId = "lens-123",
 *     name = "Updated Name",
 *     description = "New description"
 * )
 * ```
 */
open class UpdateLensUseCase(
    private val lensRepository: LensRepository,
) {
    /**
     * Update an existing lens.
     *
     * @param lensId The ID of the lens to update
     * @param name The new lens name (required, will be trimmed)
     * @param description Optional new description (empty strings converted to null)
     * @return Result containing the updated lens or a failure
     */
    open suspend operator fun invoke(
        lensId: String,
        name: String,
        description: String?,
    ): Result<Lens> {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            return validationError("Lens name is required")
        }

        val trimmedDescription = description?.trim()?.takeIf { it.isNotEmpty() }

        logger.info { "Updating lens $lensId: $trimmedName" }

        return suspendRunCatching {
            lensRepository.updateLens(lensId, trimmedName, trimmedDescription)
        }
    }
}
