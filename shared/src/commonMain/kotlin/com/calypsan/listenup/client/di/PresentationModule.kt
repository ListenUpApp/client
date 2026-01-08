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
        factory { ServerSelectViewModel(serverRepository = get(), serverConfig = get()) }
        factory { ServerConnectViewModel(serverConfig = get(), instanceRepository = get()) }
        factory {
            com.calypsan.listenup.client.presentation.auth.SetupViewModel(
                authRepository = get(),
                authSession = get(),
                userRepository = get(),
            )
        }
        factory {
            com.calypsan.listenup.client.presentation.auth.LoginViewModel(
                loginUseCase = get(),
            )
        }
        factory {
            com.calypsan.listenup.client.presentation.auth.RegisterViewModel(
                registerUseCase = get(),
            )
        }
        // PendingApprovalViewModel - takes userId, email, password as parameters
        factory { params ->
            PendingApprovalViewModel(
                authRepository = get(),
                authSession = get(),
                registrationStatusStream = get(),
                userId = params.get<String>(0),
                email = params.get<String>(1),
                password = params.get<String>(2),
            )
        }
        // InviteRegistrationViewModel - takes serverUrl and inviteCode as parameters
        factory { params ->
            InviteRegistrationViewModel(
                inviteRepository = get(),
                serverConfig = get(),
                authSession = get(),
                userRepository = get(),
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
        factory {
            AdminViewModel(
                instanceRepository = get(),
                loadUsersUseCase = get(),
                loadPendingUsersUseCase = get(),
                loadInvitesUseCase = get(),
                deleteUserUseCase = get(),
                revokeInviteUseCase = get(),
                approveUserUseCase = get(),
                denyUserUseCase = get(),
                setOpenRegistrationUseCase = get(),
                eventStreamRepository = get(),
            )
        }
        factory { CreateInviteViewModel(createInviteUseCase = get()) }
        factory {
            com.calypsan.listenup.client.presentation.admin.AdminSettingsViewModel(
                loadServerSettingsUseCase = get(),
                updateServerSettingsUseCase = get(),
            )
        }
        factory {
            com.calypsan.listenup.client.presentation.admin.AdminInboxViewModel(
                loadInboxBooksUseCase = get(),
                releaseBooksUseCase = get(),
                stageCollectionUseCase = get(),
                unstageCollectionUseCase = get(),
                eventStreamRepository = get(),
            )
        }
        factory {
            com.calypsan.listenup.client.presentation.admin.AdminCollectionsViewModel(
                collectionRepository = get(),
                createCollectionUseCase = get(),
                deleteCollectionUseCase = get(),
            )
        }
        // AdminCollectionDetailViewModel - takes collectionId as parameter
        factory { params ->
            com.calypsan.listenup.client.presentation.admin.AdminCollectionDetailViewModel(
                collectionId = params.get<String>(0),
                collectionRepository = get(),
                loadCollectionBooksUseCase = get(),
                loadCollectionSharesUseCase = get(),
                updateCollectionNameUseCase = get(),
                removeBookFromCollectionUseCase = get(),
                shareCollectionUseCase = get(),
                removeCollectionShareUseCase = get(),
                getUsersForSharingUseCase = get(),
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
                seriesRepository = get(),
                contributorRepository = get(),
                playbackPositionRepository = get(),
                syncRepository = get(),
                authSession = get(),
                libraryPreferences = get(),
                syncStatusRepository = get(),
                selectionManager = get(),
            )
        }

        // LibraryActionsViewModel as singleton - shares selection state with LibraryViewModel
        single {
            LibraryActionsViewModel(
                selectionManager = get(),
                userRepository = get(),
                collectionRepository = get(),
                lensRepository = get(),
                addBooksToCollectionUseCase = get(),
                refreshCollectionsUseCase = get(),
                addBooksToLensUseCase = get(),
                createLensUseCase = get(),
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
                genreRepository = get(),
                tagRepository = get(),
                playbackPositionRepository = get(),
                userRepository = get(),
            )
        }
        factory {
            com.calypsan.listenup.client.presentation.bookdetail.BookReadersViewModel(
                sessionRepository = get(),
                eventStreamRepository = get(),
            )
        }
        factory {
            com.calypsan.listenup.client.presentation.bookedit.BookEditViewModel(
                loadBookForEditUseCase = get(),
                updateBookUseCase = get(),
                contributorRepository = get(),
                seriesRepository = get(),
                imageRepository = get(),
            )
        }
        // MetadataViewModel for Audible metadata search and matching
        factory {
            com.calypsan.listenup.client.presentation.metadata.MetadataViewModel(
                metadataRepository = get(),
                applyMetadataMatchUseCase = get(),
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
                seriesRepository = get<com.calypsan.listenup.client.domain.repository.SeriesRepository>(),
                imageRepository = get(),
            )
        }
        factory {
            com.calypsan.listenup.client.presentation.seriesedit.SeriesEditViewModel(
                seriesRepository = get<com.calypsan.listenup.client.domain.repository.SeriesRepository>(),
                updateSeriesUseCase = get(),
                imageRepository = get(),
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
                contributorRepository = get<com.calypsan.listenup.client.domain.repository.ContributorRepository>(),
                playbackPositionRepository = get(),
                deleteContributorUseCase = get(),
            )
        }
        factory {
            com.calypsan.listenup.client.presentation.contributordetail.ContributorBooksViewModel(
                contributorRepository = get<com.calypsan.listenup.client.domain.repository.ContributorRepository>(),
                playbackPositionRepository = get(),
            )
        }
        factory {
            com.calypsan.listenup.client.presentation.contributoredit.ContributorEditViewModel(
                contributorRepository = get<com.calypsan.listenup.client.domain.repository.ContributorRepository>(),
                contributorEditRepository = get(),
                updateContributorUseCase = get(),
                imageRepository = get(),
            )
        }
        factory {
            com.calypsan.listenup.client.presentation.contributormetadata.ContributorMetadataViewModel(
                contributorRepository = get<com.calypsan.listenup.client.domain.repository.ContributorRepository>(),
                metadataRepository = get(),
                applyContributorMetadataUseCase = get(),
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
                userRepository = get(),
                lensRepository = get(),
            )
        }
        // HomeStatsViewModel for home screen stats section (observes local stats)
        factory { HomeStatsViewModel(statsRepository = get()) }
        factory {
            com.calypsan.listenup.client.presentation.discover.DiscoverViewModel(
                bookRepository = get(),
                activeSessionRepository = get(),
                authSession = get(),
                lensRepository = get(),
            )
        }
        // LeaderboardViewModel for discover screen leaderboard
        factory { LeaderboardViewModel(leaderboardRepository = get()) }
        // ActivityFeedViewModel for discover screen activity feed
        factory { ActivityFeedViewModel(activityRepository = get(), fetchActivitiesUseCase = get()) }
    }

/**
 * Tag and lens ViewModels.
 */
val tagLensPresentationModule =
    module {
        factory {
            com.calypsan.listenup.client.presentation.tagdetail.TagDetailViewModel(
                tagRepository = get(),
                bookRepository = get(),
            )
        }
        factory {
            com.calypsan.listenup.client.presentation.lens.LensDetailViewModel(
                loadLensDetailUseCase = get(),
                removeBookFromLensUseCase = get(),
                userRepository = get(),
            )
        }
        factory {
            com.calypsan.listenup.client.presentation.lens.CreateEditLensViewModel(
                createLensUseCase = get(),
                updateLensUseCase = get(),
                deleteLensUseCase = get(),
                lensRepository = get(),
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
                loadUserProfileUseCase = get(),
                userRepository = get(),
                imageRepository = get(),
            )
        }
        // EditProfileViewModel for editing own profile
        factory {
            com.calypsan.listenup.client.presentation.profile.EditProfileViewModel(
                profileEditRepository = get(),
                userRepository = get(),
                imageRepository = get(),
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
                libraryPreferences = get(),
                playbackPreferences = get(),
                localPreferences = get(),
                userPreferencesRepository = get(),
                instanceRepository = get(),
                serverConfig = get(),
                authSession = get(),
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
