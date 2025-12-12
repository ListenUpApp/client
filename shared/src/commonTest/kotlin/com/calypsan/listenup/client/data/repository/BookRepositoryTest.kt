package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.data.local.db.BookContributorCrossRef
import com.calypsan.listenup.client.data.local.db.BookDao
import com.calypsan.listenup.client.data.local.db.BookEntity
import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.local.db.BookWithContributors
import com.calypsan.listenup.client.data.local.db.ChapterDao
import com.calypsan.listenup.client.data.local.db.ChapterEntity
import com.calypsan.listenup.client.data.local.db.ChapterId
import com.calypsan.listenup.client.data.local.db.ContributorEntity
import com.calypsan.listenup.client.data.local.db.SyncState
import com.calypsan.listenup.client.data.local.db.Timestamp
import com.calypsan.listenup.client.data.local.images.ImageStorage
import com.calypsan.listenup.client.data.sync.SyncManagerContract
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BookRepositoryTest {
    // ========== Test Fixtures ==========

    private class TestFixture {
        val bookDao: BookDao = mock()
        val chapterDao: ChapterDao = mock()
        val syncManager: SyncManagerContract = mock()
        val imageStorage: ImageStorage = mock()

        fun build(): BookRepository =
            BookRepository(
                bookDao = bookDao,
                chapterDao = chapterDao,
                syncManager = syncManager,
                imageStorage = imageStorage,
            )
    }

    private fun createFixture(): TestFixture {
        val fixture = TestFixture()
        // Default stubs for common operations
        every { fixture.bookDao.observeAllWithContributors() } returns flowOf(emptyList())
        every { fixture.imageStorage.exists(any()) } returns false
        every { fixture.imageStorage.getCoverPath(any()) } returns "/covers/default.jpg"
        return fixture
    }

    private fun createTestBookEntity(
        id: String = "book-1",
        title: String = "Test Book",
        subtitle: String? = null,
        coverUrl: String? = null,
        totalDuration: Long = 3_600_000L,
        description: String? = null,
        genres: String? = null,
        publishYear: Int? = null,
    ): BookEntity =
        BookEntity(
            id = BookId(id),
            title = title,
            subtitle = subtitle,
            coverUrl = coverUrl,
            totalDuration = totalDuration,
            description = description,
            genres = genres,
            publishYear = publishYear,
            audioFilesJson = null,
            syncState = SyncState.SYNCED,
            lastModified = Timestamp(0L),
            serverVersion = Timestamp(0L),
            createdAt = Timestamp(1000L),
            updatedAt = Timestamp(2000L),
        )

    private fun createTestContributor(
        id: String = "contributor-1",
        name: String = "Test Author",
    ): ContributorEntity =
        ContributorEntity(
            id = id,
            name = name,
            description = null,
            imagePath = null,
            syncState = SyncState.SYNCED,
            lastModified = Timestamp(0L),
            serverVersion = Timestamp(0L),
            createdAt = Timestamp(0L),
            updatedAt = Timestamp(0L),
        )

    private fun createTestChapter(
        id: String = "chapter-1",
        bookId: String = "book-1",
        title: String = "Chapter 1",
        duration: Long = 1_800_000L,
        startTime: Long = 0L,
    ): ChapterEntity =
        ChapterEntity(
            id = ChapterId(id),
            bookId = BookId(bookId),
            title = title,
            duration = duration,
            startTime = startTime,
            syncState = SyncState.SYNCED,
            lastModified = Timestamp(0L),
            serverVersion = Timestamp(0L),
        )

    // ========== observeBooks Tests ==========

    @Test
    fun `observeBooks returns empty list when no books`() =
        runTest {
            // Given
            val fixture = createFixture()
            every { fixture.bookDao.observeAllWithContributors() } returns flowOf(emptyList())
            val repository = fixture.build()

            // When
            val result = repository.observeBooks().first()

            // Then
            assertTrue(result.isEmpty())
        }

    @Test
    fun `observeBooks transforms entities to domain models`() =
        runTest {
            // Given
            val bookEntity =
                createTestBookEntity(
                    id = "book-1",
                    title = "The Great Audiobook",
                    totalDuration = 7_200_000L,
                )
            val bookWithContributors =
                BookWithContributors(
                    book = bookEntity,
                    contributors = emptyList(),
                    contributorRoles = emptyList(),
                    series = emptyList(),
                    seriesSequences = emptyList(),
                )
            val fixture = createFixture()
            every { fixture.bookDao.observeAllWithContributors() } returns flowOf(listOf(bookWithContributors))
            val repository = fixture.build()

            // When
            val result = repository.observeBooks().first()

            // Then
            assertEquals(1, result.size)
            val book = result.first()
            assertEquals("book-1", book.id.value)
            assertEquals("The Great Audiobook", book.title)
            assertEquals(7_200_000L, book.duration)
        }

    @Test
    fun `observeBooks includes cover path when image exists`() =
        runTest {
            // Given
            val bookEntity = createTestBookEntity(id = "book-1")
            val bookWithContributors =
                BookWithContributors(
                    book = bookEntity,
                    contributors = emptyList(),
                    contributorRoles = emptyList(),
                    series = emptyList(),
                    seriesSequences = emptyList(),
                )
            val fixture = createFixture()
            every { fixture.bookDao.observeAllWithContributors() } returns flowOf(listOf(bookWithContributors))
            every { fixture.imageStorage.exists(BookId("book-1")) } returns true
            every { fixture.imageStorage.getCoverPath(BookId("book-1")) } returns "/covers/book-1.jpg"
            val repository = fixture.build()

            // When
            val result = repository.observeBooks().first()

            // Then
            assertEquals("/covers/book-1.jpg", result.first().coverPath)
        }

    @Test
    fun `observeBooks excludes cover path when image does not exist`() =
        runTest {
            // Given
            val bookEntity = createTestBookEntity(id = "book-1")
            val bookWithContributors =
                BookWithContributors(
                    book = bookEntity,
                    contributors = emptyList(),
                    contributorRoles = emptyList(),
                    series = emptyList(),
                    seriesSequences = emptyList(),
                )
            val fixture = createFixture()
            every { fixture.bookDao.observeAllWithContributors() } returns flowOf(listOf(bookWithContributors))
            every { fixture.imageStorage.exists(BookId("book-1")) } returns false
            val repository = fixture.build()

            // When
            val result = repository.observeBooks().first()

            // Then
            assertNull(result.first().coverPath)
        }

    @Test
    fun `observeBooks includes authors from contributors with author role`() =
        runTest {
            // Given
            val bookEntity = createTestBookEntity(id = "book-1")
            val author = createTestContributor(id = "author-1", name = "Stephen King")
            val crossRef =
                BookContributorCrossRef(
                    bookId = BookId("book-1"),
                    contributorId = "author-1",
                    role = "author",
                )
            val bookWithContributors =
                BookWithContributors(
                    book = bookEntity,
                    contributors = listOf(author),
                    contributorRoles = listOf(crossRef),
                    series = emptyList(),
                    seriesSequences = emptyList(),
                )
            val fixture = createFixture()
            every { fixture.bookDao.observeAllWithContributors() } returns flowOf(listOf(bookWithContributors))
            val repository = fixture.build()

            // When
            val result = repository.observeBooks().first()

            // Then
            assertEquals(1, result.first().authors.size)
            assertEquals(
                "Stephen King",
                result
                    .first()
                    .authors
                    .first()
                    .name,
            )
        }

    @Test
    fun `observeBooks includes narrators from contributors with narrator role`() =
        runTest {
            // Given
            val bookEntity = createTestBookEntity(id = "book-1")
            val narrator = createTestContributor(id = "narrator-1", name = "Morgan Freeman")
            val crossRef =
                BookContributorCrossRef(
                    bookId = BookId("book-1"),
                    contributorId = "narrator-1",
                    role = "narrator",
                )
            val bookWithContributors =
                BookWithContributors(
                    book = bookEntity,
                    contributors = listOf(narrator),
                    contributorRoles = listOf(crossRef),
                    series = emptyList(),
                    seriesSequences = emptyList(),
                )
            val fixture = createFixture()
            every { fixture.bookDao.observeAllWithContributors() } returns flowOf(listOf(bookWithContributors))
            val repository = fixture.build()

            // When
            val result = repository.observeBooks().first()

            // Then
            assertEquals(1, result.first().narrators.size)
            assertEquals(
                "Morgan Freeman",
                result
                    .first()
                    .narrators
                    .first()
                    .name,
            )
        }

    @Test
    fun `observeBooks handles contributor with multiple roles`() =
        runTest {
            // Given - same person is both author and narrator
            val bookEntity = createTestBookEntity(id = "book-1")
            val contributor = createTestContributor(id = "person-1", name = "Neil Gaiman")
            val authorRole =
                BookContributorCrossRef(
                    bookId = BookId("book-1"),
                    contributorId = "person-1",
                    role = "author",
                )
            val narratorRole =
                BookContributorCrossRef(
                    bookId = BookId("book-1"),
                    contributorId = "person-1",
                    role = "narrator",
                )
            val bookWithContributors =
                BookWithContributors(
                    book = bookEntity,
                    contributors = listOf(contributor),
                    contributorRoles = listOf(authorRole, narratorRole),
                    series = emptyList(),
                    seriesSequences = emptyList(),
                )
            val fixture = createFixture()
            every { fixture.bookDao.observeAllWithContributors() } returns flowOf(listOf(bookWithContributors))
            val repository = fixture.build()

            // When
            val result = repository.observeBooks().first()

            // Then
            val book = result.first()
            assertEquals(1, book.authors.size)
            assertEquals("Neil Gaiman", book.authors.first().name)
            assertEquals(1, book.narrators.size)
            assertEquals("Neil Gaiman", book.narrators.first().name)
            // allContributors should have roles populated
            assertEquals(1, book.allContributors.size)
            assertTrue(
                book.allContributors
                    .first()
                    .roles
                    .containsAll(listOf("author", "narrator")),
            )
        }

    @Test
    fun `observeBooks includes multiple authors and narrators`() =
        runTest {
            // Given
            val bookEntity = createTestBookEntity(id = "book-1")
            val author1 = createTestContributor(id = "author-1", name = "Author One")
            val author2 = createTestContributor(id = "author-2", name = "Author Two")
            val narrator = createTestContributor(id = "narrator-1", name = "Narrator One")
            val authorRole1 = BookContributorCrossRef(BookId("book-1"), "author-1", "author")
            val authorRole2 = BookContributorCrossRef(BookId("book-1"), "author-2", "author")
            val narratorRole = BookContributorCrossRef(BookId("book-1"), "narrator-1", "narrator")
            val bookWithContributors =
                BookWithContributors(
                    book = bookEntity,
                    contributors = listOf(author1, author2, narrator),
                    contributorRoles = listOf(authorRole1, authorRole2, narratorRole),
                    series = emptyList(),
                    seriesSequences = emptyList(),
                )
            val fixture = createFixture()
            every { fixture.bookDao.observeAllWithContributors() } returns flowOf(listOf(bookWithContributors))
            val repository = fixture.build()

            // When
            val result = repository.observeBooks().first()

            // Then
            val book = result.first()
            assertEquals(2, book.authors.size)
            assertEquals(listOf("Author One", "Author Two"), book.authors.map { it.name })
            assertEquals(1, book.narrators.size)
            assertEquals("Narrator One", book.narrators.first().name)
        }

    // ========== refreshBooks Tests ==========

    @Test
    fun `refreshBooks delegates to syncManager`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.syncManager.sync() } returns Result.Success(Unit)
            val repository = fixture.build()

            // When
            repository.refreshBooks()

            // Then
            verifySuspend { fixture.syncManager.sync() }
        }

    @Test
    fun `refreshBooks returns success when sync succeeds`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.syncManager.sync() } returns Result.Success(Unit)
            val repository = fixture.build()

            // When
            val result = repository.refreshBooks()

            // Then
            assertTrue(result is Result.Success)
        }

    @Test
    fun `refreshBooks returns failure when sync fails`() =
        runTest {
            // Given
            val fixture = createFixture()
            val exception = Exception("Network error")
            everySuspend { fixture.syncManager.sync() } returns Result.Failure(exception)
            val repository = fixture.build()

            // When
            val result = repository.refreshBooks()

            // Then
            assertTrue(result is Result.Failure)
            assertEquals(exception, (result as Result.Failure).exception)
        }

    // ========== getBook Tests ==========

    @Test
    fun `getBook returns null when not found`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.bookDao.getByIdWithContributors(BookId("book-1")) } returns null
            val repository = fixture.build()

            // When
            val result = repository.getBook("book-1")

            // Then
            assertNull(result)
        }

    @Test
    fun `getBook transforms entity to domain model`() =
        runTest {
            // Given
            val bookEntity =
                createTestBookEntity(
                    id = "book-1",
                    title = "Found Book",
                    description = "A great book",
                )
            val bookWithContributors =
                BookWithContributors(
                    book = bookEntity,
                    contributors = emptyList(),
                    contributorRoles = emptyList(),
                    series = emptyList(),
                    seriesSequences = emptyList(),
                )
            val fixture = createFixture()
            everySuspend { fixture.bookDao.getByIdWithContributors(BookId("book-1")) } returns bookWithContributors
            val repository = fixture.build()

            // When
            val result = repository.getBook("book-1")

            // Then
            assertEquals("book-1", result?.id?.value)
            assertEquals("Found Book", result?.title)
            assertEquals("A great book", result?.description)
        }

    @Test
    fun `getBook includes cover path when image exists`() =
        runTest {
            // Given
            val bookEntity = createTestBookEntity(id = "book-1")
            val bookWithContributors =
                BookWithContributors(
                    book = bookEntity,
                    contributors = emptyList(),
                    contributorRoles = emptyList(),
                    series = emptyList(),
                    seriesSequences = emptyList(),
                )
            val fixture = createFixture()
            everySuspend { fixture.bookDao.getByIdWithContributors(BookId("book-1")) } returns bookWithContributors
            every { fixture.imageStorage.exists(BookId("book-1")) } returns true
            every { fixture.imageStorage.getCoverPath(BookId("book-1")) } returns "/covers/book-1.jpg"
            val repository = fixture.build()

            // When
            val result = repository.getBook("book-1")

            // Then
            assertEquals("/covers/book-1.jpg", result?.coverPath)
        }

    // ========== getChapters Tests ==========

    @Test
    fun `getChapters returns existing chapters from database`() =
        runTest {
            // Given
            val chapters =
                listOf(
                    createTestChapter(id = "ch-1", title = "Chapter 1", startTime = 0L),
                    createTestChapter(id = "ch-2", title = "Chapter 2", startTime = 1_800_000L),
                )
            val fixture = createFixture()
            everySuspend { fixture.chapterDao.getChaptersForBook(BookId("book-1")) } returns chapters
            val repository = fixture.build()

            // When
            val result = repository.getChapters("book-1")

            // Then
            assertEquals(2, result.size)
            assertEquals("Chapter 1", result[0].title)
            assertEquals("Chapter 2", result[1].title)
            assertEquals(0L, result[0].startTime)
            assertEquals(1_800_000L, result[1].startTime)
        }

    @Test
    fun `getChapters generates mock chapters when database empty`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.chapterDao.getChaptersForBook(BookId("book-1")) } returns emptyList()
            everySuspend { fixture.chapterDao.upsertAll(any()) } returns Unit
            val repository = fixture.build()

            // When
            val result = repository.getChapters("book-1")

            // Then
            assertEquals(15, result.size) // Mock generates 15 chapters
            assertEquals("Chapter 1", result[0].title)
            assertEquals("Chapter 15", result[14].title)
        }

    @Test
    fun `getChapters persists mock chapters to database`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.chapterDao.getChaptersForBook(BookId("book-1")) } returns emptyList()
            everySuspend { fixture.chapterDao.upsertAll(any()) } returns Unit
            val repository = fixture.build()

            // When
            repository.getChapters("book-1")

            // Then
            verifySuspend { fixture.chapterDao.upsertAll(any()) }
        }

    @Test
    fun `getChapters transforms chapter entities to domain models`() =
        runTest {
            // Given
            val chapter =
                createTestChapter(
                    id = "ch-1",
                    title = "Introduction",
                    duration = 900_000L,
                    startTime = 0L,
                )
            val fixture = createFixture()
            everySuspend { fixture.chapterDao.getChaptersForBook(BookId("book-1")) } returns listOf(chapter)
            val repository = fixture.build()

            // When
            val result = repository.getChapters("book-1")

            // Then
            assertEquals(1, result.size)
            assertEquals("ch-1", result[0].id)
            assertEquals("Introduction", result[0].title)
            assertEquals(900_000L, result[0].duration)
            assertEquals(0L, result[0].startTime)
        }
}
