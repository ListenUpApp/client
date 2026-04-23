package com.calypsan.listenup.client.data.local.db

import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.core.ContributorId
import com.calypsan.listenup.client.core.SeriesId
import com.calypsan.listenup.client.core.Timestamp
import com.calypsan.listenup.client.domain.model.BookContributor
import com.calypsan.listenup.client.domain.model.BookSeries
import com.calypsan.listenup.client.domain.repository.ImageStorage
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Golden-output tests for the canonical [BookWithContributors.toDomain] mapper.
 *
 * These tests pin the canonical mapper's current behavior so a future regression
 * fails immediately. They assert what the mapper DOES produce, not what a caller
 * might wish it produced (e.g., allContributors is always emptyList here — that
 * is the canonical mapper's contract; see Task 13 drift row for future consolidation).
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
}
