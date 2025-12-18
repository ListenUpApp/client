package com.calypsan.listenup.client.core

/**
 * Expected function to get current epoch milliseconds in a multiplatform way.
 */
expect fun currentEpochMilliseconds(): Long

/**
 * Expected function to get current hour of day (0-23) in the user's local timezone.
 * Used for time-aware greetings.
 */
expect fun currentHourOfDay(): Int
