package com.calypsan.listenup.client.features.bookdetail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.calypsan.listenup.client.design.components.CoverColors
import com.calypsan.listenup.client.design.components.ElevatedCoverCard
import com.calypsan.listenup.client.design.components.ProgressOverlay
import com.calypsan.listenup.client.design.theme.GoogleSansDisplay

/**
 * Hero section with color-extracted gradient background.
 * Uses Palette API colors for a cohesive look that matches the cover art.
 */
@Suppress("MagicNumber")
@Composable
fun HeroSection(
    coverPath: String?,
    title: String,
    subtitle: String?,
    progress: Float?,
    timeRemaining: String?,
    coverColors: CoverColors,
    onBackClick: () -> Unit,
    onEditClick: () -> Unit,
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val surfaceContainerColor = MaterialTheme.colorScheme.surfaceContainer

    // Take a bite, take a bite... (Sleep Token - The Offering)
    // Expressive gradient: saturated cover color -> surfaceContainer -> surface
    // Creates a more dramatic, immersive transition that honors the cover art
    val gradientColors = listOf(
        coverColors.darkMuted,
        coverColors.darkMuted.copy(alpha = 0.85f),
        surfaceContainerColor.copy(alpha = 0.7f),
        surfaceContainerColor,
        surfaceColor,
    )


    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 480.dp)
                .background(
                    Brush.verticalGradient(gradientColors),
                ),
    ) {
        // Content overlay
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Navigation bar
            HeroNavigationBar(
                onBackClick = onBackClick,
                onEditClick = onEditClick,
                surfaceColor = surfaceColor,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Floating cover card
            FloatingCoverCard(
                coverPath = coverPath,
                title = title,
                progress = progress,
                timeRemaining = timeRemaining,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Title - Magazine headline style with high contrast for dark mode
            Text(
                text = title,
                style =
                    MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = GoogleSansDisplay,
                        fontWeight = FontWeight.Bold,
                    ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 32.dp),
            )

            // Subtitle
            subtitle?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/**
 * Navigation bar with translucent buttons.
 * Uses surfaceContainerHigh for better contrast against dynamic cover colors.
 */
@Composable
private fun HeroNavigationBar(
    onBackClick: () -> Unit,
    onEditClick: () -> Unit,
    @Suppress("UNUSED_PARAMETER") surfaceColor: Color,
) {
    val buttonBackground = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f)

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
                        color = buttonBackground,
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
                        color = buttonBackground,
                        shape = CircleShape,
                    ),
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Edit book",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * Floating cover card (240dp width) - the visual anchor.
 */
@Composable
private fun FloatingCoverCard(
    coverPath: String?,
    title: String,
    progress: Float?,
    timeRemaining: String?,
) {
    Box(contentAlignment = Alignment.Center) {
        ElevatedCoverCard(
            path = coverPath,
            contentDescription = title,
            modifier =
                Modifier
                    .width(240.dp)
                    .aspectRatio(1f),
        ) {
            progress?.let { prog ->
                ProgressOverlay(
                    progress = prog,
                    timeRemaining = timeRemaining,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}
