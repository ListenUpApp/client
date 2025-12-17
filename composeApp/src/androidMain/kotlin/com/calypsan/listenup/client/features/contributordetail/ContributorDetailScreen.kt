@file:Suppress("MagicNumber")

package com.calypsan.listenup.client.features.contributordetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.calypsan.listenup.client.design.components.ListenUpLoadingIndicator
import com.calypsan.listenup.client.design.components.getInitials
import com.calypsan.listenup.client.design.theme.GoogleSansDisplay
import com.calypsan.listenup.client.features.contributoredit.components.ContributorColorScheme
import com.calypsan.listenup.client.features.contributoredit.components.rememberContributorColorScheme
import com.calypsan.listenup.client.features.library.BookCard
import com.calypsan.listenup.client.presentation.contributordetail.ContributorDetailUiState
import com.calypsan.listenup.client.presentation.contributordetail.ContributorDetailViewModel
import com.calypsan.listenup.client.presentation.contributordetail.RoleSection
import org.koin.compose.viewmodel.koinViewModel

/**
 * Artist Portfolio screen - an immersive contributor detail experience.
 *
 * Design Philosophy: "The person is the brand."
 * Uses dynamic color gradients derived from contributor ID to create
 * unique, personalized atmospheres for each artist.
 *
 * Layout Hierarchy:
 * 1. Hero Section - Immersive gradient header with floating avatar
 * 2. Artist Identity - Name, dates, aliases in premium typography
 * 3. Biography - Expandable description
 * 4. The Work - Role sections with horizontal carousels
 */
@Composable
fun ContributorDetailScreen(
    contributorId: String,
    onBackClick: () -> Unit,
    onBookClick: (String) -> Unit,
    onEditClick: (String) -> Unit,
    onViewAllClick: (contributorId: String, role: String) -> Unit,
    viewModel: ContributorDetailViewModel = koinViewModel(),
) {
    LaunchedEffect(contributorId) {
        viewModel.loadContributor(contributorId)
    }

    val state by viewModel.state.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
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
                ContributorPortfolio(
                    contributorId = contributorId,
                    state = state,
                    onBackClick = onBackClick,
                    onEditClick = { onEditClick(contributorId) },
                    onBookClick = onBookClick,
                    onViewAllClick = { role -> onViewAllClick(contributorId, role) },
                )
            }
        }
    }
}

// =============================================================================
// MAIN PORTFOLIO LAYOUT
// =============================================================================

@Composable
private fun ContributorPortfolio(
    contributorId: String,
    state: ContributorDetailUiState,
    onBackClick: () -> Unit,
    onEditClick: () -> Unit,
    onBookClick: (String) -> Unit,
    onViewAllClick: (role: String) -> Unit,
) {
    var isDescriptionExpanded by rememberSaveable { mutableStateOf(false) }
    val colorScheme = rememberContributorColorScheme(contributorId)
    val surfaceColor = MaterialTheme.colorScheme.surface

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        // =====================================================================
        // 1. HERO SECTION - Immersive gradient with floating avatar
        // =====================================================================
        item {
            HeroHeader(
                name = state.contributor?.name ?: "",
                aliases = state.contributor?.aliasList() ?: emptyList(),
                imagePath = state.contributor?.imagePath,
                contributorId = contributorId,
                colorScheme = colorScheme,
                surfaceColor = surfaceColor,
                onBackClick = onBackClick,
                onEditClick = onEditClick,
            )
        }

        // =====================================================================
        // 2. ARTIST METADATA - Life dates, website
        // =====================================================================
        item {
            ArtistMetadata(
                birthDate = state.contributor?.birthDate,
                deathDate = state.contributor?.deathDate,
                website = state.contributor?.website,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }

        // =====================================================================
        // 3. BIOGRAPHY - Expandable description
        // =====================================================================
        state.contributor?.description?.takeIf { it.isNotBlank() }?.let { description ->
            item {
                BiographySection(
                    description = description,
                    isExpanded = isDescriptionExpanded,
                    onToggleExpanded = { isDescriptionExpanded = !isDescriptionExpanded },
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
            }
        }

        // =====================================================================
        // 4. THE WORK - Role sections with carousels
        // =====================================================================
        items(
            items = state.roleSections,
            key = { it.role },
        ) { section ->
            WorkSection(
                section = section,
                bookProgress = state.bookProgress,
                onBookClick = onBookClick,
                onViewAllClick = { onViewAllClick(section.role) },
            )
        }
    }
}

// =============================================================================
// 1. HERO HEADER - The dramatic first impression
// =============================================================================

@Composable
private fun HeroHeader(
    name: String,
    aliases: List<String>,
    imagePath: String?,
    contributorId: String,
    colorScheme: ContributorColorScheme,
    surfaceColor: Color,
    onBackClick: () -> Unit,
    onEditClick: () -> Unit,
) {
    // Create a smooth gradient from rich color to surface
    val gradientColors =
        listOf(
            colorScheme.primaryDark,
            colorScheme.primaryMuted.copy(alpha = 0.8f),
            colorScheme.primaryMuted.copy(alpha = 0.4f),
            surfaceColor.copy(alpha = 0.9f),
            surfaceColor,
        )

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 400.dp)
                .background(Brush.verticalGradient(gradientColors)),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Floating navigation buttons
            NavigationBar(
                onBackClick = onBackClick,
                onEditClick = onEditClick,
                surfaceColor = surfaceColor,
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Elevated Avatar - the visual anchor
            ElevatedAvatar(
                name = name,
                imagePath = imagePath,
                contributorId = contributorId,
                colorScheme = colorScheme,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Name in display typography
            Text(
                text = name,
                style =
                    MaterialTheme.typography.headlineLarge.copy(
                        fontFamily = GoogleSansDisplay,
                        fontWeight = FontWeight.Bold,
                    ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 32.dp),
            )

            // Aliases (AKA) directly under name
            if (aliases.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "aka ${aliases.joinToString(", ")}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Floating back and edit buttons with semi-transparent surface.
 */
@Composable
private fun NavigationBar(
    onBackClick: () -> Unit,
    onEditClick: () -> Unit,
    surfaceColor: Color,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(
            onClick = onBackClick,
            modifier =
                Modifier
                    .size(48.dp)
                    .background(
                        color = surfaceColor.copy(alpha = 0.5f),
                        shape = CircleShape,
                    ),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }

        IconButton(
            onClick = onEditClick,
            modifier =
                Modifier
                    .size(48.dp)
                    .background(
                        color = surfaceColor.copy(alpha = 0.5f),
                        shape = CircleShape,
                    ),
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Edit contributor",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * Large elevated avatar (140dp) - the visual centerpiece.
 * Displays contributor image if available, otherwise shows initials.
 */
@Suppress("UnusedParameter")
@Composable
private fun ElevatedAvatar(
    name: String,
    imagePath: String?,
    contributorId: String,
    colorScheme: ContributorColorScheme,
) {
    val initials = if (name.isNotBlank()) getInitials(name) else "?"

    ElevatedCard(
        shape = CircleShape,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 12.dp),
        colors =
            CardDefaults.elevatedCardColors(
                containerColor = colorScheme.primary,
            ),
        modifier = Modifier.size(140.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            if (imagePath != null) {
                com.calypsan.listenup.client.design.components.ListenUpAsyncImage(
                    path = imagePath,
                    contentDescription = "$name profile image",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = initials,
                    style =
                        MaterialTheme.typography.displayMedium.copy(
                            fontFamily = GoogleSansDisplay,
                            fontWeight = FontWeight.Bold,
                        ),
                    color = colorScheme.onPrimary,
                )
            }
        }
    }
}

// =============================================================================
// 2. ARTIST METADATA - Supporting identity information
// =============================================================================

@Composable
private fun ArtistMetadata(
    birthDate: String?,
    deathDate: String?,
    website: String?,
    modifier: Modifier = Modifier,
) {
    val hasContent = birthDate != null || deathDate != null || website?.isNotBlank() == true

    if (!hasContent) return

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Life dates
        val lifeDates = formatLifeDates(birthDate, deathDate)
        if (lifeDates != null) {
            Text(
                text = lifeDates,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        // Website
        website?.takeIf { it.isNotBlank() }?.let { url ->
            Text(
                text = url,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// =============================================================================
// 3. BIOGRAPHY - The story behind the artist
// =============================================================================

@Composable
private fun BiographySection(
    description: String,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "About",
            style =
                MaterialTheme.typography.titleMedium.copy(
                    fontFamily = GoogleSansDisplay,
                    fontWeight = FontWeight.Bold,
                ),
            modifier = Modifier.padding(bottom = 12.dp),
        )

        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (isExpanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
        )

        if (description.length > 200) {
            TextButton(
                onClick = onToggleExpanded,
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(if (isExpanded) "Read less" else "Read more")
            }
        }
    }
}

// =============================================================================
// 4. THE WORK - Role sections with premium carousels
// =============================================================================

@Composable
private fun WorkSection(
    section: RoleSection,
    bookProgress: Map<String, Float>,
    onBookClick: (String) -> Unit,
    onViewAllClick: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(vertical = 12.dp),
    ) {
        // Section header with display typography
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = section.displayName,
                    style =
                        MaterialTheme.typography.titleLarge.copy(
                            fontFamily = GoogleSansDisplay,
                            fontWeight = FontWeight.Bold,
                        ),
                )
                Text(
                    text = "${section.bookCount} ${if (section.bookCount == 1) "book" else "books"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (section.showViewAll) {
                FilledTonalButton(
                    onClick = onViewAllClick,
                    shape = RoundedCornerShape(24.dp),
                ) {
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

        Spacer(modifier = Modifier.height(16.dp))

        // Horizontal book carousel with generous spacing
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(
                items = section.previewBooks,
                key = { it.id.value },
            ) { book ->
                BookCard(
                    book = book,
                    onClick = { onBookClick(book.id.value) },
                    progress = bookProgress[book.id.value],
                    modifier = Modifier.width(150.dp),
                )
            }
        }
    }
}

// =============================================================================
// HELPERS
// =============================================================================

/**
 * Format birth and death dates for display.
 */
private fun formatLifeDates(
    birthDate: String?,
    deathDate: String?,
): String? {
    val birth = birthDate?.let { formatDateForDisplay(it) }
    val death = deathDate?.let { formatDateForDisplay(it) }

    return when {
        birth != null && death != null -> "$birth – $death"
        birth != null -> "Born $birth"
        death != null -> "Died $death"
        else -> null
    }
}

/**
 * Format an ISO date (YYYY-MM-DD) for user-friendly display.
 */
private fun formatDateForDisplay(isoDate: String): String? {
    return try {
        val parts = isoDate.split("-")
        if (parts.size != 3) return null
        val year = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val day = parts[2].toIntOrNull() ?: return null
        if (month < 1 || month > 12) return null
        val monthNames =
            listOf(
                "January",
                "February",
                "March",
                "April",
                "May",
                "June",
                "July",
                "August",
                "September",
                "October",
                "November",
                "December",
            )
        "${monthNames[month - 1]} $day, $year"
    } catch (
        @Suppress("SwallowedException") e: Exception,
    ) {
        null
    }
}
