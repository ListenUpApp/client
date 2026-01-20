@file:Suppress(
    "MagicNumber",
    "LongMethod",
    "LongParameterList",
    "CyclomaticComplexMethod",
    "CognitiveComplexMethod",
)

package com.calypsan.listenup.client.features.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.calypsan.listenup.client.design.components.ListenUpLoadingIndicator
import com.calypsan.listenup.client.design.components.getInitials
import com.calypsan.listenup.client.presentation.profile.EditProfileUiState
import com.calypsan.listenup.client.presentation.profile.EditProfileViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.viewmodel.koinViewModel
import java.io.ByteArrayOutputStream
import android.graphics.Color as AndroidColor

/**
 * Screen for editing user profile.
 *
 * Features:
 * - Avatar editing (upload new or revert to auto)
 * - Tagline editing with character count
 * - Save button with loading state
 *
 * @param onBack Callback when back button is clicked
 * @param viewModel The ViewModel for edit profile
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    onBack: () -> Unit,
    onProfileUpdated: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: EditProfileViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Image picker
    val imagePicker =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia(),
        ) { uri: Uri? ->
            uri?.let { selectedUri ->
                scope.launch {
                    val compressedBytes = compressAvatar(context, selectedUri)
                    if (compressedBytes != null) {
                        viewModel.uploadAvatar(compressedBytes, "image/jpeg")
                    } else {
                        snackbarHostState.showSnackbar("Failed to process image")
                    }
                }
            }
        }

    // Show snackbar on success and notify parent
    LaunchedEffect(state.saveSuccess) {
        if (state.saveSuccess) {
            snackbarHostState.showSnackbar("Profile updated")
            onProfileUpdated()
            viewModel.clearSaveSuccess()
        }
    }

    // Show snackbar on password change success
    LaunchedEffect(state.passwordChangeSuccess) {
        if (state.passwordChangeSuccess) {
            snackbarHostState.showSnackbar("Password changed successfully")
            viewModel.clearPasswordChangeSuccess()
        }
    }

    // Show snackbar on error
    LaunchedEffect(state.error) {
        state.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            if (state.isLoading) {
                ListenUpLoadingIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                EditProfileContent(
                    state = state,
                    onTaglineChange = viewModel::onTaglineChange,
                    onSaveTagline = viewModel::saveTagline,
                    onUploadAvatar = {
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    onRevertAvatar = viewModel::revertToAutoAvatar,
                    onFirstNameChange = viewModel::onFirstNameChange,
                    onLastNameChange = viewModel::onLastNameChange,
                    onSaveName = viewModel::saveName,
                    onNewPasswordChange = viewModel::onNewPasswordChange,
                    onConfirmPasswordChange = viewModel::onConfirmPasswordChange,
                    onChangePassword = viewModel::changePassword,
                )
            }

            // Overlay loading indicator when saving
            if (state.isSaving) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center,
                ) {
                    ListenUpLoadingIndicator()
                }
            }
        }
    }
}

@Composable
private fun EditProfileContent(
    state: EditProfileUiState,
    onTaglineChange: (String) -> Unit,
    onSaveTagline: () -> Unit,
    onUploadAvatar: () -> Unit,
    onRevertAvatar: () -> Unit,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onSaveName: () -> Unit,
    onNewPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onChangePassword: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
    ) {
        // Avatar section
        Text(
            text = "Avatar",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Current avatar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val backgroundColor =
                remember(state.avatarColor) {
                    try {
                        Color(AndroidColor.parseColor(state.avatarColor))
                    } catch (_: Exception) {
                        Color(0xFF6B7280)
                    }
                }

            val context = LocalContext.current
            val cacheBuster = state.user?.updatedAtMs ?: 0

            if (state.hasImageAvatar && state.localAvatarPath != null) {
                // Use local avatar path for offline-first with cache busting
                AsyncImage(
                    model =
                        ImageRequest
                            .Builder(context)
                            .data(state.localAvatarPath)
                            .memoryCacheKey("${state.localAvatarPath}-$cacheBuster")
                            .diskCacheKey("${state.localAvatarPath}-$cacheBuster")
                            .build(),
                    contentDescription = "Current avatar",
                    modifier =
                        Modifier
                            .size(80.dp)
                            .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier =
                        Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(backgroundColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = getInitials(state.displayName),
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Button(
                    onClick = onUploadAvatar,
                    enabled = !state.isSaving,
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Upload Photo")
                }

                if (state.hasImageAvatar) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onRevertAvatar,
                        enabled = !state.isSaving,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Remove Photo")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Tagline section
        Text(
            text = "Tagline",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "A short bio that appears on your profile",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = state.editedTagline,
            onValueChange = onTaglineChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("What are you listening to?") },
            singleLine = true,
            supportingText = {
                Text(
                    text = "${state.taglineCharCount}/${EditProfileViewModel.MAX_TAGLINE_LENGTH}",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (state.taglineCharCount >= EditProfileViewModel.MAX_TAGLINE_LENGTH) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            },
            enabled = !state.isSaving,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onSaveTagline,
            enabled = state.hasTaglineChanged && !state.isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save Tagline")
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(24.dp))

        // Name section
        Text(
            text = "Name",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Update your first and last name",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = state.editedFirstName,
            onValueChange = onFirstNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("First Name") },
            placeholder = { Text("Enter your first name") },
            singleLine = true,
            enabled = !state.isSaving,
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = state.editedLastName,
            onValueChange = onLastNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Last Name") },
            placeholder = { Text("Enter your last name") },
            singleLine = true,
            enabled = !state.isSaving,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onSaveName,
            enabled = state.hasNameChanged && !state.isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save Name")
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(24.dp))

        // Password section
        Text(
            text = "Change Password",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Set a new password for your account",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(12.dp))

        var newPasswordVisible by remember { mutableStateOf(false) }
        var confirmPasswordVisible by remember { mutableStateOf(false) }

        OutlinedTextField(
            value = state.newPassword,
            onValueChange = onNewPasswordChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("New Password") },
            placeholder = { Text("Enter new password") },
            singleLine = true,
            enabled = !state.isSaving,
            visualTransformation =
                if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                    Icon(
                        imageVector =
                            if (newPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription =
                            if (newPasswordVisible) "Hide password" else "Show password",
                    )
                }
            },
            supportingText = {
                if (state.newPassword.isNotEmpty() && !state.isPasswordValid) {
                    Text(
                        text = "Must be at least 8 characters",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = state.confirmPassword,
            onValueChange = onConfirmPasswordChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Confirm Password") },
            placeholder = { Text("Re-enter new password") },
            singleLine = true,
            enabled = !state.isSaving,
            visualTransformation =
                if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                    Icon(
                        imageVector =
                            if (confirmPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription =
                            if (confirmPasswordVisible) "Hide password" else "Show password",
                    )
                }
            },
            supportingText = {
                if (state.confirmPassword.isNotEmpty() && !state.passwordsMatch) {
                    Text(
                        text = "Passwords do not match",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onChangePassword,
            enabled = state.canSavePassword && !state.isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Change Password")
        }
    }
}

/**
 * Maximum dimension for avatar images (width and height).
 */
private const val MAX_AVATAR_SIZE = 2048

/**
 * JPEG compression quality (0-100).
 */
private const val AVATAR_QUALITY = 85

/**
 * Compress and resize an avatar image.
 *
 * - Resizes to fit within [MAX_AVATAR_SIZE] x [MAX_AVATAR_SIZE] while maintaining aspect ratio
 * - Compresses as JPEG with [AVATAR_QUALITY]
 *
 * @param context Android context for content resolver
 * @param uri URI of the selected image
 * @return Compressed image bytes, or null if processing fails
 */
private suspend fun compressAvatar(
    context: Context,
    uri: Uri,
): ByteArray? =
    withContext(Dispatchers.IO) {
        try {
            // Decode bitmap
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (originalBitmap == null) {
                return@withContext null
            }

            // Calculate scaled dimensions maintaining aspect ratio
            val width = originalBitmap.width
            val height = originalBitmap.height
            val scale = minOf(MAX_AVATAR_SIZE.toFloat() / width, MAX_AVATAR_SIZE.toFloat() / height, 1f)

            val scaledWidth = (width * scale).toInt()
            val scaledHeight = (height * scale).toInt()

            // Resize if needed
            val scaledBitmap =
                if (scale < 1f) {
                    Bitmap.createScaledBitmap(originalBitmap, scaledWidth, scaledHeight, true)
                } else {
                    originalBitmap
                }

            // Compress to JPEG
            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, AVATAR_QUALITY, outputStream)

            // Clean up
            if (scaledBitmap != originalBitmap) {
                scaledBitmap.recycle()
            }
            originalBitmap.recycle()

            outputStream.toByteArray()
        } catch (_: Exception) {
            null
        }
    }
