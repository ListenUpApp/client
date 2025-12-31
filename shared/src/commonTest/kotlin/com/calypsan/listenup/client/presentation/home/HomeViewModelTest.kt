package com.calypsan.listenup.client.presentation.home

import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.data.local.db.LensDao
import com.calypsan.listenup.client.data.local.db.UserEntity
import com.calypsan.listenup.client.data.repository.HomeRepositoryContract
import com.calypsan.listenup.client.domain.model.ContinueListeningBook
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verifySuspend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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

/**
 * Tests for HomeViewModel.
 *
 * Tests cover:
 * - Initial state and init block behavior
 * - Loading continue listening books (success/error)
 * - User observation and name extraction
 * - Greeting generation (userName updates)
 * - Refresh functionality
 * - State derived properties
 *
 * Uses Mokkery for mocking HomeRepositoryContract.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    // ========== Test Fixtures ==========

    private class TestFixture {
        val homeRepository: HomeRepositoryContract = mock()
        val lensDao: LensDao = mock()
        val userFlow = MutableStateFlow<UserEntity?>(null)
        var currentHour: Int = 10 // Default to morning

        fun build(): HomeViewModel =
            HomeViewModel(
                homeRepository = homeRepository,
                lensDao = lensDao,
                currentHour = { currentHour },
            )
    }

    private fun createFixture(): TestFixture {
        val fixture = TestFixture()

        // Default stubs
        every { fixture.homeRepository.observeCurrentUser() } returns fixture.userFlow
        everySuspend { fixture.homeRepository.getContinueListening(any()) } returns Success(emptyList())
        every { fixture.lensDao.observeMyLenses(any()) } returns flowOf(emptyList())

        return fixture
    }

    // ========== Test Data Factories ==========

    private fun createUser(
        id: String = "user-1",
        email: String = "john@example.com",
        displayName: String = "John Smith",
        isRoot: Boolean = false,
    ): UserEntity =
        UserEntity(
            id = id,
            email = email,
            displayName = displayName,
            isRoot = isRoot,
            createdAt = 1704067200000L,
            updatedAt = 1704067200000L,
        )

    private fun createContinueListeningBook(
        bookId: String = "book-1",
        title: String = "Test Book",
        authorNames: String = "Test Author",
        progress: Float = 0.5f,
        currentPositionMs: Long = 1_800_000L,
        totalDurationMs: Long = 3_600_000L,
    ): ContinueListeningBook =
        ContinueListeningBook(
            bookId = bookId,
            title = title,
            authorNames = authorNames,
            coverPath = null,
            progress = progress,
            currentPositionMs = currentPositionMs,
            totalDurationMs = totalDurationMs,
            lastPlayedAt = "2024-01-01T00:00:00Z",
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
    fun `init triggers loadHomeData and observeUser`() =
        runTest {
            // Given
            val fixture = createFixture()

            // When - viewModel created, init block runs
            val viewModel = fixture.build()
            advanceUntilIdle()

            // Then - getContinueListening should have been called
            verifySuspend { fixture.homeRepository.getContinueListening(10) }
        }

    @Test
    fun `initial state has isLoading false after init completes`() =
        runTest {
            // Given
            val fixture = createFixture()

            // When
            val viewModel = fixture.build()
            advanceUntilIdle()

            // Then
            assertFalse(viewModel.state.value.isLoading)
        }

    // ========== Load Home Data Tests ==========

    @Test
    fun `loadHomeData success populates continueListening`() =
        runTest {
            // Given
            val fixture = createFixture()
            val books =
                listOf(
                    createContinueListeningBook(bookId = "book-1", title = "Book 1"),
                    createContinueListeningBook(bookId = "book-2", title = "Book 2"),
                )
            everySuspend { fixture.homeRepository.getContinueListening(any()) } returns Success(books)
            val viewModel = fixture.build()
            advanceUntilIdle()

            // Then
            val state = viewModel.state.value
            assertFalse(state.isLoading)
            assertEquals(2, state.continueListening.size)
            assertEquals("Book 1", state.continueListening[0].title)
            assertNull(state.error)
        }

    @Test
    fun `loadHomeData error sets error state`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.homeRepository.getContinueListening(any()) } returns Failure(Exception("Network error"))
            val viewModel = fixture.build()
            advanceUntilIdle()

            // Then
            val state = viewModel.state.value
            assertFalse(state.isLoading)
            assertEquals("Failed to load continue listening", state.error)
        }

    @Test
    fun `loadHomeData clears previous error on success`() =
        runTest {
            // Given - start with error
            val fixture = createFixture()
            everySuspend { fixture.homeRepository.getContinueListening(any()) } returns Failure(Exception("Error"))
            val viewModel = fixture.build()
            advanceUntilIdle()
            assertEquals("Failed to load continue listening", viewModel.state.value.error)

            // When - load succeeds
            everySuspend { fixture.homeRepository.getContinueListening(any()) } returns Success(emptyList())
            viewModel.refresh()
            advanceUntilIdle()

            // Then
            assertNull(viewModel.state.value.error)
        }

    // ========== User Observation Tests ==========

    @Test
    fun `observeUser updates userName from displayName`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()
            advanceUntilIdle()
            assertEquals("", viewModel.state.value.userName)

            // When
            fixture.userFlow.value = createUser(displayName = "John Smith")
            advanceUntilIdle()

            // Then - first name extracted
            assertEquals("John", viewModel.state.value.userName)
        }

    @Test
    fun `observeUser extracts first name only`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()
            advanceUntilIdle()

            // When
            fixture.userFlow.value = createUser(displayName = "Jane Doe Johnson")
            advanceUntilIdle()

            // Then
            assertEquals("Jane", viewModel.state.value.userName)
        }

    @Test
    fun `observeUser handles null user`() =
        runTest {
            // Given
            val fixture = createFixture()
            fixture.userFlow.value = createUser(displayName = "John")
            val viewModel = fixture.build()
            advanceUntilIdle()
            assertEquals("John", viewModel.state.value.userName)

            // When
            fixture.userFlow.value = null
            advanceUntilIdle()

            // Then
            assertEquals("", viewModel.state.value.userName)
        }

    @Test
    fun `observeUser handles blank displayName`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()
            advanceUntilIdle()

            // When
            fixture.userFlow.value = createUser(displayName = "   ")
            advanceUntilIdle()

            // Then
            assertEquals("", viewModel.state.value.userName)
        }

    @Test
    fun `observeUser handles single name`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()
            advanceUntilIdle()

            // When
            fixture.userFlow.value = createUser(displayName = "Madonna")
            advanceUntilIdle()

            // Then
            assertEquals("Madonna", viewModel.state.value.userName)
        }

    // ========== Refresh Tests ==========

    @Test
    fun `refresh calls loadHomeData`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()
            advanceUntilIdle()

            // When
            viewModel.refresh()
            advanceUntilIdle()

            // Then - getContinueListening called twice (init + refresh)
            verifySuspend { fixture.homeRepository.getContinueListening(10) }
        }

    // ========== State Derived Properties Tests ==========

    @Test
    fun `hasContinueListening is true when list not empty`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.homeRepository.getContinueListening(any()) } returns
                Success(
                    listOf(createContinueListeningBook()),
                )
            val viewModel = fixture.build()
            advanceUntilIdle()

            // Then
            assertTrue(viewModel.state.value.hasContinueListening)
        }

    @Test
    fun `hasContinueListening is false when list empty`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.homeRepository.getContinueListening(any()) } returns Success(emptyList())
            val viewModel = fixture.build()
            advanceUntilIdle()

            // Then
            assertFalse(viewModel.state.value.hasContinueListening)
        }

    @Test
    fun `greeting includes userName when available`() =
        runTest {
            // Given
            val fixture = createFixture()
            fixture.currentHour = 10 // Morning
            fixture.userFlow.value = createUser(displayName = "Alice")
            val viewModel = fixture.build()
            advanceUntilIdle()

            // Then
            assertEquals("Good morning, Alice", viewModel.state.value.greeting)
        }

    @Test
    fun `greeting without userName is time-only`() =
        runTest {
            // Given
            val fixture = createFixture()
            fixture.currentHour = 14 // Afternoon
            val viewModel = fixture.build()
            advanceUntilIdle()

            // Then
            assertEquals("Good afternoon", viewModel.state.value.greeting)
        }

    // ========== Time-Based Greeting Tests ==========

    @Test
    fun `greeting is morning between 5 and 11`() =
        runTest {
            // Given
            val fixture = createFixture()
            fixture.currentHour = 8
            val viewModel = fixture.build()
            advanceUntilIdle()

            // Then
            assertEquals("Good morning", viewModel.state.value.greeting)
        }

    @Test
    fun `greeting is afternoon between 12 and 16`() =
        runTest {
            // Given
            val fixture = createFixture()
            fixture.currentHour = 14
            val viewModel = fixture.build()
            advanceUntilIdle()

            // Then
            assertEquals("Good afternoon", viewModel.state.value.greeting)
        }

    @Test
    fun `greeting is evening between 17 and 20`() =
        runTest {
            // Given
            val fixture = createFixture()
            fixture.currentHour = 19
            val viewModel = fixture.build()
            advanceUntilIdle()

            // Then
            assertEquals("Good evening", viewModel.state.value.greeting)
        }

    @Test
    fun `greeting is night after 21`() =
        runTest {
            // Given
            val fixture = createFixture()
            fixture.currentHour = 23
            val viewModel = fixture.build()
            advanceUntilIdle()

            // Then
            assertEquals("Good night", viewModel.state.value.greeting)
        }

    @Test
    fun `greeting is night before 5`() =
        runTest {
            // Given
            val fixture = createFixture()
            fixture.currentHour = 3
            val viewModel = fixture.build()
            advanceUntilIdle()

            // Then
            assertEquals("Good night", viewModel.state.value.greeting)
        }
}
