package com.calypsan.listenup.client.features.home.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calypsan.listenup.client.domain.model.Lens

/**
 * Card for a lens in the My Lenses section.
 *
 * Features:
 * - Icon with owner's avatar color
 * - Lens name and book count
 * - Press-to-scale animation
 *
 * @param lens The lens domain model to display
 * @param onClick Callback when card is clicked
 * @param modifier Optional modifier
 */
@Composable
fun LensCard(
    lens: Lens,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        label = "card_scale",
    )

    // Parse avatar color from hex string
    val avatarColor =
        remember(lens.ownerAvatarColor) {
            try {
                Color(android.graphics.Color.parseColor(lens.ownerAvatarColor))
            } catch (_: Exception) {
                Color(0xFF6B7280) // Fallback gray
            }
        }

    Column(
        modifier =
            modifier
                .width(140.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
    ) {
        // Icon container
        LensIcon(
            color = avatarColor,
            modifier = Modifier.size(140.dp),
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Metadata
        Column(modifier = Modifier.padding(horizontal = 2.dp)) {
            Text(
                text = lens.name,
                style =
                    MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp,
                    ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = "${lens.bookCount} ${if (lens.bookCount == 1) "book" else "books"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Lens icon with colored background.
 */
@Composable
private fun LensIcon(
    color: Color,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.medium

    Box(
        modifier =
            modifier
                .shadow(elevation = 4.dp, shape = shape)
                .clip(shape)
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
}
