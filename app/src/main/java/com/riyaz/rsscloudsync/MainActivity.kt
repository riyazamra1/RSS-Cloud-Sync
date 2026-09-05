package com.riyaz.rsscloudsync

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.atomic.AtomicBoolean

/** Lightweight navigation-first UI with native Android styling, motion and transfer actions. */
class MainActivity : AppCompatActivity() {
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var title: TextView
    private val selectedFiles = mutableListOf<Uri>()
    private val cancelTransfer = AtomicBoolean(false)

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (!uris.isNullOrEmpty()) { selectedFiles.clear(); selectedFiles.addAll(uris); showExplorer() }
    }

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (_: SecurityException) { }
        getSharedPreferences("rss_cloud_sync", MODE_PRIVATE).edit().putString("external_storage_uri", uri.toString()).apply()
        Toast.makeText(this, "Folder added", Toast.LENGTH_SHORT).show()
        showExplorer()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildShell()
        showHome()
    }

    private fun buildShell() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor())
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(10))
        }
        title = TextView(this).apply {
            text = "RSS CLOUD SYNC"
            textSize = 21f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(textColor())
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        header.addView(title)
        header.addView(TextView(this).apply {
            text = "🔔"
            textSize = 20f
            setPadding(dp(10), dp(6), 0, dp(6))
            setOnClickListener { startActivity(Intent(this@MainActivity, NotificationsActivity::class.java)) }
        })

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), 0, dp(18), dp(18))
        }
        val scroll = ScrollView(this).apply {
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(content)
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(7), dp(6), dp(7), dp(7))
            setBackgroundColor(surfaceColor())
            elevation = dp(5).toFloat()
        }
        val items = listOf("⌂\nHome", "▣\nExplorer", "☁\nClouds", "◷\nActivity", "⚙\nSettings")
        items.forEachIndexed { index, label ->
            nav.addView(TextView(this).apply {
                text = label
                gravity = Gravity.CENTER
                textSize = 10f
                setTextColor(if (index == 0) accentColor() else secondaryColor())
                setPadding(0, dp(7), 0, dp(7))
                layoutParams = LinearLayout.LayoutParams(0, dp(55), 1f)
                setOnClickListener {
                    animatePress(this)
                    when (index) {
                        0 -> showHome()
                        1 -> showExplorer()
                        2 -> showClouds()
                        3 -> showActivity()
                        else -> showSettings()
                    }
                }
            })
        }
        root.addView(header)
        root.addView(scroll)
        root.addView(nav)
        setContentView(root)
    }

    private fun clear(name: String) {
        title.text = name
        content.removeAllViews()
    }

    private fun showHome() {
        clear("RSS CLOUD SYNC")
        addHero("Your files.\nSafe everywhere.", "Backup • Sync • Restore • Anywhere")
        val connected = getSharedPreferences("rss_cloud_sync", MODE_PRIVATE).getStringSet("connected_cloud_providers", emptySet()) ?: emptySet()
        val last = SyncHistoryManager.get(this).firstOrNull()
        addCard("SYNC STATUS", if (connected.isEmpty()) "Ready — connect a cloud to begin" else "Ready — ${connected.size} cloud account(s) connected")
        if (last != null) addMuted("Last activity • ${last.filesChanged} changed • ↑${last.uploadedFiles} ↓${last.downloadedFiles}")
        addAction("SYNC NOW", accent = true) { startActivity(Intent(this, SyncPairsActivity::class.java)) }
        addAction("OPEN FILE EXPLORER") { showExplorer() }
        addSection("Connected clouds")
        if (connected.isEmpty()) addMuted("No cloud account connected yet.") else connected.forEach { addCard(it, "Connected • ready for sync") }
        addSection("Recent activity")
        val history = SyncHistoryManager.get(this).take(3)
        if (history.isEmpty()) addMuted("No transfers yet.") else history.forEach { addMuted("${it.filesChanged} changed • ↑${it.uploadedFiles} ↓${it.downloadedFiles}") }
    }

    private fun showExplorer() {
        clear("FILE EXPLORER")
        addToolbarRow("Local storage", "Search • Sort")
        addMuted("Select files or a folder. Choose a cloud destination, then upload with live progress.")
        addAction("＋ SELECT FILES") { filePicker.launch(arrayOf("*/*")) }
        addAction("＋ ADD FOLDER") { folderPicker.launch(null) }
        val folder = getSharedPreferences("rss_cloud_sync", MODE_PRIVATE).getString("external_storage_uri", null)
        if (folder != null) addCard("LOCAL FOLDER", "Folder permission saved • ready to read files")
        addSection("Selected files (${selectedFiles.size})")
        if (selectedFiles.isEmpty()) {
            addMuted("Nothing selected")
        } else {
            selectedFiles.forEach { uri ->
                addFileRow(uri)
            }
            addSection("Destination")
            val provider = getSharedPreferences("rss_cloud_sync", MODE_PRIVATE).getString("selected_cloud_provider", null)
            addCard("CLOUD DESTINATION", provider ?: "No cloud selected")
            addAction("CHOOSE CLOUD") { showClouds() }
            addAction("UPLOAD TO GOOGLE DRIVE", accent = true) { uploadSelectedToGoogleDrive() }
            addAction("CLEAR SELECTION") { selectedFiles.clear(); showExplorer() }
        }
    }

    private fun addFileRow(uri: Uri) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(surfaceColor(), 16)
            setPadding(dp(13), dp(11), dp(13), dp(11))
            elevation = dp(1).toFloat()
        }
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        info.addView(TextView(this).apply { text = "📄  ${queryName(uri)}"; textSize = 14f; setTextColor(textColor()); setTypeface(typeface, Typeface.BOLD) })
        info.addView(TextView(this).apply { text = formatSize(querySize(uri)); textSize = 11f; setTextColor(secondaryColor()); setPadding(0, dp(3), 0, 0) })
        row.addView(info)
        row.addView(TextView(this).apply {
            text = "✕"
            textSize = 16f
            setTextColor(secondaryColor())
            setPadding(dp(10), dp(8), 0, dp(8))
            setOnClickListener { selectedFiles.remove(uri); showExplorer() }
        })
        content.addView(row, margins(0, 0, 0, 8))
        animateIn(row, content.childCount)
    }

    private fun showClouds() {
        clear("CLOUDS")
        addMuted("Manage storage accounts and choose a destination for transfers.")
        listOf("Google Drive", "OneDrive", "Dropbox", "MEGA", "Box", "WebDAV").forEach { provider ->
            val selected = getSharedPreferences("rss_cloud_sync", MODE_PRIVATE).getString("selected_cloud_provider", null) == provider
            addCard(provider, if (selected) "Selected destination" else "Connection • storage • destination")
            addAction(if (selected) "SELECTED" else "SELECT $provider", accent = selected) {
                getSharedPreferences("rss_cloud_sync", MODE_PRIVATE).edit().putString("selected_cloud_provider", provider).apply()
                if (provider == "Google Drive") {
                    startActivity(Intent(this, CloudAccountsActivity::class.java))
                } else {
                    Toast.makeText(this, "$provider selected. Connect the account to enable transfers.", Toast.LENGTH_SHORT).show()
                    showExplorer()
                }
            }
        }
    }

    private fun showActivity() {
        clear("ACTIVITY")
        addMuted("Uploads, downloads and synchronization history")
        val history = SyncHistoryManager.get(this)
        if (history.isEmpty()) addMuted("No activity yet.") else history.forEach { addCard("SYNC", "${it.filesChanged} changed • uploaded ${it.uploadedFiles} • downloaded ${it.downloadedFiles}") }
        addAction("FULL HISTORY") { startActivity(Intent(this, HistoryActivity::class.java)) }
    }

    private fun showSettings() {
        clear("SETTINGS")
        addCard("Automatic sync", "Sync pairs, schedules and behavior")
        addAction("SYNC SETTINGS") { startActivity(Intent(this, SettingsActivity::class.java)) }
        addCard("Appearance", "Light • System • Dark")
        addAction("APPEARANCE") { startActivity(Intent(this, SettingsActivity::class.java)) }
        addCard("Storage", "Local and external folder access")
        addAction("STORAGE") { startActivity(Intent(this, ExternalStorageActivity::class.java)) }
        addCard("Premium", "Additional cloud and automation capabilities")
        addAction("VIEW PREMIUM", accent = true) { startActivity(Intent(this, PremiumActivity::class.java)) }
        addAction("ABOUT RSS CLOUD SYNC") { startActivity(Intent(this, AboutActivity::class.java)) }
    }

    private fun uploadSelectedToGoogleDrive() {
        if (selectedFiles.isEmpty()) { Toast.makeText(this, "Select at least one file", Toast.LENGTH_SHORT).show(); return }
        try {
            GoogleDriveAuthManager.currentAccount(this) ?: throw IllegalStateException("Google Drive account is not connected")
        } catch (e: Exception) {
            Toast.makeText(this, "Connect Google Drive first", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, CloudAccountsActivity::class.java))
            return
        }

        cancelTransfer.set(false)
        val files = selectedFiles.toList()
        clear("UPLOAD")
        addMuted("Google Drive • ${files.size} file(s) queued")
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progress = 0 }
        content.addView(progress, margins(0, dp(12), 0, dp(8)))
        val status = TextView(this).apply { text = "Preparing…"; textSize = 13f; setTextColor(secondaryColor()) }
        content.addView(status)
        addAction("CANCEL UPLOAD") { cancelTransfer.set(true); Toast.makeText(this, "Cancelling…", Toast.LENGTH_SHORT).show() }

        Thread {
            var success = 0
            var failed = 0
            files.forEachIndexed { index, uri ->
                if (cancelTransfer.get()) return@forEachIndexed
                try {
                    val name = queryName(uri)
                    val mime = contentResolver.getType(uri) ?: "application/octet-stream"
                    val size = querySize(uri).coerceAtLeast(0L)
                    runOnUiThread { status.text = "Uploading $name • ${index + 1}/${files.size}" }
                    DriveClient(this).upload(uri, DriveClient.ROOT_ID, name, mime) { bytes ->
                        val pct = if (size > 0) ((bytes * 100L) / size).coerceIn(0L, 100L).toInt() else 0
                        runOnUiThread { progress.progress = ((index * 100) + pct) / files.size }
                    }
                    success++
                } catch (_: Exception) {
                    failed++
                }
            }
            runOnUiThread {
                if (cancelTransfer.get()) {
                    status.text = "Upload cancelled • $success completed"
                } else {
                    progress.progress = 100
                    status.text = "Upload complete • $success succeeded • $failed failed"
                    if (success > 0) selectedFiles.removeAll(files.take(success).toSet())
                }
                addAction("BACK TO EXPLORER") { showExplorer() }
                addAction("OPEN ACTIVITY") { showActivity() }
            }
        }.start()
    }

    private fun addHero(head: String, sub: String) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(25), dp(20), dp(25))
            background = gradient()
            elevation = dp(2).toFloat()
        }
        box.addView(TextView(this).apply { text = head; textSize = 28f; setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD) })
        box.addView(TextView(this).apply { text = sub; textSize = 13f; setTextColor(0xFFD7E1EF.toInt()); setPadding(0, dp(9), 0, 0) })
        content.addView(box, margins(0, 0, 0, 15))
        animateIn(box, 0)
    }

    private fun addToolbarRow(left: String, right: String) {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(TextView(this).apply { text = left; textSize = 18f; setTypeface(typeface, Typeface.BOLD); setTextColor(textColor()); layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
        row.addView(TextView(this).apply { text = right; textSize = 11f; setTextColor(secondaryColor()) })
        content.addView(row, margins(0, 4, 0, 8))
    }

    private fun addSection(text: String) {
        content.addView(TextView(this).apply { this.text = text; textSize = 17f; setTypeface(typeface, Typeface.BOLD); setTextColor(textColor()); setPadding(0, dp(15), 0, dp(8)) })
    }

    private fun addMuted(text: String) {
        content.addView(TextView(this).apply { this.text = text; textSize = 13f; setTextColor(secondaryColor()); setPadding(0, dp(5), 0, dp(8)) })
    }

    private fun addCard(head: String, body: String) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = rounded(surfaceColor(), 18)
            elevation = dp(1).toFloat()
        }
        box.addView(TextView(this).apply { text = head; textSize = 15f; setTypeface(typeface, Typeface.BOLD); setTextColor(textColor()) })
        box.addView(TextView(this).apply { text = body; textSize = 13f; setTextColor(secondaryColor()); setPadding(0, dp(5), 0, 0) })
        box.setOnClickListener { animatePress(box) }
        content.addView(box, margins(0, 0, 0, 9))
        animateIn(box, content.childCount)
    }

    private fun addAction(text: String, accent: Boolean = false, click: () -> Unit) {
        val button = Button(this).apply {
            this.text = text
            isAllCaps = false
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (accent) Color.WHITE else textColor())
            background = if (accent) gradient() else rounded(surfaceColor(), 14)
            minHeight = dp(46)
            stateListAnimator = null
            setOnClickListener { animatePress(this); click() }
        }
        content.addView(button, margins(0, 0, 0, 8))
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), if (isDark()) 0xFF26344A.toInt() else 0xFFE4E8EF.toInt())
    }

    private fun gradient() = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF172033.toInt(), 0xFF315C88.toInt())).apply { cornerRadius = dp(20).toFloat() }

    private fun animateIn(view: View, index: Int) {
        view.alpha = 0f
        view.translationY = dp(8).toFloat()
        view.animate().alpha(1f).translationY(0f).setDuration(220L + (index.coerceAtMost(4) * 35L)).start()
    }

    private fun animatePress(view: View) {
        view.animate().scaleX(0.97f).scaleY(0.97f).setDuration(70).withEndAction { view.animate().scaleX(1f).scaleY(1f).setDuration(100).start() }.start()
    }

    private fun queryName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) return it.getString(0) }
        return uri.lastPathSegment ?: "File"
    }

    private fun querySize(uri: Uri): Long = contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else -1L } ?: -1L

    private fun formatSize(value: Long): String {
        if (value < 0) return "Size unavailable"
        if (value < 1024) return "$value B"
        if (value < 1024 * 1024) return "%.1f KB".format(value / 1024.0)
        if (value < 1024L * 1024L * 1024L) return "%.1f MB".format(value / (1024.0 * 1024.0))
        return "%.2f GB".format(value / (1024.0 * 1024.0 * 1024.0))
    }

    private fun isDark() = (resources.configuration.uiMode and 0x30) == 0x20
    private fun bgColor() = if (isDark()) 0xFF080D16.toInt() else 0xFFF5F7FB.toInt()
    private fun surfaceColor() = if (isDark()) 0xFF121B29.toInt() else Color.WHITE
    private fun textColor() = if (isDark()) Color.WHITE else 0xFF182132.toInt()
    private fun secondaryColor() = if (isDark()) 0xFF9AA9BE.toInt() else 0xFF667085.toInt()
    private fun accentColor() = 0xFF4C8DFF.toInt()
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun margins(l: Int, t: Int, r: Int, b: Int) = LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(l), dp(t), dp(r), dp(b)) }
}
