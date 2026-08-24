package com.riyaz.rsscloudsync

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar

class HistoryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(resolveColor(com.google.android.material.R.attr.colorSurface)) }
        val toolbar = MaterialToolbar(this).apply { title = "Sync history"; setNavigationIcon(android.R.drawable.ic_menu_revert); setNavigationOnClickListener { finish() } }
        root.addView(toolbar, LinearLayout.LayoutParams(-1, dp(56)))
        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(10), dp(16), dp(28)) }
        val entries = SyncHistoryManager.get(this)
        if (entries.isEmpty()) {
            content.addView(TextView(this).apply { text = "No sync history yet."; textSize = 18f; setTypeface(typeface, Typeface.BOLD); setPadding(0, dp(30), 0, dp(8)) })
            content.addView(TextView(this).apply { text = "Completed and failed sync operations will appear here."; textSize = 13f })
        } else {
            entries.forEach { entry ->
                val status = if (entry.success) "✓ Success" else if (entry.bytesTransferred > 0) "⚠ Completed with warnings" else "✕ Failed"
                val text = buildString {
                    append(status).append('\n')
                    append("Files:       ").append(entry.filesProcessed).append('\n')
                    append("Uploaded:    ").append(entry.uploadedFiles).append('\n')
                    append("Downloaded:  ").append(entry.downloadedFiles).append('\n')
                    append("Video:       ").append(entry.videoFiles).append('\n')
                    append("Audio:       ").append(entry.audioFiles).append('\n')
                    append("Documents:   ").append(entry.documentFiles).append('\n')
                    append("Transferred: ").append(formatBytes(entry.bytesTransferred)).append('\n')
                    append("Duration:    ").append(formatDuration(entry.durationMs)).append('\n')
                    append("Method:      ").append(entry.direction.replace('_', ' ')).append('\n')
                    append("Result:      ").append(if (entry.success) "Success" else entry.message)
                }
                content.addView(TextView(this).apply { this.text = text; textSize = 13f; setPadding(dp(14), dp(14), dp(14), dp(14)); setTypeface(typeface, Typeface.NORMAL) }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
            }
        }
        scroll.addView(content); root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f)); setContentView(root)
    }
    private fun formatDuration(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return String.format(java.util.Locale.getDefault(), "%02d:%02d", s / 60, s % 60) }
    private fun formatBytes(bytes: Long): String { if (bytes < 1024) return "$bytes B"; var v = bytes.toDouble(); val u = arrayOf("KB", "MB", "GB", "TB"); var i = 0; while (v >= 1024 && i < u.lastIndex) { v /= 1024; i++ }; return String.format(java.util.Locale.getDefault(), "%.2f %s", v, u[i]) }
    private fun resolveColor(attr: Int): Int { val value = android.util.TypedValue(); theme.resolveAttribute(attr, value, true); return value.data }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
