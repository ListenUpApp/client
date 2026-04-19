package com.calypsan.listenup.client.data.sync.push

import com.calypsan.listenup.client.core.AppResult
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.data.local.db.EntityType
import com.calypsan.listenup.client.data.local.db.ListenUpDatabase
import com.calypsan.listenup.client.data.local.db.OperationType
import com.calypsan.listenup.client.data.local.db.PendingOperationEntity
import com.calypsan.listenup.client.data.local.db.RoomTransactionRunner
import com.calypsan.listenup.client.test.db.createInMemoryTestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Proves two invariants for `PendingOperationRepository.queue`:
 *
 * 1. Sequential `queue` calls on the same entity coalesce into exactly one row
 *    (the transactional wrap serializes the find+insert window). The stronger
 *    concurrent-coalesce invariant — serialisation against concurrent queue inserts
 *    — is defended structurally by the `atomically` wrap (matching W4 rubric §P-R1
 *    precedent) but cannot be physically reproduced at unit level because Room's
 *    IO-dispatched DAO queries do not participate in the caller's `useWriterConnection`
 *    scope in the in-memory test database. The structural guarantee holds in production
 *    where SQLite's single-writer serialisation enforces it.
 *
 * 2. Calling `queue` from inside an outer `atomically` block is safe — the inner
 *    transaction participates in the outer scope (Room KMP 2.8 full-atomic
 *    semantics, confirmed empirically at plan-write-time). This invariant is
 *    relevant for `ContributorEditRepository`'s three existing call sites that
 *    already invoke `queue` from inside `atomically`.
 *
 * Uses a real in-memory [ListenUpDatabase] and real [RoomTransactionRunner].
 */
class PendingOperationRepositoryCoalesceAtomicityTest {
    private val testHandler = object : OperationHandler<String> {
        override val operationType = OperationType.PLAYBACK_POSITION

        override fun batchKey(payload: String): String? = null

        override fun serializePayload(payload: String): String = payload

        override fun parsePayload(raw: String): String = raw

        override fun tryCoalesce(
            existing: PendingOperationEntity,
            existingPayload: String,
            newPayload: String,
        ): String? = newPayload  // always coalesce — take the newer payload

        override suspend fun execute(
            operation: PendingOperationEntity,
            payload: String,
        ): AppResult<Unit> = Success(Unit)
    }

    private val db: ListenUpDatabase = createInMemoryTestDatabase()

    private fun buildRepo(): PendingOperationRepository {
        val txRunner = RoomTransactionRunner(db)
        return PendingOperationRepository(
            transactionRunner = txRunner,
            dao = db.pendingOperationDao(),
            bookDao = db.bookDao(),
            contributorDao = db.contributorDao(),
            seriesDao = db.seriesDao(),
            shelfDao = db.shelfDao(),
        )
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    @Test
    fun `sequential queue for same entity produces exactly one row`() =
        runTest {
            val repo = buildRepo()

            repo.queue(
                type = OperationType.PLAYBACK_POSITION,
                entityType = EntityType.BOOK,
                entityId = "book-coalesce",
                payload = "payload-a",
                handler = testHandler,
            )
            repo.queue(
                type = OperationType.PLAYBACK_POSITION,
                entityType = EntityType.BOOK,
                entityId = "book-coalesce",
                payload = "payload-b",
                handler = testHandler,
            )

            val rows = db.pendingOperationDao().observeAll().first().filter {
                it.entityId == "book-coalesce" && it.operationType == OperationType.PLAYBACK_POSITION
            }
            assertEquals(1, rows.size, "sequential queue calls should coalesce to exactly one row")
        }

    @Test
    fun `queue called from inside outer atomically participates in outer tx - success path`() =
        runTest {
            val txRunner = RoomTransactionRunner(db)
            val repo = PendingOperationRepository(
                transactionRunner = txRunner,
                dao = db.pendingOperationDao(),
                bookDao = db.bookDao(),
                contributorDao = db.contributorDao(),
                seriesDao = db.seriesDao(),
                shelfDao = db.shelfDao(),
            )

            txRunner.atomically {
                repo.queue(
                    type = OperationType.PLAYBACK_POSITION,
                    entityType = EntityType.BOOK,
                    entityId = "book-nested-success",
                    payload = "payload-nested",
                    handler = testHandler,
                )
            }

            val rows = db.pendingOperationDao().observeAll().first().filter {
                it.entityId == "book-nested-success"
            }
            assertEquals(1, rows.size, "queue called inside outer atomically should commit normally")
        }

    @Test
    fun `queue called from inside outer atomically rolls back when outer throws`() =
        runTest {
            val txRunner = RoomTransactionRunner(db)
            val repo = PendingOperationRepository(
                transactionRunner = txRunner,
                dao = db.pendingOperationDao(),
                bookDao = db.bookDao(),
                contributorDao = db.contributorDao(),
                seriesDao = db.seriesDao(),
                shelfDao = db.shelfDao(),
            )

            val caught = try {
                txRunner.atomically {
                    repo.queue(
                        type = OperationType.PLAYBACK_POSITION,
                        entityType = EntityType.BOOK,
                        entityId = "book-nested-throw",
                        payload = "payload-throw",
                        handler = testHandler,
                    )
                    throw RuntimeException("outer throws after inner queue")
                }
                null
            } catch (e: Exception) {
                e
            }

            assertNotNull(caught, "outer throw must propagate")
            val rows = db.pendingOperationDao().observeAll().first().filter {
                it.entityId == "book-nested-throw"
            }
            assertEquals(0, rows.size, "queue's write must roll back when outer tx throws")
        }
}
