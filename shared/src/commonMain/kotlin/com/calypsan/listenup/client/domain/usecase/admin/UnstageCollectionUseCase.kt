package com.calypsan.listenup.client.domain.usecase.admin

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.suspendRunCatching
import com.calypsan.listenup.client.domain.repository.AdminRepository

/**
 * Removes a staged collection from an inbox book.
 *
 * The book will no longer be added to this collection when released.
 */
open class UnstageCollectionUseCase(
    private val adminRepository: AdminRepository,
) {
    open suspend operator fun invoke(
        bookId: String,
        collectionId: String,
    ): Result<Unit> =
        suspendRunCatching {
            adminRepository.unstageCollection(bookId, collectionId)
        }
}
