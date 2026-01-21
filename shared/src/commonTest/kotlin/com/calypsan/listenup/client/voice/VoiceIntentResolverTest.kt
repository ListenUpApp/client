package com.calypsan.listenup.client.voice

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class VoiceIntentResolverTest {

    private val searchRepository = FakeSearchRepository()
    private val homeRepository = FakeHomeRepository()
    private val seriesRepository = FakeSeriesRepository()
    private val bookRepository = FakeBookRepository()

    private val resolver = VoiceIntentResolver(
        searchRepository = searchRepository,
        homeRepository = homeRepository,
        seriesRepository = seriesRepository,
        bookRepository = bookRepository,
    )

    // ========== Resume Intent Tests ==========

    @Test
    fun `resume phrase returns Resume intent`() = runTest {
        val result = resolver.resolve("resume")
        assertIs<PlaybackIntent.Resume>(result)
    }

    @Test
    fun `continue my audiobook returns Resume intent`() = runTest {
        val result = resolver.resolve("continue my audiobook")
        assertIs<PlaybackIntent.Resume>(result)
    }

    @Test
    fun `where I left off returns Resume intent`() = runTest {
        val result = resolver.resolve("where I left off")
        assertIs<PlaybackIntent.Resume>(result)
    }

    // ========== Search Resolution Tests ==========

    @Test
    fun `exact title match returns PlayBook with high confidence`() = runTest {
        searchRepository.setResults(
            testSearchHit(id = "book1", name = "The Hobbit", score = 1.0f),
        )

        val result = resolver.resolve("The Hobbit")

        assertIs<PlaybackIntent.PlayBook>(result)
        assertEquals("book1", result.bookId)
    }

    @Test
    fun `partial title match with high score returns PlayBook`() = runTest {
        searchRepository.setResults(
            testSearchHit(id = "book1", name = "The Hobbit", score = 0.9f),
        )

        val result = resolver.resolve("Hobbit")

        assertIs<PlaybackIntent.PlayBook>(result)
        assertEquals("book1", result.bookId)
    }

    @Test
    fun `multiple matches returns Ambiguous with bestGuess`() = runTest {
        searchRepository.setResults(
            testSearchHit(id = "book1", name = "The Hobbit", score = 0.7f),
            testSearchHit(id = "book2", name = "The Hobbit: An Unexpected Journey", score = 0.6f),
        )

        val result = resolver.resolve("Hobbit")

        assertIs<PlaybackIntent.Ambiguous>(result)
        assertEquals(2, result.candidates.size)
        assertNotNull(result.bestGuess)
        assertEquals("book1", result.bestGuess?.bookId)
    }

    @Test
    fun `no matches returns NotFound`() = runTest {
        searchRepository.setResults() // empty

        val result = resolver.resolve("xyzzy gibberish")

        assertIs<PlaybackIntent.NotFound>(result)
        assertEquals("xyzzy gibberish", result.originalQuery)
    }

    @Test
    fun `title hint boosts exact match confidence`() = runTest {
        searchRepository.setResults(
            testSearchHit(id = "book1", name = "The Hobbit", score = 0.5f),
        )

        val result = resolver.resolve(
            query = "The Hobbit",
            hints = VoiceHints(title = "The Hobbit"),
        )

        // With title hint matching exactly, confidence should be high enough for PlayBook
        assertIs<PlaybackIntent.PlayBook>(result)
    }

    // ========== Series Navigation Tests ==========

    @Test
    fun `next book with series context returns PlaySeriesFrom`() = runTest {
        // Setup: User has been listening to book 1 of a series
        val series = testSeries("series1", "Lord of the Rings")
        val book1 = testBook(
            id = "book1",
            title = "The Fellowship of the Ring",
            series = listOf(testBookSeries("series1", "Lord of the Rings", "1")),
        )
        val book2 = testBook(
            id = "book2",
            title = "The Two Towers",
            series = listOf(testBookSeries("series1", "Lord of the Rings", "2")),
        )

        bookRepository.addBook(book1)
        bookRepository.addBook(book2)
        seriesRepository.addSeries(series, listOf("book1", "book2"))
        homeRepository.setContinueListening(testContinueListeningBook("book1", "The Fellowship of the Ring"))

        val result = resolver.resolve("next book")

        assertIs<PlaybackIntent.PlaySeriesFrom>(result)
        assertEquals("series1", result.seriesId)
        assertEquals("book2", result.startBookId)
    }

    @Test
    fun `book 2 with series context returns PlaySeriesFrom`() = runTest {
        val series = testSeries("series1", "Mistborn")
        val book1 = testBook(
            id = "book1",
            title = "The Final Empire",
            series = listOf(testBookSeries("series1", "Mistborn", "1")),
        )
        val book2 = testBook(
            id = "book2",
            title = "The Well of Ascension",
            series = listOf(testBookSeries("series1", "Mistborn", "2")),
        )

        bookRepository.addBook(book1)
        bookRepository.addBook(book2)
        seriesRepository.addSeries(series, listOf("book1", "book2"))
        homeRepository.setContinueListening(testContinueListeningBook("book1", "The Final Empire"))

        val result = resolver.resolve("book 2")

        assertIs<PlaybackIntent.PlaySeriesFrom>(result)
        assertEquals("book2", result.startBookId)
    }

    @Test
    fun `next book without series context falls back to search`() = runTest {
        // No continue listening, no series context
        searchRepository.setResults(
            testSearchHit(id = "book1", name = "Next Book: A Novel", score = 0.9f),
        )

        val result = resolver.resolve("next book")

        // Should fall back to search since there's no series context
        assertIs<PlaybackIntent.PlayBook>(result)
    }

    @Test
    fun `first book in series returns PlaySeriesFrom`() = runTest {
        val series = testSeries("series1", "Harry Potter")
        val book1 = testBook(
            id = "book1",
            title = "Philosopher's Stone",
            series = listOf(testBookSeries("series1", "Harry Potter", "1")),
        )
        val book3 = testBook(
            id = "book3",
            title = "Prisoner of Azkaban",
            series = listOf(testBookSeries("series1", "Harry Potter", "3")),
        )

        bookRepository.addBook(book1)
        bookRepository.addBook(book3)
        seriesRepository.addSeries(series, listOf("book1", "book3"))
        homeRepository.setContinueListening(testContinueListeningBook("book3", "Prisoner of Azkaban"))

        val result = resolver.resolve("first book")

        assertIs<PlaybackIntent.PlaySeriesFrom>(result)
        assertEquals("book1", result.startBookId)
    }
}
