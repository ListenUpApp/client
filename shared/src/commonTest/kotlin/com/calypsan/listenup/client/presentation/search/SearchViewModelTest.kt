package com.calypsan.listenup.client.presentation.search

import com.calypsan.listenup.client.data.repository.SearchRepositoryContract
import com.calypsan.listenup.client.domain.model.SearchHit
import com.calypsan.listenup.client.domain.model.SearchHitType
import com.calypsan.listenup.client.domain.model.SearchResult
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verifySuspend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Tests for SearchViewModel.
 *
 * Tests cover:
 * - Initial state
 * - Query handling with debounce (300ms, min 2 chars)
 * - Search success and error states
 * - Type filtering (toggle, re-search)
 * - Navigation actions (book, contributor, series)
 * - Expand/collapse and clear query
 *
 * Uses Mokkery for mocking SearchRepositoryContract.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    // ========== Test Fixtures ==========

    private class TestFixture(
        private val scope: TestScope,
    ) {
        val searchRepository: SearchRepositoryContract = mock()

        fun build(): SearchViewModel =
            SearchViewModel(
                searchRepository = searchRepository,
            )
    }

    private fun TestScope.createFixture(): TestFixture = TestFixture(this)

    // ========== Test Data Factories ==========

    private fun createSearchResult(
        query: String = "test",
        hits: List<SearchHit> = emptyList(),
        total: Int = hits.size,
    ): SearchResult =
        SearchResult(
            query = query,
            total = total,
            tookMs = 10L,
            hits = hits,
        )

    private fun createBookHit(
        id: String = "book-1",
        name: String = "Test Book",
    ): SearchHit =
        SearchHit(
            id = id,
            type = SearchHitType.BOOK,
            name = name,
        )

    private fun createContributorHit(
        id: String = "contributor-1",
        name: String = "Test Author",
    ): SearchHit =
        SearchHit(
            id = id,
            type = SearchHitType.CONTRIBUTOR,
            name = name,
        )

    private fun createSeriesHit(
        id: String = "series-1",
        name: String = "Test Series",
    ): SearchHit =
        SearchHit(
            id = id,
            type = SearchHitType.SERIES,
            name = name,
        )

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ========== Initial State Tests ==========

    @Test
    fun `initial state has empty query and no results`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()
            advanceUntilIdle()

            // Then
            val state = viewModel.state.value
            assertEquals("", state.query)
            assertFalse(state.isSearching)
            assertNull(state.results)
            assertNull(state.error)
            assertFalse(state.isExpanded)
            assertTrue(state.selectedTypes.isEmpty())
        }

    // ========== Query Handling Tests ==========

    @Test
    fun `QueryChanged updates query in state`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()
            advanceUntilIdle()

            // When
            viewModel.onEvent(SearchUiEvent.QueryChanged("hello"))
            advanceUntilIdle()

            // Then
            assertEquals("hello", viewModel.state.value.query)
        }

    @Test
    fun `search triggers after debounce delay for query with min 2 chars`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.searchRepository.search(any(), any(), any(), any(), any()) } returns
                createSearchResult(
                    query = "test",
                    hits = listOf(createBookHit()),
                )
            val viewModel = fixture.build()
            advanceUntilIdle()

            // When - type query with 2+ chars
            viewModel.onEvent(SearchUiEvent.QueryChanged("te"))

            // Before debounce - no search yet
            advanceTimeBy(200.milliseconds)
            assertNull(viewModel.state.value.results)

            // After debounce (300ms) - search should execute
            advanceTimeBy(150.milliseconds)
            advanceUntilIdle()

            // Then
            assertNotNull(viewModel.state.value.results)
            verifySuspend {
                fixture.searchRepository.search(
                    query = "te",
                    types = null,
                    genres = null,
                    genrePath = null,
                    limit = 30,
                )
            }
        }

    @Test
    fun `search does not trigger for single character query`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()
            advanceUntilIdle()

            // When - type single char
            viewModel.onEvent(SearchUiEvent.QueryChanged("a"))
            advanceTimeBy(500.milliseconds)
            advanceUntilIdle()

            // Then - no search should have occurred
            assertNull(viewModel.state.value.results)
        }

    @Test
    fun `blank query clears results`() =
        runTest {
            // Given - viewModel with results
            val fixture = createFixture()
            everySuspend { fixture.searchRepository.search(any(), any(), any(), any(), any()) } returns
                createSearchResult(
                    hits = listOf(createBookHit()),
                )
            val viewModel = fixture.build()
            viewModel.onEvent(SearchUiEvent.QueryChanged("test"))
            advanceTimeBy(400.milliseconds)
            advanceUntilIdle()
            assertNotNull(viewModel.state.value.results)

            // When - clear query
            viewModel.onEvent(SearchUiEvent.QueryChanged(""))
            advanceTimeBy(400.milliseconds)
            advanceUntilIdle()

            // Then
            assertNull(viewModel.state.value.results)
            assertFalse(viewModel.state.value.isSearching)
        }

    // ========== Search Success/Error Tests ==========

    @Test
    fun `search success updates results and clears searching state`() =
        runTest {
            // Given
            val fixture = createFixture()
            val expectedHits = listOf(createBookHit(), createContributorHit())
            everySuspend { fixture.searchRepository.search(any(), any(), any(), any(), any()) } returns
                createSearchResult(
                    hits = expectedHits,
                )
            val viewModel = fixture.build()
            advanceUntilIdle()

            // When
            viewModel.onEvent(SearchUiEvent.QueryChanged("test"))
            advanceTimeBy(400.milliseconds)
            advanceUntilIdle()

            // Then
            val state = viewModel.state.value
            assertFalse(state.isSearching)
            assertNotNull(state.results)
            assertEquals(2, state.results!!.hits.size)
            assertNull(state.error)
        }

    @Test
    fun `search failure sets error state`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend {
                fixture.searchRepository.search(any(), any(), any(), any(), any())
            } throws Exception("Network error")
            val viewModel = fixture.build()
            advanceUntilIdle()

            // When
            viewModel.onEvent(SearchUiEvent.QueryChanged("test"))
            advanceTimeBy(400.milliseconds)
            advanceUntilIdle()

            // Then
            val state = viewModel.state.value
            assertFalse(state.isSearching)
            assertNotNull(state.error)
            // Error message is user-friendly, not raw exception message
            assertEquals("Search unavailable. Please try again.", state.error)
        }

    // ========== Type Filtering Tests ==========

    @Test
    fun `ToggleTypeFilter adds type to selected types`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()
            advanceUntilIdle()
            assertTrue(
                viewModel.state.value.selectedTypes
                    .isEmpty(),
            )

            // When
            viewModel.onEvent(SearchUiEvent.ToggleTypeFilter(SearchHitType.BOOK))
            advanceUntilIdle()

            // Then
            assertEquals(setOf(SearchHitType.BOOK), viewModel.state.value.selectedTypes)
        }

    @Test
    fun `ToggleTypeFilter removes type if already selected`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()
            viewModel.onEvent(SearchUiEvent.ToggleTypeFilter(SearchHitType.BOOK))
            advanceUntilIdle()
            assertEquals(setOf(SearchHitType.BOOK), viewModel.state.value.selectedTypes)

            // When - toggle same type again
            viewModel.onEvent(SearchUiEvent.ToggleTypeFilter(SearchHitType.BOOK))
            advanceUntilIdle()

            // Then
            assertTrue(
                viewModel.state.value.selectedTypes
                    .isEmpty(),
            )
        }

    @Test
    fun `ToggleTypeFilter triggers re-search if query is not blank`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.searchRepository.search(any(), any(), any(), any(), any()) } returns createSearchResult()
            val viewModel = fixture.build()

            // Set up a query first
            viewModel.onEvent(SearchUiEvent.QueryChanged("test"))
            advanceTimeBy(400.milliseconds)
            advanceUntilIdle()

            // When - toggle type filter
            viewModel.onEvent(SearchUiEvent.ToggleTypeFilter(SearchHitType.BOOK))
            advanceUntilIdle()

            // Then - should have searched with the new type filter
            verifySuspend {
                fixture.searchRepository.search(
                    query = "test",
                    types = listOf(SearchHitType.BOOK),
                    genres = null,
                    genrePath = null,
                    limit = 30,
                )
            }
        }

    // ========== Navigation Tests ==========

    @Test
    fun `ResultClicked on book emits NavigateToBook action`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()
            advanceUntilIdle()
            val bookHit = createBookHit(id = "book-123")

            // When
            viewModel.onEvent(SearchUiEvent.ResultClicked(bookHit))
            advanceUntilIdle()

            // Then
            val action = viewModel.navActions.value
            assertIs<SearchNavAction.NavigateToBook>(action)
            assertEquals("book-123", action.bookId)
        }

    @Test
    fun `ResultClicked on contributor emits NavigateToContributor action`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()
            advanceUntilIdle()
            val contributorHit = createContributorHit(id = "author-456")

            // When
            viewModel.onEvent(SearchUiEvent.ResultClicked(contributorHit))
            advanceUntilIdle()

            // Then
            val action = viewModel.navActions.value
            assertIs<SearchNavAction.NavigateToContributor>(action)
            assertEquals("author-456", action.contributorId)
        }

    @Test
    fun `ResultClicked on series emits NavigateToSeries action`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()
            advanceUntilIdle()
            val seriesHit = createSeriesHit(id = "series-789")

            // When
            viewModel.onEvent(SearchUiEvent.ResultClicked(seriesHit))
            advanceUntilIdle()

            // Then
            val action = viewModel.navActions.value
            assertIs<SearchNavAction.NavigateToSeries>(action)
            assertEquals("series-789", action.seriesId)
        }

    @Test
    fun `ResultClicked collapses search`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()
            viewModel.onEvent(SearchUiEvent.ExpandSearch)
            advanceUntilIdle()
            assertTrue(viewModel.state.value.isExpanded)

            // When
            viewModel.onEvent(SearchUiEvent.ResultClicked(createBookHit()))
            advanceUntilIdle()

            // Then
            assertFalse(viewModel.state.value.isExpanded)
        }

    @Test
    fun `clearNavAction clears navigation action`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()
            viewModel.onEvent(SearchUiEvent.ResultClicked(createBookHit()))
            advanceUntilIdle()
            assertNotNull(viewModel.navActions.value)

            // When
            viewModel.clearNavAction()

            // Then
            assertNull(viewModel.navActions.value)
        }

    // ========== Expand/Collapse/Clear Tests ==========

    @Test
    fun `ExpandSearch sets isExpanded to true`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()
            advanceUntilIdle()
            assertFalse(viewModel.state.value.isExpanded)

            // When
            viewModel.onEvent(SearchUiEvent.ExpandSearch)
            advanceUntilIdle()

            // Then
            assertTrue(viewModel.state.value.isExpanded)
        }

    @Test
    fun `CollapseSearch clears state and sets isExpanded to false`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.searchRepository.search(any(), any(), any(), any(), any()) } returns
                createSearchResult(
                    hits = listOf(createBookHit()),
                )
            val viewModel = fixture.build()

            // Set up expanded state with query and results
            viewModel.onEvent(SearchUiEvent.ExpandSearch)
            viewModel.onEvent(SearchUiEvent.QueryChanged("test"))
            advanceTimeBy(400.milliseconds)
            advanceUntilIdle()
            assertTrue(viewModel.state.value.isExpanded)
            assertNotNull(viewModel.state.value.results)

            // When
            viewModel.onEvent(SearchUiEvent.CollapseSearch)
            advanceUntilIdle()

            // Then
            val state = viewModel.state.value
            assertFalse(state.isExpanded)
            assertEquals("", state.query)
            assertNull(state.results)
            assertNull(state.error)
        }

    @Test
    fun `ClearQuery clears query and results but keeps expanded state`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.searchRepository.search(any(), any(), any(), any(), any()) } returns
                createSearchResult(
                    hits = listOf(createBookHit()),
                )
            val viewModel = fixture.build()

            // Set up state with query and results
            viewModel.onEvent(SearchUiEvent.ExpandSearch)
            viewModel.onEvent(SearchUiEvent.QueryChanged("test"))
            advanceTimeBy(400.milliseconds)
            advanceUntilIdle()

            // When
            viewModel.onEvent(SearchUiEvent.ClearQuery)
            advanceUntilIdle()

            // Then
            val state = viewModel.state.value
            assertTrue(state.isExpanded) // Still expanded
            assertEquals("", state.query)
            assertNull(state.results)
        }

    // ========== State Derived Properties Tests ==========

    @Test
    fun `hasResults is true when results exist and are not empty`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.searchRepository.search(any(), any(), any(), any(), any()) } returns
                createSearchResult(
                    hits = listOf(createBookHit()),
                )
            val viewModel = fixture.build()

            // When
            viewModel.onEvent(SearchUiEvent.QueryChanged("test"))
            advanceTimeBy(400.milliseconds)
            advanceUntilIdle()

            // Then
            assertTrue(viewModel.state.value.hasResults)
        }

    @Test
    fun `isEmpty is true when results exist but are empty and query is not blank`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.searchRepository.search(any(), any(), any(), any(), any()) } returns
                createSearchResult(
                    hits = emptyList(),
                )
            val viewModel = fixture.build()

            // When
            viewModel.onEvent(SearchUiEvent.QueryChanged("test"))
            advanceTimeBy(400.milliseconds)
            advanceUntilIdle()

            // Then
            assertTrue(viewModel.state.value.isEmpty)
        }

    @Test
    fun `groupedHits groups results by type`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.searchRepository.search(any(), any(), any(), any(), any()) } returns
                createSearchResult(
                    hits =
                        listOf(
                            createBookHit(id = "book-1"),
                            createBookHit(id = "book-2"),
                            createContributorHit(id = "author-1"),
                            createSeriesHit(id = "series-1"),
                        ),
                )
            val viewModel = fixture.build()

            // When
            viewModel.onEvent(SearchUiEvent.QueryChanged("test"))
            advanceTimeBy(400.milliseconds)
            advanceUntilIdle()

            // Then
            val state = viewModel.state.value
            assertEquals(2, state.books.size)
            assertEquals(1, state.contributors.size)
            assertEquals(1, state.series.size)
        }
}
