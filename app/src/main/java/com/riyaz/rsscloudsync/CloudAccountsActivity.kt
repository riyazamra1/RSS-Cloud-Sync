package com.riyaz.rsscloudsync

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

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) refreshState()
    }

    private fun setupProviderButtons() {
        binding.googleDriveConnect.setOnClickListener { openProvider("Google Drive") }
        binding.oneDriveConnect.setOnClickListener { openProvider("OneDrive") }
        binding.dropboxConnect.setOnClickListener { openProvider("Dropbox") }
        binding.megaConnect.setOnClickListener { openProvider("MEGA") }
        binding.boxConnect.setOnClickListener { openProvider("Box") }
        binding.webDavConnect.setOnClickListener { openProvider("WebDAV") }
    }

    private fun openProvider(provider: String) {
        prefs.edit().putString("selected_cloud_provider", provider).apply()
        if (provider == "WebDAV") {
            startActivity(android.content.Intent(this, SyncSetupActivity::class.java))
        } else {
            startActivity(android.content.Intent(this, SyncSetupActivity::class.java))
        }
    }

    private fun refreshState() {
        val selected = prefs.getString("cloud_provider", "") ?: ""
        val connected = prefs.getStringSet("connected_cloud_providers", emptySet()) ?: emptySet()
        updateProvider("Google Drive", binding.googleDriveStorageText, binding.googleDriveProgress, connected.contains("Google Drive"), selected == "Google Drive")
        updateProvider("OneDrive", binding.oneDriveStorageText, binding.oneDriveProgress, connected.contains("OneDrive"), selected == "OneDrive")
        updateProvider("Dropbox", binding.dropboxStorageText, binding.dropboxProgress, connected.contains("Dropbox"), selected == "Dropbox")
        updateProvider("MEGA", binding.megaStorageText, binding.megaProgress, connected.contains("MEGA"), selected == "MEGA")
        updateProvider("Box", binding.boxStorageText, binding.boxProgress, connected.contains("Box"), selected == "Box")
        binding.webDavStorageText.text = if (connected.contains("WebDAV")) "Configured" else "Not connected"
        binding.webDavProgress.isIndeterminate = false
        binding.webDavProgress.setProgressCompat(if (connected.contains("WebDAV")) 1 else 0, false)
        binding.totalStorageText.text = "Connect a provider to read its real quota"
        binding.totalStorageProgress.isIndeterminate = false
        binding.totalStorageProgress.setProgressCompat(0, false)
    }

    private fun updateProvider(name: String, text: android.widget.TextView, progress: com.google.android.material.progressindicator.LinearProgressIndicator, isConnected: Boolean, selected: Boolean) {
        text.text = when {
            isConnected -> "Connected • quota will be shown after provider authentication"
            selected -> "Selected • authentication required"
            else -> "Not connected"
        }
        progress.isIndeterminate = false
        progress.setProgressCompat(0, false)
    }

    private fun animateRows() {
        val root = binding.root as? ViewGroup ?: return
        root.alpha = 1f
        root.translationY = 0f
        animateProviderCards(root)
    }

    private fun animateProviderCards(parent: ViewGroup) {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child is com.google.android.material.card.MaterialCardView) {
                child.alpha = 1f
                child.translationY = 0f
            }
            if (child is ViewGroup) animateProviderCards(child)
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
