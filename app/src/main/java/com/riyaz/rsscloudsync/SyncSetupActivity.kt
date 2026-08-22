package com.riyaz.rsscloudsync

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

        binding.saveSyncButton.setOnClickListener { saveSyncConfiguration() }
    }

    private fun setupCloudProvider() {
        val providers = arrayOf(
            "Google Drive",
            "OneDrive",
            "Dropbox",
            "MEGA",
            "Box",
            "pCloud",
            "WebDAV",
            "NAS / SMB"
        )
        binding.cloudProviderSpinner.adapter = spinnerAdapter(providers)
    }

    private fun setupSyncDirection() {
        val directions = arrayOf(
            "Two-way Sync",
            "Upload only",
            "Upload mirror",
            "Upload then delete",
            "Download only",
            "Download mirror",
            "Download then delete"
        )
        binding.syncDirectionSpinner.adapter = spinnerAdapter(directions)
    }

    private fun setupSchedule() {
        val schedules = arrayOf(
            "Manual",
            "Every 15 minutes",
            "Every 30 minutes",
            "Every 1 hour",
            "Every 6 hours",
            "Every 12 hours",
            "Daily"
        )
        binding.scheduleSpinner.adapter = spinnerAdapter(schedules)
    }

    private fun spinnerAdapter(items: Array<String>): ArrayAdapter<String> {
        return ArrayAdapter(this, android.R.layout.simple_spinner_item, items).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }

    private fun loadSelectedFolder() {
        val savedUri = preferences.getString("sync_folder_uri", null)
        binding.localFolderText.text = savedUri ?: "No local folder selected"

        val savedProvider = preferences.getString("cloud_provider", null)
        val savedDirection = preferences.getString("sync_direction", null)
        val savedSchedule = preferences.getString("sync_schedule", null)

        savedProvider?.let { selectSpinnerValue(binding.cloudProviderSpinner, it) }
        savedDirection?.let { selectSpinnerValue(binding.syncDirectionSpinner, it) }
        savedSchedule?.let { selectSpinnerValue(binding.scheduleSpinner, it) }
    }

    private fun selectSpinnerValue(spinner: android.widget.Spinner, value: String) {
        for (index in 0 until spinner.count) {
            if (spinner.getItemAtPosition(index).toString() == value) {
                spinner.setSelection(index)
                return
            }
        }
    }

    private fun saveSyncConfiguration() {
        val localFolder = preferences.getString("sync_folder_uri", null)
        if (localFolder == null) {
            Toast.makeText(this, "Please select a local folder first", Toast.LENGTH_SHORT).show()
            return
        }

        val cloudProvider = binding.cloudProviderSpinner.selectedItem.toString()
        val syncDirection = binding.syncDirectionSpinner.selectedItem.toString()
        val schedule = binding.scheduleSpinner.selectedItem.toString()
        val premium = preferences.getBoolean("premium_unlocked", false)

        val premiumDirection = syncDirection != "Two-way Sync"
        val premiumSchedule = schedule != "Manual"

        if (!premium && (premiumDirection || premiumSchedule)) {
            AlertDialog.Builder(this)
                .setTitle("Premium feature")
                .setMessage("FREE includes only Two-way Sync and Manual Sync. Upgrade to PREMIUM to use this sync direction or automatic schedule.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("View Premium") { _, _ ->
                    AlertDialog.Builder(this)
                        .setTitle("PREMIUM")
                        .setMessage("Unlock all 7 sync directions, automatic scheduling, multiple folder pairs, advanced filtering and no ads.")
                        .setPositiveButton("OK", null)
                        .show()
                }
                .show()
            return
        }

        preferences.edit()
            .putString("cloud_provider", cloudProvider)
            .putString("sync_direction", syncDirection)
            .putString("sync_schedule", schedule)
            .putBoolean("sync_configuration_saved", true)
            .apply()

        Toast.makeText(this, "Sync configuration saved", Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK)
        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}