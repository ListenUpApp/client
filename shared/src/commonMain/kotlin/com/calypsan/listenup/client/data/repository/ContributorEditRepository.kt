@file:Suppress("StringLiteralDuplication")

package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.core.ContributorId
import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.IODispatcher
import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.core.Timestamp
import com.calypsan.listenup.client.data.local.db.BookContributorCrossRef
import com.calypsan.listenup.client.data.local.db.BookContributorDao
import com.calypsan.listenup.client.data.local.db.ContributorDao
import com.calypsan.listenup.client.data.local.db.ContributorEntity
import com.calypsan.listenup.client.data.local.db.EntityType
import com.calypsan.listenup.client.data.local.db.OperationType
import com.calypsan.listenup.client.data.local.db.SyncState
import com.calypsan.listenup.client.data.sync.push.ContributorUpdateHandler
import com.calypsan.listenup.client.data.sync.push.ContributorUpdatePayload
import com.calypsan.listenup.client.data.sync.push.MergeContributorHandler
import com.calypsan.listenup.client.data.sync.push.MergeContributorPayload
import com.calypsan.listenup.client.data.sync.push.PendingOperationRepositoryContract
import com.calypsan.listenup.client.data.sync.push.UnmergeContributorHandler
import com.calypsan.listenup.client.data.sync.push.UnmergeContributorPayload
import com.calypsan.listenup.client.util.NanoId
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.withContext

private val logger = KotlinLogging.logger {}

/**
 * Data class for contributor update request.
 */
data class ContributorUpdateRequest(
    val name: String? = null,
    val biography: String? = null,
    val website: String? = null,
    val birthDate: String? = null,
    val deathDate: String? = null,
    val aliases: List<String>? = null,
    val imagePath: String? = null,
)

/**
 * Contract for contributor editing operations.
 *
 * Provides methods for modifying contributor metadata and managing aliases.
 * Uses offline-first pattern: changes are applied locally immediately
 * and queued for sync to server.
 */
interface ContributorEditRepositoryContract {
    /**
     * Update contributor metadata.
     *
     * Applies update locally and queues for server sync.
     * Only non-null fields are updated (PATCH semantics).
     *
     * @param contributorId ID of the contributor to update
     * @param update Fields to update
     * @return Result indicating success or failure
     */
    suspend fun updateContributor(
        contributorId: String,
        update: ContributorUpdateRequest,
    ): Result<Unit>

    /**
     * Merge a source contributor into a target contributor.
     *
     * Local effects (applied immediately):
     * - Re-links all book relationships from source to target (with creditedAs)
     * - Adds source name to target's aliases
     * - Deletes source contributor
     *
     * Server sync will perform the same operations on the server side.
     *
     * @param targetId ID of the target contributor (receives the merge)
     * @param sourceId ID of the source contributor (will be deleted)
     * @return Result indicating success or failure
     */
    suspend fun mergeContributor(
        targetId: String,
        sourceId: String,
    ): Result<Unit>

    /**
     * Unmerge an alias from a contributor, creating a new contributor.
     *
     * Local effects (applied immediately):
     * - Creates new contributor with the alias name (temporary local ID)
     * - Re-links book relationships where creditedAs matches the alias
     * - Removes alias from original contributor
     *
     * Server sync will create the real contributor and we'll update the local ID.
     *
     * @param contributorId ID of the contributor to unmerge from
     * @param aliasName Name of the alias to split out
     * @return Result indicating success or failure
     */
    suspend fun unmergeContributor(
        contributorId: String,
        aliasName: String,
    ): Result<Unit>
}

/**
 * Repository for contributor editing operations using offline-first pattern.
 *
 * Handles the edit flow:
 * 1. Apply optimistic update to local database
 * 2. Queue operation for server sync via PendingOperationRepository
 * 3. Return success immediately
 *
 * @property contributorDao Room DAO for contributor operations
 * @property bookContributorDao Room DAO for book-contributor relationships
 * @property pendingOperationRepository Repository for queuing sync operations
 * @property contributorUpdateHandler Handler for contributor update operations
 * @property mergeContributorHandler Handler for merge operations
 * @property unmergeContributorHandler Handler for unmerge operations
 */
class ContributorEditRepository(
    private val contributorDao: ContributorDao,
    private val bookContributorDao: BookContributorDao,
    private val pendingOperationRepository: PendingOperationRepositoryContract,
    private val contributorUpdateHandler: ContributorUpdateHandler,
    private val mergeContributorHandler: MergeContributorHandler,
    private val unmergeContributorHandler: UnmergeContributorHandler,
) : ContributorEditRepositoryContract,
    com.calypsan.listenup.client.domain.repository.ContributorEditRepository {
    // ========== Domain Interface Implementation ==========

    /**
     * Domain interface method - adapts to internal updateContributor implementation.
     */
    override suspend fun updateContributor(
        contributorId: String,
        name: String?,
        biography: String?,
        website: String?,
        birthDate: String?,
        deathDate: String?,
        aliases: List<String>?,
    ): Result<Unit> =
        updateContributor(
            contributorId,
            ContributorUpdateRequest(
                name = name,
                biography = biography,
                website = website,
                birthDate = birthDate,
                deathDate = deathDate,
                aliases = aliases,
            ),
        )

    // ========== Data Layer Contract Implementation ==========

    /**
     * Update contributor metadata.
     */
    override suspend fun updateContributor(
        contributorId: String,
        update: ContributorUpdateRequest,
    ): Result<Unit> =
        withContext(IODispatcher) {
            logger.debug { "Updating contributor (offline-first): $contributorId" }

            // Get existing contributor
            val existing = contributorDao.getById(contributorId)
            if (existing == null) {
                logger.error { "Contributor not found: $contributorId" }
                return@withContext Failure(Exception("Contributor not found: $contributorId"))
            }

            // Apply optimistic update
            val updated =
                existing.copy(
                    name = update.name ?: existing.name,
                    description = update.biography ?: existing.description,
                    website = update.website ?: existing.website,
                    birthDate = update.birthDate ?: existing.birthDate,
                    deathDate = update.deathDate ?: existing.deathDate,
                    aliases = update.aliases?.joinToString(", ") ?: existing.aliases,
                    imagePath = update.imagePath ?: existing.imagePath,
                    syncState = SyncState.NOT_SYNCED,
                    lastModified = Timestamp.now(),
                )
            contributorDao.upsert(updated)

            // Queue operation
            val payload =
                ContributorUpdatePayload(
                    name = update.name,
                    biography = update.biography,
                    website = update.website,
                    birthDate = update.birthDate,
                    deathDate = update.deathDate,
                    aliases = update.aliases,
                )
            pendingOperationRepository.queue(
                type = OperationType.CONTRIBUTOR_UPDATE,
                entityType = EntityType.CONTRIBUTOR,
                entityId = contributorId,
                payload = payload,
                handler = contributorUpdateHandler,
            )

            logger.info { "Contributor update queued: $contributorId" }
            Success(Unit)
        }

    /**
     * Merge a source contributor into a target contributor.
     */
    override suspend fun mergeContributor(
        targetId: String,
        sourceId: String,
    ): Result<Unit> =
        withContext(IODispatcher) {
            logger.debug { "Merging contributor $sourceId into $targetId (offline-first)" }

            val target = contributorDao.getById(targetId)
            if (target == null) {
                logger.error { "Target contributor not found: $targetId" }
                return@withContext Failure(Exception("Target contributor not found: $targetId"))
            }

            val source = contributorDao.getById(sourceId)
            if (source == null) {
                logger.error { "Source contributor not found: $sourceId" }
                return@withContext Failure(Exception("Source contributor not found: $sourceId"))
            }

            if (targetId == sourceId) {
                logger.error { "Cannot merge contributor into itself" }
                return@withContext Failure(Exception("Cannot merge contributor into itself"))
            }

            // 1. Re-link book relationships from source to target
            val sourceRelations = bookContributorDao.getByContributorId(sourceId)
            for (relation in sourceRelations) {
                // Check if target already has this book/role
                val existingTarget =
                    bookContributorDao.get(
                        relation.bookId,
                        targetId,
                        relation.role,
                    )
                if (existingTarget == null) {
                    // Create new relationship with creditedAs preserving original name
                    val newRelation =
                        BookContributorCrossRef(
                            bookId = relation.bookId,
                            contributorId = ContributorId(targetId),
                            role = relation.role,
                            creditedAs = relation.creditedAs ?: source.name,
                        )
                    bookContributorDao.insert(newRelation)
                }
                // Delete old relationship
                bookContributorDao.delete(relation.bookId, sourceId, relation.role)
            }

            // 2. Update target's aliases to include source name
            val currentAliases = target.aliasList()
            val newAliases = (currentAliases + source.name).distinct()
            val updatedTarget =
                target.copy(
                    aliases = newAliases.joinToString(", "),
                    syncState = SyncState.NOT_SYNCED,
                    lastModified = Timestamp.now(),
                )
            contributorDao.upsert(updatedTarget)

            // 3. Delete source contributor locally
            contributorDao.deleteById(sourceId)

            // 4. Queue merge operation
            val payload =
                MergeContributorPayload(
                    targetId = targetId,
                    sourceId = sourceId,
                )
            pendingOperationRepository.queue(
                type = OperationType.MERGE_CONTRIBUTOR,
                entityType = EntityType.CONTRIBUTOR,
                entityId = targetId,
                payload = payload,
                handler = mergeContributorHandler,
            )

            logger.info { "Contributor merge queued: $sourceId -> $targetId" }
            Success(Unit)
        }

    /**
     * Unmerge an alias from a contributor, creating a new contributor.
     */
    override suspend fun unmergeContributor(
        contributorId: String,
        aliasName: String,
    ): Result<Unit> =
        withContext(IODispatcher) {
            logger.debug { "Unmerging alias '$aliasName' from contributor $contributorId (offline-first)" }

            val contributor = contributorDao.getById(contributorId)
            if (contributor == null) {
                logger.error { "Contributor not found: $contributorId" }
                return@withContext Failure(Exception("Contributor not found: $contributorId"))
            }

            // Check alias exists
            val aliases = contributor.aliasList()
            if (!aliases.any { it.equals(aliasName, ignoreCase = true) }) {
                logger.error { "Alias '$aliasName' not found for contributor $contributorId" }
                return@withContext Failure(Exception("Alias not found: $aliasName"))
            }

            // 1. Create placeholder contributor with temporary ID
            val tempId = ContributorId(NanoId.generate("temp"))
            val newContributor =
                ContributorEntity(
                    id = tempId,
                    name = aliasName,
                    description = null,
                    imagePath = null,
                    syncState = SyncState.NOT_SYNCED,
                    lastModified = Timestamp.now(),
                    serverVersion = null,
                    createdAt = Timestamp.now(),
                    updatedAt = Timestamp.now(),
                    website = null,
                    birthDate = null,
                    deathDate = null,
                    aliases = null,
                )
            contributorDao.upsert(newContributor)

            // 2. Re-link book relationships where creditedAs matches aliasName
            val relations = bookContributorDao.getByContributorId(contributorId)
            for (relation in relations) {
                if (relation.creditedAs?.equals(aliasName, ignoreCase = true) == true) {
                    // Create new relationship pointing to new contributor
                    val newRelation =
                        BookContributorCrossRef(
                            bookId = relation.bookId,
                            contributorId = tempId,
                            role = relation.role,
                            creditedAs = null, // New contributor's name matches creditedAs
                        )
                    bookContributorDao.insert(newRelation)
                    // Delete old relationship
                    bookContributorDao.delete(relation.bookId, contributorId, relation.role)
                }
            }

            // 3. Remove alias from original contributor
            val updatedAliases = aliases.filter { !it.equals(aliasName, ignoreCase = true) }
            val updatedContributor =
                contributor.copy(
                    aliases = updatedAliases.takeIf { it.isNotEmpty() }?.joinToString(", "),
                    syncState = SyncState.NOT_SYNCED,
                    lastModified = Timestamp.now(),
                )
            contributorDao.upsert(updatedContributor)

            // 4. Queue unmerge operation
            val payload =
                UnmergeContributorPayload(
                    contributorId = contributorId,
                    aliasName = aliasName,
                )
            pendingOperationRepository.queue(
                type = OperationType.UNMERGE_CONTRIBUTOR,
                entityType = EntityType.CONTRIBUTOR,
                entityId = contributorId,
                payload = payload,
                handler = unmergeContributorHandler,
            )

            logger.info { "Contributor unmerge queued: '$aliasName' from $contributorId (temp ID: $tempId)" }
            Success(Unit)
        }
}
