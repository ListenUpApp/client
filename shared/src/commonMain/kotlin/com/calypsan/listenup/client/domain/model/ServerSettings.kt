package com.calypsan.listenup.client.domain.model

/**
 * Server-wide settings accessible to admins.
 */
data class ServerSettings(
    /** Whether the inbox workflow is enabled */
    val inboxEnabled: Boolean,
    /** Number of books currently in the inbox */
    val inboxCount: Int,
)
