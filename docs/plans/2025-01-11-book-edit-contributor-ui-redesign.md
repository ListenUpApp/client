# Book Edit Contributor UI Redesign

## Overview

Redesign the contributor section of the Book Edit screen to use per-role sections with dedicated search fields, replacing the current single search box with role filter chips.

## Current State

- Single contributor search box
- "Add as:" filter chips to select Author/Narrator before searching
- Search results shown in dropdown, click to add
- Contributors displayed as chips grouped by role

## New Design

### Structure

Each role present on the book gets its own section:

```
Authors
┌─────────────────────────────────────────────┐
│ [Stephen King ×]  [Peter Straub ×]          │  ← Removable chips
│                                             │
│ ┌─────────────────────────────────────────┐ │
│ │ 🔍 Add author...                        │ │  ← Search field
│ └─────────────────────────────────────────┘ │
│   ┌─────────────────────────────────────┐   │
│   │ Stephen Fry        (12 books)       │   │  ← Autocomplete dropdown
│   │ Stephen Hawking    (3 books)        │   │
│   └─────────────────────────────────────┘   │
└─────────────────────────────────────────────┘

Narrators
┌─────────────────────────────────────────────┐
│ [Frank Muller ×]                            │
│ ┌─────────────────────────────────────────┐ │
│ │ 🔍 Add narrator...                      │ │
│ └─────────────────────────────────────────┘ │
└─────────────────────────────────────────────┘

[ + Add Role ]  ← Button to add new role section
```

### Interaction Flow

**Adding a contributor:**
1. User types in the role's search box (e.g., "Add author...")
2. After 2+ characters, autocomplete dropdown appears
3. User either:
   - **Clicks a result** → Added as chip, search clears, dropdown closes
   - **Presses Enter** → Adds top result if exists, otherwise adds typed name as new contributor
4. Search box remains visible for adding more

**Removing a contributor:**
- Click the × on any chip to remove

**Adding a new role section:**
1. User clicks "+ Add Role" button
2. Menu shows roles not yet present (e.g., if only Authors exist, shows "Narrator")
3. Selecting creates new section with empty search box

**Edge cases:**
- Contributors already added are filtered from autocomplete results for that role
- Empty sections show just the search box with placeholder
- Sections persist even when emptied (cleaned on save if still empty)

## Design System Primitives

### Existing
- `ListenUpTextField` - single-line text input (for title, subtitle, series sequence, publish year)

### New Components

#### ListenUpTextArea
Multi-line text input for description field.

```kotlin
@Composable
fun ListenUpTextArea(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    minLines: Int = 3,
    maxLines: Int = 6,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
)
```

#### ListenUpSearchField
Search input with autocomplete dropdown support.

```kotlin
@Composable
fun ListenUpSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,  // Called on Enter key
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    leadingIcon: @Composable (() -> Unit)? = { SearchIcon() },
    trailingIcon: @Composable (() -> Unit)? = null,  // Clear button when has text
)
```

#### ListenUpAutocompleteField
Combines search field with dropdown results.

```kotlin
@Composable
fun <T> ListenUpAutocompleteField(
    value: String,
    onValueChange: (String) -> Unit,
    results: List<T>,
    onResultSelected: (T) -> Unit,
    onSubmit: (String) -> Unit,  // Enter with no selection
    resultContent: @Composable (T) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
)
```

## State Changes

### BookEditUiState

```kotlin
data class BookEditUiState(
    // ... existing fields ...

    // Replace single search state with per-role state
    val roleSearchQueries: Map<ContributorRole, String> = emptyMap(),
    val roleSearchResults: Map<ContributorRole, List<ContributorSearchResult>> = emptyMap(),
    val roleSearchLoading: Map<ContributorRole, Boolean> = emptyMap(),

    // Track visible role sections (prepopulated from existing + user-added)
    val visibleRoles: Set<ContributorRole> = emptySet(),
)
```

### BookEditUiEvent

```kotlin
sealed interface BookEditUiEvent {
    // ... existing events ...

    // Replace single search events with role-scoped events
    data class RoleSearchQueryChanged(val role: ContributorRole, val query: String) : BookEditUiEvent
    data class RoleContributorSelected(val role: ContributorRole, val result: ContributorSearchResult) : BookEditUiEvent
    data class RoleContributorEntered(val role: ContributorRole, val name: String) : BookEditUiEvent
    data class ClearRoleSearch(val role: ContributorRole) : BookEditUiEvent
    data class AddRoleSection(val role: ContributorRole) : BookEditUiEvent
}
```

## Implementation Plan

### Phase 1: Design System Primitives
1. Create `ListenUpTextArea` component
2. Create `ListenUpSearchField` component
3. Create `ListenUpAutocompleteField` component
4. Add previews for all new components

### Phase 2: ViewModel Updates
1. Update `BookEditUiState` with per-role search state
2. Update `BookEditUiEvent` with role-scoped events
3. Update `BookEditViewModel` to handle per-role debounced search
4. Initialize `visibleRoles` from existing contributors on load

### Phase 3: UI Implementation
1. Create `RoleContributorSection` composable
2. Create `AddRoleButton` composable with dropdown menu
3. Update `BookEditScreen` to use new components
4. Replace raw `OutlinedTextField` with design system components

### Phase 4: Bug Fix
1. Investigate description not prepopulating (may be server data issue)
2. Replace metadata `OutlinedTextField` with `ListenUpTextField` / `ListenUpTextArea`

## Files to Modify

**New files:**
- `design/components/ListenUpTextArea.kt`
- `design/components/ListenUpSearchField.kt`
- `design/components/ListenUpAutocompleteField.kt`

**Modified files:**
- `shared/.../presentation/bookedit/BookEditViewModel.kt`
- `composeApp/.../features/bookedit/BookEditScreen.kt`
