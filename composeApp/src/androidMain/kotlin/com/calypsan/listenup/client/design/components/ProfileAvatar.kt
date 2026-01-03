@file:Suppress("MagicNumber")

package com.calypsan.listenup.client.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.calypsan.listenup.client.data.local.db.UserProfileDao
import com.calypsan.listenup.client.data.local.images.ImageStorage
import com.calypsan.listenup.client.data.repository.SettingsRepository
import org.koin.compose.koinInject
import java.io.File
import android.graphics.Color as AndroidColor

/**
 * Simple avatar component for displaying other users' profiles.
 *
 * Offline-first approach:
 * 1. Looks up cached UserProfileEntity for full avatar data
 * 2. Prefers local avatar image if available
 * 3. Falls back to server URL if online
 * 4. Shows initials on colored background otherwise
 *
 * Use this component for:
 * - Activity feed items
 * - Lenses (showing owner)
 * - Book detail reader lists
 * - Any place showing another user
 *
 * For the current user's avatar with dropdown menu, use UserAvatar instead.
 *
 * @param userId The user's unique ID
 * @param displayName The user's display name (for initials)
 * @param avatarColor Fallback color in hex format (e.g., "#6B7280")
 * @param size Size of the avatar circle
 * @param fontSize Font size for initials
 * @param modifier Optional modifier
 */
@Composable
fun ProfileAvatar(
    userId: String,
    displayName: String,
    avatarColor: String,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    fontSize: TextUnit = 14.sp,
) {
    val context = LocalContext.current
    val userProfileDao: UserProfileDao = koinInject()
    val settingsRepository: SettingsRepository = koinInject()
    val imageStorage: ImageStorage = koinInject()

    // Look up cached profile for full avatar data (avatarType, avatarValue)
    val cachedProfile by produceState(initialValue = null as CachedProfileData?) {
        val profile = userProfileDao.getById(userId)
        value = profile?.let {
            CachedProfileData(
                avatarType = it.avatarType,
                avatarValue = it.avatarValue,
                avatarColor = it.avatarColor,
            )
        }
    }

    val serverUrl by produceState<String?>(null) {
        value = settingsRepository.getServerUrl()?.value
    }

    val effectiveAvatarType = cachedProfile?.avatarType ?: "auto"
    val effectiveAvatarValue = cachedProfile?.avatarValue
    val effectiveAvatarColor = cachedProfile?.avatarColor ?: avatarColor

    val color = remember(effectiveAvatarColor) {
        parseAvatarColor(effectiveAvatarColor)
    }
    val initials = remember(displayName) {
        getInitials(displayName)
    }

    val hasImageAvatar = effectiveAvatarType == "image" && !effectiveAvatarValue.isNullOrEmpty()

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (hasImageAvatar) {
            // Offline-first: prefer local cached avatar
            val localPath = if (imageStorage.userAvatarExists(userId)) {
                imageStorage.getUserAvatarPath(userId)
            } else {
                null
            }

            if (localPath != null) {
                // Use local file with modification time as cache buster
                val file = File(localPath)
                val lastModified = file.lastModified()
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(localPath)
                        .memoryCacheKey("$userId-avatar-$lastModified")
                        .diskCacheKey("$userId-avatar-$lastModified")
                        .build(),
                    contentDescription = "$displayName avatar",
                    modifier = Modifier
                        .size(size)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else if (serverUrl != null) {
                // Fallback: fetch from server with disabled caching
                // effectiveAvatarValue is non-null here because hasImageAvatar checked it
                val avatarUrl = "$serverUrl$effectiveAvatarValue"
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(avatarUrl)
                        .memoryCachePolicy(CachePolicy.DISABLED)
                        .diskCachePolicy(CachePolicy.DISABLED)
                        .build(),
                    contentDescription = "$displayName avatar",
                    modifier = Modifier
                        .size(size)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                // No local file and no server URL - show initials
                InitialsAvatar(initials = initials, color = color, size = size, fontSize = fontSize)
            }
        } else {
            // Auto-generated avatar with initials
            InitialsAvatar(initials = initials, color = color, size = size, fontSize = fontSize)
        }
    }
}

/**
 * Cached profile data for avatar lookup.
 */
private data class CachedProfileData(
    val avatarType: String,
    val avatarValue: String?,
    val avatarColor: String,
)

/**
 * Parse avatar color from hex string.
 */
private fun parseAvatarColor(hexColor: String): Color =
    try {
        Color(AndroidColor.parseColor(hexColor))
    } catch (_: Exception) {
        Color(0xFF6B7280) // Fallback gray
    }

/**
 * Simple initials avatar.
 */
@Composable
private fun InitialsAvatar(
    initials: String,
    color: Color,
    size: Dp,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            color = Color.White,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}
