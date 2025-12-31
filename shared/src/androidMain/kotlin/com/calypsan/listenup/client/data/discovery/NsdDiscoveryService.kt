package com.calypsan.listenup.client.data.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.concurrent.Executors

/**
 * Android implementation of [ServerDiscoveryService] using NsdManager.
 *
 * Discovers ListenUp servers advertising via mDNS on the local network.
 * Service type: _listenup._tcp
 *
 * TXT record parsing:
 * - id: Server's unique identifier (required)
 * - name: Human-readable name (required)
 * - version: Server version (required)
 * - api: API version (required)
 * - remote: Remote URL (optional)
 */
class NsdDiscoveryService(
    context: Context,
) : ServerDiscoveryService {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val serversState = MutableStateFlow<Map<String, DiscoveredServer>>(emptyMap())

    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var isDiscovering = false

    companion object {
        private const val TAG = "NsdDiscoveryService"
        private const val SERVICE_TYPE = "_listenup._tcp."
    }

    override fun discover(): Flow<List<DiscoveredServer>> = serversState.map { it.values.toList() }

    override fun startDiscovery() {
        if (isDiscovering) {
            Log.d(TAG, "Discovery already running")
            return
        }

        Log.i(TAG, "Starting mDNS discovery for $SERVICE_TYPE")

        discoveryListener =
            object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) {
                    Log.i(TAG, "Discovery started for $serviceType")
                    isDiscovering = true
                }

                override fun onDiscoveryStopped(serviceType: String) {
                    Log.i(TAG, "Discovery stopped for $serviceType")
                    isDiscovering = false
                }

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    Log.d(TAG, "Service found: ${serviceInfo.serviceName}")
                    resolveService(serviceInfo)
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                    Log.d(TAG, "Service lost: ${serviceInfo.serviceName}")
                    // Remove by service name since we don't have the ID yet
                    serversState.update { current ->
                        val removedId =
                            current.entries
                                .firstOrNull { it.value.name == serviceInfo.serviceName }
                                ?.key
                        if (removedId != null) current - removedId else current
                    }
                }

                override fun onStartDiscoveryFailed(
                    serviceType: String,
                    errorCode: Int,
                ) {
                    Log.e(TAG, "Discovery start failed: $errorCode")
                    isDiscovering = false
                }

                override fun onStopDiscoveryFailed(
                    serviceType: String,
                    errorCode: Int,
                ) {
                    Log.e(TAG, "Discovery stop failed: $errorCode")
                }
            }

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start discovery", e)
            isDiscovering = false
        }
    }

    override fun stopDiscovery() {
        if (!isDiscovering || discoveryListener == null) {
            Log.d(TAG, "Discovery not running, nothing to stop")
            return
        }

        Log.i(TAG, "Stopping mDNS discovery")
        try {
            nsdManager.stopServiceDiscovery(discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop discovery", e)
        }
        discoveryListener = null
        isDiscovering = false
    }

    private fun resolveService(serviceInfo: NsdServiceInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // API 34+: Use the new ServiceInfoCallback API
            resolveServiceModern(serviceInfo)
        } else {
            // API 33 and below: Use the deprecated ResolveListener API
            @Suppress("DEPRECATION")
            resolveServiceLegacy(serviceInfo)
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveServiceLegacy(serviceInfo: NsdServiceInfo) {
        val resolveListener =
            object : NsdManager.ResolveListener {
                override fun onResolveFailed(
                    serviceInfo: NsdServiceInfo,
                    errorCode: Int,
                ) {
                    Log.e(TAG, "Resolve failed for ${serviceInfo.serviceName}: $errorCode")
                }

                @Suppress("DEPRECATION")
                override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                    val hostAddress = resolvedInfo.host?.hostAddress
                    Log.d(TAG, "Resolved: ${resolvedInfo.serviceName} at $hostAddress:${resolvedInfo.port}")
                    val server = parseDiscoveredServer(resolvedInfo)
                    if (server != null) {
                        serversState.update { it + (server.id to server) }
                        Log.i(TAG, "Server discovered: ${server.name} (${server.id}) at ${server.localUrl}")
                    } else {
                        Log.w(TAG, "Failed to parse server: ${resolvedInfo.serviceName}")
                    }
                }
            }

        try {
            nsdManager.resolveService(serviceInfo, resolveListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve service", e)
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun resolveServiceModern(serviceInfo: NsdServiceInfo) {
        val executor = Executors.newSingleThreadExecutor()
        var callback: NsdManager.ServiceInfoCallback? = null

        callback =
            object : NsdManager.ServiceInfoCallback {
                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                    Log.e(TAG, "ServiceInfoCallback registration failed: $errorCode")
                }

                override fun onServiceUpdated(resolvedInfo: NsdServiceInfo) {
                    val hostAddress = resolvedInfo.hostAddresses.firstOrNull()?.hostAddress
                    Log.d(TAG, "Resolved: ${resolvedInfo.serviceName} at $hostAddress:${resolvedInfo.port}")
                    val server = parseDiscoveredServer(resolvedInfo)
                    if (server != null) {
                        serversState.update { it + (server.id to server) }
                        Log.i(TAG, "Server discovered: ${server.name} (${server.id}) at ${server.localUrl}")
                    } else {
                        Log.w(TAG, "Failed to parse server: ${resolvedInfo.serviceName}")
                    }
                    // Unregister after successful resolution
                    callback?.let { nsdManager.unregisterServiceInfoCallback(it) }
                }

                override fun onServiceLost() {
                    Log.d(TAG, "Service lost during resolution: ${serviceInfo.serviceName}")
                    callback?.let { nsdManager.unregisterServiceInfoCallback(it) }
                }

                override fun onServiceInfoCallbackUnregistered() {
                    Log.d(TAG, "ServiceInfoCallback unregistered for ${serviceInfo.serviceName}")
                }
            }

        try {
            nsdManager.registerServiceInfoCallback(serviceInfo, executor, callback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register service info callback", e)
        }
    }

    private fun parseDiscoveredServer(serviceInfo: NsdServiceInfo): DiscoveredServer? {
        val host = getHostAddress(serviceInfo) ?: return null
        val port = serviceInfo.port

        // Parse TXT records (available on Android 7.0+)
        val txtRecords = parseTxtRecords(serviceInfo)

        val id = txtRecords["id"] ?: return null
        val name = txtRecords["name"] ?: serviceInfo.serviceName
        val apiVersion = txtRecords["api"] ?: "v1"
        val serverVersion = txtRecords["version"] ?: "unknown"
        val remoteUrl = txtRecords["remote"]

        return DiscoveredServer(
            id = id,
            name = name,
            host = host,
            port = port,
            apiVersion = apiVersion,
            serverVersion = serverVersion,
            remoteUrl = remoteUrl,
        )
    }

    @Suppress("DEPRECATION")
    private fun getHostAddress(serviceInfo: NsdServiceInfo): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            serviceInfo.hostAddresses.firstOrNull()?.hostAddress
        } else {
            serviceInfo.host?.hostAddress
        }

    private fun parseTxtRecords(serviceInfo: NsdServiceInfo): Map<String, String> {
        val result = mutableMapOf<String, String>()

        // NsdServiceInfo.attributes is available on API 21+
        try {
            val attributes = serviceInfo.attributes
            for ((key, value) in attributes) {
                if (value != null) {
                    result[key] = String(value, Charsets.UTF_8)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse TXT records", e)
        }

        return result
    }
}
