package com.riyaz.rsscloudsync

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.riyaz.rsscloudsync.databinding.ActivitySyncPairsBinding

class SyncPairsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySyncPairsBinding
    private val prefs by lazy { getSharedPreferences("rss_cloud_sync", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySyncPairsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Sync Folders"
        binding.upgradeButton.isEnabled = true
        binding.upgradeButton.alpha = 1f
        binding.upgradeButton.setOnClickListener { startActivity(Intent(this, PremiumActivity::class.java)) }
        renderPairs()
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) renderPairs()
    }

    private fun renderPairs() {
        val pairs = SyncPairStore.migrateLegacyIfNeeded(prefs)
        val premium = SyncPairStore.isPremium(prefs)
        binding.planText.text = if (premium) "PREMIUM • Multiple folder pairs" else "FREE • 1 folder pair"
        binding.summaryText.text = if (premium) "All enabled pairs are included when you press Sync Now." else "Free includes one folder pair. Upgrade to Premium for more pairs."
        binding.upgradeButton.visibility = if (premium) android.view.View.GONE else android.view.View.VISIBLE
        binding.upgradeButton.isEnabled = true
        binding.upgradeButton.alpha = 1f
        binding.pairsContainer.removeAllViews()

        if (pairs.isEmpty()) {
            val card = MaterialCardView(this).apply {
                radius = 22.dp().toFloat()
                strokeWidth = 1.dp()
                strokeColor = 0xFFE1E4EC.toInt()
                setCardBackgroundColor(Color.WHITE)
                cardElevation = 0f
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 6.dp(); bottomMargin = 10.dp() }
            }
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(20.dp(), 22.dp(), 20.dp(), 22.dp())
            }
            box.addView(TextView(this).apply { text = "☁️  ↔  📁"; textSize = 32f; gravity = Gravity.CENTER; setTextColor(0xFF7C4DFF.toInt()) })
            box.addView(TextView(this).apply { text = "No folder pairs yet"; textSize = 18f; setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(0xFF252B3A.toInt()); gravity = Gravity.CENTER; setPadding(0, 8.dp(), 0, 0) })
            box.addView(TextView(this).apply { text = "Choose a cloud account, remote folder and device folder to create your first sync pair."; textSize = 12f; setTextColor(0xFF687080.toInt()); gravity = Gravity.CENTER; setPadding(0, 5.dp(), 0, 10.dp()) })
            box.addView(MaterialButton(this).apply { text = "＋  CREATE FOLDER PAIR"; isAllCaps = false; setOnClickListener { addPair() } })
            card.addView(box)
            binding.pairsContainer.addView(card)
        } else {
            pairs.forEach { addPairCard(it) }
        }
        binding.addPairButton.visibility = android.view.View.GONE
    }

    private fun addPairCard(pair: SyncPairStore.Pair) {
        val card = MaterialCardView(this).apply {
            radius = 18.dp().toFloat()
            strokeWidth = 1.dp()
            strokeColor = 0xFFE1E4EC.toInt()
            setCardBackgroundColor(Color.WHITE)
            cardElevation = 0f
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 10.dp() }
        }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16.dp(), 13.dp(), 16.dp(), 13.dp()) }
        box.addView(TextView(this).apply {
            text = if (pair.enabled) "●  ${pair.name}" else "○  ${pair.name}"
            textSize = 16f
            setTextColor(if (pair.enabled) 0xFF1F8E55.toInt() else 0xFF707784.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        box.addView(TextView(this).apply {
            text = when {
                pair.provider == "Google Drive" && pair.accountEmail.isNotBlank() -> "Google Drive • ${pair.accountEmail}"
                pair.provider.isNotBlank() -> pair.provider
                else -> "Cloud account not selected"
            }
            textSize = 12f; setTextColor(0xFF687080.toInt()); setPadding(0, 4.dp(), 0, 0)
        })
        val localName = localFolderName(pair.localFolderUri)
        val remote = pair.remoteFolderName.ifBlank { "Remote folder not selected" }
        val local = if (pair.selectedFiles.isNotEmpty()) "${pair.selectedFiles.size} individual file(s)" else localName
        box.addView(TextView(this).apply {
            text = "Remote folder\n$remote\n\nLocal folder\n$local"
            textSize = 12f; setTextColor(0xFF4B5260.toInt()); setPadding(0, 7.dp(), 0, 0)
        })
        box.addView(TextView(this).apply {
            text = "Sync method: ${pair.direction}\n${if (pair.enabled) "Enabled" else "Disabled"}"
            textSize = 12f; setTextColor(0xFF687080.toInt()); setPadding(0, 6.dp(), 0, 8.dp())
        })
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val edit = MaterialButton(this).apply {
            text = "EDIT"; isAllCaps = false; isEnabled = true; isClickable = true
            layoutParams = LinearLayout.LayoutParams(0, 42.dp(), 1f).apply { marginEnd = 5.dp() }
            setOnClickListener { editPair(pair.id) }
        }
        val delete = MaterialButton(this).apply {
            text = "DELETE"; isAllCaps = false; isEnabled = true; isClickable = true
            layoutParams = LinearLayout.LayoutParams(0, 42.dp(), 1f).apply { marginStart = 5.dp() }
            setOnClickListener {
                SyncPairStore.delete(prefs, pair.id)
                renderPairs()
                Toast.makeText(this@SyncPairsActivity, "Folder pair deleted", Toast.LENGTH_SHORT).show()
            }
        }
        actions.addView(edit); actions.addView(delete); box.addView(actions)
        card.addView(box)
        binding.pairsContainer.addView(card)
    }

    private fun localFolderName(uri: String): String {
        if (uri.isBlank()) return "Local folder not selected"
        return try { DocumentFile.fromTreeUri(this, Uri.parse(uri))?.name ?: uri.substringAfterLast('/').ifBlank { "Selected folder" } } catch (_: Exception) { "Selected folder" }
    }

    private fun addPair() {
        val pairs = SyncPairStore.all(prefs)
        if (!SyncPairStore.isPremium(prefs) && pairs.isNotEmpty()) {
            startActivity(Intent(this, PremiumActivity::class.java))
            return
        }
        prefs.edit().remove("folder_pair_name").remove("google_drive_target_folder_id").remove("google_drive_target_folder_name").remove("sync_folder_uri").remove("selected_local_files").putBoolean("folder_pair_enabled", true).apply()
        startActivity(Intent(this, SyncSetupActivity::class.java).putExtra("new_pair", true))
    }

    private fun editPair(id: String) {
        if (SyncPairStore.load(prefs, id)) startActivity(Intent(this, SyncSetupActivity::class.java).putExtra("pair_id", id))
    }

    private fun Int.dp() = (this * resources.displayMetrics.density).toInt()
    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
