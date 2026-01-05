package com.calypsan.listenup.client.di

import com.calypsan.listenup.client.presentation.admin.AdminViewModel
import com.calypsan.listenup.client.presentation.admin.CreateInviteViewModel
import com.calypsan.listenup.client.presentation.auth.PendingApprovalViewModel
import com.calypsan.listenup.client.presentation.connect.ServerConnectViewModel
import com.calypsan.listenup.client.presentation.connect.ServerSelectViewModel
import com.calypsan.listenup.client.presentation.discover.ActivityFeedViewModel
import com.calypsan.listenup.client.presentation.discover.LeaderboardViewModel
import com.calypsan.listenup.client.presentation.home.HomeStatsViewModel
import com.calypsan.listenup.client.presentation.invite.InviteRegistrationViewModel
import com.calypsan.listenup.client.presentation.library.LibraryActionsViewModel
import com.calypsan.listenup.client.presentation.library.LibrarySelectionManager
import com.calypsan.listenup.client.presentation.library.LibraryViewModel
import com.calypsan.listenup.client.presentation.settings.SettingsViewModel
import com.calypsan.listenup.client.presentation.sync.SyncIndicatorViewModel
import org.koin.dsl.module

/**
 * Auth and connection ViewModels.
 */
val authPresentationModule =
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
    }

/**
 * Admin ViewModels.
 */
val adminPresentationModule =
    module {
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
    }

/**
 * Library and core browsing ViewModels.
 */
val libraryPresentationModule =
    module {
        // Shared selection state - singleton so both ViewModels observe the same state
        single { LibrarySelectionManager() }

        // LibraryViewModel as singleton for preloading - starts loading Room data
        // immediately when injected at AppShell level, making Library instant
        single {
            LibraryViewModel(
                bookRepository = get(),
                seriesDao = get(),
                contributorDao = get(),
                playbackPositionDao = get(),
                syncManager = get(),
                settingsRepository = get(),
                syncDao = get(),
                selectionManager = get(),
            )
        }

        // LibraryActionsViewModel as singleton - shares selection state with LibraryViewModel
        single {
            LibraryActionsViewModel(
                selectionManager = get(),
                userDao = get(),
                collectionDao = get(),
                adminCollectionApi = get(),
                lensDao = get(),
                lensApi = get(),
            )
        }

        factory {
            com.calypsan.listenup.client.presentation.search.SearchViewModel(
                searchRepository = get(),
            )
        }
    }

/**
 * Book and content detail ViewModels.
 */
val bookPresentationModule =
    module {
        factory {
            com.calypsan.listenup.client.presentation.bookdetail.BookDetailViewModel(
                bookRepository = get(),
                genreDao = get(),
                tagApi = get(),
                tagDao = get(),
                playbackPositionDao = get(),
                userDao = get(),
            )
        }
        factory {
            com.calypsan.listenup.client.presentation.bookdetail.BookReadersViewModel(
                sessionApi = get(),
                sseManager = get(),
            )
        }
        factory {
            com.calypsan.listenup.client.presentation.bookedit.BookEditViewModel(
                bookRepository = get(),
                bookEditRepository = get(),
                contributorRepository = get(),
                seriesRepository = get(),
                genreApi = get(),
                genreDao = get(),
                tagApi = get(),
                tagDao = get(),
                imageApi = get(),
                imageStorage = get(),
            )
        }
        // MetadataViewModel for Audible metadata search and matching
        factory {
            com.calypsan.listenup.client.presentation.metadata.MetadataViewModel(
                metadataRepository = get(),
                imageDownloader = get(),
            )
        }
    }

/**
 * Series ViewModels.
 */
val seriesPresentationModule =
    module {
        factory {
            com.calypsan.listenup.client.presentation.seriesdetail.SeriesDetailViewModel(
                seriesDao = get(),
                bookRepository = get(),
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
    }

/**
 * Contributor ViewModels.
 */
val contributorPresentationModule =
    module {
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
            com.calypsan.listenup.client.presentation.contributoredit.ContributorEditViewModel(
                contributorDao = get(),
                contributorRepository = get(),
                contributorEditRepository = get(),
                imageApi = get(),
                imageStorage = get(),
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
    }

/**
 * Discover and social ViewModels.
 */
val discoverPresentationModule =
    module {
        factory {
            com.calypsan.listenup.client.presentation.home.HomeViewModel(
                homeRepository = get(),
                bookDao = get(),
                lensDao = get(),
            )
        }
        // HomeStatsViewModel for home screen stats section (observes local stats)
        factory { HomeStatsViewModel(statsRepository = get()) }
        factory {
            com.calypsan.listenup.client.presentation.discover.DiscoverViewModel(
                bookDao = get(),
                activeSessionDao = get(),
                authSession = get(),
                lensDao = get(),
                lensApi = get(),
                imageStorage = get(),
            )
        }
        // LeaderboardViewModel for discover screen leaderboard
        factory { LeaderboardViewModel(leaderboardRepository = get()) }
        // ActivityFeedViewModel for discover screen activity feed
        factory { ActivityFeedViewModel(activityDao = get(), activityFeedApi = get()) }
    }

/**
 * Tag and lens ViewModels.
 */
val tagLensPresentationModule =
    module {
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
    }

/**
 * User profile ViewModels.
 */
val profilePresentationModule =
    module {
        // UserProfileViewModel for viewing user profiles
        factory {
            com.calypsan.listenup.client.presentation.profile.UserProfileViewModel(
                profileApi = get(),
                userDao = get(),
                imageStorage = get(),
                imageDownloader = get(),
            )
        }
        // EditProfileViewModel for editing own profile
        factory {
            com.calypsan.listenup.client.presentation.profile.EditProfileViewModel(
                profileEditRepository = get(),
                userDao = get(),
                imageStorage = get(),
            )
        }
    }

/**
 * Settings and sync indicator ViewModels.
 */
val settingsPresentationModule =
    module {
        factory {
            SettingsViewModel(
                settingsRepository = get(),
                userPreferencesApi = get(),
                instanceRepository = get(),
                serverConfigContract = get(),
                authSessionContract = get(),
            )
        }
        // SyncIndicatorViewModel as singleton for app-wide sync status
        single { SyncIndicatorViewModel(pendingOperationRepository = get()) }
    }

/**
 * All presentation modules combined.
 */
val allPresentationModules =
    listOf(
        authPresentationModule,
        adminPresentationModule,
        libraryPresentationModule,
        bookPresentationModule,
        seriesPresentationModule,
        contributorPresentationModule,
        discoverPresentationModule,
        tagLensPresentationModule,
        profilePresentationModule,
        settingsPresentationModule,
    )
