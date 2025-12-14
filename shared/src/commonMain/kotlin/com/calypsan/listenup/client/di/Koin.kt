package com.calypsan.listenup.client.di

import com.calypsan.listenup.client.data.local.db.ListenUpDatabase
import com.calypsan.listenup.client.data.local.db.platformDatabaseModule
import com.calypsan.listenup.client.data.remote.ApiClientFactory
import com.calypsan.listenup.client.data.remote.AuthApi
import com.calypsan.listenup.client.data.remote.AuthApiContract
import com.calypsan.listenup.client.data.remote.ImageApi
import com.calypsan.listenup.client.data.remote.ImageApiContract
import com.calypsan.listenup.client.data.remote.SearchApi
import com.calypsan.listenup.client.data.remote.SearchApiContract
import com.calypsan.listenup.client.data.remote.SyncApi
import com.calypsan.listenup.client.data.remote.SyncApiContract
import com.calypsan.listenup.client.data.remote.GenreApi
import com.calypsan.listenup.client.data.remote.GenreApiContract
import com.calypsan.listenup.client.data.remote.TagApi
import com.calypsan.listenup.client.data.remote.TagApiContract
import com.calypsan.listenup.client.data.remote.api.ListenUpApi
import com.calypsan.listenup.client.data.remote.ListenUpApiContract
import com.calypsan.listenup.client.data.repository.BookEditRepository
import com.calypsan.listenup.client.data.repository.BookEditRepositoryContract
import com.calypsan.listenup.client.data.repository.BookRepository
import com.calypsan.listenup.client.data.repository.BookRepositoryContract
import com.calypsan.listenup.client.data.repository.ContributorRepository
import com.calypsan.listenup.client.data.repository.ContributorRepositoryContract
import com.calypsan.listenup.client.data.repository.SeriesRepository
import com.calypsan.listenup.client.data.repository.SeriesRepositoryContract
import com.calypsan.listenup.client.data.repository.HomeRepository
import com.calypsan.listenup.client.data.repository.HomeRepositoryContract
import com.calypsan.listenup.client.data.repository.InstanceRepositoryImpl
import com.calypsan.listenup.client.data.repository.SearchRepository
import com.calypsan.listenup.client.data.repository.SearchRepositoryContract
import com.calypsan.listenup.client.data.repository.SettingsRepository
import com.calypsan.listenup.client.data.repository.SettingsRepositoryContract
import com.calypsan.listenup.client.data.sync.FtsPopulator
import com.calypsan.listenup.client.data.sync.FtsPopulatorContract
import com.calypsan.listenup.client.data.sync.ImageDownloader
import com.calypsan.listenup.client.data.sync.ImageDownloaderContract
import com.calypsan.listenup.client.data.sync.SSEManager
import com.calypsan.listenup.client.data.sync.SSEManagerContract
import com.calypsan.listenup.client.data.sync.SyncManager
import com.calypsan.listenup.client.data.sync.SyncManagerContract
import com.calypsan.listenup.client.domain.repository.InstanceRepository
import com.calypsan.listenup.client.domain.usecase.GetInstanceUseCase
import com.calypsan.listenup.client.presentation.connect.ServerConnectViewModel
import com.calypsan.listenup.client.presentation.library.LibraryViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
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
val dataModule =
    module {
        // Settings repository - single source of truth for app configuration
        // Bind to both concrete type and interface (for ViewModels)
        single {
            SettingsRepository(
                secureStorage = get(),
                instanceRepository = get(),
            )
        } bind SettingsRepositoryContract::class
    }

/**
 * Network layer dependencies.
 * Provides HTTP clients and API configuration with authentication support.
 *
 * Note: Initial setup uses default base URL from getBaseUrl().
 * When user configures a different server URL at runtime, API instances
 * should be recreated via factory pattern or manual invalidation.
 */
val networkModule =
    module {
        // AuthApi - handles login, logout, and token refresh
        // Gets server URL dynamically from SettingsRepository
        // Bind to both concrete type and interface
        single {
            val settingsRepository: SettingsRepositoryContract = get()
            AuthApi(getServerUrl = { settingsRepository.getServerUrl() })
        } bind AuthApiContract::class

        // ApiClientFactory - creates authenticated HTTP clients with auto-refresh
        single {
            ApiClientFactory(
                settingsRepository = get(),
                authApi = get(),
            )
        }

        // ListenUpApi - main API for server communication
        // Uses default base URL initially; can be recreated when server URL changes
        single<ListenUpApiContract> {
            ListenUpApi(
                baseUrl = getBaseUrl(),
                apiClientFactory = get(),
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
val repositoryModule =
    module {
        // InstanceRepository needs unauthenticated API to avoid circular dependency
        // (SettingsRepository -> InstanceRepository -> ListenUpApi -> ApiClientFactory -> SettingsRepository)
        single<InstanceRepository> {
            InstanceRepositoryImpl(
                api =
                    ListenUpApi(
                        baseUrl = getBaseUrl(),
                        apiClientFactory = null, // Public endpoints don't need authentication
                    ),
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
        single { get<ListenUpDatabase>().bookSeriesDao() }
        single { get<ListenUpDatabase>().playbackPositionDao() }
        single { get<ListenUpDatabase>().pendingListeningEventDao() }
        single { get<ListenUpDatabase>().downloadDao() }
        single { get<ListenUpDatabase>().searchDao() }
    }

/**
 * Use case layer dependencies.
 * Creates use case instances for business logic.
 */
val useCaseModule =
    module {
        factoryOf(::GetInstanceUseCase)
    }

/**
 * Presentation layer dependencies.
 * Provides ViewModels for UI screens.
 */
val presentationModule =
    module {
        factory { ServerConnectViewModel(settingsRepository = get()) }
        factory {
            com.calypsan.listenup.client.presentation.auth.SetupViewModel(
                authApi = get(),
                settingsRepository = get(),
                userDao = get(),
            )
        }
        factory {
            com.calypsan.listenup.client.presentation.auth.LoginViewModel(
                authApi = get(),
                settingsRepository = get(),
                userDao = get(),
            )
        }
        factory {
            LibraryViewModel(
                bookRepository = get(),
                seriesDao = get(),
                contributorDao = get(),
                syncManager = get(),
                settingsRepository = get(),
                syncDao = get(),
                playbackPositionDao = get(),
            )
        }
        factory {
            com.calypsan.listenup.client.presentation.bookdetail.BookDetailViewModel(
                bookRepository = get(),
                genreApi = get(),
                tagApi = get(),
                playbackPositionDao = get(),
            )
        }
        factory {
            com.calypsan.listenup.client.presentation.seriesdetail.SeriesDetailViewModel(
                seriesDao = get(),
                bookRepository = get(),
            )
        }
        factory {
            com.calypsan.listenup.client.presentation.contributordetail.ContributorDetailViewModel(
                contributorDao = get(),
                bookDao = get(),
                imageStorage = get(),
                playbackPositionDao = get(),
            )
        }
        factory {
            com.calypsan.listenup.client.presentation.contributordetail.ContributorBooksViewModel(
                contributorDao = get(),
                bookDao = get(),
                imageStorage = get(),
                playbackPositionDao = get(),
            )
        }
        factory {
            com.calypsan.listenup.client.presentation.home.HomeViewModel(
                homeRepository = get(),
            )
        }
        factory {
            com.calypsan.listenup.client.presentation.search.SearchViewModel(
                searchRepository = get(),
            )
        }
        factory {
            com.calypsan.listenup.client.presentation.bookedit.BookEditViewModel(
                bookRepository = get(),
                bookEditRepository = get(),
                contributorRepository = get(),
                seriesRepository = get(),
                genreApi = get(),
                tagApi = get(),
            )
        }
        factory {
            com.calypsan.listenup.client.presentation.contributoredit.ContributorEditViewModel(
                contributorDao = get(),
                bookContributorDao = get(),
                contributorRepository = get(),
                api = get(),
            )
        }
    }

/**
 * Sync infrastructure module.
 *
 * Provides SyncManager, SyncApi, and related sync components.
 */
val syncModule =
    module {
        // Application-scoped CoroutineScope for long-lived background operations.
        // Used by SSEManager and SyncManager for tasks that span the app's lifetime.
        // SupervisorJob ensures child failures don't cancel siblings.
        single<kotlinx.coroutines.CoroutineScope>(
            qualifier =
                org.koin.core.qualifier
                    .named("appScope"),
        ) {
            kotlinx.coroutines.CoroutineScope(
                kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default,
            )
        }

        // Sync API uses ApiClientFactory to get authenticated HttpClient at call time
        // This avoids runBlocking during DI initialization (structured concurrency)
        single<SyncApiContract> {
            SyncApi(clientFactory = get())
        }

        // Image API for downloading cover images
        single {
            ImageApi(clientFactory = get())
        } bind ImageApiContract::class

        // Image downloader for batch cover downloads during sync
        single {
            ImageDownloader(
                imageApi = get(),
                imageStorage = get(),
            )
        } bind ImageDownloaderContract::class

        // SSE Manager for real-time updates
        single {
            SSEManager(
                clientFactory = get(),
                settingsRepository = get(),
                scope =
                    get(
                        qualifier =
                            org.koin.core.qualifier
                                .named("appScope"),
                    ),
            )
        } bind SSEManagerContract::class

        // SearchApi for server-side search
        single<SearchApiContract> {
            SearchApi(clientFactory = get())
        }

        // TagApi for user tag operations
        single {
            TagApi(clientFactory = get())
        } bind TagApiContract::class

        // GenreApi for genre operations
        single {
            GenreApi(clientFactory = get())
        } bind GenreApiContract::class

        // FtsPopulator for rebuilding FTS tables after sync
        single {
            FtsPopulator(
                bookDao = get(),
                contributorDao = get(),
                seriesDao = get(),
                searchDao = get(),
            )
        } bind FtsPopulatorContract::class

        // SyncManager orchestrates sync operations
        single {
            SyncManager(
                syncApi = get(),
                bookDao = get(),
                seriesDao = get(),
                contributorDao = get(),
                chapterDao = get(),
                bookContributorDao = get(),
                bookSeriesDao = get(),
                syncDao = get(),
                imageDownloader = get(),
                sseManager = get(),
                ftsPopulator = get(),
                scope =
                    get(
                        qualifier =
                            org.koin.core.qualifier
                                .named("appScope"),
                    ),
            )
        } bind SyncManagerContract::class

        // SearchRepository for offline-first search
        single {
            SearchRepository(
                searchApi = get(),
                searchDao = get(),
                imageStorage = get(),
                networkMonitor = get(),
            )
        } bind SearchRepositoryContract::class

        // BookRepository for UI data access
        single {
            BookRepository(
                bookDao = get(),
                chapterDao = get(),
                syncManager = get(),
                imageStorage = get(),
            )
        } bind BookRepositoryContract::class

        // HomeRepository for Home screen data (local-first)
        single {
            HomeRepository(
                bookRepository = get(),
                playbackPositionDao = get(),
                userDao = get(),
            )
        } bind HomeRepositoryContract::class

        // ContributorRepository for contributor search with offline fallback
        single {
            ContributorRepository(
                api = get(),
                searchDao = get(),
                networkMonitor = get(),
            )
        } bind ContributorRepositoryContract::class

        // SeriesRepository for series search with offline fallback
        single {
            SeriesRepository(
                api = get(),
                searchDao = get(),
                networkMonitor = get(),
            )
        } bind SeriesRepositoryContract::class

        // BookEditRepository for book editing operations
        single {
            BookEditRepository(
                api = get(),
                bookDao = get(),
            )
        } bind BookEditRepositoryContract::class
    }

/**
 * All shared modules that should be loaded in both Android and iOS.
 */
val sharedModules =
    listOf(
        platformStorageModule,
        platformDatabaseModule,
        dataModule,
        networkModule,
        repositoryModule,
        useCaseModule,
        presentationModule,
        syncModule,
    )

/**
 * Platform-specific initialization function.
 * Each platform (Android/iOS) implements this to set up Koin appropriately.
 *
 * @param additionalModules Platform-specific modules to include
 */
expect fun initializeKoin(additionalModules: List<Module> = emptyList())
