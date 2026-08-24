package com.riyaz.rsscloudsync

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.riyaz.rsscloudsync.databinding.ActivityCloudAccountsBinding
import java.util.concurrent.Executors

class CloudAccountsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCloudAccountsBinding
    private val prefs by lazy { getSharedPreferences("rss_cloud_sync", MODE_PRIVATE) }
    private val executor = Executors.newSingleThreadExecutor()

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) {
            Toast.makeText(this, "Google Drive sign-in cancelled", Toast.LENGTH_SHORT).show()
            refreshState()
            return@registerForActivityResult
        }
        val account = GoogleDriveAuthManager.accountFromResult(
            GoogleSignIn.getSignedInAccountFromIntent(result.data)
        )
        if (account == null) {
            Toast.makeText(this, "Google Drive sign-in failed", Toast.LENGTH_LONG).show()
            refreshState()
            return@registerForActivityResult
        }
        val connected = (prefs.getStringSet("connected_cloud_providers", emptySet()) ?: emptySet()).toMutableSet()
        connected.add(GoogleDriveAuthManager.PROVIDER)
        prefs.edit()
            .putStringSet("connected_cloud_providers", connected)
            .putString("cloud_provider", GoogleDriveAuthManager.PROVIDER)
            .putString("selected_cloud_provider", GoogleDriveAuthManager.PROVIDER)
            .putString("google_drive_account_email", account.email ?: "")
            .putString("google_drive_account_name", account.displayName ?: account.email ?: "Google Drive")
            .apply()
        Toast.makeText(this, "Google Drive connected", Toast.LENGTH_SHORT).show()
        refreshState()
        loadGoogleQuota()
    }

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
        loadGoogleQuota()
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) {
            refreshState()
            loadGoogleQuota()
        }
    }

    private fun setupProviderButtons() {
        binding.googleDriveConnect.setOnClickListener { connectGoogleDrive() }
        binding.oneDriveConnect.setOnClickListener { selectProvider("OneDrive") }
        binding.dropboxConnect.setOnClickListener { selectProvider("Dropbox") }
        binding.megaConnect.setOnClickListener { selectProvider("MEGA") }
        binding.boxConnect.setOnClickListener { selectProvider("Box") }
        binding.webDavConnect.setOnClickListener { selectProvider("WebDAV") }
    }

    private fun connectGoogleDrive() {
        val connected = prefs.getStringSet("connected_cloud_providers", emptySet())?.contains(GoogleDriveAuthManager.PROVIDER) == true
        prefs.edit().putString("selected_cloud_provider", GoogleDriveAuthManager.PROVIDER).apply()
        if (connected) {
            GoogleDriveAuthManager.switchAccount(this)
        }
        googleSignInLauncher.launch(GoogleDriveAuthManager.signInClient(this).signInIntent)
    }

    private fun selectProvider(provider: String) {
        prefs.edit().putString("selected_cloud_provider", provider).apply()
        startActivity(Intent(this, SyncSetupActivity::class.java))
    }

    private fun refreshState() {
        val selected = prefs.getString("cloud_provider", "") ?: ""
        val connected = prefs.getStringSet("connected_cloud_providers", emptySet()) ?: emptySet()
        val googleEmail = prefs.getString("google_drive_account_email", "") ?: ""
        updateProvider(binding.googleDriveStorageText, binding.googleDriveProgress, connected.contains(GoogleDriveAuthManager.PROVIDER), selected == GoogleDriveAuthManager.PROVIDER, googleEmail)
        updateProvider(binding.oneDriveStorageText, binding.oneDriveProgress, connected.contains("OneDrive"), selected == "OneDrive")
        updateProvider(binding.dropboxStorageText, binding.dropboxProgress, connected.contains("Dropbox"), selected == "Dropbox")
        updateProvider(binding.megaStorageText, binding.megaProgress, connected.contains("MEGA"), selected == "MEGA")
        updateProvider(binding.boxStorageText, binding.boxProgress, connected.contains("Box"), selected == "Box")
        binding.webDavStorageText.text = if (connected.contains("WebDAV")) "Connected" else "Not connected"
        binding.webDavProgress.isIndeterminate = false
        binding.webDavProgress.setProgressCompat(if (connected.contains("WebDAV")) 100 else 0, false)
        binding.totalStorageText.text = if (connected.isEmpty()) "No cloud account connected" else "Connected accounts: ${connected.size}"
        binding.totalStorageProgress.isIndeterminate = false
        binding.totalStorageProgress.setProgressCompat(0, false)
        binding.googleDriveConnect.text = if (connected.contains(GoogleDriveAuthManager.PROVIDER)) "CHANGE" else "CONNECT"
    }

    private fun updateProvider(text: android.widget.TextView, progress: com.google.android.material.progressindicator.LinearProgressIndicator, connected: Boolean, selected: Boolean, account: String = "") {
        text.text = when {
            connected && account.isNotBlank() -> "Connected • $account"
            connected -> "Connected"
            selected -> "Ready to connect"
            else -> "Not connected"
        }
        progress.isIndeterminate = false
        progress.setProgressCompat(if (connected) 100 else 0, false)
    }

    private fun loadGoogleQuota() {
        val connected = prefs.getStringSet("connected_cloud_providers", emptySet())?.contains(GoogleDriveAuthManager.PROVIDER) == true
        if (!connected) return
        binding.googleDriveStorageText.text = "Loading Google Drive storage..."
        executor.execute {
            try {
                val quota = DriveClient(this).quotaText()
                runOnUiThread { if (!isFinishing) binding.googleDriveStorageText.text = quota }
            } catch (_: Exception) {
                runOnUiThread { if (!isFinishing) binding.googleDriveStorageText.text = "Connected • quota unavailable" }
            }
        }
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

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
