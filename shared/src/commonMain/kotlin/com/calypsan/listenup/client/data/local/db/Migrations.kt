package com.calypsan.listenup.client.data.local.db

/**
 * Database migrations for ListenUp.
 *
 * Currently empty — the schema lives at v1 as the single baseline. `MIGRATION_1_2` was
 * deleted during W4.1 per Finding 05 D1's checkpoint resolution: it was dead code because
 * the prior v1 schema was regenerated from the post-cover-download-queue state, making the
 * "add cover_download_queue" migration a no-op against every real v1 installation.
 *
 * Add new `Migration(from, to) { connection -> ... }` instances here when the schema advances,
 * and wire them into each `DatabaseModule` via `.addMigrations(...)`.
 */
object Migrations
