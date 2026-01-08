package com.calypsan.listenup.client.domain.repository

import com.calypsan.listenup.client.domain.model.InviteDetails

/**
 * Repository contract for invite operations.
 *
 * Handles public invite endpoints (no authentication required).
 * Used for fetching invite details and claiming invites.
 *
 * Part of the domain layer - implementations live in the data layer.
 */
interface InviteRepository {
    /**
     * Get invite details for the registration screen.
     *
     * @param serverUrl The server URL (e.g., "https://audiobooks.example.com")
     * @param code The invite code
     * @return Invite details including name, email, server name, and validity
     * @throws Exception on network errors or invalid code
     */
    suspend fun getInviteDetails(
        serverUrl: String,
        code: String,
    ): InviteDetails

    /**
     * Claim an invite by creating a new user account.
     *
     * On success, returns tokens and user data for session establishment.
     *
     * @param serverUrl The server URL
     * @param code The invite code
     * @param password The password for the new account
     * @return LoginResult with tokens and user info
     * @throws Exception on network errors or invalid/expired invite
     */
    suspend fun claimInvite(
        serverUrl: String,
        code: String,
        password: String,
    ): LoginResult
}
