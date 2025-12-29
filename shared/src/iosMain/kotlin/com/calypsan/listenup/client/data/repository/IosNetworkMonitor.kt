package com.calypsan.listenup.client.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * iOS implementation of NetworkMonitor.
 *
 * TODO: Implement using NWPathMonitor for actual connectivity detection.
 * For now, this stub assumes the device is always online and on WiFi,
 * which means downloads will always proceed regardless of WiFi-only setting.
 */
class IosNetworkMonitor : NetworkMonitor {
    private val _isOnlineFlow = MutableStateFlow(true)
    override val isOnlineFlow: StateFlow<Boolean> = _isOnlineFlow.asStateFlow()

    // TODO: Implement using NWPathMonitor isExpensive property
    private val _isOnUnmeteredNetworkFlow = MutableStateFlow(true)
    override val isOnUnmeteredNetworkFlow: StateFlow<Boolean> = _isOnUnmeteredNetworkFlow.asStateFlow()

    override fun isOnline(): Boolean = true
}
