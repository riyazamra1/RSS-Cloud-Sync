package com.riyaz.rsscloudsync

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import com.riyaz.rsscloudsync.databinding.ActivityCloudAccountsBinding

class CloudAccountsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCloudAccountsBinding
    private val prefs by lazy { getSharedPreferences("rss_cloud_sync", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCloudAccountsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Cloud accounts"
        binding.toolbar.setNavigationOnClickListener { finish() }
        setupProviderButtons()
        refreshState()
        animateRows()
    }

    override fun onResume() { super.onResume(); if (::binding.isInitialized) refreshState() }

    private fun setupProviderButtons() {
        binding.googleDriveConnect.setOnClickListener { connect("Google Drive") }
        binding.oneDriveConnect.setOnClickListener { connect("OneDrive") }
        binding.dropboxConnect.setOnClickListener { connect("Dropbox") }
        binding.megaConnect.setOnClickListener { connect("MEGA") }
        binding.boxConnect.setOnClickListener { connect("Box") }
        binding.webDavConnect.setOnClickListener { connect("WebDAV") }
    }

    private fun connect(provider: String) {
        prefs.edit().putString("selected_cloud_provider", provider).apply()
        startActivity(Intent(this, SyncSetupActivity::class.java))
    }

    private fun refreshState() {
        val selected = prefs.getString("cloud_provider", "") ?: ""
        val connected = prefs.getStringSet("connected_cloud_providers", emptySet()) ?: emptySet()
        updateProvider(binding.googleDriveStorageText, binding.googleDriveProgress, connected.contains("Google Drive"), selected == "Google Drive")
        updateProvider(binding.oneDriveStorageText, binding.oneDriveProgress, connected.contains("OneDrive"), selected == "OneDrive")
        updateProvider(binding.dropboxStorageText, binding.dropboxProgress, connected.contains("Dropbox"), selected == "Dropbox")
        updateProvider(binding.megaStorageText, binding.megaProgress, connected.contains("MEGA"), selected == "MEGA")
        updateProvider(binding.boxStorageText, binding.boxProgress, connected.contains("Box"), selected == "Box")
        binding.webDavStorageText.text = if (connected.contains("WebDAV")) "Connected" else "Not connected"
        binding.webDavProgress.isIndeterminate = false
        binding.webDavProgress.setProgressCompat(if (connected.contains("WebDAV")) 1 else 0, false)
        binding.totalStorageText.text = "No cloud account connected"
        binding.totalStorageProgress.isIndeterminate = false
        binding.totalStorageProgress.setProgressCompat(0, false)
    }

    private fun updateProvider(text: android.widget.TextView, progress: com.google.android.material.progressindicator.LinearProgressIndicator, connected: Boolean, selected: Boolean) {
        text.text = when {
            connected -> "Connected • quota available after provider API authorization"
            selected -> "Selected • connect account to continue"
            else -> "Not connected"
        }
        progress.isIndeterminate = false
        progress.setProgressCompat(0, false)
    }

    private fun animateRows() {
        val root = binding.root as? ViewGroup ?: return
        root.alpha = 1f
        root.translationY = 0f
        clearLoadingAnimations(root)
    }

    private fun clearLoadingAnimations(parent: ViewGroup) {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            child.animate().cancel()
            child.alpha = 1f
            child.translationY = 0f
            if (child is ViewGroup) clearLoadingAnimations(child)
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
