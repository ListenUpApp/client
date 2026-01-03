@file:Suppress("MagicNumber")

package com.calypsan.listenup.client.design.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.calypsan.listenup.client.data.local.db.UserEntity
import com.calypsan.listenup.client.data.local.images.ImageStorage
import com.calypsan.listenup.client.data.repository.SettingsRepository
import org.koin.compose.koinInject
import java.io.File

/**
 * Circular avatar displaying user initials with a dropdown menu.
 *
 * The avatar color is deterministically generated from the user ID,
 * ensuring consistent colors across sessions.
 *
 * Reusable across the app wherever user representation is needed.
 *
 * @param user The current user entity (null shows placeholder)
 * @param expanded Whether the dropdown menu is expanded
 * @param onExpandedChange Callback when expanded state changes
 * @param onMyProfileClick Callback when My Profile is clicked
 * @param onAdminClick Callback when Administration is clicked (only shown for admin users)
 * @param onSettingsClick Callback when Settings is clicked
 * @param onSignOutClick Callback when Sign out is clicked
 * @param modifier Optional modifier
 */
@Composable
fun UserAvatar(
    user: UserEntity?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onMyProfileClick: () -> Unit,
    onAdminClick: (() -> Unit)? = null,
    onSettingsClick: () -> Unit,
    onSignOutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val settingsRepository: SettingsRepository = koinInject()
    val imageStorage: ImageStorage = koinInject()
    val serverUrl by produceState<String?>(null) {
        value = settingsRepository.getServerUrl()?.value
    }

    val hasImageAvatar = user?.avatarType == "image" && !user.avatarValue.isNullOrEmpty()

    Box(modifier = modifier) {
        // Clickable avatar circle
        Surface(
            onClick = { onExpandedChange(true) },
            shape = CircleShape,
            color =
                if (hasImageAvatar) {
                    Color.Transparent
                } else {
                    user?.let { avatarColorForUser(it.id) }
                        ?: MaterialTheme.colorScheme.surfaceContainerHighest
                },
            modifier = Modifier.size(40.dp),
        ) {
            if (hasImageAvatar && user != null) {
                // Offline-first: prefer local cached avatar
                val localPath =
                    if (imageStorage.userAvatarExists(user.id)) {
                        imageStorage.getUserAvatarPath(user.id)
                    } else {
                        null
                    }

                if (localPath != null) {
                    // Use local file with modification time as cache buster
                    val file = File(localPath)
                    val lastModified = file.lastModified()
                    AsyncImage(
                        model =
                            ImageRequest
                                .Builder(context)
                                .data(localPath)
                                .memoryCacheKey("${user.id}-avatar-$lastModified")
                                .diskCacheKey("${user.id}-avatar-$lastModified")
                                .build(),
                        contentDescription = "${user.displayName} avatar",
                        modifier =
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else if (serverUrl != null) {
                    // Fallback: fetch from server with disabled caching
                    val avatarUrl = "$serverUrl${user.avatarValue}"
                    AsyncImage(
                        model =
                            ImageRequest
                                .Builder(context)
                                .data(avatarUrl)
                                .memoryCachePolicy(CachePolicy.DISABLED)
                                .diskCachePolicy(CachePolicy.DISABLED)
                                .build(),
                        contentDescription = "${user.displayName} avatar",
                        modifier =
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    // No local file and no server URL - show initials
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = user.let { getInitials(it.displayName) },
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                        )
                    }
                }
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = user?.let { getInitials(it.displayName) } ?: "?",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                    )
                }
            }
        }

        // Dropdown menu
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            // Header with user info (non-clickable)
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                if (user != null) {
                    Text(
                        text = user.displayName,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = user.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    // Loading state - user data not yet available
                    Text(
                        text = "Loading...",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider()

            // My Profile menu item
            DropdownMenuItem(
                text = { Text("My Profile") },
                leadingIcon = { Icon(Icons.Outlined.AccountCircle, contentDescription = null) },
                onClick = {
                    onExpandedChange(false)
                    onMyProfileClick()
                },
            )

            // Administration menu item - only shown for admin users
            if (user?.isRoot == true && onAdminClick != null) {
                DropdownMenuItem(
                    text = { Text("Administration") },
                    leadingIcon = { Icon(Icons.Outlined.AdminPanelSettings, contentDescription = null) },
                    onClick = {
                        onExpandedChange(false)
                        onAdminClick()
                    },
                )
            }

            DropdownMenuItem(
                text = { Text("Settings") },
                leadingIcon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                onClick = {
                    onExpandedChange(false)
                    onSettingsClick()
                },
            )

            DropdownMenuItem(
                text = { Text("Sign out") },
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null) },
                onClick = {
                    onExpandedChange(false)
                    onSignOutClick()
                },
            )
        }
    }
}

/**
 * Generate a consistent avatar color based on user ID.
 *
 * Uses hue rotation to create visually distinct colors while
 * maintaining pleasant saturation and lightness values.
 *
 * @param userId The user's unique identifier
 * @return A Color for the avatar background
 */
fun avatarColorForUser(userId: String): Color {
    val hue = (userId.hashCode() and 0x7FFFFFFF) % 360
    return Color.hsl(hue.toFloat(), 0.4f, 0.65f)
}

/**
 * Extract initials from a display name.
 *
 * Logic:
 * - Two+ words: First letter of first two words (e.g., "John Doe" → "JD")
 * - One word with 2+ chars: First two letters (e.g., "Admin" → "AD")
 * - Single char: That character (e.g., "X" → "X")
 *
 * @param displayName The user's display name
 * @return Uppercase initials string
 */
fun getInitials(displayName: String): String {
    val parts = displayName.trim().split("\\s+".toRegex())
    return when {
        parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}"
        displayName.length >= 2 -> displayName.take(2)
        else -> displayName.take(1)
    }.uppercase()
}
