@file:Suppress("StringLiteralDuplication")

package com.calypsan.listenup.client.di

import com.calypsan.listenup.client.core.ServerUrl
import com.calypsan.listenup.client.data.local.db.ListenUpDatabase
import com.calypsan.listenup.client.data.local.db.platformDatabaseModule
import com.calypsan.listenup.client.data.remote.AdminApi
import com.calypsan.listenup.client.data.remote.AdminApiContract
import com.calypsan.listenup.client.data.remote.AdminCollectionApi
import com.calypsan.listenup.client.data.remote.AdminCollectionApiContract
import com.calypsan.listenup.client.data.remote.ApiClientFactory
import com.calypsan.listenup.client.data.remote.AuthApi
import com.calypsan.listenup.client.data.remote.AuthApiContract
import com.calypsan.listenup.client.data.remote.BookApiContract
import com.calypsan.listenup.client.data.remote.ContributorApiContract
import com.calypsan.listenup.client.data.remote.GenreApi
import com.calypsan.listenup.client.data.remote.GenreApiContract
import com.calypsan.listenup.client.data.remote.ImageApi
import com.calypsan.listenup.client.data.remote.ImageApiContract
import com.calypsan.listenup.client.data.remote.InstanceApiContract
import com.calypsan.listenup.client.data.remote.InviteApi
import com.calypsan.listenup.client.data.remote.InviteApiContract
import com.calypsan.listenup.client.data.remote.LensApi
import com.calypsan.listenup.client.data.remote.LensApiContract
import com.calypsan.listenup.client.data.remote.ListenUpApiContract
import com.calypsan.listenup.client.data.remote.MetadataApi
import com.calypsan.listenup.client.data.remote.MetadataApiContract
import com.calypsan.listenup.client.data.remote.SearchApi
import com.calypsan.listenup.client.data.remote.SearchApiContract
import com.calypsan.listenup.client.data.remote.SeriesApiContract
import com.calypsan.listenup.client.data.remote.StatsApi
import com.calypsan.listenup.client.data.remote.StatsApiContract
import com.calypsan.listenup.client.data.remote.LeaderboardApi
import com.calypsan.listenup.client.data.remote.LeaderboardApiContract
import com.calypsan.listenup.client.data.remote.SyncApi
import com.calypsan.listenup.client.data.remote.SyncApiContract
import com.calypsan.listenup.client.data.remote.TagApi
import com.calypsan.listenup.client.data.remote.TagApiContract
import com.calypsan.listenup.client.data.remote.UserPreferencesApi
import com.calypsan.listenup.client.data.remote.UserPreferencesApiContract
import com.calypsan.listenup.client.data.remote.api.ListenUpApi
import com.calypsan.listenup.client.data.repository.AuthSessionContract
import com.calypsan.listenup.client.data.repository.BookEditRepository
import com.calypsan.listenup.client.data.repository.BookEditRepositoryContract
import com.calypsan.listenup.client.data.repository.BookRepository
import com.calypsan.listenup.client.data.repository.BookRepositoryContract
import com.calypsan.listenup.client.data.repository.ContributorEditRepository
import com.calypsan.listenup.client.data.repository.ContributorEditRepositoryContract
import com.calypsan.listenup.client.data.repository.ContributorRepository
import com.calypsan.listenup.client.data.repository.ContributorRepositoryContract
import com.calypsan.listenup.client.data.repository.DeepLinkManager
import com.calypsan.listenup.client.data.repository.HomeRepository
import com.calypsan.listenup.client.data.repository.HomeRepositoryContract
import com.calypsan.listenup.client.data.repository.InstanceRepositoryImpl
import com.calypsan.listenup.client.data.repository.LibraryPreferencesContract
import com.calypsan.listenup.client.data.repository.LibrarySyncContract
import com.calypsan.listenup.client.data.repository.LocalPreferencesContract
import com.calypsan.listenup.client.data.repository.MetadataRepository
import com.calypsan.listenup.client.data.repository.MetadataRepositoryContract
import com.calypsan.listenup.client.data.repository.PlaybackPreferencesContract
import com.calypsan.listenup.client.data.repository.SearchRepository
import com.calypsan.listenup.client.data.repository.SearchRepositoryContract
import com.calypsan.listenup.client.data.repository.SeriesEditRepository
import com.calypsan.listenup.client.data.repository.SeriesEditRepositoryContract
import com.calypsan.listenup.client.data.repository.SeriesRepository
import com.calypsan.listenup.client.data.repository.SeriesRepositoryContract
import com.calypsan.listenup.client.data.repository.ServerConfigContract
import com.calypsan.listenup.client.data.repository.ServerMigrationHelper
import com.calypsan.listenup.client.data.repository.ServerRepository
import com.calypsan.listenup.client.data.repository.ServerRepositoryContract
import com.calypsan.listenup.client.data.repository.ServerUrlChangeListener
import com.calypsan.listenup.client.data.repository.SettingsRepository
import com.calypsan.listenup.client.data.repository.SettingsRepositoryContract
import com.calypsan.listenup.client.data.sync.FtsPopulator
import com.calypsan.listenup.client.data.sync.FtsPopulatorContract
import com.calypsan.listenup.client.data.sync.ImageDownloader
import com.calypsan.listenup.client.data.sync.ImageDownloaderContract
import com.calypsan.listenup.client.data.sync.LibraryResetHelper
import com.calypsan.listenup.client.data.sync.LibraryResetHelperContract
import com.calypsan.listenup.client.data.sync.SSEManager
import com.calypsan.listenup.client.data.sync.SSEManagerContract
import com.calypsan.listenup.client.data.sync.SyncCoordinator
import com.calypsan.listenup.client.data.sync.SyncManager
import com.calypsan.listenup.client.data.sync.SyncManagerContract
import com.calypsan.listenup.client.data.sync.conflict.ConflictDetector
import com.calypsan.listenup.client.data.sync.conflict.ConflictDetectorContract
import com.calypsan.listenup.client.data.sync.pull.BookPuller
import com.calypsan.listenup.client.data.sync.pull.ContributorPuller
import com.calypsan.listenup.client.data.sync.pull.PullSyncOrchestrator
import com.calypsan.listenup.client.data.sync.pull.Puller
import com.calypsan.listenup.client.data.sync.pull.SeriesPuller
import com.calypsan.listenup.client.data.sync.pull.TagPuller
import com.calypsan.listenup.client.data.sync.push.BookUpdateHandler
import com.calypsan.listenup.client.data.sync.push.ContributorUpdateHandler
import com.calypsan.listenup.client.data.sync.push.ListeningEventHandler
import com.calypsan.listenup.client.data.sync.push.MergeContributorHandler
import com.calypsan.listenup.client.data.sync.push.OperationExecutor
import com.calypsan.listenup.client.data.sync.push.OperationExecutorContract
import com.calypsan.listenup.client.data.sync.push.PendingOperationRepository
import com.calypsan.listenup.client.data.sync.push.PendingOperationRepositoryContract
import com.calypsan.listenup.client.data.sync.push.PlaybackPositionHandler
import com.calypsan.listenup.client.data.sync.push.PreferencesSyncObserver
import com.calypsan.listenup.client.data.sync.push.PushSyncOrchestrator
import com.calypsan.listenup.client.data.sync.push.PushSyncOrchestratorContract
import com.calypsan.listenup.client.data.sync.push.SeriesUpdateHandler
import com.calypsan.listenup.client.data.sync.push.SetBookContributorsHandler
import com.calypsan.listenup.client.data.sync.push.SetBookSeriesHandler
import com.calypsan.listenup.client.data.sync.push.UnmergeContributorHandler
import com.calypsan.listenup.client.data.sync.push.UserPreferencesHandler
import com.calypsan.listenup.client.data.sync.sse.SSEEventProcessor
import com.calypsan.listenup.client.domain.repository.InstanceRepository
import com.calypsan.listenup.client.domain.usecase.GetInstanceUseCase
import com.calypsan.listenup.client.playback.PlaybackManager
import com.calypsan.listenup.client.presentation.admin.AdminViewModel
import com.calypsan.listenup.client.presentation.admin.CreateInviteViewModel
import com.calypsan.listenup.client.presentation.auth.PendingApprovalViewModel
import com.calypsan.listenup.client.presentation.connect.ServerConnectViewModel
import com.calypsan.listenup.client.presentation.connect.ServerSelectViewModel
import com.calypsan.listenup.client.presentation.discover.LeaderboardViewModel
import com.calypsan.listenup.client.presentation.home.HomeStatsViewModel
import com.calypsan.listenup.client.presentation.invite.InviteRegistrationViewModel
import com.calypsan.listenup.client.presentation.library.LibraryViewModel
import com.calypsan.listenup.client.presentation.settings.SettingsViewModel
import com.calypsan.listenup.client.presentation.sync.SyncIndicatorViewModel
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
 * Platform-specific discovery module.
 * Each platform provides mDNS/Bonjour discovery implementation.
 */
expect val platformDiscoveryModule: Module

/**
 * Data layer dependencies.
 * Provides repositories for settings and domain data.
 */
val dataModule =
    module {
        // Deep link manager - singleton for handling invite deep links
        // Must be initialized before MainActivity handles intents
        single { DeepLinkManager() }

        // Settings repository - single source of truth for app configuration
        // Note: SettingsRepository has no sync dependencies - it emits preference change events
        // that are observed by PreferencesSyncObserver (in syncModule) to avoid circular deps.
        single {
            SettingsRepository(
                secureStorage = get(),
                instanceRepository = get(),
            )
        }

        // Bind segregated interfaces to the same SettingsRepository instance (ISP compliance)
        single<SettingsRepositoryContract> { get<SettingsRepository>() }
        single<AuthSessionContract> { get<SettingsRepository>() }
        single<ServerConfigContract> { get<SettingsRepository>() }
        single<LibrarySyncContract> { get<SettingsRepository>() }
        single<LibraryPreferencesContract> { get<SettingsRepository>() }
        single<PlaybackPreferencesContract> { get<SettingsRepository>() }
        single<LocalPreferencesContract> { get<SettingsRepository>() }
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

        // InviteApi - handles public invite operations (no auth required)
        // Server URL comes from deep link, not stored settings
        single { InviteApi() } bind InviteApiContract::class

        // ApiClientFactory - creates authenticated HTTP clients with auto-refresh
        single {
            ApiClientFactory(
                settingsRepository = get(),
                authApi = get(),
            )
        }

        // ListenUpApi - main API for server communication
        // Uses default base URL initially; can be recreated when server URL changes
        single {
            ListenUpApi(
                baseUrl = getBaseUrl(),
                apiClientFactory = get(),
            )
        }

        // Bind segregated interfaces to the same ListenUpApi instance (ISP compliance)
        single<ListenUpApiContract> { get<ListenUpApi>() }
        single<InstanceApiContract> { get<ListenUpApi>() }
        single<BookApiContract> { get<ListenUpApi>() }
        single<ContributorApiContract> { get<ListenUpApi>() }
        single<SeriesApiContract> { get<ListenUpApi>() }
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
        // InstanceRepository reads server URL directly from SecureStorage to avoid circular
        // dependency (SettingsRepository -> InstanceRepository -> SettingsRepository).
        // The URL is stored before checkServerStatus() is called.
        single<InstanceRepository> {
            val secureStorage: com.calypsan.listenup.client.core.SecureStorage = get()
            InstanceRepositoryImpl(
                getServerUrl = {
                    secureStorage.read("server_url")?.let { ServerUrl(it) }
                },
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
        single { get<ListenUpDatabase>().pendingOperationDao() }
        single { get<ListenUpDatabase>().downloadDao() }
        single { get<ListenUpDatabase>().searchDao() }
        single { get<ListenUpDatabase>().serverDao() }
        single { get<ListenUpDatabase>().collectionDao() }
        single { get<ListenUpDatabase>().lensDao() }
        single { get<ListenUpDatabase>().tagDao() }

        // ServerRepository - bridges mDNS discovery with database persistence
        // When active server's URL changes via mDNS rediscovery, updates SettingsRepository
        // and invalidates the API client cache to use the new IP address.
        single {
            ServerRepository(
                serverDao = get(),
                discoveryService = get(),
                scope =
                    get(
                        qualifier =
                            org.koin.core.qualifier
                                .named("appScope"),
                    ),
                urlChangeListener =
                    ServerUrlChangeListener { newUrl ->
                        // Update settings with new URL and invalidate API client
                        val settings: SettingsRepositoryContract = get()
                        val apiClientFactory: ApiClientFactory = get()
                        settings.setServerUrl(newUrl)
                        apiClientFactory.invalidate()
                    },
            )
        } bind ServerRepositoryContract::class

        // ServerMigrationHelper - migrates legacy single-server data
        single {
            ServerMigrationHelper(
                secureStorage = get(),
                serverDao = get(),
            )
        }
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
        factory { ServerSelectViewModel(serverRepository = get(), settingsRepository = get()) }
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
            com.calypsan.listenup.client.presentation.auth.RegisterViewModel(
                authApi = get(),
                settingsRepository = get(),
            )
        }
        // PendingApprovalViewModel - takes userId, email, password as parameters
        factory { params ->
            PendingApprovalViewModel(
                authApi = get(),
                settingsRepository = get(),
                apiClientFactory = get(),
                userId = params.get<String>(0),
                email = params.get<String>(1),
                password = params.get<String>(2),
            )
        }
        // InviteRegistrationViewModel - takes serverUrl and inviteCode as parameters
        factory { params ->
            InviteRegistrationViewModel(
                inviteApi = get(),
                settingsRepository = get(),
                userDao = get(),
                serverUrl = params.get<String>(0),
                inviteCode = params.get<String>(1),
            )
        }
        // Admin ViewModels
        factory { AdminViewModel(adminApi = get(), instanceApi = get(), sseManager = get()) }
        factory { CreateInviteViewModel(adminApi = get()) }
        factory {
            com.calypsan.listenup.client.presentation.admin.AdminSettingsViewModel(
                adminApi = get(),
            )
        }
        factory {
            com.calypsan.listenup.client.presentation.admin.AdminInboxViewModel(
                adminApi = get(),
                sseManager = get(),
            )
        }
        factory {
            com.calypsan.listenup.client.presentation.admin.AdminCollectionsViewModel(
                collectionDao = get(),
                adminCollectionApi = get(),
            )
        }
        // AdminCollectionDetailViewModel - takes collectionId as parameter
        factory { params ->
            com.calypsan.listenup.client.presentation.admin.AdminCollectionDetailViewModel(
                collectionId = params.get<String>(0),
                collectionDao = get(),
                adminCollectionApi = get(),
                adminApi = get(),
                userDao = get(),
            )
        }
        // LibraryViewModel as singleton for preloading - starts loading Room data
        // immediately when injected at AppShell level, making Library instant
        single {
            LibraryViewModel(
                bookRepository = get(),
                seriesDao = get(),
                contributorDao = get(),
                syncManager = get(),
                settingsRepository = get(),
                syncDao = get(),
                playbackPositionDao = get(),
                userDao = get(),
                collectionDao = get(),
                adminCollectionApi = get(),
                lensDao = get(),
                lensApi = get(),
            )
        }
        factory {
            com.calypsan.listenup.client.presentation.bookdetail.BookDetailViewModel(
                bookRepository = get(),
                genreApi = get(),
                tagApi = get(),
                tagDao = get(),
                playbackPositionDao = get(),
                userDao = get(),
            )
        }
        factory {
            com.calypsan.listenup.client.presentation.seriesdetail.SeriesDetailViewModel(
                seriesDao = get(),
                bookRepository = get(),
                imageStorage = get(),
            )
        }
        factory {
            com.calypsan.listenup.client.presentation.tagdetail.TagDetailViewModel(
                tagDao = get(),
                bookRepository = get(),
            )
        }
        factory {
            com.calypsan.listenup.client.presentation.lens.LensDetailViewModel(
                lensApi = get(),
                lensDao = get(),
                userDao = get(),
                imageStorage = get(),
            )
        }
        factory {
            com.calypsan.listenup.client.presentation.lens.CreateEditLensViewModel(
                lensApi = get(),
                lensDao = get(),
            )
        }
        factory {
            com.calypsan.listenup.client.presentation.discover.DiscoverViewModel(
                lensApi = get(),
            )
        }
        factory {
            com.calypsan.listenup.client.presentation.contributordetail.ContributorDetailViewModel(
                contributorDao = get(),
                bookDao = get(),
                imageStorage = get(),
                playbackPositionDao = get(),
                contributorRepository = get(),
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
            com.calypsan.listenup.client.presentation.contributormetadata.ContributorMetadataViewModel(
                contributorDao = get(),
                metadataApi = get(),
                imageApi = get(),
                imageStorage = get(),
            )
        }
        factory {
            com.calypsan.listenup.client.presentation.home.HomeViewModel(
                homeRepository = get(),
                lensDao = get(),
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
                imageApi = get(),
                imageStorage = get(),
            )
        }
        factory {
            com.calypsan.listenup.client.presentation.contributoredit.ContributorEditViewModel(
                contributorDao = get(),
                contributorRepository = get(),
                contributorEditRepository = get(),
                imageApi = get(),
                imageStorage = get(),
            )
        }
        factory {
            com.calypsan.listenup.client.presentation.seriesedit.SeriesEditViewModel(
                seriesDao = get(),
                seriesEditRepository = get(),
                imageStorage = get(),
                imageApi = get(),
            )
        }
        factory {
            SettingsViewModel(
                settingsRepository = get(),
                userPreferencesApi = get(),
                instanceRepository = get(),
                serverConfigContract = get(),
                authSessionContract = get(),
            )
        }
        // MetadataViewModel for Audible metadata search and matching
        factory {
            com.calypsan.listenup.client.presentation.metadata.MetadataViewModel(
                metadataRepository = get(),
                imageDownloader = get(),
            )
        }
        // SyncIndicatorViewModel as singleton for app-wide sync status
        single { SyncIndicatorViewModel(pendingOperationRepository = get()) }

        // HomeStatsViewModel for home screen stats section
        factory { HomeStatsViewModel(statsApi = get()) }

        // LeaderboardViewModel for discover screen leaderboard
        factory { LeaderboardViewModel(leaderboardApi = get()) }
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

        // Image API for downloading cover images and uploading images
        single {
            ImageApi(clientFactory = get(), settingsRepository = get())
        } bind ImageApiContract::class

        // Image downloader for batch cover downloads during sync
        single {
            ImageDownloader(
                imageApi = get(),
                imageStorage = get(),
                colorExtractor = get(),
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

        // StatsApi for listening statistics
        single {
            StatsApi(clientFactory = get())
        } bind StatsApiContract::class

        // LeaderboardApi for social leaderboard
        single {
            LeaderboardApi(clientFactory = get())
        } bind LeaderboardApiContract::class

        // AdminApi for admin operations (user/invite management)
        single {
            AdminApi(clientFactory = get())
        } bind AdminApiContract::class

        // MetadataApi for Audible metadata search and matching
        single {
            MetadataApi(clientFactory = get())
        } bind MetadataApiContract::class

        // MetadataRepository for metadata operations
        single {
            MetadataRepository(metadataApi = get())
        } bind MetadataRepositoryContract::class

        // AdminCollectionApi for admin collection operations
        single {
            AdminCollectionApi(clientFactory = get())
        } bind AdminCollectionApiContract::class

        // UserPreferencesApi for syncing user preferences across devices
        single {
            UserPreferencesApi(clientFactory = get())
        } bind UserPreferencesApiContract::class

        // LensApi for personal curation lenses
        single {
            LensApi(clientFactory = get())
        } bind LensApiContract::class

        // FtsPopulator for rebuilding FTS tables after sync
        single {
            FtsPopulator(
                bookDao = get(),
                contributorDao = get(),
                seriesDao = get(),
                searchDao = get(),
            )
        } bind FtsPopulatorContract::class

        // Sync infrastructure - decomposed components

        // SyncCoordinator - retry logic and error classification
        single { SyncCoordinator() }

        // ConflictDetector - timestamp-based conflict detection
        single {
            ConflictDetector(
                bookDao = get(),
                contributorDao = get(),
                seriesDao = get(),
            )
        } bind ConflictDetectorContract::class

        // SSEEventProcessor - processes real-time SSE events
        single {
            SSEEventProcessor(
                bookDao = get(),
                bookContributorDao = get(),
                bookSeriesDao = get(),
                collectionDao = get(),
                lensDao = get(),
                tagDao = get(),
                imageDownloader = get(),
                playbackStateProvider = get<PlaybackManager>(),
                downloadService = get(),
                scope =
                    get(
                        qualifier =
                            org.koin.core.qualifier
                                .named("appScope"),
                    ),
            )
        }

        // Entity pullers - fetch data from server with pagination
        single<Puller>(
            qualifier =
                org.koin.core.qualifier
                    .named("bookPuller"),
        ) {
            BookPuller(
                syncApi = get(),
                bookDao = get(),
                chapterDao = get(),
                bookContributorDao = get(),
                bookSeriesDao = get(),
                tagDao = get(),
                imageDownloader = get(),
                conflictDetector = get(),
                scope =
                    get(
                        qualifier =
                            org.koin.core.qualifier
                                .named("appScope"),
                    ),
            )
        }

        single<Puller>(
            qualifier =
                org.koin.core.qualifier
                    .named("seriesPuller"),
        ) {
            SeriesPuller(
                syncApi = get(),
                seriesDao = get(),
                imageDownloader = get(),
                scope =
                    get(
                        qualifier =
                            org.koin.core.qualifier
                                .named("appScope"),
                    ),
            )
        }

        single<Puller>(
            qualifier =
                org.koin.core.qualifier
                    .named("contributorPuller"),
        ) {
            ContributorPuller(
                syncApi = get(),
                contributorDao = get(),
                imageDownloader = get(),
                scope =
                    get(
                        qualifier =
                            org.koin.core.qualifier
                                .named("appScope"),
                    ),
            )
        }

        single<Puller>(
            qualifier =
                org.koin.core.qualifier
                    .named("tagPuller"),
        ) {
            TagPuller(
                tagApi = get(),
                tagDao = get(),
            )
        }

        // PullSyncOrchestrator - coordinates parallel entity pulls
        single {
            PullSyncOrchestrator(
                bookPuller =
                    get(
                        qualifier =
                            org.koin.core.qualifier
                                .named("bookPuller"),
                    ),
                seriesPuller =
                    get(
                        qualifier =
                            org.koin.core.qualifier
                                .named("seriesPuller"),
                    ),
                contributorPuller =
                    get(
                        qualifier =
                            org.koin.core.qualifier
                                .named("contributorPuller"),
                    ),
                tagPuller =
                    get(
                        qualifier =
                            org.koin.core.qualifier
                                .named("tagPuller"),
                    ),
                coordinator = get(),
                syncDao = get(),
            )
        }

        // Push sync handlers
        single { BookUpdateHandler(api = get()) }
        single { ContributorUpdateHandler(api = get()) }
        single { SeriesUpdateHandler(api = get()) }
        single { SetBookContributorsHandler(api = get()) }
        single { SetBookSeriesHandler(api = get()) }
        single { MergeContributorHandler(api = get()) }
        single { UnmergeContributorHandler(api = get()) }
        single { ListeningEventHandler(api = get()) }
        single { PlaybackPositionHandler(api = get()) }
        single { UserPreferencesHandler(api = get()) }

        // PreferencesSyncObserver - observes SettingsRepository.preferenceChanges and queues sync operations.
        // This breaks the circular dependency between SettingsRepository and the sync layer.
        // Started automatically on creation via the appScope.
        single {
            PreferencesSyncObserver(
                settingsRepository = get(),
                pendingOperationRepository = get(),
                userPreferencesHandler = get(),
            ).also { observer ->
                observer.start(
                    scope =
                        get(
                            qualifier =
                                org.koin.core.qualifier
                                    .named("appScope"),
                        ),
                )
            }
        }

        // OperationExecutor - dispatches to handlers
        single {
            OperationExecutor.create(
                bookUpdateHandler = get(),
                contributorUpdateHandler = get(),
                seriesUpdateHandler = get(),
                setBookContributorsHandler = get(),
                setBookSeriesHandler = get(),
                mergeContributorHandler = get(),
                unmergeContributorHandler = get(),
                listeningEventHandler = get(),
                playbackPositionHandler = get(),
                userPreferencesHandler = get(),
            )
        } bind OperationExecutorContract::class

        // PendingOperationRepository - queue and coalesce operations
        single {
            PendingOperationRepository(
                dao = get(),
                bookDao = get(),
                contributorDao = get(),
                seriesDao = get(),
            )
        } bind PendingOperationRepositoryContract::class

        // PushSyncOrchestrator - flush pending operations
        single {
            PushSyncOrchestrator(
                repository = get(),
                executor = get(),
                conflictDetector = get(),
                networkMonitor = get(),
                scope =
                    get(
                        qualifier =
                            org.koin.core.qualifier
                                .named("appScope"),
                    ),
            )
        } bind PushSyncOrchestratorContract::class

        // LibraryResetHelper - clears local data on library mismatch or server switch
        single {
            LibraryResetHelper(
                bookDao = get(),
                seriesDao = get(),
                contributorDao = get(),
                chapterDao = get(),
                bookContributorDao = get(),
                bookSeriesDao = get(),
                playbackPositionDao = get(),
                pendingOperationDao = get(),
                userDao = get(),
                syncDao = get(),
                librarySyncContract = get(),
            )
        } bind LibraryResetHelperContract::class

        // SyncManager - thin orchestrator coordinating sync phases
        single {
            SyncManager(
                pullOrchestrator = get(),
                pushOrchestrator = get(),
                sseEventProcessor = get(),
                coordinator = get(),
                sseManager = get(),
                userPreferencesApi = get(),
                settingsRepository = get(),
                instanceRepository = get(),
                pendingOperationDao = get(),
                libraryResetHelper = get(),
                syncDao = get(),
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

        // HomeRepository for Home screen data (cross-device sync)
        single {
            HomeRepository(
                bookRepository = get(),
                playbackPositionDao = get(),
                syncApi = get(),
                userDao = get(),
                networkMonitor = get(),
            )
        } bind HomeRepositoryContract::class

        // ContributorRepository for contributor search with offline fallback
        single {
            ContributorRepository(
                api = get(),
                metadataApi = get(),
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

        // BookEditRepository for book editing operations (offline-first)
        single {
            BookEditRepository(
                bookDao = get(),
                pendingOperationRepository = get(),
                bookUpdateHandler = get(),
                setBookContributorsHandler = get(),
                setBookSeriesHandler = get(),
            )
        } bind BookEditRepositoryContract::class

        // SeriesEditRepository for series editing operations (offline-first)
        single {
            SeriesEditRepository(
                seriesDao = get(),
                pendingOperationRepository = get(),
                seriesUpdateHandler = get(),
            )
        } bind SeriesEditRepositoryContract::class

        // ContributorEditRepository for contributor editing operations (offline-first)
        single {
            ContributorEditRepository(
                contributorDao = get(),
                bookContributorDao = get(),
                pendingOperationRepository = get(),
                contributorUpdateHandler = get(),
                mergeContributorHandler = get(),
                unmergeContributorHandler = get(),
            )
        } bind ContributorEditRepositoryContract::class
    }

/**
 * All shared modules that should be loaded in both Android and iOS.
 */
val sharedModules =
    listOf(
        platformStorageModule,
        platformDatabaseModule,
        platformDiscoveryModule,
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
