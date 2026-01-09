# iOS Foundation Refactor

## Problem Statement

The current iOS app shell has accumulated technical debt:
- Duplicated observer patterns (AuthStateObserver, CurrentUserObserver)
- Multiple observers for the same data (violates single source of truth)
- Navigation types buried in view files
- Dead code and unused convenience properties
- Not using iOS 17's @Environment for dependency injection
- Repetitive tab construction

## Goals

1. **Single source of truth** for user data
2. **DRY** observer pattern
3. **Clean architecture** with proper file organization
4. **iOS 17+ best practices** using @Environment
5. **Zero dead code**

## Implementation Plan

### Phase 1: Extract Navigation Infrastructure

**File: `Navigation/Destinations.swift`**
- Move all destination types from MainTabView.swift
- Single enum-based approach for type safety

### Phase 2: Consolidate User Observation

**Problem:** MainTabView and UserProfileView both create CurrentUserObserver instances.

**Solution:**
- Create CurrentUserObserver once in RootView
- Pass via @Environment to all descendants
- Remove duplicate observer from UserProfileView

### Phase 3: Clean Up Observer Classes

**AuthStateObserver:**
- Keep as-is (it's the auth state machine, has unique logic)

**CurrentUserObserver:**
- Remove unused convenience properties (displayName, initials, avatarColor, hasImageAvatar)
- Views should access `user?.property` directly

### Phase 4: Clean Up UserAvatarView

- Remove dead code path for image avatars (both branches do same thing)
- Simplify to two cases: user with initials, placeholder

### Phase 5: Extract Tab Builder

- Create helper to reduce repetition in MainTabView
- Single place to configure tab structure

### Phase 6: Fix UserId String Conversion

- Investigate proper SKIE bridge for Kotlin value classes
- If not available, document the String(describing:) workaround clearly

## File Changes Summary

| File | Action |
|------|--------|
| `Navigation/Destinations.swift` | CREATE - navigation types |
| `MainTabView.swift` | MODIFY - remove destinations, add tab builder |
| `iOSApp.swift` | MODIFY - add user observer to environment |
| `CurrentUserObserver.swift` | MODIFY - remove dead code |
| `UserAvatarView.swift` | MODIFY - simplify branches |
| `UserProfileView.swift` | MODIFY - use @Environment |
| `HomeView.swift` | MODIFY - use @Environment |
| `LibraryView.swift` | MODIFY - use @Environment |
| `DiscoverView.swift` | MODIFY - use @Environment |

## Success Criteria

- [ ] Single CurrentUserObserver instance in entire app
- [ ] All views access user via @Environment
- [ ] Navigation types in dedicated file
- [ ] No dead code
- [ ] Build succeeds
- [ ] App runs correctly
