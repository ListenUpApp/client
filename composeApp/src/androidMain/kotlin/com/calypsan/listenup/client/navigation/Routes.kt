package com.calypsan.listenup.client.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes for ListenUp Android app.
 *
 * Using Navigation 3 with Kotlinx Serialization for compile-time
 * safety and automatic argument passing.
 */

/**
 * Server setup screen - initial flow when no server URL configured.
 * User enters and verifies their ListenUp server URL.
 */
@Serializable
object ServerSetup

/**
 * Setup screen - root user creation when server has no users.
 * User creates the first admin account.
 */
@Serializable
object Setup

/**
 * Login screen - authentication when server is configured.
 * User enters credentials to access their account.
 */
@Serializable
object Login

/**
 * Library screen - main authenticated screen.
 * Shows user's audiobook collection.
 */
@Serializable
object Library
