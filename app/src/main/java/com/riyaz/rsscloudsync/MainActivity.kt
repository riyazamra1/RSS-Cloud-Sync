package com.riyaz.rsscloudsync

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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
    private val bannerHandler = Handler(Looper.getMainLooper())
    private val bannerTitles = arrayOf("Your data, always with you", "Upgrade to Premium", "Simple sync. Powerful control.", "One app. All your clouds.")
    private val bannerSubtitles = arrayOf("Secure • Sync • Access • Anywhere", "Unlock advanced sync methods and automatic scheduling", "Compare FREE and PREMIUM inside the app", "Google Drive • OneDrive • Dropbox • MEGA • Box • WebDAV")
    private val bannerRunnable = object : Runnable { override fun run() { bannerPage = (bannerPage + 1) % bannerTitles.size; updateBanner(); bannerHandler.postDelayed(this, 4500L) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupAppearanceButtons(); setupGradientButtons(); setupNavigation(); setupCloudCards(); setupDrawer(); setupBottomNavigation(); setupBannerSlider(); applyAppearance(); animateInterface()
        binding.gradientProgress.setProgress(72f, false)
    }

    override fun onResume() { super.onResume(); if (::binding.isInitialized) { applyAppearance(); bannerHandler.removeCallbacks(bannerRunnable); bannerHandler.postDelayed(bannerRunnable, 4500L) } }
    override fun onPause() { bannerHandler.removeCallbacks(bannerRunnable); super.onPause() }

    private fun setupAppearanceButtons() {
        binding.lightButton.setOnClickListener { setMode("light", AppCompatDelegate.MODE_NIGHT_NO) }
        binding.systemButton.setOnClickListener { setMode("system", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) }
        binding.darkButton.setOnClickListener { setMode("dark", AppCompatDelegate.MODE_NIGHT_YES) }
    }
    private fun setMode(mode: String, nightMode: Int) { prefs.edit().putString("mode", mode).apply(); AppCompatDelegate.setDefaultNightMode(nightMode) }

    private fun applyAppearance() {
        val mode = prefs.getString("mode", "system") ?: "system"
        val dark = when (mode) { "dark" -> true; "light" -> false; else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES }
        val background = if (dark) Color.rgb(7, 11, 20) else Color.rgb(245, 247, 251)
        val surface = if (dark) Color.rgb(15, 22, 36) else Color.WHITE
        val outline = if (dark) Color.rgb(38, 51, 73) else Color.rgb(228, 231, 236)
        val text = if (dark) Color.rgb(248, 250, 255) else Color.rgb(17, 24, 39)
        val secondary = if (dark) Color.rgb(154, 167, 188) else Color.rgb(102, 112, 133)
        binding.root.setBackgroundColor(background); binding.mainScrollView.setBackgroundColor(background)
        binding.toolbar.setTitleTextColor(text); binding.cloudStorageSubtitle.setTextColor(text); binding.cloudSwipeHint.setTextColor(secondary); binding.syncStatusText.setTextColor(text); binding.syncSubtitle.setTextColor(secondary); binding.lastSyncText.setTextColor(secondary)
        applyTextTheme(binding.contentLayout, text, secondary)
        listOf(binding.syncStatusCard, binding.foldersCard, binding.syncSetupCard).forEach { styleCard(it, surface, outline) }
        for (i in 0 until binding.cloudProviderRow.childCount) (binding.cloudProviderRow.getChildAt(i) as? MaterialCardView)?.let { styleCard(it, surface, outline) }
        binding.premiumBanner.background = gradient(if (dark) intArrayOf(Color.rgb(17, 36, 67), Color.rgb(42, 78, 145)) else intArrayOf(Color.rgb(47, 91, 185), Color.rgb(74, 151, 218)), 26f)
        val selectedText = Color.WHITE; val unselectedText = if (dark) Color.rgb(215, 224, 238) else Color.rgb(60, 68, 82)
        listOf(binding.lightButton, binding.systemButton, binding.darkButton).forEach { button ->
            val selected = when (mode) { "light" -> button == binding.lightButton; "dark" -> button == binding.darkButton; else -> button == binding.systemButton }
            button.background = if (selected) gradient(intArrayOf(Color.rgb(50, 105, 218), Color.rgb(54, 194, 235)), 50f) else solid(Color.TRANSPARENT, 50f)
            button.setTextColor(if (selected) selectedText else unselectedText)
        }
        binding.bottomNav.setBackgroundColor(surface)
        binding.bottomNav.elevation = dp(12f)
        binding.bottomNav.translationZ = dp(3f)
    }

    private fun applyTextTheme(parent: ViewGroup, primary: Int, secondary: Int) {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child === binding.premiumBanner) continue
            when (child) {
                is MaterialButton -> Unit
                is TextView -> if (child !== binding.lightButton && child !== binding.systemButton && child !== binding.darkButton) child.setTextColor(if (child.textSize <= 12f * resources.displayMetrics.scaledDensity) secondary else primary)
                is ViewGroup -> applyTextTheme(child, primary, secondary)
            }
        }
    }

    private fun styleCard(card: MaterialCardView, surface: Int, outline: Int) { card.setCardBackgroundColor(surface); card.strokeColor = outline; card.strokeWidth = dpInt(1f); card.cardElevation = 0f; card.radius = dp(22f) }
    private fun setupGradientButtons() { applyGradient(binding.syncNowButton); applyGradient(binding.googleDriveConnectButton); applyGradient(binding.oneDriveConnectButton); applyGradient(binding.dropboxConnectButton) }
    private fun applyGradient(button: MaterialButton) { button.background = gradient(intArrayOf(Color.rgb(50, 105, 218), Color.rgb(54, 194, 235)), 60f); button.setTextColor(Color.WHITE) }

    private fun setupCloudCards() {
        val providers = arrayOf("Google Drive", "OneDrive", "Dropbox", "MEGA", "Box", "WebDAV")
        val row = binding.cloudProviderRow
        for (index in 0 until row.childCount) { val provider = providers.getOrElse(index) { "Cloud" }; val card = row.getChildAt(index); card.isClickable = true; card.isFocusable = true; card.setOnClickListener { openCloud(provider) }; setButtonsInsideCard(card as? ViewGroup, provider) }
    }
    private fun setButtonsInsideCard(parent: ViewGroup?, provider: String) { if (parent == null) return; for (i in 0 until parent.childCount) when (val child = parent.getChildAt(i)) { is MaterialButton -> child.setOnClickListener { openCloud(provider) }; is ViewGroup -> setButtonsInsideCard(child, provider) } }
    private fun setupNavigation() {
        binding.foldersCard.setOnClickListener { startActivity(Intent(this, FolderSyncActivity::class.java)) }
        binding.syncSetupCard.setOnClickListener { openAutomaticSync() }
        binding.syncNowButton.setOnClickListener { binding.syncStatusText.text = "Sync complete"; binding.syncSubtitle.text = "Everything is up to date"; binding.lastSyncText.text = "Last sync: Just now"; binding.gradientProgress.setProgress(100f, true) }
    }
    private fun setupDrawer() {
        binding.toolbar.setNavigationOnClickListener { binding.drawerLayout.openDrawer(binding.navigationView) }
        binding.navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) { R.id.nav_home -> binding.mainScrollView.smoothScrollTo(0, 0); R.id.nav_folders -> startActivity(Intent(this, FolderSyncActivity::class.java)); R.id.nav_automatic -> openAutomaticSync(); R.id.nav_cloud -> startActivity(Intent(this, CloudAccountsActivity::class.java)); R.id.nav_external -> startActivity(Intent(this, FolderSyncActivity::class.java)); R.id.nav_upgrade -> startActivity(Intent(this, PremiumActivity::class.java)); R.id.nav_about -> startActivity(Intent(this, AboutActivity::class.java)); R.id.nav_contact -> startActivity(Intent(this, ContactActivity::class.java)) }
            binding.navigationView.setCheckedItem(item.itemId); binding.drawerLayout.closeDrawers(); true
        }
    }
    private fun setupBottomNavigation() {
        binding.bottomNav.selectedItemId = R.id.bottom_home
        binding.bottomNav.setOnItemSelectedListener { item -> when (item.itemId) {
            R.id.bottom_home -> { binding.mainScrollView.smoothScrollTo(0, 0); true }
            R.id.bottom_sync -> { startActivity(Intent(this, FolderSyncActivity::class.java)); true }
            R.id.bottom_cloud -> { startActivity(Intent(this, CloudAccountsActivity::class.java)); true }
            R.id.bottom_premium -> { startActivity(Intent(this, PremiumActivity::class.java)); true }
            R.id.bottom_settings -> { startActivity(Intent(this, SettingsActivity::class.java)); true }
            else -> false
        } }
    }
    private fun setupBannerSlider() {
        binding.bannerTitle.text = bannerTitles[0]; binding.bannerSubtitle.text = bannerSubtitles[0]
        binding.premiumBanner.setOnClickListener { if (bannerPage == 1) startActivity(Intent(this, PremiumActivity::class.java)) }
        binding.premiumBanner.setOnTouchListener { _, event -> when (event.actionMasked) { MotionEvent.ACTION_DOWN -> { bannerDownX = event.x; false }; MotionEvent.ACTION_UP -> { val distance = event.x - bannerDownX; if (kotlin.math.abs(distance) > dp(45f)) { bannerPage = if (distance < 0) (bannerPage + 1) % bannerTitles.size else (bannerPage - 1 + bannerTitles.size) % bannerTitles.size; updateBanner(); true } else false }; else -> false } }
    }
    private fun updateBanner() { binding.bannerTitle.text = bannerTitles[bannerPage]; binding.bannerSubtitle.text = bannerSubtitles[bannerPage]; binding.bannerLogo.alpha = 1f; binding.bannerTitle.animate().alpha(0f).setDuration(90).withEndAction { binding.bannerTitle.animate().alpha(1f).setDuration(180).start() }.start() }
    private fun animateInterface() {
        binding.contentLayout.alpha = 0f
        binding.contentLayout.translationY = dp(8f)
        binding.contentLayout.animate().alpha(1f).translationY(0f).setDuration(320).start()
        for (i in 0 until binding.cloudProviderRow.childCount) {
            val child = binding.cloudProviderRow.getChildAt(i)
            child.alpha = 0f; child.translationY = dp(8f)
            child.animate().alpha(1f).translationY(0f).setStartDelay((i * 45L)).setDuration(240).start()
        }
    }
    private fun openAutomaticSync() { if (appPrefs.getBoolean("premium_unlocked", false)) startActivity(Intent(this, SyncSetupActivity::class.java)) else startActivity(Intent(this, PremiumActivity::class.java)) }
    private fun openCloud(provider: String) { appPrefs.edit().putString("selected_cloud_provider", provider).apply(); startActivity(Intent(this, CloudAccountsActivity::class.java)) }
    private fun gradient(colors: IntArray, radius: Float): GradientDrawable = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors).apply { cornerRadius = radius }
    private fun solid(color: Int, radius: Float): GradientDrawable = GradientDrawable().apply { setColor(color); cornerRadius = radius }
    private fun dp(value: Float): Float = value * resources.displayMetrics.density
    private fun dpInt(value: Float): Int = dp(value).toInt()
}
