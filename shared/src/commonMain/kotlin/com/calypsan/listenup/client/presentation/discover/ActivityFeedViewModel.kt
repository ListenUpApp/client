package com.calypsan.listenup.client.presentation.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.domain.model.Activity
import com.calypsan.listenup.client.domain.repository.ActivityRepository
import com.calypsan.listenup.client.domain.usecase.activity.FetchActivitiesUseCase
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/** Number of activities to fetch on initial load */
private const val INITIAL_FETCH_SIZE = 50

/** Maximum activities to observe from Room (avoids loading entire history) */
private const val MAX_ACTIVITIES = 100

/**
 * ViewModel for the Activity Feed section on the Discover screen.
 *
 * Offline-first architecture:
 * - On first launch, fetches initial activities from API and stores in Room
 * - UI observes Room Flow and automatically updates
 * - SSE events are processed by SSEEventProcessor which stores new activities in Room
 * - After initial fetch, works completely offline
 *
 * @property activityRepository Repository for activity feed operations
 * @property fetchActivitiesUseCase Use case for fetching activities from API
 */
class ActivityFeedViewModel(
    private val activityRepository: ActivityRepository,
    private val fetchActivitiesUseCase: FetchActivitiesUseCase,
) : ViewModel() {
    init {
        // Fetch initial activities if Room is empty
        fetchInitialActivitiesIfNeeded()
    }

    /**
     * Observe recent activities from Room.
     * Automatically updates when SSE events add new activities.
     */
    val state: StateFlow<ActivityFeedUiState> =
        activityRepository
            .observeRecent(limit = MAX_ACTIVITIES)
            .map { activities ->
                ActivityFeedUiState(
                    isLoading = false,
                    activities = activities.map { it.toUiModel() },
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = ActivityFeedUiState(isLoading = true),
            )

    /**
     * Fetch initial activities from API if Room is empty.
     * This ensures data is available on first launch before any SSE events arrive.
     */
    private fun fetchInitialActivitiesIfNeeded() {
        viewModelScope.launch {
            val existingCount = activityRepository.count()
            if (existingCount > 0) {
                logger.debug { "Room has $existingCount activities, skipping initial fetch" }
                return@launch
            }

            logger.debug { "Room is empty, fetching initial activities from API" }
            fetchActivitiesUseCase(limit = INITIAL_FETCH_SIZE)
            // Not fatal if fails - Room Flow will show empty state, SSE will populate over time
        }
    }

    /**
     * Refresh activities from API.
     * Fetches latest activities and stores in Room.
     */
    fun refresh() {
        viewModelScope.launch {
            fetchActivitiesUseCase(limit = INITIAL_FETCH_SIZE)
        }
    }
}

/**
 * Convert Activity domain model to UI model.
 */
private fun Activity.toUiModel(): ActivityUiModel =
    ActivityUiModel(
        id = id,
        userId = userId,
        type = type,
        createdAt = createdAtMs,
        userDisplayName = user.displayName,
        userAvatarColor = user.avatarColor,
        userAvatarType = user.avatarType,
        userAvatarValue = user.avatarValue,
        bookId = book?.id,
        bookTitle = book?.title,
        bookAuthorName = book?.authorName,
        bookCoverPath = book?.coverPath,
        isReread = isReread,
        durationMs = durationMs,
        milestoneValue = milestoneValue,
        milestoneUnit = milestoneUnit,
        lensId = lensId,
        lensName = lensName,
    )

/**
 * UI model for a single activity in the feed.
 * Flattened representation of Activity domain model for UI consumption.
 */
data class ActivityUiModel(
    val id: String,
    val userId: String,
    val type: String,
    val createdAt: Long,
    val userDisplayName: String,
    val userAvatarColor: String,
    val userAvatarType: String,
    val userAvatarValue: String?,
    val bookId: String?,
    val bookTitle: String?,
    val bookAuthorName: String?,
    val bookCoverPath: String?,
    val isReread: Boolean,
    val durationMs: Long,
    val milestoneValue: Int,
    val milestoneUnit: String?,
    val lensId: String?,
    val lensName: String?,
)

/**
 * UI state for the activity feed section.
 * Data comes from Room - no network errors possible after initial fetch.
 */
data class ActivityFeedUiState(
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val activities: List<ActivityUiModel> = emptyList(),
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
        get() = !isLoading && activities.isEmpty()
}
