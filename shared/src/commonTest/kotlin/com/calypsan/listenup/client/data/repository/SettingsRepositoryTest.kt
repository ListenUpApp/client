package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.core.AccessToken
import com.calypsan.listenup.client.core.RefreshToken
import com.calypsan.listenup.client.core.SecureStorage
import com.calypsan.listenup.client.core.ServerUrl
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for SettingsRepository.
 * Uses Mokkery for mocking SecureStorage.
 */
class SettingsRepositoryTest {

    private fun createMockStorage(): SecureStorage = mock<SecureStorage>()

    @Test
    fun `initial auth state is Loading`() = runTest {
        // Given
        val storage = createMockStorage()
        val repository = SettingsRepository(storage)

        // Then
        assertIs<AuthState.Loading>(repository.authState.value)
    }

    @Test
    fun `setServerUrl stores URL in secure storage`() = runTest {
        // Given
        val storage = createMockStorage()
        val repository = SettingsRepository(storage)
        val url = ServerUrl("https://api.example.com")
        everySuspend { storage.save("server_url", "https://api.example.com") } returns Unit

        // When
        repository.setServerUrl(url)

        // Then
        verifySuspend { storage.save("server_url", "https://api.example.com") }
    }

    @Test
    fun `getServerUrl returns stored URL`() = runTest {
        // Given
        val storage = createMockStorage()
        val repository = SettingsRepository(storage)
        everySuspend { storage.read("server_url") } returns "https://api.example.com"

        // When
        val result = repository.getServerUrl()

        // Then
        assertEquals(ServerUrl("https://api.example.com"), result)
    }

    @Test
    fun `getServerUrl returns null when not configured`() = runTest {
        // Given
        val storage = createMockStorage()
        val repository = SettingsRepository(storage)
        everySuspend { storage.read("server_url") } returns null

        // When
        val result = repository.getServerUrl()

        // Then
        assertNull(result)
    }

    @Test
    fun `saveAuthTokens stores all tokens and updates state to Authenticated`() = runTest {
        // Given
        val storage = createMockStorage()
        val repository = SettingsRepository(storage)
        everySuspend { storage.save(any(), any()) } returns Unit

        // When
        repository.saveAuthTokens(
            AccessToken("access123"),
            RefreshToken("refresh456"),
            "session789",
            "user001"
        )

        // Then
        verifySuspend { storage.save("access_token", "access123") }
        verifySuspend { storage.save("refresh_token", "refresh456") }
        verifySuspend { storage.save("session_id", "session789") }
        verifySuspend { storage.save("user_id", "user001") }

        val state = repository.authState.value
        assertIs<AuthState.Authenticated>(state)
        assertEquals("user001", state.userId)
        assertEquals("session789", state.sessionId)
    }

    @Test
    fun `getAccessToken returns stored token`() = runTest {
        // Given
        val storage = createMockStorage()
        val repository = SettingsRepository(storage)
        everySuspend { storage.read("access_token") } returns "access123"

        // When
        val result = repository.getAccessToken()

        // Then
        assertEquals(AccessToken("access123"), result)
    }

    @Test
    fun `getRefreshToken returns stored token`() = runTest {
        // Given
        val storage = createMockStorage()
        val repository = SettingsRepository(storage)
        everySuspend { storage.read("refresh_token") } returns "refresh456"

        // When
        val result = repository.getRefreshToken()

        // Then
        assertEquals(RefreshToken("refresh456"), result)
    }

    @Test
    fun `getSessionId returns stored session ID`() = runTest {
        // Given
        val storage = createMockStorage()
        val repository = SettingsRepository(storage)
        everySuspend { storage.read("session_id") } returns "session789"

        // When
        val result = repository.getSessionId()

        // Then
        assertEquals("session789", result)
    }

    @Test
    fun `getUserId returns stored user ID`() = runTest {
        // Given
        val storage = createMockStorage()
        val repository = SettingsRepository(storage)
        everySuspend { storage.read("user_id") } returns "user001"

        // When
        val result = repository.getUserId()

        // Then
        assertEquals("user001", result)
    }

    @Test
    fun `updateAccessToken updates only access token`() = runTest {
        // Given
        val storage = createMockStorage()
        val repository = SettingsRepository(storage)
        everySuspend { storage.save("access_token", "newAccess") } returns Unit

        // When
        repository.updateAccessToken(AccessToken("newAccess"))

        // Then
        verifySuspend { storage.save("access_token", "newAccess") }
    }

    @Test
    fun `clearAuthTokens removes all auth data and updates state to Unauthenticated`() = runTest {
        // Given
        val storage = createMockStorage()
        val repository = SettingsRepository(storage)
        everySuspend { storage.delete(any()) } returns Unit

        // When
        repository.clearAuthTokens()

        // Then
        verifySuspend { storage.delete("access_token") }
        verifySuspend { storage.delete("refresh_token") }
        verifySuspend { storage.delete("session_id") }
        verifySuspend { storage.delete("user_id") }

        assertIs<AuthState.Unauthenticated>(repository.authState.value)
    }

    @Test
    fun `clearAll removes all data and updates state to Unauthenticated`() = runTest {
        // Given
        val storage = createMockStorage()
        val repository = SettingsRepository(storage)
        everySuspend { storage.clear() } returns Unit

        // When
        repository.clearAll()

        // Then
        verifySuspend { storage.clear() }
        assertIs<AuthState.Unauthenticated>(repository.authState.value)
    }

    @Test
    fun `isAuthenticated returns true when access token exists`() = runTest {
        // Given
        val storage = createMockStorage()
        val repository = SettingsRepository(storage)
        everySuspend { storage.read("access_token") } returns "access123"

        // When
        val result = repository.isAuthenticated()

        // Then
        assertTrue(result)
    }

    @Test
    fun `isAuthenticated returns false when access token missing`() = runTest {
        // Given
        val storage = createMockStorage()
        val repository = SettingsRepository(storage)
        everySuspend { storage.read("access_token") } returns null

        // When
        val result = repository.isAuthenticated()

        // Then
        assertFalse(result)
    }

    @Test
    fun `hasServerConfigured returns true when URL configured`() = runTest {
        // Given
        val storage = createMockStorage()
        val repository = SettingsRepository(storage)
        everySuspend { storage.read("server_url") } returns "https://api.example.com"

        // When
        val result = repository.hasServerConfigured()

        // Then
        assertTrue(result)
    }

    @Test
    fun `hasServerConfigured returns false when URL not configured`() = runTest {
        // Given
        val storage = createMockStorage()
        val repository = SettingsRepository(storage)
        everySuspend { storage.read("server_url") } returns null

        // When
        val result = repository.hasServerConfigured()

        // Then
        assertFalse(result)
    }

    @Test
    fun `initializeAuthState sets Authenticated when tokens and IDs present`() = runTest {
        // Given
        val storage = createMockStorage()
        val repository = SettingsRepository(storage)
        everySuspend { storage.read("access_token") } returns "access123"
        everySuspend { storage.read("user_id") } returns "user001"
        everySuspend { storage.read("session_id") } returns "session789"

        // When
        repository.initializeAuthState()

        // Then
        val state = repository.authState.value
        assertIs<AuthState.Authenticated>(state)
        assertEquals("user001", state.userId)
        assertEquals("session789", state.sessionId)
    }

    @Test
    fun `initializeAuthState sets Unauthenticated when tokens missing`() = runTest {
        // Given
        val storage = createMockStorage()
        val repository = SettingsRepository(storage)
        everySuspend { storage.read("access_token") } returns null
        everySuspend { storage.read("user_id") } returns null
        everySuspend { storage.read("session_id") } returns null

        // When
        repository.initializeAuthState()

        // Then
        assertIs<AuthState.Unauthenticated>(repository.authState.value)
    }

    @Test
    fun `initializeAuthState sets Unauthenticated when userId missing`() = runTest {
        // Given
        val storage = createMockStorage()
        val repository = SettingsRepository(storage)
        everySuspend { storage.read("access_token") } returns "access123"
        everySuspend { storage.read("user_id") } returns null
        everySuspend { storage.read("session_id") } returns "session789"

        // When
        repository.initializeAuthState()

        // Then
        assertIs<AuthState.Unauthenticated>(repository.authState.value)
    }

    @Test
    fun `initializeAuthState sets Unauthenticated when sessionId missing`() = runTest {
        // Given
        val storage = createMockStorage()
        val repository = SettingsRepository(storage)
        everySuspend { storage.read("access_token") } returns "access123"
        everySuspend { storage.read("user_id") } returns "user001"
        everySuspend { storage.read("session_id") } returns null

        // When
        repository.initializeAuthState()

        // Then
        assertIs<AuthState.Unauthenticated>(repository.authState.value)
    }
}
