package com.calypsan.listenup.client.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Tests for type-safe value classes.
 *
 * Tests cover:
 * - AccessToken validation
 * - RefreshToken validation
 * - ServerUrl validation and normalization
 */
class ValueClassesTest {
    // ========== AccessToken Tests ==========

    @Test
    fun `AccessToken accepts valid token`() {
        val token = AccessToken("v4.public.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIn0")
        assertEquals("v4.public.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIn0", token.value)
    }

    @Test
    fun `AccessToken rejects blank token`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            AccessToken("")
        }
        assertEquals("Access token cannot be blank", exception.message)
    }

    @Test
    fun `AccessToken rejects whitespace-only token`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            AccessToken("   ")
        }
        assertEquals("Access token cannot be blank", exception.message)
    }

    // ========== RefreshToken Tests ==========

    @Test
    fun `RefreshToken accepts valid token`() {
        val token = RefreshToken("refresh_abc123xyz")
        assertEquals("refresh_abc123xyz", token.value)
    }

    @Test
    fun `RefreshToken rejects blank token`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            RefreshToken("")
        }
        assertEquals("Refresh token cannot be blank", exception.message)
    }

    @Test
    fun `RefreshToken rejects whitespace-only token`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            RefreshToken("   ")
        }
        assertEquals("Refresh token cannot be blank", exception.message)
    }

    // ========== ServerUrl Tests ==========

    @Test
    fun `ServerUrl accepts https URL`() {
        val url = ServerUrl("https://api.example.com")
        assertEquals("https://api.example.com", url.value)
    }

    @Test
    fun `ServerUrl accepts http URL`() {
        val url = ServerUrl("http://localhost:8080")
        assertEquals("http://localhost:8080", url.value)
    }

    @Test
    fun `ServerUrl removes trailing slash`() {
        val url = ServerUrl("https://api.example.com/")
        assertEquals("https://api.example.com", url.value)
    }

    @Test
    fun `ServerUrl removes multiple trailing slashes`() {
        val url = ServerUrl("https://api.example.com///")
        assertEquals("https://api.example.com", url.value)
    }

    @Test
    fun `ServerUrl preserves path without trailing slash`() {
        val url = ServerUrl("https://api.example.com/v1/api")
        assertEquals("https://api.example.com/v1/api", url.value)
    }

    @Test
    fun `ServerUrl removes trailing slash from path`() {
        val url = ServerUrl("https://api.example.com/v1/api/")
        assertEquals("https://api.example.com/v1/api", url.value)
    }

    @Test
    fun `ServerUrl rejects blank URL`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            ServerUrl("")
        }
        assertEquals("Server URL cannot be blank", exception.message)
    }

    @Test
    fun `ServerUrl rejects whitespace-only URL`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            ServerUrl("   ")
        }
        assertEquals("Server URL cannot be blank", exception.message)
    }

    @Test
    fun `ServerUrl rejects URL without protocol`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            ServerUrl("api.example.com")
        }
        assertEquals("Server URL must start with http:// or https://, got: api.example.com", exception.message)
    }

    @Test
    fun `ServerUrl rejects URL with ftp protocol`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            ServerUrl("ftp://files.example.com")
        }
        assertEquals("Server URL must start with http:// or https://, got: ftp://files.example.com", exception.message)
    }

    @Test
    fun `ServerUrl toString returns normalized value`() {
        val url = ServerUrl("https://api.example.com/")
        assertEquals("https://api.example.com", url.toString())
    }

    @Test
    fun `ServerUrl with port number is preserved`() {
        val url = ServerUrl("http://localhost:3000")
        assertEquals("http://localhost:3000", url.value)
    }

    @Test
    fun `ServerUrl with query string is preserved`() {
        val url = ServerUrl("https://api.example.com?key=value")
        assertEquals("https://api.example.com?key=value", url.value)
    }
}
