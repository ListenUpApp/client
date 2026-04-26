package com.calypsan.listenup.client.presentation.sync

import com.calypsan.listenup.client.domain.model.PendingOperationType
import kotlin.test.Test
import kotlin.test.assertNotEquals

/**
 * Guards the exhaustiveness contract of [describeEntityOp] and [describeGlobalOp].
 *
 * Both helpers use `else ->` fallbacks after being split from a single exhaustive `when`
 * (Task 2 refactor). A future contributor adding a new [PendingOperationType] entry will
 * silently receive the generic "Syncing" description at runtime. This test catches the
 * omission at build time by asserting that every known entry produces a description that
 * is not the generic fallback.
 *
 * The test calls [describe] directly (file-level `internal` function in the same package),
 * bypassing ViewModel construction entirely.
 */
class SyncIndicatorViewModelTest {
    @Test
    fun `describe handles every PendingOperationType without falling through to default`() {
        PendingOperationType.entries.forEach { type ->
            val description = type.describe(entityName = "test-entity")
            assertNotEquals(
                "Syncing",
                description,
                "PendingOperationType.$type fell through to the generic 'Syncing' default — " +
                    "add it to describeEntityOp or describeGlobalOp in SyncIndicatorViewModel.kt",
            )
        }
    }
}
