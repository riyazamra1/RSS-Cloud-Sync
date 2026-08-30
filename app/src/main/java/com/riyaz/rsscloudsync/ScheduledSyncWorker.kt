package com.riyaz.rsscloudsync

import android.content.Context
import android.net.Uri
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Runs a saved folder pair in the background when its schedule is enabled. */
class ScheduledSyncWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val pairId = inputData.getString(KEY_PAIR_ID) ?: return Result.failure()
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!SyncPairStore.load(prefs, pairId)) return Result.failure()
        if (!prefs.getBoolean("folder_pair_enabled", true)) return Result.success()
        if (prefs.getString("schedule_mode", "Save only") != "Schedule now") return Result.success()
        val provider = prefs.getString("selected_cloud_provider", "") ?: ""
        if (provider != "Google Drive") return Result.success()
        val targetId = prefs.getString("google_drive_target_folder_id", null) ?: return Result.failure()
        val localFolder = prefs.getString("sync_folder_uri", null)
        val files = (prefs.getStringSet("selected_local_files", emptySet()) ?: emptySet()).map(Uri::parse)
        if (localFolder.isNullOrBlank() && files.isEmpty()) return Result.failure()
        val directionName = prefs.getString("sync_direction", "Two-way Sync") ?: "Two-way Sync"
        val started = System.currentTimeMillis()
        return try {
            val engine = GoogleDriveSyncEngine(applicationContext, applicationContext.contentResolver)
            val result = if (files.isNotEmpty()) {
                if (directionName !in UPLOAD_MODES) return Result.failure()
                engine.uploadSelectedFiles(files, targetId)
            } else {
                val direction = when (directionName) {
                    "Upload only" -> GoogleDriveSyncEngine.Direction.UPLOAD_ONLY
                    "Upload mirror" -> GoogleDriveSyncEngine.Direction.UPLOAD_MIRROR
                    "Upload then delete" -> GoogleDriveSyncEngine.Direction.UPLOAD_THEN_DELETE
                    "Download only" -> GoogleDriveSyncEngine.Direction.DOWNLOAD_ONLY
                    "Download mirror" -> GoogleDriveSyncEngine.Direction.DOWNLOAD_MIRROR
                    "Download then delete" -> GoogleDriveSyncEngine.Direction.DOWNLOAD_THEN_DELETE
                    else -> GoogleDriveSyncEngine.Direction.TWO_WAY
                }
                engine.sync(Uri.parse(localFolder!!), targetId, direction, GoogleDriveSyncEngine.Options(
                    prefs.getBoolean("exclude_hidden_files", true), prefs.getBoolean("exclude_subfolders", false), prefs.getBoolean("delete_empty_subfolders", false)
                ))
            }
            SyncHistoryManager.add(applicationContext, SyncHistoryManager.Entry(
                timestamp = System.currentTimeMillis(), direction = directionName, filesProcessed = result.processed,
                filesChanged = result.uploaded + result.downloaded, uploadedFiles = result.uploaded, downloadedFiles = result.downloaded,
                failedFiles = result.failed, bytesTransferred = result.bytes, durationMs = System.currentTimeMillis() - started,
                success = result.failed == 0, message = if (result.failed == 0) "Scheduled sync completed" else "Scheduled sync completed with warnings"
            ))
            Result.success()
        } catch (_: Exception) {
            SyncHistoryManager.add(applicationContext, SyncHistoryManager.Entry(
                timestamp = System.currentTimeMillis(), direction = directionName, filesProcessed = 0, filesChanged = 0, failedFiles = 1,
                bytesTransferred = 0, durationMs = System.currentTimeMillis() - started, success = false, message = "Scheduled sync failed; retry scheduled"
            ))
            Result.retry()
        }
    }

    companion object {
        private const val PREFS = "rss_cloud_sync"
        private const val KEY_PAIR_ID = "pair_id"
        private const val UNIQUE_PREFIX = "rss_cloud_sync_pair_"
        private val UPLOAD_MODES = setOf("Upload only", "Upload mirror", "Upload then delete")
        fun schedule(context: Context, pairId: String) {
            val request = PeriodicWorkRequestBuilder<ScheduledSyncWorker>(1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(androidx.work.workDataOf(KEY_PAIR_ID to pairId))
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE_PREFIX + pairId, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
        fun cancel(context: Context, pairId: String) { WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_PREFIX + pairId) }
        fun ensureScheduledForSavedPairs(context: Context) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            SyncPairStore.all(prefs).forEach { pair ->
                if (pair.enabled && pair.scheduleMode == "Schedule now") schedule(context, pair.id) else cancel(context, pair.id)
            }
        }
    }
}
