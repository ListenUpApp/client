package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.core.currentEpochMilliseconds
import com.calypsan.listenup.client.data.discovery.DiscoveredServer
import com.calypsan.listenup.client.data.discovery.ServerDiscoveryService
import com.calypsan.listenup.client.data.local.db.ServerDao
import com.calypsan.listenup.client.data.local.db.ServerEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

/**
 * Server with online status, combining persisted data and discovery state.
 *
 * @property server The persisted server entity
 * @property isOnline Whether the server is currently visible on the local network
 */
data class ServerWithStatus(
    val server: ServerEntity,
    val isOnline: Boolean,
)

/**
 * Contract for server repository operations.
 */
interface ServerRepositoryContract {
    /**
     * Observe all servers with their online status.
     * Merges persisted servers with discovery state.
     */
    fun observeServers(): Flow<List<ServerWithStatus>>

    /**
     * Observe the currently active server.
     */
    fun observeActiveServer(): Flow<ServerEntity?>

    /**
     * Get the currently active server.
     */
    suspend fun getActiveServer(): ServerEntity?

    /**
     * Set a server as the active server.
     * Creates the server if it doesn't exist (from discovery).
     *
     * @param serverId The server ID to activate
     */
    suspend fun setActiveServer(serverId: String)

    /**
     * Set a discovered server as active, creating it if needed.
     *
     * @param discovered The discovered server to activate
     */
    suspend fun setActiveServer(discovered: DiscoveredServer)

    /**
     * Save authentication tokens for the active server.
     */
    suspend fun saveAuthTokens(
        accessToken: String,
        refreshToken: String,
        sessionId: String,
        userId: String,
    )

    /**
     * Update only the access token (for token refresh).
     */
    suspend fun updateAccessToken(accessToken: String)

    /**
     * Clear authentication tokens for the active server.
     * Preserves the server in the list (soft logout).
     */
    suspend fun clearAuthTokens()

    /**
     * Add a server manually (not from discovery).
     * Used for remote-only servers or manual URL entry.
     */
    suspend fun addManualServer(
        id: String,
        name: String,
        remoteUrl: String,
        apiVersion: String = "v1",
        serverVersion: String = "unknown",
    )

    /**
     * Delete a server and all its data.
     *
     * @param serverId The server ID to delete
     */
    suspend fun deleteServer(serverId: String)

    /**
     * Start server discovery.
     * Call when app becomes active.
     */
    fun startDiscovery()

    /**
     * Stop server discovery.
     * Call when app goes to background.
     */
    fun stopDiscovery()
}

/**
 * Repository for managing ListenUp servers.
 *
 * Bridges mDNS discovery with local persistence:
 * - Discovered servers are automatically persisted
 * - Persisted servers show online/offline status based on discovery
 * - Auth tokens are stored per-server for instant switching
 *
 * @param serverDao Database access for server persistence
 * @param discoveryService Platform-specific mDNS discovery
 * @param scope Coroutine scope for background operations
 */
class ServerRepository(
    private val serverDao: ServerDao,
    private val discoveryService: ServerDiscoveryService,
    private val scope: CoroutineScope,
) : ServerRepositoryContract {
    override fun observeServers(): Flow<List<ServerWithStatus>> =
        combine(
            serverDao.observeAll(),
            discoveryService.discover().onStart { emit(emptyList()) },
        ) { persisted, discovered ->
            val discoveredIds = discovered.map { it.id }.toSet()

            // Update local URLs for rediscovered servers
            scope.launch {
                for (disc in discovered) {
                    val existing = serverDao.getById(disc.id)
                    if (existing != null) {
                        // Server exists - update from discovery
                        serverDao.updateFromDiscovery(
                            id = disc.id,
                            name = disc.name,
                            apiVersion = disc.apiVersion,
                            serverVersion = disc.serverVersion,
                            localUrl = disc.localUrl,
                            remoteUrl = disc.remoteUrl,
                        )
                    } else {
                        // New server - insert it
                        serverDao.upsert(disc.toEntity())
                    }
                }
            }

            // Merge persisted servers with online status
            val merged =
                persisted.map { server ->
                    ServerWithStatus(
                        server = server,
                        isOnline = server.id in discoveredIds,
                    )
                }

            // Add newly discovered servers that aren't persisted yet
            val newServers =
                discovered
                    .filter { d -> persisted.none { it.id == d.id } }
                    .map { ServerWithStatus(it.toEntity(), isOnline = true) }

            merged + newServers
        }

    override fun observeActiveServer(): Flow<ServerEntity?> = serverDao.observeActive()

    override suspend fun getActiveServer(): ServerEntity? = serverDao.getActive()

    override suspend fun setActiveServer(serverId: String) {
        serverDao.setActive(serverId)
    }

    override suspend fun setActiveServer(discovered: DiscoveredServer) {
        // Ensure server exists in database
        val existing = serverDao.getById(discovered.id)
        if (existing == null) {
            serverDao.upsert(discovered.toEntity())
        } else {
            // Update with latest discovery info
            serverDao.updateFromDiscovery(
                id = discovered.id,
                name = discovered.name,
                apiVersion = discovered.apiVersion,
                serverVersion = discovered.serverVersion,
                localUrl = discovered.localUrl,
                remoteUrl = discovered.remoteUrl,
            )
        }
        serverDao.setActive(discovered.id)
    }

    override suspend fun saveAuthTokens(
        accessToken: String,
        refreshToken: String,
        sessionId: String,
        userId: String,
    ) {
        val active =
            serverDao.getActive()
                ?: error("No active server to save tokens for")
        serverDao.saveAuthTokens(
            serverId = active.id,
            accessToken = accessToken,
            refreshToken = refreshToken,
            sessionId = sessionId,
            userId = userId,
        )
    }

    override suspend fun updateAccessToken(accessToken: String) {
        val active =
            serverDao.getActive()
                ?: error("No active server to update token for")
        serverDao.updateAccessToken(active.id, accessToken)
    }

    override suspend fun clearAuthTokens() {
        val active =
            serverDao.getActive()
                ?: error("No active server to clear tokens for")
        serverDao.clearAuthTokens(active.id)
    }

    override suspend fun addManualServer(
        id: String,
        name: String,
        remoteUrl: String,
        apiVersion: String,
        serverVersion: String,
    ) {
        serverDao.upsert(
            ServerEntity(
                id = id,
                name = name,
                apiVersion = apiVersion,
                serverVersion = serverVersion,
                localUrl = null,
                remoteUrl = remoteUrl,
                isActive = false,
                lastSeenAt = 0,
            ),
        )
    }

    override suspend fun deleteServer(serverId: String) {
        serverDao.deleteById(serverId)
    }

    override fun startDiscovery() {
        discoveryService.startDiscovery()
    }

    override fun stopDiscovery() {
        discoveryService.stopDiscovery()
    }
}

/**
 * Convert a discovered server to a persistable entity.
 */
private fun DiscoveredServer.toEntity(): ServerEntity =
    ServerEntity(
        id = id,
        name = name,
        apiVersion = apiVersion,
        serverVersion = serverVersion,
        localUrl = localUrl,
        remoteUrl = remoteUrl,
        isActive = false,
        lastSeenAt = currentEpochMilliseconds(),
    )
