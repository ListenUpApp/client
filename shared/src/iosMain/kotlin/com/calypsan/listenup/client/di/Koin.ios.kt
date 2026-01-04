package com.calypsan.listenup.client.di

import com.calypsan.listenup.client.data.discovery.IosDiscoveryService
import com.calypsan.listenup.client.data.discovery.ServerDiscoveryService
import com.calypsan.listenup.client.domain.usecase.GetInstanceUseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * iOS-specific Koin initialization.
 *
 * Starts Koin with shared modules plus any iOS-specific modules.
 * Should be called from the iOS app's initialization code (typically in App struct).
 *
 * @param additionalModules iOS-specific modules to include
 */
actual fun initializeKoin(additionalModules: List<Module>) {
    startKoin {
        // Include shared modules, iOS playback module, and any app-specific modules
        modules(sharedModules + iosPlaybackModule + additionalModules)
    }
}

/**
 * iOS simulator connects to host via 127.0.0.1.
 * Using explicit IPv4 address instead of localhost to avoid IPv6 resolution issues.
 */
actual fun getBaseUrl(): String = "http://127.0.0.1:8080"

/**
 * iOS-specific discovery module.
 * Provides Bonjour-based mDNS discovery using NSNetServiceBrowser.
 */
actual val platformDiscoveryModule: Module =
    module {
        single { IosDiscoveryService() } bind ServerDiscoveryService::class
    }

/**
 * Helper object for accessing Koin dependencies from Swift.
 * Provides strongly-typed accessors that are easier to use from Swift.
 */
object KoinHelper : KoinComponent {
    fun getInstanceUseCase(): GetInstanceUseCase {
        val useCase: GetInstanceUseCase by inject()
        return useCase
    }

    fun getServerConnectViewModel(): com.calypsan.listenup.client.presentation.connect.ServerConnectViewModel {
        val viewModel: com.calypsan.listenup.client.presentation.connect.ServerConnectViewModel by inject()
        return viewModel
    }

    fun getSettingsRepository(): com.calypsan.listenup.client.data.repository.SettingsRepository {
        val repository: com.calypsan.listenup.client.data.repository.SettingsRepository by inject()
        return repository
    }
}
