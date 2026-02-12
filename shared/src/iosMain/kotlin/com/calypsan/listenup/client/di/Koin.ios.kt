package com.calypsan.listenup.client.di

import com.calypsan.listenup.client.core.configureLogging
import com.calypsan.listenup.client.data.discovery.IosDiscoveryService
import com.calypsan.listenup.client.data.discovery.ServerDiscoveryService
import com.calypsan.listenup.client.domain.usecase.GetInstanceUseCase
import com.calypsan.listenup.client.presentation.contributordetail.ContributorDetailViewModel
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
 * Also configures kotlin-logging to use OSLog for unified logging.
 * Should be called from the iOS app's initialization code (typically in App struct).
 *
 * @param additionalModules iOS-specific modules to include
 */
actual fun initializeKoin(additionalModules: List<Module>) {
    // Configure logging before anything else
    configureLogging()

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

    fun getLoginViewModel(): com.calypsan.listenup.client.presentation.auth.LoginViewModel {
        val viewModel: com.calypsan.listenup.client.presentation.auth.LoginViewModel by inject()
        return viewModel
    }

    fun getRegisterViewModel(): com.calypsan.listenup.client.presentation.auth.RegisterViewModel {
        val viewModel: com.calypsan.listenup.client.presentation.auth.RegisterViewModel by inject()
        return viewModel
    }

    fun getServerSelectViewModel(): com.calypsan.listenup.client.presentation.connect.ServerSelectViewModel {
        val viewModel: com.calypsan.listenup.client.presentation.connect.ServerSelectViewModel by inject()
        return viewModel
    }

    fun getAuthSession(): com.calypsan.listenup.client.domain.repository.AuthSession {
        val authSession: com.calypsan.listenup.client.domain.repository.AuthSession by inject()
        return authSession
    }

    fun getServerConfig(): com.calypsan.listenup.client.domain.repository.ServerConfig {
        val serverConfig: com.calypsan.listenup.client.domain.repository.ServerConfig by inject()
        return serverConfig
    }

    fun getUserRepository(): com.calypsan.listenup.client.domain.repository.UserRepository {
        val userRepository: com.calypsan.listenup.client.domain.repository.UserRepository by inject()
        return userRepository
    }

    fun getLibraryViewModel(): com.calypsan.listenup.client.presentation.library.LibraryViewModel {
        val viewModel: com.calypsan.listenup.client.presentation.library.LibraryViewModel by inject()
        return viewModel
    }

    fun getBookDetailViewModel(): com.calypsan.listenup.client.presentation.bookdetail.BookDetailViewModel {
        val viewModel: com.calypsan.listenup.client.presentation.bookdetail.BookDetailViewModel by inject()
        return viewModel
    }

    fun getSeriesDetailViewModel(): com.calypsan.listenup.client.presentation.seriesdetail.SeriesDetailViewModel {
        val viewModel: com.calypsan.listenup.client.presentation.seriesdetail.SeriesDetailViewModel by inject()
        return viewModel
    }

    fun getContributorDetailViewModel(): ContributorDetailViewModel {
        val viewModel: ContributorDetailViewModel by inject()
        return viewModel
    }
}

/**
 * iOS-specific device detection module.
 * Uses UIDevice.userInterfaceIdiom to detect device type.
 */
actual val platformDeviceModule: Module =
    module {
        single {
            com.calypsan.listenup.client.device
                .DeviceContextProvider()
        }
        single { get<com.calypsan.listenup.client.device.DeviceContextProvider>().detect() }
    }
