package com.calypsan.listenup.client.domain.usecase.admin

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.suspendRunCatching
import com.calypsan.listenup.client.domain.repository.AdminRepository

/**
 * Revokes/deletes an invite.
 */
open class RevokeInviteUseCase(
    private val adminRepository: AdminRepository,
) {
    open suspend operator fun invoke(inviteId: String): Result<Unit> =
        suspendRunCatching { adminRepository.deleteInvite(inviteId) }
}
