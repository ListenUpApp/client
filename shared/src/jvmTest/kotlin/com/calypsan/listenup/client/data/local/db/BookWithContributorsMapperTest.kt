package com.calypsan.listenup.client.data.local.db

import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.core.ContributorId
import com.calypsan.listenup.client.core.SeriesId
import com.calypsan.listenup.client.core.Timestamp
import com.calypsan.listenup.client.domain.model.BookContributor
import com.calypsan.listenup.client.domain.model.BookDetail
import com.calypsan.listenup.client.domain.model.BookSeries
import com.calypsan.listenup.client.domain.model.Genre
import com.calypsan.listenup.client.domain.model.Tag
import com.calypsan.listenup.client.domain.repository.ImageStorage
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Golden-output tests for the canonical mappers in [BookEntityMapper].
 *
 * These tests pin canonical mapper behavior so a future regression fails
 * immediately. Covers [BookWithContributors.toDomain] (legacy, slated for
 * deletion in W6 Phase G Task 12), [BookWithContributors.toListItem], and
 * [BookWithContributors.toDetail].
 */
class BookWithContributorsMapperTest {
    private val bookId = BookId("book-1")
    private val createdAt = Timestamp(1_700_000_000_000L)
    private val updatedAt = Timestamp(1_700_000_001_000L)

    private fun makeBook() =
        BookEntity(
            id = bookId,
            title = "The Way of Kings",
            sortTitle = "Way of Kings, The",
            subtitle = "The Stormlight Archive",
            coverUrl = "https://example.com/cover.jpg",
            coverBlurHash = "L5H2EC=PM+yV",
            dominantColor = 0xFF2244CC.toInt(),
            darkMutedColor = 0xFF112233.toInt(),
            vibrantColor = 0xFF3366FF.toInt(),
            totalDuration = 72_000_000L,
            description = "A fantasy epic.",
            publishYear = 2010,
            publisher = "Tor Books",
            language = "en",
            isbn = "978-0-7653-2637-9",
            asin = "B003P2WO5E",
            abridged = false,
            syncState = SyncState.SYNCED,
            lastModified = updatedAt,
            serverVersion = updatedAt,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private val authorId = ContributorId("contrib-author")
    private val narratorId = ContributorId("contrib-narrator")
    private val narrator2Id = ContributorId("contrib-narrator2")
    private val seriesId = SeriesId("series-1")

    private fun makeContributors() =
        listOf(
            ContributorEntity(
                id = authorId,
                name = "Brandon Sanderson",
                sortName = "Sanderson, Brandon",
                asin = null,
                description = null,
                imagePath = null,
                syncState = SyncState.SYNCED,
                lastModified = updatedAt,
                serverVersion = updatedAt,
                createdAt = createdAt,
                updatedAt = updatedAt,
            ),
            ContributorEntity(
                id = narratorId,
                name = "Michael Kramer",
                sortName = null,
                asin = null,
                description = null,
                imagePath = null,
                syncState = SyncState.SYNCED,
                lastModified = updatedAt,
                serverVersion = updatedAt,
                createdAt = createdAt,
                updatedAt = updatedAt,
            ),
            ContributorEntity(
                id = narrator2Id,
                name = "Kate Reading",
                sortName = null,
                asin = null,
                description = null,
                imagePath = null,
                syncState = SyncState.SYNCED,
                lastModified = updatedAt,
                serverVersion = updatedAt,
                createdAt = createdAt,
                updatedAt = updatedAt,
            ),
        )

    private fun makeContributorRoles() =
        listOf(
            BookContributorCrossRef(
                bookId = bookId,
                contributorId = authorId,
                role = "author",
                creditedAs = null,
            ),
            BookContributorCrossRef(
                bookId = bookId,
                contributorId = narratorId,
                role = "narrator",
                creditedAs = null,
            ),
            BookContributorCrossRef(
                bookId = bookId,
                contributorId = narrator2Id,
                role = "narrator",
                creditedAs = null,
            ),
        )

    private fun makeSeries() =
        listOf(
            SeriesEntity(
                id = seriesId,
                name = "The Stormlight Archive",
                description = null,
                syncState = SyncState.SYNCED,
                lastModified = updatedAt,
                serverVersion = updatedAt,
                createdAt = createdAt,
                updatedAt = updatedAt,
            ),
        )

    private fun makeSeriesSequences() =
        listOf(
            BookSeriesCrossRef(
                bookId = bookId,
                seriesId = seriesId,
                sequence = "1",
            ),
        )

    @Test
    fun `toDomain preserves all populated fields with a representative fixture`() {
        val imageStorage: ImageStorage = mock()
        every { imageStorage.exists(any()) } returns true
        every { imageStorage.getCoverPath(any()) } returns "/data/covers/book-1.jpg"

        val fixture =
            BookWithContributors(
                book = makeBook(),
                contributors = makeContributors(),
                contributorRoles = makeContributorRoles(),
                series = makeSeries(),
                seriesSequences = makeSeriesSequences(),
            )

        val result = fixture.toDomain(imageStorage, includeSeries = true)

        assertEquals(bookId, result.id)
        assertEquals("The Way of Kings", result.title)
        assertEquals("Way of Kings, The", result.sortTitle)
        assertEquals("The Stormlight Archive", result.subtitle)
        assertEquals("/data/covers/book-1.jpg", result.coverPath)
        assertEquals("L5H2EC=PM+yV", result.coverBlurHash)
        assertEquals(0xFF2244CC.toInt(), result.dominantColor)
        assertEquals(0xFF112233.toInt(), result.darkMutedColor)
        assertEquals(0xFF3366FF.toInt(), result.vibrantColor)
        assertEquals(72_000_000L, result.duration)
        assertEquals("A fantasy epic.", result.description)
        assertEquals(2010, result.publishYear)
        assertEquals("Tor Books", result.publisher)
        assertEquals("en", result.language)
        assertEquals("978-0-7653-2637-9", result.isbn)
        assertEquals("B003P2WO5E", result.asin)
        assertEquals(false, result.abridged)
        assertEquals(createdAt, result.addedAt)
        assertEquals(updatedAt, result.updatedAt)
        // genres and tags are always emptyList — loaded on-demand when editing
        assertEquals(emptyList(), result.genres)
        assertEquals(emptyList(), result.tags)
        // allContributors is always emptyList from this mapper (see Task 13 drift row)
        assertEquals(emptyList(), result.allContributors)
        // rating is always null from this mapper
        assertEquals(null, result.rating)
        // authors
        assertEquals(
            listOf(BookContributor(id = authorId.value, name = "Brandon Sanderson")),
            result.authors,
        )
        // narrators
        assertEquals(
            listOf(
                BookContributor(id = narratorId.value, name = "Michael Kramer"),
                BookContributor(id = narrator2Id.value, name = "Kate Reading"),
            ),
            result.narrators,
        )
        // series
        assertEquals(
            listOf(BookSeries(seriesId = seriesId.value, seriesName = "The Stormlight Archive", sequence = "1")),
            result.series,
        )
    }

    @Test
    fun `toDomain with includeSeries=false omits series fields`() {
        val imageStorage: ImageStorage = mock()
        every { imageStorage.exists(any()) } returns false

        val fixture =
            BookWithContributors(
                book = makeBook(),
                contributors = makeContributors(),
                contributorRoles = makeContributorRoles(),
                series = makeSeries(),
                seriesSequences = makeSeriesSequences(),
            )

        val result = fixture.toDomain(imageStorage, includeSeries = false)

        assertEquals(emptyList(), result.series)
        // cover is null because imageStorage.exists returns false
        assertEquals(null, result.coverPath)
        // non-series fields still populated
        assertEquals("The Way of Kings", result.title)
        assertEquals(
            listOf(BookContributor(id = authorId.value, name = "Brandon Sanderson")),
            result.authors,
        )
    }

    @Test
    fun `toDomain handles empty contributors list`() {
        val imageStorage: ImageStorage = mock()
        every { imageStorage.exists(any()) } returns false

        val fixture =
            BookWithContributors(
                book = makeBook(),
                contributors = emptyList(),
                contributorRoles = emptyList(),
                series = emptyList(),
                seriesSequences = emptyList(),
            )

        val result = fixture.toDomain(imageStorage, includeSeries = true)

        assertEquals(emptyList(), result.authors)
        assertEquals(emptyList(), result.narrators)
        assertEquals(emptyList(), result.series)
    }

    @Test
    fun `toListItem returns BookListItem with authors and narrators populated, no detail-only fields`() {
        val imageStorage = mock<ImageStorage>()
        every { imageStorage.exists(any()) } returns false

        val bookWithContributors =
            BookWithContributors(
                book = makeBook(),
                contributors = makeContributors(),
                contributorRoles = makeContributorRoles(),
                series = makeSeries(),
                seriesSequences = makeSeriesSequences(),
            )

        val result = bookWithContributors.toListItem(imageStorage)

        assertEquals(bookId, result.id)
        assertEquals("The Way of Kings", result.title)
        // Authors
        assertEquals(
            listOf(BookContributor(id = "contrib-author", name = "Brandon Sanderson")),
            result.authors,
        )
        // Narrators
        assertEquals(
            listOf(
                BookContributor(id = "contrib-narrator", name = "Michael Kramer"),
                BookContributor(id = "contrib-narrator2", name = "Kate Reading"),
            ),
            result.narrators,
        )
        // Series populated from junction.
        assertEquals(1, result.series.size)
        assertEquals("The Stormlight Archive", result.series.first().seriesName)
    }

    @Test
    fun `toDetail computes allContributors via dedup-and-role-aggregation, genres+tags forwarded as-is`() {
        val imageStorage = mock<ImageStorage>()
        every { imageStorage.exists(any()) } returns false

        val genres = listOf(Genre(id = "genre-1", name = "Fantasy", slug = "fantasy", path = "/fantasy"))
        val tags = listOf(Tag(id = "tag-1", slug = "epic"))

        val bookWithContributors =
            BookWithContributors(
                book = makeBook(),
                contributors = makeContributors(),
                contributorRoles = makeContributorRoles(),
                series = makeSeries(),
                seriesSequences = makeSeriesSequences(),
            )

        val result = bookWithContributors.toDetail(imageStorage, genres, tags)

        // allContributors: every distinct contributor with all roles grouped.
        // The fixture has authorId (role: author), narratorId (role: narrator), narrator2Id (role: narrator) — 3 distinct contributors.
        assertEquals(3, result.allContributors.size)
        val sandersonRoles = result.allContributors.first { it.id == "contrib-author" }.roles
        assertEquals(listOf("author"), sandersonRoles)
        val kramerRoles = result.allContributors.first { it.id == "contrib-narrator" }.roles
        assertEquals(listOf("narrator"), kramerRoles)
        val readingRoles = result.allContributors.first { it.id == "contrib-narrator2" }.roles
        assertEquals(listOf("narrator"), readingRoles)

        // Genres + tags forwarded verbatim.
        assertEquals(genres, result.genres)
        assertEquals(tags, result.tags)
    }

    @Test
    fun `toDetail allContributors handles same person in multiple roles by grouping`() {
        val imageStorage = mock<ImageStorage>()
        every { imageStorage.exists(any()) } returns false

        // Sanderson is BOTH author and narrator on this book.
        val authorAndNarratorRoles =
            listOf(
                BookContributorCrossRef(bookId, authorId, role = "author", creditedAs = null),
                BookContributorCrossRef(bookId, authorId, role = "narrator", creditedAs = null),
            )
        val singleContributor = makeContributors().first { it.id == authorId }

        val bookWithContributors =
            BookWithContributors(
                book = makeBook(),
                contributors = listOf(singleContributor),
                contributorRoles = authorAndNarratorRoles,
                series = emptyList(),
                seriesSequences = emptyList(),
            )

        val result = bookWithContributors.toDetail(imageStorage, emptyList(), emptyList())

        assertEquals(1, result.allContributors.size, "Same contributor across roles must dedupe to a single entry")
        assertEquals(listOf("author", "narrator"), result.allContributors.first().roles)
    }

    @Test
    fun `toDetail allContributors prefers creditedAs over canonical contributor name when creditedAs is non-null`() {
        val imageStorage = mock<ImageStorage>()
        every { imageStorage.exists(any()) } returns false

        // Same Sanderson contributor, but the cross-ref carries a different attribution
        // (e.g., the book was originally credited to the author's pen-name before merging).
        val rolesWithCreditedAs =
            listOf(
                BookContributorCrossRef(
                    bookId = bookId,
                    contributorId = authorId,
                    role = "author",
                    creditedAs = "B. Sanderson (originally credited)",
                ),
            )

        val bookWithContributors =
            BookWithContributors(
                book = makeBook(),
                contributors = listOf(makeContributors().first { it.id == authorId }),
                contributorRoles = rolesWithCreditedAs,
                series = emptyList(),
                seriesSequences = emptyList(),
            )

        val result = bookWithContributors.toDetail(imageStorage, emptyList(), emptyList())

        assertEquals(1, result.allContributors.size)
        assertEquals(
            "B. Sanderson (originally credited)",
            result.allContributors.first().name,
            "creditedAs must win over canonical contributor name when non-null",
        )
    }

    @Test
    fun `toDetail allContributors falls back to canonical contributor name when creditedAs is null`() {
        val imageStorage = mock<ImageStorage>()
        every { imageStorage.exists(any()) } returns false

        val rolesWithoutCreditedAs =
            listOf(
                BookContributorCrossRef(
                    bookId = bookId,
                    contributorId = authorId,
                    role = "author",
                    creditedAs = null,
                ),
            )

        val bookWithContributors =
            BookWithContributors(
                book = makeBook(),
                contributors = listOf(makeContributors().first { it.id == authorId }),
                contributorRoles = rolesWithoutCreditedAs,
                series = emptyList(),
                seriesSequences = emptyList(),
            )

        val result = bookWithContributors.toDetail(imageStorage, emptyList(), emptyList())

        assertEquals(
            "Brandon Sanderson",
            result.allContributors.first().name,
            "Fallback to canonical contributor name when creditedAs is null",
        )
    }

    @Test
    fun `toDetail then toListItem equals toListItem for same input`() {
        val imageStorage = mock<ImageStorage>()
        every { imageStorage.exists(any()) } returns false

        val bookWithContributors =
            BookWithContributors(
                book = makeBook(),
                contributors = makeContributors(),
                contributorRoles = makeContributorRoles(),
                series = makeSeries(),
                seriesSequences = makeSeriesSequences(),
            )

        val viaDetail = bookWithContributors.toDetail(imageStorage, emptyList(), emptyList()).toListItem()
        val direct = bookWithContributors.toListItem(imageStorage)

        assertEquals(direct, viaDetail, "BookDetail.toListItem() must match canonical toListItem mapper")
    }
}
