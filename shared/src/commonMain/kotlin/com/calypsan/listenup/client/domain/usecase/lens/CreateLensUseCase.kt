package com.calypsan.listenup.client.domain.usecase.lens

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.suspendRunCatching
import com.calypsan.listenup.client.core.validationError
import com.calypsan.listenup.client.domain.model.Lens
import com.calypsan.listenup.client.domain.repository.LensRepository
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Creates a new lens with the given name and optional description.
 *
 * Validates that the name is not blank before calling the repository.
 * Empty descriptions are converted to null.
 *
 * Usage:
 * ```kotlin
 * val result = createLensUseCase(
 *     name = "My Reading List",
 *     description = "Books I want to read"
 * )
 * ```
 */
open class CreateLensUseCase(
    private val lensRepository: LensRepository,
) {
    /**
     * Create a new lens.
     *
     * @param name The lens name (required, will be trimmed)
     * @param description Optional description (empty strings converted to null)
     * @return Result containing the created lens or a failure
     */
    open suspend operator fun invoke(
        name: String,
        description: String?,
    ): Result<Lens> {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            return validationError("Lens name is required")
        }

        val trimmedDescription = description?.trim()?.takeIf { it.isNotEmpty() }

        logger.info { "Creating lens: $trimmedName" }

        return suspendRunCatching {
            lensRepository.createLens(trimmedName, trimmedDescription)
        }
    }
}
