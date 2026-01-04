@file:Suppress("MagicNumber")

package com.calypsan.listenup.client.features.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.calypsan.listenup.client.data.remote.model.LensSummaryResponse
import com.calypsan.listenup.client.data.remote.model.RecentBookResponse
import com.calypsan.listenup.client.data.repository.SettingsRepository
import com.calypsan.listenup.client.design.components.ListenUpAsyncImage
import com.calypsan.listenup.client.design.components.ListenUpLoadingIndicator
import com.calypsan.listenup.client.design.components.getInitials
import com.calypsan.listenup.client.presentation.profile.UserProfileUiState
import com.calypsan.listenup.client.presentation.profile.UserProfileViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import android.graphics.Color as AndroidColor

/**
 * Screen displaying a user's full profile.
 *
 * Features:
 * - Large avatar (image or auto-generated)
 * - Display name and tagline
 * - Listening stats in card format
 * - Recent finished books carousel
 * - Public lenses list
 * - Edit button if viewing own profile
 *
 * @param userId The ID of the user to display
 * @param onBack Callback when back button is clicked
 * @param onEditClick Callback when edit button is clicked (own profile only)
 * @param onBookClick Callback when a book is clicked
 * @param onLensClick Callback when a lens is clicked
 * @param viewModel The ViewModel for profile data
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    userId: String,
    onBack: () -> Unit,
    onEditClick: () -> Unit,
    onBookClick: (String) -> Unit,
    onLensClick: (String) -> Unit,
    onCreateLensClick: () -> Unit,
    refreshKey: Int = 0,
    modifier: Modifier = Modifier,
    viewModel: UserProfileViewModel = koinViewModel(),
) {
    // Load profile initially and refresh when refreshKey changes
    LaunchedEffect(userId, refreshKey) {
        viewModel.loadProfile(userId, forceRefresh = refreshKey > 0)
    }

    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    if (state.isOwnProfile) {
                        IconButton(onClick = onEditClick) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit profile",
                            )
                        }
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            when {
                state.isLoading -> {
                    ListenUpLoadingIndicator(modifier = Modifier.align(Alignment.Center))
                }

                state.error != null -> {
                    Text(
                        text = state.error ?: "Unknown error",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                else -> {
                    ProfileContent(
                        state = state,
                        formatListenTime = viewModel::formatListenTime,
                        onBookClick = onBookClick,
                        onLensClick = onLensClick,
                        onCreateLensClick = onCreateLensClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileContent(
    state: UserProfileUiState,
    formatListenTime: (Long) -> String,
    onBookClick: (String) -> Unit,
    onLensClick: (String) -> Unit,
    onCreateLensClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        // Header with avatar, name, tagline
        item {
            ProfileHeader(
                displayName = state.displayName,
                avatarType = state.avatarType,
                avatarValue = state.avatarValue,
                avatarColor = state.avatarColor,
                tagline = state.tagline,
                isOwnProfile = state.isOwnProfile,
                localAvatarPath = state.localAvatarPath,
                avatarCacheBuster = state.avatarCacheBuster,
            )
        }

        // Stats section
        item {
            Spacer(modifier = Modifier.height(24.dp))
            StatsSection(
                totalListenTime = formatListenTime(state.totalListenTimeMs),
                booksFinished = state.booksFinished,
                currentStreak = state.currentStreak,
                longestStreak = state.longestStreak,
            )
        }

        // Recent books section
        if (state.recentBooks.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(32.dp))
                SectionHeader(title = "Recently Finished")
            }
            item {
                Spacer(modifier = Modifier.height(16.dp))
                RecentBooksRow(
                    books = state.recentBooks,
                    onBookClick = onBookClick,
                )
            }
        }

        // Public lenses section (show for own profile even if empty, to allow creating lenses)
        if (state.publicLenses.isNotEmpty() || state.isOwnProfile) {
            item {
                Spacer(modifier = Modifier.height(32.dp))
                LensesSectionHeader(
                    showAddButton = state.isOwnProfile,
                    onAddClick = onCreateLensClick,
                )
            }
            if (state.publicLenses.isNotEmpty()) {
                items(state.publicLenses) { lens ->
                    LensItem(
                        lens = lens,
                        onClick = { onLensClick(lens.id) },
                    )
                }
            } else {
                item {
                    Text(
                        text = "No lenses yet. Create one to curate your favorite books!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(
    displayName: String,
    avatarType: String,
    avatarValue: String?,
    avatarColor: String,
    tagline: String?,
    isOwnProfile: Boolean,
    localAvatarPath: String?,
    avatarCacheBuster: Long,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val settingsRepository: SettingsRepository = koinInject()
    val serverUrl by produceState<String?>(null) {
        value = settingsRepository.getServerUrl()?.value
    }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Avatar
        val backgroundColor =
            remember(avatarColor) {
                try {
                    Color(AndroidColor.parseColor(avatarColor))
                } catch (_: Exception) {
                    Color(0xFF6B7280)
                }
            }

        when {
            // Image avatar: prefer local file path (offline-first)
            avatarType == "image" && localAvatarPath != null -> {
                AsyncImage(
                    model =
                        ImageRequest
                            .Builder(context)
                            .data(localAvatarPath)
                            // Use cache buster (updatedAt) to force reload when avatar changes
                            .memoryCacheKey("$localAvatarPath-$avatarCacheBuster")
                            .diskCacheKey("$localAvatarPath-$avatarCacheBuster")
                            .build(),
                    contentDescription = "$displayName avatar",
                    modifier =
                        Modifier
                            .size(120.dp)
                            .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            }

            // Fallback: fetch from server if no local path yet (downloading in background)
            avatarType == "image" && avatarValue != null && serverUrl != null -> {
                AsyncImage(
                    model =
                        ImageRequest
                            .Builder(context)
                            .data("$serverUrl$avatarValue")
                            // Use cache buster to force reload when avatar changes
                            .memoryCacheKey("$avatarValue-$avatarCacheBuster")
                            .diskCacheKey("$avatarValue-$avatarCacheBuster")
                            .build(),
                    contentDescription = "$displayName avatar",
                    modifier =
                        Modifier
                            .size(120.dp)
                            .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            }

            // Auto-generated avatar with initials
            else -> {
                Box(
                    modifier =
                        Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(backgroundColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = getInitials(displayName),
                        color = Color.White,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Display name
        Text(
            text = displayName,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        // Tagline
        if (!tagline.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = tagline,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun StatsSection(
    totalListenTime: String,
    booksFinished: Int,
    currentStreak: Int,
    longestStreak: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatCard(
            icon = Icons.Default.Schedule,
            value = totalListenTime,
            label = "Listened",
            modifier = Modifier.weight(1f),
        )
        StatCard(
            icon = Icons.Default.Book,
            value = booksFinished.toString(),
            label = "Finished",
            modifier = Modifier.weight(1f),
        )
        StatCard(
            icon = Icons.Default.LocalFireDepartment,
            value = "${currentStreak}d",
            label = "Streak",
            modifier = Modifier.weight(1f),
        )
        StatCard(
            icon = Icons.Default.EmojiEvents,
            value = "${longestStreak}d",
            label = "Best",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatCard(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun LensesSectionHeader(
    showAddButton: Boolean,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Lenses",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        if (showAddButton) {
            IconButton(onClick = onAddClick) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Create lens",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun RecentBooksRow(
    books: List<RecentBookResponse>,
    onBookClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(books, key = { it.bookId }) { book ->
            RecentBookCard(
                book = book,
                onClick = { onBookClick(book.bookId) },
            )
        }
    }
}

@Composable
private fun RecentBookCard(
    book: RecentBookResponse,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .width(140.dp)
                .clickable(onClick = onClick),
    ) {
        // Book cover
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            // Square
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            if (book.coverPath != null) {
                ListenUpAsyncImage(
                    path = book.coverPath,
                    contentDescription = book.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                // Placeholder
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Book,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Title
        Text(
            text = book.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LensItem(
    lens: LensSummaryResponse,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        onClick = onClick,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = lens.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "${lens.bookCount} books",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
