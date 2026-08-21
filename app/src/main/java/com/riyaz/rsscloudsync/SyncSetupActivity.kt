package com.riyaz.rsscloudsync

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.riyaz.rsscloudsync.databinding.ActivitySyncSetupBinding

class SyncSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySyncSetupBinding

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

        binding.saveSyncButton.setOnClickListener {
            saveSyncConfiguration()
        }
    }

    private fun setupCloudProvider() {

        val providers = arrayOf(
            "Google Drive",
            "Dropbox",
            "OneDrive",
            "MEGA",
            "Box",
            "Coming Soon"
        )

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            providers
        )

        adapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )

        binding.cloudProviderSpinner.adapter = adapter
    }

    private fun setupSyncDirection() {

        val directions = arrayOf(
            "Two-way Sync",
            "Upload Only",
            "Download Only"
        )

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            directions
        )

        adapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )

        binding.syncDirectionSpinner.adapter = adapter
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

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            schedules
        )

        adapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )

        binding.scheduleSpinner.adapter = adapter
    }

    private fun loadSelectedFolder() {

        val preferences = getSharedPreferences(
            "rss_cloud_sync",
            MODE_PRIVATE
        )

        val savedUri = preferences.getString(
            "sync_folder_uri",
            null
        )

        if (savedUri != null) {

            binding.localFolderText.text =
                savedUri

        } else {

            binding.localFolderText.text =
                "No local folder selected"
        }
    }

    private fun saveSyncConfiguration() {

        val preferences = getSharedPreferences(
            "rss_cloud_sync",
            MODE_PRIVATE
        )

        val localFolder =
            preferences.getString(
                "sync_folder_uri",
                null
            )

        if (localFolder == null) {

            Toast.makeText(
                this,
                "Please select a local folder first",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val cloudProvider =
            binding.cloudProviderSpinner
                .selectedItem
                .toString()

        val syncDirection =
            binding.syncDirectionSpinner
                .selectedItem
                .toString()

        val schedule =
            binding.scheduleSpinner
                .selectedItem
                .toString()

        preferences.edit()
            .putString(
                "cloud_provider",
                cloudProvider
            )
            .putString(
                "sync_direction",
                syncDirection
            )
            .putString(
                "sync_schedule",
                schedule
            )
            .putBoolean(
                "sync_configuration_saved",
                true
            )
            .apply()

        Toast.makeText(
            this,
            "Sync configuration saved",
            Toast.LENGTH_SHORT
        ).show()

        setResult(RESULT_OK)

        finish()
    }

    override fun onSupportNavigateUp(): Boolean {

        finish()

        return true
    }
}