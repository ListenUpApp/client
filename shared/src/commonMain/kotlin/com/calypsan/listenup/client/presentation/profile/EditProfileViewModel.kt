package com.calypsan.listenup.client.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.domain.model.User
import com.calypsan.listenup.client.domain.repository.ImageRepository
import com.calypsan.listenup.client.domain.repository.ProfileEditRepository
import com.calypsan.listenup.client.domain.repository.UserRepository
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
    private val profileEditRepository: ProfileEditRepository,
    private val userRepository: UserRepository,
    private val imageRepository: ImageRepository,
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
     * - Any other sync updates the User
     *
     * NO server fetch - everything from local cache.
     */
    private fun observeUser() {
        viewModelScope.launch {
            userRepository.observeCurrentUser().collect { user ->
                if (user != null) {
                    val currentState = state.value

                    // Get local avatar path if it exists
                    val localAvatarPath =
                        if (user.avatarType == "image" && imageRepository.userAvatarExists(user.id.value)) {
                            imageRepository.getUserAvatarPath(user.id.value)
                        } else {
                            null
                        }

                    state.update {
                        it.copy(
                            isLoading = false,
                            user = user,
                            localAvatarPath = localAvatarPath,
                            // Only set edited fields from DB on first load
                            editedTagline =
                                if (currentState.user == null) {
                                    user.tagline ?: ""
                                } else {
                                    currentState.editedTagline
                                },
                            editedFirstName =
                                if (currentState.user == null) {
                                    user.firstName ?: ""
                                } else {
                                    currentState.editedFirstName
                                },
                            editedLastName =
                                if (currentState.user == null) {
                                    user.lastName ?: ""
                                } else {
                                    currentState.editedLastName
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

            if (profileEditRepository.updateTagline(editedTagline.ifEmpty { null }) is Success) {
                // Local state will update via observeUser() when DB is updated
                state.update {
                    it.copy(
                        isSaving = false,
                        saveSuccess = true,
                    )
                }
                logger.info { "Tagline update queued for sync" }
            } else {
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

    /**
     * Upload an avatar image using offline-first pattern.
     */
    fun uploadAvatar(
        imageData: ByteArray,
        contentType: String,
    ) {
        viewModelScope.launch {
            state.update { it.copy(isSaving = true, error = null) }

            if (profileEditRepository.uploadAvatar(imageData, contentType) is Success) {
                // Don't update avatar state here - we don't have the server path yet.
                // The current avatar continues to display until:
                // 1. ProfileAvatarHandler successfully uploads the image
                // 2. Handler updates user data with the server's response
                // 3. observeUser() sees the change and updates UI
                state.update {
                    it.copy(
                        isSaving = false,
                        saveSuccess = true,
                    )
                }
                logger.info { "Avatar upload queued for sync" }
            } else {
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

    /**
     * Revert to auto-generated avatar using offline-first pattern.
     */
    fun revertToAutoAvatar() {
        viewModelScope.launch {
            state.update { it.copy(isSaving = true, error = null) }

            if (profileEditRepository.revertToAutoAvatar() is Success) {
                // Local state will update via observeUser() when DB is updated
                state.update {
                    it.copy(
                        isSaving = false,
                        saveSuccess = true,
                    )
                }
                logger.info { "Revert to auto avatar queued for sync" }
            } else {
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

    /**
     * Update the first name input field.
     */
    fun onFirstNameChange(newFirstName: String) {
        state.update { it.copy(editedFirstName = newFirstName) }
    }

    /**
     * Update the last name input field.
     */
    fun onLastNameChange(newLastName: String) {
        state.update { it.copy(editedLastName = newLastName) }
    }

    /**
     * Save the updated name using offline-first pattern.
     */
    fun saveName() {
        val firstName = state.value.editedFirstName
        val lastName = state.value.editedLastName

        // Skip if both are empty
        if (firstName.isBlank() && lastName.isBlank()) return

        viewModelScope.launch {
            state.update { it.copy(isSaving = true, error = null) }

            if (profileEditRepository.updateName(firstName, lastName) is Success) {
                // DB is updated optimistically, observeUser() will receive the update
                state.update {
                    it.copy(
                        isSaving = false,
                        saveSuccess = true,
                    )
                }
                logger.info { "Name update saved" }
            } else {
                logger.error { "Failed to save name" }
                state.update {
                    it.copy(
                        isSaving = false,
                        error = "Failed to save name",
                    )
                }
            }
        }
    }

    /**
     * Update the new password input field.
     */
    fun onNewPasswordChange(newPassword: String) {
        state.update { it.copy(newPassword = newPassword) }
    }

    /**
     * Update the confirm password input field.
     */
    fun onConfirmPasswordChange(confirmPassword: String) {
        state.update { it.copy(confirmPassword = confirmPassword) }
    }

    /**
     * Change the user's password.
     * Requires server confirmation - not offline-first.
     */
    fun changePassword() {
        val newPassword = state.value.newPassword
        val confirmPassword = state.value.confirmPassword

        // Validate
        if (newPassword.length < 8) {
            state.update { it.copy(error = "Password must be at least 8 characters") }
            return
        }
        if (newPassword != confirmPassword) {
            state.update { it.copy(error = "Passwords do not match") }
            return
        }

        viewModelScope.launch {
            state.update { it.copy(isSaving = true, error = null) }

            when (profileEditRepository.changePassword(newPassword)) {
                is Success -> {
                    state.update {
                        it.copy(
                            isSaving = false,
                            passwordChangeSuccess = true,
                            newPassword = "",
                            confirmPassword = "",
                        )
                    }
                    logger.info { "Password changed successfully" }
                }

                is Failure -> {
                    logger.error { "Failed to change password" }
                    state.update {
                        it.copy(
                            isSaving = false,
                            error = "Failed to change password",
                        )
                    }
                }
            }
        }
    }

    /**
     * Clear the password change success flag after showing feedback.
     */
    fun clearPasswordChangeSuccess() {
        state.update { it.copy(passwordChangeSuccess = false) }
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
    val user: User? = null,
    val localAvatarPath: String? = null,
    val editedTagline: String = "",
    val editedFirstName: String = "",
    val editedLastName: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val error: String? = null,
    val saveSuccess: Boolean = false,
    val passwordChangeSuccess: Boolean = false,
) {
    val displayName: String get() = user?.displayName ?: ""
    val avatarType: String get() = user?.avatarType ?: "auto"
    val avatarValue: String? get() = user?.avatarValue
    val avatarColor: String get() = user?.avatarColor ?: "#6B7280"
    val currentTagline: String get() = user?.tagline ?: ""
    val hasTaglineChanged: Boolean get() = editedTagline != currentTagline
    val taglineCharCount: Int get() = editedTagline.length
    val hasImageAvatar: Boolean get() = avatarType == "image"
    val currentFirstName: String get() = user?.firstName ?: ""
    val currentLastName: String get() = user?.lastName ?: ""
    val hasNameChanged: Boolean
        get() = editedFirstName != currentFirstName || editedLastName != currentLastName
    val passwordsMatch: Boolean get() = newPassword == confirmPassword
    val isPasswordValid: Boolean get() = newPassword.length >= 8
    val canSavePassword: Boolean get() = newPassword.isNotEmpty() && passwordsMatch && isPasswordValid
}
