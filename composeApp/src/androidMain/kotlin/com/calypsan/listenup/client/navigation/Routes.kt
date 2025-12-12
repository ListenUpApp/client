package com.calypsan.listenup.client.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes for ListenUp Android app.
 *
 * Using Navigation 3 with Kotlinx Serialization for compile-time
 * safety and automatic argument passing.
 *
 * All routes must implement the sealed [Route] interface to ensure
 * type safety in the navigation graph.
 */
@Serializable
sealed interface Route

/**
 * Server setup screen - initial flow when no server URL configured.
 * User enters and verifies their ListenUp server URL.
 */
@Serializable
data object ServerSetup : Route

/**
 * Setup screen - root user creation when server has no users.
 * User creates the first admin account.
 */
@Serializable
data object Setup : Route

/**
 * Login screen - authentication when server is configured.
 * User enters credentials to access their account.
 */
@Serializable
data object Login : Route

/**
 * App shell - main authenticated container.
 *
 * Contains the bottom navigation bar with Home, Library, and Discover tabs.
 * Tab switching is handled internally within the shell.
 */
@Serializable
data object Shell : Route

/**
 * Book detail screen - displays full book info and chapters.
 *
 * @property bookId The unique ID of the book to display.
 */
@Serializable
data class BookDetail(
    val bookId: String,
) : Route

/**
 * Book edit screen - edit book metadata and contributors.
 *
 * Allows editing title, subtitle, description, series info, publish year,
 * and managing contributors (authors/narrators) with autocomplete search.
 *
 * @property bookId The unique ID of the book to edit.
 */
@Serializable
data class BookEdit(
    val bookId: String,
) : Route

/**
 * Series detail screen - displays series info and its books.
 *
 * @property seriesId The unique ID of the series to display.
 */
@Serializable
data class SeriesDetail(
    val seriesId: String,
) : Route

/**
 * Contributor detail screen - displays contributor info and books by role.
 *
 * A contributor is a person who may have multiple roles (author, narrator, etc.)
 * across different books. This screen shows all their work grouped by role.
 *
 * @property contributorId The unique ID of the contributor to display.
 */
@Serializable
data class ContributorDetail(
    val contributorId: String,
) : Route

/**
 * Contributor books screen - displays all books for a contributor in a specific role.
 *
 * Shows books grouped by series with standalone books at the bottom.
 * Accessed via "View All" from the contributor detail screen.
 *
 * @property contributorId The contributor's unique ID.
 * @property role The role to filter by (e.g., "author", "narrator").
 */
@Serializable
data class ContributorBooks(
    val contributorId: String,
    val role: String,
) : Route
