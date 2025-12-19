package com.calypsan.listenup.client.presentation.seriesedit

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.data.local.db.BookEntity
import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.local.db.BookSeriesCrossRef
import com.calypsan.listenup.client.data.local.db.SeriesDao
import com.calypsan.listenup.client.data.local.db.SeriesEntity
import com.calypsan.listenup.client.data.local.db.SeriesWithBooks
import com.calypsan.listenup.client.data.local.db.SyncState
import com.calypsan.listenup.client.data.local.db.Timestamp
import com.calypsan.listenup.client.data.local.images.ImageStorage
import com.calypsan.listenup.client.data.remote.ImageApiContract
import com.calypsan.listenup.client.data.remote.SeriesEditResponse
import com.calypsan.listenup.client.data.repository.SeriesEditRepositoryContract
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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SeriesEditViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private fun createSeriesEntity(
        id: String = "series-1",
        name: String = "Test Series",
        description: String? = "A test series",
    ) = SeriesEntity(
        id = id,
        name = name,
        description = description,
        syncState = SyncState.SYNCED,
        lastModified = Timestamp(1000L),
        serverVersion = Timestamp(1000L),
        createdAt = Timestamp(1000L),
        updatedAt = Timestamp(1000L),
    )

    private fun createBookEntity(id: String = "book-1") = BookEntity(
        id = BookId(id),
        title = "Test Book",
        coverUrl = null,
        totalDuration = 3_600_000L,
        syncState = SyncState.SYNCED,
        lastModified = Timestamp(1000L),
        serverVersion = Timestamp(1000L),
        createdAt = Timestamp(1000L),
        updatedAt = Timestamp(1000L),
    )

    private fun createSeriesWithBooks(
        series: SeriesEntity = createSeriesEntity(),
        books: List<BookEntity> = listOf(createBookEntity()),
    ) = SeriesWithBooks(
        series = series,
        books = books,
        bookSequences = books.map { BookSeriesCrossRef(bookId = it.id, seriesId = series.id, sequence = "1") },
    )

    private fun createSeriesEditResponse() = SeriesEditResponse(
        id = "series-1",
        name = "Updated Name",
        description = "A test series",
        updatedAt = "2024-01-01T00:00:00Z",
    )

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is loading`() = runTest {
        val seriesDao: SeriesDao = mock()
        val seriesEditRepository: SeriesEditRepositoryContract = mock()
        val imageStorage: ImageStorage = mock()
        val imageApi: ImageApiContract = mock()

        val viewModel = SeriesEditViewModel(seriesDao, seriesEditRepository, imageStorage, imageApi)

        assertTrue(viewModel.state.value.isLoading)
    }

    @Test
    fun `loadSeries populates state with series data`() = runTest {
        val seriesDao: SeriesDao = mock()
        val seriesEditRepository: SeriesEditRepositoryContract = mock()
        val imageStorage: ImageStorage = mock()
        val imageApi: ImageApiContract = mock()
        val seriesWithBooks = createSeriesWithBooks()
        everySuspend { seriesDao.getByIdWithBooks("series-1") } returns seriesWithBooks
        everySuspend { imageStorage.seriesCoverExists("series-1") } returns false

        val viewModel = SeriesEditViewModel(seriesDao, seriesEditRepository, imageStorage, imageApi)
        viewModel.loadSeries("series-1")
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
        assertEquals("Test Series", viewModel.state.value.name)
        assertEquals("A test series", viewModel.state.value.description)
        assertEquals(1, viewModel.state.value.bookCount)
    }

    @Test
    fun `loadSeries shows error for missing series`() = runTest {
        val seriesDao: SeriesDao = mock()
        val seriesEditRepository: SeriesEditRepositoryContract = mock()
        val imageStorage: ImageStorage = mock()
        val imageApi: ImageApiContract = mock()
        everySuspend { seriesDao.getByIdWithBooks("missing") } returns null

        val viewModel = SeriesEditViewModel(seriesDao, seriesEditRepository, imageStorage, imageApi)
        viewModel.loadSeries("missing")
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
        assertEquals("Series not found", viewModel.state.value.error)
    }

    @Test
    fun `NameChanged updates state and tracks changes`() = runTest {
        val seriesDao: SeriesDao = mock()
        val seriesEditRepository: SeriesEditRepositoryContract = mock()
        val imageStorage: ImageStorage = mock()
        val imageApi: ImageApiContract = mock()
        everySuspend { seriesDao.getByIdWithBooks("series-1") } returns createSeriesWithBooks()
        everySuspend { imageStorage.seriesCoverExists("series-1") } returns false

        val viewModel = SeriesEditViewModel(seriesDao, seriesEditRepository, imageStorage, imageApi)
        viewModel.loadSeries("series-1")
        advanceUntilIdle()
        assertFalse(viewModel.state.value.hasChanges)

        viewModel.onEvent(SeriesEditUiEvent.NameChanged("New Name"))

        assertEquals("New Name", viewModel.state.value.name)
        assertTrue(viewModel.state.value.hasChanges)
    }

    @Test
    fun `DescriptionChanged updates state and tracks changes`() = runTest {
        val seriesDao: SeriesDao = mock()
        val seriesEditRepository: SeriesEditRepositoryContract = mock()
        val imageStorage: ImageStorage = mock()
        val imageApi: ImageApiContract = mock()
        everySuspend { seriesDao.getByIdWithBooks("series-1") } returns createSeriesWithBooks()
        everySuspend { imageStorage.seriesCoverExists("series-1") } returns false

        val viewModel = SeriesEditViewModel(seriesDao, seriesEditRepository, imageStorage, imageApi)
        viewModel.loadSeries("series-1")
        advanceUntilIdle()

        viewModel.onEvent(SeriesEditUiEvent.DescriptionChanged("New description"))

        assertEquals("New description", viewModel.state.value.description)
        assertTrue(viewModel.state.value.hasChanges)
    }

    @Test
    fun `SaveClicked with no changes navigates back immediately`() = runTest {
        val seriesDao: SeriesDao = mock()
        val seriesEditRepository: SeriesEditRepositoryContract = mock()
        val imageStorage: ImageStorage = mock()
        val imageApi: ImageApiContract = mock()
        everySuspend { seriesDao.getByIdWithBooks("series-1") } returns createSeriesWithBooks()
        everySuspend { imageStorage.seriesCoverExists("series-1") } returns false

        val viewModel = SeriesEditViewModel(seriesDao, seriesEditRepository, imageStorage, imageApi)
        viewModel.loadSeries("series-1")
        advanceUntilIdle()

        viewModel.onEvent(SeriesEditUiEvent.SaveClicked)
        advanceUntilIdle()

        assertIs<SeriesEditNavAction.NavigateBack>(viewModel.navActions.value)
    }

    @Test
    fun `SaveClicked with changes calls repository`() = runTest {
        val seriesDao: SeriesDao = mock()
        val seriesEditRepository: SeriesEditRepositoryContract = mock()
        val imageStorage: ImageStorage = mock()
        val imageApi: ImageApiContract = mock()
        everySuspend { seriesDao.getByIdWithBooks("series-1") } returns createSeriesWithBooks()
        everySuspend { imageStorage.seriesCoverExists("series-1") } returns false
        everySuspend { seriesEditRepository.updateSeries(any(), any(), any()) } returns Result.Success(createSeriesEditResponse())

        val viewModel = SeriesEditViewModel(seriesDao, seriesEditRepository, imageStorage, imageApi)
        viewModel.loadSeries("series-1")
        advanceUntilIdle()
        viewModel.onEvent(SeriesEditUiEvent.NameChanged("Updated Name"))

        viewModel.onEvent(SeriesEditUiEvent.SaveClicked)
        advanceUntilIdle()

        verifySuspend { seriesEditRepository.updateSeries("series-1", "Updated Name", any()) }
        assertIs<SeriesEditNavAction.NavigateBack>(viewModel.navActions.value)
    }

    @Test
    fun `CancelClicked navigates back`() = runTest {
        val seriesDao: SeriesDao = mock()
        val seriesEditRepository: SeriesEditRepositoryContract = mock()
        val imageStorage: ImageStorage = mock()
        val imageApi: ImageApiContract = mock()
        everySuspend { seriesDao.getByIdWithBooks("series-1") } returns createSeriesWithBooks()
        everySuspend { imageStorage.seriesCoverExists("series-1") } returns false
        everySuspend { imageStorage.deleteSeriesCoverStaging(any()) } returns Result.Success(Unit)

        val viewModel = SeriesEditViewModel(seriesDao, seriesEditRepository, imageStorage, imageApi)
        viewModel.loadSeries("series-1")
        advanceUntilIdle()

        viewModel.onEvent(SeriesEditUiEvent.CancelClicked)
        advanceUntilIdle()

        assertIs<SeriesEditNavAction.NavigateBack>(viewModel.navActions.value)
    }

    @Test
    fun `ErrorDismissed clears error`() = runTest {
        val seriesDao: SeriesDao = mock()
        val seriesEditRepository: SeriesEditRepositoryContract = mock()
        val imageStorage: ImageStorage = mock()
        val imageApi: ImageApiContract = mock()
        everySuspend { seriesDao.getByIdWithBooks("missing") } returns null

        val viewModel = SeriesEditViewModel(seriesDao, seriesEditRepository, imageStorage, imageApi)
        viewModel.loadSeries("missing")
        advanceUntilIdle()
        assertTrue(viewModel.state.value.error != null)

        viewModel.onEvent(SeriesEditUiEvent.ErrorDismissed)

        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `consumeNavAction clears navigation action`() = runTest {
        val seriesDao: SeriesDao = mock()
        val seriesEditRepository: SeriesEditRepositoryContract = mock()
        val imageStorage: ImageStorage = mock()
        val imageApi: ImageApiContract = mock()
        everySuspend { seriesDao.getByIdWithBooks("series-1") } returns createSeriesWithBooks()
        everySuspend { imageStorage.seriesCoverExists("series-1") } returns false

        val viewModel = SeriesEditViewModel(seriesDao, seriesEditRepository, imageStorage, imageApi)
        viewModel.loadSeries("series-1")
        advanceUntilIdle()
        viewModel.onEvent(SeriesEditUiEvent.SaveClicked)
        advanceUntilIdle()
        assertTrue(viewModel.navActions.value != null)

        viewModel.consumeNavAction()

        assertNull(viewModel.navActions.value)
    }
}
