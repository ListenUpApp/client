package com.calypsan.listenup.client.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes for ListenUp Android app.
 *
 * Using Navigation 3 with Kotlinx Serialization for compile-time
 * safety and automatic argument passing.
 */

/**
 * Server setup screen - first step in unauthenticated flow.
 * User enters and verifies their ListenUp server URL.
 */
@Serializable
object ServerSetup

/**
 * Login screen - second step in unauthenticated flow.
 * User enters credentials after server is verified.
 */
@Serializable
object Login

/**
 * Library screen - main authenticated screen.
 * Shows user's audiobook collection.
 */
@Serializable
object Library
