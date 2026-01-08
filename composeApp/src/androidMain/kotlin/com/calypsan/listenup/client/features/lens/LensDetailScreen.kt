@file:Suppress("MagicNumber")

package com.calypsan.listenup.client.features.lens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.calypsan.listenup.client.design.components.ListenUpAsyncImage
import com.calypsan.listenup.client.design.components.ListenUpLoadingIndicator
import com.calypsan.listenup.client.domain.model.LensBook
import com.calypsan.listenup.client.presentation.lens.LensDetailUiState
import com.calypsan.listenup.client.presentation.lens.LensDetailViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Screen displaying lens details with its books.
 *
 * Features:
 * - Hero section with lens icon and stats
 * - Lens name as title
 * - Owner info with avatar
 * - Optional description (expandable)
 * - List of books in the lens with covers
 * - Book items are clickable to navigate to book detail
 *
 * @param lensId The ID of the lens to display
 * @param onBack Callback when back button is clicked
 * @param onBookClick Callback when a book is clicked
 * @param viewModel The ViewModel for lens detail data
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LensDetailScreen(
    lensId: String,
    onBack: () -> Unit,
    onBookClick: (String) -> Unit,
    onEditClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: LensDetailViewModel = koinViewModel(),
) {
    LaunchedEffect(lensId) {
        viewModel.loadLens(lensId)
    }

    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.name.ifBlank { "Lens" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    if (state.isOwner && onEditClick != null) {
                        IconButton(onClick = { onEditClick(lensId) }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit lens",
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
                    LensDetailContent(
                        state = state,
                        onBookClick = onBookClick,
                        formatDuration = viewModel::formatDuration,
                    )
                }
            }
        }
    }
}

/**
 * Content for lens detail screen.
 */
@Composable
private fun LensDetailContent(
    state: LensDetailUiState,
    onBookClick: (String) -> Unit,
    formatDuration: (Long) -> String,
) {
    var isDescriptionExpanded by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        // Hero section with lens icon and stats
        item {
            LensHeroSection(
                avatarColor = state.ownerAvatarColor,
                ownerDisplayName = state.ownerDisplayName,
                bookCount = state.bookCount,
                totalDuration = formatDuration(state.totalDurationSeconds),
            )
        }

        // Description section (if available)
        state.description.takeIf { it.isNotBlank() }?.let { description ->
            item {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 16.dp),
                ) {
                    Text(
                        text = "About",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (description.length > 150) {
                        TextButton(
                            onClick = { isDescriptionExpanded = !isDescriptionExpanded },
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Text(if (isDescriptionExpanded) "Read less" else "Read more")
                        }
                    }
                }
            }
        }

        // Books section header
        item {
            Text(
                text = "Books in Lens",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier =
                    Modifier
                        .padding(horizontal = 16.dp)
                        .padding(top = 24.dp, bottom = 8.dp),
            )
        }

        // Empty state
        if (state.books.isEmpty()) {
            item {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Book,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No books yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (state.isOwner) {
                            Text(
                                text = "Add books from the library",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        // Books list
        items(
            items = state.books,
            key = { it.id },
        ) { book ->
            LensBookItem(
                book = book,
                onClick = { onBookClick(book.id) },
                formatDuration = formatDuration,
            )
        }
    }
}

/**
 * Hero section with lens icon, owner info, and aggregate stats.
 */
@Composable
private fun LensHeroSection(
    avatarColor: String,
    ownerDisplayName: String,
    bookCount: Int,
    totalDuration: String,
) {
    // Parse avatar color from hex string
    val color =
        remember(avatarColor) {
            try {
                Color(android.graphics.Color.parseColor(avatarColor))
            } catch (_: Exception) {
                Color(0xFF6B7280) // Fallback gray
            }
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Lens icon
        Box(
            modifier =
                Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.FilterList,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(48.dp),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Owner info
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(color),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = ownerDisplayName.firstOrNull()?.uppercaseChar()?.toString() ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
            Text(
                text = ownerDisplayName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Stats row
        Row(
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatItem(
                icon = Icons.AutoMirrored.Filled.LibraryBooks,
                value = "$bookCount",
                label = if (bookCount == 1) "Book" else "Books",
            )
            StatItem(
                icon = Icons.Default.Schedule,
                value = totalDuration,
                label = "Total",
            )
        }
    }
}

/**
 * Single stat item with icon, value, and label.
 */
@Composable
private fun StatItem(
    icon: ImageVector,
    value: String,
    label: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * List item for a book in the lens.
 */
@Composable
private fun LensBookItem(
    book: LensBook,
    onClick: () -> Unit,
    formatDuration: (Long) -> String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Book cover
            Box(
                modifier =
                    Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                if (book.coverPath != null) {
                    ListenUpAsyncImage(
                        path = book.coverPath,
                        contentDescription = book.title,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp)),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Book,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(32.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Book info
            Column(modifier = Modifier.weight(1f)) {
                // Title
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Author
                Text(
                    text = book.authorNames.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Duration
                Text(
                    text = formatDuration(book.durationSeconds),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
