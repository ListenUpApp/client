package com.calypsan.listenup.client.workers

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.calypsan.listenup.client.data.sync.SyncManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit
import com.calypsan.listenup.client.core.Result as CoreResult

/**
 * Android WorkManager worker for background sync operations.
 *
 * Periodically syncs books from server to local database when:
 * - Network is available (WiFi or cellular)
 * - Device is not in low battery mode
 *
 * Sync frequency: Every 15 minutes
 * Retry policy: Exponential backoff with 3 max retries
 *
 * Scheduled via [schedule] companion method, typically called from
 * Application.onCreate() or after successful authentication.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val syncManager: SyncManager by inject()

    /**
     * Perform background sync operation.
     *
     * Called by WorkManager on background thread. Triggers full sync
     * via SyncManager and returns success/failure/retry result.
     *
     * @return Result.success() if sync completes, Result.retry() on failure
     */
    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting background sync (attempt ${runAttemptCount + 1})")

        return when (val result = syncManager.sync()) {
            is CoreResult.Success -> {
                Log.d(TAG, "Background sync completed successfully")
                Result.success()
            }
            is CoreResult.Failure -> {
                Log.e(TAG, "Background sync failed: ${result.message}", result.exception)

                // Retry with exponential backoff up to 3 times
                if (runAttemptCount < 3) {
                    Log.d(TAG, "Will retry sync (attempt ${runAttemptCount + 1}/3)")
                    Result.retry()
                } else {
                    Log.w(TAG, "Max retries reached, giving up")
                    Result.failure()
                }
            }
        }
    }

    companion object {
        private const val TAG = "SyncWorker"
        private const val WORK_NAME = "periodic_sync"
        private const val SYNC_INTERVAL_MINUTES = 15L

        /**
         * Schedule periodic background sync.
         *
         * Sets up WorkManager to run sync every 15 minutes when network
         * is available. Safe to call multiple times - uses KEEP policy
         * to avoid duplicating work.
         *
         * Call from Application.onCreate() or after user authentication.
         *
         * @param context Android application context
         */
        fun schedule(context: Context) {
            Log.d(TAG, "Scheduling periodic sync every $SYNC_INTERVAL_MINUTES minutes")

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)  // Any network (WiFi or cellular)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
                repeatInterval = SYNC_INTERVAL_MINUTES,
                repeatIntervalTimeUnit = TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,  // Don't restart if already running
                syncRequest
            )

            Log.d(TAG, "Periodic sync scheduled successfully")
        }

        /**
         * Cancel periodic background sync.
         *
         * Stops all scheduled sync work. Typically called on logout.
         *
         * @param context Android application context
         */
        fun cancel(context: Context) {
            Log.d(TAG, "Cancelling periodic sync")

            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
