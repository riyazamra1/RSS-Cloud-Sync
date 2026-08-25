package com.riyaz.rsscloudsync

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.riyaz.rsscloudsync.databinding.ActivityCloudAccountsBinding
import java.util.concurrent.Executors

class CloudAccountsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCloudAccountsBinding
    private val prefs by lazy { getSharedPreferences("rss_cloud_sync", MODE_PRIVATE) }
    private val executor = Executors.newSingleThreadExecutor()

    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK) {
            Toast.makeText(this, "Google Drive sign-in cancelled", Toast.LENGTH_SHORT).show(); refreshState(); return@registerForActivityResult
        }
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java)
            val connected = (prefs.getStringSet("connected_cloud_providers", emptySet()) ?: emptySet()).toMutableSet().apply { add(GoogleDriveAuthManager.PROVIDER) }
            prefs.edit().putStringSet("connected_cloud_providers", connected).putString("cloud_provider", GoogleDriveAuthManager.PROVIDER).putString("selected_cloud_provider", GoogleDriveAuthManager.PROVIDER).putString("google_drive_account_email", account.email ?: "").putString("google_drive_account_name", account.displayName ?: account.email ?: "Google Drive").apply()
            Toast.makeText(this, "Google Drive connected", Toast.LENGTH_SHORT).show(); refreshState(); loadGoogleQuota()
        } catch (e: ApiException) {
            val detail = when (e.statusCode) {
                CommonStatusCodes.DEVELOPER_ERROR -> "OAuth configuration error (DEVELOPER_ERROR). Check package name and SHA-1 in Google Cloud Console."
                CommonStatusCodes.SIGN_IN_CANCELLED -> "Google Drive sign-in cancelled."
                CommonStatusCodes.NETWORK_ERROR -> "Network error. Check your internet connection."
                else -> "Google Drive sign-in failed (${e.statusCode})."
            }
            Toast.makeText(this, detail, Toast.LENGTH_LONG).show(); refreshState()
        } catch (e: Exception) {
            Toast.makeText(this, "Google Drive sign-in failed: ${e.message ?: "Unknown error"}", Toast.LENGTH_LONG).show(); refreshState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCloudAccountsBinding.inflate(layoutInflater); setContentView(binding.root); setSupportActionBar(binding.toolbar); supportActionBar?.setDisplayHomeAsUpEnabled(true); supportActionBar?.title = "Cloud accounts"; binding.toolbar.setNavigationOnClickListener { finish() }
        setupProviderButtons(); refreshState(); animateRows(); loadGoogleQuota()
    }
    override fun onResume() { super.onResume(); if (::binding.isInitialized) { refreshState(); loadGoogleQuota() } }
    private fun setupProviderButtons() {
        binding.googleDriveConnect.setOnClickListener { connectGoogleDrive() }
        binding.oneDriveConnect.setOnClickListener { selectProvider("OneDrive") }; binding.dropboxConnect.setOnClickListener { selectProvider("Dropbox") }; binding.megaConnect.setOnClickListener { selectProvider("MEGA") }; binding.boxConnect.setOnClickListener { selectProvider("Box") }; binding.webDavConnect.setOnClickListener { selectProvider("WebDAV") }
    }
    private fun connectGoogleDrive() {
        val connected = prefs.getStringSet("connected_cloud_providers", emptySet())?.contains(GoogleDriveAuthManager.PROVIDER) == true
        prefs.edit().putString("selected_cloud_provider", GoogleDriveAuthManager.PROVIDER).apply()
        if (connected) GoogleDriveAuthManager.switchAccount(this)
        googleSignInLauncher.launch(GoogleDriveAuthManager.signInClient(this).signInIntent)
    }
    private fun selectProvider(provider: String) { prefs.edit().putString("selected_cloud_provider", provider).apply(); startActivity(Intent(this, SyncSetupActivity::class.java)) }
    private fun refreshState() {
        val selected = prefs.getString("cloud_provider", "") ?: ""; val connected = prefs.getStringSet("connected_cloud_providers", emptySet()) ?: emptySet(); val googleEmail = prefs.getString("google_drive_account_email", "") ?: ""
        updateProvider(binding.googleDriveStorageText, binding.googleDriveProgress, connected.contains(GoogleDriveAuthManager.PROVIDER), selected == GoogleDriveAuthManager.PROVIDER, googleEmail); updateProvider(binding.oneDriveStorageText, binding.oneDriveProgress, connected.contains("OneDrive"), selected == "OneDrive"); updateProvider(binding.dropboxStorageText, binding.dropboxProgress, connected.contains("Dropbox"), selected == "Dropbox"); updateProvider(binding.megaStorageText, binding.megaProgress, connected.contains("MEGA"), selected == "MEGA"); updateProvider(binding.boxStorageText, binding.boxProgress, connected.contains("Box"), selected == "Box")
        binding.webDavStorageText.text = if (connected.contains("WebDAV")) "Connected" else "Not connected"; binding.webDavProgress.isIndeterminate = false; binding.webDavProgress.setProgressCompat(if (connected.contains("WebDAV")) 100 else 0, false); binding.totalStorageText.text = if (connected.isEmpty()) "No cloud account connected" else "Connected accounts: ${connected.size}"; binding.totalStorageProgress.isIndeterminate = false; binding.totalStorageProgress.setProgressCompat(0, false); binding.googleDriveConnect.text = if (connected.contains(GoogleDriveAuthManager.PROVIDER)) "CHANGE" else "CONNECT"
    }
    private fun updateProvider(text: android.widget.TextView, progress: com.google.android.material.progressindicator.LinearProgressIndicator, connected: Boolean, selected: Boolean, account: String = "") { text.text = when { connected && account.isNotBlank() -> "Connected • $account"; connected -> "Connected"; selected -> "Ready to connect"; else -> "Not connected" }; progress.isIndeterminate = false; progress.setProgressCompat(if (connected) 100 else 0, false) }
    private fun loadGoogleQuota() {
        val connected = prefs.getStringSet("connected_cloud_providers", emptySet())?.contains(GoogleDriveAuthManager.PROVIDER) == true; if (!connected) return
        binding.googleDriveStorageText.text = "Loading Google Drive storage..."; executor.execute { try { val quota = DriveClient(this).quotaText(); runOnUiThread { if (!isFinishing) binding.googleDriveStorageText.text = quota } } catch (e: Exception) { runOnUiThread { if (!isFinishing) binding.googleDriveStorageText.text = "Connected • quota unavailable" } } }
    }
    private fun animateRows() { binding.root.alpha = 1f; binding.root.translationY = 0f }
    override fun onSupportNavigateUp(): Boolean { finish(); return true }
    override fun onDestroy() { executor.shutdownNow(); super.onDestroy() }
}
