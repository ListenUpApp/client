package com.calypsan.listenup.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.calypsan.listenup.client.data.sync.LibraryResetHelperContract
import com.calypsan.listenup.client.design.components.LocalSnackbarHostState
import com.calypsan.listenup.client.domain.repository.AuthSession
import com.calypsan.listenup.client.features.bookdetail.BookDetailScreen
import com.calypsan.listenup.client.features.contributordetail.ContributorDetailScreen
import com.calypsan.listenup.client.features.lens.LensDetailScreen
import com.calypsan.listenup.client.features.library.LibraryScreen
import com.calypsan.listenup.client.features.seriesdetail.SeriesDetailScreen
import com.calypsan.listenup.client.features.shell.AppShell
import com.calypsan.listenup.client.features.shell.ShellDestination
import com.calypsan.listenup.client.features.tagdetail.TagDetailScreen
import com.calypsan.listenup.client.navigation.AuthNavigation
import com.calypsan.listenup.client.playback.DesktopPlayerViewModel
import com.calypsan.listenup.desktop.nowplaying.DesktopNowPlayingBar
import com.calypsan.listenup.desktop.nowplaying.DesktopNowPlayingScreen
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private val logger = KotlinLogging.logger {}

/**
 * Detail screen destinations for the desktop back stack.
 */
sealed interface DetailDestination {
    data class Book(val bookId: String) : DetailDestination
    data class Series(val seriesId: String) : DetailDestination
    data class Contributor(val contributorId: String) : DetailDestination
    data class Lens(val lensId: String) : DetailDestination
    data class Tag(val tagId: String) : DetailDestination
    data object NowPlaying : DetailDestination
}

/**
 * Root composable for the desktop application.
 *
 * Handles:
 * - Authentication flow via shared AuthNavigation
 * - Navigation to main app shell after authentication
 * - Detail screen navigation via back stack overlay
 */
@Composable
fun DesktopApp() {
    var isAuthenticated by remember { mutableStateOf(false) }

    if (!isAuthenticated) {
        AuthNavigation(
            onAuthenticated = {
                isAuthenticated = true
            },
        )
    } else {
        DesktopAuthenticatedNavigation()
    }
}

/**
 * Navigation for authenticated desktop users.
 *
 * Uses a back stack to overlay detail screens on top of the shell.
 * When the back stack is empty, the shell is shown. When non-empty,
 * the top destination is rendered as the current screen.
 */
@Composable
private fun DesktopAuthenticatedNavigation() {
    val scope = rememberCoroutineScope()
    val authSession: AuthSession = koinInject()
    val libraryResetHelper: LibraryResetHelperContract = koinInject()
    val playerViewModel: DesktopPlayerViewModel = koinInject()
    val snackbarHostState = remember { SnackbarHostState() }

    val playerState by playerViewModel.state.collectAsState()

    var currentDestination by remember { mutableStateOf<ShellDestination>(ShellDestination.Home) }
    val backStack: SnapshotStateList<DetailDestination> = remember { emptyList<DetailDestination>().toMutableStateList() }

    val navigateTo: (DetailDestination) -> Unit = { backStack.add(it) }
    val navigateBack: () -> Unit = { backStack.removeLastOrNull() }

    val isShowingNowPlaying = backStack.lastOrNull() is DetailDestination.NowPlaying

    CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                if (backStack.isNotEmpty()) {
                    DetailScreen(
                        destination = backStack.last(),
                        navigateTo = navigateTo,
                        navigateBack = navigateBack,
                        playerViewModel = playerViewModel,
                    )
                } else {
                    AppShell(
                        currentDestination = currentDestination,
                        onDestinationChange = { currentDestination = it },
                        onBookClick = { navigateTo(DetailDestination.Book(it)) },
                        onSeriesClick = { navigateTo(DetailDestination.Series(it)) },
                        onContributorClick = { navigateTo(DetailDestination.Contributor(it)) },
                        onLensClick = { navigateTo(DetailDestination.Lens(it)) },
                        onTagClick = { navigateTo(DetailDestination.Tag(it)) },
                        onAdminClick = {
                            logger.info { "Admin clicked (admin screen not yet migrated)" }
                        },
                        onSettingsClick = {
                            logger.info { "Settings clicked (settings screen not yet migrated)" }
                        },
                        onSignOut = {
                            scope.launch {
                                logger.info { "Signing out..." }
                                libraryResetHelper.clearLibraryData()
                                authSession.clearAuthTokens()
                            }
                        },
                        onUserProfileClick = { userId ->
                            logger.info { "User profile clicked: $userId (profile screen not yet migrated)" }
                        },
                        homeContent = { padding, _, _ ->
                            PlaceholderScreen(
                                title = "Home",
                                description = "Your personal landing page with continue listening, up next, and stats.",
                                icon = { Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.padding(bottom = 16.dp)) },
                                padding = padding,
                            )
                        },
                        libraryContent = { padding, topBarCollapseFraction ->
                            LibraryScreen(
                                onBookClick = { navigateTo(DetailDestination.Book(it)) },
                                onSeriesClick = { navigateTo(DetailDestination.Series(it)) },
                                onAuthorClick = { navigateTo(DetailDestination.Contributor(it)) },
                                onNarratorClick = { navigateTo(DetailDestination.Contributor(it)) },
                                topBarCollapseFraction = topBarCollapseFraction,
                                modifier = Modifier.padding(padding),
                            )
                        },
                        discoverContent = { padding ->
                            PlaceholderScreen(
                                title = "Discover",
                                description = "Social features and recommendations.",
                                icon = { Icon(Icons.Default.Explore, contentDescription = null, modifier = Modifier.padding(bottom = 16.dp)) },
                                padding = padding,
                            )
                        },
                        searchOverlayContent = { _ ->
                            // Search overlay will be added later
                        },
                    )
                }
            }

            // Persistent mini player (visible when playing, hidden on NowPlaying screen)
            if (playerState.isVisible && !isShowingNowPlaying) {
                DesktopNowPlayingBar(
                    state = playerState,
                    onPlayPause = { playerViewModel.playPause() },
                    onSkipBack = { playerViewModel.skipBack() },
                    onSkipForward = { playerViewModel.skipForward() },
                    onClick = { navigateTo(DetailDestination.NowPlaying) },
                )
            }
        }
    }
}

/**
 * Renders the appropriate detail screen for the given destination.
 */
@Composable
private fun DetailScreen(
    destination: DetailDestination,
    navigateTo: (DetailDestination) -> Unit,
    navigateBack: () -> Unit,
    playerViewModel: DesktopPlayerViewModel,
) {
    when (destination) {
        is DetailDestination.Book -> BookDetailScreen(
            bookId = destination.bookId,
            onBackClick = navigateBack,
            onEditClick = { logger.info { "Book edit: $it (not yet migrated)" } },
            onMetadataSearchClick = { logger.info { "Metadata search: $it (not yet migrated)" } },
            onSeriesClick = { navigateTo(DetailDestination.Series(it)) },
            onContributorClick = { navigateTo(DetailDestination.Contributor(it)) },
            onTagClick = { navigateTo(DetailDestination.Tag(it)) },
            onUserProfileClick = { logger.info { "User profile: $it (not yet migrated)" } },
        )

        is DetailDestination.Series -> SeriesDetailScreen(
            seriesId = destination.seriesId,
            onBackClick = navigateBack,
            onBookClick = { navigateTo(DetailDestination.Book(it)) },
            onEditClick = { logger.info { "Series edit: $it (not yet migrated)" } },
        )

        is DetailDestination.Contributor -> ContributorDetailScreen(
            contributorId = destination.contributorId,
            onBackClick = navigateBack,
            onBookClick = { navigateTo(DetailDestination.Book(it)) },
            onEditClick = { logger.info { "Contributor edit: $it (not yet migrated)" } },
            onViewAllClick = { id, role -> logger.info { "Contributor books: $id/$role (not yet migrated)" } },
            onMetadataClick = { logger.info { "Contributor metadata: $it (not yet migrated)" } },
        )

        is DetailDestination.Lens -> LensDetailScreen(
            lensId = destination.lensId,
            onBack = navigateBack,
            onBookClick = { navigateTo(DetailDestination.Book(it)) },
            onEditClick = { logger.info { "Lens edit: $it (not yet migrated)" } },
        )

        is DetailDestination.Tag -> TagDetailScreen(
            tagId = destination.tagId,
            onBackClick = navigateBack,
            onBookClick = { navigateTo(DetailDestination.Book(it)) },
        )

        is DetailDestination.NowPlaying -> {
            val state by playerViewModel.state.collectAsState()
            DesktopNowPlayingScreen(
                state = state,
                onPlayPause = { playerViewModel.playPause() },
                onSkipBack = { playerViewModel.skipBack() },
                onSkipForward = { playerViewModel.skipForward() },
                onPreviousChapter = { playerViewModel.previousChapter() },
                onNextChapter = { playerViewModel.nextChapter() },
                onSeekWithinChapter = { progress -> playerViewModel.seekWithinChapter(progress) },
                onSetSpeed = { speed -> playerViewModel.setSpeed(speed) },
                onClose = {
                    playerViewModel.closeBook()
                    navigateBack()
                },
                onBackClick = navigateBack,
            )
        }
    }
}

/**
 * Placeholder screen for destinations not yet implemented.
 */
@Composable
private fun PlaceholderScreen(
    title: String,
    description: String,
    icon: @Composable () -> Unit,
    padding: PaddingValues,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            icon()
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
