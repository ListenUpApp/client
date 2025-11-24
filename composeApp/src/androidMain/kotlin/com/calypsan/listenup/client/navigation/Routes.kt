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
 * Library screen - main authenticated screen.
 * Shows user's audiobook collection.
 */
@Serializable
data object Library : Route
