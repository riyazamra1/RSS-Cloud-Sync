package com.riyaz.rsscloudsync

import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.riyaz.rsscloudsync.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val prefs by lazy { getSharedPreferences("appearance", MODE_PRIVATE) }
    private val appPrefs by lazy { getSharedPreferences("rss_cloud_sync", MODE_PRIVATE) }

    private var bannerPage = 0
    private var bannerDownX = 0f

    private val bannerTitles = arrayOf(
        "Your data, always with you",
        "Upgrade to Premium",
        "Simple sync. Powerful control.",
        "One app. All your clouds."
    )

    private val bannerSubtitles = arrayOf(
        "Secure • Sync • Access • Anywhere",
        "Unlock advanced sync methods and automatic scheduling",
        "Compare FREE and PREMIUM inside the app",
        "Google Drive • OneDrive • Dropbox • MEGA • Box • WebDAV"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupAppearanceButtons()
        setupGradientButtons()
        setupNavigation()
        setupCloudCards()
        setupDrawer()
        setupBottomNavigation()
        setupBannerSlider()
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
        val mode = prefs.getString("mode", "system") ?: "system"
        val dark = when (mode) {
            "dark" -> true
            "light" -> false
            else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }

        val background = if (dark) Color.rgb(10, 13, 22) else Color.rgb(247, 248, 252)
        val surface = if (dark) Color.rgb(22, 27, 40) else Color.WHITE
        val outline = if (dark) Color.rgb(52, 60, 78) else Color.rgb(226, 228, 236)
        val text = if (dark) Color.rgb(247, 248, 252) else Color.rgb(31, 32, 42)
        val secondary = if (dark) Color.rgb(166, 173, 190) else Color.rgb(104, 107, 122)

        binding.root.setBackgroundColor(background)
        binding.mainScrollView.setBackgroundColor(background)
        binding.toolbar.setTitleTextColor(text)
        binding.cloudStorageSubtitle.setTextColor(text)
        binding.cloudSwipeHint.setTextColor(secondary)
        binding.syncStatusText.setTextColor(text)
        binding.syncSubtitle.setTextColor(secondary)
        binding.lastSyncText.setTextColor(secondary)

        val cards = listOf(binding.syncStatusCard, binding.foldersCard, binding.syncSetupCard)
        cards.forEach { card ->
            card.setCardBackgroundColor(surface)
            card.strokeColor = outline
            card.strokeWidth = dpInt(1f)
            card.cardElevation = 0f
        }

        for (i in 0 until binding.cloudProviderRow.childCount) {
            val child = binding.cloudProviderRow.getChildAt(i)
            if (child is MaterialCardView) {
                child.setCardBackgroundColor(surface)
                child.strokeColor = outline
                child.strokeWidth = dpInt(1f)
                child.cardElevation = 0f
            }
        }

        binding.premiumBanner.background = gradient(
            if (dark) intArrayOf(Color.rgb(55, 31, 110), Color.rgb(88, 47, 145))
            else intArrayOf(Color.rgb(89, 67, 196), Color.rgb(126, 79, 204)),
            26f
        )

        val selectedText = Color.WHITE
        val unselectedText = if (dark) Color.rgb(215, 219, 231) else Color.rgb(60, 61, 72)
        listOf(binding.lightButton, binding.systemButton, binding.darkButton).forEach { button ->
            val selected = when (mode) {
                "light" -> button == binding.lightButton
                "dark" -> button == binding.darkButton
                else -> button == binding.systemButton
            }
            button.background = if (selected) gradient(
                intArrayOf(Color.rgb(92, 75, 205), Color.rgb(170, 82, 207)), 50f
            ) else solid(Color.TRANSPARENT, 50f)
            button.setTextColor(if (selected) selectedText else unselectedText)
        }

        binding.bottomNav.setBackgroundColor(surface)
        binding.bottomNav.elevation = dp(4f)
    }

    private fun setupGradientButtons() {
        applyGradient(binding.syncNowButton)
        applyGradient(binding.googleDriveConnectButton)
        applyGradient(binding.oneDriveConnectButton)
        applyGradient(binding.dropboxConnectButton)
    }

    private fun applyGradient(button: MaterialButton) {
        button.background = gradient(intArrayOf(Color.rgb(92, 75, 205), Color.rgb(170, 82, 207)), 60f)
        button.setTextColor(Color.WHITE)
    }

    private fun setupCloudCards() {
        val providers = arrayOf("Google Drive", "OneDrive", "Dropbox", "MEGA", "Box", "WebDAV")
        val row = binding.cloudProviderRow
        for (index in 0 until row.childCount) {
            val provider = providers.getOrElse(index) { "Cloud" }
            val card = row.getChildAt(index)
            card.isClickable = true
            card.isFocusable = true
            card.setOnClickListener { openCloud(provider) }
            setButtonsInsideCard(card as? ViewGroup, provider)
        }
    }

    private fun setButtonsInsideCard(parent: ViewGroup?, provider: String) {
        if (parent == null) return
        for (i in 0 until parent.childCount) {
            when (val child = parent.getChildAt(i)) {
                is MaterialButton -> child.setOnClickListener { openCloud(provider) }
                is ViewGroup -> setButtonsInsideCard(child, provider)
            }
        }
    }

    private fun setupNavigation() {
        binding.foldersCard.setOnClickListener { startActivity(Intent(this, FolderSyncActivity::class.java)) }
        binding.syncSetupCard.setOnClickListener { openAutomaticSync() }
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
                R.id.nav_cloud -> startActivity(Intent(this, CloudAccountsActivity::class.java))
                R.id.nav_external -> startActivity(Intent(this, FolderSyncActivity::class.java))
                R.id.nav_upgrade -> startActivity(Intent(this, PremiumActivity::class.java))
                R.id.nav_about -> startActivity(Intent(this, AboutActivity::class.java))
                R.id.nav_contact -> startActivity(Intent(this, ContactActivity::class.java))
            }
            binding.navigationView.setCheckedItem(item.itemId)
            binding.drawerLayout.closeDrawers()
            true
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNav.selectedItemId = R.id.bottom_home
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.bottom_home -> { binding.mainScrollView.smoothScrollTo(0, 0); true }
                R.id.bottom_sync -> { startActivity(Intent(this, FolderSyncActivity::class.java)); true }
                R.id.bottom_cloud -> { startActivity(Intent(this, CloudAccountsActivity::class.java)); true }
                R.id.bottom_premium -> { startActivity(Intent(this, PremiumActivity::class.java)); true }
                else -> false
            }
        }
    }

    private fun setupBannerSlider() {
        binding.bannerTitle.text = bannerTitles[0]
        binding.bannerSubtitle.text = bannerSubtitles[0]
        binding.premiumBanner.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { bannerDownX = event.x; true }
                MotionEvent.ACTION_UP -> {
                    val distance = event.x - bannerDownX
                    if (kotlin.math.abs(distance) > dp(45f)) {
                        bannerPage = if (distance < 0) (bannerPage + 1) % bannerTitles.size else (bannerPage - 1 + bannerTitles.size) % bannerTitles.size
                        updateBanner()
                    }
                    true
                }
                else -> true
            }
        }
    }

    private fun updateBanner() {
        binding.bannerTitle.text = bannerTitles[bannerPage]
        binding.bannerSubtitle.text = bannerSubtitles[bannerPage]
        binding.bannerLogo.alpha = 1f
    }

    private fun openAutomaticSync() {
        if (appPrefs.getBoolean("premium_unlocked", false)) startActivity(Intent(this, SyncSetupActivity::class.java))
        else startActivity(Intent(this, PremiumActivity::class.java))
    }

    private fun openCloud(provider: String) {
        appPrefs.edit().putString("selected_cloud_provider", provider).apply()
        startActivity(Intent(this, CloudAccountsActivity::class.java))
    }

    private fun gradient(colors: IntArray, radius: Float): GradientDrawable = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors).apply { cornerRadius = radius }

    private fun solid(color: Int, radius: Float): GradientDrawable = GradientDrawable().apply { setColor(color); cornerRadius = radius }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
    private fun dpInt(value: Float): Int = dp(value).toInt()
}
