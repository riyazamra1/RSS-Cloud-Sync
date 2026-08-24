package com.riyaz.rsscloudsync

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.riyaz.rsscloudsync.databinding.ActivitySyncSetupBinding
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SyncSetupActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySyncSetupBinding
    private val preferences by lazy { getSharedPreferences("rss_cloud_sync", MODE_PRIVATE) }
    private val syncExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var activeEngine: SyncEngine? = null
    private var selectingTarget = false

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (_: SecurityException) {
            Toast.makeText(this, "Folder permission could not be saved", Toast.LENGTH_SHORT).show()
        }
        if (selectingTarget) {
            preferences.edit().putString("external_storage_uri", uri.toString()).apply()
        } else {
            preferences.edit().putString("sync_folder_uri", uri.toString()).apply()
        }
        loadFolders()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySyncSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Sync"

        setupCloudProvider()
        setupSyncDirection()
        setupSchedule()
        loadConfiguration()
        loadHistory()

        binding.chooseLocalButton.setOnClickListener {
            selectingTarget = false
            folderPicker.launch(null)
        }
        binding.chooseTargetButton.setOnClickListener {
            selectingTarget = true
            folderPicker.launch(null)
        }
        binding.syncNowButton.setOnClickListener { startSync() }
        binding.clearHistoryButton.setOnClickListener {
            SyncHistoryManager.clear(this)
            loadHistory()
        }
    }

    private fun setupCloudProvider() {
        val providers = arrayOf("Google Drive", "OneDrive", "Dropbox", "MEGA", "Box", "pCloud", "WebDAV", "NAS / SMB", "External storage")
        binding.cloudProviderSpinner.adapter = spinnerAdapter(providers)
    }

    private fun setupSyncDirection() {
        val directions = arrayOf("Two-way Sync", "Upload only", "Upload mirror", "Upload then delete", "Download only", "Download mirror", "Download then delete")
        binding.syncDirectionSpinner.adapter = spinnerAdapter(directions)
    }

    private fun setupSchedule() {
        val schedules = arrayOf("Manual", "Every 15 minutes", "Every 30 minutes", "Every 1 hour", "Every 6 hours", "Every 12 hours", "Daily")
        binding.scheduleSpinner.adapter = spinnerAdapter(schedules)
    }

    private fun spinnerAdapter(items: Array<String>): ArrayAdapter<String> = ArrayAdapter(this, android.R.layout.simple_spinner_item, items).apply {
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    }

    private fun loadConfiguration() {
        loadFolders()
        preferences.getString("cloud_provider", null)?.let { selectSpinnerValue(binding.cloudProviderSpinner, it) }
        preferences.getString("sync_direction", null)?.let { selectSpinnerValue(binding.syncDirectionSpinner, it) }
        preferences.getString("sync_schedule", null)?.let { selectSpinnerValue(binding.scheduleSpinner, it) }
    }

    private fun loadFolders() {
        val local = preferences.getString("sync_folder_uri", null)
        val target = preferences.getString("external_storage_uri", null)
        binding.localFolderText.text = local?.let { prettyUri(it) } ?: "No local folder selected"
        binding.targetFolderText.text = target?.let { prettyUri(it) } ?: "No cloud / target folder selected"
    }

    private fun prettyUri(value: String): String {
        val uri = Uri.parse(value)
        val raw = uri.lastPathSegment ?: value
        return raw.substringAfterLast(':').replace('%20', ' ').ifBlank { value }
    }

    private fun selectSpinnerValue(spinner: android.widget.Spinner, value: String) {
        for (index in 0 until spinner.count) if (spinner.getItemAtPosition(index).toString() == value) {
            spinner.setSelection(index)
            return
        }
    }

    private fun startSync() {
        val sourceString = preferences.getString("sync_folder_uri", null)
        val targetString = preferences.getString("external_storage_uri", null)
        if (sourceString == null || targetString == null) {
            AlertDialog.Builder(this)
                .setTitle("Folders required")
                .setMessage("Select both the local folder and the cloud / target folder before starting sync.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val directionName = binding.syncDirectionSpinner.selectedItem.toString()
        val direction = when (directionName) {
            "Upload only" -> SyncEngine.Direction.UPLOAD_ONLY
            "Upload mirror" -> SyncEngine.Direction.UPLOAD_MIRROR
            "Upload then delete" -> SyncEngine.Direction.UPLOAD_THEN_DELETE
            "Download only" -> SyncEngine.Direction.DOWNLOAD_ONLY
            "Download mirror" -> SyncEngine.Direction.DOWNLOAD_MIRROR
            "Download then delete" -> SyncEngine.Direction.DOWNLOAD_THEN_DELETE
            else -> SyncEngine.Direction.TWO_WAY
        }
        val cloudProvider = binding.cloudProviderSpinner.selectedItem.toString()
        val schedule = binding.scheduleSpinner.selectedItem.toString()
        preferences.edit()
            .putString("cloud_provider", cloudProvider)
            .putString("sync_direction", directionName)
            .putString("sync_schedule", schedule)
            .putBoolean("sync_configuration_saved", true)
            .apply()

        binding.syncNowButton.isEnabled = false
        binding.syncStatusText.text = "Syncing..."
        binding.syncStatusDetail.text = "Preparing files"
        binding.progressText.text = "0%"

        syncExecutor.execute {
            val engine = SyncEngine(contentResolver, this)
            activeEngine = engine
            val result = engine.sync(Uri.parse(sourceString), Uri.parse(targetString), direction) { progress ->
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    val percent = if (progress.totalFiles > 0) progress.filesProcessed * 100 / progress.totalFiles else 100
                    binding.progressText.text = "$percent%"
                    binding.syncStatusDetail.text = "${progress.filesProcessed}/${progress.totalFiles} files • ${progress.filesChanged} changed"
                    binding.currentFileText.text = progress.currentPath
                }
            }
            runOnUiThread {
                activeEngine = null
                binding.syncNowButton.isEnabled = true
                when {
                    result.cancelled -> {
                        binding.syncStatusText.text = "Sync cancelled"
                        binding.syncStatusDetail.text = "Sync stopped safely"
                    }
                    result.error != null -> {
                        binding.syncStatusText.text = "Sync failed"
                        binding.syncStatusDetail.text = result.error
                    }
                    else -> {
                        binding.syncStatusText.text = "Sync complete"
                        binding.syncStatusDetail.text = "${result.filesChanged} files changed • ${formatBytes(result.bytesTransferred)} transferred"
                        binding.progressText.text = "100%"
                    }
                }
                loadHistory()
            }
        }
    }

    private fun loadHistory() {
        val entries = SyncHistoryManager.get(this)
        if (entries.isEmpty()) {
            binding.historyText.text = "No sync history yet.\nYour completed syncs will appear here."
            return
        }
        binding.historyText.text = entries.take(10).joinToString("\n\n") { entry ->
            val time = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault()).format(Date(entry.timestamp))
            val status = if (entry.success) "✓ Completed" else "✕ Failed"
            "$status  •  $time\n${entry.direction.replace('_', ' ')}  •  ${entry.filesChanged} changed  •  ${formatBytes(entry.bytesTransferred)}\n${entry.message}"
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        var value = bytes.toDouble()
        val units = arrayOf("KB", "MB", "GB", "TB")
        var i = 0
        while (value >= 1024 && i < units.lastIndex) { value /= 1024; i++ }
        return String.format(Locale.getDefault(), "%.1f %s", value, units[i])
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    override fun onDestroy() {
        activeEngine?.cancel()
        syncExecutor.shutdownNow()
        super.onDestroy()
    }
}
