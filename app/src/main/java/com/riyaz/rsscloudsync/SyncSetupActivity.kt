package com.riyaz.rsscloudsync

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.widget.Toast
import com.riyaz.rsscloudsync.databinding.ActivitySyncSetupBinding

class SyncSetupActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySyncSetupBinding
    private val preferences by lazy { getSharedPreferences("rss_cloud_sync", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySyncSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Sync Setup"
        setupCloudProvider()
        setupSyncDirection()
        setupSchedule()
        loadSelectedFolder()
        updateTargetStatus()
        binding.saveSyncButton.setOnClickListener { saveSyncConfiguration() }
    }

    private fun setupCloudProvider() {
        val providers = arrayOf("External storage", "Google Drive", "OneDrive", "Dropbox", "MEGA", "Box", "pCloud", "WebDAV", "NAS / SMB")
        binding.cloudProviderSpinner.adapter = spinnerAdapter(providers)
        binding.cloudProviderSpinner.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                binding.cloudConnectionStatus.text = if (position == 0) "Connected to selected external storage folder" else "Provider connection required"
            }
        })
    }

    private fun setupSyncDirection() {
        val directions = arrayOf("Two-way Sync", "Upload only", "Upload mirror", "Upload then delete", "Download only", "Download mirror", "Download then delete")
        binding.syncDirectionSpinner.adapter = spinnerAdapter(directions)
    }

    private fun setupSchedule() {
        val schedules = arrayOf("Manual", "Every 15 minutes", "Every 30 minutes", "Every 1 hour", "Every 6 hours", "Every 12 hours", "Daily")
        binding.scheduleSpinner.adapter = spinnerAdapter(schedules)
    }

    private fun spinnerAdapter(items: Array<String>): ArrayAdapter<String> = ArrayAdapter(this, android.R.layout.simple_spinner_item, items).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

    private fun loadSelectedFolder() {
        val savedUri = preferences.getString("sync_folder_uri", null)
        binding.localFolderText.text = savedUri ?: "No local folder selected"
        preferences.getString("cloud_provider", null)?.let { selectSpinnerValue(binding.cloudProviderSpinner, it) }
        preferences.getString("sync_direction", null)?.let { selectSpinnerValue(binding.syncDirectionSpinner, it) }
        preferences.getString("sync_schedule", null)?.let { selectSpinnerValue(binding.scheduleSpinner, it) }
    }

    private fun updateTargetStatus() {
        val target = preferences.getString("external_storage_uri", null)
        if (target != null && binding.cloudProviderSpinner.selectedItem?.toString() == "External storage") binding.cloudConnectionStatus.text = "Connected to external storage"
    }

    private fun selectSpinnerValue(spinner: android.widget.Spinner, value: String) {
        for (index in 0 until spinner.count) if (spinner.getItemAtPosition(index).toString() == value) { spinner.setSelection(index); return }
    }

    private fun saveSyncConfiguration() {
        val localFolder = preferences.getString("sync_folder_uri", null)
        val targetFolder = preferences.getString("external_storage_uri", null)
        if (localFolder == null) {
            Toast.makeText(this, "Please select a local folder first", Toast.LENGTH_SHORT).show()
            return
        }
        val cloudProvider = binding.cloudProviderSpinner.selectedItem.toString()
        if (cloudProvider == "External storage" && targetFolder == null) {
            AlertDialog.Builder(this).setTitle("External storage not selected").setMessage("Choose an external storage folder before saving this sync configuration.").setPositiveButton("OK", null).show()
            return
        }
        val syncDirection = binding.syncDirectionSpinner.selectedItem.toString()
        val schedule = binding.scheduleSpinner.selectedItem.toString()
        val premium = preferences.getBoolean("premium_unlocked", false)
        if (!premium && (syncDirection != "Two-way Sync" || schedule != "Manual")) {
            AlertDialog.Builder(this).setTitle("Premium feature").setMessage("FREE includes only Two-way Sync and Manual Sync. Upgrade to PREMIUM to use this sync direction or automatic schedule.").setNegativeButton("Cancel", null).setPositiveButton("View Premium") { _, _ -> startActivity(Intent(this, PremiumActivity::class.java)) }.show()
            return
        }
        preferences.edit().putString("cloud_provider", cloudProvider).putString("sync_direction", syncDirection).putString("sync_schedule", schedule).putBoolean("sync_configuration_saved", true).apply()
        Toast.makeText(this, "Sync configuration saved", Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK)
        finish()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
