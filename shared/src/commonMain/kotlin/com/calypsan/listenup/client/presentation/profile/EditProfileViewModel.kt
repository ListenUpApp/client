package com.calypsan.listenup.client.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.data.local.db.UserDao
import com.calypsan.listenup.client.data.local.db.UserEntity
import com.calypsan.listenup.client.data.local.images.ImageStorage
import com.calypsan.listenup.client.data.repository.ProfileEditRepositoryContract
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * ViewModel for the Edit Profile screen.
 *
 * Uses offline-first pattern:
 * - Observes profile from local database ONLY (no server fetch)
 * - Saves changes locally immediately via ProfileEditRepository
 * - Changes are synced to server by PushSyncOrchestrator
 * - When sync completes, handlers update local cache, UI updates automatically
 */
class EditProfileViewModel(
    private val profileEditRepository: ProfileEditRepositoryContract,
    private val userDao: UserDao,
    private val imageStorage: ImageStorage,
) : ViewModel() {
    val state: StateFlow<EditProfileUiState>
        field = MutableStateFlow(EditProfileUiState())

    init {
        observeUser()
    }

    /**
     * Observe current user from local database.
     *
     * This ensures the UI updates when:
     * - ProfileAvatarHandler syncs new avatar after upload
     * - ProfileUpdateHandler syncs profile changes
     * - Any other sync updates the UserEntity
     *
     * NO server fetch - everything from local cache.
     */
    private fun observeUser() {
        viewModelScope.launch {
            userDao.observeCurrentUser().collect { user ->
                if (user != null) {
                    val currentState = state.value

                    // Get local avatar path if it exists
                    val localAvatarPath =
                        if (user.avatarType == "image" && imageStorage.userAvatarExists(user.id)) {
                            imageStorage.getUserAvatarPath(user.id)
                        } else {
                            null
                        }

                    state.update {
                        it.copy(
                            isLoading = false,
                            user = user,
                            localAvatarPath = localAvatarPath,
                            // Only set editedTagline from DB on first load
                            editedTagline =
                                if (currentState.user == null) {
                                    user.tagline ?: ""
                                } else {
                                    currentState.editedTagline
                                },
                            error = null,
                        )
                    }
                    logger.debug {
                        "User updated from local cache: avatar=${user.avatarType}/${user.avatarValue}, localPath=$localAvatarPath"
                    }
                } else {
                    state.update {
                        it.copy(
                            isLoading = false,
                            error = "No user data available",
                        )
                    }
                }
            }
        }
    }

    /**
     * Update the tagline input field.
     */
    fun onTaglineChange(newTagline: String) {
        // Enforce max length
        val truncated =
            if (newTagline.length > MAX_TAGLINE_LENGTH) {
                newTagline.take(MAX_TAGLINE_LENGTH)
            } else {
                newTagline
            }
        state.update { it.copy(editedTagline = truncated) }
    }

    /**
     * Save the updated tagline using offline-first pattern.
     */
    fun saveTagline() {
        val currentTagline = state.value.user?.tagline ?: ""
        val editedTagline = state.value.editedTagline

        // No change, skip
        if (editedTagline == currentTagline) return

        viewModelScope.launch {
            state.update { it.copy(isSaving = true, error = null) }

            when (profileEditRepository.updateTagline(editedTagline.ifEmpty { null })) {
                is Success -> {
                    // Local state will update via observeUser() when DB is updated
                    state.update {
                        it.copy(
                            isSaving = false,
                            saveSuccess = true,
                        )
                    }
                    logger.info { "Tagline update queued for sync" }
                }

                else -> {
                    logger.error { "Failed to save tagline" }
                    state.update {
                        it.copy(
                            isSaving = false,
                            error = "Failed to save tagline",
                        )
                    }
                }
            }
        }
    }

    /**
     * Upload an avatar image using offline-first pattern.
     */
    fun uploadAvatar(
        imageData: ByteArray,
        contentType: String,
    ) {
        viewModelScope.launch {
            state.update { it.copy(isSaving = true, error = null) }

            when (profileEditRepository.uploadAvatar(imageData, contentType)) {
                is Success -> {
                    // Don't update avatar state here - we don't have the server path yet.
                    // The current avatar continues to display until:
                    // 1. ProfileAvatarHandler successfully uploads the image
                    // 2. Handler updates UserEntity with the server's response
                    // 3. observeUser() sees the change and updates UI
                    state.update {
                        it.copy(
                            isSaving = false,
                            saveSuccess = true,
                        )
                    }
                    logger.info { "Avatar upload queued for sync" }
                }

                else -> {
                    logger.error { "Failed to upload avatar" }
                    state.update {
                        it.copy(
                            isSaving = false,
                            error = "Failed to upload avatar",
                        )
                    }
                }
            }
        }
    }

    /**
     * Revert to auto-generated avatar using offline-first pattern.
     */
    fun revertToAutoAvatar() {
        viewModelScope.launch {
            state.update { it.copy(isSaving = true, error = null) }

            when (profileEditRepository.revertToAutoAvatar()) {
                is Success -> {
                    // Local state will update via observeUser() when DB is updated
                    state.update {
                        it.copy(
                            isSaving = false,
                            saveSuccess = true,
                        )
                    }
                    logger.info { "Revert to auto avatar queued for sync" }
                }

                else -> {
                    logger.error { "Failed to revert avatar" }
                    state.update {
                        it.copy(
                            isSaving = false,
                            error = "Failed to revert avatar",
                        )
                    }
                }
            }
        }
    }

    /**
     * Clear the success flag after showing feedback.
     */
    fun clearSaveSuccess() {
        state.update { it.copy(saveSuccess = false) }
    }

    /**
     * Clear error after displaying.
     */
    fun clearError() {
        state.update { it.copy(error = null) }
    }

    companion object {
        const val MAX_TAGLINE_LENGTH = 60
    }
}

/**
 * UI state for the Edit Profile screen.
 */
data class EditProfileUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val user: UserEntity? = null,
    val localAvatarPath: String? = null,
    val editedTagline: String = "",
    val error: String? = null,
    val saveSuccess: Boolean = false,
) {
    val displayName: String get() = user?.displayName ?: ""
    val avatarType: String get() = user?.avatarType ?: "auto"
    val avatarValue: String? get() = user?.avatarValue
    val avatarColor: String get() = user?.avatarColor ?: "#6B7280"
    val currentTagline: String get() = user?.tagline ?: ""
    val hasTaglineChanged: Boolean get() = editedTagline != currentTagline
    val taglineCharCount: Int get() = editedTagline.length
    val hasImageAvatar: Boolean get() = avatarType == "image"
}
