package com.calypsan.listenup.client.presentation.contributormetadata

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.core.Result as CoreResult
import com.calypsan.listenup.client.data.local.db.ContributorDao
import com.calypsan.listenup.client.data.local.db.ContributorEntity
import com.calypsan.listenup.client.data.local.images.ImageStorage
import com.calypsan.listenup.client.data.remote.ApplyContributorMetadataResult
import com.calypsan.listenup.client.data.remote.ImageApiContract
import com.calypsan.listenup.client.data.remote.MetadataApiContract
import com.calypsan.listenup.client.data.remote.model.ContributorMetadataProfile
import com.calypsan.listenup.client.data.remote.model.ContributorMetadataSearchResult
import com.calypsan.listenup.client.data.remote.model.toEntity
import com.calypsan.listenup.client.presentation.metadata.AudibleRegion
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * Tracks which contributor metadata fields the user has selected to apply.
 *
 * Note: Currently the server applies all fields. These selections are for
 * UI preview purposes and future field-level selection support.
 */
data class ContributorMetadataSelections(
    val name: Boolean = true,
    val biography: Boolean = true,
    val image: Boolean = true,
)

/**
 * Toggleable fields for contributor metadata.
 */
enum class ContributorMetadataField {
    NAME,
    BIOGRAPHY,
    IMAGE,
}

/**
 * UI state for the contributor metadata search and match flow.
 */
data class ContributorMetadataUiState(
    // Contributor context
    val contributorId: String = "",
    val currentContributor: ContributorEntity? = null,

    // Region selection
    val selectedRegion: AudibleRegion = AudibleRegion.US,

    // Search state
    val searchQuery: String = "",
    val searchResults: List<ContributorMetadataSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val searchError: String? = null,

    // Preview state
    val selectedCandidate: ContributorMetadataSearchResult? = null,
    val previewProfile: ContributorMetadataProfile? = null,
    val isLoadingPreview: Boolean = false,
    val previewError: String? = null,

    // Field selections (for UI preview)
    val selections: ContributorMetadataSelections = ContributorMetadataSelections(),

    // Apply state
    val isApplying: Boolean = false,
    val applySuccess: Boolean = false,
    val applyError: String? = null,
)

/**
 * ViewModel for the contributor metadata search and match flow.
 *
 * Manages the full flow:
 * 1. Search Audible for contributor matches
 * 2. Select a match and preview the changes
 * 3. Apply the match to update the contributor via server API
 */
class ContributorMetadataViewModel(
    private val contributorDao: ContributorDao,
    private val metadataApi: MetadataApiContract,
    private val imageApi: ImageApiContract,
    private val imageStorage: ImageStorage,
) : ViewModel() {
    val state: StateFlow<ContributorMetadataUiState>
        field = MutableStateFlow(ContributorMetadataUiState())

    /**
     * Initialize the ViewModel for a specific contributor.
     *
     * Performs a synchronous state reset first to prevent stale state from being
     * visible during navigation transitions, then loads contributor data async.
     */
    fun init(contributorId: String) {
        // Synchronous reset - prevents stale state (e.g., applySuccess=true from
        // a previous contributor) from triggering side effects before async load
        state.value = ContributorMetadataUiState(contributorId = contributorId)

        viewModelScope.launch {
            // Load current contributor
            val contributor = contributorDao.observeById(contributorId).first()

            state.update {
                it.copy(
                    currentContributor = contributor,
                    searchQuery = contributor?.name ?: "",
                )
            }

            // Auto-search with the contributor's name
            if (!contributor?.name.isNullOrBlank()) {
                search()
            }
        }
    }

    /**
     * Update the search query.
     */
    fun updateQuery(query: String) {
        state.update { it.copy(searchQuery = query) }
    }

    /**
     * Change the Audible region and re-search.
     */
    fun changeRegion(region: AudibleRegion) {
        state.update { it.copy(selectedRegion = region) }

        // Re-search with new region if we have a query
        if (state.value.searchQuery.isNotBlank()) {
            search()
        }
    }

    /**
     * Execute an Audible search with the current query.
     */
    fun search() {
        val query = state.value.searchQuery.trim()
        if (query.isBlank()) return

        viewModelScope.launch {
            state.update {
                it.copy(
                    isSearching = true,
                    searchError = null,
                )
            }

            try {
                val region = state.value.selectedRegion.code
                val results = metadataApi.searchContributors(query, region)
                logger.debug { "Contributor search for '$query' in $region returned ${results.size} results" }

                state.update {
                    it.copy(
                        searchResults = results,
                        isSearching = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error(e) { "Contributor metadata search failed" }
                state.update {
                    it.copy(
                        isSearching = false,
                        searchError = e.message ?: "Search failed",
                    )
                }
            }
        }
    }

    /**
     * Select a candidate from search results and load its full profile.
     */
    fun selectCandidate(result: ContributorMetadataSearchResult) {
        state.update {
            it.copy(
                selectedCandidate = result,
                isLoadingPreview = true,
                previewProfile = null,
                previewError = null,
            )
        }

        viewModelScope.launch {
            try {
                val profile = metadataApi.getContributorProfile(result.asin)
                logger.debug { "Loaded contributor profile for ${result.asin}: ${profile.name}" }

                // Initialize selections based on available data
                val selections = initializeSelections(profile)

                state.update {
                    it.copy(
                        previewProfile = profile,
                        isLoadingPreview = false,
                        selections = selections,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error(e) { "Failed to load contributor profile" }
                state.update {
                    it.copy(
                        isLoadingPreview = false,
                        previewError = e.message ?: "Failed to load profile",
                    )
                }
            }
        }
    }

    /**
     * Load a profile directly by ASIN (for returning to preview from search).
     */
    fun loadProfileByAsin(asin: String) {
        state.update {
            it.copy(
                selectedCandidate = ContributorMetadataSearchResult(
                    asin = asin,
                    name = "",
                    imageUrl = null,
                    description = null,
                ),
                isLoadingPreview = true,
                previewProfile = null,
                previewError = null,
            )
        }

        viewModelScope.launch {
            try {
                val profile = metadataApi.getContributorProfile(asin)
                logger.debug { "Loaded contributor profile by ASIN $asin: ${profile.name}" }

                val selections = initializeSelections(profile)

                state.update {
                    it.copy(
                        selectedCandidate = ContributorMetadataSearchResult(
                            asin = asin,
                            name = profile.name,
                            imageUrl = profile.imageUrl,
                            description = null,
                        ),
                        previewProfile = profile,
                        isLoadingPreview = false,
                        selections = selections,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error(e) { "Failed to load contributor profile by ASIN" }
                state.update {
                    it.copy(
                        isLoadingPreview = false,
                        previewError = e.message ?: "Failed to load profile",
                    )
                }
            }
        }
    }

    /**
     * Clear selection and return to search results.
     */
    fun clearSelection() {
        state.update {
            it.copy(
                selectedCandidate = null,
                previewProfile = null,
                previewError = null,
            )
        }
    }

    /**
     * Toggle a field selection.
     *
     * Note: Currently for UI preview only. The server applies all fields.
     */
    fun toggleField(field: ContributorMetadataField) {
        state.update { currentState ->
            val selections = currentState.selections
            val newSelections = when (field) {
                ContributorMetadataField.NAME -> selections.copy(name = !selections.name)
                ContributorMetadataField.BIOGRAPHY -> selections.copy(biography = !selections.biography)
                ContributorMetadataField.IMAGE -> selections.copy(image = !selections.image)
            }
            currentState.copy(selections = newSelections)
        }
    }

    /**
     * Check if any field is selected.
     */
    fun hasSelectedFields(): Boolean {
        val selections = state.value.selections
        return selections.name || selections.biography || selections.image
    }

    /**
     * Apply the selected metadata to the contributor via server API.
     *
     * The server handles downloading the image and updating the contributor.
     * Changes will sync back to the client automatically.
     */
    fun apply() {
        val currentState = state.value
        val contributorId = currentState.contributorId
        val candidate = currentState.selectedCandidate ?: return
        val contributor = currentState.currentContributor

        if (!hasSelectedFields()) return

        viewModelScope.launch {
            state.update {
                it.copy(
                    isApplying = true,
                    applyError = null,
                )
            }

            try {
                // Call server API to apply metadata with the selected ASIN
                // Pass imageUrl from search results since Audible API no longer returns images
                val result = metadataApi.applyContributorMetadata(
                    contributorId = contributorId,
                    asin = candidate.asin,
                    name = contributor?.name,
                    imageUrl = candidate.imageUrl,
                )

                when (result) {
                    is ApplyContributorMetadataResult.Success -> {
                        // Update local database with the server's response
                        var updatedEntity = result.contributor.toEntity()

                        // Download contributor image from server and save locally
                        // Note: toEntity() sets imagePath=null since server returns a URL, not local path
                        if (result.contributor.imageUrl != null) {
                            val downloadResult = imageApi.downloadContributorImage(contributorId)
                            if (downloadResult is CoreResult.Success) {
                                val saveResult = imageStorage.saveContributorImage(contributorId, downloadResult.data)
                                if (saveResult is CoreResult.Success) {
                                    updatedEntity = updatedEntity.copy(
                                        imagePath = imageStorage.getContributorImagePath(contributorId),
                                    )
                                    logger.debug { "Downloaded and saved contributor image locally" }
                                } else {
                                    logger.warn { "Failed to save contributor image: ${(saveResult as CoreResult.Failure).message}" }
                                }
                            } else {
                                logger.warn { "Failed to download contributor image: ${(downloadResult as CoreResult.Failure).message}" }
                            }
                        }

                        contributorDao.upsert(updatedEntity)
                        logger.info { "Applied Audible metadata to contributor $contributorId" }
                        state.update {
                            it.copy(
                                isApplying = false,
                                applySuccess = true,
                                currentContributor = updatedEntity,
                            )
                        }
                    }

                    is ApplyContributorMetadataResult.NeedsDisambiguation -> {
                        // Shouldn't happen since we're providing an ASIN
                        logger.warn { "Unexpected disambiguation when ASIN was provided" }
                        state.update {
                            it.copy(
                                isApplying = false,
                                applyError = "Unexpected disambiguation request",
                            )
                        }
                    }

                    is ApplyContributorMetadataResult.Error -> {
                        logger.warn { "Failed to apply metadata: ${result.message}" }
                        state.update {
                            it.copy(
                                isApplying = false,
                                applyError = result.message,
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error(e) { "Failed to apply contributor metadata" }
                state.update {
                    it.copy(
                        isApplying = false,
                        applyError = e.message ?: "Failed to apply metadata",
                    )
                }
            }
        }
    }

    /**
     * Reset all state.
     */
    fun reset() {
        state.update { ContributorMetadataUiState() }
    }

    /**
     * Initialize selections based on available profile data.
     */
    private fun initializeSelections(profile: ContributorMetadataProfile): ContributorMetadataSelections {
        return ContributorMetadataSelections(
            name = profile.name.isNotBlank(),
            biography = !profile.biography.isNullOrBlank(),
            image = !profile.imageUrl.isNullOrBlank(),
        )
    }
}
