package com.calypsan.listenup.client.features.shell.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.calypsan.listenup.client.design.components.UserAvatar
import com.calypsan.listenup.client.domain.model.User
import com.calypsan.listenup.client.features.shell.ShellDestination

/**
 * Navigation rail for medium-sized screens (tablets in portrait).
 *
 * Displays navigation destinations vertically with the user avatar
 * positioned at the bottom of the rail.
 *
 * @param currentDestination The currently selected destination
 * @param onDestinationSelected Callback when a destination is selected
 * @param user Current user entity for avatar display
 * @param isAvatarMenuExpanded Whether avatar dropdown is expanded
 * @param onAvatarMenuExpandedChange Callback when avatar menu expand state changes
 * @param onAdminClick Callback when administration is clicked (only shown for admin users)
 * @param onSettingsClick Callback when settings is clicked
 * @param onSignOutClick Callback when sign out is clicked
 * @param modifier Optional modifier
 */
@Composable
fun AppNavigationRail(
    currentDestination: ShellDestination?,
    onDestinationSelected: (ShellDestination) -> Unit,
    user: User?,
    isAvatarMenuExpanded: Boolean,
    onAvatarMenuExpandedChange: (Boolean) -> Unit,
    onAdminClick: (() -> Unit)? = null,
    onSettingsClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onMyProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val safeDestination = currentDestination ?: ShellDestination.Home

    NavigationRail(
        modifier = modifier.fillMaxHeight(),
    ) {
        // Navigation destinations
        ShellDestination.entries.forEach { destination ->
            val selected = safeDestination == destination

            NavigationRailItem(
                selected = selected,
                onClick = { onDestinationSelected(destination) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.icon,
                        contentDescription = destination.title,
                    )
                },
                label = { Text(destination.title) },
            )
        }

        // Push avatar to bottom
        Spacer(modifier = Modifier.weight(1f))

        // User avatar at bottom
        UserAvatar(
            user = user,
            expanded = isAvatarMenuExpanded,
            onExpandedChange = onAvatarMenuExpandedChange,
            onMyProfileClick = onMyProfileClick,
            onAdminClick = onAdminClick,
            onSettingsClick = onSettingsClick,
            onSignOutClick = onSignOutClick,
            modifier = Modifier.padding(bottom = 16.dp),
        )
    }
}
