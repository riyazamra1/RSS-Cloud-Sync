package com.riyaz.rsscloudsync

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.MotionEvent
<<<<<<< HEAD
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
=======
>>>>>>> branch 'main' of https://github.com/riyazamra1/RSS-Cloud-Sync.git
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import com.google.android.material.button.MaterialButton
import com.riyaz.rsscloudsync.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val prefs by lazy { getSharedPreferences("appearance", MODE_PRIVATE) }
<<<<<<< HEAD
=======
    private val appPrefs by lazy { getSharedPreferences("rss_cloud_sync", MODE_PRIVATE) }
>>>>>>> branch 'main' of https://github.com/riyazamra1/RSS-Cloud-Sync.git

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupAppearanceButtons()
        setupGradientButtons()
        setupNavigation()
        setupDrawer()
<<<<<<< HEAD
=======
        setupBottomNavigation()
>>>>>>> branch 'main' of https://github.com/riyazamra1/RSS-Cloud-Sync.git
        applyAppearance()
        binding.gradientProgress.setProgress(72f, false)
    }

<<<<<<< HEAD
=======
    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) applyAppearance()
    }

>>>>>>> branch 'main' of https://github.com/riyazamra1/RSS-Cloud-Sync.git
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
<<<<<<< HEAD
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
=======

        val background = if (dark) Color.rgb(11, 14, 24) else Color.rgb(247, 249, 255)
        val surface = if (dark) Color.rgb(22, 25, 37) else Color.WHITE
        val text = if (dark) Color.rgb(245, 245, 250) else Color.rgb(24, 22, 36)
        val secondary = if (dark) Color.rgb(178, 177, 194) else Color.rgb(105, 101, 120)
        val outline = if (dark) Color.rgb(54, 57, 72) else Color.rgb(226, 222, 234)
>>>>>>> branch 'main' of https://github.com/riyazamra1/RSS-Cloud-Sync.git

<<<<<<< HEAD
    private fun applyTextColors(parent: ViewGroup, text: Int, secondary: Int) {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child is TextView && child.id != R.id.lightButton && child.id != R.id.systemButton && child.id != R.id.darkButton) {
                val size = child.textSize / resources.displayMetrics.scaledDensity
                child.setTextColor(if (size <= 11f) secondary else text)
=======
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
>>>>>>> branch 'main' of https://github.com/riyazamra1/RSS-Cloud-Sync.git
            }
<<<<<<< HEAD
            if (child is ViewGroup) applyTextColors(child, text, secondary)
=======
            button.setTextColor(if (button.isSelected) selectedText else unselectedText)
>>>>>>> branch 'main' of https://github.com/riyazamra1/RSS-Cloud-Sync.git
        }
    }

    private fun setupGradientButtons() {
        applyGradient(binding.syncNowButton)
        applyGradient(binding.googleDriveConnectButton)
        applyGradient(binding.oneDriveConnectButton)
        applyGradient(binding.dropboxConnectButton)
    }

    private fun applyGradient(button: MaterialButton) {
<<<<<<< HEAD
        button.background = gradient(intArrayOf(Color.rgb(116, 78, 255), Color.rgb(234, 78, 180)), 60f)
=======
        button.background = gradient(intArrayOf(Color.rgb(116, 73, 232), Color.rgb(214, 72, 175)), 60f)
>>>>>>> branch 'main' of https://github.com/riyazamra1/RSS-Cloud-Sync.git
        button.setTextColor(Color.WHITE)
        button.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> view.alpha = 0.82f
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.alpha = 1f
            }
            false
        }
    }

<<<<<<< HEAD
    private fun gradient(colors: IntArray, radius: Float) = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors).apply { cornerRadius = radius }
    private fun solid(color: Int, radius: Float) = GradientDrawable().apply { setColor(color); cornerRadius = radius }
    private fun dp(value: Float) = value * resources.displayMetrics.density
=======
    private fun gradient(colors: IntArray, radius: Float) =
        GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors).apply { cornerRadius = radius }

    private fun solid(color: Int, radius: Float) =
        GradientDrawable().apply { setColor(color); cornerRadius = radius }
>>>>>>> branch 'main' of https://github.com/riyazamra1/RSS-Cloud-Sync.git

    private fun setupNavigation() {
        binding.foldersCard.setOnClickListener { startActivity(Intent(this, FolderSyncActivity::class.java)) }
<<<<<<< HEAD
        binding.syncSetupCard.setOnClickListener { startActivity(Intent(this, SyncSetupActivity::class.java)) }
        binding.googleDriveCard.setOnClickListener { showCloudMessage("Google Drive connection coming next") }
        binding.oneDriveCard.setOnClickListener { showCloudMessage("OneDrive connection coming next") }
        binding.dropboxCard.setOnClickListener { showCloudMessage("Dropbox connection coming next") }
=======
        binding.syncSetupCard.setOnClickListener { openAutomaticSync() }

        binding.googleDriveCard.setOnClickListener { showCloudMessage("Google Drive") }
        binding.oneDriveCard.setOnClickListener { showCloudMessage("OneDrive") }
        binding.dropboxCard.setOnClickListener { showCloudMessage("Dropbox") }

        binding.googleDriveConnectButton.setOnClickListener { showCloudMessage("Google Drive connection") }
        binding.oneDriveConnectButton.setOnClickListener { showCloudMessage("OneDrive connection") }
        binding.dropboxConnectButton.setOnClickListener { showCloudMessage("Dropbox connection") }

>>>>>>> branch 'main' of https://github.com/riyazamra1/RSS-Cloud-Sync.git
        binding.syncNowButton.setOnClickListener {
            binding.syncStatusText.text = "Sync complete"
            binding.syncSubtitle.text = "Everything is up to date"
            binding.lastSyncText.text = "Last sync: Just now"
            binding.gradientProgress.setProgress(100f, true)
        }
    }

    private fun setupDrawer() {
<<<<<<< HEAD
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
=======
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
>>>>>>> branch 'main' of https://github.com/riyazamra1/RSS-Cloud-Sync.git
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