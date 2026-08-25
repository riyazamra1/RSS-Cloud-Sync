package com.riyaz.rsscloudsync

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
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
        binding.addPairButton.setOnClickListener { addPair() }
        renderPairs()
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) renderPairs()
    }

    private fun renderPairs() {
        val pairs = SyncPairStore.migrateLegacyIfNeeded(prefs)
        val premium = SyncPairStore.isPremium(prefs)
        binding.planText.text = if (premium) "PREMIUM • Unlimited folder pairs" else "FREE • 1 folder pair"
        binding.summaryText.text = if (premium) {
            "All enabled pairs are included when you press Sync Now."
        } else {
            "Free includes one enabled folder pair. Upgrade to Premium for multiple pairs."
        }
        binding.pairsContainer.removeAllViews()
        if (pairs.isEmpty()) {
            val empty = TextView(this).apply {
                text = "No folder pairs yet.\n\nCreate your first pair to start syncing."
                textSize = 15f
                setTextColor(Color.DKGRAY)
                gravity = Gravity.CENTER
                setPadding(16, 80, 16, 80)
            }
            binding.pairsContainer.addView(empty)
        } else {
            pairs.forEach { pair -> addPairCard(pair, premium) }
        }
        binding.addPairButton.isEnabled = premium || pairs.isEmpty()
        binding.addPairButton.text = if (binding.addPairButton.isEnabled) "＋  ADD FOLDER PAIR" else "★  UPGRADE FOR MORE PAIRS"
    }

    private fun addPairCard(pair: SyncPairStore.Pair, premium: Boolean) {
        val card = MaterialCardView(this).apply {
            radius = 18f * resources.displayMetrics.density
            strokeWidth = (1 * resources.displayMetrics.density).toInt()
            strokeColor = 0xFFE1E4EC.toInt()
            setCardBackgroundColor(Color.WHITE)
            cardElevation = 0f
            layoutParams = LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 12.dp() }
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp(), 15.dp(), 18.dp(), 15.dp())
        }
        val title = TextView(this).apply {
            text = if (pair.enabled) "●  ${pair.name}" else "○  ${pair.name}"
            textSize = 17f
            setTextColor(if (pair.enabled) 0xFF1F8E55.toInt() else 0xFF707784.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val account = TextView(this).apply {
            text = when {
                pair.provider == "Google Drive" && pair.accountEmail.isNotBlank() -> "Google Drive • ${pair.accountEmail}"
                pair.provider.isNotBlank() -> pair.provider
                else -> "Cloud account not selected"
            }
            textSize = 12f
            setTextColor(0xFF687080.toInt())
            setPadding(0, 5.dp(), 0, 0)
        }
        val paths = TextView(this).apply {
            text = "Cloud: ${pair.remoteFolderName.ifBlank { "Not selected" }}\nDevice: ${if (pair.selectedFiles.isNotEmpty()) "${pair.selectedFiles.size} individual files" else pair.localFolderUri.ifBlank { "Not selected" }}"
            textSize = 12f
            setTextColor(0xFF4B5260.toInt())
            setPadding(0, 8.dp(), 0, 0)
        }
        val method = TextView(this).apply {
            text = "${pair.direction}  •  ${if (pair.enabled) "Enabled" else "Disabled"}"
            textSize = 12f
            setTextColor(0xFF687080.toInt())
            setPadding(0, 5.dp(), 0, 10.dp())
        }
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
        val edit = MaterialButton(this).apply { text = "EDIT"; setOnClickListener { editPair(pair.id) } }
        val remove = MaterialButton(this).apply { text = "DELETE"; setOnClickListener { deletePair(pair.id) } }
        actions.addView(edit)
        actions.addView(remove)
        box.addView(title); box.addView(account); box.addView(paths); box.addView(method); box.addView(actions)
        card.addView(box)
        binding.pairsContainer.addView(card)
    }

    private fun addPair() {
        val pairs = SyncPairStore.all(prefs)
        if (!SyncPairStore.isPremium(prefs) && pairs.isNotEmpty()) {
            startActivity(Intent(this, PremiumActivity::class.java))
            return
        }
        prefs.edit().remove("folder_pair_name").remove("google_drive_target_folder_id").remove("google_drive_target_folder_name").remove("sync_folder_uri").remove("selected_local_files").putBoolean("folder_pair_enabled", true).apply()
        startActivity(Intent(this, SyncSetupActivity::class.java))
    }

    private fun editPair(id: String) {
        if (SyncPairStore.load(prefs, id)) startActivity(Intent(this, SyncSetupActivity::class.java).putExtra("pair_id", id))
    }

    private fun deletePair(id: String) {
        SyncPairStore.delete(prefs, id)
        renderPairs()
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
