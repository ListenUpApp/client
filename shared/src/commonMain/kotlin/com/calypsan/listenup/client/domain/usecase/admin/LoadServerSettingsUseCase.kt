package com.calypsan.listenup.client.domain.usecase.admin

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.suspendRunCatching
import com.calypsan.listenup.client.domain.model.ServerSettings
import com.calypsan.listenup.client.domain.repository.AdminRepository

/**
 * Loads server settings.
 *
 * Returns current server-wide settings including inbox workflow status.
 */
open class LoadServerSettingsUseCase(
    private val adminRepository: AdminRepository,
) {
    open suspend operator fun invoke(): Result<ServerSettings> =
        suspendRunCatching {
            adminRepository.getServerSettings()
        }
}
