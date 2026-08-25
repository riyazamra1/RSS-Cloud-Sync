package com.riyaz.rsscloudsync

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.riyaz.rsscloudsync.databinding.ActivitySyncSetupBinding
import java.util.Locale
import java.util.concurrent.Executors

class SyncSetupActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySyncSetupBinding
    private val prefs by lazy { getSharedPreferences("rss_cloud_sync", MODE_PRIVATE) }
    private val executor = Executors.newSingleThreadExecutor()
    private var activeEngine: SyncEngine? = null
    private var activeDriveEngine: GoogleDriveSyncEngine? = null
    private var selectingTarget = false

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (_: SecurityException) {
            Toast.makeText(this, "Folder permission could not be saved", Toast.LENGTH_SHORT).show()
        }
        prefs.edit().putString(if (selectingTarget) "external_storage_uri" else "sync_folder_uri", uri.toString()).apply()
        if (!selectingTarget) prefs.edit().remove("selected_local_files").apply()
        loadFolders()
        updateStorageInfo()
    }

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@registerForActivityResult
        uris.forEach { uri ->
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            } catch (_: SecurityException) {}
        }
        prefs.edit().putStringSet("selected_local_files", uris.map(Uri::toString).toSet()).remove("sync_folder_uri").apply()
        loadFolders()
    }

    private val driveFolderPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK || result.data == null) return@registerForActivityResult
        val id = result.data?.getStringExtra("folder_id") ?: return@registerActivityResult
        val name = result.data?.getStringExtra("folder_name") ?: "My Drive"
        prefs.edit().putString("google_drive_target_folder_id", id).putString("google_drive_target_folder_name", name).apply()
        loadFolders()
        updateStorageInfo()
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        binding = ActivitySyncSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Sync"
        setupCloudProvider()
        setupDirection()
        setupSchedule()
        loadConfiguration()
        loadHistory()
        binding.chooseLocalButton.setOnClickListener { chooseLocalSource() }
        binding.chooseTargetButton.setOnClickListener { chooseTarget() }
        binding.syncNowButton.setOnClickListener {
            if (activeEngine == null && activeDriveEngine == null) startSync() else { activeEngine?.cancel(); activeDriveEngine?.cancel() }
        }
        binding.clearHistoryButton.setOnClickListener { SyncHistoryManager.clear(this); loadHistory() }
    }

    private fun chooseLocalSource() {
        MaterialAlertDialogBuilder(this).setTitle("Local source").setItems(arrayOf("Folder", "Individual files")) { _, which ->
            if (which == 0) { selectingTarget = false; folderPicker.launch(null) } else filePicker.launch(arrayOf("*/*"))
        }.show()
    }

    private fun chooseTarget() {
        val provider = binding.cloudProviderSpinner.selectedItem?.toString() ?: "External storage"
        if (provider == "Google Drive") {
            val connected = prefs.getStringSet("connected_cloud_providers", emptySet())?.contains("Google Drive") == true
            if (!connected) { Toast.makeText(this, "Connect Google Drive first", Toast.LENGTH_SHORT).show(); return }
            driveFolderPicker.launch(Intent(this, GoogleDriveFolderPickerActivity::class.java))
        } else {
            selectingTarget = true
            folderPicker.launch(null)
        }
    }

    private fun setupCloudProvider() {
        binding.cloudProviderSpinner.adapter = spinnerAdapter(arrayOf("Google Drive", "OneDrive", "Dropbox", "MEGA", "Box", "pCloud", "WebDAV", "NAS / SMB", "External storage"))
        binding.cloudProviderSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) { loadFolders(); updateStorageInfo() }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
    }

    private fun setupDirection() {
        binding.syncDirectionSpinner.adapter = spinnerAdapter(arrayOf("Two-way Sync", "Upload only", "Upload mirror", "Upload then delete", "Download only", "Download mirror", "Download then delete"))
    }

    private fun setupSchedule() {
        binding.scheduleSpinner.adapter = spinnerAdapter(arrayOf("Manual", "Every 15 minutes", "Every 30 minutes", "Every 1 hour", "Every 6 hours", "Every 12 hours", "Daily"))
    }

    private fun spinnerAdapter(items: Array<String>) = ArrayAdapter(this, android.R.layout.simple_spinner_item, items).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

    private fun loadConfiguration() {
        prefs.getString("cloud_provider", null)?.let { selectSpinnerValue(binding.cloudProviderSpinner, it) }
        prefs.getString("selected_cloud_provider", null)?.let { selectSpinnerValue(binding.cloudProviderSpinner, it) }
        prefs.getString("sync_direction", null)?.let { selectSpinnerValue(binding.syncDirectionSpinner, it) }
        prefs.getString("sync_schedule", null)?.let { selectSpinnerValue(binding.scheduleSpinner, it) }
        loadFolders(); updateStorageInfo()
    }

    private fun loadFolders() {
        val folder = prefs.getString("sync_folder_uri", null)
        val files = prefs.getStringSet("selected_local_files", emptySet()) ?: emptySet()
        binding.localFolderText.text = when {
            files.isNotEmpty() -> "${files.size} individual file${if (files.size == 1) "" else "s"} selected"
            folder != null -> prettyUri(folder)
            else -> "No local source selected"
        }
        val provider = binding.cloudProviderSpinner.selectedItem?.toString() ?: "External storage"
        binding.targetFolderText.text = if (provider == "Google Drive") {
            prefs.getString("google_drive_target_folder_name", null)?.let { "Google Drive / $it" } ?: "No Google Drive folder selected"
        } else prefs.getString("external_storage_uri", null)?.let(::prettyUri) ?: "No target folder selected"
        binding.cloudAccountName.text = if (provider == "Google Drive") prefs.getString("google_drive_account_email", "Google Drive") else provider
    }

    private fun updateStorageInfo() {
        val provider = binding.cloudProviderSpinner.selectedItem?.toString() ?: "External storage"
        if (provider == "Google Drive" && prefs.getStringSet("connected_cloud_providers", emptySet())?.contains("Google Drive") == true) {
            binding.storageUsageText.text = "Loading Google Drive storage..."
            executor.execute {
                try {
                    val text = DriveClient(this).quotaText()
                    runOnUiThread { binding.storageUsageText.text = text; binding.storageProgress.isIndeterminate = false }
                } catch (e: Exception) {
                    runOnUiThread { binding.storageUsageText.text = e.message ?: "Google Drive connected • quota unavailable"; binding.storageProgress.isIndeterminate = false }
                }
            }
            return
        }
        if (provider != "External storage") {
            val connected = prefs.getStringSet("connected_cloud_providers", emptySet())?.contains(provider) == true
            binding.storageUsageText.text = if (connected) "Connected" else "Not connected"
            binding.storageProgress.setProgressCompat(0, false)
            return
        }
        try {
            val stat = StatFs(Environment.getExternalStorageDirectory().path)
            val total = stat.totalBytes.coerceAtLeast(1L)
            val free = stat.availableBytes.coerceAtLeast(0L)
            val used = (total - free).coerceAtLeast(0L)
            binding.storageUsageText.text = "Used: ${formatBytes(used)} • Free: ${formatBytes(free)} • Total: ${formatBytes(total)}"
            binding.storageProgress.setProgressCompat(((used.toDouble() / total) * 100).toInt().coerceIn(0, 100), false)
        } catch (_: Exception) { binding.storageUsageText.text = "Storage information unavailable" }
    }

    private fun startSync() {
        val provider = binding.cloudProviderSpinner.selectedItem?.toString() ?: "External storage"
        val directionName = binding.syncDirectionSpinner.selectedItem.toString()
        prefs.edit().putString("sync_direction", directionName).putString("cloud_provider", provider).apply()
        if (provider == "Google Drive") { startGoogleDriveSync(directionName); return }
        val source = prefs.getString("sync_folder_uri", null)
        val target = prefs.getString("external_storage_uri", null)
        if (source == null || target == null) { Toast.makeText(this, "Select both local and target folders", Toast.LENGTH_SHORT).show(); return }
        runLocalSync(Uri.parse(source), Uri.parse(target), direction(directionName))
    }

    private fun startGoogleDriveSync(directionName: String) {
        val folderId = prefs.getString("google_drive_target_folder_id", null)
        val localFolder = prefs.getString("sync_folder_uri", null)
        val files = (prefs.getStringSet("selected_local_files", emptySet()) ?: emptySet()).map(Uri::parse)
        if (folderId == null) { Toast.makeText(this, "Select a Google Drive target folder", Toast.LENGTH_SHORT).show(); return }
        if (localFolder == null && files.isEmpty()) { Toast.makeText(this, "Select a local folder or individual files", Toast.LENGTH_SHORT).show(); return }

        binding.syncNowButton.text = "CANCEL SYNC"
        binding.syncNowButton.isEnabled = true
        binding.syncStatusText.text = "Google Drive sync in progress..."
        executor.execute {
            val started = System.currentTimeMillis()
            try {
                val engine = GoogleDriveSyncEngine(this, contentResolver)
                activeDriveEngine = engine
                val result = if (files.isNotEmpty()) {
                    if (directionName != "Upload only" && directionName != "Upload mirror" && directionName != "Upload then delete") {
                        throw IllegalStateException("Individual file selection currently supports upload modes only")
                    }
                    engine.uploadSelectedFiles(files, folderId) { p -> updateDriveProgress(p) }
                } else {
                    val dir = when (directionName) {
                        "Upload only" -> GoogleDriveSyncEngine.Direction.UPLOAD_ONLY
                        "Upload mirror" -> GoogleDriveSyncEngine.Direction.UPLOAD_MIRROR
                        "Upload then delete" -> GoogleDriveSyncEngine.Direction.UPLOAD_THEN_DELETE
                        "Download only" -> GoogleDriveSyncEngine.Direction.DOWNLOAD_ONLY
                        "Download mirror" -> GoogleDriveSyncEngine.Direction.DOWNLOAD_MIRROR
                        "Download then delete" -> GoogleDriveSyncEngine.Direction.DOWNLOAD_THEN_DELETE
                        else -> GoogleDriveSyncEngine.Direction.TWO_WAY
                    }
                    engine.sync(Uri.parse(localFolder!!), folderId, dir) { p -> updateDriveProgress(p) }
                }
                val success = result.failed == 0 && !engine.isCancelled()
                SyncHistoryManager.add(this, SyncHistoryManager.Entry(
                    timestamp = System.currentTimeMillis(),
                    direction = directionName.uppercase(Locale.US).replace(' ', '_'),
                    filesProcessed = result.processed,
                    filesChanged = result.changed,
                    uploadedFiles = result.uploaded,
                    downloadedFiles = result.downloaded,
                    failedFiles = result.failed,
                    bytesTransferred = result.bytes,
                    durationMs = System.currentTimeMillis() - started,
                    success = success,
                    message = if (success) "Google Drive sync completed" else "Google Drive sync completed with warnings"
                ))
                runOnUiThread {
                    activeDriveEngine = null
                    binding.syncNowButton.text = "SYNC NOW"
                    binding.syncStatusText.text = if (success) "Sync completed" else "Sync completed with warnings"
                    binding.syncStatusDetail.text = "Files: ${result.processed} • ↑${result.uploaded} ↓${result.downloaded} • Failed: ${result.failed} • ${formatBytes(result.bytes)}"
                    binding.progressText.text = "100%"
                    binding.syncNowButton.isEnabled = true
                    loadHistory()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    activeDriveEngine = null
                    binding.syncNowButton.text = "SYNC NOW"
                    binding.syncStatusText.text = "Sync failed"
                    binding.syncStatusDetail.text = e.message ?: "Google Drive sync failed"
                    binding.syncNowButton.isEnabled = true
                }
            }
        }
    }

    private fun updateDriveProgress(progress: GoogleDriveSyncEngine.Progress) {
        runOnUiThread {
            binding.progressText.text = if (progress.total == 0) "100%" else "${progress.processed * 100 / progress.total}%"
            binding.syncStatusDetail.text = "${progress.processed}/${progress.total} • ↑${progress.uploaded} ↓${progress.downloaded} • Failed: ${progress.failed} • ${formatBytes(progress.bytes)}"
            binding.currentFileText.text = progress.currentPath
        }
    }

    private fun collectFiles(tree: Uri): List<Uri> {
        val output = ArrayList<Uri>()
        val rootId = android.provider.DocumentsContract.getTreeDocumentId(tree)
        fun walk(id: String) {
            val children = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(tree, id)
            contentResolver.query(children, arrayOf(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID, android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val mimeCol = cursor.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    val childId = cursor.getString(idCol)
                    if (cursor.getString(mimeCol) == android.provider.DocumentsContract.Document.MIME_TYPE_DIR) walk(childId)
                    else output += android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, childId)
                }
            }
        }
        walk(rootId)
        return output
    }

    private fun queryName(uri: Uri): String = contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else "file" } ?: "file"

    private fun runLocalSync(source: Uri, target: Uri, dir: SyncEngine.Direction) {
        binding.syncNowButton.text = "CANCEL SYNC"
        executor.execute {
            val engine = SyncEngine(contentResolver, this)
            activeEngine = engine
            val result = engine.sync(source, target, dir) { progress -> runOnUiThread {
                binding.progressText.text = if (progress.totalFiles == 0) "100%" else "${progress.filesProcessed * 100 / progress.totalFiles}%"
                binding.syncStatusDetail.text = "${progress.filesProcessed}/${progress.totalFiles} • ↑${progress.uploadedFiles} ↓${progress.downloadedFiles} • ${formatBytes(progress.bytesTransferred)}"
                binding.currentFileText.text = progress.currentPath
            }}
            runOnUiThread {
                activeEngine = null
                binding.syncNowButton.text = "SYNC NOW"
                binding.syncStatusText.text = if (result.error == null && !result.cancelled && result.failedFiles == 0) "Sync completed" else "Sync completed with warnings"
                binding.syncStatusDetail.text = "Files: ${result.filesProcessed} • Uploaded: ${result.uploadedFiles} • Downloaded: ${result.downloadedFiles} • Failed: ${result.failedFiles} • ${formatBytes(result.bytesTransferred)}"
                binding.progressText.text = "100%"
                loadHistory()
            }
        }
    }

    private fun direction(name: String) = when (name) {
        "Upload only" -> SyncEngine.Direction.UPLOAD_ONLY
        "Upload mirror" -> SyncEngine.Direction.UPLOAD_MIRROR
        "Upload then delete" -> SyncEngine.Direction.UPLOAD_THEN_DELETE
        "Download only" -> SyncEngine.Direction.DOWNLOAD_ONLY
        "Download mirror" -> SyncEngine.Direction.DOWNLOAD_MIRROR
        "Download then delete" -> SyncEngine.Direction.DOWNLOAD_THEN_DELETE
        else -> SyncEngine.Direction.TWO_WAY
    }

    private fun selectSpinnerValue(spinner: android.widget.Spinner, value: String) {
        for (i in 0 until spinner.count) if (spinner.getItemAtPosition(i).toString() == value) { spinner.setSelection(i); return }
    }

    private fun prettyUri(value: String) = (Uri.parse(value).lastPathSegment ?: value).substringAfterLast(':').replace("%20", " ").ifBlank { value }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        var value = bytes.toDouble()
        val units = arrayOf("KB", "MB", "GB", "TB")
        var index = 0
        while (value >= 1024 && index < units.lastIndex) { value /= 1024; index++ }
        return String.format(Locale.getDefault(), "%.2f %s", value, units[index])
    }

    private fun loadHistory() {
        val entries = SyncHistoryManager.get(this)
        binding.historyText.text = if (entries.isEmpty()) "No sync history yet." else entries.take(10).joinToString("\n\n") { entry ->
            "${if (entry.success) "✓" else "⚠"} ${entry.direction.replace('_', ' ')}\nFiles: ${entry.filesProcessed} • ↑${entry.uploadedFiles} ↓${entry.downloadedFiles} • Failed: ${entry.failedFiles}\nTransferred: ${formatBytes(entry.bytesTransferred)}"
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    override fun onDestroy() {
        activeEngine?.cancel()
        activeDriveEngine?.cancel()
        executor.shutdownNow()
        super.onDestroy()
    }
}
