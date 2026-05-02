package com.calypsan.listenup.client.data.repository

import app.cash.turbine.turbineScope
import com.calypsan.listenup.client.core.AppResult
import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.core.Timestamp
import com.calypsan.listenup.client.data.local.db.AudioFileDao
import com.calypsan.listenup.client.data.local.db.AudioFileEntity
import com.calypsan.listenup.client.data.local.db.BookDao
import com.calypsan.listenup.client.data.local.db.BookEntity
import com.calypsan.listenup.client.data.local.db.BookGenreCrossRef
import com.calypsan.listenup.client.data.local.db.BookTagCrossRef
import com.calypsan.listenup.client.data.local.db.GenreEntity
import com.calypsan.listenup.client.data.local.db.ListenUpDatabase
import com.calypsan.listenup.client.data.local.db.SyncState
import com.calypsan.listenup.client.data.local.db.TagEntity
import com.calypsan.listenup.client.data.local.db.RoomTransactionRunner
import com.calypsan.listenup.client.data.local.db.TransactionRunner
import com.calypsan.listenup.client.data.remote.GenreApiContract
import com.calypsan.listenup.client.data.remote.TagApiContract
import com.calypsan.listenup.client.data.sync.SyncManagerContract
import com.calypsan.listenup.client.domain.repository.ImageStorage
import com.calypsan.listenup.client.test.db.createInMemoryTestDatabase
import com.calypsan.listenup.client.test.db.passThroughTransactionRunner
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Seam-level tests for [BookRepositoryImpl]'s new BookListItem/BookDetail surface
 * (W6 Phase G Task 3).
 *
 * Uses a real in-memory [ListenUpDatabase] with real DAOs and real
 * [GenreRepositoryImpl] + [TagRepositoryImpl] so the combine() composition in
 * [BookRepositoryImpl.observeBookDetail] is exercised end-to-end. Mocks only
 * the non-DB collaborators ([ImageStorage], [SyncManagerContract],
 * [GenreApiContract], [TagApiContract]).
 */
class BookRepositoryImplTest {
    private val db: ListenUpDatabase = createInMemoryTestDatabase()
    private val bookDao = db.bookDao()
    private val chapterDao = db.chapterDao()
    private val genreDao = db.genreDao()
    private val tagDao = db.tagDao()

    private val imageStorage: ImageStorage =
        mock {
            every { exists(any()) } returns false
            every { getCoverPath(any()) } returns ""
        }
    private val syncManager: SyncManagerContract = mock()
    private val genreApi: GenreApiContract = mock()
    private val tagApi: TagApiContract = mock()

    private val genreRepository = GenreRepositoryImpl(genreDao, genreApi)
    private val tagRepository = TagRepositoryImpl(tagDao, tagApi)

    // Real audioFileDao + a pass-through TransactionRunner for the real-DB tests.
    // The upsertWithAudioFiles atomicity tests use mocks and live in a separate class below.
    private val audioFileDao = db.audioFileDao()
    private val transactionRunner: TransactionRunner = passThroughTransactionRunner()

    private val repository =
        BookRepositoryImpl(
            bookDao = bookDao,
            chapterDao = chapterDao,
            audioFileDao = audioFileDao,
            transactionRunner = transactionRunner,
            syncManager = syncManager,
            imageStorage = imageStorage,
            genreRepository = genreRepository,
            tagRepository = tagRepository,
        )

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private fun makeBookEntity(
        id: String,
        title: String = "Book $id",
        createdAt: Long = 1_700_000_000_000L,
    ): BookEntity =
        BookEntity(
            id = BookId(id),
            title = title,
            sortTitle = title,
            subtitle = null,
            coverUrl = null,
            coverBlurHash = null,
            dominantColor = null,
            darkMutedColor = null,
            vibrantColor = null,
            totalDuration = 0L,
            description = null,
            publishYear = null,
            publisher = null,
            language = null,
            isbn = null,
            asin = null,
            abridged = false,
            syncState = SyncState.SYNCED,
            lastModified = Timestamp(createdAt),
            serverVersion = Timestamp(createdAt),
            createdAt = Timestamp(createdAt),
            updatedAt = Timestamp(createdAt),
        )

    private fun makeGenreEntity(
        id: String,
        name: String,
        slug: String = name.lowercase(),
    ): GenreEntity =
        GenreEntity(
            id = id,
            name = name,
            slug = slug,
            path = "/$slug",
            bookCount = 0,
            parentId = null,
            depth = 0,
            sortOrder = 0,
        )

    private fun makeTagEntity(
        id: String,
        slug: String,
    ): TagEntity =
        TagEntity(
            id = id,
            slug = slug,
            bookCount = 0,
            createdAt = Timestamp(1_700_000_000_000L),
        )

    @Test
    fun `observeBookDetail emits null then BookDetail when row inserted`() =
        runTest {
            turbineScope {
                val turbine = repository.observeBookDetail("book-1").testIn(backgroundScope)

                // Initial emission: null because row doesn't exist yet.
                assertNull(turbine.awaitItem())

                bookDao.upsert(makeBookEntity(id = "book-1", title = "Stormlight"))

                val first = turbine.awaitItem()
                assertNotNull(first)
                assertEquals("Stormlight", first.title)

                turbine.cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `observeBookDetail re-emits when genres change for the book`() =
        runTest {
            bookDao.upsert(makeBookEntity(id = "book-1"))

            turbineScope {
                val turbine = repository.observeBookDetail("book-1").testIn(backgroundScope)

                val initial = turbine.awaitItem()
                assertNotNull(initial)
                assertTrue(initial.genres.isEmpty())

                // Insert genre + cross-ref atomically so Room's invalidation tracker
                // fires once at commit, after both rows are present. Without the
                // transaction, two separate invalidations expose an intermediate
                // state (genre row inserted, cross-ref row not yet) where the
                // observer re-emits with empty genres — the cause of drift #23's
                // intermittent flake. Real Room transaction is required; the
                // class-field passthrough TransactionRunner does not invoke Room's
                // invalidation-batching machinery.
                RoomTransactionRunner(db).atomically {
                    genreDao.upsert(makeGenreEntity(id = "g-1", name = "Fantasy"))
                    genreDao.insertBookGenre(BookGenreCrossRef(bookId = BookId("book-1"), genreId = "g-1"))
                }

                val updated = turbine.awaitItem()
                assertNotNull(updated)
                assertEquals(listOf("Fantasy"), updated.genres.map { it.name })

                turbine.cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `observeBookDetail re-emits when tags change for the book`() =
        runTest {
            bookDao.upsert(makeBookEntity(id = "book-1"))

            turbineScope {
                val turbine = repository.observeBookDetail("book-1").testIn(backgroundScope)

                val initial = turbine.awaitItem()
                assertNotNull(initial)
                assertTrue(initial.tags.isEmpty())

                // See genres-test note above — same atomic-write rationale.
                RoomTransactionRunner(db).atomically {
                    tagDao.upsert(makeTagEntity(id = "t-1", slug = "epic"))
                    tagDao.insertBookTag(BookTagCrossRef(bookId = BookId("book-1"), tagId = "t-1"))
                }

                val updated = turbine.awaitItem()
                assertNotNull(updated)
                assertEquals(1, updated.tags.size)
                assertEquals("epic", updated.tags.first().slug)

                turbine.cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `observeBookListItems emits current rows and ignores genre-only changes`() =
        runTest {
            bookDao.upsert(makeBookEntity(id = "book-1"))

            turbineScope {
                val turbine = repository.observeBookListItems().testIn(backgroundScope)

                val initial = turbine.awaitItem()
                assertEquals(1, initial.size)
                assertEquals(BookId("book-1"), initial.first().id)

                // Genre changes should not trip the list-flow observer (book row unchanged).
                genreDao.upsert(makeGenreEntity(id = "g-1", name = "Fantasy"))
                genreDao.insertBookGenre(BookGenreCrossRef(bookId = BookId("book-1"), genreId = "g-1"))

                // Expect no further emission from the list flow — the list surface does
                // not care about genre edges.
                turbine.expectNoEvents()
                turbine.cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getBookListItems returns rows for requested ids`() =
        runTest {
            bookDao.upsert(makeBookEntity(id = "book-1", title = "A"))
            bookDao.upsert(makeBookEntity(id = "book-2", title = "B"))
            bookDao.upsert(makeBookEntity(id = "book-3", title = "C"))

            val result = repository.getBookListItems(listOf("book-2", "book-1", "book-3"))

            assertEquals(3, result.size)
            assertEquals(setOf("A", "B", "C"), result.map { it.title }.toSet())
        }

    @Test
    fun `getBookListItems returns empty list for empty input`() =
        runTest {
            val result = repository.getBookListItems(emptyList())
            assertTrue(result.isEmpty())
        }

    @Test
    fun `getBookListItem returns null for unknown id`() =
        runTest {
            assertNull(repository.getBookListItem("unknown"))
        }

    @Test
    fun `getBookListItem returns mapped BookListItem when present`() =
        runTest {
            bookDao.upsert(makeBookEntity(id = "book-1", title = "Mistborn"))

            val result = repository.getBookListItem("book-1")

            assertNotNull(result)
            assertEquals("Mistborn", result.title)
        }

    @Test
    fun `getBookDetail returns null for unknown id`() =
        runTest {
            assertNull(repository.getBookDetail("unknown"))
        }

    @Test
    fun `getBookDetail returns BookDetail with genres and tags merged`() =
        runTest {
            bookDao.upsert(makeBookEntity(id = "book-1", title = "Warbreaker"))
            genreDao.upsert(makeGenreEntity(id = "g-1", name = "Fantasy"))
            genreDao.insertBookGenre(BookGenreCrossRef(bookId = BookId("book-1"), genreId = "g-1"))
            tagDao.upsert(makeTagEntity(id = "t-1", slug = "magic-system"))
            tagDao.insertBookTag(BookTagCrossRef(bookId = BookId("book-1"), tagId = "t-1"))

            val result = repository.getBookDetail("book-1")

            assertNotNull(result)
            assertEquals("Warbreaker", result.title)
            assertEquals(listOf("Fantasy"), result.genres.map { it.name })
            assertEquals(listOf("magic-system"), result.tags.map { it.slug })
        }
}

/**
 * Mock-based tests for [BookRepositoryImpl.upsertWithAudioFiles].
 *
 * Uses mocked DAOs and [TransactionRunner] so each interaction can be
 * verified independently from Room and from the real-DB tests above.
 */
class BookRepositoryImplUpsertWithAudioFilesTest {
    private val bookDao: BookDao = mock(MockMode.autoUnit)
    private val audioFileDao: AudioFileDao = mock(MockMode.autoUnit)
    private val transactionRunner: TransactionRunner = passThroughTransactionRunner()

    private val imageStorage: ImageStorage =
        mock {
            every { exists(any()) } returns false
            every { getCoverPath(any()) } returns ""
        }

    private val repo =
        BookRepositoryImpl(
            bookDao = bookDao,
            chapterDao = mock(MockMode.autoUnit),
            audioFileDao = audioFileDao,
            transactionRunner = transactionRunner,
            syncManager = mock(MockMode.autoUnit),
            imageStorage = imageStorage,
            genreRepository = mock(MockMode.autoUnit),
            tagRepository = mock(MockMode.autoUnit),
        )

    private fun makeBookEntity(
        id: String,
        title: String = "Book $id",
    ): BookEntity {
        val ts = Timestamp(1_700_000_000_000L)
        return BookEntity(
            id = BookId(id),
            title = title,
            sortTitle = title,
            subtitle = null,
            coverUrl = null,
            coverBlurHash = null,
            dominantColor = null,
            darkMutedColor = null,
            vibrantColor = null,
            totalDuration = 0L,
            description = null,
            publishYear = null,
            publisher = null,
            language = null,
            isbn = null,
            asin = null,
            abridged = false,
            syncState = SyncState.SYNCED,
            lastModified = ts,
            serverVersion = ts,
            createdAt = ts,
            updatedAt = ts,
        )
    }

    private fun makeAudioFileEntity(
        id: String,
        bookId: String,
    ): AudioFileEntity =
        AudioFileEntity(
            bookId = BookId(bookId),
            index = 0,
            id = id,
            filename = "chapter.m4b",
            format = "m4b",
            codec = "aac",
            duration = 1_800_000L,
            size = 45_000_000L,
        )

    @Test
    fun `upsertWithAudioFiles writes book delete-and-upsert audio files inside one transaction`() =
        runTest {
            val book = makeBookEntity("book-1", "Title")
            val audioFiles = listOf(makeAudioFileEntity("file-1", "book-1"))

            val result = repo.upsertWithAudioFiles(book, audioFiles)

            assertEquals(AppResult.Success(Unit), result)
            verifySuspend(VerifyMode.exactly(1)) { transactionRunner.atomically(any<suspend () -> Any>()) }
            verifySuspend(VerifyMode.exactly(1)) { bookDao.upsert(book) }
            verifySuspend(VerifyMode.exactly(1)) { audioFileDao.deleteForBook("book-1") }
            verifySuspend(VerifyMode.exactly(1)) { audioFileDao.upsertAll(audioFiles) }
        }

    @Test
    fun `upsertWithAudioFiles with empty audio file list skips upsertAll`() =
        runTest {
            val book = makeBookEntity("book-2", "Empty Files")

            val result = repo.upsertWithAudioFiles(book, emptyList())

            assertEquals(AppResult.Success(Unit), result)
            verifySuspend(VerifyMode.exactly(1)) { transactionRunner.atomically(any<suspend () -> Any>()) }
            verifySuspend(VerifyMode.exactly(1)) { bookDao.upsert(book) }
            verifySuspend(VerifyMode.exactly(1)) { audioFileDao.deleteForBook("book-2") }
            verifySuspend(VerifyMode.exactly(0)) { audioFileDao.upsertAll(any()) }
        }
}
