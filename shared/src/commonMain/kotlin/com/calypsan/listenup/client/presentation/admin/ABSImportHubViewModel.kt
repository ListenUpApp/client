package com.calypsan.listenup.client.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.FileSource
import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.core.error.ErrorBus
import com.calypsan.listenup.client.data.remote.ABSImportApiContract
import com.calypsan.listenup.client.data.remote.ABSImportBook
import com.calypsan.listenup.client.data.remote.ABSImportResponse
import com.calypsan.listenup.client.data.remote.ABSImportSummary
import com.calypsan.listenup.client.data.remote.ABSImportUser
import com.calypsan.listenup.client.data.remote.ABSSessionsResponse
import com.calypsan.listenup.client.data.remote.ImportSessionsResult
import com.calypsan.listenup.client.data.remote.MappingFilter
import com.calypsan.listenup.client.data.remote.SearchApiContract
import com.calypsan.listenup.client.data.remote.SearchHitResponse
import com.calypsan.listenup.client.data.remote.SessionStatusFilter
import com.calypsan.listenup.client.data.remote.UserSearchResult
import com.calypsan.listenup.client.domain.repository.SyncRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

private const val IMPORT_STATUS_ANALYZING = "analyzing"
private const val ANALYSIS_POLL_INTERVAL_MS = 3_000L

/**
 * Tab in the import hub detail view.
 */
enum class ImportHubTab {
    OVERVIEW,
    USERS,
    BOOKS,
    SESSIONS,
}

/**
 * State for the import list screen.
 */
data class ABSImportListState(
    val imports: List<ABSImportSummary> = emptyList(),
    val isLoading: Boolean = false,
    val isCreating: Boolean = false,
    val error: String? = null,
)

/**
 * State for a single import's detail/hub view.
 */
data class ABSImportHubState(
    val importId: String = "",
    val import: ABSImportResponse? = null,
    val activeTab: ImportHubTab = ImportHubTab.OVERVIEW,
    val isLoading: Boolean = false,
    // Users tab
    val users: List<ABSImportUser> = emptyList(),
    val usersFilter: MappingFilter = MappingFilter.ALL,
    val isLoadingUsers: Boolean = false,
    val activeSearchAbsUserId: String? = null,
    val userSearchQuery: String = "",
    val userSearchResults: List<UserSearchResult> = emptyList(),
    val isSearchingUsers: Boolean = false,
    // Books tab
    val books: List<ABSImportBook> = emptyList(),
    val booksFilter: MappingFilter = MappingFilter.ALL,
    val isLoadingBooks: Boolean = false,
    val activeSearchAbsMediaId: String? = null,
    val bookSearchQuery: String = "",
    val bookSearchResults: List<SearchHitResponse> = emptyList(),
    val isSearchingBooks: Boolean = false,
    // In-flight mapping tracking (#140)
    val mappingInFlightUsers: Set<String> = emptySet(),
    val mappingInFlightBooks: Set<String> = emptySet(),
    // Sessions tab
    val sessionsResponse: ABSSessionsResponse? = null,
    val sessionsFilter: SessionStatusFilter = SessionStatusFilter.ALL,
    val isLoadingSessions: Boolean = false,
    val isImportingSessions: Boolean = false,
    val importResult: ImportSessionsResult? = null,
    val error: String? = null,
)

/**
 * ViewModel for the persistent ABS import hub.
 *
 * Manages:
 * - List of all imports (resumable)
 * - Creating new imports
 * - Detail view for a specific import
 * - User/book mapping within an import
 * - Session import
 */
class ABSImportHubViewModel(
    private val absImportApi: ABSImportApiContract,
    private val searchApi: SearchApiContract,
    private val syncRepository: SyncRepository,
) : ViewModel() {
    val listState: StateFlow<ABSImportListState>
        field = MutableStateFlow(ABSImportListState())

    val hubState: StateFlow<ABSImportHubState>
        field = MutableStateFlow(ABSImportHubState())

    private var userSearchJob: Job? = null
    private var bookSearchJob: Job? = null
    private var analysisPollingJob: Job? = null

    init {
        loadImports()
    }

    // === Import List ===

    fun loadImports() {
        viewModelScope.launch {
            listState.update { it.copy(isLoading = true, error = null) }
            when (val result = absImportApi.listImports()) {
                is Success -> {
                    listState.update { it.copy(imports = result.data, isLoading = false) }
                }

                is Failure -> {
                    logger.error { "Failed to load imports: ${result.exception}" }
                    listState.update {
                        it.copy(isLoading = false, error = "Failed to load imports")
                    }
                }
            }
        }
    }

    fun createImport(
        fileSource: FileSource,
        name: String,
    ) {
        viewModelScope.launch {
            listState.update { it.copy(isCreating = true, error = null) }
            when (val result = absImportApi.createImport(fileSource, name)) {
                is Success -> {
                    listState.update { it.copy(isCreating = false) }
                    loadImports() // Refresh list
                    if (result.data.status == IMPORT_STATUS_ANALYZING) {
                        startAnalysisPolling(result.data.id)
                    }
                }

                is Failure -> {
                    logger.error { "Failed to create import: ${result.exception}" }
                    listState.update {
                        it.copy(
                            isCreating = false,
                            error = "Failed to create import: ${result.exception?.message ?: "Unknown error"}",
                        )
                    }
                }
            }
        }
    }

    /**
     * Create an import and return the import ID.
     * Used by the upload sheet to navigate to the import hub after upload.
     */
    suspend fun createImportAndGetId(
        fileSource: FileSource,
        name: String,
    ): Result<String> =
        when (val result = absImportApi.createImport(fileSource, name)) {
            is Success -> {
                loadImports() // Refresh list in background
                if (result.data.status == IMPORT_STATUS_ANALYZING) {
                    startAnalysisPolling(result.data.id)
                }
                Success(result.data.id)
            }

            is Failure -> {
                logger.error { "Failed to create import: ${result.exception}" }
                Failure(
                    exception = result.exception,
                    message = result.message,
                    errorCode = result.errorCode,
                )
            }
        }

    fun createImportFromPath(
        backupPath: String,
        name: String,
    ) {
        viewModelScope.launch {
            listState.update { it.copy(isCreating = true, error = null) }
            when (val result = absImportApi.createImportFromPath(backupPath, name)) {
                is Success -> {
                    listState.update { it.copy(isCreating = false) }
                    loadImports() // Refresh list
                    if (result.data.status == IMPORT_STATUS_ANALYZING) {
                        startAnalysisPolling(result.data.id)
                    }
                }

                is Failure -> {
                    logger.error { "Failed to create import from path: ${result.exception}" }
                    listState.update {
                        it.copy(
                            isCreating = false,
                            error = "Failed to create import: ${result.exception?.message ?: "Unknown error"}",
                        )
                    }
                }
            }
        }
    }

    fun deleteImport(importId: String) {
        viewModelScope.launch {
            when (val result = absImportApi.deleteImport(importId)) {
                is Success -> {
                    loadImports()
                }

                is Failure -> {
                    logger.error { "Failed to delete import: ${result.exception}" }
                    listState.update { it.copy(error = "Failed to delete import") }
                }
            }
        }
    }

    // === Import Hub (Detail View) ===

    fun openImport(importId: String) {
        hubState.update { ABSImportHubState(importId = importId, isLoading = true) }
        viewModelScope.launch {
            when (val result = absImportApi.getImport(importId)) {
                is Success -> {
                    hubState.update { it.copy(import = result.data, isLoading = false) }
                    // Load initial data for the active tab
                    loadTabData(ImportHubTab.OVERVIEW)
                    // Start polling if analysis is still in progress (covers WorkManager upload flow)
                    if (result.data.status == IMPORT_STATUS_ANALYZING) {
                        startAnalysisPolling(importId)
                    }
                    // If this import isn't in the list yet (newly created via background upload),
                    // reload the list so the user sees it when they navigate back
                    if (listState.value.imports.none { it.id == importId }) {
                        loadImports()
                    }
                }

                is Failure -> {
                    logger.error { "Failed to load import: ${result.exception}" }
                    hubState.update {
                        it.copy(isLoading = false, error = "Failed to load import")
                    }
                }
            }
        }
    }

    fun setActiveTab(tab: ImportHubTab) {
        hubState.update { it.copy(activeTab = tab) }
        loadTabData(tab)
    }

    private fun loadTabData(tab: ImportHubTab) {
        when (tab) {
            ImportHubTab.OVERVIEW -> refreshImport()
            ImportHubTab.USERS -> loadUsers()
            ImportHubTab.BOOKS -> loadBooks()
            ImportHubTab.SESSIONS -> loadSessions()
        }
    }

    private fun refreshImport() {
        val importId = hubState.value.importId
        if (importId.isEmpty()) return

        viewModelScope.launch {
            when (val result = absImportApi.getImport(importId)) {
                is Success -> {
                    hubState.update { it.copy(import = result.data) }
                }

                is Failure -> {
                    logger.error { "Failed to refresh import: ${result.exception}" }
                }
            }
        }
    }

    // === Users Tab ===

    fun setUsersFilter(filter: MappingFilter) {
        hubState.update { it.copy(usersFilter = filter) }
        loadUsers()
    }

    private fun loadUsers() {
        val state = hubState.value
        if (state.importId.isEmpty()) return

        viewModelScope.launch {
            hubState.update { it.copy(isLoadingUsers = true) }
            when (val result = absImportApi.listImportUsers(state.importId, state.usersFilter)) {
                is Success -> {
                    hubState.update { it.copy(users = result.data, isLoadingUsers = false) }
                }

                is Failure -> {
                    logger.error { "Failed to load users: ${result.exception}" }
                    hubState.update { it.copy(isLoadingUsers = false, error = "Failed to load users") }
                }
            }
        }
    }

    fun activateUserSearch(absUserId: String) {
        hubState.update {
            it.copy(
                activeSearchAbsUserId = absUserId,
                userSearchQuery = "",
                userSearchResults = emptyList(),
                isSearchingUsers = false,
            )
        }
    }

    fun deactivateUserSearch() {
        userSearchJob?.cancel()
        hubState.update {
            it.copy(
                activeSearchAbsUserId = null,
                userSearchQuery = "",
                userSearchResults = emptyList(),
                isSearchingUsers = false,
            )
        }
    }

    @Suppress("MagicNumber")
    fun updateUserSearchQuery(query: String) {
        hubState.update { it.copy(userSearchQuery = query) }
        userSearchJob?.cancel()

        if (query.length < 2) {
            hubState.update { it.copy(userSearchResults = emptyList(), isSearchingUsers = false) }
            return
        }

        userSearchJob =
            viewModelScope.launch {
                delay(300)
                hubState.update { it.copy(isSearchingUsers = true) }
                when (val result = absImportApi.searchUsers(query, limit = 10)) {
                    is Success -> {
                        hubState.update {
                            it.copy(userSearchResults = result.data, isSearchingUsers = false)
                        }
                    }

                    is Failure -> {
                        logger.error { "User search failed: ${result.exception}" }
                        hubState.update {
                            it.copy(userSearchResults = emptyList(), isSearchingUsers = false)
                        }
                    }
                }
            }
    }

    fun mapUser(
        absUserId: String,
        listenUpId: String,
    ) {
        val importId = hubState.value.importId
        if (importId.isEmpty()) return

        hubState.update { it.copy(mappingInFlightUsers = it.mappingInFlightUsers + absUserId) }
        viewModelScope.launch {
            when (val result = absImportApi.mapUser(importId, absUserId, listenUpId)) {
                is Success -> {
                    deactivateUserSearch()
                    hubState.update { state ->
                        state.copy(
                            users =
                                state.users.map { user ->
                                    if (user.absUserId == absUserId) result.data else user
                                },
                            mappingInFlightUsers = state.mappingInFlightUsers - absUserId,
                        )
                    }
                    refreshImport()
                }

                is Failure -> {
                    logger.error { "Failed to map user: ${result.exception}" }
                    hubState.update {
                        it.copy(
                            error = "Failed to map user",
                            mappingInFlightUsers = it.mappingInFlightUsers - absUserId,
                        )
                    }
                }
            }
        }
    }

    fun clearUserMapping(absUserId: String) {
        val importId = hubState.value.importId
        if (importId.isEmpty()) return

        viewModelScope.launch {
            when (val result = absImportApi.clearUserMapping(importId, absUserId)) {
                is Success -> {
                    hubState.update { state ->
                        state.copy(
                            users =
                                state.users.map { user ->
                                    if (user.absUserId == absUserId) result.data else user
                                },
                        )
                    }
                    refreshImport()
                }

                is Failure -> {
                    logger.error { "Failed to clear user mapping: ${result.exception}" }
                    hubState.update { it.copy(error = "Failed to clear mapping") }
                }
            }
        }
    }

    // === Books Tab ===

    fun setBooksFilter(filter: MappingFilter) {
        hubState.update { it.copy(booksFilter = filter) }
        loadBooks()
    }

    private fun loadBooks() {
        val state = hubState.value
        if (state.importId.isEmpty()) return

        viewModelScope.launch {
            hubState.update { it.copy(isLoadingBooks = true) }
            when (val result = absImportApi.listImportBooks(state.importId, state.booksFilter)) {
                is Success -> {
                    hubState.update { it.copy(books = result.data, isLoadingBooks = false) }
                }

                is Failure -> {
                    logger.error { "Failed to load books: ${result.exception}" }
                    hubState.update { it.copy(isLoadingBooks = false, error = "Failed to load books") }
                }
            }
        }
    }

    fun activateBookSearch(absMediaId: String) {
        hubState.update {
            it.copy(
                activeSearchAbsMediaId = absMediaId,
                bookSearchQuery = "",
                bookSearchResults = emptyList(),
                isSearchingBooks = false,
            )
        }
    }

    fun deactivateBookSearch() {
        bookSearchJob?.cancel()
        hubState.update {
            it.copy(
                activeSearchAbsMediaId = null,
                bookSearchQuery = "",
                bookSearchResults = emptyList(),
                isSearchingBooks = false,
            )
        }
    }

    @Suppress("MagicNumber")
    fun updateBookSearchQuery(query: String) {
        hubState.update { it.copy(bookSearchQuery = query) }
        bookSearchJob?.cancel()

        if (query.length < 2) {
            hubState.update { it.copy(bookSearchResults = emptyList(), isSearchingBooks = false) }
            return
        }

        bookSearchJob =
            viewModelScope.launch {
                delay(300)
                hubState.update { it.copy(isSearchingBooks = true) }
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
                    hubState.update {
                        it.copy(bookSearchResults = response.hits, isSearchingBooks = false)
                    }
                } catch (e: Exception) {
                    ErrorBus.emit(e)
                    logger.error(e) { "Book search failed" }
                    hubState.update {
                        it.copy(bookSearchResults = emptyList(), isSearchingBooks = false)
                    }
                }
            }
    }

    fun mapBook(
        absMediaId: String,
        listenUpId: String,
    ) {
        val importId = hubState.value.importId
        if (importId.isEmpty()) return

        hubState.update { it.copy(mappingInFlightBooks = it.mappingInFlightBooks + absMediaId) }
        viewModelScope.launch {
            when (val result = absImportApi.mapBook(importId, absMediaId, listenUpId)) {
                is Success -> {
                    deactivateBookSearch()
                    hubState.update { state ->
                        state.copy(
                            books =
                                state.books.map { book ->
                                    if (book.absMediaId == absMediaId) result.data else book
                                },
                            mappingInFlightBooks = state.mappingInFlightBooks - absMediaId,
                        )
                    }
                    refreshImport()
                }

                is Failure -> {
                    logger.error { "Failed to map book: ${result.exception}" }
                    hubState.update {
                        it.copy(
                            error = "Failed to map book",
                            mappingInFlightBooks = it.mappingInFlightBooks - absMediaId,
                        )
                    }
                }
            }
        }
    }

    fun clearBookMapping(absMediaId: String) {
        val importId = hubState.value.importId
        if (importId.isEmpty()) return

        viewModelScope.launch {
            when (val result = absImportApi.clearBookMapping(importId, absMediaId)) {
                is Success -> {
                    hubState.update { state ->
                        state.copy(
                            books =
                                state.books.map { book ->
                                    if (book.absMediaId == absMediaId) result.data else book
                                },
                        )
                    }
                    refreshImport()
                }

                is Failure -> {
                    logger.error { "Failed to clear book mapping: ${result.exception}" }
                    hubState.update { it.copy(error = "Failed to clear mapping") }
                }
            }
        }
    }

    // === Sessions Tab ===

    fun setSessionsFilter(filter: SessionStatusFilter) {
        hubState.update { it.copy(sessionsFilter = filter) }
        loadSessions()
    }

    private fun loadSessions() {
        val state = hubState.value
        if (state.importId.isEmpty()) return

        viewModelScope.launch {
            hubState.update { it.copy(isLoadingSessions = true) }
            when (val result = absImportApi.listSessions(state.importId, state.sessionsFilter)) {
                is Success -> {
                    hubState.update { it.copy(sessionsResponse = result.data, isLoadingSessions = false) }
                }

                is Failure -> {
                    logger.error { "Failed to load sessions: ${result.exception}" }
                    hubState.update { it.copy(isLoadingSessions = false, error = "Failed to load sessions") }
                }
            }
        }
    }

    fun importReadySessions() {
        val importId = hubState.value.importId
        if (importId.isEmpty()) return

        viewModelScope.launch {
            hubState.update { it.copy(isImportingSessions = true, importResult = null) }
            when (val result = absImportApi.importReadySessions(importId)) {
                is Success -> {
                    hubState.update {
                        it.copy(isImportingSessions = false, importResult = result.data)
                    }
                    loadSessions()
                    refreshImport()
                    // Refresh listening history to pull imported events
                    syncRepository.refreshListeningHistory()
                }

                is Failure -> {
                    logger.error { "Failed to import sessions: ${result.exception}" }
                    hubState.update {
                        it.copy(isImportingSessions = false, error = "Failed to import sessions")
                    }
                }
            }
        }
    }

    fun skipSession(
        sessionId: String,
        reason: String? = null,
    ) {
        val importId = hubState.value.importId
        if (importId.isEmpty()) return

        viewModelScope.launch {
            when (val result = absImportApi.skipSession(importId, sessionId, reason)) {
                is Success -> {
                    loadSessions()
                    refreshImport()
                }

                is Failure -> {
                    logger.error { "Failed to skip session: ${result.exception}" }
                    hubState.update { it.copy(error = "Failed to skip session") }
                }
            }
        }
    }

    fun clearError() {
        listState.update { it.copy(error = null) }
        hubState.update { it.copy(error = null) }
    }

    fun clearImportResult() {
        hubState.update { it.copy(importResult = null) }
    }

    private fun startAnalysisPolling(importId: String) {
        analysisPollingJob?.cancel()
        analysisPollingJob =
            viewModelScope.launch {
                while (true) {
                    delay(ANALYSIS_POLL_INTERVAL_MS)
                    when (val result = absImportApi.getImport(importId)) {
                        is Success -> {
                            val imp = result.data
                            // Update hub state if we're viewing this import
                            if (hubState.value.importId == importId) {
                                hubState.update { it.copy(import = imp) }
                            }
                            // Update list state
                            listState.update { state ->
                                state.copy(
                                    imports =
                                        state.imports.map { summary ->
                                            if (summary.id == importId) {
                                                ABSImportSummary(
                                                    id = imp.id,
                                                    name = imp.name,
                                                    status = imp.status,
                                                    createdAt = imp.createdAt,
                                                    updatedAt = imp.updatedAt,
                                                    totalUsers = imp.totalUsers,
                                                    totalBooks = imp.totalBooks,
                                                    totalSessions = imp.totalSessions,
                                                    usersMapped = imp.usersMapped,
                                                    booksMapped = imp.booksMapped,
                                                    sessionsImported = imp.sessionsImported,
                                                )
                                            } else {
                                                summary
                                            }
                                        },
                                )
                            }
                            if (imp.status != IMPORT_STATUS_ANALYZING) {
                                logger.info { "Import $importId analysis complete: ${imp.status}" }
                                break
                            }
                        }

                        is Failure -> {
                            logger.error { "Failed to poll import status: ${result.exception}" }
                            break
                        }
                    }
                }
            }
    }

    override fun onCleared() {
        super.onCleared()
        analysisPollingJob?.cancel()
    }
}
