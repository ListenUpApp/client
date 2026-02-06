package com.calypsan.listenup.client.domain.usecase.admin

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.suspendRunCatching
import com.calypsan.listenup.client.domain.model.ServerSettings
import com.calypsan.listenup.client.domain.repository.AdminRepository

/**
 * Updates server settings.
 *
 * Allows admins to update server-wide settings like the server name
 * and inbox workflow toggle.
 */
open class UpdateServerSettingsUseCase(
    private val adminRepository: AdminRepository,
) {
    /**
     * Update inbox enabled setting only (backwards compatible).
     */
    open suspend operator fun invoke(inboxEnabled: Boolean): Result<ServerSettings> =
        suspendRunCatching {
            adminRepository.updateServerSettings(inboxEnabled = inboxEnabled)
        }

    /**
     * Update server name only.
     */
    open suspend fun updateServerName(serverName: String): Result<ServerSettings> =
        suspendRunCatching {
            adminRepository.updateServerSettings(serverName = serverName)
        }
}
