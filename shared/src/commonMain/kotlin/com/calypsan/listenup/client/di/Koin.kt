package com.calypsan.listenup.client.di

import com.calypsan.listenup.client.core.ServerUrl
import com.calypsan.listenup.client.data.local.db.ListenUpDatabase
import com.calypsan.listenup.client.data.local.db.platformDatabaseModule
import com.calypsan.listenup.client.data.remote.ApiClientFactory
import com.calypsan.listenup.client.data.remote.AuthApi
import com.calypsan.listenup.client.data.remote.ImageApi
import com.calypsan.listenup.client.data.remote.SyncApi
import com.calypsan.listenup.client.data.remote.api.ListenUpApi
import com.calypsan.listenup.client.data.repository.BookRepository
import com.calypsan.listenup.client.data.repository.HomeRepository
import com.calypsan.listenup.client.data.repository.InstanceRepositoryImpl
import com.calypsan.listenup.client.data.repository.SettingsRepository
import com.calypsan.listenup.client.data.sync.ImageDownloader
import com.calypsan.listenup.client.data.sync.SSEManager
import com.calypsan.listenup.client.data.sync.SyncManager
import com.calypsan.listenup.client.domain.repository.InstanceRepository
import com.calypsan.listenup.client.domain.usecase.GetInstanceUseCase
import com.calypsan.listenup.client.presentation.connect.ServerConnectViewModel
import com.calypsan.listenup.client.presentation.library.LibraryViewModel
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
        SettingsRepository(
            secureStorage = get(),
            instanceRepository = get()
        )
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
    // InstanceRepository needs unauthenticated API to avoid circular dependency
    // (SettingsRepository -> InstanceRepository -> ListenUpApi -> ApiClientFactory -> SettingsRepository)
    single<InstanceRepository> {
        InstanceRepositoryImpl(
            api = ListenUpApi(
                baseUrl = getBaseUrl(),
                apiClientFactory = null // Public endpoints don't need authentication
            )
        )
    }

    // Provide DAOs from database
    single { get<ListenUpDatabase>().userDao() }
    single { get<ListenUpDatabase>().bookDao() }
    single { get<ListenUpDatabase>().syncDao() }
    single { get<ListenUpDatabase>().chapterDao() }
    single { get<ListenUpDatabase>().seriesDao() }
    single { get<ListenUpDatabase>().contributorDao() }
    single { get<ListenUpDatabase>().bookContributorDao() }
    single { get<ListenUpDatabase>().playbackPositionDao() }
    single { get<ListenUpDatabase>().pendingListeningEventDao() }
    single { get<ListenUpDatabase>().downloadDao() }
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
    factory {
        com.calypsan.listenup.client.presentation.auth.SetupViewModel(
            authApi = get(),
            settingsRepository = get(),
            userDao = get()
        )
    }
    factory {
        com.calypsan.listenup.client.presentation.auth.LoginViewModel(
            authApi = get(),
            settingsRepository = get(),
            userDao = get()
        )
    }
    factory {
        LibraryViewModel(
            bookRepository = get(),
            seriesDao = get(),
            contributorDao = get(),
            syncManager = get(),
            settingsRepository = get(),
            syncDao = get()
        )
    }
    factory {
        com.calypsan.listenup.client.presentation.book_detail.BookDetailViewModel(
            bookRepository = get()
        )
    }
    factory {
        com.calypsan.listenup.client.presentation.series_detail.SeriesDetailViewModel(
            seriesDao = get(),
            bookRepository = get()
        )
    }
    factory {
        com.calypsan.listenup.client.presentation.contributor_detail.ContributorDetailViewModel(
            contributorDao = get(),
            bookDao = get(),
            imageStorage = get()
        )
    }
    factory {
        com.calypsan.listenup.client.presentation.contributor_detail.ContributorBooksViewModel(
            contributorDao = get(),
            bookDao = get(),
            imageStorage = get()
        )
    }
    factory {
        com.calypsan.listenup.client.presentation.home.HomeViewModel(
            homeRepository = get()
        )
    }
}

/**
 * Sync infrastructure module.
 *
 * Provides SyncManager, SyncApi, and related sync components.
 */
val syncModule = module {
    // Application-scoped CoroutineScope for long-lived background operations.
    // Used by SSEManager and SyncManager for tasks that span the app's lifetime.
    // SupervisorJob ensures child failures don't cancel siblings.
    single<kotlinx.coroutines.CoroutineScope>(qualifier = org.koin.core.qualifier.named("appScope")) {
        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default
        )
    }

    // Sync API uses ApiClientFactory to get authenticated HttpClient at call time
    // This avoids runBlocking during DI initialization (structured concurrency)
    single {
        SyncApi(clientFactory = get())
    }

    // Image API for downloading cover images
    single {
        ImageApi(clientFactory = get())
    }

    // Image downloader for batch cover downloads during sync
    single {
        ImageDownloader(
            imageApi = get(),
            imageStorage = get()
        )
    }

    // SSE Manager for real-time updates
    single<SSEManager> {
        SSEManager(
            clientFactory = get(),
            settingsRepository = get(),
            scope = get(qualifier = org.koin.core.qualifier.named("appScope"))
        )
    }

    // SyncManager orchestrates sync operations
    single {
        SyncManager(
            syncApi = get(),
            bookDao = get(),
            seriesDao = get(),
            contributorDao = get(),
            chapterDao = get(),
            bookContributorDao = get(),
            syncDao = get(),
            imageDownloader = get(),
            sseManager = get(),
            settingsRepository = get(),
            scope = get(qualifier = org.koin.core.qualifier.named("appScope"))
        )
    }

    // BookRepository for UI data access
    single {
        BookRepository(
            bookDao = get(),
            chapterDao = get(),
            syncManager = get(),
            imageStorage = get()
        )
    }

    // HomeRepository for Home screen data (local-first)
    single {
        HomeRepository(
            bookRepository = get(),
            playbackPositionDao = get(),
            userDao = get()
        )
    }
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
    presentationModule,
    syncModule
)

/**
 * Platform-specific initialization function.
 * Each platform (Android/iOS) implements this to set up Koin appropriately.
 *
 * @param additionalModules Platform-specific modules to include
 */
expect fun initializeKoin(additionalModules: List<Module> = emptyList())
