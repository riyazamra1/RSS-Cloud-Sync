package com.riyaz.rsscloudsync

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
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
import java.util.Locale
import java.util.concurrent.Executors

class CloudAccountsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCloudAccountsBinding
    private val prefs by lazy { getSharedPreferences("rss_cloud_sync", MODE_PRIVATE) }
    private val executor = Executors.newSingleThreadExecutor()
    private var emptyAddButton: MaterialButton? = null

    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java)
            val connected = (prefs.getStringSet("connected_cloud_providers", emptySet()) ?: emptySet()).toMutableSet().apply { add(GoogleDriveAuthManager.PROVIDER) }
            prefs.edit()
                .putStringSet("connected_cloud_providers", connected)
                .putString("cloud_provider", GoogleDriveAuthManager.PROVIDER)
                .putString("selected_cloud_provider", GoogleDriveAuthManager.PROVIDER)
                .putString("google_drive_account_email", account.email ?: "")
                .putString("google_drive_account_name", account.displayName ?: account.email ?: "Google Drive")
                .apply()
            Toast.makeText(this, "Google Drive connected", Toast.LENGTH_SHORT).show()
            refreshState(); loadGoogleQuota()
        } catch (e: ApiException) {
            val detail = when (e.statusCode) {
                CommonStatusCodes.DEVELOPER_ERROR -> "Google sign-in configuration error (10). Check the APK SHA-1 and package name in Google Cloud."
                GoogleSignInStatusCodes.SIGN_IN_CANCELLED, CommonStatusCodes.CANCELED -> "Google sign-in was cancelled."
                CommonStatusCodes.NETWORK_ERROR -> "Google sign-in network error."
                else -> "Google sign-in failed (${e.statusCode}: ${e.status.statusMessage ?: e.message ?: "Unknown error"})."
            }
            Toast.makeText(this, detail, Toast.LENGTH_LONG).show(); refreshState()
        } catch (e: Exception) {
            Toast.makeText(this, "Google sign-in failed: ${e.message ?: "Unknown error"}", Toast.LENGTH_LONG).show(); refreshState()
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
        setupEmptyStateButton(); setupProviderButtons(); refreshState(); loadGoogleQuota(); polishCards()
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) { refreshState(); loadGoogleQuota() }
    }

    private fun setupEmptyStateButton() {
        val scroll = binding.root.getChildAt(1) as? ScrollView ?: return
        val content = scroll.getChildAt(0) as? LinearLayout ?: return
        val button = MaterialButton(this).apply {
            text = "ADD CLOUD ACCOUNT"; isAllCaps = false; textSize = 14f; setTextColor(Color.WHITE); background = gradient()
            setOnClickListener { binding.googleDriveConnect.performClick() }
        }
        content.addView(button, 2, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)).apply { topMargin = dp(10) }); emptyAddButton = button
    }

    private fun setupProviderButtons() {
        binding.googleDriveConnect.setOnClickListener { connectGoogleDrive() }
        binding.oneDriveConnect.setOnClickListener { selectProvider("OneDrive") }
        binding.dropboxConnect.setOnClickListener { selectProvider("Dropbox") }
        binding.megaConnect.setOnClickListener { selectProvider("MEGA") }
        binding.boxConnect.setOnClickListener { selectProvider("Box") }
        binding.webDavConnect.setOnClickListener { selectProvider("WebDAV") }
        listOf(binding.googleDriveConnect, binding.oneDriveConnect, binding.dropboxConnect, binding.megaConnect, binding.boxConnect, binding.webDavConnect).forEach { it.background = gradient(); it.setTextColor(Color.WHITE) }
    }

    private fun gradient() = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(Color.rgb(124, 61, 237), Color.rgb(38, 181, 235))).apply { cornerRadius = 24f * resources.displayMetrics.density }

    private fun connectGoogleDrive() {
        prefs.edit().putString("selected_cloud_provider", GoogleDriveAuthManager.PROVIDER).apply()
        val client = GoogleDriveAuthManager.signInClient(this)
        val connected = prefs.getStringSet("connected_cloud_providers", emptySet())?.contains(GoogleDriveAuthManager.PROVIDER) == true
        if (connected) client.signOut().addOnCompleteListener { clearGoogleAccountState(); googleSignInLauncher.launch(GoogleDriveAuthManager.signInClient(this).signInIntent) }
        else googleSignInLauncher.launch(client.signInIntent)
    }

    private fun clearGoogleAccountState() {
        prefs.edit().remove("google_drive_account_email").remove("google_drive_account_name").remove("google_drive_target_folder_id").remove("google_drive_target_folder_name").remove("google_quota_used").remove("google_quota_limit").apply()
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
        binding.webDavProgress.isIndeterminate = false; binding.webDavProgress.setProgressCompat(0, false)
        val used = prefs.getLong("google_quota_used", 0L); val limit = prefs.getLong("google_quota_limit", 0L)
        if (connected.contains(GoogleDriveAuthManager.PROVIDER) && limit > 0L) {
            binding.totalStorageText.text = "Google Drive • ${formatBytes(used)} used • ${formatBytes(limit - used)} free • ${formatBytes(limit)} total"
            binding.totalStorageProgress.setProgressCompat(((used.toDouble() / limit.toDouble()) * 100.0).coerceIn(0.0, 100.0).toInt(), false)
        } else binding.totalStorageText.text = if (connected.isEmpty()) "No cloud account connected" else "Connected accounts: ${connected.size} • Storage details loading"
        binding.totalStorageProgress.isIndeterminate = false
        binding.googleDriveConnect.text = if (connected.contains(GoogleDriveAuthManager.PROVIDER)) "CHANGE ACCOUNT" else "CONNECT"
        emptyAddButton?.visibility = if (connected.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateProvider(text: TextView, progress: com.google.android.material.progressindicator.LinearProgressIndicator, connected: Boolean, selected: Boolean, account: String = "") {
        text.text = when {
            connected && account.isNotBlank() -> "Connected • $account"
            connected -> "Connected • storage details unavailable"
            selected -> "Ready to connect"
            else -> "Not connected"
        }
        progress.isIndeterminate = false; progress.setProgressCompat(0, false)
    }

    private fun loadGoogleQuota() {
        val connected = prefs.getStringSet("connected_cloud_providers", emptySet())?.contains(GoogleDriveAuthManager.PROVIDER) == true
        if (!connected) return
        executor.execute {
            try {
                val (usage, limit, _) = DriveClient(this).quota()
                prefs.edit().putLong("google_quota_used", usage).putLong("google_quota_limit", limit).apply()
                val percent = if (limit > 0L) ((usage.toDouble() / limit.toDouble()) * 100.0).coerceIn(0.0, 100.0).toInt() else 0
                val quotaText = if (limit > 0L) "${formatBytes(usage)} used • ${formatBytes((limit - usage).coerceAtLeast(0L))} free • ${formatBytes(limit)} total" else "${formatBytes(usage)} used • Unlimited total"
                runOnUiThread {
                    if (!isFinishing) {
                        binding.googleDriveStorageText.text = quotaText
                        binding.googleDriveProgress.isIndeterminate = false
                        binding.googleDriveProgress.setProgressCompat(percent, true)
                        binding.totalStorageText.text = "Google Drive • $quotaText"
                        binding.totalStorageProgress.isIndeterminate = false
                        binding.totalStorageProgress.setProgressCompat(percent, true)
                    }
                }
            } catch (_: Exception) { runOnUiThread { if (!isFinishing) binding.googleDriveStorageText.text = "Connected • quota unavailable" } }
        }
    }

    private fun polishCards() {
        val light = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) != android.content.res.Configuration.UI_MODE_NIGHT_YES
        val surface = if (light) Color.WHITE else Color.rgb(15, 22, 36); val outline = if (light) Color.rgb(225, 228, 236) else Color.rgb(48, 61, 82)
        binding.root.setBackgroundColor(if (light) Color.rgb(247, 248, 252) else Color.rgb(7, 11, 20)); styleCards(binding.root, surface, outline)
        findText(binding.root, "Storage overview")?.text = "Ready to sync"
        val scroll = binding.root.getChildAt(1) as? ScrollView; scroll?.getChildAt(0)?.setPadding(dp(12), 0, dp(12), dp(16))
    }

    private fun findText(parent: ViewGroup, value: String): TextView? { for (i in 0 until parent.childCount) { val child = parent.getChildAt(i); if (child is TextView && child.text.toString() == value) return child; if (child is ViewGroup) findText(child, value)?.let { return it } }; return null }
    private fun styleCards(parent: ViewGroup, surface: Int, outline: Int) { for (i in 0 until parent.childCount) { val child = parent.getChildAt(i); if (child is MaterialCardView) { child.setCardBackgroundColor(surface); child.strokeColor = outline; child.strokeWidth = dp(1); child.cardElevation = dp(1).toFloat(); child.radius = 20f * resources.displayMetrics.density }; if (child is ViewGroup) styleCards(child, surface, outline) } }
    private fun formatBytes(value: Long): String { if (value < 1024L) return "$value B"; if (value < 1024L * 1024L) return String.format(Locale.getDefault(), "%.2f KB", value / 1024.0); if (value < 1024L * 1024L * 1024L) return String.format(Locale.getDefault(), "%.2f MB", value / (1024.0 * 1024.0)); if (value < 1024L * 1024L * 1024L * 1024L) return String.format(Locale.getDefault(), "%.2f GB", value / (1024.0 * 1024.0 * 1024.0)); return String.format(Locale.getDefault(), "%.2f TB", value / (1024.0 * 1024.0 * 1024.0 * 1024.0)) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    override fun onSupportNavigateUp(): Boolean { finish(); return true }
    override fun onDestroy() { executor.shutdownNow(); super.onDestroy() }
}
