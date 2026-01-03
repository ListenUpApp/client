package com.calypsan.listenup.client.presentation.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.core.currentEpochMilliseconds
import com.calypsan.listenup.client.data.local.db.UserProfileDao
import com.calypsan.listenup.client.data.local.db.UserProfileEntity
import com.calypsan.listenup.client.data.remote.ActivityFeedApiContract
import com.calypsan.listenup.client.data.remote.ActivityResponse
import com.calypsan.listenup.client.data.sync.SSEEventType
import com.calypsan.listenup.client.data.sync.SSEManagerContract
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/** Number of activities to load per page */
private const val PAGE_SIZE = 10

/**
 * ViewModel for the Activity Feed section on the Discover screen.
 *
 * Manages:
 * - Loading activity feed from API
 * - Real-time updates via SSE
 * - Pagination (load more)
 *
 * @property activityFeedApi API client for fetching activity feed
 * @property sseManager SSE manager for real-time activity updates
 */
class ActivityFeedViewModel(
    private val activityFeedApi: ActivityFeedApiContract,
    private val sseManager: SSEManagerContract,
    private val userProfileDao: UserProfileDao,
) : ViewModel() {
    val state: StateFlow<ActivityFeedUiState>
        field = MutableStateFlow(ActivityFeedUiState())

    init {
        loadFeed()
        observeSseEvents()
    }

    /**
     * Observe SSE events for real-time activity updates.
     */
    private fun observeSseEvents() {
        viewModelScope.launch {
            sseManager.eventFlow.collect { event ->
                when (event) {
                    is SSEEventType.ActivityCreated -> {
                        logger.debug { "SSE: Activity created - ${event.type}" }
                        // Prepend the new activity to the list
                        val newActivity = event.toActivityResponse()
                        state.update { current ->
                            current.copy(
                                activities = listOf(newActivity) + current.activities,
                            )
                        }
                    }

                    else -> {
                        // Ignore other events
                    }
                }
            }
        }
    }

    /**
     * Load initial activity feed.
     */
    fun loadFeed() {
        viewModelScope.launch {
            state.update { it.copy(isLoading = true, error = null) }

            try {
                val response = activityFeedApi.getFeed(limit = PAGE_SIZE)

                // Cache user profiles for offline support
                cacheUserProfiles(response.activities)

                state.update {
                    it.copy(
                        isLoading = false,
                        error = null,
                        activities = response.activities,
                        nextCursor = response.nextCursor,
                        hasMore = response.hasMore,
                    )
                }
                logger.debug { "Loaded ${response.activities.size} activities" }
            } catch (e: Exception) {
                val errorMessage = "Failed to load activities: ${e.message}"
                state.update {
                    it.copy(
                        isLoading = false,
                        error = errorMessage,
                    )
                }
                logger.error(e) { "Failed to load activity feed" }
            }
        }
    }

    /**
     * Load more activities (pagination).
     */
    fun loadMore() {
        val currentState = state.value
        if (currentState.isLoadingMore || !currentState.hasMore) return

        viewModelScope.launch {
            state.update { it.copy(isLoadingMore = true) }

            try {
                val response =
                    activityFeedApi.getFeed(
                        limit = PAGE_SIZE,
                        before = currentState.nextCursor,
                    )

                // Cache user profiles for offline support
                cacheUserProfiles(response.activities)

                state.update {
                    it.copy(
                        isLoadingMore = false,
                        activities = it.activities + response.activities,
                        nextCursor = response.nextCursor,
                        hasMore = response.hasMore,
                    )
                }
                logger.debug { "Loaded ${response.activities.size} more activities" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to load more activities" }
                state.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    /**
     * Refresh the activity feed.
     */
    fun refresh() {
        loadFeed()
    }

    /**
     * Cache user profiles from activity responses.
     *
     * Activity API only provides displayName and avatarColor (no avatarType/Value).
     * We use a two-step approach to avoid overwriting existing image avatar data:
     * 1. Insert new profiles with default "auto" avatar type
     * 2. Update existing profiles but preserve their avatarType/avatarValue
     *
     * This ensures that if a profile was previously cached with full avatar data
     * (from SSE ProfileUpdated or CurrentlyListening API), we don't lose it.
     */
    private suspend fun cacheUserProfiles(activities: List<ActivityResponse>) {
        if (activities.isEmpty()) return

        val now = currentEpochMilliseconds()
        val uniqueUsers = activities.distinctBy { it.userId }

        for (user in uniqueUsers) {
            // Try to insert if not exists (with defaults for missing avatar data)
            val inserted = userProfileDao.insertIfNotExists(
                userId = user.userId,
                displayName = user.userDisplayName,
                avatarType = "auto",
                avatarValue = null,
                avatarColor = user.userAvatarColor,
                updatedAt = now,
            )

            // If already exists (inserted == -1), update only the partial fields
            if (inserted == -1L) {
                userProfileDao.updatePartial(
                    userId = user.userId,
                    displayName = user.userDisplayName,
                    avatarColor = user.userAvatarColor,
                    updatedAt = now,
                )
            }
        }
        logger.debug { "Cached ${uniqueUsers.size} user profiles from activity feed" }
    }
}

/**
 * Convert SSE event to ActivityResponse for display.
 */
private fun SSEEventType.ActivityCreated.toActivityResponse(): ActivityResponse =
    ActivityResponse(
        id = id,
        userId = userId,
        type = type,
        createdAt = createdAt,
        userDisplayName = userDisplayName,
        userAvatarColor = userAvatarColor,
        bookId = bookId,
        bookTitle = bookTitle,
        bookAuthorName = bookAuthorName,
        bookCoverPath = bookCoverPath,
        isReread = isReread,
        durationMs = durationMs,
        milestoneValue = milestoneValue,
        milestoneUnit = milestoneUnit,
        lensId = lensId,
        lensName = lensName,
    )

/**
 * UI state for the activity feed section.
 */
data class ActivityFeedUiState(
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val activities: List<ActivityResponse> = emptyList(),
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
) {
    /**
     * Whether there is data to display.
     */
    val hasData: Boolean
        get() = activities.isNotEmpty()

    /**
     * Whether the feed is empty (after loading).
     */
    val isEmpty: Boolean
        get() = !isLoading && activities.isEmpty() && error == null
}
