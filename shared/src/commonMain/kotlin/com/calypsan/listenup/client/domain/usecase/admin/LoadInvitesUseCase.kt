package com.calypsan.listenup.client.domain.usecase.admin

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.suspendRunCatching
import com.calypsan.listenup.client.domain.model.InviteInfo
import com.calypsan.listenup.client.domain.repository.AdminRepository

/**
 * Loads all invites (both pending and claimed).
 */
open class LoadInvitesUseCase(
    private val adminRepository: AdminRepository,
) {
    open suspend operator fun invoke(): Result<List<InviteInfo>> =
        suspendRunCatching { adminRepository.getInvites() }
}
