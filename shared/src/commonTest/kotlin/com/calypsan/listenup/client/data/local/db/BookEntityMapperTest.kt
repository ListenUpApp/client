package com.calypsan.listenup.client.data.local.db

import com.calypsan.listenup.client.data.local.images.ImageStorage
import com.calypsan.listenup.client.domain.model.Contributor
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tests for BookEntityMapper extension function.
 *
 * Tests cover:
 * - Basic property mapping
 * - Cover path resolution (exists vs not exists)
 * - Authors and narrators mapping
 * - Optional fields handling
 */
class BookEntityMapperTest {
    // ========== Test Fixtures ==========

    private class TestFixture {
        val imageStorage: ImageStorage = mock()

        init {
            // Default: cover doesn't exist
            every { imageStorage.exists(any()) } returns false
            every { imageStorage.getCoverPath(any()) } returns "/default/path"
        }
    }

    private fun createFixture(): TestFixture = TestFixture()

    // ========== Test Data Factories ==========

    private fun createBookEntity(
        id: String = "book-1",
        title: String = "Test Book",
        subtitle: String? = null,
        totalDuration: Long = 3_600_000L,
        description: String? = null,
        genres: String? = null,
        seriesId: String? = null,
        seriesName: String? = null,
        sequence: String? = null,
        publishYear: Int? = null,
    ): BookEntity =
        BookEntity(
            id = BookId(id),
            title = title,
            subtitle = subtitle,
            coverUrl = null,
            totalDuration = totalDuration,
            description = description,
            genres = genres,
            seriesId = seriesId,
            seriesName = seriesName,
            sequence = sequence,
            publishYear = publishYear,
            audioFilesJson = null,
            syncState = SyncState.SYNCED,
            lastModified = Timestamp(1704067200000L),
            serverVersion = Timestamp(1704067200000L),
            createdAt = Timestamp(1704067200000L),
            updatedAt = Timestamp(1704153600000L),
        )

    private fun createContributor(
        id: String = "contributor-1",
        name: String = "Test Author",
    ): Contributor =
        Contributor(
            id = id,
            name = name,
        )

    // ========== Basic Property Mapping Tests ==========

    @Test
    fun `toDomain maps id correctly`() {
        val fixture = createFixture()
        val entity = createBookEntity(id = "book-123")

        val book = entity.toDomain(fixture.imageStorage)

        assertEquals(BookId("book-123"), book.id)
    }

    @Test
    fun `toDomain maps title correctly`() {
        val fixture = createFixture()
        val entity = createBookEntity(title = "The Great Gatsby")

        val book = entity.toDomain(fixture.imageStorage)

        assertEquals("The Great Gatsby", book.title)
    }

    @Test
    fun `toDomain maps subtitle correctly`() {
        val fixture = createFixture()
        val entity = createBookEntity(subtitle = "A Novel")

        val book = entity.toDomain(fixture.imageStorage)

        assertEquals("A Novel", book.subtitle)
    }

    @Test
    fun `toDomain maps null subtitle correctly`() {
        val fixture = createFixture()
        val entity = createBookEntity(subtitle = null)

        val book = entity.toDomain(fixture.imageStorage)

        assertNull(book.subtitle)
    }

    @Test
    fun `toDomain maps duration correctly`() {
        val fixture = createFixture()
        val entity = createBookEntity(totalDuration = 7_200_000L)

        val book = entity.toDomain(fixture.imageStorage)

        assertEquals(7_200_000L, book.duration)
    }

    @Test
    fun `toDomain maps description correctly`() {
        val fixture = createFixture()
        val entity = createBookEntity(description = "A classic American novel")

        val book = entity.toDomain(fixture.imageStorage)

        assertEquals("A classic American novel", book.description)
    }

    @Test
    fun `toDomain maps genres correctly`() {
        val fixture = createFixture()
        val entity = createBookEntity(genres = "fiction,classic")

        val book = entity.toDomain(fixture.imageStorage)

        assertEquals("fiction,classic", book.genres)
    }

    @Test
    fun `toDomain maps series info correctly`() {
        val fixture = createFixture()
        val entity =
            createBookEntity(
                seriesId = "series-1",
                seriesName = "Epic Fantasy Saga",
                sequence = "2.5",
            )

        val book = entity.toDomain(fixture.imageStorage)

        assertEquals("series-1", book.seriesId)
        assertEquals("Epic Fantasy Saga", book.seriesName)
        assertEquals("2.5", book.seriesSequence)
    }

    @Test
    fun `toDomain maps publishYear correctly`() {
        val fixture = createFixture()
        val entity = createBookEntity(publishYear = 2024)

        val book = entity.toDomain(fixture.imageStorage)

        assertEquals(2024, book.publishYear)
    }

    @Test
    fun `toDomain maps timestamps correctly`() {
        val fixture = createFixture()
        val entity = createBookEntity()

        val book = entity.toDomain(fixture.imageStorage)

        assertEquals(Timestamp(1704067200000L), book.addedAt)
        assertEquals(Timestamp(1704153600000L), book.updatedAt)
    }

    // ========== Cover Path Tests ==========

    @Test
    fun `toDomain returns cover path when cover exists`() {
        val fixture = createFixture()
        val bookId = BookId("book-with-cover")
        every { fixture.imageStorage.exists(bookId) } returns true
        every { fixture.imageStorage.getCoverPath(bookId) } returns "/covers/book-with-cover.jpg"
        val entity = createBookEntity(id = "book-with-cover")

        val book = entity.toDomain(fixture.imageStorage)

        assertEquals("/covers/book-with-cover.jpg", book.coverPath)
    }

    @Test
    fun `toDomain returns null cover path when cover does not exist`() {
        val fixture = createFixture()
        val bookId = BookId("book-no-cover")
        every { fixture.imageStorage.exists(bookId) } returns false
        val entity = createBookEntity(id = "book-no-cover")

        val book = entity.toDomain(fixture.imageStorage)

        assertNull(book.coverPath)
    }

    // ========== Authors and Narrators Tests ==========

    @Test
    fun `toDomain includes authors when provided`() {
        val fixture = createFixture()
        val authors =
            listOf(
                createContributor(id = "author-1", name = "Stephen King"),
                createContributor(id = "author-2", name = "Peter Straub"),
            )
        val entity = createBookEntity()

        val book = entity.toDomain(fixture.imageStorage, authors = authors)

        assertEquals(2, book.authors.size)
        assertEquals("Stephen King", book.authors[0].name)
        assertEquals("Peter Straub", book.authors[1].name)
    }

    @Test
    fun `toDomain includes narrators when provided`() {
        val fixture = createFixture()
        val narrators =
            listOf(
                createContributor(id = "narrator-1", name = "Will Patton"),
            )
        val entity = createBookEntity()

        val book = entity.toDomain(fixture.imageStorage, narrators = narrators)

        assertEquals(1, book.narrators.size)
        assertEquals("Will Patton", book.narrators[0].name)
    }

    @Test
    fun `toDomain defaults to empty authors and narrators`() {
        val fixture = createFixture()
        val entity = createBookEntity()

        val book = entity.toDomain(fixture.imageStorage)

        assertEquals(0, book.authors.size)
        assertEquals(0, book.narrators.size)
    }

    @Test
    fun `toDomain includes both authors and narrators when provided`() {
        val fixture = createFixture()
        val authors = listOf(createContributor(id = "author-1", name = "Jane Austen"))
        val narrators = listOf(createContributor(id = "narrator-1", name = "Rosamund Pike"))
        val entity = createBookEntity()

        val book = entity.toDomain(fixture.imageStorage, authors = authors, narrators = narrators)

        assertEquals(1, book.authors.size)
        assertEquals("Jane Austen", book.authors[0].name)
        assertEquals(1, book.narrators.size)
        assertEquals("Rosamund Pike", book.narrators[0].name)
    }

    // ========== Rating Tests ==========

    @Test
    fun `toDomain always returns null rating`() {
        val fixture = createFixture()
        val entity = createBookEntity()

        val book = entity.toDomain(fixture.imageStorage)

        assertNull(book.rating)
    }
}
