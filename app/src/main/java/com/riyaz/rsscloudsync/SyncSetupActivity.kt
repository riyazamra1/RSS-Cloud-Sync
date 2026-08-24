package com.riyaz.rsscloudsync

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
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
        try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (_: SecurityException) { Toast.makeText(this, "Folder permission could not be saved", Toast.LENGTH_SHORT).show() }
        if (selectingTarget) preferences.edit().putString("external_storage_uri", uri.toString()).apply() else preferences.edit().putString("sync_folder_uri", uri.toString()).apply()
        loadFolders(); updateStorageInfo()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); binding = ActivitySyncSetupBinding.inflate(layoutInflater); setContentView(binding.root)
        setSupportActionBar(binding.toolbar); supportActionBar?.setDisplayHomeAsUpEnabled(true); supportActionBar?.title = "Sync"
        setupCloudProvider(); setupSyncDirection(); setupSchedule(); loadConfiguration(); loadHistory()
        binding.chooseLocalButton.setOnClickListener { selectingTarget = false; folderPicker.launch(null) }
        binding.chooseTargetButton.setOnClickListener { selectingTarget = true; folderPicker.launch(null) }
        binding.syncNowButton.setOnClickListener { if (activeEngine == null) startSync() else activeEngine?.cancel() }
        binding.clearHistoryButton.setOnClickListener { SyncHistoryManager.clear(this); loadHistory() }
    }
    private fun setupCloudProvider() {
        val providers = arrayOf("Google Drive", "OneDrive", "Dropbox", "MEGA", "Box", "pCloud", "WebDAV", "NAS / SMB", "External storage")
        binding.cloudProviderSpinner.adapter = spinnerAdapter(providers)
        binding.cloudProviderSpinner.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) { updateStorageInfo() }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        })
    }
    private fun setupSyncDirection() { binding.syncDirectionSpinner.adapter = spinnerAdapter(arrayOf("Two-way Sync", "Upload only", "Upload mirror", "Upload then delete", "Download only", "Download mirror", "Download then delete")) }
    private fun setupSchedule() { binding.scheduleSpinner.adapter = spinnerAdapter(arrayOf("Manual", "Every 15 minutes", "Every 30 minutes", "Every 1 hour", "Every 6 hours", "Every 12 hours", "Daily")) }
    private fun spinnerAdapter(items: Array<String>): ArrayAdapter<String> = ArrayAdapter(this, android.R.layout.simple_spinner_item, items).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
    private fun loadConfiguration() { loadFolders(); preferences.getString("cloud_provider", null)?.let { selectSpinnerValue(binding.cloudProviderSpinner, it) }; preferences.getString("sync_direction", null)?.let { selectSpinnerValue(binding.syncDirectionSpinner, it) }; preferences.getString("sync_schedule", null)?.let { selectSpinnerValue(binding.scheduleSpinner, it) }; updateStorageInfo() }
    private fun loadFolders() { val local = preferences.getString("sync_folder_uri", null); val target = preferences.getString("external_storage_uri", null); binding.localFolderText.text = local?.let { prettyUri(it) } ?: "No local folder selected"; binding.targetFolderText.text = target?.let { prettyUri(it) } ?: "No cloud / target folder selected"; binding.cloudAccountName.text = binding.cloudProviderSpinner.selectedItem?.toString() ?: "External storage" }
    private fun prettyUri(value: String): String { val uri = Uri.parse(value); val raw = uri.lastPathSegment ?: value; return raw.substringAfterLast(':').replace("%20", " ").ifBlank { value } }
    private fun updateStorageInfo() {
        if (!::binding.isInitialized) return; val provider = binding.cloudProviderSpinner.selectedItem?.toString() ?: "External storage"; binding.cloudAccountName.text = provider
        if (provider == "External storage") try { val stat = StatFs(Environment.getExternalStorageDirectory().path); val total = stat.totalBytes.coerceAtLeast(1L); val free = stat.availableBytes.coerceAtLeast(0L); val used = (total - free).coerceAtLeast(0L); val percent = ((used.toDouble() / total) * 100).toInt().coerceIn(0, 100); binding.storageUsageText.text = "Used: ${formatBytes(used)}   •   Free: ${formatBytes(free)}   •   Total: ${formatBytes(total)}"; binding.storageProgress.isIndeterminate = false; binding.storageProgress.setProgressCompat(percent, true) } catch (_: Exception) { binding.storageUsageText.text = "Storage information unavailable"; binding.storageProgress.isIndeterminate = false; binding.storageProgress.setProgressCompat(0, false) } else { binding.storageUsageText.text = "Account selected • Provider quota unavailable through Android's folder picker"; binding.storageProgress.isIndeterminate = true }
    }
    private fun selectSpinnerValue(spinner: android.widget.Spinner, value: String) { for (index in 0 until spinner.count) if (spinner.getItemAtPosition(index).toString() == value) { spinner.setSelection(index); return } }
    private fun startSync() {
        val sourceString = preferences.getString("sync_folder_uri", null); val targetString = preferences.getString("external_storage_uri", null)
        if (sourceString == null || targetString == null) { AlertDialog.Builder(this).setTitle("Folders required").setMessage("Select both the local folder and the cloud / target folder before starting sync.").setPositiveButton("OK", null).show(); return }
        val directionName = binding.syncDirectionSpinner.selectedItem.toString(); val direction = when (directionName) { "Upload only" -> SyncEngine.Direction.UPLOAD_ONLY; "Upload mirror" -> SyncEngine.Direction.UPLOAD_MIRROR; "Upload then delete" -> SyncEngine.Direction.UPLOAD_THEN_DELETE; "Download only" -> SyncEngine.Direction.DOWNLOAD_ONLY; "Download mirror" -> SyncEngine.Direction.DOWNLOAD_MIRROR; "Download then delete" -> SyncEngine.Direction.DOWNLOAD_THEN_DELETE; else -> SyncEngine.Direction.TWO_WAY }
        preferences.edit().putString("cloud_provider", binding.cloudProviderSpinner.selectedItem.toString()).putString("sync_direction", directionName).putString("sync_schedule", binding.scheduleSpinner.selectedItem.toString()).putBoolean("sync_configuration_saved", true).apply()
        binding.syncNowButton.text = "CANCEL SYNC"; binding.syncStatusText.text = "Syncing..."; binding.syncStatusDetail.text = "Preparing files"; binding.progressText.text = "0%"
        syncExecutor.execute {
            val engine = SyncEngine(contentResolver, this); activeEngine = engine
            val result = engine.sync(Uri.parse(sourceString), Uri.parse(targetString), direction) { p -> runOnUiThread { if (!isFinishing && !isDestroyed) { val percent = if (p.totalFiles > 0) p.filesProcessed * 100 / p.totalFiles else 100; binding.progressText.text = "$percent%"; binding.syncStatusDetail.text = "${p.filesProcessed}/${p.totalFiles} files • ${p.filesChanged} changed • ↑${p.uploadedFiles} ↓${p.downloadedFiles} • ${formatBytes(p.bytesTransferred)}"; binding.currentFileText.text = p.currentPath } } }
            runOnUiThread {
                activeEngine = null; binding.syncNowButton.text = "SYNC NOW"; binding.syncNowButton.isEnabled = true
                when { result.cancelled -> { binding.syncStatusText.text = "Sync cancelled"; binding.syncStatusDetail.text = "Sync stopped safely" }; result.error != null && result.bytesTransferred > 0L -> { binding.syncStatusText.text = "Sync completed with warnings"; binding.syncStatusDetail.text = "Transferred ${formatBytes(result.bytesTransferred)} • ${result.uploadedFiles} uploaded • ${result.downloadedFiles} downloaded\nWarning: ${result.error}"; binding.progressText.text = "100%" }; result.error != null -> { binding.syncStatusText.text = "Sync failed"; binding.syncStatusDetail.text = result.error }; else -> { binding.syncStatusText.text = "Sync completed"; binding.syncStatusDetail.text = "Files: ${result.filesProcessed} • Uploaded: ${result.uploadedFiles} • Downloaded: ${result.downloadedFiles} • ${formatBytes(result.bytesTransferred)} transferred"; binding.progressText.text = "100%" } }
                loadHistory(); updateStorageInfo()
            }
        }
    }
    private fun loadHistory() {
        val entries = SyncHistoryManager.get(this); if (entries.isEmpty()) { binding.historyText.text = "No sync history yet.\nYour completed syncs will appear here."; return }
        binding.historyText.text = entries.take(10).joinToString("\n\n") { e -> val time = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault()).format(Date(e.timestamp)); val status = when { e.success -> "✓ Sync completed"; e.bytesTransferred > 0L -> "⚠ Completed with warnings"; else -> "✕ Sync failed" }; buildString { append(status).append('\n'); append("Files:       ").append(e.filesProcessed).append('\n'); append("Uploaded:    ").append(e.uploadedFiles).append('\n'); append("Downloaded:  ").append(e.downloadedFiles).append('\n'); append("Video:       ").append(e.videoFiles).append('\n'); append("Audio:       ").append(e.audioFiles).append('\n'); append("Documents:   ").append(e.documentFiles).append('\n'); append("Transferred: ").append(formatBytes(e.bytesTransferred)).append('\n'); append("Duration:    ").append(formatDuration(e.durationMs)).append('\n'); append("Result:      ").append(if (e.success || e.bytesTransferred > 0L) "Completed" else "Failed").append('\n'); append("Method:      ").append(e.direction.replace('_', ' ')).append('\n'); append("Time:        ").append(time) } }
    }
    private fun formatDuration(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return String.format(Locale.getDefault(), "%02d:%02d", s / 60, s % 60) }
    private fun formatBytes(bytes: Long): String { if (bytes < 1024) return "$bytes B"; var v = bytes.toDouble(); val u = arrayOf("KB", "MB", "GB", "TB"); var i = 0; while (v >= 1024 && i < u.lastIndex) { v /= 1024; i++ }; return String.format(Locale.getDefault(), "%.2f %s", v, u[i]) }
    override fun onSupportNavigateUp(): Boolean { finish(); return true }
    override fun onDestroy() { activeEngine?.cancel(); syncExecutor.shutdownNow(); super.onDestroy() }
}
