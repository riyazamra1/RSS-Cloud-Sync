package com.riyaz.rsscloudsync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object SyncScheduler {
    private const val WORK_NAME = "rss_cloud_sync_periodic"

    fun schedule(context: Context, schedule: String) {
        if (schedule == "Manual") {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            return
        }

        val minutes = when (schedule) {
            "Every 15 minutes" -> 15L
            "Every 30 minutes" -> 30L
            "Every 1 hour" -> 60L
            "Every 6 hours" -> 360L
            "Every 12 hours" -> 720L
            "Daily" -> 1440L
            else -> 15L
        }

        val request = PeriodicWorkRequestBuilder<SyncWorker>(minutes, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun runNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "rss_cloud_sync_manual",
            androidx.work.ExistingWorkPolicy.REPLACE,
            androidx.work.OneTimeWorkRequestBuilder<SyncWorker>().build()
        )
    }
}
