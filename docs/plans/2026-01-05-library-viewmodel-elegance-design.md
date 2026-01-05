# Library ViewModel Elegance Refactor

## Overview

Refactor the 875-line `LibraryViewModel` (11 dependencies) into focused, composable components following the composition with shared state pattern.

## Phase A: LibraryViewModel Split

### Problem

`LibraryViewModel` handles too many concerns:
- Content lists (books, series, contributors)
- Sort state management
- Playback progress tracking
- Sync state
- Selection mode
- Admin collection actions
- Lens actions

This results in 11 constructor dependencies and cognitive overload.

### Solution: Composition with Shared State

```
┌─────────────────────────────────────────────────────────────────┐
│                    LIBRARY SCREEN                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────────┐    ┌─────────────────────────────────┐│
│  │ LibraryViewModel    │    │ LibrarySelectionManager         ││
│  │ (Content + Sorting) │    │ (Shared state holder)           ││
│  │                     │    │                                 ││
│  │ • books             │    │ • selectionMode: StateFlow      ││
│  │ • series            │    │ • enter/exit/toggle             ││
│  │ • authors           │◄───┤                                 ││
│  │ • narrators         │    └─────────────┬───────────────────┘│
│  │ • bookProgress      │                  │                    │
│  │ • sortStates        │                  ▼                    │
│  │ • syncState         │    ┌─────────────────────────────────┐│
│  └─────────────────────┘    │ LibraryActionsViewModel         ││
│                             │ (Batch operations)              ││
│                             │                                 ││
│                             │ • collections (admin)           ││
│                             │ • myLenses (all users)          ││
│                             │ • addToCollection()             ││
│                             │ • addToLens()                   ││
│                             │ • createLensAndAddBooks()       ││
│                             └─────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
```

### Components

#### 1. LibrarySelectionManager

A lightweight state holder (not a ViewModel) for selection state:

```kotlin
class LibrarySelectionManager {
    val selectionMode: StateFlow<SelectionMode>
        field = MutableStateFlow<SelectionMode>(SelectionMode.None)

    fun enterSelectionMode(initialBookId: String)
    fun toggleSelection(bookId: String)
    fun exitSelectionMode()
    fun clearAfterAction()
}
```

**Scoping:** Singleton - selection persists during navigation within app.

#### 2. LibraryViewModel (Slimmed)

Content-focused ViewModel:

**Responsibilities:**
- Books, series, authors, narrators lists
- Sort state management (4 sort states + persistence)
- Playback progress tracking
- Sync state observation
- Initial sync trigger
- Delegates selection actions to manager

**Dependencies (8, down from 11):**
- `bookRepository`
- `seriesDao`
- `contributorDao`
- `playbackPositionDao`
- `syncManager`
- `settingsRepository`
- `syncDao`
- `selectionManager`

#### 3. LibraryActionsViewModel

Batch operations ViewModel:

**Responsibilities:**
- Admin state (`isAdmin`)
- Collections list and refresh
- Lenses list
- Add to collection action
- Add to lens action
- Create lens and add books action

**Dependencies (6):**
- `selectionManager`
- `userDao`
- `collectionDao`
- `adminCollectionApi`
- `lensDao`
- `lensApi`

**Events:** `LibraryActionEvent` (renamed from `LibraryEvent` subset)

### UI Composition

```kotlin
@Composable
fun LibraryScreen(...) {
    val libraryViewModel: LibraryViewModel = koinViewModel()
    val actionsViewModel: LibraryActionsViewModel = koinViewModel()

    // Content state from LibraryViewModel
    val books by libraryViewModel.books.collectAsStateWithLifecycle()
    val selectionMode by libraryViewModel.selectionMode.collectAsStateWithLifecycle()

    // Action state from LibraryActionsViewModel
    val isAdmin by actionsViewModel.isAdmin.collectAsStateWithLifecycle()
    val collections by actionsViewModel.collections.collectAsStateWithLifecycle()

    // Bridge: notify actions VM when selection mode entered
    LaunchedEffect(selectionMode) {
        if (selectionMode is SelectionMode.Active) {
            actionsViewModel.onSelectionModeEntered()
        }
    }

    LibraryContent(
        books = books,
        selectionMode = selectionMode,
        onLongPress = libraryViewModel::enterSelectionMode,
        onAddToCollection = actionsViewModel::addSelectedToCollection,
        // ...
    )
}
```

### DI Setup

```kotlin
val libraryModule = module {
    // Shared state (singleton)
    single { LibrarySelectionManager() }

    // Content VM (singleton - preloads at AppShell)
    single { LibraryViewModel(..., selectionManager = get()) }

    // Actions VM (singleton - shares selection state)
    single { LibraryActionsViewModel(selectionManager = get(), ...) }
}
```

---

## Phase B: Additional Elegance Fixes

### B1. SettingsViewModel Unsafe Cast

Replace array indexing with type-safe named parameters in `combine()`.

### B2. LoginViewModel Error Mapper

Extract `Exception.toLoginErrorType()` to a dedicated `LoginErrorMapper` interface using HTTP status codes instead of string matching.

### B3. Manual Job Cancellation Patterns

Replace `Job?.cancel()` + reassign patterns with `flatMapLatest` where found.

---

## Implementation Order

### Phase A (LibraryViewModel Split)
1. Create `LibrarySelectionManager` class
2. Create `LibraryActionsViewModel` with extracted functionality
3. Slim down `LibraryViewModel` (remove action-related code)
4. Update `LibraryModule.kt` DI setup
5. Update `LibraryScreen` composable to use both ViewModels
6. Run tests, verify behavior unchanged

### Phase B (Additional Fixes)
1. Fix SettingsViewModel combine() cast
2. Extract LoginErrorMapper
3. Audit and fix Job cancellation patterns

---

## Success Criteria

- [ ] LibraryViewModel constructor reduced from 11 to 8 dependencies
- [ ] LibraryActionsViewModel has 6 focused dependencies
- [ ] Selection state shared correctly between ViewModels
- [ ] All existing Library tests pass
- [ ] UI behavior unchanged (selection, actions, sorting all work)
- [ ] No unsafe casts in SettingsViewModel
- [ ] LoginViewModel uses type-based error mapping
