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
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.calypsan.listenup.client.data.sync.LibraryResetHelperContract
import com.calypsan.listenup.client.domain.repository.AuthSession
import com.calypsan.listenup.client.features.shell.AppShell
import com.calypsan.listenup.client.features.shell.ShellDestination
import com.calypsan.listenup.client.navigation.AuthNavigation
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private val logger = KotlinLogging.logger {}

/**
 * Root composable for the desktop application.
 *
 * Handles:
 * - Authentication flow via shared AuthNavigation
 * - Navigation to main app shell after authentication
 *
 * Uses shared AppShell for adaptive navigation (bottom nav, rail, drawer).
 * Content screens are placeholders until Phase 2 migrates them to commonMain.
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
 * Uses the shared AppShell with placeholder screen content.
 * Screen implementations will be shared in Phase 2.
 */
@Composable
private fun DesktopAuthenticatedNavigation() {
    val scope = rememberCoroutineScope()
    val authSession: AuthSession = koinInject()
    val libraryResetHelper: LibraryResetHelperContract = koinInject()

    var currentDestination by remember { mutableStateOf<ShellDestination>(ShellDestination.Home) }

    AppShell(
        currentDestination = currentDestination,
        onDestinationChange = { currentDestination = it },
        onBookClick = { bookId ->
            logger.info { "Book clicked: $bookId (detail screen coming in Phase 2)" }
        },
        onSeriesClick = { seriesId ->
            logger.info { "Series clicked: $seriesId (detail screen coming in Phase 2)" }
        },
        onContributorClick = { contributorId ->
            logger.info { "Contributor clicked: $contributorId (detail screen coming in Phase 2)" }
        },
        onLensClick = { lensId ->
            logger.info { "Lens clicked: $lensId (detail screen coming in Phase 2)" }
        },
        onTagClick = { tagId ->
            logger.info { "Tag clicked: $tagId (detail screen coming in Phase 2)" }
        },
        onAdminClick = {
            logger.info { "Admin clicked (admin screen coming in Phase 3)" }
        },
        onSettingsClick = {
            logger.info { "Settings clicked (settings screen coming in Phase 3)" }
        },
        onSignOut = {
            scope.launch {
                logger.info { "Signing out..." }
                libraryResetHelper.clearLibraryData()
                authSession.clearAuthTokens()
            }
        },
        onUserProfileClick = { userId ->
            logger.info { "User profile clicked: $userId (profile screen coming in Phase 3)" }
        },
        homeContent = { padding, _, _ ->
            PlaceholderScreen(
                title = "Home",
                description = "Your personal landing page with continue listening, up next, and stats.",
                icon = { Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.padding(bottom = 16.dp)) },
                padding = padding,
            )
        },
        libraryContent = { padding, _ ->
            PlaceholderScreen(
                title = "Library",
                description = "Browse your full audiobook collection. Coming in Phase 2.",
                icon = { Icon(Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = null, modifier = Modifier.padding(bottom = 16.dp)) },
                padding = padding,
            )
        },
        discoverContent = { padding ->
            PlaceholderScreen(
                title = "Discover",
                description = "Social features and recommendations. Coming in Phase 2.",
                icon = { Icon(Icons.Default.Explore, contentDescription = null, modifier = Modifier.padding(bottom = 16.dp)) },
                padding = padding,
            )
        },
        searchOverlayContent = { _ ->
            // Search overlay will be added in Phase 2
        },
    )
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
