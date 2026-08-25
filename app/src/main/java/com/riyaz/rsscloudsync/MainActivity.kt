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
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.WindowCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.riyaz.rsscloudsync.databinding.ActivityMainBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val prefs by lazy { getSharedPreferences("appearance", MODE_PRIVATE) }
    private val appPrefs by lazy { getSharedPreferences("rss_cloud_sync", MODE_PRIVATE) }
    private val syncExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var activeEngine: SyncEngine? = null
    private var bannerPage = 0
    private var bannerDownX = 0f
    private val bannerHandler = Handler(Looper.getMainLooper())
    private val bannerTitles = arrayOf("Your data, always safe with you", "Upgrade to Premium", "Simple sync. Powerful control.", "One app. All your clouds.")
    private val bannerSubtitles = arrayOf("Backup • Sync • Access • Anywhere", "Unlock advanced sync methods and automatic scheduling", "Compare FREE and PREMIUM inside the app", "Google Drive • OneDrive • Dropbox • MEGA • Box • WebDAV")
    private val bannerRunnable = object : Runnable { override fun run() { bannerPage = (bannerPage + 1) % bannerTitles.size; updateBanner(); bannerHandler.postDelayed(this, 4500L) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupAppearanceButtons(); setupGradientButtons(); setupNavigation(); setupCloudCards(); setupDrawer(); setupBottomNavigation(); setupBannerSlider(); applyAppearance(); applyReferenceUi(); animateInterface()
        binding.gradientProgress.setProgress(72f, false)
        refreshDashboard()
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) {
            applyAppearance(); applyReferenceUi(); refreshDashboard()
            bannerHandler.removeCallbacks(bannerRunnable); bannerHandler.postDelayed(bannerRunnable, 4500L)
        }
    }

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
        val background = if (dark) Color.rgb(7, 11, 20) else Color.rgb(247, 248, 252)
        val surface = if (dark) Color.rgb(15, 22, 36) else Color.WHITE
        val outline = if (dark) Color.rgb(38, 51, 73) else Color.rgb(225, 228, 236)
        val text = if (dark) Color.rgb(248, 250, 255) else Color.rgb(37, 43, 58)
        val secondary = if (dark) Color.rgb(154, 167, 188) else Color.rgb(111, 119, 137)
        binding.root.setBackgroundColor(background); binding.mainScrollView.setBackgroundColor(background)
        binding.toolbar.setTitleTextColor(text); binding.cloudStorageSubtitle.setTextColor(text); binding.cloudSwipeHint.setTextColor(secondary); binding.syncStatusText.setTextColor(text); binding.syncSubtitle.setTextColor(secondary); binding.lastSyncText.setTextColor(secondary)
        applyTextTheme(binding.contentLayout, text, secondary)
        listOf(binding.syncStatusCard, binding.foldersCard, binding.syncSetupCard).forEach { styleCard(it, surface, outline) }
        for (i in 0 until binding.cloudProviderRow.childCount) (binding.cloudProviderRow.getChildAt(i) as? MaterialCardView)?.let { styleCard(it, surface, outline) }
        binding.premiumBanner.background = gradient(if (dark) intArrayOf(Color.rgb(35, 28, 90), Color.rgb(37, 91, 180)) else intArrayOf(Color.rgb(72, 39, 177), Color.rgb(39, 119, 225)), 26f)
        val selectedText = Color.WHITE; val unselectedText = if (dark) Color.rgb(215, 224, 238) else Color.rgb(60, 68, 82)
        listOf(binding.lightButton, binding.systemButton, binding.darkButton).forEach { button ->
            val selected = when (mode) { "light" -> button == binding.lightButton; "dark" -> button == binding.darkButton; else -> button == binding.systemButton }
            button.background = if (selected) gradient(intArrayOf(Color.rgb(125, 49, 235), Color.rgb(39, 190, 235)), 50f) else solid(Color.TRANSPARENT, 50f)
            button.setTextColor(if (selected) selectedText else unselectedText)
        }
        binding.bottomNav.setBackgroundColor(surface); binding.bottomNav.elevation = dp(12f); binding.bottomNav.translationZ = dp(3f)
        tintNavigationIcons(); tintCloudIcons()
    }

    private fun applyReferenceUi() {
        binding.toolbar.title = "RSS CLOUD SYNC"
        binding.cloudStorageSubtitle.text = "CLOUD ACCOUNTS"
        binding.cloudSwipeHint.text = "Manage  ›"
        binding.syncStatusText.text = if (binding.syncStatusText.text.isBlank()) "Ready to sync" else binding.syncStatusText.text
        binding.syncNowButton.text = "↻  SYNC NOW"
        (binding.premiumBanner.layoutParams as? ViewGroup.LayoutParams)?.let { it.height = dpInt(190f); binding.premiumBanner.layoutParams = it }
        (binding.syncStatusCard.layoutParams as? ViewGroup.LayoutParams)?.let { it.height = dpInt(214f); binding.syncStatusCard.layoutParams = it }
        (binding.bottomNav.layoutParams as? ViewGroup.LayoutParams)?.let { it.height = dpInt(70f); binding.bottomNav.layoutParams = it }
        for (i in 0 until binding.cloudProviderRow.childCount) {
            val card = binding.cloudProviderRow.getChildAt(i)
            (card.layoutParams as? ViewGroup.MarginLayoutParams)?.let { it.width = dpInt(142f); it.height = dpInt(158f); card.layoutParams = it }
        }
    }

    private fun tintNavigationIcons() {
        val drawerColors = intArrayOf(0xFF6C3FEA.toInt(), 0xFF4D8DFF.toInt(), 0xFF2DC9A3.toInt(), 0xFF38A6F2.toInt(), 0xFFFF9F43.toInt(), 0xFFFFC83D.toInt(), 0xFF8B5CF6.toInt(), 0xFF2AB7C9.toInt())
        binding.navigationView.menu.forEachIndexed { index, item -> item.icon?.let { DrawableCompat.setTint(it, drawerColors[index % drawerColors.size]) } }
        val bottomColors = intArrayOf(0xFF7C4DFF.toInt(), 0xFF3F83F8.toInt(), 0xFF22B8CF.toInt(), 0xFFFFB020.toInt(), 0xFF6875F5.toInt())
        binding.bottomNav.menu.forEachIndexed { index, item -> item.icon?.let { DrawableCompat.setTint(it, bottomColors[index % bottomColors.size]) } }
    }

    private fun tintCloudIcons() {
        val colors = intArrayOf(0xFF4285F4.toInt(), 0xFF2563EB.toInt(), 0xFF0061FF.toInt(), 0xFFD9008D.toInt(), 0xFF1677FF.toInt(), 0xFF6B7280.toInt())
        for (i in 0 until binding.cloudProviderRow.childCount) {
            val card = binding.cloudProviderRow.getChildAt(i) as? ViewGroup ?: continue
            val image = findFirstImage(card) ?: continue
            image.imageTintList = null
            image.alpha = 1f
            image.tag = colors[i % colors.size]
        }
    }

    private fun findFirstImage(parent: ViewGroup): ImageView? {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child is ImageView) return child
            if (child is ViewGroup) findFirstImage(child)?.let { return it }
        }
        return null
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
    private fun applyGradient(button: MaterialButton) { button.background = gradient(intArrayOf(Color.rgb(124, 61, 237), Color.rgb(38, 181, 235)), 60f); button.setTextColor(Color.WHITE) }

    private fun setupCloudCards() {
        val providers = arrayOf("Google Drive", "OneDrive", "Dropbox", "MEGA", "Box", "WebDAV")
        val row = binding.cloudProviderRow
        for (index in 0 until row.childCount) {
            val provider = providers.getOrElse(index) { "Cloud" }
            val card = row.getChildAt(index)
            card.isClickable = true; card.isFocusable = true
            card.setOnClickListener { openCloud(provider) }
            setButtonsInsideCard(card as? ViewGroup, provider)
        }
    }

    private fun setButtonsInsideCard(parent: ViewGroup?, provider: String) {
        if (parent == null) return
        for (i in 0 until parent.childCount) {
            when (val child = parent.getChildAt(i)) {
                is MaterialButton -> child.setOnClickListener { if (provider == "Google Drive") startActivity(Intent(this, CloudAccountsActivity::class.java)) else openCloud(provider) }
                is ViewGroup -> setButtonsInsideCard(child, provider)
            }
        }
    }

    private fun refreshDashboard() {
        val connected = appPrefs.getStringSet("connected_cloud_providers", emptySet()) ?: emptySet()
        val googleConnected = connected.contains("Google Drive")
        val email = appPrefs.getString("google_drive_account_email", "") ?: ""
        val history = SyncHistoryManager.get(this)
        val last = history.firstOrNull()
        if (last == null) { binding.lastSyncText.text = "Last sync: Never"; binding.syncSubtitle.text = if (googleConnected) "Google Drive connected • Ready to sync" else "Connect a cloud account to begin" }
        else { val status = if (last.success) "Success" else if (last.bytesTransferred > 0) "Completed with warnings" else "Failed"; binding.lastSyncText.text = "Last sync: ${formatDate(last.timestamp)} • $status"; binding.syncSubtitle.text = "${last.filesChanged} changed • ↑${last.uploadedFiles} ↓${last.downloadedFiles} • ${formatBytes(last.bytesTransferred)}" }
        updateGoogleDashboardCard(googleConnected, email)
        if (googleConnected) syncExecutor.execute { try { val quota = DriveClient(this).quotaText(); runOnUiThread { if (!isFinishing) updateGoogleDashboardCard(true, email, quota) } } catch (_: Exception) { runOnUiThread { if (!isFinishing) updateGoogleDashboardCard(true, email, "Storage unavailable") } } }
    }

    private fun updateGoogleDashboardCard(connected: Boolean, email: String, quota: String = "") {
        val row = binding.cloudProviderRow; if (row.childCount == 0) return
        val card = row.getChildAt(0) as? ViewGroup ?: return
        val texts = ArrayList<TextView>(); collectTextViews(card, texts)
        val subtitle = texts.firstOrNull { it.text.toString() != "Google Drive" && it.text.toString() != "" && it !is MaterialButton }
        subtitle?.text = when { !connected -> "Not connected"; quota.isNotBlank() && email.isNotBlank() -> "$email\n$quota"; email.isNotBlank() -> "Connected • $email"; quota.isNotBlank() -> quota; else -> "Connected" }
        card.findButton()?.let { button -> button.text = if (connected) "CHANGE" else "CONNECT" }
    }

    private fun collectTextViews(parent: ViewGroup, out: MutableList<TextView>) { for (i in 0 until parent.childCount) { val child = parent.getChildAt(i); if (child is MaterialButton) continue; if (child is TextView) out += child; if (child is ViewGroup) collectTextViews(child, out) } }
    private fun ViewGroup.findButton(): MaterialButton? { for (i in 0 until childCount) { val child = getChildAt(i); if (child is MaterialButton) return child; if (child is ViewGroup) child.findButton()?.let { return it } }; return null }
    private fun formatDate(timestamp: Long): String = java.text.SimpleDateFormat("dd MMM, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
    private fun formatBytes(bytes: Long): String { if (bytes < 1024L) return "$bytes B"; if (bytes < 1024L * 1024L) return String.format(java.util.Locale.getDefault(), "%.1f KB", bytes / 1024.0); if (bytes < 1024L * 1024L * 1024L) return String.format(java.util.Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0)); if (bytes < 1024L * 1024L * 1024L * 1024L) return String.format(java.util.Locale.getDefault(), "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0)); return String.format(java.util.Locale.getDefault(), "%.2f TB", bytes / (1024.0 * 1024.0 * 1024.0 * 1024.0)) }

    private fun setupNavigation() { binding.foldersCard.setOnClickListener { startActivity(Intent(this, SyncSetupActivity::class.java)) }; binding.syncSetupCard.setOnClickListener { openAutomaticSync() }; binding.syncNowButton.setOnClickListener { startActivity(Intent(this, SyncSetupActivity::class.java)) } }

    private fun setupDrawer() {
        binding.toolbar.setNavigationOnClickListener { binding.drawerLayout.openDrawer(binding.navigationView) }
        binding.navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> binding.mainScrollView.smoothScrollTo(0, 0)
                R.id.nav_folders -> startActivity(Intent(this, SyncSetupActivity::class.java))
                R.id.nav_automatic -> openAutomaticSync()
                R.id.nav_cloud -> startActivity(Intent(this, CloudAccountsActivity::class.java))
                R.id.nav_external -> startActivity(Intent(this, SyncSetupActivity::class.java))
                R.id.nav_upgrade -> startActivity(Intent(this, PremiumActivity::class.java))
                R.id.nav_about -> startActivity(Intent(this, AboutActivity::class.java))
                R.id.nav_contact -> startActivity(Intent(this, ContactActivity::class.java))
            }
            binding.navigationView.setCheckedItem(item.itemId); binding.drawerLayout.closeDrawers(); true
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNav.selectedItemId = R.id.bottom_home
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.bottom_home -> { binding.mainScrollView.smoothScrollTo(0, 0); true }
                R.id.bottom_sync -> { startActivity(Intent(this, SyncSetupActivity::class.java)); true }
                R.id.bottom_cloud -> { startActivity(Intent(this, CloudAccountsActivity::class.java)); true }
                R.id.bottom_premium -> { startActivity(Intent(this, PremiumActivity::class.java)); true }
                R.id.bottom_settings -> { startActivity(Intent(this, SettingsActivity::class.java)); true }
                else -> false
            }
        }
    }

    private fun setupBannerSlider() {
        binding.bannerTitle.text = bannerTitles[0]; binding.bannerSubtitle.text = bannerSubtitles[0]
        binding.premiumBanner.setOnClickListener { if (bannerPage == 1) startActivity(Intent(this, PremiumActivity::class.java)) }
        binding.premiumBanner.setOnTouchListener { _, event -> when (event.actionMasked) { MotionEvent.ACTION_DOWN -> { bannerDownX = event.x; false }; MotionEvent.ACTION_UP -> { val distance = event.x - bannerDownX; if (kotlin.math.abs(distance) > dp(45f)) { bannerPage = if (distance < 0) (bannerPage + 1) % bannerTitles.size else (bannerPage - 1 + bannerTitles.size) % bannerTitles.size; updateBanner(); true } else false }; else -> false } }
    }

    private fun updateBanner() { binding.bannerTitle.text = bannerTitles[bannerPage]; binding.bannerSubtitle.text = bannerSubtitles[bannerPage]; binding.bannerLogo.alpha = 1f; binding.bannerTitle.animate().alpha(0f).setDuration(90).withEndAction { binding.bannerTitle.animate().alpha(1f).setDuration(180).start() }.start() }
    private fun animateInterface() { binding.contentLayout.alpha = 0f; binding.contentLayout.translationY = dp(8f); binding.contentLayout.animate().alpha(1f).translationY(0f).setDuration(320).start(); for (i in 0 until binding.cloudProviderRow.childCount) { val child = binding.cloudProviderRow.getChildAt(i); child.alpha = 0f; child.translationY = dp(8f); child.animate().alpha(1f).translationY(0f).setStartDelay(i * 45L).setDuration(240).start() } }
    private fun openAutomaticSync() { startActivity(Intent(this, SyncSetupActivity::class.java)) }
    private fun openCloud(provider: String) { appPrefs.edit().putString("selected_cloud_provider", provider).apply(); startActivity(Intent(this, SyncSetupActivity::class.java)) }
    private fun gradient(colors: IntArray, radius: Float): GradientDrawable = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors).apply { cornerRadius = radius }
    private fun solid(color: Int, radius: Float): GradientDrawable = GradientDrawable().apply { setColor(color); cornerRadius = radius }
    private fun dp(value: Float): Float = value * resources.displayMetrics.density
    private fun dpInt(value: Float): Int = dp(value).toInt()
    override fun onDestroy() { activeEngine?.cancel(); syncExecutor.shutdownNow(); super.onDestroy() }
}
