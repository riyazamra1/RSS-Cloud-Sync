package com.riyaz.rsscloudsync

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/** New navigation-first UI: Home, File Explorer, Clouds, Activity, Settings. */
class MainActivity : AppCompatActivity() {
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var title: TextView
    private val selectedFiles = mutableListOf<Uri>()

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (!uris.isNullOrEmpty()) { selectedFiles.clear(); selectedFiles.addAll(uris); showExplorer() }
    }
    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@registerForActivityResult
        try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (_: SecurityException) { }
        getSharedPreferences("rss_cloud_sync", MODE_PRIVATE).edit().putString("external_storage_uri", uri.toString()).apply()
        Toast.makeText(this, "Folder added", Toast.LENGTH_SHORT).show(); showExplorer()
    }

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); buildShell(); showHome() }

    private fun buildShell() {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFFF7F8FC.toInt()) }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(20), dp(18), dp(20), dp(12)) }
        title = TextView(this).apply { text = "RSS CLOUD SYNC"; textSize = 22f; setTypeface(typeface, Typeface.BOLD); layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        header.addView(title); header.addView(TextView(this).apply { text = "🔔"; textSize = 20f; setOnClickListener { startActivity(Intent(this@MainActivity, NotificationsActivity::class.java)) } })
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), 0, dp(20), dp(12)) }
        val scroll = ScrollView(this).apply { addView(content); layoutParams = LinearLayout.LayoutParams(-1, 0, 1f) }
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(dp(6), dp(6), dp(6), dp(6)); setBackgroundColor(-1) }
        listOf("⌂\nHome", "▣\nExplorer", "☁\nClouds", "◷\nActivity", "⚙\nSettings").forEachIndexed { index, label -> nav.addView(TextView(this).apply { text = label; gravity = Gravity.CENTER; textSize = 10f; setPadding(0, dp(6), 0, dp(6)); layoutParams = LinearLayout.LayoutParams(0, dp(56), 1f); setOnClickListener { when(index) { 0 -> showHome(); 1 -> showExplorer(); 2 -> showClouds(); 3 -> showActivity(); else -> showSettings() } } }) }
        root.addView(header); root.addView(scroll); root.addView(nav); setContentView(root)
    }

    private fun clear(name: String) { title.text = name; content.removeAllViews() }
    private fun showHome() {
        clear("RSS CLOUD SYNC"); addHero("Your files.\nSafe everywhere.", "Backup • Sync • Restore • Anywhere")
        val connected = getSharedPreferences("rss_cloud_sync", MODE_PRIVATE).getStringSet("connected_cloud_providers", emptySet()) ?: emptySet()
        addCard("SYNC STATUS", if (connected.isEmpty()) "Ready — connect a cloud to begin" else "Ready — ${connected.size} cloud account(s) connected")
        addAction("SYNC NOW") { startActivity(Intent(this, SyncPairsActivity::class.java)) }; addAction("OPEN FILE EXPLORER") { showExplorer() }
        addSection("Recent activity"); val history = SyncHistoryManager.get(this).take(3)
        if (history.isEmpty()) addMuted("No transfers yet.") else history.forEach { addMuted("${it.filesChanged} changed • ↑${it.uploadedFiles} ↓${it.downloadedFiles}") }
    }
    private fun showExplorer() {
        clear("FILE EXPLORER"); addMuted("Choose local files or a folder. Then select a cloud destination.")
        addAction("＋ SELECT FILES") { filePicker.launch(arrayOf("*/*")) }; addAction("＋ ADD FOLDER") { folderPicker.launch(null) }
        addSection("Selected files")
        if (selectedFiles.isEmpty()) addMuted("Nothing selected") else selectedFiles.forEach { uri ->
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10), dp(8), dp(10), dp(8)) }
            row.addView(TextView(this).apply { text = "📄  ${queryName(uri)}"; textSize = 14f; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
            row.addView(TextView(this).apply { text = "✕"; textSize = 16f; setOnClickListener { selectedFiles.remove(uri); showExplorer() } }); content.addView(row)
        }
        addSection("Destination"); addAction("CHOOSE CLOUD") { showClouds() }; addCard("TRANSFER", "Selected files are ready for the cloud transfer workflow. Progress and cancellation will be shown here.")
    }
    private fun showClouds() {
        clear("CLOUDS"); addMuted("One place for all storage accounts and destinations.")
        listOf("Google Drive", "OneDrive", "Dropbox", "MEGA", "Box", "WebDAV").forEach { provider ->
            addCard(provider, "Connection • storage • destination"); addAction("MANAGE $provider") { getSharedPreferences("rss_cloud_sync", MODE_PRIVATE).edit().putString("selected_cloud_provider", provider).apply(); startActivity(Intent(this, CloudAccountsActivity::class.java)) }
        }
    }
    private fun showActivity() {
        clear("ACTIVITY"); addMuted("Uploads, downloads and synchronization history"); val history = SyncHistoryManager.get(this)
        if (history.isEmpty()) addMuted("No activity yet.") else history.forEach { addCard("SYNC", "${it.filesChanged} changed • uploaded ${it.uploadedFiles} • downloaded ${it.downloadedFiles}") }
        addAction("FULL HISTORY") { startActivity(Intent(this, HistoryActivity::class.java)) }
    }
    private fun showSettings() {
        clear("SETTINGS"); addCard("Automatic sync", "Sync pairs, schedules and behavior"); addAction("SYNC SETTINGS") { startActivity(Intent(this, SettingsActivity::class.java)) }
        addCard("Appearance", "Light • System • Dark"); addAction("APPEARANCE") { startActivity(Intent(this, SettingsActivity::class.java)) }
        addCard("Storage", "Local and external folder access"); addAction("STORAGE") { startActivity(Intent(this, ExternalStorageActivity::class.java)) }
        addCard("Premium", "Additional cloud and automation capabilities"); addAction("VIEW PREMIUM") { startActivity(Intent(this, PremiumActivity::class.java)) }; addAction("ABOUT RSS CLOUD SYNC") { startActivity(Intent(this, AboutActivity::class.java)) }
    }
    private fun addHero(head: String, sub: String) { val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(24), dp(20), dp(24)); setBackgroundColor(0xFF111827.toInt()) }; box.addView(TextView(this).apply { text = head; textSize = 28f; setTextColor(-1); setTypeface(typeface, Typeface.BOLD) }); box.addView(TextView(this).apply { text = sub; textSize = 13f; setTextColor(0xFFD1D5DB.toInt()); setPadding(0, dp(9), 0, 0) }); content.addView(box, margins(0,0,0,16)) }
    private fun addSection(text: String) { content.addView(TextView(this).apply { this.text = text; textSize = 17f; setTypeface(typeface, Typeface.BOLD); setPadding(0,dp(16),0,dp(8)) }) }
    private fun addMuted(text: String) { content.addView(TextView(this).apply { this.text = text; textSize = 13f; setTextColor(0xFF5F6675.toInt()); setPadding(0,dp(5),0,dp(7)) }) }
    private fun addCard(head: String, body: String) { val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16),dp(14),dp(16),dp(14)); setBackgroundColor(-1) }; box.addView(TextView(this).apply { text=head; textSize=15f; setTypeface(typeface,Typeface.BOLD) }); box.addView(TextView(this).apply { text=body; textSize=13f; setTextColor(0xFF5F6675.toInt()); setPadding(0,dp(5),0,0) }); content.addView(box,margins(0,0,0,9)) }
    private fun addAction(text: String, click: () -> Unit) { content.addView(Button(this).apply { this.text=text; isAllCaps=false; setOnClickListener { click() } },margins(0,0,0,8)) }
    private fun margins(l:Int,t:Int,r:Int,b:Int)=LinearLayout.LayoutParams(-1,-2).apply{setMargins(dp(l),dp(t),dp(r),dp(b))}
    private fun queryName(uri: Uri): String { contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())return it.getString(0)}; return uri.lastPathSegment ?: "File" }
    private fun dp(v:Int)= (v*resources.displayMetrics.density).toInt()
}
