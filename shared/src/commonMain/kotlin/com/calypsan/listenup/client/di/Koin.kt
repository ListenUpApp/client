package com.calypsan.listenup.client.di

import com.calypsan.listenup.client.core.ServerUrl
import com.calypsan.listenup.client.data.local.db.ListenUpDatabase
import com.calypsan.listenup.client.data.local.db.platformDatabaseModule
import com.calypsan.listenup.client.data.remote.ApiClientFactory
import com.calypsan.listenup.client.data.remote.AuthApi
import com.calypsan.listenup.client.data.remote.api.ListenUpApi
import com.calypsan.listenup.client.data.repository.InstanceRepositoryImpl
import com.calypsan.listenup.client.data.repository.SettingsRepository
import com.calypsan.listenup.client.domain.repository.InstanceRepository
import com.calypsan.listenup.client.domain.usecase.GetInstanceUseCase
import com.calypsan.listenup.client.presentation.connect.ServerConnectViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Platform-specific storage module.
 * Each platform provides SecureStorage implementation via this module.
 */
expect val platformStorageModule: Module

/**
 * Data layer dependencies.
 * Provides repositories for settings and domain data.
 */
val dataModule = module {
    // Settings repository - single source of truth for app configuration
    single {
        SettingsRepository(secureStorage = get()).apply {
            // Initialize with default server URL if not set
            // This will be updated when user connects to a server
        }
    }
}

/**
 * Network layer dependencies.
 * Provides HTTP clients and API configuration with authentication support.
 *
 * Note: Initial setup uses default base URL from getBaseUrl().
 * When user configures a different server URL at runtime, API instances
 * should be recreated via factory pattern or manual invalidation.
 */
val networkModule = module {
    // AuthApi - handles login, logout, and token refresh
    // Uses default base URL initially; can be recreated when server URL changes
    single {
        AuthApi(serverUrl = ServerUrl(getBaseUrl()))
    }

    // ApiClientFactory - creates authenticated HTTP clients with auto-refresh
    single {
        ApiClientFactory(
            settingsRepository = get(),
            authApi = get()
        )
    }

    // ListenUpApi - main API for server communication
    // Uses default base URL initially; can be recreated when server URL changes
    single {
        ListenUpApi(
            baseUrl = getBaseUrl(),
            apiClientFactory = get()
        )
    }
}

/**
 * Platform-specific base URL for the API.
 * - Android emulator: 10.0.2.2 (maps to host's localhost)
 * - iOS simulator: localhost/127.0.0.1
 * - Physical devices: Use your computer's LAN IP
 */
expect fun getBaseUrl(): String

/**
 * Repository layer dependencies.
 * Binds repository interfaces to their implementations.
 */
val repositoryModule = module {
    singleOf(::InstanceRepositoryImpl) bind InstanceRepository::class

    // DAOs from database
    single { get<ListenUpDatabase>().userDao() }
}

/**
 * Use case layer dependencies.
 * Creates use case instances for business logic.
 */
val useCaseModule = module {
    factoryOf(::GetInstanceUseCase)
}

/**
 * Presentation layer dependencies.
 * Provides ViewModels for UI screens.
 */
val presentationModule = module {
    factory { ServerConnectViewModel(settingsRepository = get()) }
}

/**
 * All shared modules that should be loaded in both Android and iOS.
 */
val sharedModules = listOf(
    platformStorageModule,
    platformDatabaseModule,
    dataModule,
    networkModule,
    repositoryModule,
    useCaseModule,
    presentationModule
)

/**
 * Platform-specific initialization function.
 * Each platform (Android/iOS) implements this to set up Koin appropriately.
 *
 * @param additionalModules Platform-specific modules to include
 */
expect fun initializeKoin(additionalModules: List<Module> = emptyList())
