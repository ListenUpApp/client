@file:Suppress("TooManyFunctions")

package com.calypsan.listenup.client.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.FileSource
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.core.error.ErrorBus
import com.calypsan.listenup.client.data.remote.ABSImportApiContract
import com.calypsan.listenup.client.data.remote.BackupApiContract
import com.calypsan.listenup.client.data.remote.DirectoryEntryResponse
import com.calypsan.listenup.client.data.remote.SearchApiContract
import com.calypsan.listenup.client.data.remote.SearchHitResponse
import com.calypsan.listenup.client.data.remote.UserSearchResult
import com.calypsan.listenup.client.data.remote.model.ABSBookMatch
import com.calypsan.listenup.client.data.remote.model.ABSUserMatch
import com.calypsan.listenup.client.data.remote.model.AnalyzeABSRequest
import com.calypsan.listenup.client.data.remote.model.ImportABSRequest
import com.calypsan.listenup.client.domain.repository.SyncRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * Source type for the ABS backup file.
 */
enum class ABSSourceType {
    /** File from user's device (phone/tablet) */
    LOCAL,

    /** File already on the server */
    REMOTE,
}

/**
 * Step in the ABS import wizard.
 */
enum class ABSImportStep {
    /** Choose between local file or server file */
    SOURCE_SELECTION,

    /** Browsing server filesystem (remote mode) */
    FILE_BROWSER,

    /** Uploading local file to server */
    UPLOADING,

    /** Analyzing the backup */
    ANALYZING,

    /** Mapping ABS users to ListenUp users */
    USER_MAPPING,

    /** Mapping ABS books to ListenUp books */
    BOOK_MAPPING,

    /** Configuring import options */
    IMPORT_OPTIONS,

    /** Import in progress */
    IMPORTING,

    /** Import complete, showing results */
    RESULTS,
}

/**
 * Tab selection for user mapping step.
 */
enum class UserMappingTab {
    /** Users that need manual mapping */
    NEEDS_REVIEW,

    /** Auto-matched users for verification */
    AUTO_MATCHED,
}

/**
 * Tab selection for book mapping step.
 */
enum class BookMappingTab {
    /** Books that need manual mapping */
    NEEDS_REVIEW,

    /** Auto-matched books for verification */
    AUTO_MATCHED,
}

/**
 * Display information for a selected user mapping.
 */
data class SelectedUserDisplay(
    val userId: String,
    val email: String,
    val displayName: String? = null,
)

/**
 * Display information for a selected book mapping.
 */
data class SelectedBookDisplay(
    val bookId: String,
    val title: String,
    val author: String? = null,
    val durationMs: Long? = null,
)

/**
 * UI state for ABS import flow.
 */
data class ABSImportState(
    val step: ABSImportStep = ABSImportStep.SOURCE_SELECTION,
    val sourceType: ABSSourceType? = null,
    // Local file state
    val selectedLocalFile: SelectedLocalFile? = null,
    val isUploading: Boolean = false,
    val uploadProgress: Float = 0f,
    // Remote file browser state
    val currentPath: String = "/",
    val parentPath: String? = null,
    val directories: List<DirectoryEntryResponse> = emptyList(),
    val isLoadingDirectories: Boolean = false,
    val isRoot: Boolean = true,
    val selectedRemotePath: String = "",
    // After file is selected/uploaded
    val backupPath: String = "",
    val isAnalyzing: Boolean = false,
    val analysisComplete: Boolean = false,
    val analyzePhase: String = "",
    val analyzeCurrent: Int = 0,
    val analyzeTotal: Int = 0,
    // Analysis results
    val summary: String = "",
    val totalUsers: Int = 0,
    val totalBooks: Int = 0,
    val totalSessions: Int = 0,
    val usersMatched: Int = 0,
    val usersPending: Int = 0,
    val booksMatched: Int = 0,
    val booksPending: Int = 0,
    val sessionsReady: Int = 0,
    val sessionsPending: Int = 0,
    val progressReady: Int = 0,
    val progressPending: Int = 0,
    val userMatches: List<ABSUserMatch> = emptyList(),
    val bookMatches: List<ABSBookMatch> = emptyList(),
    val analysisWarnings: List<String> = emptyList(),
    // User/book mappings - ABS ID -> ListenUp ID
    val userMappings: Map<String, String> = emptyMap(),
    val bookMappings: Map<String, String> = emptyMap(),
    // User mapping UI state
    val userMappingTab: UserMappingTab = UserMappingTab.NEEDS_REVIEW,
    // Display info for selected users (absUserId -> display info)
    val selectedUserDisplays: Map<String, SelectedUserDisplay> = emptyMap(),
    // Inline user search state (for the currently active search field)
    val activeSearchAbsUserId: String? = null,
    val userSearchQuery: String = "",
    val userSearchResults: List<UserSearchResult> = emptyList(),
    val isSearchingUsers: Boolean = false,
    // ID of the user result item currently being processed (shows loading spinner)
    val loadingUserItemId: String? = null,
    // Book mapping UI state
    val bookMappingTab: BookMappingTab = BookMappingTab.NEEDS_REVIEW,
    // Display info for selected books (absItemId -> display info)
    val selectedBookDisplays: Map<String, SelectedBookDisplay> = emptyMap(),
    // Inline book search state (for the currently active search field)
    val activeSearchAbsItemId: String? = null,
    val bookSearchQuery: String = "",
    val bookSearchResults: List<SearchHitResponse> = emptyList(),
    val isSearchingBooks: Boolean = false,
    // ID of the book result item currently being processed (shows loading spinner)
    val loadingBookItemId: String? = null,
    // Import options
    val importSessions: Boolean = true,
    val importProgress: Boolean = true,
    val rebuildProgress: Boolean = true,
    // Import results
    val isImporting: Boolean = false,
    val importResults: ABSImportResults? = null,
    val error: String? = null,
)

/**
 * A locally selected file ready for upload.
 *
 * Uses [FileSource] for streaming access to avoid loading the entire file into memory.
 * This is critical for large backup files that could otherwise cause OOM crashes.
 */
data class SelectedLocalFile(
    val fileSource: FileSource,
    val filename: String,
    val size: Long,
)

/**
 * Results from the ABS import.
 */
data class ABSImportResults(
    val sessionsImported: Int,
    val sessionsSkipped: Int,
    val progressImported: Int,
    val progressSkipped: Int,
    val eventsCreated: Int,
    val affectedUsers: Int,
    val duration: String,
    val warnings: List<String>,
    val errors: List<String>,
)

/**
 * ViewModel for ABS import flow.
 *
 * Supports two source types:
 * - LOCAL: User picks file from device, uploads to server, then analyzes
 * - REMOTE: User browses server filesystem, selects file, then analyzes
 *
 * TODO: Split into smaller pieces — e.g. extract UserMappingHandler and BookMappingHandler
 *  delegate classes to reduce class size below the detekt LargeClass threshold.
 */
@Suppress("LargeClass")
class ABSImportViewModel(
    private val backupApi: BackupApiContract,
    private val searchApi: SearchApiContract,
    private val absImportApi: ABSImportApiContract,
    private val syncRepository: SyncRepository,
) : ViewModel() {
    val state: StateFlow<ABSImportState>
        field = MutableStateFlow(ABSImportState())

    // === Source Selection ===

    fun selectSourceType(type: ABSSourceType) {
        state.update { it.copy(sourceType = type, error = null) }
        when (type) {
            ABSSourceType.LOCAL -> {
                // Stay on source selection, user will pick file via UI
            }

            ABSSourceType.REMOTE -> {
                // Navigate to file browser and load root
                state.update { it.copy(step = ABSImportStep.FILE_BROWSER) }
                loadDirectory("/")
            }
        }
    }

    // === Local File Handling ===

    /**
     * Called when user picks a local file via the document picker.
     *
     * @param fileSource Streaming source for the file content (avoids loading into memory)
     * @param filename Original filename for display
     * @param size File size in bytes
     */
    fun setLocalFile(
        fileSource: FileSource,
        filename: String,
        size: Long,
    ) {
        state.update {
            it.copy(
                selectedLocalFile = SelectedLocalFile(fileSource, filename, size),
                error = null,
            )
        }
    }

    /**
     * Clear the selected local file.
     */
    fun clearLocalFile() {
        state.update { it.copy(selectedLocalFile = null) }
    }

    /**
     * Upload the selected local file to the server and proceed to analysis.
     *
     * Uses streaming upload to avoid loading the entire file into memory.
     */
    fun uploadAndAnalyze() {
        val file = state.value.selectedLocalFile ?: return

        viewModelScope.launch {
            state.update { it.copy(step = ABSImportStep.UPLOADING, isUploading = true, error = null) }

            try {
                // Upload the file using streaming (file content is read on-demand)
                val uploadResult = backupApi.uploadABSBackup(file.fileSource)

                // Proceed to analysis with the returned path
                state.update {
                    it.copy(
                        isUploading = false,
                        backupPath = uploadResult.path,
                    )
                }
                analyzeBackup(uploadResult.path)
            } catch (e: Exception) {
                ErrorBus.emit(e)
                logger.error(e) { "Failed to upload ABS backup" }
                state.update {
                    it.copy(
                        step = ABSImportStep.SOURCE_SELECTION,
                        isUploading = false,
                        error = "Failed to upload file: ${e.message}",
                    )
                }
            }
        }
    }

    // === Remote File Browser ===

    /**
     * Load directory contents for the file browser.
     */
    fun loadDirectory(path: String) {
        viewModelScope.launch {
            state.update { it.copy(isLoadingDirectories = true, error = null) }

            try {
                val result = backupApi.browseFilesystem(path)
                state.update {
                    it.copy(
                        currentPath = result.path,
                        parentPath = result.parent,
                        directories = result.entries,
                        isRoot = result.isRoot,
                        isLoadingDirectories = false,
                    )
                }
            } catch (e: Exception) {
                ErrorBus.emit(e)
                logger.error(e) { "Failed to browse filesystem" }
                state.update {
                    it.copy(
                        isLoadingDirectories = false,
                        error = "Failed to browse: ${e.message}",
                    )
                }
            }
        }
    }

    /**
     * Navigate up to parent directory.
     */
    fun navigateUp() {
        val parent = state.value.parentPath
        if (parent != null) {
            loadDirectory(parent)
        }
    }

    /**
     * Set the remote file path and proceed to analysis.
     * User enters filename within the current directory.
     */
    fun setRemoteFilePath(filename: String) {
        val currentPath = state.value.currentPath
        val fullPath =
            if (currentPath.endsWith("/")) {
                "$currentPath$filename"
            } else {
                "$currentPath/$filename"
            }

        state.update { it.copy(selectedRemotePath = fullPath, backupPath = fullPath) }
        analyzeBackup(fullPath)
    }

    /**
     * Set the full remote path directly and proceed to analysis.
     */
    fun setFullRemotePath(path: String) {
        state.update { it.copy(selectedRemotePath = path, backupPath = path) }
        analyzeBackup(path)
    }

    // === Analysis ===

    @Suppress("CyclomaticComplexMethod")
    private fun analyzeBackup(path: String) {
        viewModelScope.launch {
            state.update {
                it.copy(
                    step = ABSImportStep.ANALYZING,
                    isAnalyzing = true,
                    error = null,
                    analyzePhase = "",
                    analyzeCurrent = 0,
                    analyzeTotal = 0,
                )
            }

            try {
                // Start async analysis
                val asyncResponse =
                    backupApi.analyzeABSBackupAsync(
                        AnalyzeABSRequest(
                            backupPath = path,
                            matchByEmail = true,
                            matchByPath = true,
                            fuzzyMatchBooks = true,
                            fuzzyThreshold = 0.85,
                        ),
                    )

                // Poll for status
                val analysisId = asyncResponse.analysisId
                var statusResponse = backupApi.getAnalysisStatus(analysisId)

                while (statusResponse.status == "running") {
                    state.update {
                        it.copy(
                            analyzePhase = statusResponse.phase,
                            analyzeCurrent = statusResponse.current,
                            analyzeTotal = statusResponse.total,
                        )
                    }
                    @Suppress("MagicNumber")
                    delay(1500)
                    statusResponse = backupApi.getAnalysisStatus(analysisId)
                }

                if (statusResponse.status == "failed") {
                    error(statusResponse.error ?: "Analysis failed")
                }

                val result = statusResponse.result!!

                // Build initial mappings from server-matched items
                // All items with listenupId are auto-matched; users can review and change
                val initialUserMappings =
                    result.userMatches
                        .filter { it.listenupId != null }
                        .associate { it.absUserId to it.listenupId!! }

                val initialBookMappings =
                    result.bookMatches
                        .filter { it.listenupId != null }
                        .associate { it.absItemId to it.listenupId!! }

                // Pre-populate display info for auto-matched books
                val initialBookDisplays =
                    result.bookMatches
                        .filter { it.listenupId != null }
                        .associate { match ->
                            val listenupId = match.listenupId!!
                            // Try to find the matched book in suggestions for full details
                            val matchedSuggestion = match.suggestions.firstOrNull { it.bookId == listenupId }
                            // Fallback to first suggestion if matched book not in list
                            val suggestion = matchedSuggestion ?: match.suggestions.firstOrNull()

                            val display =
                                if (suggestion != null) {
                                    SelectedBookDisplay(
                                        bookId = listenupId,
                                        title = suggestion.title,
                                        author = suggestion.author,
                                        durationMs = suggestion.durationMs,
                                    )
                                } else {
                                    // No suggestions available — use ABS metadata for display
                                    SelectedBookDisplay(
                                        bookId = listenupId,
                                        title = match.absTitle,
                                        author = match.absAuthor,
                                        durationMs = null,
                                    )
                                }
                            match.absItemId to display
                        }

                // Determine next step based on what needs mapping
                val nextStep =
                    when {
                        result.usersPending > 0 -> ABSImportStep.USER_MAPPING
                        result.booksPending > 0 -> ABSImportStep.BOOK_MAPPING
                        else -> ABSImportStep.IMPORT_OPTIONS
                    }

                state.update {
                    it.copy(
                        isAnalyzing = false,
                        analysisComplete = true,
                        step = nextStep,
                        summary = result.summary,
                        totalUsers = result.totalUsers,
                        totalBooks = result.totalBooks,
                        totalSessions = result.totalSessions,
                        usersMatched = result.usersMatched,
                        usersPending = result.usersPending,
                        booksMatched = result.booksMatched,
                        booksPending = result.booksPending,
                        sessionsReady = result.sessionsReady,
                        sessionsPending = result.sessionsPending,
                        progressReady = result.progressReady,
                        progressPending = result.progressPending,
                        userMatches = result.userMatches,
                        bookMatches = result.bookMatches,
                        analysisWarnings = result.warnings,
                        userMappings = initialUserMappings,
                        bookMappings = initialBookMappings,
                        selectedBookDisplays = initialBookDisplays,
                    )
                }
            } catch (e: Exception) {
                ErrorBus.emit(e)
                logger.error(e) { "Failed to analyze ABS backup" }
                state.update {
                    it.copy(
                        isAnalyzing = false,
                        step = ABSImportStep.SOURCE_SELECTION,
                        error = "Failed to analyze backup: ${e.message}",
                    )
                }
            }
        }
    }

    // === Mapping ===

    fun setUserMapping(
        absUserId: String,
        listenupUserId: String?,
    ) {
        state.update { current ->
            val newMappings = current.userMappings.toMutableMap()
            if (listenupUserId != null) {
                newMappings[absUserId] = listenupUserId
            } else {
                newMappings.remove(absUserId)
            }
            current.copy(userMappings = newMappings)
        }
    }

    fun setBookMapping(
        absItemId: String,
        listenupBookId: String?,
    ) {
        state.update { current ->
            val newMappings = current.bookMappings.toMutableMap()
            if (listenupBookId != null) {
                newMappings[absItemId] = listenupBookId
            } else {
                newMappings.remove(absItemId)
            }
            current.copy(bookMappings = newMappings)
        }
    }

    // === User Mapping Tab ===

    /**
     * Set the active tab in the user mapping step.
     */
    fun setUserMappingTab(tab: UserMappingTab) {
        state.update { it.copy(userMappingTab = tab) }
    }

    // === Inline User Search ===

    /**
     * Called when a user search field gains focus.
     * Activates search for that specific user and clears previous search state.
     */
    fun activateUserSearch(absUserId: String) {
        state.update {
            it.copy(
                activeSearchAbsUserId = absUserId,
                userSearchQuery = "",
                userSearchResults = emptyList(),
                isSearchingUsers = false,
            )
        }
    }

    /**
     * Called when a user search field loses focus.
     * Clears the active search state.
     */
    fun deactivateUserSearch() {
        state.update {
            it.copy(
                activeSearchAbsUserId = null,
                userSearchQuery = "",
                userSearchResults = emptyList(),
                isSearchingUsers = false,
            )
        }
    }

    /**
     * Update search query for the active user search field.
     */
    fun updateUserSearchQuery(query: String) {
        state.update { it.copy(userSearchQuery = query) }

        if (query.length < 2) {
            state.update { it.copy(userSearchResults = emptyList(), isSearchingUsers = false) }
            return
        }

        viewModelScope.launch {
            state.update { it.copy(isSearchingUsers = true) }
            try {
                when (val result = absImportApi.searchUsers(query, limit = 10)) {
                    is Success -> {
                        state.update {
                            it.copy(
                                userSearchResults = result.data,
                                isSearchingUsers = false,
                            )
                        }
                    }

                    is Failure -> {
                        logger.error { "User search failed: ${result.exception}" }
                        state.update {
                            it.copy(
                                userSearchResults = emptyList(),
                                isSearchingUsers = false,
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                ErrorBus.emit(e)
                logger.error(e) { "User search failed: ${e.message}" }
                state.update {
                    it.copy(
                        userSearchResults = emptyList(),
                        isSearchingUsers = false,
                    )
                }
            }
        }
    }

    /**
     * Select a user from search results or suggestions and apply the mapping.
     */
    fun selectUser(
        absUserId: String,
        userId: String,
        email: String,
        displayName: String?,
    ) {
        viewModelScope.launch {
            // Show loading spinner on the tapped result while state propagates
            state.update { it.copy(loadingUserItemId = userId) }

            // Store display info for the selected user
            val displayInfo =
                SelectedUserDisplay(
                    userId = userId,
                    email = email,
                    displayName = displayName,
                )

            state.update { s ->
                val newDisplays = s.selectedUserDisplays.toMutableMap()
                newDisplays[absUserId] = displayInfo

                val newMappings = s.userMappings.toMutableMap()
                newMappings[absUserId] = userId

                s.copy(
                    selectedUserDisplays = newDisplays,
                    userMappings = newMappings,
                    // Clear search state
                    activeSearchAbsUserId = null,
                    userSearchQuery = "",
                    userSearchResults = emptyList(),
                    loadingUserItemId = null,
                )
            }
        }
    }

    /**
     * Clear the user mapping for an ABS user (allows re-searching).
     */
    fun clearUserMapping(absUserId: String) {
        state.update { s ->
            val newDisplays = s.selectedUserDisplays.toMutableMap()
            newDisplays.remove(absUserId)

            val newMappings = s.userMappings.toMutableMap()
            newMappings.remove(absUserId)

            s.copy(
                selectedUserDisplays = newDisplays,
                userMappings = newMappings,
            )
        }
    }

    // === Book Mapping Tab ===

    /**
     * Set the active tab in the book mapping step.
     */
    fun setBookMappingTab(tab: BookMappingTab) {
        state.update { it.copy(bookMappingTab = tab) }
    }

    // === Inline Book Search ===

    /**
     * Called when a book search field gains focus.
     * Activates search for that specific book and clears previous search state.
     */
    fun activateBookSearch(absItemId: String) {
        state.update {
            it.copy(
                activeSearchAbsItemId = absItemId,
                bookSearchQuery = "",
                bookSearchResults = emptyList(),
                isSearchingBooks = false,
            )
        }
    }

    /**
     * Called when a book search field loses focus.
     * Clears the active search state.
     */
    fun deactivateBookSearch() {
        state.update {
            it.copy(
                activeSearchAbsItemId = null,
                bookSearchQuery = "",
                bookSearchResults = emptyList(),
                isSearchingBooks = false,
            )
        }
    }

    /**
     * Update search query for the active book search field.
     */
    fun updateBookSearchQuery(query: String) {
        state.update { it.copy(bookSearchQuery = query) }

        if (query.length < 2) {
            state.update { it.copy(bookSearchResults = emptyList(), isSearchingBooks = false) }
            return
        }

        viewModelScope.launch {
            state.update { it.copy(isSearchingBooks = true) }
            try {
                val response =
                    searchApi.search(
                        query = query,
                        types = "book",
                        genres = null,
                        genrePath = null,
                        minDuration = null,
                        maxDuration = null,
                        limit = 10,
                        offset = 0,
                    )
                state.update {
                    it.copy(
                        bookSearchResults = response.hits,
                        isSearchingBooks = false,
                    )
                }
            } catch (e: Exception) {
                ErrorBus.emit(e)
                logger.error(e) { "Book search failed: ${e.message}" }
                state.update {
                    it.copy(
                        bookSearchResults = emptyList(),
                        isSearchingBooks = false,
                    )
                }
            }
        }
    }

    /**
     * Select a book from search results or suggestions and apply the mapping.
     */
    fun selectBook(
        absItemId: String,
        bookId: String,
        title: String,
        author: String?,
        durationMs: Long?,
    ) {
        viewModelScope.launch {
            // Show loading spinner on the tapped result while state propagates
            state.update { it.copy(loadingBookItemId = bookId) }

            // Store display info for the selected book
            val displayInfo =
                SelectedBookDisplay(
                    bookId = bookId,
                    title = title,
                    author = author,
                    durationMs = durationMs,
                )

            state.update { s ->
                val newDisplays = s.selectedBookDisplays.toMutableMap()
                newDisplays[absItemId] = displayInfo

                val newMappings = s.bookMappings.toMutableMap()
                newMappings[absItemId] = bookId

                s.copy(
                    selectedBookDisplays = newDisplays,
                    bookMappings = newMappings,
                    // Clear search state
                    activeSearchAbsItemId = null,
                    bookSearchQuery = "",
                    bookSearchResults = emptyList(),
                    loadingBookItemId = null,
                )
            }
        }
    }

    /**
     * Clear the book mapping for an ABS item (allows re-searching).
     */
    fun clearBookMapping(absItemId: String) {
        state.update { s ->
            val newDisplays = s.selectedBookDisplays.toMutableMap()
            newDisplays.remove(absItemId)

            val newMappings = s.bookMappings.toMutableMap()
            newMappings.remove(absItemId)

            s.copy(
                selectedBookDisplays = newDisplays,
                bookMappings = newMappings,
            )
        }
    }

    // === Import Options ===

    fun setImportSessions(value: Boolean) {
        state.update { it.copy(importSessions = value) }
    }

    fun setImportProgress(value: Boolean) {
        state.update { it.copy(importProgress = value) }
    }

    fun setRebuildProgress(value: Boolean) {
        state.update { it.copy(rebuildProgress = value) }
    }

    // === Navigation ===

    fun nextStep() {
        val current = state.value
        when (current.step) {
            ABSImportStep.SOURCE_SELECTION -> {
                when (current.sourceType) {
                    ABSSourceType.LOCAL -> {
                        if (current.selectedLocalFile != null) {
                            uploadAndAnalyze()
                        }
                    }

                    ABSSourceType.REMOTE -> {
                        state.update { it.copy(step = ABSImportStep.FILE_BROWSER) }
                        loadDirectory("/")
                    }

                    null -> { /* No source selected yet */ }
                }
            }

            ABSImportStep.FILE_BROWSER -> {
                // User needs to select a file path
            }

            ABSImportStep.UPLOADING -> {
                // Wait for upload to complete
            }

            ABSImportStep.ANALYZING -> {
                // Wait for analysis to complete
            }

            ABSImportStep.USER_MAPPING -> {
                // Move to book mapping or import options
                if (current.booksPending > 0) {
                    state.update { it.copy(step = ABSImportStep.BOOK_MAPPING) }
                } else {
                    state.update { it.copy(step = ABSImportStep.IMPORT_OPTIONS) }
                }
            }

            ABSImportStep.BOOK_MAPPING -> {
                state.update { it.copy(step = ABSImportStep.IMPORT_OPTIONS) }
            }

            ABSImportStep.IMPORT_OPTIONS -> {
                performImport()
            }

            ABSImportStep.IMPORTING -> {
                // Wait for import to complete
            }

            ABSImportStep.RESULTS -> {
                // Done
            }
        }
    }

    fun previousStep() {
        val current = state.value
        when (current.step) {
            ABSImportStep.SOURCE_SELECTION -> { /* Can't go back */ }

            ABSImportStep.FILE_BROWSER -> {
                state.update { it.copy(step = ABSImportStep.SOURCE_SELECTION) }
            }

            ABSImportStep.UPLOADING -> { /* Can't go back during upload */ }

            ABSImportStep.ANALYZING -> { /* Can't go back during analysis */ }

            ABSImportStep.USER_MAPPING -> {
                state.update { it.copy(step = ABSImportStep.SOURCE_SELECTION) }
            }

            ABSImportStep.BOOK_MAPPING -> {
                if (current.usersPending > 0) {
                    state.update { it.copy(step = ABSImportStep.USER_MAPPING) }
                } else {
                    state.update { it.copy(step = ABSImportStep.SOURCE_SELECTION) }
                }
            }

            ABSImportStep.IMPORT_OPTIONS -> {
                if (current.booksPending > 0) {
                    state.update { it.copy(step = ABSImportStep.BOOK_MAPPING) }
                } else if (current.usersPending > 0) {
                    state.update { it.copy(step = ABSImportStep.USER_MAPPING) }
                } else {
                    state.update { it.copy(step = ABSImportStep.SOURCE_SELECTION) }
                }
            }

            ABSImportStep.IMPORTING -> { /* Can't go back during import */ }

            ABSImportStep.RESULTS -> { /* Can't go back after complete */ }
        }
    }

    // === Import ===

    private fun performImport() {
        viewModelScope.launch {
            state.update { it.copy(step = ABSImportStep.IMPORTING, isImporting = true, error = null) }

            try {
                val current = state.value
                val result =
                    backupApi.importABSBackup(
                        ImportABSRequest(
                            backupPath = current.backupPath,
                            userMappings = current.userMappings,
                            bookMappings = current.bookMappings,
                            importSessions = current.importSessions,
                            importProgress = current.importProgress,
                            rebuildProgress = current.rebuildProgress,
                        ),
                    )

                state.update {
                    it.copy(
                        isImporting = false,
                        step = ABSImportStep.RESULTS,
                        importResults =
                            ABSImportResults(
                                sessionsImported = result.sessionsImported,
                                sessionsSkipped = result.sessionsSkipped,
                                progressImported = result.progressImported,
                                progressSkipped = result.progressSkipped,
                                eventsCreated = result.eventsCreated,
                                affectedUsers = result.affectedUsers,
                                duration = result.duration,
                                warnings = result.warnings,
                                errors = result.errors,
                            ),
                    )
                }

                // Refresh listening history to pull all imported events and rebuild positions
                // This uses a full refresh (ignoring delta sync cursor) because imported
                // events have historical timestamps that wouldn't be included in normal sync
                logger.info { "Import complete, refreshing listening history" }
                syncRepository.refreshListeningHistory()
            } catch (e: Exception) {
                ErrorBus.emit(e)
                logger.error(e) { "Failed to import ABS backup" }
                state.update {
                    it.copy(
                        isImporting = false,
                        step = ABSImportStep.IMPORT_OPTIONS,
                        error = "Failed to import: ${e.message}",
                    )
                }
            }
        }
    }

    fun clearError() {
        state.update { it.copy(error = null) }
    }
}
