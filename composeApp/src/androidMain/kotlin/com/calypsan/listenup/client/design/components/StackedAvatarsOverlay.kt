package com.calypsan.listenup.client.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.calypsan.listenup.client.data.local.images.ImageStorage
import com.calypsan.listenup.client.data.remote.CurrentlyListeningReaderResponse
import com.calypsan.listenup.client.data.repository.SettingsRepository
import org.koin.compose.koinInject
import java.io.File
import android.graphics.Color as AndroidColor

// Use getInitials from UserAvatar.kt

/**
 * Stacked horizontally overlapping avatars for displaying who's reading a book.
 *
 * Shows up to 3 avatar circles with user initials on a colored background,
 * with a "+N" badge if there are more readers.
 *
 * Visual design:
 * ```
 * ○○○+2
 * ```
 * - 32dp circles with 10dp overlap
 * - 2dp white border around each for separation
 * - Positioned in bottom-right of parent
 * - Each avatar is clickable to navigate to user profile
 *
 * @param readers List of readers (up to 3 will be displayed)
 * @param totalCount Total number of readers (for "+N" badge)
 * @param onUserClick Optional callback when a user avatar is clicked (receives userId)
 * @param modifier Modifier for positioning (typically Alignment.BottomEnd)
 */
@Composable
fun StackedAvatarsOverlay(
    readers: List<CurrentlyListeningReaderResponse>,
    totalCount: Int,
    modifier: Modifier = Modifier,
    onUserClick: ((String) -> Unit)? = null,
) {
    if (readers.isEmpty()) return

    val context = LocalContext.current
    val settingsRepository: SettingsRepository = koinInject()
    val imageStorage: ImageStorage = koinInject()
    val serverUrl by produceState<String?>(null) {
        value = settingsRepository.getServerUrl()?.value
    }

    val displayReaders = readers.take(3)
    val remainingCount = totalCount - displayReaders.size

    Row(
        modifier =
            modifier
                .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Stacked avatars
        displayReaders.forEachIndexed { index, reader ->
            val avatarColor =
                remember(reader.avatarColor) {
                    parseAvatarColor(reader.avatarColor)
                }
            val initials =
                remember(reader.displayName) {
                    getInitials(reader.displayName)
                }
            val hasImageAvatar = reader.avatarType == "image" && !reader.avatarValue.isNullOrEmpty()

            Box(
                modifier =
                    Modifier
                        // Each subsequent avatar overlaps the previous
                        .offset(x = (-10 * index).dp)
                        // Higher z-index for later avatars so they appear on top
                        .zIndex((displayReaders.size - index).toFloat())
                        .shadow(2.dp, CircleShape)
                        .size(32.dp)
                        .clip(CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                        .then(
                            if (onUserClick != null) {
                                Modifier.clickable { onUserClick(reader.userId) }
                            } else {
                                Modifier
                            },
                        ),
                contentAlignment = Alignment.Center,
            ) {
                if (hasImageAvatar) {
                    // Offline-first: prefer local cached avatar
                    val localPath =
                        if (imageStorage.userAvatarExists(reader.userId)) {
                            imageStorage.getUserAvatarPath(reader.userId)
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
                                    .memoryCacheKey("${reader.userId}-avatar-$lastModified")
                                    .diskCacheKey("${reader.userId}-avatar-$lastModified")
                                    .build(),
                            contentDescription = "${reader.displayName} avatar",
                            modifier =
                                Modifier
                                    .size(32.dp)
                                    .clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    } else if (serverUrl != null) {
                        // Fallback: fetch from server with disabled caching
                        // (will be cached locally via SSE download for future use)
                        val avatarUrl = "$serverUrl${reader.avatarValue}"
                        AsyncImage(
                            model =
                                ImageRequest
                                    .Builder(context)
                                    .data(avatarUrl)
                                    .memoryCachePolicy(CachePolicy.DISABLED)
                                    .diskCachePolicy(CachePolicy.DISABLED)
                                    .build(),
                            contentDescription = "${reader.displayName} avatar",
                            modifier =
                                Modifier
                                    .size(32.dp)
                                    .clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        // No local file and no server URL - show initials
                        AutoGeneratedAvatar(initials = initials, color = avatarColor)
                    }
                } else {
                    // Auto-generated avatar with initials
                    AutoGeneratedAvatar(initials = initials, color = avatarColor)
                }
            }
        }

        // "+N" badge if more readers
        if (remainingCount > 0) {
            Box(
                modifier =
                    Modifier
                        .offset(x = (-10 * displayReaders.size).dp)
                        .zIndex(0f)
                        .shadow(2.dp, RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 5.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+$remainingCount",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * Parse avatar color from hex string.
 *
 * @param hexColor Color in "#RRGGBB" format
 * @return Compose Color, or fallback gray if parsing fails
 */
private fun parseAvatarColor(hexColor: String): Color =
    try {
        Color(AndroidColor.parseColor(hexColor))
    } catch (_: Exception) {
        Color(0xFF6B7280) // Fallback gray
    }

/**
 * Auto-generated avatar with initials on colored background.
 */
@Composable
private fun AutoGeneratedAvatar(
    initials: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(32.dp)
                .background(color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}
