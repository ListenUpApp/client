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
 * Server selection screen - shows discovered servers via mDNS.
 * User can select a discovered server or enter URL manually.
 */
@Serializable
data object ServerSelect : Route

/**
 * Server setup screen - manual URL entry when no servers discovered.
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
 * Match preview screen - preview Audible metadata before applying.
 *
 * Shows side-by-side comparison of current book metadata vs metadata
 * from Audible. User can confirm to apply the changes.
 *
 * @property bookId The unique ID of the book to update.
 * @property asin The Audible ASIN of the matched book.
 */
@Serializable
data class MatchPreview(
    val bookId: String,
    val asin: String,
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
 * Series edit screen - edit series metadata and cover.
 *
 * Allows editing name, description, and cover image for a series.
 *
 * @property seriesId The unique ID of the series to edit.
 */
@Serializable
data class SeriesEdit(
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

/**
 * Contributor edit screen - edit contributor metadata and manage aliases.
 *
 * Allows editing name, biography, website, dates, and adding/removing aliases.
 * Adding an alias from search results triggers a merge operation.
 *
 * @property contributorId The unique ID of the contributor to edit.
 */
@Serializable
data class ContributorEdit(
    val contributorId: String,
) : Route

/**
 * Invite registration screen - claim an invite and create account.
 *
 * Shown when the app is opened via an invite deep link.
 * User only needs to set a password; other details come from the invite.
 *
 * @property serverUrl The server URL from the invite link.
 * @property inviteCode The invite code from the URL.
 */
@Serializable
data class InviteRegistration(
    val serverUrl: String,
    val inviteCode: String,
) : Route

// Admin Routes

/**
 * Admin screen - combined users and invites management.
 *
 * Shows users list, pending invites, and invite action.
 * Only accessible to admin users (root or role=admin).
 */
@Serializable
data object Admin : Route

/**
 * Create invite screen - create a new invite.
 *
 * Form for name, email, role, and expiration.
 */
@Serializable
data object CreateInvite : Route

/**
 * Settings screen - app preferences and configuration.
 */
@Serializable
data object Settings : Route
