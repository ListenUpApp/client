package com.calypsan.listenup.client.domain.usecase.admin

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.suspendRunCatching
import com.calypsan.listenup.client.domain.model.ServerSettings
import com.calypsan.listenup.client.domain.repository.AdminRepository

/**
 * Updates server settings.
 *
 * Allows admins to toggle server-wide settings like the inbox workflow.
 */
open class UpdateServerSettingsUseCase(
    private val adminRepository: AdminRepository,
) {
    open suspend operator fun invoke(inboxEnabled: Boolean): Result<ServerSettings> =
        suspendRunCatching {
            adminRepository.updateServerSettings(inboxEnabled)
        }
}
