package com.calypsan.listenup.client.di

import com.calypsan.listenup.client.core.SecureStorage
import com.calypsan.listenup.client.data.discovery.ServerDiscoveryService
import com.calypsan.listenup.client.data.local.db.BookContributorDao
import com.calypsan.listenup.client.data.local.db.BookDao
import com.calypsan.listenup.client.data.local.db.BookSeriesDao
import com.calypsan.listenup.client.data.local.db.ChapterDao
import com.calypsan.listenup.client.data.local.db.CollectionDao
import com.calypsan.listenup.client.data.local.db.ContributorDao
import com.calypsan.listenup.client.data.local.db.DownloadDao
import com.calypsan.listenup.client.data.local.db.LensDao
import com.calypsan.listenup.client.data.local.db.ListeningEventDao
import com.calypsan.listenup.client.data.local.db.PendingOperationDao
import com.calypsan.listenup.client.data.local.db.PlaybackPositionDao
import com.calypsan.listenup.client.data.local.db.SearchDao
import com.calypsan.listenup.client.data.local.db.SeriesDao
import com.calypsan.listenup.client.data.local.db.ServerDao
import com.calypsan.listenup.client.data.local.db.SyncDao
import com.calypsan.listenup.client.data.local.db.TagDao
import com.calypsan.listenup.client.data.local.db.UserDao
import com.calypsan.listenup.client.data.local.db.UserProfileDao
import com.calypsan.listenup.client.data.local.images.CoverColorExtractor
import com.calypsan.listenup.client.data.local.images.ImageStorage
import com.calypsan.listenup.client.data.remote.ApiClientFactory
import com.calypsan.listenup.client.data.remote.AuthApiContract
import com.calypsan.listenup.client.data.remote.BookApiContract
import com.calypsan.listenup.client.data.remote.ContributorApiContract
import com.calypsan.listenup.client.data.remote.GenreApiContract
import com.calypsan.listenup.client.data.remote.ImageApiContract
import com.calypsan.listenup.client.data.remote.InstanceApiContract
import com.calypsan.listenup.client.data.remote.ListenUpApiContract
import com.calypsan.listenup.client.data.remote.SearchApiContract
import com.calypsan.listenup.client.data.remote.SeriesApiContract
import com.calypsan.listenup.client.data.remote.SyncApiContract
import com.calypsan.listenup.client.data.remote.TagApiContract
import com.calypsan.listenup.client.data.remote.UserPreferencesApiContract
import com.calypsan.listenup.client.data.repository.AuthSessionContract
import com.calypsan.listenup.client.data.repository.LibraryPreferencesContract
import com.calypsan.listenup.client.data.repository.LibrarySyncContract
import com.calypsan.listenup.client.data.repository.NetworkMonitor
import com.calypsan.listenup.client.data.repository.PlaybackPreferencesContract
import com.calypsan.listenup.client.data.repository.ServerConfigContract
import com.calypsan.listenup.client.data.repository.SettingsRepository
import com.calypsan.listenup.client.data.repository.SettingsRepositoryContract
import com.calypsan.listenup.client.data.sync.push.OperationHandler
import com.calypsan.listenup.client.data.sync.sse.PlaybackStateProvider
import com.calypsan.listenup.client.domain.repository.InstanceRepository
import com.calypsan.listenup.client.download.DownloadService
import com.calypsan.listenup.client.playback.PlaybackManager
import kotlinx.coroutines.CoroutineScope
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify
import kotlin.test.Test

/**
 * Verifies Koin module definitions are correctly configured.
 *
 * This test uses Koin's verify() API to statically check that all constructor
 * dependencies have corresponding definitions. This catches issues like:
 * - Missing interface bindings (e.g., concrete class registered but interface not bound)
 * - Missing dependency definitions
 * - Circular dependencies
 *
 * Note: Platform-specific dependencies (DAOs, APIs, etc.) are declared as extraTypes
 * since they're defined in platform modules that can't be loaded in commonTest.
 */
@OptIn(KoinExperimentalAPI::class)
class KoinModuleVerifyTest {
    /**
     * Verify syncModule definitions.
     *
     * This module contains the sync infrastructure (PushSyncOrchestrator, handlers, etc.)
     * which was the source of the OperationExecutorContract binding issue.
     */
    @Test
    fun verifySyncModule() {
        syncModule.verify(
            extraTypes =
                listOf(
                    // Platform-specific types provided by other modules
                    CoroutineScope::class,
                    SecureStorage::class,
                    ImageStorage::class,
                    CoverColorExtractor::class,
                    NetworkMonitor::class,
                    Map::class, // For OperationExecutor constructor (uses factory method in reality)
                    OperationHandler::class,
                    // DAOs from database module
                    UserDao::class,
                    BookDao::class,
                    SyncDao::class,
                    ChapterDao::class,
                    SeriesDao::class,
                    ContributorDao::class,
                    BookContributorDao::class,
                    BookSeriesDao::class,
                    CollectionDao::class,
                    LensDao::class,
                    ListeningEventDao::class,
                    TagDao::class,
                    UserProfileDao::class,
                    PlaybackPositionDao::class,
                    PendingOperationDao::class,
                    DownloadDao::class,
                    SearchDao::class,
                    ServerDao::class,
                    // Playback and download services
                    PlaybackManager::class,
                    PlaybackStateProvider::class,
                    DownloadService::class,
                    // Repositories and APIs from other modules
                    SettingsRepository::class,
                    SettingsRepositoryContract::class,
                    AuthSessionContract::class,
                    LibrarySyncContract::class,
                    ServerConfigContract::class,
                    LibraryPreferencesContract::class,
                    PlaybackPreferencesContract::class,
                    InstanceRepository::class,
                    AuthApiContract::class,
                    ApiClientFactory::class,
                    ListenUpApiContract::class,
                    InstanceApiContract::class,
                    BookApiContract::class,
                    ContributorApiContract::class,
                    SeriesApiContract::class,
                    SyncApiContract::class,
                    SearchApiContract::class,
                    ImageApiContract::class,
                    GenreApiContract::class,
                    TagApiContract::class,
                    UserPreferencesApiContract::class,
                    ServerDiscoveryService::class,
                ),
        )
    }
}
