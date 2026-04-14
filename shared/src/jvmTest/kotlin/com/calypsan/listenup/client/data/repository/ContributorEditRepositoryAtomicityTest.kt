package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.core.ContributorId
import com.calypsan.listenup.client.core.Timestamp
import com.calypsan.listenup.client.data.local.db.ContributorAliasCrossRef
import com.calypsan.listenup.client.data.local.db.ContributorEntity
import com.calypsan.listenup.client.data.local.db.ListenUpDatabase
import com.calypsan.listenup.client.data.local.db.RoomTransactionRunner
import com.calypsan.listenup.client.data.local.db.SyncState
import com.calypsan.listenup.client.data.remote.ContributorApiContract
import com.calypsan.listenup.client.data.sync.push.ContributorUpdateHandler
import com.calypsan.listenup.client.data.sync.push.MergeContributorHandler
import com.calypsan.listenup.client.data.sync.push.PendingOperationRepositoryContract
import com.calypsan.listenup.client.data.sync.push.UnmergeContributorHandler
import com.calypsan.listenup.client.test.db.createInMemoryTestDatabase
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Proves [ContributorEditRepository]'s three write paths are atomic — when
 * the pending-operation queue step throws (the last write inside each
 * `transactionRunner.atomically { }` block), the prior contributor + alias +
 * book-contributor writes must roll back so the DB never holds a partial edit
 * whose sync-queue entry is missing.
 *
 * This is the carry-over atomicity coverage from W4.2: `ContributorEditRepository`
 * is the heaviest multi-DAO writer in the repository layer but lacked a dedicated
 * regression test. Item C's rewrites increased the surface (alias junction writes
 * joined the atomically block); FU-C2 closes that coverage gap.
 *
 * Uses a real in-memory [ListenUpDatabase] with real [RoomTransactionRunner] so
 * transaction rollback is exercised end-to-end. Handlers are real instances
 * (they're final classes) with mocked [ContributorApiContract] — handlers aren't
 * invoked inside the atomically block, only referenced by the queue call, so the
 * mocked API is never touched during the test.
 */
class ContributorEditRepositoryAtomicityTest {
    private val db: ListenUpDatabase = createInMemoryTestDatabase()

    @AfterTest
    fun tearDown() {
        db.close()
    }

    @Test
    fun `updateContributor rollback when queue throws`() =
        runTest {
            seedContributor(id = "c-1", name = "Original Name")

            val repo = createRepo(pendingOpQueueThrows = true)

            val result =
                runCatching {
                    repo.updateContributor(
                        contributorId = "c-1",
                        update =
                            ContributorUpdateRequest(
                                name = "New Name",
                                aliases = listOf("Alias A", "Alias B"),
                            ),
                    )
                }

            // The throw propagates out of atomically; caller sees the exception.
            assertNotNull(result.exceptionOrNull(), "queue throw must propagate")

            val after = db.contributorDao().getById("c-1")
            assertNotNull(after)
            assertEquals("Original Name", after.name, "name must roll back")
            assertEquals(SyncState.SYNCED, after.syncState, "syncState must roll back")
            assertEquals(
                0,
                db.contributorAliasDao().getForContributor("c-1").size,
                "alias rows must roll back",
            )
        }

    @Test
    fun `mergeContributor rollback when queue throws`() =
        runTest {
            seedContributor(id = "target", name = "Target")
            seedContributor(id = "source", name = "Source Name")

            val repo = createRepo(pendingOpQueueThrows = true)

            val result =
                runCatching {
                    repo.mergeContributor(targetId = "target", sourceId = "source")
                }

            assertNotNull(result.exceptionOrNull(), "queue throw must propagate")

            val target = db.contributorDao().getById("target")
            val source = db.contributorDao().getById("source")
            assertNotNull(target, "target must still exist")
            assertNotNull(source, "source deletion must roll back")
            assertEquals(SyncState.SYNCED, target.syncState, "target syncState must roll back")
            assertEquals(
                0,
                db.contributorAliasDao().getForContributor("target").size,
                "alias additions to target must roll back",
            )
        }

    @Test
    fun `unmergeContributor rollback when queue throws`() =
        runTest {
            seedContributor(id = "c-1", name = "Stephen King")
            db.contributorAliasDao().insertAll(
                listOf(
                    ContributorAliasCrossRef(ContributorId("c-1"), "Richard Bachman"),
                    ContributorAliasCrossRef(ContributorId("c-1"), "John Swithen"),
                ),
            )

            val repo = createRepo(pendingOpQueueThrows = true)

            val result =
                runCatching {
                    repo.unmergeContributor(contributorId = "c-1", aliasName = "Richard Bachman")
                }

            assertNotNull(result.exceptionOrNull(), "queue throw must propagate")

            // Original alias list must be intact.
            val aliases = db.contributorAliasDao().getForContributor("c-1")
            assertEquals(
                setOf("Richard Bachman", "John Swithen"),
                aliases.toSet(),
                "alias removal must roll back",
            )

            // No temp contributor should have been created.
            val tempContributors =
                db
                    .contributorDao()
                    .getAll()
                    .filter { it.id.value.startsWith("temp") }
            assertEquals(0, tempContributors.size, "temp contributor creation must roll back")

            // syncState on c-1 must remain unchanged.
            val contributor = db.contributorDao().getById("c-1")
            assertNotNull(contributor)
            assertEquals(SyncState.SYNCED, contributor.syncState, "syncState must roll back")
        }

    private suspend fun seedContributor(
        id: String,
        name: String,
    ) {
        db.contributorDao().upsert(
            ContributorEntity(
                id = ContributorId(id),
                name = name,
                description = null,
                imagePath = null,
                syncState = SyncState.SYNCED,
                lastModified = Timestamp(1L),
                serverVersion = Timestamp(1L),
                createdAt = Timestamp(1L),
                updatedAt = Timestamp(1L),
            ),
        )
    }

    private fun createRepo(pendingOpQueueThrows: Boolean): ContributorEditRepository {
        val contributorApi: ContributorApiContract = mock()

        val pendingOpRepo: PendingOperationRepositoryContract = mock()
        if (pendingOpQueueThrows) {
            everySuspend {
                pendingOpRepo.queue<Any>(any(), any(), any(), any(), any())
            } throws RuntimeException("boom — pending-op queue failed")
        }

        return ContributorEditRepository(
            transactionRunner = RoomTransactionRunner(db),
            contributorDao = db.contributorDao(),
            contributorAliasDao = db.contributorAliasDao(),
            bookContributorDao = db.bookContributorDao(),
            pendingOperationRepository = pendingOpRepo,
            contributorUpdateHandler = ContributorUpdateHandler(contributorApi),
            mergeContributorHandler = MergeContributorHandler(contributorApi),
            unmergeContributorHandler = UnmergeContributorHandler(contributorApi),
        )
    }
}
