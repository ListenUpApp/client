package com.calypsan.listenup.client.presentation.settings

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.data.remote.UserPreferencesApiContract
import com.calypsan.listenup.client.data.remote.UserPreferencesRequest
import com.calypsan.listenup.client.data.remote.UserPreferencesResponse
import com.calypsan.listenup.client.data.repository.SettingsRepository
import com.calypsan.listenup.client.data.repository.SettingsRepositoryContract
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
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
import kotlin.test.assertTrue

/**
 * Tests for SettingsViewModel.
 *
 * Tests cover:
 * - Loading settings on init
 * - Updating individual settings
 * - Optimistic UI updates
 * - Server sync for playback speed
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    // ========== Test Fixture Builder ==========

    private class TestFixture {
        val settingsRepository: SettingsRepositoryContract = mock()
        val userPreferencesApi: UserPreferencesApiContract = mock()

        fun build(): SettingsViewModel =
            SettingsViewModel(
                settingsRepository = settingsRepository,
                userPreferencesApi = userPreferencesApi,
            )
    }

    private fun createFixture(): TestFixture {
        val fixture = TestFixture()

        // Default stubs for settings repository
        everySuspend { fixture.settingsRepository.getDefaultPlaybackSpeed() } returns SettingsRepository.DEFAULT_PLAYBACK_SPEED
        everySuspend { fixture.settingsRepository.getSpatialPlayback() } returns true
        everySuspend { fixture.settingsRepository.getIgnoreTitleArticles() } returns true
        everySuspend { fixture.settingsRepository.getHideSingleBookSeries() } returns true

        return fixture
    }

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ========== Init / Loading Tests ==========

    @Test
    fun `initial state is loading`() = runTest {
        // Given
        val fixture = createFixture()

        // When
        val viewModel = fixture.build()

        // Then - before advanceUntilIdle, should be loading
        assertTrue(viewModel.state.value.isLoading)
    }

    @Test
    fun `loads settings on init`() = runTest {
        // Given
        val fixture = createFixture()
        everySuspend { fixture.settingsRepository.getDefaultPlaybackSpeed() } returns 1.5f
        everySuspend { fixture.settingsRepository.getSpatialPlayback() } returns false
        everySuspend { fixture.settingsRepository.getIgnoreTitleArticles() } returns false
        everySuspend { fixture.settingsRepository.getHideSingleBookSeries() } returns false

        // When
        val viewModel = fixture.build()
        advanceUntilIdle()

        // Then
        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals(1.5f, state.defaultPlaybackSpeed)
        assertFalse(state.spatialPlayback)
        assertFalse(state.ignoreTitleArticles)
        assertFalse(state.hideSingleBookSeries)
    }

    @Test
    fun `uses default values when loading`() = runTest {
        // Given
        val fixture = createFixture()

        // When
        val viewModel = fixture.build()
        advanceUntilIdle()

        // Then - should use defaults
        val state = viewModel.state.value
        assertEquals(SettingsRepository.DEFAULT_PLAYBACK_SPEED, state.defaultPlaybackSpeed)
        assertTrue(state.spatialPlayback)
        assertTrue(state.ignoreTitleArticles)
        assertTrue(state.hideSingleBookSeries)
    }

    // ========== Playback Speed Tests ==========

    @Test
    fun `setDefaultPlaybackSpeed updates local cache and UI immediately`() = runTest {
        // Given
        val fixture = createFixture()
        everySuspend { fixture.settingsRepository.setDefaultPlaybackSpeed(1.25f) } returns Unit
        everySuspend { fixture.userPreferencesApi.updatePreferences(UserPreferencesRequest(defaultPlaybackSpeed = 1.25f)) } returns
            Result.Success(UserPreferencesResponse(defaultPlaybackSpeed = 1.25f))
        val viewModel = fixture.build()
        advanceUntilIdle()

        // When
        viewModel.setDefaultPlaybackSpeed(1.25f)
        advanceUntilIdle()

        // Then - local cache updated
        verifySuspend { fixture.settingsRepository.setDefaultPlaybackSpeed(1.25f) }
        assertEquals(1.25f, viewModel.state.value.defaultPlaybackSpeed)
    }

    @Test
    fun `setDefaultPlaybackSpeed syncs to server`() = runTest {
        // Given
        val fixture = createFixture()
        everySuspend { fixture.settingsRepository.setDefaultPlaybackSpeed(1.5f) } returns Unit
        everySuspend { fixture.userPreferencesApi.updatePreferences(UserPreferencesRequest(defaultPlaybackSpeed = 1.5f)) } returns
            Result.Success(UserPreferencesResponse(defaultPlaybackSpeed = 1.5f))
        val viewModel = fixture.build()
        advanceUntilIdle()

        // When
        viewModel.setDefaultPlaybackSpeed(1.5f)
        advanceUntilIdle()

        // Then - server sync called
        verifySuspend { fixture.userPreferencesApi.updatePreferences(UserPreferencesRequest(defaultPlaybackSpeed = 1.5f)) }
    }

    @Test
    fun `setDefaultPlaybackSpeed continues on server sync failure`() = runTest {
        // Given
        val fixture = createFixture()
        everySuspend { fixture.settingsRepository.setDefaultPlaybackSpeed(2.0f) } returns Unit
        everySuspend { fixture.userPreferencesApi.updatePreferences(UserPreferencesRequest(defaultPlaybackSpeed = 2.0f)) } returns
            Result.Failure(exception = Exception("Network error"), message = "Network error")
        val viewModel = fixture.build()
        advanceUntilIdle()

        // When
        viewModel.setDefaultPlaybackSpeed(2.0f)
        advanceUntilIdle()

        // Then - UI still updated (optimistic), no error shown
        assertEquals(2.0f, viewModel.state.value.defaultPlaybackSpeed)
    }

    // ========== Spatial Playback Tests ==========

    @Test
    fun `setSpatialPlayback updates setting`() = runTest {
        // Given
        val fixture = createFixture()
        everySuspend { fixture.settingsRepository.setSpatialPlayback(false) } returns Unit
        val viewModel = fixture.build()
        advanceUntilIdle()

        // When
        viewModel.setSpatialPlayback(false)
        advanceUntilIdle()

        // Then
        verifySuspend { fixture.settingsRepository.setSpatialPlayback(false) }
        assertFalse(viewModel.state.value.spatialPlayback)
    }

    @Test
    fun `setSpatialPlayback toggles correctly`() = runTest {
        // Given
        val fixture = createFixture()
        everySuspend { fixture.settingsRepository.getSpatialPlayback() } returns true
        everySuspend { fixture.settingsRepository.setSpatialPlayback(false) } returns Unit
        val viewModel = fixture.build()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.spatialPlayback)

        // When
        viewModel.setSpatialPlayback(false)
        advanceUntilIdle()

        // Then
        assertFalse(viewModel.state.value.spatialPlayback)
    }

    // ========== Ignore Title Articles Tests ==========

    @Test
    fun `setIgnoreTitleArticles updates setting`() = runTest {
        // Given
        val fixture = createFixture()
        everySuspend { fixture.settingsRepository.setIgnoreTitleArticles(false) } returns Unit
        val viewModel = fixture.build()
        advanceUntilIdle()

        // When
        viewModel.setIgnoreTitleArticles(false)
        advanceUntilIdle()

        // Then
        verifySuspend { fixture.settingsRepository.setIgnoreTitleArticles(false) }
        assertFalse(viewModel.state.value.ignoreTitleArticles)
    }

    // ========== Hide Single Book Series Tests ==========

    @Test
    fun `setHideSingleBookSeries updates setting`() = runTest {
        // Given
        val fixture = createFixture()
        everySuspend { fixture.settingsRepository.setHideSingleBookSeries(false) } returns Unit
        val viewModel = fixture.build()
        advanceUntilIdle()

        // When
        viewModel.setHideSingleBookSeries(false)
        advanceUntilIdle()

        // Then
        verifySuspend { fixture.settingsRepository.setHideSingleBookSeries(false) }
        assertFalse(viewModel.state.value.hideSingleBookSeries)
    }

    @Test
    fun `setHideSingleBookSeries can be enabled`() = runTest {
        // Given - start with false
        val fixture = createFixture()
        everySuspend { fixture.settingsRepository.getHideSingleBookSeries() } returns false
        everySuspend { fixture.settingsRepository.setHideSingleBookSeries(true) } returns Unit
        val viewModel = fixture.build()
        advanceUntilIdle()
        assertFalse(viewModel.state.value.hideSingleBookSeries)

        // When
        viewModel.setHideSingleBookSeries(true)
        advanceUntilIdle()

        // Then
        assertTrue(viewModel.state.value.hideSingleBookSeries)
    }
}
