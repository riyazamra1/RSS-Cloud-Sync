package com.riyaz.rsscloudsync

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = applicationContext.getSharedPreferences("rss_cloud_sync", Context.MODE_PRIVATE)
        val local = prefs.getString("sync_folder_uri", null)
        val external = prefs.getString("external_storage_uri", null)
        val direction = prefs.getString("sync_direction", "Two-way Sync") ?: "Two-way Sync"

        if (local == null || external == null) return@withContext Result.failure()

        return@withContext try {
            val result = SyncEngine(applicationContext.contentResolver).run(
                Uri.parse(local), Uri.parse(external), direction
            )
            prefs.edit()
                .putLong("last_sync_time", System.currentTimeMillis())
                .putString("last_sync_result", result.message)
                .apply()
            if (result.failed > 0) Result.retry() else Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
