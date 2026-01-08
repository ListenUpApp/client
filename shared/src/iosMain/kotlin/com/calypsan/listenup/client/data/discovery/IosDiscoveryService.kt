@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.calypsan.listenup.client.data.discovery

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import platform.Foundation.NSData
import platform.Foundation.NSNetService
import platform.Foundation.NSNetServiceBrowser
import platform.Foundation.NSNetServiceBrowserDelegateProtocol
import platform.Foundation.NSNetServiceDelegateProtocol
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.darwin.NSObject

private val logger = KotlinLogging.logger {}

/**
 * iOS implementation of [ServerDiscoveryService] using NSNetServiceBrowser (Bonjour).
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
class IosDiscoveryService : ServerDiscoveryService {
    private val serviceBrowser = NSNetServiceBrowser()
    private val serversState = MutableStateFlow<Map<String, DiscoveredServer>>(emptyMap())
    private val pendingServices = mutableMapOf<String, NSNetService>()

    private var browserDelegate: BrowserDelegate? = null
    private var isDiscovering = false

    companion object {
        private const val SERVICE_TYPE = "_listenup._tcp."
        private const val SERVICE_DOMAIN = "local."
        private const val RESOLVE_TIMEOUT = 10.0
    }

    override fun discover(): Flow<List<DiscoveredServer>> = serversState.map { it.values.toList() }

    override fun startDiscovery() {
        if (isDiscovering) {
            logger.debug { "Discovery already running" }
            return
        }

        logger.info { "Starting mDNS discovery for $SERVICE_TYPE" }
        isDiscovering = true

        browserDelegate = BrowserDelegate()
        serviceBrowser.delegate = browserDelegate
        serviceBrowser.searchForServicesOfType(SERVICE_TYPE, inDomain = SERVICE_DOMAIN)
    }

    override fun stopDiscovery() {
        if (!isDiscovering) {
            logger.debug { "Discovery not running, nothing to stop" }
            return
        }

        logger.info { "Stopping mDNS discovery" }
        serviceBrowser.stop()
        isDiscovering = false
        browserDelegate = null
        pendingServices.clear()
    }

    private fun onServiceFound(service: NSNetService) {
        val serviceName = service.name
        logger.debug { "Service found: $serviceName" }

        pendingServices[serviceName] = service
        service.delegate = ServiceDelegate()
        service.resolveWithTimeout(RESOLVE_TIMEOUT)
    }

    private fun onServiceRemoved(service: NSNetService) {
        val serviceName = service.name
        logger.debug { "Service removed: $serviceName" }

        pendingServices.remove(serviceName)
        serversState.update { current ->
            val removedId = current.entries.firstOrNull { it.value.name == serviceName }?.key
            if (removedId != null) current - removedId else current
        }
    }

    private fun onServiceResolved(service: NSNetService) {
        val hostName = service.hostName
        val port = service.port.toInt()
        val serviceName = service.name

        logger.debug { "Resolved: $serviceName at $hostName:$port" }

        if (hostName == null) {
            logger.warn { "Resolved service has no host: $serviceName" }
            return
        }

        val txtData = service.TXTRecordData()
        val txtRecords = parseTxtRecords(txtData)

        val serverId = txtRecords["id"]
        if (serverId == null) {
            logger.warn { "Service missing required 'id' in TXT record: $serviceName" }
            return
        }

        val server =
            DiscoveredServer(
                id = serverId,
                name = txtRecords["name"] ?: serviceName,
                host = hostName.trimEnd('.'),
                port = port,
                apiVersion = txtRecords["api"] ?: "v1",
                serverVersion = txtRecords["version"] ?: "unknown",
                remoteUrl = txtRecords["remote"],
            )

        serversState.update { it + (server.id to server) }
        logger.info { "Server discovered: ${server.name} (${server.id}) at ${server.localUrl}" }
        pendingServices.remove(serviceName)
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseTxtRecords(data: NSData?): Map<String, String> {
        if (data == null) return emptyMap()

        val result = mutableMapOf<String, String>()
        try {
            val dictionary = NSNetService.dictionaryFromTXTRecordData(data) as? Map<Any?, Any?>
            dictionary?.forEach { (key, value) ->
                if (key is String && value is NSData) {
                    val string = NSString.create(value, NSUTF8StringEncoding)
                    if (string != null) {
                        result[key] = string.toString()
                    }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to parse TXT records" }
        }
        return result
    }

    /**
     * Delegate for NSNetServiceBrowser events.
     */
    private inner class BrowserDelegate :
        NSObject(),
        NSNetServiceBrowserDelegateProtocol {
        override fun netServiceBrowserWillSearch(browser: NSNetServiceBrowser) {
            logger.info { "Service browser will search" }
        }

        override fun netServiceBrowserDidStopSearch(browser: NSNetServiceBrowser) {
            logger.info { "Service browser stopped searching" }
            isDiscovering = false
        }

        override fun netServiceBrowser(
            browser: NSNetServiceBrowser,
            didNotSearch: Map<Any?, *>,
        ) {
            logger.error { "Service browser failed to search: $didNotSearch" }
            isDiscovering = false
        }

        @ObjCSignatureOverride
        override fun netServiceBrowser(
            browser: NSNetServiceBrowser,
            didFindService: NSNetService,
            moreComing: Boolean,
        ) {
            onServiceFound(didFindService)
        }

        @ObjCSignatureOverride
        override fun netServiceBrowser(
            browser: NSNetServiceBrowser,
            didRemoveService: NSNetService,
            moreComing: Boolean,
        ) {
            onServiceRemoved(didRemoveService)
        }
    }

    /**
     * Delegate for NSNetService resolution events.
     */
    private inner class ServiceDelegate :
        NSObject(),
        NSNetServiceDelegateProtocol {
        override fun netServiceDidResolveAddress(sender: NSNetService) {
            onServiceResolved(sender)
        }

        override fun netService(
            sender: NSNetService,
            didNotResolve: Map<Any?, *>,
        ) {
            logger.error { "Failed to resolve service ${sender.name}: $didNotResolve" }
            pendingServices.remove(sender.name)
        }
    }
}
