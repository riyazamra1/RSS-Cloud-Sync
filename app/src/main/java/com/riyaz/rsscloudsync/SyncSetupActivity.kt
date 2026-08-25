package com.riyaz.rsscloudsync

import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
    private var accountProviders = emptyList<String>()

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@registerForActivityResult
        try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (_: SecurityException) { Toast.makeText(this, "Folder permission could not be saved", Toast.LENGTH_SHORT).show() }
        prefs.edit().putString(if (selectingTarget) "external_storage_uri" else "sync_folder_uri", uri.toString()).apply()
        if (!selectingTarget) prefs.edit().remove("selected_local_files").apply()
        loadFolders()
    }
    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) { binding.selectFilesByNameCheckBox.isChecked = false; return@registerForActivityResult }
        uris.forEach { uri -> try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (_: SecurityException) {} }
        prefs.edit().putStringSet("selected_local_files", uris.map(Uri::toString).toSet()).remove("sync_folder_uri").apply()
        binding.selectFilesByNameCheckBox.isChecked = true
        loadFolders()
    }
    private val driveFolderPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK || result.data == null) return@registerForActivityResult
        val id = result.data?.getStringExtra("folder_id") ?: return@registerForActivityResult
        val name = result.data?.getStringExtra("folder_name") ?: "My Drive"
        prefs.edit().putString("google_drive_target_folder_id", id).putString("google_drive_target_folder_name", name).apply()
        loadFolders()
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        binding = ActivitySyncSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Folder pair"
        setupCloudAccounts(); setupDirection(); loadConfiguration(); loadFolders()
        binding.chooseLocalButton.setOnClickListener { chooseLocalSource() }
        binding.chooseTargetButton.setOnClickListener { chooseTarget() }
        binding.savePairButton.setOnClickListener { savePair(); Toast.makeText(this, "Folder pair saved", Toast.LENGTH_SHORT).show() }
        binding.syncNowButton.setOnClickListener { if (activeEngine == null && activeDriveEngine == null) startSync() else { activeEngine?.cancel(); activeDriveEngine?.cancel() } }
        binding.clearHistoryButton.setOnClickListener { SyncHistoryManager.clear(this); loadHistory() }
        binding.selectFilesByNameCheckBox.setOnCheckedChangeListener { _, checked -> if (checked) filePicker.launch(arrayOf("*/*")) }
        binding.folderPairEnabledSwitch.setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("folder_pair_enabled", checked).apply() }
    }

    private fun setupCloudAccounts() {
        val connected = prefs.getStringSet("connected_cloud_providers", emptySet()) ?: emptySet()
        val accounts = ArrayList<String>()
        accountProviders = ArrayList<String>().apply {
            if (connected.contains("Google Drive")) {
                val email = prefs.getString("google_drive_account_email", null)
                accounts += if (email.isNullOrBlank()) "Google Drive" else "Google Drive • $email"
                add("Google Drive")
            }
            connected.filter { it != "Google Drive" }.sorted().forEach { provider -> accounts += provider; add(provider) }
        }
        if (accounts.isEmpty()) accounts += "No connected cloud accounts"
        binding.cloudProviderSpinner.adapter = spinnerAdapter(accounts.toTypedArray())
        binding.cloudProviderSpinner.setSelection(0)
        binding.cloudProviderSpinner.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) { loadFolders() }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        })
    }
    private fun selectedProvider(): String? = accountProviders.getOrNull(binding.cloudProviderSpinner.selectedItemPosition)

    private fun chooseLocalSource() {
        MaterialAlertDialogBuilder(this).setTitle("Local source").setItems(arrayOf("Folder", "Individual files")) { _, which ->
            if (which == 0) { selectingTarget = false; folderPicker.launch(null) } else binding.selectFilesByNameCheckBox.isChecked = true
        }.show()
    }
    private fun chooseTarget() {
        if (selectedProvider() == null) { Toast.makeText(this, "Connect a cloud account first", Toast.LENGTH_SHORT).show(); return }
        if (selectedProvider() == "Google Drive") driveFolderPicker.launch(Intent(this, GoogleDriveFolderPickerActivity::class.java))
        else Toast.makeText(this, "This cloud provider's folder browser is not available yet", Toast.LENGTH_LONG).show()
    }
    private fun setupDirection() { binding.syncDirectionSpinner.adapter = spinnerAdapter(arrayOf("Two-way Sync", "Upload only", "Upload mirror", "Upload then delete", "Download only", "Download mirror", "Download then delete")) }
    private fun spinnerAdapter(items: Array<String>) = ArrayAdapter(this, android.R.layout.simple_spinner_item, items).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

    private fun loadConfiguration() {
        binding.folderPairNameEditText.setText(prefs.getString("folder_pair_name", "My Folder Pair"))
        prefs.getString("sync_direction", null)?.let { selectSpinnerValue(binding.syncDirectionSpinner, it) }
        binding.folderPairEnabledSwitch.isChecked = prefs.getBoolean("folder_pair_enabled", true)
        binding.excludeHiddenFilesCheckBox.isChecked = prefs.getBoolean("exclude_hidden_files", true)
        binding.excludeSubfoldersCheckBox.isChecked = prefs.getBoolean("exclude_subfolders", false)
        binding.deleteEmptySubfoldersCheckBox.isChecked = prefs.getBoolean("delete_empty_subfolders", false)
        binding.selectFilesByNameCheckBox.isChecked = (prefs.getStringSet("selected_local_files", emptySet()) ?: emptySet()).isNotEmpty()
    }
    private fun savePair() {
        prefs.edit().putString("folder_pair_name", binding.folderPairNameEditText.text?.toString()?.trim().orEmpty().ifBlank { "My Folder Pair" })
            .putString("sync_direction", binding.syncDirectionSpinner.selectedItem.toString())
            .putBoolean("folder_pair_enabled", binding.folderPairEnabledSwitch.isChecked)
            .putBoolean("exclude_hidden_files", binding.excludeHiddenFilesCheckBox.isChecked)
            .putBoolean("exclude_subfolders", binding.excludeSubfoldersCheckBox.isChecked)
            .putBoolean("delete_empty_subfolders", binding.deleteEmptySubfoldersCheckBox.isChecked).apply()
    }
    private fun loadFolders() {
        val files = prefs.getStringSet("selected_local_files", emptySet()) ?: emptySet(); val folder = prefs.getString("sync_folder_uri", null)
        binding.localFolderText.text = when { files.isNotEmpty() -> "${files.size} individual file${if (files.size == 1) "" else "s"} selected"; folder != null -> prettyUri(folder); else -> "Select a local folder" }
        val provider = selectedProvider()
        binding.cloudAccountName.text = when { provider == "Google Drive" -> "Google Drive • ${prefs.getString("google_drive_account_email", "signed-in account")}"; provider != null -> provider; else -> "Only signed-in cloud accounts are shown" }
        binding.targetFolderText.text = if (provider == "Google Drive") prefs.getString("google_drive_target_folder_name", null)?.let { "Google Drive / $it" } ?: "Select a cloud folder" else "Select a cloud folder"
    }

    private fun startSync() {
        savePair()
        if (!binding.folderPairEnabledSwitch.isChecked) { Toast.makeText(this, "This folder pair is disabled", Toast.LENGTH_SHORT).show(); return }
        val provider = selectedProvider() ?: run { Toast.makeText(this, "Connect a cloud account first", Toast.LENGTH_SHORT).show(); return }
        val directionName = binding.syncDirectionSpinner.selectedItem.toString()
        if (provider == "Google Drive") startGoogleDriveSync(directionName) else Toast.makeText(this, "Only Google Drive sync is available in this build", Toast.LENGTH_LONG).show()
    }

    private fun startGoogleDriveSync(directionName: String) {
        val folderId = prefs.getString("google_drive_target_folder_id", null); val localFolder = prefs.getString("sync_folder_uri", null)
        val files = (prefs.getStringSet("selected_local_files", emptySet()) ?: emptySet()).map(Uri::parse)
        if (folderId == null) { Toast.makeText(this, "Select a Google Drive target folder", Toast.LENGTH_SHORT).show(); return }
        if (localFolder == null && files.isEmpty()) { Toast.makeText(this, "Select a local folder or individual files", Toast.LENGTH_SHORT).show(); return }
        binding.syncNowButton.text = "CANCEL SYNC"; binding.syncStatusText.text = "Google Drive sync in progress..."
        executor.execute {
            try {
                val engine = GoogleDriveSyncEngine(this, contentResolver); activeDriveEngine = engine
                val result = if (files.isNotEmpty()) {
                    if (directionName !in listOf("Upload only", "Upload mirror", "Upload then delete")) throw IllegalStateException("Individual file selection supports upload modes only")
                    engine.uploadSelectedFiles(files, folderId) { p -> updateDriveProgress(p) }
                } else {
                    val dir = when (directionName) { "Upload only" -> GoogleDriveSyncEngine.Direction.UPLOAD_ONLY; "Upload mirror" -> GoogleDriveSyncEngine.Direction.UPLOAD_MIRROR; "Upload then delete" -> GoogleDriveSyncEngine.Direction.UPLOAD_THEN_DELETE; "Download only" -> GoogleDriveSyncEngine.Direction.DOWNLOAD_ONLY; "Download mirror" -> GoogleDriveSyncEngine.Direction.DOWNLOAD_MIRROR; "Download then delete" -> GoogleDriveSyncEngine.Direction.DOWNLOAD_THEN_DELETE; else -> GoogleDriveSyncEngine.Direction.TWO_WAY }
                    val options = GoogleDriveSyncEngine.Options(binding.excludeHiddenFilesCheckBox.isChecked, binding.excludeSubfoldersCheckBox.isChecked, binding.deleteEmptySubfoldersCheckBox.isChecked)
                    engine.sync(Uri.parse(localFolder!!), folderId, dir, options) { p -> updateDriveProgress(p) }
                }
                runOnUiThread { activeDriveEngine = null; binding.syncNowButton.text = "SYNC NOW"; binding.syncStatusText.text = if (result.failed == 0) "Sync completed" else "Sync completed with warnings"; binding.syncStatusDetail.text = "Files: ${result.processed} • ↑${result.uploaded} ↓${result.downloaded} • Failed: ${result.failed} • ${formatBytes(result.bytes)}"; binding.progressText.text = "100%"; loadHistory() }
            } catch (e: Exception) { runOnUiThread { activeDriveEngine = null; binding.syncNowButton.text = "SYNC NOW"; binding.syncStatusText.text = "Sync failed"; binding.syncStatusDetail.text = e.message ?: "Google Drive sync failed" } }
        }
    }
    private fun updateDriveProgress(progress: GoogleDriveSyncEngine.Progress) { runOnUiThread { binding.progressText.text = if (progress.total == 0) "100%" else "${progress.processed * 100 / progress.total}%"; binding.syncStatusDetail.text = "${progress.processed}/${progress.total} • ↑${progress.uploaded} ↓${progress.downloaded} • Failed: ${progress.failed} • ${formatBytes(progress.bytes)}"; binding.currentFileText.text = progress.currentPath } }
    private fun selectSpinnerValue(spinner: android.widget.Spinner, value: String) { for (i in 0 until spinner.count) if (spinner.getItemAtPosition(i).toString() == value) { spinner.setSelection(i); return } }
    private fun prettyUri(value: String) = (Uri.parse(value).lastPathSegment ?: value).substringAfterLast(':').replace("%20", " ").ifBlank { value }
    private fun formatBytes(bytes: Long): String { if (bytes < 1024) return "$bytes B"; var value = bytes.toDouble(); val units = arrayOf("KB", "MB", "GB", "TB"); var index = 0; while (value >= 1024 && index < units.lastIndex) { value /= 1024; index++ }; return String.format(Locale.getDefault(), "%.2f %s", value, units[index]) }
    private fun loadHistory() { val entries = SyncHistoryManager.get(this); binding.historyText.text = if (entries.isEmpty()) "No sync history yet." else entries.take(10).joinToString("\n\n") { e -> "${if (e.success) "✓" else "⚠"} ${e.direction.replace('_', ' ')}\nFiles: ${e.filesProcessed} • ↑${e.uploadedFiles} ↓${e.downloadedFiles} • Failed: ${e.failedFiles}\nTransferred: ${formatBytes(e.bytesTransferred)}" } }
    override fun onSupportNavigateUp(): Boolean { finish(); return true }
    override fun onDestroy() { activeEngine?.cancel(); activeDriveEngine?.cancel(); executor.shutdownNow(); super.onDestroy() }
}