@file:Suppress("MagicNumber")

package com.calypsan.listenup.client.deeplink

import android.content.Intent
import android.net.Uri
import com.calypsan.listenup.client.data.repository.InviteDeepLink

/**
 * Parsed book deep link: listenup://book/{bookId}
 */
data class BookDeepLink(val bookId: String)

/**
 * Parses deep link intents into structured data.
 *
 * Handles two types of invite URLs:
 *
 * 1. HTTPS App Links (verified domain):
 *    https://audiobooks.example.com/join/ABC123
 *    - Requires assetlinks.json verification
 *    - Opens directly in app if verified
 *
 * 2. Custom scheme (fallback):
 *    listenup://join?server=https://audiobooks.example.com&code=ABC123
 *    - Always opens in app
 *    - Used as fallback when App Links don't work
 */
object DeepLinkParser {
    private const val CUSTOM_SCHEME = "listenup"
    private const val JOIN_HOST = "join"
    private const val BOOK_HOST = "book"
    private const val JOIN_PATH_PREFIX = "/join/"

    /**
     * Parses an intent into an InviteDeepLink if it contains a valid invite URL.
     *
     * @param intent The incoming intent (from onCreate or onNewIntent)
     * @return Parsed invite data, or null if not a valid invite deep link
     */
    fun parse(intent: Intent?): InviteDeepLink? {
        if (intent?.action != Intent.ACTION_VIEW) return null

        val uri = intent.data ?: return null
        return parseUri(uri)
    }

    /**
     * Parses a book deep link from an intent.
     * Handles: listenup://book/{bookId}
     */
    fun parseBookDeepLink(intent: Intent?): BookDeepLink? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val uri = intent.data ?: return null
        if (uri.scheme?.lowercase() != CUSTOM_SCHEME) return null
        if (uri.host?.lowercase() != BOOK_HOST) return null
        val bookId = uri.pathSegments?.firstOrNull()?.takeIf { it.isNotBlank() } ?: return null
        return BookDeepLink(bookId)
    }

    /**
     * Parses a URI into an InviteDeepLink.
     *
     * Handles:
     * - https://server.com/join/CODE
     * - http://server.com/join/CODE (for local dev)
     * - listenup://join?server=URL&code=CODE
     *
     * @param uri The deep link URI
     * @return Parsed invite data, or null if not a valid invite URL
     */
    fun parseUri(uri: Uri): InviteDeepLink? =
        when (uri.scheme?.lowercase()) {
            CUSTOM_SCHEME -> parseCustomScheme(uri)
            "https", "http" -> parseHttpsScheme(uri)
            else -> null
        }

    /**
     * Parses custom scheme: listenup://join?server=URL&code=CODE
     */
    private fun parseCustomScheme(uri: Uri): InviteDeepLink? {
        if (uri.host?.lowercase() != JOIN_HOST) return null

        val serverUrl = uri.getQueryParameter("server") ?: return null
        val code = uri.getQueryParameter("code") ?: return null

        if (serverUrl.isBlank() || code.isBlank()) return null

        return InviteDeepLink(
            serverUrl = serverUrl,
            code = code,
        )
    }

    /**
     * Parses HTTPS scheme: https://server.com/join/CODE
     *
     * Extracts:
     * - Server URL from scheme + host + port
     * - Invite code from path after /join/
     */
    private fun parseHttpsScheme(uri: Uri): InviteDeepLink? {
        val path = uri.path ?: return null

        // Must start with /join/
        if (!path.startsWith(JOIN_PATH_PREFIX)) return null

        // Extract code from path
        val code = path.removePrefix(JOIN_PATH_PREFIX).takeIf { it.isNotBlank() } ?: return null

        // Build server URL (scheme + host + optional port)
        val serverUrl = buildServerUrl(uri) ?: return null

        return InviteDeepLink(
            serverUrl = serverUrl,
            code = code,
        )
    }

    /**
     * Builds the server base URL from a URI.
     * Includes scheme, host, and port (if non-standard).
     */
    private fun buildServerUrl(uri: Uri): String? {
        val scheme = uri.scheme ?: return null
        val host = uri.host ?: return null
        val port = uri.port

        return buildString {
            append(scheme)
            append("://")
            append(host)
            if (port != -1 && !isStandardPort(scheme, port)) {
                append(":")
                append(port)
            }
        }
    }

    /**
     * Checks if the port is the standard port for the scheme.
     */
    private fun isStandardPort(
        scheme: String,
        port: Int,
    ): Boolean =
        when (scheme.lowercase()) {
            "https" -> port == 443
            "http" -> port == 80
            else -> false
        }
}
