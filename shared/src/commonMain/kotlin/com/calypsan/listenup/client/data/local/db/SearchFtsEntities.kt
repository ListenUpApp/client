package com.calypsan.listenup.client.data.local.db

/**
 * Data classes for FTS5 full-text search entries.
 *
 * These are NOT Room entities - FTS5 virtual tables are created manually
 * in migrations and accessed via raw SQL queries in SearchDao.
 *
 * Room doesn't have @Fts5 annotation, so we manage these tables directly.
 * The FTS5 tables use porter tokenizer for stemming.
 *
 * Why FTS5 over FTS4?
 * - bm25() built-in ranking function
 * - Better performance
 * - Modern SQLite feature (available since SQLite 3.9.0, Android 5.0+)
 *
 * Why standalone tables (not external content)?
 * - Simpler sync: delete all + reinsert during sync
 * - No trigger complexity
 * - Room handles it better
 * - Small storage overhead is acceptable for reliability
 */

/**
 * FTS5 entry for book full-text search.
 *
 * Denormalizes author/narrator names from the cross-reference table
 * into the FTS index for efficient search.
 */
data class BookFtsEntry(
    val bookId: String,
    val title: String,
    val subtitle: String?,
    val description: String?,
    val author: String?,
    val narrator: String?,
    val seriesName: String?,
    val genres: String?
)

/**
 * FTS5 entry for contributor full-text search.
 */
data class ContributorFtsEntry(
    val contributorId: String,
    val name: String,
    val description: String?
)

/**
 * FTS5 entry for series full-text search.
 */
data class SeriesFtsEntry(
    val seriesId: String,
    val name: String,
    val description: String?
)
