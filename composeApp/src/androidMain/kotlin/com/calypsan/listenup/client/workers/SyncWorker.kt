package com.calypsan.listenup.client.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.calypsan.listenup.client.data.sync.SyncManager
import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import com.calypsan.listenup.client.core.Result as CoreResult

private val logger = KotlinLogging.logger {}

/**
 * Android WorkManager worker for background sync operations.
 *
 * Executes sync via [SyncManager] when triggered by WorkManager.
 * Scheduling is handled by [com.calypsan.listenup.client.sync.AndroidBackgroundSyncScheduler].
 *
 * Retry policy: Exponential backoff with 3 max retries.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params),
    KoinComponent {
    private val syncManager: SyncManager by inject()

    /**
     * Perform background sync operation.
     *
     * Called by WorkManager on background thread. Triggers full sync
     * via SyncManager and returns success/failure/retry result.
     */
    override suspend fun doWork(): Result {
        logger.debug { "Starting background sync (attempt ${runAttemptCount + 1})" }

        return when (val result = syncManager.sync()) {
            is CoreResult.Success -> {
                logger.info { "Background sync completed successfully" }
                Result.success()
            }

            is CoreResult.Failure -> {
                logger.error(result.exception) { "Background sync failed: ${result.message}" }

                // Retry with exponential backoff up to 3 times
                if (runAttemptCount < 3) {
                    logger.debug { "Will retry sync (attempt ${runAttemptCount + 1}/3)" }
                    Result.retry()
                } else {
                    logger.warn { "Max retries reached, giving up" }
                    Result.failure()
                }
            }
        }
    }
}
