package com.riyaz.rsscloudsync

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import java.util.Locale
import java.util.concurrent.Executors

class GoogleDriveFolderPickerActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var list: LinearLayout
    private lateinit var title: TextView
    private var currentId = DriveClient.ROOT_ID
    private val parents = ArrayDeque<Pair<String, String>>()

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(), 0, 16.dp(), 16.dp())
        }
        val toolbar = MaterialToolbar(this).apply {
            title = "Google Drive"
            setNavigationIcon(android.R.drawable.ic_menu_revert)
            setNavigationOnClickListener { goBack() }
        }
        root.addView(toolbar, LinearLayout.LayoutParams(-1, 58.dp()))
        title = TextView(this).apply {
            textSize = 13f
            setPadding(4.dp(), 10.dp(), 4.dp(), 8.dp())
            maxLines = 2
        }
        root.addView(title)
        val select = MaterialButton(this).apply {
            text = "USE THIS FOLDER"
            setOnClickListener { choose() }
        }
        root.addView(select, LinearLayout.LayoutParams(-1, 50.dp()))
        val scroll = ScrollView(this).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = true
            isSmoothScrollingEnabled = true
            overScrollMode = ScrollView.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 6.dp(), 0, 24.dp())
        }
        scroll.addView(list, LinearLayout.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        load(currentId, "My Drive")
    }

    private fun load(id: String, label: String) {
        currentId = id
        title.text = "Location: $label"
        list.removeAllViews()
        list.addView(ProgressBar(this), LinearLayout.LayoutParams(-1, 48.dp()))
        executor.execute {
            try {
                val entries = DriveClient(this).listChildren(id)
                val folders = entries.filter { it.mimeType == DriveClient.FOLDER_MIME }.sortedBy { it.name.lowercase() }
                val files = entries.filter { it.mimeType != DriveClient.FOLDER_MIME }.sortedBy { it.name.lowercase() }
                runOnUiThread {
                    list.removeAllViews()
                    if (folders.isEmpty() && files.isEmpty()) {
                        list.addView(TextView(this).apply {
                            text = "This folder is empty"
                            textSize = 14f
                            gravity = Gravity.CENTER
                            setPadding(12.dp(), 32.dp(), 12.dp(), 32.dp())
                        })
                        return@runOnUiThread
                    }
                    if (folders.isNotEmpty()) addSection("FOLDERS")
                    folders.forEach { folder ->
                        val button = MaterialButton(this).apply {
                            text = "📁  ${folder.name}"
                            gravity = Gravity.START or Gravity.CENTER_VERTICAL
                            setAllCaps(false)
                            minHeight = 50.dp()
                            setOnClickListener {
                                parents.addLast(currentId to title.text.toString().removePrefix("Location: "))
                                load(folder.id, folder.name)
                            }
                        }
                        list.addView(button, LinearLayout.LayoutParams(-1, 50.dp()).apply { topMargin = 5.dp() })
                    }
                    if (files.isNotEmpty()) addSection("FILES")
                    files.forEach { file ->
                        list.addView(TextView(this).apply {
                            text = "${file.name}\n${file.mimeType} • ${formatBytes(file.size)}"
                            textSize = 13f
                            setPadding(14.dp(), 10.dp(), 14.dp(), 10.dp())
                            gravity = Gravity.CENTER_VERTICAL
                            alpha = 0.78f
                        }, LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 2.dp() })
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    list.removeAllViews()
                    list.addView(TextView(this).apply {
                        text = "Unable to load this Drive folder\n\n${e.message ?: "Unknown Google Drive error"}"
                        textSize = 14f
                        setPadding(12.dp(), 24.dp(), 12.dp(), 24.dp())
                    })
                    Toast.makeText(this, e.message ?: "Unable to load Google Drive", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun addSection(text: String) {
        list.addView(TextView(this).apply {
            this.text = text
            textSize = 11f
            setPadding(8.dp(), 12.dp(), 8.dp(), 5.dp())
            alpha = 0.65f
        })
    }

    private fun goBack() {
        if (parents.isEmpty()) finish() else {
            val (id, label) = parents.removeLast()
            load(id, label)
        }
    }

    private fun choose() {
        setResult(RESULT_OK, intent.putExtra("folder_id", currentId).putExtra("folder_name", title.text.toString().removePrefix("Location: ")))
        finish()
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        var value = bytes.toDouble()
        val units = arrayOf("KB", "MB", "GB", "TB")
        var index = 0
        while (value >= 1024.0 && index < units.lastIndex) { value /= 1024.0; index++ }
        return String.format(Locale.getDefault(), "%.1f %s", value, units[index])
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun Int.dp() = (this * resources.displayMetrics.density).toInt()
}
