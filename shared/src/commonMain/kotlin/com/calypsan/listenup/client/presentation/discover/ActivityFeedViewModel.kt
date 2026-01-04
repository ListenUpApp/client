package com.calypsan.listenup.client.presentation.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.core.currentEpochMilliseconds
import com.calypsan.listenup.client.data.local.db.ActivityDao
import com.calypsan.listenup.client.data.local.db.ActivityEntity
import com.calypsan.listenup.client.data.remote.ActivityFeedApiContract
import com.calypsan.listenup.client.data.remote.ActivityResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant

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
 * @property activityDao DAO for activity feed operations
 * @property activityFeedApi API for fetching initial activities
 */
class ActivityFeedViewModel(
    private val activityDao: ActivityDao,
    private val activityFeedApi: ActivityFeedApiContract,
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
        activityDao
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
            val existingCount = activityDao.count()
            if (existingCount > 0) {
                logger.debug { "Room has $existingCount activities, skipping initial fetch" }
                return@launch
            }

            logger.debug { "Room is empty, fetching initial activities from API" }
            try {
                val response = activityFeedApi.getFeed(limit = INITIAL_FETCH_SIZE)
                val entities = response.activities.map { it.toEntity() }
                activityDao.upsertAll(entities)
                logger.info { "Fetched and stored ${entities.size} initial activities" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to fetch initial activities" }
                // Not fatal - Room Flow will show empty state, SSE will populate over time
            }
        }
    }

    /**
     * Refresh activities from API.
     * Fetches latest activities and stores in Room.
     */
    fun refresh() {
        viewModelScope.launch {
            try {
                val response = activityFeedApi.getFeed(limit = INITIAL_FETCH_SIZE)
                val entities = response.activities.map { it.toEntity() }
                activityDao.upsertAll(entities)
                logger.debug { "Refreshed ${entities.size} activities from API" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to refresh activities" }
            }
        }
    }
}

/**
 * Convert API response to Room entity.
 */
private fun ActivityResponse.toEntity(): ActivityEntity {
    // Parse ISO timestamp to epoch milliseconds
    val createdAtMs =
        try {
            Instant.parse(createdAt).toEpochMilliseconds()
        } catch (e: Exception) {
            currentEpochMilliseconds()
        }

    return ActivityEntity(
        id = id,
        userId = userId,
        type = type,
        createdAt = createdAtMs,
        userDisplayName = userDisplayName,
        userAvatarColor = userAvatarColor,
        userAvatarType = userAvatarType,
        userAvatarValue = userAvatarValue,
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
}

/**
 * Convert ActivityEntity to UI model.
 */
private fun ActivityEntity.toUiModel(): ActivityUiModel =
    ActivityUiModel(
        id = id,
        userId = userId,
        type = type,
        createdAt = createdAt,
        userDisplayName = userDisplayName,
        userAvatarColor = userAvatarColor,
        userAvatarType = userAvatarType,
        userAvatarValue = userAvatarValue,
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
 * UI model for a single activity in the feed.
 * Matches the fields stored in ActivityEntity.
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
