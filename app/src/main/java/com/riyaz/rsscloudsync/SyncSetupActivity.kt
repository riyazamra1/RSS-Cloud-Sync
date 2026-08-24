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
    private var selectingTarget = false

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@registerForActivityResult
        try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (_: SecurityException) {}
        prefs.edit().putString(if (selectingTarget) "external_storage_uri" else "sync_folder_uri", uri.toString()).apply()
        if (!selectingTarget) prefs.edit().remove("selected_local_files").apply()
        loadFolders(); updateStorageInfo()
    }

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@registerForActivityResult
        uris.forEach { uri -> try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: SecurityException) {} }
        prefs.edit().putStringSet("selected_local_files", uris.map(Uri::toString).toSet()).remove("sync_folder_uri").apply()
        loadFolders()
    }

    private val driveFolderPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK || result.data == null) return@registerForActivityResult
        val id = result.data?.getStringExtra("folder_id") ?: return@registerForActivityResult
        val name = result.data?.getStringExtra("folder_name") ?: "My Drive"
        prefs.edit().putString("google_drive_target_folder_id", id).putString("google_drive_target_folder_name", name).apply()
        loadFolders(); updateStorageInfo()
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        binding = ActivitySyncSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Sync"
        setupCloudProvider(); setupDirection(); setupSchedule(); loadConfiguration(); loadHistory()
        binding.chooseLocalButton.setOnClickListener { chooseLocalSource() }
        binding.chooseTargetButton.setOnClickListener { chooseTarget() }
        binding.syncNowButton.setOnClickListener { if (activeEngine == null) startSync() else activeEngine?.cancel() }
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
        } else { selectingTarget = true; folderPicker.launch(null) }
    }

    private fun setupCloudProvider() {
        binding.cloudProviderSpinner.adapter = spinnerAdapter(arrayOf("Google Drive", "OneDrive", "Dropbox", "MEGA", "Box", "pCloud", "WebDAV", "NAS / SMB", "External storage"))
        binding.cloudProviderSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: android.view.View?, position: Int, id: Long) { loadFolders(); updateStorageInfo() }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) = Unit
        }
    }

    private fun setupDirection() { binding.syncDirectionSpinner.adapter = spinnerAdapter(arrayOf("Two-way Sync", "Upload only", "Upload mirror", "Upload then delete", "Download only", "Download mirror", "Download then delete")) }
    private fun setupSchedule() { binding.scheduleSpinner.adapter = spinnerAdapter(arrayOf("Manual", "Every 15 minutes", "Every 30 minutes", "Every 1 hour", "Every 6 hours", "Every 12 hours", "Daily")) }
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
        binding.localFolderText.text = when { files.isNotEmpty() -> "${files.size} individual file${if (files.size == 1) "" else "s"} selected"; folder != null -> prettyUri(folder); else -> "No local source selected" }
        val provider = binding.cloudProviderSpinner.selectedItem?.toString() ?: "External storage"
        binding.targetFolderText.text = if (provider == "Google Drive") prefs.getString("google_drive_target_folder_name", null)?.let { "Google Drive / $it" } ?: "No Google Drive folder selected" else prefs.getString("external_storage_uri", null)?.let(::prettyUri) ?: "No target folder selected"
        binding.cloudAccountName.text = if (provider == "Google Drive") prefs.getString("google_drive_account_email", "Google Drive") else provider
    }

    private fun updateStorageInfo() {
        val provider = binding.cloudProviderSpinner.selectedItem?.toString() ?: "External storage"
        if (provider == "Google Drive" && prefs.getStringSet("connected_cloud_providers", emptySet())?.contains("Google Drive") == true) {
            binding.storageUsageText.text = "Loading Google Drive storage..."
            executor.execute { try { val text = DriveClient(this).quotaText(); runOnUiThread { binding.storageUsageText.text = text; binding.storageProgress.isIndeterminate = false } } catch (_: Exception) { runOnUiThread { binding.storageUsageText.text = "Google Drive connected • quota unavailable"; binding.storageProgress.isIndeterminate = false } } }
            return
        }
        if (provider != "External storage") { binding.storageUsageText.text = if (prefs.getStringSet("connected_cloud_providers", emptySet())?.contains(provider) == true) "Connected" else "Not connected"; binding.storageProgress.setProgressCompat(0, false); return }
        try { val stat = StatFs(Environment.getExternalStorageDirectory().path); val total = stat.totalBytes.coerceAtLeast(1L); val free = stat.availableBytes.coerceAtLeast(0L); val used = (total - free).coerceAtLeast(0L); binding.storageUsageText.text = "Used: ${formatBytes(used)} • Free: ${formatBytes(free)} • Total: ${formatBytes(total)}"; binding.storageProgress.setProgressCompat(((used.toDouble() / total) * 100).toInt().coerceIn(0, 100), false) } catch (_: Exception) { binding.storageUsageText.text = "Storage information unavailable" }
    }

    private fun startSync() {
        val provider = binding.cloudProviderSpinner.selectedItem?.toString() ?: "External storage"
        val directionName = binding.syncDirectionSpinner.selectedItem.toString()
        if (provider == "Google Drive") { startGoogleDriveUpload(directionName); return }
        val source = prefs.getString("sync_folder_uri", null); val target = prefs.getString("external_storage_uri", null)
        if (source == null || target == null) { Toast.makeText(this, "Select both local and target folders", Toast.LENGTH_SHORT).show(); return }
        runLocalSync(Uri.parse(source), Uri.parse(target), direction(directionName))
    }

    private fun startGoogleDriveUpload(directionName: String) {
        val folderId = prefs.getString("google_drive_target_folder_id", null)
        val localFolder = prefs.getString("sync_folder_uri", null)
        val files = prefs.getStringSet("selected_local_files", emptySet()) ?: emptySet()
        if (folderId == null) { Toast.makeText(this, "Select a Google Drive target folder", Toast.LENGTH_SHORT).show(); return }
        if (localFolder == null && files.isEmpty()) { Toast.makeText(this, "Select a local folder or individual files", Toast.LENGTH_SHORT).show(); return }
        if (directionName != "Upload only") { Toast.makeText(this, "Google Drive transfer is currently enabled for Upload only. Two-way/download is next.", Toast.LENGTH_LONG).show(); return }
        binding.syncStatusText.text = "Uploading to Google Drive..."; binding.syncNowButton.isEnabled = false
        executor.execute {
            try {
                val client = DriveClient(this); val uris = if (files.isNotEmpty()) files.map(Uri::parse) else collectFiles(Uri.parse(localFolder!!)); var done = 0; var bytes = 0L
                uris.forEach { uri -> val name = queryName(uri); val mime = contentResolver.getType(uri) ?: "application/octet-stream"; bytes += client.upload(uri, folderId, name, mime); done++; runOnUiThread { binding.progressText.text = if (uris.isEmpty()) "100%" else "${done * 100 / uris.size}%"; binding.syncStatusDetail.text = "$done/${uris.size} uploaded • ${formatBytes(bytes)}"; binding.currentFileText.text = name } }
                runOnUiThread { binding.syncStatusText.text = "Sync completed"; binding.syncStatusDetail.text = "$done files uploaded • ${formatBytes(bytes)}"; binding.progressText.text = "100%"; binding.syncNowButton.isEnabled = true }
            } catch (e: Exception) { runOnUiThread { binding.syncStatusText.text = "Sync failed"; binding.syncStatusDetail.text = e.message ?: "Google Drive upload failed"; binding.syncNowButton.isEnabled = true } }
        }
    }

    private fun collectFiles(tree: Uri): List<Uri> {
        val out = ArrayList<Uri>(); val rootId = android.provider.DocumentsContract.getTreeDocumentId(tree)
        fun walk(id: String) { val children = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(tree, id); contentResolver.query(children, arrayOf(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID, android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { c -> val idCol = c.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID); val mimeCol = c.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE); while (c.moveToNext()) { val childId = c.getString(idCol); if (c.getString(mimeCol) == android.provider.DocumentsContract.Document.MIME_TYPE_DIR) walk(childId) else out += android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, childId) } } }
        walk(rootId); return out
    }

    private fun queryName(uri: Uri): String = contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else "file" } ?: "file"
    private fun runLocalSync(source: Uri, target: Uri, dir: SyncEngine.Direction) { binding.syncNowButton.text = "CANCEL SYNC"; executor.execute { val engine = SyncEngine(contentResolver, this); activeEngine = engine; val result = engine.sync(source, target, dir) { p -> runOnUiThread { binding.progressText.text = if (p.totalFiles == 0) "100%" else "${p.filesProcessed * 100 / p.totalFiles}%"; binding.syncStatusDetail.text = "${p.filesProcessed}/${p.totalFiles} • ↑${p.uploadedFiles} ↓${p.downloadedFiles} • ${formatBytes(p.bytesTransferred)}"; binding.currentFileText.text = p.currentPath } }; runOnUiThread { activeEngine = null; binding.syncNowButton.text = "SYNC NOW"; binding.syncStatusText.text = if (result.error == null && !result.cancelled && result.failedFiles == 0) "Sync completed" else "Sync completed with warnings"; binding.syncStatusDetail.text = "Files: ${result.filesProcessed} • Uploaded: ${result.uploadedFiles} • Downloaded: ${result.downloadedFiles} • Failed: ${result.failedFiles} • ${formatBytes(result.bytesTransferred)}"; binding.progressText.text = "100%"; loadHistory() } } }
    private fun direction(n: String) = when (n) { "Upload only" -> SyncEngine.Direction.UPLOAD_ONLY; "Upload mirror" -> SyncEngine.Direction.UPLOAD_MIRROR; "Upload then delete" -> SyncEngine.Direction.UPLOAD_THEN_DELETE; "Download only" -> SyncEngine.Direction.DOWNLOAD_ONLY; "Download mirror" -> SyncEngine.Direction.DOWNLOAD_MIRROR; "Download then delete" -> SyncEngine.Direction.DOWNLOAD_THEN_DELETE; else -> SyncEngine.Direction.TWO_WAY }
    private fun selectSpinnerValue(s: android.widget.Spinner, value: String) { for (i in 0 until s.count) if (s.getItemAtPosition(i).toString() == value) { s.setSelection(i); return } }
    private fun prettyUri(v: String) = (Uri.parse(v).lastPathSegment ?: v).substringAfterLast(':').replace("%20", " ").ifBlank { v }
    private fun formatBytes(b: Long): String { if (b < 1024) return "$b B"; var v = b.toDouble(); val u = arrayOf("KB","MB","GB","TB"); var i = 0; while (v >= 1024 && i < u.lastIndex) { v /= 1024; i++ }; return String.format(Locale.getDefault(), "%.2f %s", v, u[i]) }
    private fun loadHistory() { val e = SyncHistoryManager.get(this); binding.historyText.text = if (e.isEmpty()) "No sync history yet." else e.take(10).joinToString("\n\n") { x -> "${if (x.success) "✓" else "⚠"} ${x.direction.replace('_',' ')}\nFiles: ${x.filesProcessed} • ↑${x.uploadedFiles} ↓${x.downloadedFiles} • Failed: ${x.failedFiles}\nTransferred: ${formatBytes(x.bytesTransferred)}" } }
    override fun onSupportNavigateUp(): Boolean { finish(); return true }
    override fun onDestroy() { activeEngine?.cancel(); executor.shutdownNow(); super.onDestroy() }
}
