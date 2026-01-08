package com.calypsan.listenup.client.domain.usecase.admin

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.suspendRunCatching
import com.calypsan.listenup.client.domain.repository.AdminRepository

/**
 * Deletes an existing user.
 */
open class DeleteUserUseCase(
    private val adminRepository: AdminRepository,
) {
    open suspend operator fun invoke(userId: String): Result<Unit> =
        suspendRunCatching { adminRepository.deleteUser(userId) }
}
