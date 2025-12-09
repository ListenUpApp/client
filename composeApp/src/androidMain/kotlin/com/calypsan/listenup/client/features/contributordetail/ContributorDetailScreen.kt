package com.calypsan.listenup.client.features.contributordetail

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.calypsan.listenup.client.design.components.ListenUpLoadingIndicator
import com.calypsan.listenup.client.design.components.avatarColorForUser
import com.calypsan.listenup.client.design.components.getInitials
import com.calypsan.listenup.client.features.library.BookCard
import com.calypsan.listenup.client.presentation.contributordetail.ContributorDetailUiState
import com.calypsan.listenup.client.presentation.contributordetail.ContributorDetailViewModel
import com.calypsan.listenup.client.presentation.contributordetail.RoleSection
import org.koin.compose.viewmodel.koinViewModel

/**
 * Screen displaying contributor details with books grouped by role.
 *
 * Features:
 * - Large avatar with contributor name
 * - Optional description (expandable)
 * - Sections for each role (Written By, Narrated By, etc.)
 * - Horizontal scrolling book previews per role
 * - "View All" for roles with many books
 *
 * @param contributorId The ID of the contributor to display
 * @param onBackClick Callback when back button is clicked
 * @param onBookClick Callback when a book is clicked
 * @param onViewAllClick Callback when "View All" is clicked for a role
 * @param viewModel The ViewModel for contributor detail data
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContributorDetailScreen(
    contributorId: String,
    onBackClick: () -> Unit,
    onBookClick: (String) -> Unit,
    onViewAllClick: (contributorId: String, role: String) -> Unit,
    viewModel: ContributorDetailViewModel = koinViewModel(),
) {
    LaunchedEffect(contributorId) {
        viewModel.loadContributor(contributorId)
    }

    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.contributor?.name ?: "Contributor",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
            )
        },
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
                    ContributorDetailContent(
                        state = state,
                        bookProgress = state.bookProgress,
                        onBookClick = onBookClick,
                        onViewAllClick = { role -> onViewAllClick(contributorId, role) },
                    )
                }
            }
        }
    }
}

/**
 * Content for contributor detail screen.
 */
@Composable
private fun ContributorDetailContent(
    state: ContributorDetailUiState,
    bookProgress: Map<String, Float>,
    onBookClick: (String) -> Unit,
    onViewAllClick: (role: String) -> Unit,
) {
    var isDescriptionExpanded by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        // Header with avatar and name
        item {
            ContributorHeader(
                name = state.contributor?.name ?: "",
                contributorId = state.contributor?.id ?: "",
                description = state.contributor?.description,
                isDescriptionExpanded = isDescriptionExpanded,
                onExpandClick = { isDescriptionExpanded = !isDescriptionExpanded },
            )
        }

        // Role sections
        items(
            items = state.roleSections,
            key = { it.role },
        ) { section ->
            RoleSectionRow(
                section = section,
                bookProgress = bookProgress,
                onBookClick = onBookClick,
                onViewAllClick = { onViewAllClick(section.role) },
            )
        }
    }
}

/**
 * Header with avatar, name, and optional description.
 */
@Composable
private fun ContributorHeader(
    name: String,
    contributorId: String,
    description: String?,
    isDescriptionExpanded: Boolean,
    onExpandClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
    ) {
        // Large avatar
        Box(
            modifier =
                Modifier
                    .size(96.dp)
                    .background(
                        color = avatarColorForUser(contributorId),
                        shape = CircleShape,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = getInitials(name),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Name
        Text(
            text = name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        // Description (if available)
        description?.takeIf { it.isNotBlank() }?.let { desc ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = desc,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (desc.length > 150) {
                TextButton(
                    onClick = onExpandClick,
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(if (isDescriptionExpanded) "Read less" else "Read more")
                }
            }
        }
    }
}

/**
 * A section for a single role with horizontal book carousel.
 */
@Composable
private fun RoleSectionRow(
    section: RoleSection,
    bookProgress: Map<String, Float>,
    onBookClick: (String) -> Unit,
    onViewAllClick: () -> Unit,
) {
    Column {
        // Section header
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = section.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${section.bookCount} ${if (section.bookCount == 1) "book" else "books"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (section.showViewAll) {
                TextButton(onClick = onViewAllClick) {
                    Text("View All")
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Horizontal book carousel
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                items = section.previewBooks,
                key = { it.id.value },
            ) { book ->
                BookCard(
                    book = book,
                    onClick = { onBookClick(book.id.value) },
                    progress = bookProgress[book.id.value],
                    modifier = Modifier.width(140.dp),
                )
            }
        }
    }
}
