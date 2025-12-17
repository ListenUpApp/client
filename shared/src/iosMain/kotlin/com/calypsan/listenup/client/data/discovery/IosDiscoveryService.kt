package com.calypsan.listenup.client.data.discovery

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * iOS placeholder implementation of [ServerDiscoveryService].
 *
 * TODO: Implement using NSNetServiceBrowser for Bonjour discovery.
 * For now, returns an empty flow (no servers discovered).
 *
 * Full implementation would use:
 * - NSNetServiceBrowser to browse for _listenup._tcp services
 * - NSNetService to resolve discovered services
 * - NSNetServiceDelegate for callbacks
 */
class IosDiscoveryService : ServerDiscoveryService {
    private val _discoveredServers = MutableStateFlow<List<DiscoveredServer>>(emptyList())

    override fun discover(): Flow<List<DiscoveredServer>> = _discoveredServers.asStateFlow()

    override fun startDiscovery() {
        // TODO: Implement NSNetServiceBrowser discovery
    }

    override fun stopDiscovery() {
        // TODO: Implement NSNetServiceBrowser stop
    }
}
