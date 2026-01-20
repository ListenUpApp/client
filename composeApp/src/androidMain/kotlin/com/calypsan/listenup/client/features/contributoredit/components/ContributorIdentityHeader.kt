package com.calypsan.listenup.client.features.contributoredit.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.calypsan.listenup.client.design.components.ListenUpAsyncImage
import com.calypsan.listenup.client.design.components.ListenUpLoadingIndicatorSmall
import com.calypsan.listenup.client.design.components.getInitials
import com.calypsan.listenup.client.design.theme.GoogleSansDisplay

/**
 * Identity header with large avatar and name field side by side.
 */
@Suppress("LongMethod")
@Composable
fun ContributorIdentityHeader(
    imagePath: String?,
    name: String,
    colorScheme: ContributorColorScheme,
    isUploadingImage: Boolean,
    onNameChange: (String) -> Unit,
    onAvatarClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    val surfaceColor = MaterialTheme.colorScheme.surface

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(16.dp),
    ) {
        // Floating back button
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

        Spacer(modifier = Modifier.height(24.dp))

        // Avatar + Name row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Large editable avatar (120dp) - tappable for upload
            ElevatedCard(
                onClick = onAvatarClick,
                shape = CircleShape,
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 12.dp),
                colors =
                    CardDefaults.elevatedCardColors(
                        containerColor = colorScheme.primary,
                    ),
                modifier = Modifier.size(120.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (imagePath != null) {
                        ListenUpAsyncImage(
                            path = imagePath,
                            contentDescription = "Contributor photo",
                            contentScale = ContentScale.Crop,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                        )
                    } else {
                        Text(
                            text = getInitials(name),
                            style =
                                MaterialTheme.typography.displaySmall.copy(
                                    fontFamily = GoogleSansDisplay,
                                    fontWeight = FontWeight.Bold,
                                ),
                            color = colorScheme.onPrimary,
                        )
                    }

                    // Loading overlay during upload
                    if (isUploadingImage) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            ListenUpLoadingIndicatorSmall()
                        }
                    } else {
                        // Edit indicator
                        Box(
                            modifier =
                                Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(4.dp)
                                    .size(32.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                                        shape = CircleShape,
                                    ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Change photo",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }

            // Name field - Large editorial style
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                textStyle =
                    TextStyle(
                        fontFamily = GoogleSansDisplay,
                        fontWeight = FontWeight.Bold,
                        fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                placeholder = {
                    Text(
                        "Name",
                        style =
                            MaterialTheme.typography.headlineSmall.copy(
                                fontFamily = GoogleSansDisplay,
                                fontWeight = FontWeight.Bold,
                            ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                },
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        focusedContainerColor = surfaceColor.copy(alpha = 0.4f),
                        unfocusedContainerColor = surfaceColor.copy(alpha = 0.2f),
                    ),
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
