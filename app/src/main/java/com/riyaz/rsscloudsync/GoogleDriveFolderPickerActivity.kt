package com.riyaz.rsscloudsync

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import java.util.concurrent.Executors

class GoogleDriveFolderPickerActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var list: LinearLayout
    private lateinit var title: TextView
    private var currentId = DriveClient.ROOT_ID
    private val parents = ArrayDeque<Pair<String, String>>()

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18, 0, 18, 18) }
        val toolbar = MaterialToolbar(this).apply { title = "Google Drive folder"; setNavigationIcon(android.R.drawable.ic_menu_revert); setNavigationOnClickListener { goBack() } }
        root.addView(toolbar, LinearLayout.LayoutParams(-1, 58.dp()))
        title = TextView(this).apply { textSize = 13f; setPadding(4, 12, 4, 8) }
        root.addView(title)
        val select = MaterialButton(this).apply { text = "USE THIS FOLDER"; setOnClickListener { choose() } }
        root.addView(select, LinearLayout.LayoutParams(-1, 50.dp()))
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        load(currentId, "My Drive")
    }

    private fun load(id: String, label: String) {
        currentId = id; title.text = label
        list.removeAllViews()
        val spinner = ProgressBar(this)
        list.addView(spinner, LinearLayout.LayoutParams(-1, 48.dp()))
        executor.execute {
            try {
                val folders = DriveClient(this).listChildren(id).filter { it.mimeType == DriveClient.FOLDER_MIME }
                runOnUiThread {
                    list.removeAllViews()
                    if (folders.isEmpty()) list.addView(TextView(this).apply { text = "No folders here"; textSize = 14f; setPadding(12, 20, 12, 20) })
                    folders.forEach { folder ->
                        val button = MaterialButton(this).apply { text = "📁  ${folder.name}"; gravity = android.view.Gravity.START; setOnClickListener { parents.addLast(currentId to title.text.toString()); load(folder.id, folder.name) } }
                        list.addView(button, LinearLayout.LayoutParams(-1, 50.dp()).apply { topMargin = 6.dp() })
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { list.removeAllViews(); Toast.makeText(this, e.message ?: "Unable to load Google Drive folders", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun goBack() {
        if (parents.isEmpty()) { finish(); return }
        val (id, label) = parents.removeLast(); load(id, label)
    }

    private fun choose() {
        setResult(RESULT_OK, intent.putExtra("folder_id", currentId).putExtra("folder_name", title.text.toString()))
        finish()
    }

    override fun onDestroy() { executor.shutdownNow(); super.onDestroy() }
    private fun Int.dp() = (this * resources.displayMetrics.density).toInt()
}
