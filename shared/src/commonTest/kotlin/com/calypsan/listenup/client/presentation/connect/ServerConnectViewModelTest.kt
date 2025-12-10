package com.calypsan.listenup.client.presentation.connect

import com.calypsan.listenup.client.core.error.ServerConnectError
import com.calypsan.listenup.client.data.repository.SettingsRepositoryContract
import dev.mokkery.mock
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

/**
 * Tests for ServerConnectViewModel.
 *
 * Tests cover:
 * - Initial state
 * - URL change events and state updates
 * - URL validation (blank, malformed)
 * - isConnectEnabled derived property
 * - Error clearing behavior
 *
 * Note: Network verification tests are not included because HttpClient
 * is created internally. Those paths would require integration testing
 * or dependency injection refactoring.
 *
 * Uses Mokkery for mocking SettingsRepositoryContract.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ServerConnectViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    // ========== Test Fixtures ==========

    private class TestFixture {
        val settingsRepository: SettingsRepositoryContract = mock()

        fun build(): ServerConnectViewModel = ServerConnectViewModel(
            settingsRepository = settingsRepository,
        )
    }

    private fun createFixture(): TestFixture = TestFixture()

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
    fun `initial state has empty URL and no loading`() = runTest {
        // Given
        val fixture = createFixture()
        val viewModel = fixture.build()

        // Then
        val state = viewModel.state.value
        assertEquals("", state.serverUrl)
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertFalse(state.isVerified)
    }

    @Test
    fun `initial state has connect button disabled`() = runTest {
        // Given
        val fixture = createFixture()
        val viewModel = fixture.build()

        // Then - empty URL means connect disabled
        assertFalse(viewModel.state.value.isConnectEnabled)
    }

    // ========== URL Changed Event Tests ==========

    @Test
    fun `UrlChanged updates serverUrl in state`() = runTest {
        // Given
        val fixture = createFixture()
        val viewModel = fixture.build()

        // When
        viewModel.onEvent(ServerConnectUiEvent.UrlChanged("https://example.com"))

        // Then
        assertEquals("https://example.com", viewModel.state.value.serverUrl)
    }

    @Test
    fun `UrlChanged clears existing error`() = runTest {
        // Given - start with an error by trying to connect with blank URL
        val fixture = createFixture()
        val viewModel = fixture.build()
        viewModel.onEvent(ServerConnectUiEvent.ConnectClicked)
        advanceUntilIdle()
        assertIs<ServerConnectError.InvalidUrl>(viewModel.state.value.error)

        // When - user starts typing
        viewModel.onEvent(ServerConnectUiEvent.UrlChanged("h"))

        // Then - error is cleared
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `UrlChanged enables connect button when URL not blank`() = runTest {
        // Given
        val fixture = createFixture()
        val viewModel = fixture.build()
        assertFalse(viewModel.state.value.isConnectEnabled)

        // When
        viewModel.onEvent(ServerConnectUiEvent.UrlChanged("test"))

        // Then
        assertTrue(viewModel.state.value.isConnectEnabled)
    }

    // ========== Connect Clicked Validation Tests ==========

    @Test
    fun `ConnectClicked with blank URL shows invalid URL error`() = runTest {
        // Given
        val fixture = createFixture()
        val viewModel = fixture.build()

        // When - click connect with empty URL
        viewModel.onEvent(ServerConnectUiEvent.ConnectClicked)
        advanceUntilIdle()

        // Then
        val error = viewModel.state.value.error
        assertIs<ServerConnectError.InvalidUrl>(error)
        assertEquals("blank", error.reason)
        assertEquals("Please enter a server URL", error.message)
    }

    @Test
    fun `ConnectClicked with whitespace-only URL shows invalid URL error`() = runTest {
        // Given
        val fixture = createFixture()
        val viewModel = fixture.build()
        viewModel.onEvent(ServerConnectUiEvent.UrlChanged("   "))

        // When
        viewModel.onEvent(ServerConnectUiEvent.ConnectClicked)
        advanceUntilIdle()

        // Then
        val error = viewModel.state.value.error
        assertIs<ServerConnectError.InvalidUrl>(error)
        assertEquals("blank", error.reason)
    }

    // Note: URL format validation is delegated to Ktor's URL parser.
    // Ktor is quite lenient with URLs, so testing specific malformed patterns
    // would be testing Ktor's behavior rather than our business logic.
    // The validation tests for blank/whitespace URLs cover our validation logic.

    // ========== isConnectEnabled Tests ==========

    @Test
    fun `isConnectEnabled is false when URL is blank`() {
        val state = ServerConnectUiState(serverUrl = "", isLoading = false)
        assertFalse(state.isConnectEnabled)
    }

    @Test
    fun `isConnectEnabled is false when loading`() {
        val state = ServerConnectUiState(serverUrl = "https://example.com", isLoading = true)
        assertFalse(state.isConnectEnabled)
    }

    @Test
    fun `isConnectEnabled is true when URL present and not loading`() {
        val state = ServerConnectUiState(serverUrl = "https://example.com", isLoading = false)
        assertTrue(state.isConnectEnabled)
    }

    @Test
    fun `isConnectEnabled is false when URL is whitespace only`() {
        val state = ServerConnectUiState(serverUrl = "   ", isLoading = false)
        assertFalse(state.isConnectEnabled)
    }

    // ========== Error State Tests ==========

    @Test
    fun `error does not block further URL changes`() = runTest {
        // Given - start with error
        val fixture = createFixture()
        val viewModel = fixture.build()
        viewModel.onEvent(ServerConnectUiEvent.ConnectClicked)
        advanceUntilIdle()
        assertIs<ServerConnectError.InvalidUrl>(viewModel.state.value.error)

        // When - change URL
        viewModel.onEvent(ServerConnectUiEvent.UrlChanged("new-url"))

        // Then - URL updated and error cleared
        assertEquals("new-url", viewModel.state.value.serverUrl)
        assertNull(viewModel.state.value.error)
    }

    // ========== ServerConnectUiState Property Tests ==========

    @Test
    fun `ServerConnectUiState defaults are correct`() {
        val state = ServerConnectUiState()
        assertEquals("", state.serverUrl)
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertFalse(state.isVerified)
    }
}
