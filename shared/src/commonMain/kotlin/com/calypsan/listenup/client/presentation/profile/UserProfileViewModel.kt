package com.calypsan.listenup.client.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.domain.model.ProfileLensSummary
import com.calypsan.listenup.client.domain.model.ProfileRecentBook
import com.calypsan.listenup.client.domain.model.User
import com.calypsan.listenup.client.domain.repository.ImageRepository
import com.calypsan.listenup.client.domain.repository.UserRepository
import com.calypsan.listenup.client.domain.usecase.profile.LoadUserProfileUseCase
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * ViewModel for the User Profile screen.
 *
 * Uses offline-first pattern for OWN profile:
 * - ALL data from local cache - NO server fetch
 * - Stats show 0 until local stats computation is implemented
 *
 * For OTHER users' profiles:
 * - Everything from server (we don't cache other users)
 */
class UserProfileViewModel(
    private val loadUserProfileUseCase: LoadUserProfileUseCase,
    private val userRepository: UserRepository,
    private val imageRepository: ImageRepository,
) : ViewModel() {
    val state: StateFlow<UserProfileUiState>
        field = MutableStateFlow(UserProfileUiState())

    private var currentUserId: String? = null
    private var isOwnProfileFlag: Boolean = false

    /**
     * Load full profile for a user.
     *
     * For own profile: Local cache ONLY - no server fetch
     * For other users: Fetches everything from server
     */
    fun loadProfile(
        userId: String,
        forceRefresh: Boolean = false,
    ) {
        if (!forceRefresh && userId == currentUserId && !state.value.isLoading && state.value.hasData) {
            return // Already loaded
        }

        currentUserId = userId

        viewModelScope.launch {
            state.update { it.copy(isLoading = true, error = null) }

            // Check if this is own profile
            val localUser = userRepository.getCurrentUser()
            isOwnProfileFlag = localUser?.id == userId

            if (isOwnProfileFlag && localUser != null) {
                // OWN PROFILE: Local cache ONLY - NO SERVER FETCH
                loadOwnProfileFromCache(localUser)
            } else {
                // OTHER USER: Fetch everything from server
                loadOtherProfile(userId)
            }
        }
    }

    /**
     * Load own profile from local cache ONLY.
     * NO server fetch - true offline-first.
     */
    private fun loadOwnProfileFromCache(localUser: User) {
        logger.info { "Loading own profile from LOCAL CACHE ONLY (no server fetch)" }

        // Start observing local user for reactive updates
        observeLocalUser()

        // Get local avatar path if it exists
        val localAvatarPath =
            if (localUser.avatarType == "image" && imageRepository.userAvatarExists(localUser.id)) {
                imageRepository.getUserAvatarPath(localUser.id)
            } else {
                null
            }

        // Set state from local cache immediately - NO server call
        state.update {
            it.copy(
                isLoading = false,
                isOwnProfile = true,
                localUser = localUser,
                localAvatarPath = localAvatarPath,
                // Stats default to 0 - can be computed locally from ListeningEventEntity later
                totalListenTimeMs = 0,
                booksFinished = 0,
                currentStreak = 0,
                longestStreak = 0,
                recentBooks = emptyList(),
                publicLenses = emptyList(),
                error = null,
            )
        }

        logger.info {
            "Own profile loaded from cache: ${localUser.displayName}, avatar=${localUser.avatarType}/${localUser.avatarValue}, localPath=$localAvatarPath"
        }
    }

    /**
     * Observe local User for reactive updates to own profile.
     */
    private fun observeLocalUser() {
        viewModelScope.launch {
            userRepository.observeCurrentUser().collect { user ->
                if (user != null && isOwnProfileFlag) {
                    // Update local avatar path if avatar type changed
                    val localAvatarPath =
                        if (user.avatarType == "image" && imageRepository.userAvatarExists(user.id)) {
                            imageRepository.getUserAvatarPath(user.id)
                        } else {
                            null
                        }

                    state.update {
                        it.copy(
                            localUser = user,
                            localAvatarPath = localAvatarPath,
                        )
                    }
                    logger.info {
                        "Own profile UPDATED from local cache: avatar=${user.avatarType}/${user.avatarValue}, localPath=$localAvatarPath"
                    }
                }
            }
        }
    }

    /**
     * Load other user's profile: everything from server.
     * Downloads and caches the user's avatar locally for offline access.
     */
    private suspend fun loadOtherProfile(userId: String) {
        logger.debug { "Loading other user's profile from server" }

        when (val profileResult = loadUserProfileUseCase(userId)) {
            is Success -> {
                val profile = profileResult.data

                // Map books to use local cover paths
                val booksWithLocalCovers =
                    profile.recentBooks.map { book ->
                        val bookId = BookId(book.bookId)
                        val localCoverPath =
                            if (imageRepository.bookCoverExists(bookId)) {
                                imageRepository.getBookCoverPath(bookId)
                            } else {
                                null
                            }
                        book.copy(coverPath = localCoverPath)
                    }

                // Download and cache avatar locally for offline access if user has custom avatar
                var localAvatarPath: String? = null
                if (profile.avatarType == "image" && profile.avatarValue != null) {
                    // Check if already cached locally
                    localAvatarPath =
                        if (imageRepository.userAvatarExists(profile.userId)) {
                            imageRepository.getUserAvatarPath(profile.userId)
                        } else {
                            // Download and cache it
                            viewModelScope.launch {
                                try {
                                    imageRepository.downloadUserAvatar(profile.userId, forceRefresh = false)
                                    // Update state with local path after download
                                    state.update {
                                        it.copy(
                                            localAvatarPath = imageRepository.getUserAvatarPath(profile.userId),
                                        )
                                    }
                                    logger.info { "Downloaded and cached avatar for user ${profile.userId}" }
                                } catch (e: Exception) {
                                    logger.warn(e) { "Failed to download avatar for user ${profile.userId}" }
                                }
                            }
                            null // Will be set after download completes
                        }
                }

                // For other users, set all data from server
                state.update {
                    it.copy(
                        isLoading = false,
                        isOwnProfile = false,
                        localAvatarPath = localAvatarPath,
                        // Profile data from server
                        serverDisplayName = profile.displayName,
                        serverAvatarType = profile.avatarType,
                        serverAvatarValue = profile.avatarValue,
                        serverAvatarColor = profile.avatarColor,
                        serverTagline = profile.tagline,
                        serverUserId = profile.userId,
                        // Stats from server
                        totalListenTimeMs = profile.totalListenTimeMs,
                        booksFinished = profile.booksFinished,
                        currentStreak = profile.currentStreak,
                        longestStreak = profile.longestStreak,
                        recentBooks = booksWithLocalCovers,
                        publicLenses = profile.publicLenses,
                        error = null,
                    )
                }
                logger.debug { "Loaded other user's profile: ${profile.displayName}" }
            }

            else -> {
                logger.error { "Failed to load profile for: $userId" }
                state.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load profile",
                    )
                }
            }
        }
    }

    /**
     * Refresh the current profile.
     */
    fun refresh() {
        currentUserId?.let { userId ->
            currentUserId = null // Force reload
            loadProfile(userId)
        }
    }

    /**
     * Format milliseconds to human-readable string (e.g., "42h 30m").
     */
    fun formatListenTime(totalMs: Long): String {
        val hours = totalMs / (1000 * 60 * 60)
        val minutes = (totalMs / (1000 * 60)) % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "0m"
        }
    }
}

/**
 * UI state for the User Profile screen.
 */
data class UserProfileUiState(
    val isLoading: Boolean = true,
    val isOwnProfile: Boolean = false,
    val error: String? = null,
    // For OWN profile: local cache for profile data
    val localUser: User? = null,
    // Local file path for own profile's avatar image (loaded from local storage, not server)
    val localAvatarPath: String? = null,
    // For OTHER users: server data for profile
    val serverUserId: String = "",
    val serverDisplayName: String = "",
    val serverAvatarType: String = "auto",
    val serverAvatarValue: String? = null,
    val serverAvatarColor: String = "#6B7280",
    val serverTagline: String? = null,
    // Stats
    val totalListenTimeMs: Long = 0,
    val booksFinished: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val recentBooks: List<ProfileRecentBook> = emptyList(),
    val publicLenses: List<ProfileLensSummary> = emptyList(),
) {
    val hasData: Boolean get() = if (isOwnProfile) localUser != null else serverDisplayName.isNotEmpty()

    // Profile data: from local cache for own, from server fields for others
    val userId: String get() = if (isOwnProfile) localUser?.id ?: "" else serverUserId
    val displayName: String get() = if (isOwnProfile) localUser?.displayName ?: "" else serverDisplayName
    val avatarType: String get() = if (isOwnProfile) localUser?.avatarType ?: "auto" else serverAvatarType
    val avatarValue: String? get() = if (isOwnProfile) localUser?.avatarValue else serverAvatarValue
    val avatarColor: String get() = if (isOwnProfile) localUser?.avatarColor ?: "#6B7280" else serverAvatarColor
    val tagline: String? get() = if (isOwnProfile) localUser?.tagline else serverTagline

    // Cache buster for avatar images - use updatedAtMs timestamp to force Coil refresh
    val avatarCacheBuster: Long get() = localUser?.updatedAtMs ?: 0
}
