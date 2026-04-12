package com.calypsan.listenup.client.data.remote

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.url
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins [isAuthEndpoint] semantics: `encodedPath.startsWith(AUTH_PATH_PREFIX)` rather than
 * the substring match the pre-W2b.4 code used. The substring form would false-positive on
 * any URL containing `/auth/` anywhere, including legitimate API paths like
 * `/api/v1/books/by-author/123`. See Finding 04 D2.
 */
class AuthEndpointPredicateTest {
    private fun request(url: String) = HttpRequestBuilder().apply { url(url) }

    @Test
    fun returnsTrueForLoginEndpoint() {
        assertTrue(isAuthEndpoint(request("https://server.example.com/api/v1/auth/login")))
    }

    @Test
    fun returnsTrueForRefreshEndpoint() {
        assertTrue(isAuthEndpoint(request("https://server.example.com/api/v1/auth/refresh")))
    }

    @Test
    fun returnsTrueForLogoutEndpoint() {
        assertTrue(isAuthEndpoint(request("https://server.example.com/api/v1/auth/logout")))
    }

    @Test
    fun returnsFalseForBooksEndpoint() {
        assertFalse(isAuthEndpoint(request("https://server.example.com/api/v1/books")))
    }

    @Test
    fun returnsFalseForUnrelatedPathContainingAuthSubstring() {
        // Pre-W2b.4 `urlString.contains("/auth/")` would match this path (`.../by-author/...`
        // contains `author`) — `encodedPath.startsWith` cannot false-positive.
        assertFalse(isAuthEndpoint(request("https://server.example.com/api/v1/books/by-author/tolkien")))
    }

    @Test
    fun returnsFalseForAuthSubstringOutsidePrefix() {
        assertFalse(isAuthEndpoint(request("https://server.example.com/something/api/v1/auth/login")))
    }
}
