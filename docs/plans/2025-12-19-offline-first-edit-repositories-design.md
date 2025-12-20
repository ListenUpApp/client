# Offline-First Edit Repositories Design

## Overview

Convert edit repositories from server-first to offline-first pattern. Users can edit books, series, and contributors while offline. Changes are queued and synced when connectivity is restored.

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Edit Repository                              │
│  1. Optimistic Update: Update local entity (syncState = PENDING)    │
│  2. Queue Operation: PendingOperationRepository.queueOrCoalesce()   │
│  3. Return Success: Immediately                                     │
└─────────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    PendingOperationRepository                       │
│  - Stores operation in pending_operations table                     │
│  - Coalesces if same entity+type already pending                    │
└─────────────────────────────────────────────────────────────────────┘
                                │
                                ▼ (When online)
┌─────────────────────────────────────────────────────────────────────┐
│                      PushSyncOrchestrator                           │
│  - Checks for conflicts (serverVersion > lastModified)              │
│  - Executes via OperationHandler                                    │
│  - Marks operation completed or failed                              │
└─────────────────────────────────────────────────────────────────────┘
```

## Repositories to Convert

### 1. BookEditRepository

**Operations:**
- `updateBook(bookId, update)` - Update book metadata
- `setBookContributors(bookId, contributors)` - Replace book contributors
- `setBookSeries(bookId, series)` - Replace book series

**Changes:**
- Remove `api: ListenUpApiContract` dependency
- Add `pendingOperationRepository: PendingOperationRepositoryContract`
- Add handlers: `BookUpdateHandler`, `SetBookContributorsHandler`, `SetBookSeriesHandler`
- Return `Result<Unit>` instead of `Result<BookEditResponse>`

### 2. SeriesEditRepository

**Operations:**
- `updateSeries(seriesId, name, description)` - Update series metadata

**Changes:**
- Remove `api: ListenUpApiContract` dependency
- Add `pendingOperationRepository: PendingOperationRepositoryContract`
- Add `seriesUpdateHandler: SeriesUpdateHandler`
- Return `Result<Unit>` instead of `Result<SeriesEditResponse>`

### 3. ContributorEditRepository (New)

Extract from `ContributorEditViewModel` into a new repository.

**Operations:**
- `updateContributor(contributorId, update)` - Update contributor metadata
- `mergeContributor(targetId, sourceId)` - Merge source into target
- `unmergeContributor(contributorId, aliasName)` - Split alias into new contributor

**Changes:**
- Create new `ContributorEditRepository` class
- ViewModel delegates to repository
- Add handlers: `ContributorUpdateHandler`, `MergeContributorHandler`, `UnmergeContributorHandler`

## Implementation Pattern

### Simple Updates (Book, Series, Contributor metadata)

```kotlin
override suspend fun updateBook(bookId: String, update: BookUpdateRequest): Result<Unit> =
    withContext(IODispatcher) {
        // 1. Get existing entity
        val existing = bookDao.getById(BookId(bookId))
            ?: return@withContext Failure(Exception("Book not found"))

        // 2. Apply optimistic update
        val updated = existing.copy(
            title = update.title ?: existing.title,
            subtitle = update.subtitle ?: existing.subtitle,
            description = update.description ?: existing.description,
            // ... other fields
            syncState = SyncState.PENDING,
            lastModified = Timestamp.now(),
        )
        bookDao.upsert(updated)

        // 3. Queue operation (coalesces with existing pending update)
        val payload = BookUpdatePayload(
            title = update.title,
            subtitle = update.subtitle,
            description = update.description,
            // ... other fields
        )
        pendingOperationRepository.queueOrCoalesce(
            operationType = OperationType.BOOK_UPDATE,
            entityType = EntityType.BOOK,
            entityId = bookId,
            payload = bookUpdateHandler.serializePayload(payload),
            handler = bookUpdateHandler,
        )

        logger.info { "Book update queued: $bookId" }
        Success(Unit)
    }
```

### Set Book Contributors

```kotlin
override suspend fun setBookContributors(
    bookId: String,
    contributors: List<ContributorInput>,
): Result<Unit> = withContext(IODispatcher) {
    // 1. Update local book-contributor relationships
    bookContributorDao.deleteByBook(bookId)
    contributors.forEach { input ->
        val existingContributor = contributorDao.findByName(input.name)
        if (existingContributor != null) {
            bookContributorDao.insert(
                BookContributorCrossRef(
                    bookId = bookId,
                    contributorId = existingContributor.id,
                    role = input.roles.firstOrNull() ?: "Author",
                    creditedAs = input.name,
                )
            )
        }
        // New contributors created by server on sync
    }

    // 2. Mark book as pending
    bookDao.updateSyncState(bookId, SyncState.PENDING)

    // 3. Queue operation
    val payload = SetBookContributorsPayload(
        contributors = contributors.map { ContributorInputPayload(it.name, it.roles) }
    )
    pendingOperationRepository.queueOrCoalesce(
        operationType = OperationType.SET_BOOK_CONTRIBUTORS,
        entityType = EntityType.BOOK,
        entityId = bookId,
        payload = setBookContributorsHandler.serializePayload(payload),
        handler = setBookContributorsHandler,
    )

    Success(Unit)
}
```

### Merge Contributor

```kotlin
suspend fun mergeContributor(targetId: String, sourceId: String): Result<Unit> =
    withContext(IODispatcher) {
        val source = contributorDao.getById(sourceId)
            ?: return@withContext Failure(Exception("Source contributor not found"))
        val target = contributorDao.getById(targetId)
            ?: return@withContext Failure(Exception("Target contributor not found"))

        // 1. Re-link book relationships from source to target
        val sourceRelations = bookContributorDao.getByContributorId(sourceId)
        for (relation in sourceRelations) {
            val newRelation = BookContributorCrossRef(
                bookId = relation.bookId,
                contributorId = targetId,
                role = relation.role,
                creditedAs = relation.creditedAs ?: source.name,
            )
            bookContributorDao.insert(newRelation)
            bookContributorDao.delete(relation.bookId, sourceId, relation.role)
        }

        // 2. Update target's aliases
        val currentAliases = target.aliasList()
        val newAliases = (currentAliases + source.name).distinct()
        contributorDao.upsert(target.copy(
            aliases = newAliases.joinToString(", "),
            syncState = SyncState.PENDING,
            lastModified = Timestamp.now(),
        ))

        // 3. Delete source contributor locally
        contributorDao.deleteById(sourceId)

        // 4. Queue merge operation
        val payload = MergeContributorPayload(targetId = targetId, sourceId = sourceId)
        pendingOperationRepository.queue(
            operationType = OperationType.MERGE_CONTRIBUTOR,
            entityType = EntityType.CONTRIBUTOR,
            entityId = targetId,
            payload = mergeContributorHandler.serializePayload(payload),
        )

        Success(Unit)
    }
```

### Unmerge Contributor

```kotlin
suspend fun unmergeContributor(contributorId: String, aliasName: String): Result<Unit> =
    withContext(IODispatcher) {
        val contributor = contributorDao.getById(contributorId)
            ?: return@withContext Failure(Exception("Contributor not found"))

        // 1. Create placeholder contributor with temporary ID
        val tempId = "temp_${UUID.randomUUID()}"
        val newContributor = ContributorEntity(
            id = tempId,
            name = aliasName,
            syncState = SyncState.PENDING,
            lastModified = Timestamp.now(),
            // ... other fields null
        )
        contributorDao.upsert(newContributor)

        // 2. Re-link book relationships where creditedAs matches aliasName
        val relations = bookContributorDao.getByContributorId(contributorId)
        for (relation in relations) {
            if (relation.creditedAs?.equals(aliasName, ignoreCase = true) == true) {
                val newRelation = relation.copy(contributorId = tempId)
                bookContributorDao.insert(newRelation)
                bookContributorDao.delete(relation.bookId, contributorId, relation.role)
            }
        }

        // 3. Remove alias from original contributor
        val currentAliases = contributor.aliasList()
        val updatedAliases = currentAliases.filter { !it.equals(aliasName, ignoreCase = true) }
        contributorDao.upsert(contributor.copy(
            aliases = updatedAliases.takeIf { it.isNotEmpty() }?.joinToString(", "),
            syncState = SyncState.PENDING,
            lastModified = Timestamp.now(),
        ))

        // 4. Queue unmerge operation (includes tempId for reconciliation)
        val payload = UnmergeContributorPayload(
            contributorId = contributorId,
            aliasName = aliasName,
            tempLocalId = tempId,
        )
        pendingOperationRepository.queue(
            operationType = OperationType.UNMERGE_CONTRIBUTOR,
            entityType = EntityType.CONTRIBUTOR,
            entityId = contributorId,
            payload = unmergeContributorHandler.serializePayload(payload),
        )

        Success(Unit)
    }
```

## Error Handling

### Network Errors (Retryable)
- Operation stays in queue with incremented `attemptCount`
- Retried on next flush (up to MAX_RETRIES = 3)
- Entity stays `syncState = PENDING`

### Conflicts (Server Version Newer)
- Operation marked `FAILED` with "Conflict: ..." reason
- Entity stays `syncState = PENDING`
- UI shows conflict indicator via SyncIndicatorViewModel

### Max Retries Exceeded
- Operation marked `FAILED`
- Entity stays `syncState = PENDING`
- User can manually retry or discard changes

## Unmerge ID Reconciliation

When unmerge succeeds on server, the handler receives the real contributor ID:

```kotlin
// In UnmergeContributorHandler.execute()
override suspend fun execute(operation: PendingOperationEntity, payload: UnmergeContributorPayload): Result<Unit> {
    return when (val result = api.unmergeContributor(payload.contributorId, payload.aliasName)) {
        is Success -> {
            // Update local entity with real server ID
            val realId = result.data.id
            contributorDao.updateId(payload.tempLocalId, realId)
            bookContributorDao.updateContributorId(payload.tempLocalId, realId)
            Success(Unit)
        }
        is Failure -> Failure(result.exception)
    }
}
```

## Testing Strategy

1. **Unit tests** for each repository method
2. **Integration tests** verifying queue + coalesce behavior
3. **Edge cases:**
   - Edit while offline → come online → verify sync
   - Multiple edits to same entity → verify coalescing
   - Conflict scenarios → verify proper failure state
   - Merge/unmerge → verify relationship re-linking

## Migration Notes

- Existing `BookEditRepositoryContract` return type changes from `Result<BookEditResponse>` to `Result<Unit>`
- ViewModels consuming these repositories need minor updates
- No database migration needed (uses existing `pending_operations` table)
