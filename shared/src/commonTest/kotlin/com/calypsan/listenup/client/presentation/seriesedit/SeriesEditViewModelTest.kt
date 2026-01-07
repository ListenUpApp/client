package com.calypsan.listenup.client.presentation.seriesedit

import com.calypsan.listenup.client.checkIs
import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.domain.model.Series
import com.calypsan.listenup.client.domain.repository.ImageRepository
import com.calypsan.listenup.client.domain.repository.SeriesRepository
import com.calypsan.listenup.client.domain.usecase.series.UpdateSeriesUseCase
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verifySuspend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SeriesEditViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    // ========== Test Fixture ==========

    private class TestFixture {
        val seriesRepository: SeriesRepository = mock()
        val updateSeriesUseCase: UpdateSeriesUseCase = mock()
        val imageRepository: ImageRepository = mock()

        fun build(): SeriesEditViewModel =
            SeriesEditViewModel(
                seriesRepository = seriesRepository,
                updateSeriesUseCase = updateSeriesUseCase,
                imageRepository = imageRepository,
            )
    }

    private fun createFixture(): TestFixture = TestFixture()

    // ========== Test Data Factories ==========

    private fun createSeries(
        id: String = "series-1",
        name: String = "Test Series",
        description: String? = "A test series",
    ) = Series(
        id = id,
        name = name,
        description = description,
    )

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ========== Loading Tests ==========

    @Test
    fun `initial state is loading`() =
        runTest {
            val fixture = createFixture()
            val viewModel = fixture.build()

            assertTrue(viewModel.state.value.isLoading)
        }

    @Test
    fun `loadSeries populates state with series data`() =
        runTest {
            val fixture = createFixture()
            val series = createSeries()
            everySuspend { fixture.seriesRepository.getById("series-1") } returns series
            everySuspend { fixture.seriesRepository.getBookIdsForSeries("series-1") } returns listOf("book-1")
            everySuspend { fixture.imageRepository.seriesCoverExists("series-1") } returns false

            val viewModel = fixture.build()
            viewModel.loadSeries("series-1")
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isLoading)
            assertEquals("Test Series", viewModel.state.value.name)
            assertEquals("A test series", viewModel.state.value.description)
            assertEquals(1, viewModel.state.value.bookCount)
        }

    @Test
    fun `loadSeries shows error for missing series`() =
        runTest {
            val fixture = createFixture()
            everySuspend { fixture.seriesRepository.getById("missing") } returns null

            val viewModel = fixture.build()
            viewModel.loadSeries("missing")
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isLoading)
            assertEquals("Series not found", viewModel.state.value.error)
        }

    // ========== Change Tracking Tests ==========

    @Test
    fun `NameChanged updates state and tracks changes`() =
        runTest {
            val fixture = createFixture()
            everySuspend { fixture.seriesRepository.getById("series-1") } returns createSeries()
            everySuspend { fixture.seriesRepository.getBookIdsForSeries("series-1") } returns listOf("book-1")
            everySuspend { fixture.imageRepository.seriesCoverExists("series-1") } returns false

            val viewModel = fixture.build()
            viewModel.loadSeries("series-1")
            advanceUntilIdle()
            assertFalse(viewModel.state.value.hasChanges)

            viewModel.onEvent(SeriesEditUiEvent.NameChanged("New Name"))

            assertEquals("New Name", viewModel.state.value.name)
            assertTrue(viewModel.state.value.hasChanges)
        }

    @Test
    fun `DescriptionChanged updates state and tracks changes`() =
        runTest {
            val fixture = createFixture()
            everySuspend { fixture.seriesRepository.getById("series-1") } returns createSeries()
            everySuspend { fixture.seriesRepository.getBookIdsForSeries("series-1") } returns listOf("book-1")
            everySuspend { fixture.imageRepository.seriesCoverExists("series-1") } returns false

            val viewModel = fixture.build()
            viewModel.loadSeries("series-1")
            advanceUntilIdle()

            viewModel.onEvent(SeriesEditUiEvent.DescriptionChanged("New description"))

            assertEquals("New description", viewModel.state.value.description)
            assertTrue(viewModel.state.value.hasChanges)
        }

    // ========== Save Tests ==========

    @Test
    fun `SaveClicked with no changes navigates back immediately`() =
        runTest {
            val fixture = createFixture()
            everySuspend { fixture.seriesRepository.getById("series-1") } returns createSeries()
            everySuspend { fixture.seriesRepository.getBookIdsForSeries("series-1") } returns listOf("book-1")
            everySuspend { fixture.imageRepository.seriesCoverExists("series-1") } returns false

            val viewModel = fixture.build()
            viewModel.loadSeries("series-1")
            advanceUntilIdle()

            viewModel.onEvent(SeriesEditUiEvent.SaveClicked)
            advanceUntilIdle()

            checkIs<SeriesEditNavAction.NavigateBack>(viewModel.navActions.value)
        }

    @Test
    fun `SaveClicked with changes calls use case`() =
        runTest {
            val fixture = createFixture()
            everySuspend { fixture.seriesRepository.getById("series-1") } returns createSeries()
            everySuspend { fixture.seriesRepository.getBookIdsForSeries("series-1") } returns listOf("book-1")
            everySuspend { fixture.imageRepository.seriesCoverExists("series-1") } returns false
            everySuspend { fixture.updateSeriesUseCase.invoke(any()) } returns Success(Unit)

            val viewModel = fixture.build()
            viewModel.loadSeries("series-1")
            advanceUntilIdle()
            viewModel.onEvent(SeriesEditUiEvent.NameChanged("Updated Name"))

            viewModel.onEvent(SeriesEditUiEvent.SaveClicked)
            advanceUntilIdle()

            verifySuspend { fixture.updateSeriesUseCase.invoke(any()) }
            checkIs<SeriesEditNavAction.NavigateBack>(viewModel.navActions.value)
        }

    @Test
    fun `SaveClicked shows error when use case fails`() =
        runTest {
            val fixture = createFixture()
            everySuspend { fixture.seriesRepository.getById("series-1") } returns createSeries()
            everySuspend { fixture.seriesRepository.getBookIdsForSeries("series-1") } returns listOf("book-1")
            everySuspend { fixture.imageRepository.seriesCoverExists("series-1") } returns false
            everySuspend { fixture.updateSeriesUseCase.invoke(any()) } returns
                Failure(exception = Exception("Save failed"), message = "Save failed")

            val viewModel = fixture.build()
            viewModel.loadSeries("series-1")
            advanceUntilIdle()
            viewModel.onEvent(SeriesEditUiEvent.NameChanged("Updated Name"))

            viewModel.onEvent(SeriesEditUiEvent.SaveClicked)
            advanceUntilIdle()

            assertEquals("Failed to save: Save failed", viewModel.state.value.error)
            assertNull(viewModel.navActions.value)
        }

    // ========== Cancel Tests ==========

    @Test
    fun `CancelClicked navigates back`() =
        runTest {
            val fixture = createFixture()
            everySuspend { fixture.seriesRepository.getById("series-1") } returns createSeries()
            everySuspend { fixture.seriesRepository.getBookIdsForSeries("series-1") } returns listOf("book-1")
            everySuspend { fixture.imageRepository.seriesCoverExists("series-1") } returns false
            everySuspend { fixture.imageRepository.deleteSeriesCoverStaging(any()) } returns Result.Success(Unit)

            val viewModel = fixture.build()
            viewModel.loadSeries("series-1")
            advanceUntilIdle()

            viewModel.onEvent(SeriesEditUiEvent.CancelClicked)
            advanceUntilIdle()

            checkIs<SeriesEditNavAction.NavigateBack>(viewModel.navActions.value)
        }

    // ========== Error Handling Tests ==========

    @Test
    fun `ErrorDismissed clears error`() =
        runTest {
            val fixture = createFixture()
            everySuspend { fixture.seriesRepository.getById("missing") } returns null

            val viewModel = fixture.build()
            viewModel.loadSeries("missing")
            advanceUntilIdle()
            assertTrue(viewModel.state.value.error != null)

            viewModel.onEvent(SeriesEditUiEvent.ErrorDismissed)

            assertNull(viewModel.state.value.error)
        }

    // ========== Navigation Tests ==========

    @Test
    fun `consumeNavAction clears navigation action`() =
        runTest {
            val fixture = createFixture()
            everySuspend { fixture.seriesRepository.getById("series-1") } returns createSeries()
            everySuspend { fixture.seriesRepository.getBookIdsForSeries("series-1") } returns listOf("book-1")
            everySuspend { fixture.imageRepository.seriesCoverExists("series-1") } returns false

            val viewModel = fixture.build()
            viewModel.loadSeries("series-1")
            advanceUntilIdle()
            viewModel.onEvent(SeriesEditUiEvent.SaveClicked)
            advanceUntilIdle()
            assertTrue(viewModel.navActions.value != null)

            viewModel.consumeNavAction()

            assertNull(viewModel.navActions.value)
        }
}
