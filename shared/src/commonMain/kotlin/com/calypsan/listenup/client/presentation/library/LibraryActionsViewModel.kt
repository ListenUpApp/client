package com.calypsan.listenup.client.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.data.local.db.CollectionDao
import com.calypsan.listenup.client.data.local.db.CollectionEntity
import com.calypsan.listenup.client.data.local.db.LensDao
import com.calypsan.listenup.client.data.local.db.LensEntity
import com.calypsan.listenup.client.data.local.db.UserDao
import com.calypsan.listenup.client.data.remote.AdminCollectionApiContract
import com.calypsan.listenup.client.data.remote.LensApiContract
import com.calypsan.listenup.client.data.remote.model.toTimestamp
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * ViewModel for Library batch actions (collections and lenses).
 *
 * Handles all batch operations on selected books:
 * - Adding books to admin collections
 * - Adding books to user lenses
 * - Creating new lenses with selected books
 *
 * Observes [LibrarySelectionManager] for the current selection state,
 * which is shared with [LibraryViewModel].
 */
class LibraryActionsViewModel(
    private val selectionManager: LibrarySelectionManager,
    private val userDao: UserDao,
    private val collectionDao: CollectionDao,
    private val adminCollectionApi: AdminCollectionApiContract,
    private val lensDao: LensDao,
    private val lensApi: LensApiContract,
) : ViewModel() {
    // ═══════════════════════════════════════════════════════════════════════
    // SELECTION STATE (observed from shared manager)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Current selection mode, observed from the shared [LibrarySelectionManager].
     */
    val selectionMode: StateFlow<SelectionMode> = selectionManager.selectionMode

    /**
     * Number of currently selected books.
     * Convenience property for UI display (e.g., "3 selected").
     */
    val selectedCount: StateFlow<Int> =
        selectionMode
            .map { mode ->
                when (mode) {
                    is SelectionMode.None -> 0
                    is SelectionMode.Active -> mode.selectedIds.size
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = 0,
            )

    // ═══════════════════════════════════════════════════════════════════════
    // ADMIN STATE
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Whether the current user is an admin (isRoot).
     * Only admins can use multi-select to add books to collections.
     */
    val isAdmin: StateFlow<Boolean> =
        userDao
            .observeCurrentUser()
            .map { user -> user?.isRoot == true }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = false,
            )

    /**
     * Observable list of collections for the collection picker.
     * Only relevant for admins.
     */
    val collections: StateFlow<List<CollectionEntity>> =
        collectionDao
            .observeAll()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

    // ═══════════════════════════════════════════════════════════════════════
    // LENS STATE (all users)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Observable list of the current user's lenses for the lens picker.
     * Available to all users (not just admins).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val myLenses: StateFlow<List<LensEntity>> =
        userDao
            .observeCurrentUser()
            .flatMapLatest { user ->
                if (user != null) {
                    lensDao.observeMyLenses(user.id)
                } else {
                    flowOf(emptyList())
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

    // ═══════════════════════════════════════════════════════════════════════
    // OPERATION STATE
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Whether an add-to-collection operation is in progress.
     */
    val isAddingToCollection: StateFlow<Boolean>
        field = MutableStateFlow(false)

    /**
     * Whether an add-to-lens operation is in progress.
     */
    val isAddingToLens: StateFlow<Boolean>
        field = MutableStateFlow(false)

    // ═══════════════════════════════════════════════════════════════════════
    // EVENTS
    // ═══════════════════════════════════════════════════════════════════════

    private val _events = MutableSharedFlow<LibraryActionEvent>()

    /**
     * One-time events for UI feedback (snackbars, toasts).
     */
    val events = _events.asSharedFlow()

    // ═══════════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Called when selection mode is entered.
     * Refreshes collections for admins to ensure picker has up-to-date data.
     */
    fun onSelectionModeEntered() {
        if (isAdmin.value) {
            refreshCollections()
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // COLLECTION ACTIONS (admin only)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Add all selected books to the specified collection.
     * Calls the AdminCollectionApi and emits success/error events.
     *
     * @param collectionId The ID of the collection to add books to
     */
    fun addSelectedToCollection(collectionId: String) {
        val selectedIds = selectionManager.getSelectedIds()
        if (selectedIds.isEmpty()) return

        viewModelScope.launch {
            isAddingToCollection.value = true
            try {
                val bookIds = selectedIds.toList()
                adminCollectionApi.addBooks(collectionId, bookIds)
                logger.info { "Added ${bookIds.size} books to collection $collectionId" }
                _events.emit(LibraryActionEvent.BooksAddedToCollection(bookIds.size))
                selectionManager.clearAfterAction()
            } catch (e: Exception) {
                logger.error(e) { "Failed to add books to collection" }
                _events.emit(LibraryActionEvent.AddToCollectionFailed(e.message ?: "Unknown error"))
            } finally {
                isAddingToCollection.value = false
            }
        }
    }

    /**
     * Refresh collections from the server API.
     * Syncs the local database with the latest server state including book counts.
     */
    private fun refreshCollections() {
        viewModelScope.launch {
            try {
                val serverCollections = adminCollectionApi.getCollections()
                logger.debug { "Fetched ${serverCollections.size} collections from server" }

                // Update local database with server data
                serverCollections.forEach { response ->
                    val entity = CollectionEntity(
                        id = response.id,
                        name = response.name,
                        bookCount = response.bookCount,
                        createdAt = response.createdAt.toTimestamp(),
                        updatedAt = response.updatedAt.toTimestamp(),
                    )
                    collectionDao.upsert(entity)
                }

                // Delete local collections that no longer exist on server
                val serverIds = serverCollections.map { it.id }.toSet()
                val localCollections = collectionDao.getAll()
                localCollections.filter { it.id !in serverIds }.forEach { orphan ->
                    logger.debug { "Removing orphaned collection: ${orphan.name} (${orphan.id})" }
                    collectionDao.deleteById(orphan.id)
                }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to refresh collections from server" }
                // Don't emit error - local data is still usable
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // LENS ACTIONS (all users)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Add all selected books to the specified lens.
     * Calls the LensApi and emits success/error events.
     *
     * @param lensId The ID of the lens to add books to
     */
    fun addSelectedToLens(lensId: String) {
        val selectedIds = selectionManager.getSelectedIds()
        if (selectedIds.isEmpty()) return

        viewModelScope.launch {
            isAddingToLens.value = true
            try {
                val bookIds = selectedIds.toList()
                lensApi.addBooks(lensId, bookIds)
                logger.info { "Added ${bookIds.size} books to lens $lensId" }
                _events.emit(LibraryActionEvent.BooksAddedToLens(bookIds.size))
                selectionManager.clearAfterAction()
            } catch (e: Exception) {
                logger.error(e) { "Failed to add books to lens" }
                _events.emit(LibraryActionEvent.AddToLensFailed(e.message ?: "Unknown error"))
            } finally {
                isAddingToLens.value = false
            }
        }
    }

    /**
     * Create a new lens and add all selected books to it.
     * First creates the lens via API, then adds the books.
     *
     * @param name The name for the new lens
     */
    fun createLensAndAddBooks(name: String) {
        val selectedIds = selectionManager.getSelectedIds()
        if (selectedIds.isEmpty()) return

        viewModelScope.launch {
            isAddingToLens.value = true
            try {
                val bookIds = selectedIds.toList()
                // Create the lens
                val newLens = lensApi.createLens(name, null)
                logger.info { "Created lens '${newLens.name}' with id ${newLens.id}" }
                // Add books to the new lens
                lensApi.addBooks(newLens.id, bookIds)
                logger.info { "Added ${bookIds.size} books to new lens ${newLens.id}" }
                _events.emit(LibraryActionEvent.LensCreatedAndBooksAdded(newLens.name, bookIds.size))
                selectionManager.clearAfterAction()
            } catch (e: Exception) {
                logger.error(e) { "Failed to create lens and add books" }
                _events.emit(LibraryActionEvent.AddToLensFailed(e.message ?: "Unknown error"))
            } finally {
                isAddingToLens.value = false
            }
        }
    }
}

/**
 * One-time events emitted by [LibraryActionsViewModel] for UI feedback.
 */
sealed interface LibraryActionEvent {
    /**
     * Books were successfully added to a collection.
     */
    data class BooksAddedToCollection(val count: Int) : LibraryActionEvent

    /**
     * Failed to add books to a collection.
     */
    data class AddToCollectionFailed(val message: String) : LibraryActionEvent

    /**
     * Books were successfully added to a lens.
     */
    data class BooksAddedToLens(val count: Int) : LibraryActionEvent

    /**
     * A new lens was created and books were added to it.
     */
    data class LensCreatedAndBooksAdded(val lensName: String, val bookCount: Int) : LibraryActionEvent

    /**
     * Failed to add books to a lens.
     */
    data class AddToLensFailed(val message: String) : LibraryActionEvent
}
