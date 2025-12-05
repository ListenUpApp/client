package com.calypsan.listenup.client.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * iOS implementation of NetworkMonitor.
 *
 * TODO: Implement using NWPathMonitor for actual connectivity detection.
 * For now, this stub assumes the device is always online, which means
 * search will always try server first and fall back to local FTS on failure.
 */
class IosNetworkMonitor : NetworkMonitor {
    private val _isOnlineFlow = MutableStateFlow(true)
    override val isOnlineFlow: StateFlow<Boolean> = _isOnlineFlow.asStateFlow()

    override fun isOnline(): Boolean = true
}
