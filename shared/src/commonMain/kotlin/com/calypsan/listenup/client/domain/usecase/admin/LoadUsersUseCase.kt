package com.calypsan.listenup.client.domain.usecase.admin

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.suspendRunCatching
import com.calypsan.listenup.client.domain.model.AdminUserInfo
import com.calypsan.listenup.client.domain.repository.AdminRepository

/**
 * Loads all approved users.
 */
open class LoadUsersUseCase(
    private val adminRepository: AdminRepository,
) {
    open suspend operator fun invoke(): Result<List<AdminUserInfo>> =
        suspendRunCatching { adminRepository.getUsers() }
}
