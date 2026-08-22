package com.riyaz.rsscloudsync

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.MotionEvent
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import com.google.android.material.button.MaterialButton
import com.riyaz.rsscloudsync.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val prefs by lazy { getSharedPreferences("appearance", MODE_PRIVATE) }
    private val appPrefs by lazy { getSharedPreferences("rss_cloud_sync", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupAppearanceButtons()
        setupGradientButtons()
        setupNavigation()
        setupDrawer()
        setupBottomNavigation()
        applyAppearance()
        binding.gradientProgress.setProgress(72f, false)
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) applyAppearance()
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

        val background = if (dark) Color.rgb(11, 14, 24) else Color.rgb(247, 249, 255)
        val surface = if (dark) Color.rgb(22, 25, 37) else Color.WHITE
        val text = if (dark) Color.rgb(245, 245, 250) else Color.rgb(24, 22, 36)
        val secondary = if (dark) Color.rgb(178, 177, 194) else Color.rgb(105, 101, 120)
        val outline = if (dark) Color.rgb(54, 57, 72) else Color.rgb(226, 222, 234)

        binding.root.setBackgroundColor(background)
        binding.mainScrollView.setBackgroundColor(background)
        binding.toolbar.setTitleTextColor(text)
        binding.cloudStorageSubtitle.setTextColor(secondary)
        binding.cloudSwipeHint.setTextColor(secondary)
        binding.syncStatusText.setTextColor(text)
        binding.syncSubtitle.setTextColor(secondary)
        binding.lastSyncText.setTextColor(secondary)

        val cards = listOf(
            binding.syncStatusCard,
            binding.googleDriveCard,
            binding.oneDriveCard,
            binding.dropboxCard,
            binding.foldersCard,
            binding.syncSetupCard
        )
        cards.forEach {
            it.setCardBackgroundColor(surface)
            it.strokeColor = outline
            it.cardElevation = 0f
        }

        val bannerColors = if (dark) {
            intArrayOf(Color.rgb(39, 14, 94), Color.rgb(93, 28, 174))
        } else {
            intArrayOf(Color.rgb(91, 34, 217), Color.rgb(116, 57, 226))
        }
        binding.premiumBanner.background = gradient(bannerColors, 24f)

        val selectedText = Color.WHITE
        val unselectedText = if (dark) Color.rgb(215, 213, 225) else Color.rgb(34, 31, 46)
        listOf(binding.lightButton, binding.systemButton, binding.darkButton).forEach { button ->
            button.background = if (button.isSelected) {
                gradient(intArrayOf(Color.rgb(122, 73, 235), Color.rgb(216, 70, 174)), 50f)
            } else {
                solid(Color.TRANSPARENT, 50f)
            }
            button.setTextColor(if (button.isSelected) selectedText else unselectedText)
        }
    }

    private fun setupGradientButtons() {
        applyGradient(binding.syncNowButton)
        applyGradient(binding.googleDriveConnectButton)
        applyGradient(binding.oneDriveConnectButton)
        applyGradient(binding.dropboxConnectButton)
    }

    private fun applyGradient(button: MaterialButton) {
        button.background = gradient(intArrayOf(Color.rgb(116, 73, 232), Color.rgb(214, 72, 175)), 60f)
        button.setTextColor(Color.WHITE)
        button.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> view.alpha = 0.82f
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.alpha = 1f
            }
            false
        }
    }

    private fun gradient(colors: IntArray, radius: Float) =
        GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors).apply { cornerRadius = radius }

    private fun solid(color: Int, radius: Float) =
        GradientDrawable().apply { setColor(color); cornerRadius = radius }

    private fun setupNavigation() {
        binding.foldersCard.setOnClickListener { startActivity(Intent(this, FolderSyncActivity::class.java)) }
        binding.syncSetupCard.setOnClickListener { openAutomaticSync() }

        binding.googleDriveCard.setOnClickListener { showCloudMessage("Google Drive") }
        binding.oneDriveCard.setOnClickListener { showCloudMessage("OneDrive") }
        binding.dropboxCard.setOnClickListener { showCloudMessage("Dropbox") }

        binding.googleDriveConnectButton.setOnClickListener { showCloudMessage("Google Drive connection") }
        binding.oneDriveConnectButton.setOnClickListener { showCloudMessage("OneDrive connection") }
        binding.dropboxConnectButton.setOnClickListener { showCloudMessage("Dropbox connection") }

        binding.syncNowButton.setOnClickListener {
            binding.syncStatusText.text = "Sync complete"
            binding.syncSubtitle.text = "Everything is up to date"
            binding.lastSyncText.text = "Last sync: Just now"
            binding.gradientProgress.setProgress(100f, true)
        }
    }

    private fun setupDrawer() {
        binding.toolbar.setNavigationOnClickListener { binding.drawerLayout.openDrawer(binding.navigationView) }
        binding.navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> binding.mainScrollView.smoothScrollTo(0, 0)
                R.id.nav_folders -> startActivity(Intent(this, FolderSyncActivity::class.java))
                R.id.nav_automatic -> openAutomaticSync()
                R.id.nav_cloud -> scrollToCloudAccounts()
                R.id.nav_external -> showInfo("External storage", "Choose an Android storage folder from Sync folders.")
                R.id.nav_free, R.id.nav_free_manual -> showFreeFeatures()
                R.id.nav_premium -> showPremiumFeatures()
                R.id.nav_unlock -> showUpgradeDialog()
                R.id.nav_about -> startActivity(Intent(this, AboutActivity::class.java))
                R.id.nav_contact -> startActivity(Intent(this, ContactActivity::class.java))
            }
            binding.drawerLayout.closeDrawers()
            true
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNav.selectedItemId = R.id.bottom_home
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.bottom_home -> {
                    binding.mainScrollView.smoothScrollTo(0, 0)
                    true
                }
                R.id.bottom_sync -> {
                    startActivity(Intent(this, FolderSyncActivity::class.java))
                    false
                }
                R.id.bottom_cloud -> {
                    scrollToCloudAccounts()
                    true
                }
                R.id.bottom_premium -> {
                    showPremiumFeatures()
                    false
                }
                else -> false
            }
        }
    }

    private fun openAutomaticSync() {
        if (appPrefs.getBoolean("premium_unlocked", false)) {
            startActivity(Intent(this, SyncSetupActivity::class.java))
        } else {
            showPremiumRequired()
        }
    }

    private fun scrollToCloudAccounts() {
        binding.mainScrollView.post { binding.mainScrollView.smoothScrollTo(0, binding.cloudAccountsScroll.top) }
    }

    private fun showFreeFeatures() {
        showInfo("FREE FEATURES", "• Two-way Sync\n• Manual Sync\n\nThat's it. The free plan stays simple and lightweight.")
    }

    private fun showPremiumFeatures() {
        showInfo(
            "PREMIUM FEATURES",
            "Everything in FREE, plus:\n\n• Upload only\n• Upload mirror\n• Upload & delete\n• Download only\n• Download mirror\n• Download & delete\n• Automatic sync\n• Multiple folder pairs\n• Advanced scheduling\n• Advanced filtering\n• Priority sync\n• Extended sync history\n• No ads"
        )
    }

    private fun showPremiumRequired() {
        AlertDialog.Builder(this)
            .setTitle("Premium feature")
            .setMessage("Automatic sync is available with PREMIUM. FREE includes Two-way Sync and Manual Sync.")
            .setNegativeButton("Not now", null)
            .setPositiveButton("View Premium") { _, _ -> showPremiumFeatures() }
            .show()
    }

    private fun showUpgradeDialog() {
        AlertDialog.Builder(this)
            .setTitle("Upgrade to PREMIUM")
            .setMessage("Unlock automatic sync and all advanced sync directions, scheduling and filtering.")
            .setNegativeButton("Later", null)
            .setPositiveButton("Continue", null)
            .show()
    }

    private fun showInfo(title: String, message: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show()
    }

    private fun showCloudMessage(provider: String) {
        showInfo(provider, "Cloud account connection will open the provider's secure sign-in flow when its API credentials are configured.")
    }
}