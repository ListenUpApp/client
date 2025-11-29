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
data class BookDetail(val bookId: String) : Route

/**
 * Series detail screen - displays series info and its books.
 *
 * @property seriesId The unique ID of the series to display.
 */
@Serializable
data class SeriesDetail(val seriesId: String) : Route
