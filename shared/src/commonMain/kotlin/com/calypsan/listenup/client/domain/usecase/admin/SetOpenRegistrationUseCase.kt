package com.calypsan.listenup.client.domain.usecase.admin

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.suspendRunCatching
import com.calypsan.listenup.client.domain.repository.AdminRepository

/**
 * Enables or disables open registration.
 */
open class SetOpenRegistrationUseCase(
    private val adminRepository: AdminRepository,
) {
    open suspend operator fun invoke(enabled: Boolean): Result<Unit> =
        suspendRunCatching { adminRepository.setOpenRegistration(enabled) }
}
