package com.riyaz.rsscloudsync

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.riyaz.rsscloudsync.databinding.ActivityCloudAccountsBinding
import java.util.concurrent.Executors

class CloudAccountsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCloudAccountsBinding
    private val prefs by lazy { getSharedPreferences("rss_cloud_sync", MODE_PRIVATE) }
    private val executor = Executors.newSingleThreadExecutor()
    private var emptyAddButton: MaterialButton? = null

    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            val connected = (prefs.getStringSet("connected_cloud_providers", emptySet()) ?: emptySet()).toMutableSet().apply { add(GoogleDriveAuthManager.PROVIDER) }
            prefs.edit().putStringSet("connected_cloud_providers", connected).putString("cloud_provider", GoogleDriveAuthManager.PROVIDER).putString("selected_cloud_provider", GoogleDriveAuthManager.PROVIDER).putString("google_drive_account_email", account.email ?: "").putString("google_drive_account_name", account.displayName ?: account.email ?: "Google Drive").apply()
            Toast.makeText(this, "Google Drive connected", Toast.LENGTH_SHORT).show()
            refreshState(); loadGoogleQuota()
        } catch (e: ApiException) {
            val detail = when (e.statusCode) {
                CommonStatusCodes.DEVELOPER_ERROR -> "Google sign-in configuration error (10). Check the Android OAuth package name and SHA-1 for this APK."
                GoogleSignInStatusCodes.SIGN_IN_CANCELLED, CommonStatusCodes.CANCELED -> "Google sign-in was cancelled. No account was connected."
                CommonStatusCodes.NETWORK_ERROR -> "Google sign-in network error. Check your internet connection."
                else -> "Google sign-in failed (${e.statusCode}: ${e.status.statusMessage ?: e.message ?: "Unknown error"})."
            }
            Toast.makeText(this, detail, Toast.LENGTH_LONG).show()
            refreshState()
        } catch (e: Exception) {
            Toast.makeText(this, "Google sign-in failed: ${e.message ?: "Unknown error"}", Toast.LENGTH_LONG).show()
            refreshState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCloudAccountsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Cloud accounts"
        binding.toolbar.setNavigationOnClickListener { finish() }
        setupEmptyStateButton(); setupProviderButtons(); refreshState(); loadGoogleQuota()
        polishCards()
    }

    override fun onResume() { super.onResume(); if (::binding.isInitialized) { refreshState(); loadGoogleQuota() } }

    private fun setupEmptyStateButton() {
        val scroll = binding.root.getChildAt(1) as? ScrollView ?: return
        val content = scroll.getChildAt(0) as? LinearLayout ?: return
        val button = MaterialButton(this).apply {
            text = "ADD CLOUD ACCOUNT"
            isAllCaps = false
            textSize = 14f
            setTextColor(Color.WHITE)
            cornerRadius = dp(24)
            setPadding(dp(18), 0, dp(18), 0)
            background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(Color.rgb(124,61,237), Color.rgb(38,181,235))).apply { cornerRadius = dp(24).toFloat() }
            setOnClickListener { binding.googleDriveConnect.performClick() }
        }
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)).apply { topMargin = dp(12); bottomMargin = dp(2) }
        content.addView(button, 2, params)
        emptyAddButton = button
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
        prefs.edit().putString("selected_cloud_provider", GoogleDriveAuthManager.PROVIDER).apply()
        val client = GoogleDriveAuthManager.signInClient(this)
        val connected = prefs.getStringSet("connected_cloud_providers", emptySet())?.contains(GoogleDriveAuthManager.PROVIDER) == true
        if (connected) client.signOut().addOnCompleteListener { clearGoogleAccountState(); googleSignInLauncher.launch(GoogleDriveAuthManager.signInClient(this).signInIntent) }
        else googleSignInLauncher.launch(client.signInIntent)
    }

    private fun clearGoogleAccountState() { prefs.edit().remove("google_drive_account_email").remove("google_drive_account_name").remove("google_drive_target_folder_id").remove("google_drive_target_folder_name").apply() }
    private fun selectProvider(provider: String) { prefs.edit().putString("selected_cloud_provider", provider).apply(); startActivity(Intent(this, SyncSetupActivity::class.java)) }

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
        binding.googleDriveConnect.text = if (connected.contains(GoogleDriveAuthManager.PROVIDER)) "CHANGE ACCOUNT" else "CONNECT"
        emptyAddButton?.visibility = if (connected.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateProvider(text: android.widget.TextView, progress: com.google.android.material.progressindicator.LinearProgressIndicator, connected: Boolean, selected: Boolean, account: String = "") {
        text.text = when { connected && account.isNotBlank() -> "Connected • $account"; connected -> "Connected"; selected -> "Ready to connect"; else -> "Not connected" }
        progress.isIndeterminate = false
        progress.setProgressCompat(if (connected) 100 else 0, false)
    }

    private fun loadGoogleQuota() {
        val connected = prefs.getStringSet("connected_cloud_providers", emptySet())?.contains(GoogleDriveAuthManager.PROVIDER) == true
        if (!connected) return
        binding.googleDriveStorageText.text = "Loading Google Drive storage..."
        executor.execute { try { val quota = DriveClient(this).quotaText(); runOnUiThread { if (!isFinishing) binding.googleDriveStorageText.text = quota } } catch (_: Exception) { runOnUiThread { if (!isFinishing) binding.googleDriveStorageText.text = "Connected • quota unavailable" } } }
    }

    private fun polishCards() {
        val light = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) != android.content.res.Configuration.UI_MODE_NIGHT_YES
        val surface = if (light) Color.WHITE else Color.rgb(15,22,36)
        val outline = if (light) Color.rgb(225,228,236) else Color.rgb(48,61,82)
        binding.root.setBackgroundColor(if (light) Color.rgb(247,248,252) else Color.rgb(7,11,20))
        styleCards(binding.root, surface, outline)
    }
    private fun styleCards(parent: ViewGroup, surface: Int, outline: Int) {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child is MaterialCardView) { child.setCardBackgroundColor(surface); child.strokeColor = outline; child.strokeWidth = dp(1); child.cardElevation = 0f; child.radius = dp(20).toFloat() }
            if (child is ViewGroup) styleCards(child, surface, outline)
        }
    }
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    override fun onSupportNavigateUp(): Boolean { finish(); return true }
    override fun onDestroy() { executor.shutdownNow(); super.onDestroy() }
}
