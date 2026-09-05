package com.riyaz.rsscloudsync

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.util.Locale
import java.util.concurrent.Executors

class GenericCloudSyncActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("rss_cloud_sync", MODE_PRIVATE) }
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var provider: String
    private lateinit var localText: TextView
    private lateinit var cloudText: TextView
    private lateinit var statusText: TextView
    private lateinit var progressText: TextView
    private lateinit var directionSpinner: Spinner
    private lateinit var syncButton: MaterialButton
    private var localUri: Uri? = null
    private var cloudUri: Uri? = null
    private var activeEngine: SyncEngine? = null

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@registerForActivityResult
        try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (_: SecurityException) {}
        if (selectingCloud) {
            cloudUri = uri
            prefs.edit().putString("saf_cloud_uri_$provider", uri.toString()).apply()
            cloudText.text = "Cloud folder: ${prettyUri(uri)}"
        } else {
            localUri = uri
            prefs.edit().putString("saf_local_uri_$provider", uri.toString()).apply()
            localText.text = "Local folder: ${prettyUri(uri)}"
        }
    }
    private var selectingCloud = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        provider = intent.getStringExtra("provider") ?: "Cloud"
        localUri = prefs.getString("saf_local_uri_$provider", null)?.let(Uri::parse)
        cloudUri = prefs.getString("saf_cloud_uri_$provider", null)?.let(Uri::parse)
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(10), dp(18), dp(22)); setBackgroundColor(Color.rgb(247, 249, 253)) }
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
        val title = TextView(this).apply { text = "$provider Sync"; textSize = 24f; setTextColor(Color.rgb(25, 32, 44)); setTypeface(typeface, android.graphics.Typeface.BOLD); setPadding(0, dp(12), 0, dp(4)) }
        root.addView(title)
        root.addView(TextView(this).apply { text = "Premium test access • universal Android cloud folder sync"; textSize = 13f; setTextColor(Color.rgb(85, 99, 120)); setPadding(0, 0, 0, dp(18)) })
        localText = TextView(this).apply { text = localUri?.let { "Local folder: ${prettyUri(it)}" } ?: "Local folder: not selected"; textSize = 14f; setTextColor(Color.rgb(45, 55, 72)); setPadding(0, dp(8), 0, dp(8)) }
        root.addView(localText)
        root.addView(button("CHOOSE LOCAL FOLDER") { selectingCloud = false; folderPicker.launch(null) })
        cloudText = TextView(this).apply { text = cloudUri?.let { "Cloud folder: ${prettyUri(it)}" } ?: "Cloud folder: not selected"; textSize = 14f; setTextColor(Color.rgb(45, 55, 72)); setPadding(0, dp(16), 0, dp(8)) }
        root.addView(cloudText)
        root.addView(button("CHOOSE $provider FOLDER") { selectingCloud = true; folderPicker.launch(null) })
        root.addView(TextView(this).apply { text = "SYNC DIRECTION"; textSize = 11f; setTextColor(Color.rgb(90, 103, 123)); setPadding(0, dp(20), 0, dp(5)) })
        directionSpinner = Spinner(this).apply { adapter = ArrayAdapter(this@GenericCloudSyncActivity, android.R.layout.simple_spinner_item, arrayOf("Two-way Sync", "Upload only", "Upload mirror", "Upload then delete", "Download only", "Download mirror", "Download then delete")).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) } }
        root.addView(directionSpinner, LinearLayout.LayoutParams(-1, dp(48)))
        statusText = TextView(this).apply { text = "Ready"; textSize = 16f; setTextColor(Color.rgb(25, 32, 44)); setPadding(0, dp(22), 0, dp(6)) }
        progressText = TextView(this).apply { text = "Select both folders to start"; textSize = 13f; setTextColor(Color.rgb(90, 103, 123)); setPadding(0, 0, 0, dp(14)) }
        root.addView(statusText); root.addView(progressText)
        syncButton = button("SYNC NOW") { startSync() }
        root.addView(syncButton)
        root.addView(button("BACK") { finish() })
    }

    private fun button(label: String, action: () -> Unit) = MaterialButton(this).apply {
        text = label; isAllCaps = false; setTextColor(Color.WHITE); setBackgroundColor(Color.rgb(94, 72, 220)); cornerRadius = dp(20); setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(5) }
    }

    private fun startSync() {
        val local = localUri ?: run { Toast.makeText(this, "Choose a local folder first", Toast.LENGTH_SHORT).show(); return }
        val cloud = cloudUri ?: run { Toast.makeText(this, "Choose the $provider folder from the Android file picker", Toast.LENGTH_SHORT).show(); return }
        val direction = when (directionSpinner.selectedItemPosition) {
            1 -> SyncEngine.Direction.UPLOAD_ONLY
            2 -> SyncEngine.Direction.UPLOAD_MIRROR
            3 -> SyncEngine.Direction.UPLOAD_THEN_DELETE
            4 -> SyncEngine.Direction.DOWNLOAD_ONLY
            5 -> SyncEngine.Direction.DOWNLOAD_MIRROR
            6 -> SyncEngine.Direction.DOWNLOAD_THEN_DELETE
            else -> SyncEngine.Direction.TWO_WAY
        }
        syncButton.text = "CANCEL SYNC"
        statusText.text = "$provider sync in progress..."
        executor.execute {
            try {
                val engine = SyncEngine(contentResolver, this)
                activeEngine = engine
                val result = engine.sync(local, cloud, direction) { p -> runOnUiThread { progressText.text = "${p.filesProcessed}/${p.totalFiles} • ↑${p.uploadedFiles} ↓${p.downloadedFiles} • Failed: ${p.failedFiles} • ${formatBytes(p.bytesTransferred)}\n${p.currentPath}" } }
                runOnUiThread {
                    activeEngine = null; syncButton.text = "SYNC NOW"
                    statusText.text = if (result.failedFiles == 0 && !result.cancelled) "Sync completed" else if (result.cancelled) "Sync cancelled" else "Sync completed with warnings"
                    progressText.text = "Files: ${result.filesProcessed} • ↑${result.uploadedFiles} ↓${result.downloadedFiles} • Failed: ${result.failedFiles} • ${formatBytes(result.bytesTransferred)}"
                }
            } catch (e: Exception) {
                runOnUiThread { activeEngine = null; syncButton.text = "SYNC NOW"; statusText.text = "Sync failed"; progressText.text = e.message ?: "Cloud sync failed" }
            }
        }
    }

    private fun prettyUri(uri: Uri) = (uri.lastPathSegment ?: uri.toString()).substringAfterLast(':').replace("%20", " ").ifBlank { uri.toString() }
    private fun formatBytes(bytes: Long): String { if (bytes < 1024L) return "$bytes B"; var value = bytes.toDouble(); val units = arrayOf("KB", "MB", "GB", "TB"); var i = 0; while (value >= 1024.0 && i < units.lastIndex) { value /= 1024.0; i++ }; return String.format(Locale.getDefault(), "%.2f %s", value, units[i]) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    override fun onDestroy() { activeEngine?.cancel(); executor.shutdownNow(); super.onDestroy() }
}
