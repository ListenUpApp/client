package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.data.local.db.OperationType
import com.calypsan.listenup.client.domain.model.PendingOperationType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Guards the parity contract relied on by [PendingOperationRepositoryImpl.toDomain]
 * (which uses `PendingOperationType.valueOf(operationType.name)`). If a future
 * contributor adds an entry to [OperationType] without a matching entry in
 * [PendingOperationType], this test fails at build time — preventing the runtime
 * `IllegalArgumentException` that would otherwise surface only when a real pending
 * op of the new type is read back from the database.
 */
class PendingOperationTypeParityTest {
    @Test
    fun `every OperationType maps to a PendingOperationType of the same name`() {
        OperationType.entries.forEach { dataType ->
            val domainType = PendingOperationType.valueOf(dataType.name)
            assertEquals(dataType.name, domainType.name)
        }
    }

    @Test
    fun `every PendingOperationType maps to an OperationType of the same name`() {
        PendingOperationType.entries.forEach { domainType ->
            val dataType = OperationType.valueOf(domainType.name)
            assertEquals(domainType.name, dataType.name)
        }
    }
}
