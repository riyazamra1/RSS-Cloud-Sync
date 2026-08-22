package com.riyaz.rsscloudsync

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import com.google.android.material.button.MaterialButton
import com.riyaz.rsscloudsync.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val prefs by lazy { getSharedPreferences("appearance", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupAppearanceButtons()
        setupGradientButtons()
        setupNavigation()
        setupDrawer()
        applyAppearance()
        binding.gradientProgress.setProgress(72f, false)
    }

    private fun setupAppearanceButtons() {
        binding.lightButton.setOnClickListener { setMode("light", AppCompatDelegate.MODE_NIGHT_NO) }
        binding.systemButton.setOnClickListener { setMode("system", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) }
        binding.darkButton.setOnClickListener { setMode("dark", AppCompatDelegate.MODE_NIGHT_YES) }
    }

    private fun setMode(mode: String, nightMode: Int) {
        prefs.edit().putString("mode", mode).apply()
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    private fun applyAppearance() {
        val mode = prefs.getString("mode", "system")
        val dark = when (mode) {
            "dark" -> true
            "light" -> false
            else -> (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        binding.lightButton.isSelected = mode == "light"
        binding.systemButton.isSelected = mode == "system"
        binding.darkButton.isSelected = mode == "dark"
        listOf(binding.lightButton, binding.systemButton, binding.darkButton).forEach { button ->
            button.background = if (button.isSelected) gradient(intArrayOf(Color.rgb(119, 82, 255), Color.rgb(236, 78, 177)), 50f) else solid(Color.TRANSPARENT, 50f)
            button.setTextColor(if (button.isSelected) Color.WHITE else if (dark) Color.rgb(220, 222, 235) else Color.rgb(30, 36, 55))
        }
        val bg = if (dark) Color.rgb(15, 17, 27) else Color.rgb(247, 247, 252)
        val card = if (dark) Color.rgb(28, 31, 43) else Color.WHITE
        val text = if (dark) Color.rgb(242, 243, 248) else Color.rgb(21, 23, 42)
        val secondary = if (dark) Color.rgb(166, 171, 188) else Color.rgb(115, 120, 138)
        val stroke = if (dark) Color.rgb(55, 59, 74) else Color.rgb(226, 227, 236)
        binding.root.setBackgroundColor(bg)
        binding.mainScrollView.setBackgroundColor(bg)
        binding.navigationView.setBackgroundColor(card)
        binding.toolbar.setTitleTextColor(text)
        binding.syncStatusText.setTextColor(text)
        binding.syncSubtitle.setTextColor(secondary)
        binding.lastSyncText.setTextColor(secondary)
        binding.cloudStorageSubtitle.setTextColor(secondary)
        binding.googleDriveTitle.setTextColor(text)
        binding.googleDriveStatus.setTextColor(secondary)
        listOf(binding.syncStatusCard, binding.googleDriveCard, binding.oneDriveCard, binding.dropboxCard, binding.foldersCard, binding.syncSetupCard).forEach {
            it.setCardBackgroundColor(card)
            it.strokeColor = stroke
            it.cardElevation = dp(3f)
        }
        applyTextColors(binding.contentLayout, text, secondary)
    }

    private fun applyTextColors(parent: ViewGroup, text: Int, secondary: Int) {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child is TextView && child.id != R.id.lightButton && child.id != R.id.systemButton && child.id != R.id.darkButton) {
                val size = child.textSize / resources.displayMetrics.scaledDensity
                child.setTextColor(if (size <= 11f) secondary else text)
            }
            if (child is ViewGroup) applyTextColors(child, text, secondary)
        }
    }

    private fun setupGradientButtons() {
        applyGradient(binding.syncNowButton)
        applyGradient(binding.googleDriveConnectButton)
        applyGradient(binding.oneDriveConnectButton)
        applyGradient(binding.dropboxConnectButton)
    }

    private fun applyGradient(button: MaterialButton) {
        button.background = gradient(intArrayOf(Color.rgb(116, 78, 255), Color.rgb(234, 78, 180)), 60f)
        button.setTextColor(Color.WHITE)
        button.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> view.alpha = 0.82f
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.alpha = 1f
            }
            false
        }
    }

    private fun gradient(colors: IntArray, radius: Float) = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors).apply { cornerRadius = radius }
    private fun solid(color: Int, radius: Float) = GradientDrawable().apply { setColor(color); cornerRadius = radius }
    private fun dp(value: Float) = value * resources.displayMetrics.density

    private fun setupNavigation() {
        binding.foldersCard.setOnClickListener { startActivity(Intent(this, FolderSyncActivity::class.java)) }
        binding.syncSetupCard.setOnClickListener { startActivity(Intent(this, SyncSetupActivity::class.java)) }
        binding.googleDriveCard.setOnClickListener { showCloudMessage("Google Drive connection coming next") }
        binding.oneDriveCard.setOnClickListener { showCloudMessage("OneDrive connection coming next") }
        binding.dropboxCard.setOnClickListener { showCloudMessage("Dropbox connection coming next") }
        binding.syncNowButton.setOnClickListener {
            binding.syncStatusText.text = "Sync complete"
            binding.syncSubtitle.text = "Everything is up to date"
            binding.lastSyncText.text = "Last sync: Just now"
            binding.gradientProgress.setProgress(100f, true)
        }
    }

    private fun setupDrawer() {
        binding.toolbar.setNavigationIcon(android.R.drawable.ic_menu_sort_by_size)
        binding.toolbar.setNavigationOnClickListener { binding.drawerLayout.openDrawer(binding.navigationView) }
        binding.navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> binding.drawerLayout.closeDrawers()
                R.id.nav_folders -> { startActivity(Intent(this, FolderSyncActivity::class.java)); binding.drawerLayout.closeDrawers() }
                R.id.nav_automatic -> { startActivity(Intent(this, SyncSetupActivity::class.java)); binding.drawerLayout.closeDrawers() }
                R.id.nav_cloud -> showCloudMessage("Cloud account management")
                R.id.nav_external -> showInfo("External storage", "Use the Android folder picker to choose an external storage folder for syncing.")
                R.id.nav_premium -> showInfo("Premium features", "Unlock automatic sync, multiple folder pairs, advanced scheduling, mirror modes, advanced filtering and no ads.")
                R.id.nav_unlock -> showInfo("Paid features", "Premium purchases will unlock protected sync features. Billing integration can be connected when Play Console product IDs are ready.")
                R.id.nav_about -> showInfo("RSS CLOUD SYNC", "Lightweight cloud synchronization for your local folders. Version 1.0")
                R.id.nav_contact -> showInfo("Contact", "RSS Cloud Sync\nSupport: rsscctvsolution@gmail.com")
            }
            true
        }
    }

    private fun showInfo(title: String, message: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show()
    }

    private fun showCloudMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}