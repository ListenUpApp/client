package com.calypsan.listenup.client.navigation

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.calypsan.listenup.client.data.remote.SetupApiContract
import com.calypsan.listenup.client.data.repository.DeepLinkManager
import com.calypsan.listenup.client.data.sync.LibraryResetHelperContract
import com.calypsan.listenup.client.domain.repository.SyncRepository
import com.calypsan.listenup.client.domain.repository.UserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import com.calypsan.listenup.client.design.components.FullScreenLoadingIndicator
import com.calypsan.listenup.client.design.components.LocalSnackbarHostState
import com.calypsan.listenup.client.domain.repository.AuthSession
import com.calypsan.listenup.client.domain.repository.AuthState
import com.calypsan.listenup.client.features.admin.AdminScreen
import com.calypsan.listenup.client.features.admin.CreateInviteScreen
import com.calypsan.listenup.client.features.admin.backup.AdminBackupScreen
import com.calypsan.listenup.client.features.admin.backup.ABSImportHubDetailScreen
import com.calypsan.listenup.client.features.admin.backup.ABSImportScreen
import com.calypsan.listenup.client.features.admin.backup.CreateBackupScreen
import com.calypsan.listenup.client.features.admin.backup.RestoreBackupScreen
import com.calypsan.listenup.client.features.connect.ServerSelectScreen
import com.calypsan.listenup.client.features.connect.ServerSetupScreen
import com.calypsan.listenup.client.features.invite.InviteRegistrationScreen
import com.calypsan.listenup.client.features.nowplaying.NowPlayingHost
import com.calypsan.listenup.client.features.settings.SettingsScreen
import com.calypsan.listenup.client.features.setup.LibrarySetupScreen
import com.calypsan.listenup.client.features.shell.AppShell
import com.calypsan.listenup.client.features.shell.ShellDestination
import com.calypsan.listenup.client.presentation.admin.AdminInboxViewModel
import com.calypsan.listenup.client.presentation.admin.AdminSettingsViewModel
import com.calypsan.listenup.client.presentation.admin.AdminViewModel
import com.calypsan.listenup.client.presentation.admin.CreateInviteViewModel
import com.calypsan.listenup.client.presentation.auth.PendingApprovalViewModel
import com.calypsan.listenup.client.presentation.invite.InviteRegistrationViewModel
import com.calypsan.listenup.client.presentation.invite.InviteSubmissionStatus
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private val logger = KotlinLogging.logger {}

/**
 * Root navigation composable for ListenUp Android app.
 *
 * Navigation priority:
 * 1. Pending invite deep link (shows invite registration)
 * 2. Auth state-driven routing (server setup → login → library)
 *
 * Navigation automatically adjusts when auth state changes.
 * Uses Navigation 3 stable for Android (will migrate to KMP when Desktop support needed).
 */
@Composable
fun ListenUpNavigation(
    authSession: AuthSession = koinInject(),
    deepLinkManager: DeepLinkManager = koinInject(),
) {
    // Initialize auth state on first composition
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        scope.launch {
            authSession.initializeAuthState()
        }
    }

    // Observe pending invite deep link
    val pendingInvite by deepLinkManager.pendingInvite.collectAsState()

    // Observe auth state changes
    val authState by authSession.authState.collectAsState()

    // Check for pending invite BEFORE auth state routing
    // This allows invite registration even when already authenticated
    pendingInvite?.let { invite ->
        InviteRegistrationNavigation(
            serverUrl = invite.serverUrl,
            inviteCode = invite.code,
            onComplete = { deepLinkManager.consumeInvite() },
            onCancel = { deepLinkManager.consumeInvite() },
        )
        return
    }

    // Route to appropriate screen based on auth state
    // Capture to local val to enable smart casting (delegated properties can't be smart cast)
    val currentAuthState = authState
    when (currentAuthState) {
        AuthState.Initializing -> {
            // Show blank screen while determining auth state
            // Prevents flash of wrong screen on startup
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
            )
        }

        AuthState.NeedsServerUrl -> {
            ServerSetupNavigation()
        }

        AuthState.CheckingServer -> {
            LoadingScreen("Checking server...")
        }

        AuthState.NeedsSetup -> {
            SetupNavigation()
        }

        is AuthState.NeedsLogin -> {
            LoginNavigation(authSession, currentAuthState.openRegistration)
        }

        is AuthState.PendingApproval -> {
            PendingApprovalNavigation(
                userId = currentAuthState.userId,
                email = currentAuthState.email,
                password = currentAuthState.encryptedPassword,
            )
        }

        is AuthState.Authenticated -> {
            AuthenticatedNavigation(authSession)
        }
    }
}

/**
 * Navigation for pending approval screen.
 *
 * Shows the pending approval screen for users who have registered
 * but are waiting for admin approval. Handles:
 * - SSE connection for real-time approval notification
 * - Auto-login on approval
 * - Cancel to return to login
 */
@Composable
private fun PendingApprovalNavigation(
    userId: String,
    email: String,
    password: String,
) {
    val viewModel: PendingApprovalViewModel =
        koinInject {
            org.koin.core.parameter
                .parametersOf(userId, email, password)
        }

    com.calypsan.listenup.client.features.auth.PendingApprovalScreen(
        viewModel = viewModel,
        onNavigateToLogin = {
            // Auth state will automatically update from clearPendingRegistration
            // No explicit navigation needed
        },
    )
}

/**
 * Navigation for invite registration flow.
 *
 * Shows the invite registration screen and handles completion/cancellation.
 * On successful registration, auth tokens are stored and AuthState becomes Authenticated,
 * which will trigger navigation to the library after the invite is consumed.
 */
@Composable
private fun InviteRegistrationNavigation(
    serverUrl: String,
    inviteCode: String,
    onComplete: () -> Unit,
    onCancel: () -> Unit,
) {
    val viewModel: InviteRegistrationViewModel =
        koinInject {
            org.koin.core.parameter
                .parametersOf(serverUrl, inviteCode)
        }

    // Watch for successful registration to trigger completion
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.submissionStatus) {
        if (state.submissionStatus is InviteSubmissionStatus.Success) {
            onComplete()
        }
    }

    InviteRegistrationScreen(
        viewModel = viewModel,
        onCancel = onCancel,
    )
}

/**
 * Loading screen shown during auth state initialization.
 * Displayed briefly on app start while checking for stored credentials.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
private fun LoadingScreen(message: String = "Loading...") {
    FullScreenLoadingIndicator()
}

/**
 * Server setup navigation - shown when no server URL is configured.
 *
 * Flow:
 * 1. ServerSelectScreen - shows discovered servers + manual option
 * 2. ServerSetupScreen - manual URL entry (if user clicks "Add Manually")
 *
 * After successful selection/verification, AuthState changes trigger automatic navigation.
 */
@Composable
private fun ServerSetupNavigation() {
    val backStack = remember { mutableStateListOf<Route>(ServerSelect) }

    NavDisplay(
        backStack = backStack,
        onBack = {
            if (backStack.size > 1) {
                backStack.removeAt(backStack.lastIndex)
            }
        },
        entryProvider =
            entryProvider {
                entry<ServerSelect> {
                    ServerSelectScreen(
                        onServerActivated = {
                            // Server is selected, AuthState will change automatically
                        },
                        onManualEntryRequested = {
                            backStack.add(ServerSetup)
                        },
                    )
                }
                entry<ServerSetup> {
                    ServerSetupScreen(
                        onServerVerified = {
                            // URL is saved, AuthState will change automatically
                            // Pop back to select (will be replaced by auth screen)
                        },
                        onBack = {
                            backStack.removeAt(backStack.lastIndex)
                        },
                    )
                }
            },
    )
}

/**
 * Setup navigation - shown when server needs initial root user.
 * After successful setup, AuthState.Authenticated triggers automatic navigation.
 */
@Composable
private fun SetupNavigation() {
    val backStack = remember { mutableStateListOf<Route>(Setup) }

    NavDisplay(
        backStack = backStack,
        entryProvider =
            entryProvider {
                entry<Setup> {
                    com.calypsan.listenup.client.features.auth
                        .SetupScreen()
                }
            },
    )
}

/**
 * Login navigation - shown when server is configured but user needs to authenticate.
 * After successful login, AuthState.Authenticated triggers automatic navigation.
 *
 * @param openRegistration Whether the server allows public registration
 */
@Composable
private fun LoginNavigation(
    authSession: AuthSession,
    openRegistration: Boolean,
) {
    val scope = rememberCoroutineScope()
    val backStack = remember { mutableStateListOf<Route>(Login) }
    val serverConfig: com.calypsan.listenup.client.domain.repository.ServerConfig = koinInject()

    // Refresh open registration value from server
    // This ensures the "Create Account" link appears if admin enabled it
    LaunchedEffect(Unit) {
        authSession.refreshOpenRegistration()
    }

    NavDisplay(
        backStack = backStack,
        entryProvider =
            entryProvider {
                entry<Login> {
                    com.calypsan.listenup.client.features.auth
                        .LoginScreen(
                            openRegistration = openRegistration,
                            onChangeServer = {
                                scope.launch {
                                    // Clear server URL to go back to server selection
                                    serverConfig.disconnectFromServer()
                                }
                            },
                            onRegister = {
                                backStack.add(Register)
                            },
                        )
                }
                entry<Register> {
                    com.calypsan.listenup.client.features.auth
                        .RegisterScreen(
                            onBackClick = {
                                backStack.removeAt(backStack.lastIndex)
                            },
                        )
                }
            },
    )
}

/**
 * Navigation graph for authenticated users.
 *
 * Entry point: AppShell (contains bottom nav with Home, Library, Discover)
 *
 * Predictive back behavior:
 * - Root screen (Shell): onBack doesn't pop, allowing system back-to-home animation
 * - Detail screens: Slide animations for in-app navigation
 *
 * NowPlayingHost is overlaid on top of all navigation, providing:
 * - Floating mini player above bottom nav
 * - Full screen player that expands from mini player
 *
 * When user logs out, SettingsRepository clears auth tokens,
 * triggering automatic switch to UnauthenticatedNavigation.
 */
@Suppress("LongMethod")
@Composable
private fun AuthenticatedNavigation(
    authSession: AuthSession,
    libraryResetHelper: LibraryResetHelperContract = koinInject(),
    setupApi: SetupApiContract = koinInject(),
    syncRepository: SyncRepository = koinInject(),
    userRepository: UserRepository = koinInject(),
) {
    val scope = rememberCoroutineScope()

    // Library setup check state
    var isCheckingLibrarySetup by remember { mutableStateOf(true) }
    var needsLibrarySetup by remember { mutableStateOf(false) }

    // Check if admin user needs to set up library on initial composition
    LaunchedEffect(Unit) {
        try {
            // Refresh user data to ensure we have current admin status
            val user = userRepository.refreshCurrentUser() ?: userRepository.getCurrentUser()
            logger.debug { "Checking library setup: user=${user?.displayName}, isAdmin=${user?.isAdmin}" }

            if (user?.isAdmin == true) {
                // Admin user - check if library needs setup
                try {
                    val status = setupApi.getLibraryStatus()
                    logger.info { "Library status: needsSetup=${status.needsSetup}" }
                    needsLibrarySetup = status.needsSetup
                } catch (e: Exception) {
                    // Never stranded: if status check fails, proceed to main app
                    logger.warn(e) { "Failed to check library status, proceeding to main app" }
                    needsLibrarySetup = false
                }
            } else {
                // Non-admin user - skip library setup check
                needsLibrarySetup = false
            }
        } catch (e: Exception) {
            // Never stranded: if user check fails, proceed to main app
            logger.warn(e) { "Failed to check user, proceeding to main app" }
            needsLibrarySetup = false
        } finally {
            isCheckingLibrarySetup = false
        }
    }

    // Show loading while checking library setup status
    if (isCheckingLibrarySetup) {
        FullScreenLoadingIndicator()
        return
    }

    // Determine starting route based on library setup needs
    val startRoute: Route = if (needsLibrarySetup) LibrarySetup else Shell
    val backStack = remember(startRoute) { mutableStateListOf(startRoute) }

    // Track shell tab state here so it survives navigation to detail screens
    var currentShellDestination by remember { mutableStateOf<ShellDestination>(ShellDestination.Home) }

    // Track profile refresh - incremented when profile is updated to trigger refresh
    var profileRefreshKey by remember { mutableStateOf(0) }

    // App-wide snackbar state - provided to all screens via CompositionLocal
    val snackbarHostState = remember { SnackbarHostState() }

    // Wrap navigation with NowPlayingHost for persistent mini player
    CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
        Box(modifier = Modifier.fillMaxSize()) {
            NavDisplay(
                backStack = backStack,
                // Only handle back if we're not at root - let system handle back-to-home
                onBack = {
                    if (backStack.size > 1) {
                        backStack.removeAt(backStack.lastIndex)
                    }
                    // When size == 1, don't pop - allows system back-to-home animation
                },
                // Global slide transitions for all navigation
                transitionSpec = {
                    slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                },
                popTransitionSpec = {
                    slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
                },
                predictivePopTransitionSpec = {
                    slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
                },
                entryProvider =
                    entryProvider {
                        entry<Shell> {
                            AppShell(
                                currentDestination = currentShellDestination,
                                onDestinationChange = { currentShellDestination = it },
                                onBookClick = { bookId ->
                                    backStack.add(BookDetail(bookId))
                                },
                                onSeriesClick = { seriesId ->
                                    backStack.add(SeriesDetail(seriesId))
                                },
                                onContributorClick = { contributorId ->
                                    backStack.add(ContributorDetail(contributorId))
                                },
                                onLensClick = { lensId ->
                                    backStack.add(LensDetail(lensId))
                                },
                                onTagClick = { tagId ->
                                    backStack.add(TagDetail(tagId))
                                },
                                onAdminClick = {
                                    backStack.add(Admin)
                                },
                                onSettingsClick = {
                                    backStack.add(Settings)
                                },
                                onSignOut = {
                                    scope.launch {
                                        // Clear library data before signing out
                                        // This ensures next login (same or different user) gets fresh data
                                        libraryResetHelper.clearLibraryData()
                                        authSession.clearAuthTokens()
                                    }
                                },
                                onUserProfileClick = { userId ->
                                    backStack.add(UserProfile(userId))
                                },
                            )
                        }
                        entry<LibrarySetup> {
                            LibrarySetupScreen(
                                onSetupComplete = {
                                    // Trigger sync to pull newly scanned books
                                    scope.launch {
                                        logger.info { "Library setup complete, triggering sync" }
                                        syncRepository.sync()
                                    }
                                    // Navigate to main app, clearing library setup from back stack
                                    backStack.clear()
                                    backStack.add(Shell)
                                },
                            )
                        }
                        entry<UserProfile> { args ->
                            com.calypsan.listenup.client.features.profile.UserProfileScreen(
                                userId = args.userId,
                                onBack = {
                                    backStack.removeAt(backStack.lastIndex)
                                },
                                onEditClick = {
                                    backStack.add(EditProfile)
                                },
                                onBookClick = { bookId ->
                                    backStack.add(BookDetail(bookId))
                                },
                                onLensClick = { lensId ->
                                    backStack.add(LensDetail(lensId))
                                },
                                onCreateLensClick = {
                                    backStack.add(CreateLens)
                                },
                                refreshKey = profileRefreshKey,
                            )
                        }
                        entry<EditProfile> {
                            com.calypsan.listenup.client.features.profile.EditProfileScreen(
                                onBack = {
                                    backStack.removeAt(backStack.lastIndex)
                                },
                                onProfileUpdated = {
                                    profileRefreshKey++
                                },
                            )
                        }
                        entry<BookDetail> { args ->
                            com.calypsan.listenup.client.features.bookdetail.BookDetailScreen(
                                bookId = args.bookId,
                                onBackClick = {
                                    backStack.removeAt(backStack.lastIndex)
                                },
                                onEditClick = { bookId ->
                                    backStack.add(BookEdit(bookId))
                                },
                                onMetadataSearchClick = { bookId ->
                                    backStack.add(MetadataSearch(bookId))
                                },
                                onSeriesClick = { seriesId ->
                                    backStack.add(SeriesDetail(seriesId))
                                },
                                onContributorClick = { contributorId ->
                                    backStack.add(ContributorDetail(contributorId))
                                },
                                onTagClick = { tagId ->
                                    backStack.add(TagDetail(tagId))
                                },
                                onUserProfileClick = { userId ->
                                    backStack.add(UserProfile(userId))
                                },
                            )
                        }
                        entry<BookEdit> { args ->
                            com.calypsan.listenup.client.features.bookedit.BookEditScreen(
                                bookId = args.bookId,
                                onBackClick = {
                                    backStack.removeAt(backStack.lastIndex)
                                },
                                onSaveSuccess = {
                                    // Navigate back after successful save
                                    backStack.removeAt(backStack.lastIndex)
                                },
                            )
                        }
                        entry<MetadataSearch> { args ->
                            com.calypsan.listenup.client.features.metadata.MetadataSearchRoute(
                                bookId = args.bookId,
                                onResultSelected = { asin ->
                                    backStack.add(MatchPreview(args.bookId, asin))
                                },
                                onBack = {
                                    backStack.removeAt(backStack.lastIndex)
                                },
                            )
                        }
                        entry<MatchPreview> { args ->
                            com.calypsan.listenup.client.features.metadata.MatchPreviewRoute(
                                bookId = args.bookId,
                                asin = args.asin,
                                onBack = {
                                    backStack.removeAt(backStack.lastIndex)
                                },
                                onApplySuccess = {
                                    // Navigate back to book detail after successful apply
                                    // Pop both MatchPreview and MetadataSearch
                                    backStack.removeAt(backStack.lastIndex)
                                    if (backStack.lastOrNull() is MetadataSearch) {
                                        backStack.removeAt(backStack.lastIndex)
                                    }
                                },
                            )
                        }
                        entry<SeriesDetail> { args ->
                            com.calypsan.listenup.client.features.seriesdetail.SeriesDetailScreen(
                                seriesId = args.seriesId,
                                onBackClick = {
                                    backStack.removeAt(backStack.lastIndex)
                                },
                                onBookClick = { bookId ->
                                    backStack.add(BookDetail(bookId))
                                },
                                onEditClick = { seriesId ->
                                    backStack.add(SeriesEdit(seriesId))
                                },
                            )
                        }
                        entry<TagDetail> { args ->
                            com.calypsan.listenup.client.features.tagdetail.TagDetailScreen(
                                tagId = args.tagId,
                                onBackClick = {
                                    backStack.removeAt(backStack.lastIndex)
                                },
                                onBookClick = { bookId ->
                                    backStack.add(BookDetail(bookId))
                                },
                            )
                        }
                        entry<SeriesEdit> { args ->
                            com.calypsan.listenup.client.features.seriesedit.SeriesEditScreen(
                                seriesId = args.seriesId,
                                onBackClick = {
                                    backStack.removeAt(backStack.lastIndex)
                                },
                                onSaveSuccess = {
                                    // Navigate back after successful save
                                    backStack.removeAt(backStack.lastIndex)
                                },
                            )
                        }
                        entry<ContributorDetail> { args ->
                            com.calypsan.listenup.client.features.contributordetail.ContributorDetailScreen(
                                contributorId = args.contributorId,
                                onBackClick = {
                                    backStack.removeAt(backStack.lastIndex)
                                },
                                onBookClick = { bookId ->
                                    backStack.add(BookDetail(bookId))
                                },
                                onEditClick = { contributorId ->
                                    backStack.add(ContributorEdit(contributorId))
                                },
                                onViewAllClick = { contributorId, role ->
                                    backStack.add(ContributorBooks(contributorId, role))
                                },
                                onMetadataClick = { contributorId ->
                                    backStack.add(ContributorMetadataSearch(contributorId))
                                },
                            )
                        }
                        entry<ContributorEdit> { args ->
                            com.calypsan.listenup.client.features.contributoredit.ContributorEditScreen(
                                contributorId = args.contributorId,
                                onBackClick = {
                                    backStack.removeAt(backStack.lastIndex)
                                },
                                onSaveSuccess = {
                                    // Navigate back after successful save
                                    backStack.removeAt(backStack.lastIndex)
                                },
                            )
                        }
                        entry<ContributorBooks> { args ->
                            com.calypsan.listenup.client.features.contributordetail.ContributorBooksScreen(
                                contributorId = args.contributorId,
                                role = args.role,
                                onBackClick = {
                                    backStack.removeAt(backStack.lastIndex)
                                },
                                onBookClick = { bookId ->
                                    backStack.add(BookDetail(bookId))
                                },
                            )
                        }
                        entry<ContributorMetadataSearch> { args ->
                            com.calypsan.listenup.client.features.contributormetadata.ContributorMetadataSearchRoute(
                                contributorId = args.contributorId,
                                onCandidateSelected = { asin ->
                                    backStack.add(ContributorMetadataPreview(args.contributorId, asin))
                                },
                                onBack = {
                                    backStack.removeAt(backStack.lastIndex)
                                },
                            )
                        }
                        entry<ContributorMetadataPreview> { args ->
                            com.calypsan.listenup.client.features.contributormetadata.ContributorMetadataPreviewRoute(
                                contributorId = args.contributorId,
                                asin = args.asin,
                                onApplySuccess = {
                                    // Pop both preview and search to go back to contributor detail
                                    backStack.removeAt(backStack.lastIndex)
                                    backStack.removeAt(backStack.lastIndex)
                                },
                                onChangeMatch = {
                                    // Pop preview to go back to search
                                    backStack.removeAt(backStack.lastIndex)
                                },
                                onBack = {
                                    backStack.removeAt(backStack.lastIndex)
                                },
                            )
                        }
                        // Admin screens
                        entry<Admin> {
                            val viewModel: AdminViewModel = koinInject()
                            val settingsViewModel: AdminSettingsViewModel = koinInject()
                            val settingsState by settingsViewModel.state.collectAsState()

                            AdminScreen(
                                viewModel = viewModel,
                                onBackClick = {
                                    backStack.removeAt(backStack.lastIndex)
                                },
                                onInviteClick = {
                                    backStack.add(CreateInvite)
                                },
                                onCollectionsClick = {
                                    backStack.add(AdminCollections)
                                },
                                onInboxClick = {
                                    backStack.add(AdminInbox)
                                },
                                onBackupClick = {
                                    backStack.add(AdminBackups)
                                },
                                onUserClick = { userId ->
                                    backStack.add(AdminUserDetail(userId))
                                },
                                inboxEnabled = settingsState.inboxEnabled,
                                inboxCount = settingsState.inboxCount,
                                isTogglingInbox = settingsState.isSaving,
                                onInboxEnabledChange = { settingsViewModel.setInboxEnabled(it) },
                            )

                            // Handle disable inbox confirmation dialog
                            if (settingsState.showDisableConfirmation) {
                                com.calypsan.listenup.client.design.components.ListenUpDestructiveDialog(
                                    onDismissRequest = { settingsViewModel.cancelDisableInbox() },
                                    title = "Disable Inbox Workflow",
                                    text =
                                        "This will release all ${settingsState.inboxCount} " +
                                            "book${if (settingsState.inboxCount != 1) "s" else ""} " +
                                            "currently in the inbox with their staged collection assignments.\n\n" +
                                            "New books will become immediately visible to users.",
                                    confirmText = "Disable & Release",
                                    onConfirm = { settingsViewModel.confirmDisableInbox() },
                                    onDismiss = { settingsViewModel.cancelDisableInbox() },
                                )
                            }
                        }
                        entry<AdminInbox> {
                            val viewModel: AdminInboxViewModel = koinInject()
                            com.calypsan.listenup.client.features.admin.inbox.AdminInboxScreen(
                                viewModel = viewModel,
                                onBackClick = {
                                    backStack.removeAt(backStack.lastIndex)
                                },
                                onBookClick = { bookId ->
                                    backStack.add(BookDetail(bookId))
                                },
                            )
                        }
                        entry<CreateInvite> {
                            val viewModel: CreateInviteViewModel = koinInject()
                            CreateInviteScreen(
                                viewModel = viewModel,
                                onBackClick = {
                                    backStack.removeAt(backStack.lastIndex)
                                },
                                onSuccess = {
                                    backStack.removeAt(backStack.lastIndex)
                                },
                            )
                        }
                        entry<AdminCollections> {
                            val viewModel: com.calypsan.listenup.client.presentation.admin.AdminCollectionsViewModel =
                                koinInject()
                            com.calypsan.listenup.client.features.admin.collections.AdminCollectionsScreen(
                                viewModel = viewModel,
                                onBackClick = {
                                    backStack.removeAt(backStack.lastIndex)
                                },
                                onCollectionClick = { collectionId ->
                                    backStack.add(AdminCollectionDetail(collectionId))
                                },
                            )
                        }
                        entry<AdminCollectionDetail> { args ->
                            val viewModel:
                                com.calypsan.listenup.client.presentation.admin.AdminCollectionDetailViewModel =
                                koinInject {
                                    org.koin.core.parameter
                                        .parametersOf(args.collectionId)
                                }
                            com.calypsan.listenup.client.features.admin.collections.AdminCollectionDetailScreen(
                                viewModel = viewModel,
                                onBackClick = {
                                    backStack.removeAt(backStack.lastIndex)
                                },
                            )
                        }
                        entry<AdminUserDetail> { args ->
                            val viewModel:
                                com.calypsan.listenup.client.presentation.admin.UserDetailViewModel =
                                koinInject {
                                    org.koin.core.parameter
                                        .parametersOf(args.userId)
                                }
                            com.calypsan.listenup.client.features.admin.UserDetailScreen(
                                viewModel = viewModel,
                                onBackClick = {
                                    backStack.removeAt(backStack.lastIndex)
                                },
                            )
                        }
                        entry<AdminLibrarySettings> { args ->
                            val viewModel:
                                com.calypsan.listenup.client.presentation.admin.LibrarySettingsViewModel =
                                koinInject {
                                    org.koin.core.parameter
                                        .parametersOf(args.libraryId)
                                }
                            com.calypsan.listenup.client.features.admin.LibrarySettingsScreen(
                                viewModel = viewModel,
                                onBackClick = {
                                    backStack.removeAt(backStack.lastIndex)
                                },
                            )
                        }
                        entry<AdminBackups> {
                            AdminBackupScreen(
                                onBackClick = {
                                    backStack.removeAt(backStack.lastIndex)
                                },
                                onCreateClick = {
                                    backStack.add(CreateBackup)
                                },
                                onRestoreClick = { backupId ->
                                    backStack.add(RestoreBackup(backupId))
                                },
                                onABSImportHubClick = { importId ->
                                    backStack.add(ABSImportDetail(importId))
                                },
                            )
                        }
                        entry<CreateBackup> {
                            CreateBackupScreen(
                                onBackClick = { backStack.removeAt(backStack.lastIndex) },
                                onSuccess = {
                                    // Navigate back to backup list after successful creation
                                    backStack.removeAt(backStack.lastIndex)
                                },
                            )
                        }
                        entry<RestoreBackup> { args ->
                            RestoreBackupScreen(
                                backupId = args.backupId,
                                onBackClick = { backStack.removeAt(backStack.lastIndex) },
                                onComplete = {
                                    // Navigate back to backup list after restore
                                    backStack.removeAt(backStack.lastIndex)
                                },
                            )
                        }
                        // ABSImportList removed - imports are now shown inline in AdminBackupScreen
                        entry<ABSImportDetail> { args ->
                            ABSImportHubDetailScreen(
                                importId = args.importId,
                                onBackClick = { backStack.removeAt(backStack.lastIndex) },
                            )
                        }
                        entry<ABSImport> {
                            ABSImportScreen(
                                onBackClick = { backStack.removeAt(backStack.lastIndex) },
                                onComplete = {
                                    // Navigate back to backup list after import
                                    backStack.removeAt(backStack.lastIndex)
                                },
                            )
                        }
                        entry<Settings> {
                            SettingsScreen(
                                onNavigateBack = {
                                    backStack.removeAt(backStack.lastIndex)
                                },
                                onNavigateToLicenses = {
                                    backStack.add(Licenses)
                                },
                            )
                        }
                        entry<LensDetail> { args ->
                            com.calypsan.listenup.client.features.lens.LensDetailScreen(
                                lensId = args.lensId,
                                onBack = {
                                    backStack.removeAt(backStack.lastIndex)
                                },
                                onBookClick = { bookId ->
                                    backStack.add(BookDetail(bookId))
                                },
                                onEditClick = { lensId ->
                                    backStack.add(LensEdit(lensId))
                                },
                            )
                        }
                        entry<CreateLens> {
                            com.calypsan.listenup.client.features.lens.CreateEditLensScreen(
                                lensId = null,
                                onBack = {
                                    backStack.removeAt(backStack.lastIndex)
                                },
                            )
                        }
                        entry<LensEdit> { args ->
                            com.calypsan.listenup.client.features.lens.CreateEditLensScreen(
                                lensId = args.lensId,
                                onBack = {
                                    backStack.removeAt(backStack.lastIndex)
                                },
                            )
                        }
                        entry<Licenses> {
                            com.calypsan.listenup.client.features.settings.LicensesScreen(
                                onNavigateBack = {
                                    backStack.removeAt(backStack.lastIndex)
                                },
                            )
                        }
                    },
            )

            // Now Playing overlay - persistent across all navigation
            // Position adjusts based on whether bottom nav is visible (Shell vs detail screens)
            // Also animates up when snackbar is visible
            NowPlayingHost(
                hasBottomNav = backStack.lastOrNull() == Shell,
                snackbarHostState = snackbarHostState,
                onNavigateToBook = { bookId ->
                    backStack.add(BookDetail(bookId))
                },
                onNavigateToSeries = { seriesId ->
                    backStack.add(SeriesDetail(seriesId))
                },
                onNavigateToContributor = { contributorId ->
                    backStack.add(ContributorDetail(contributorId))
                },
            )

            // App-wide snackbar - positioned at bottom, mini player animates up when visible
            SnackbarHost(
                hostState = snackbarHostState,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp),
            )
        }
    }
}

/**
 * Placeholder screen for features that are coming soon.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComingSoonScreen(
    title: String,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = null,
                    modifier = Modifier.padding(bottom = 16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Coming Soon",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "This feature is under development",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
