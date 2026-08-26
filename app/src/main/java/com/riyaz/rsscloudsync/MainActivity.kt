package com.riyaz.rsscloudsync

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.WindowCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.riyaz.rsscloudsync.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val prefs by lazy { getSharedPreferences("appearance", MODE_PRIVATE) }
    private val appPrefs by lazy { getSharedPreferences("rss_cloud_sync", MODE_PRIVATE) }
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar()
        setupAppearance()
        setupCloudCards()
        setupNavigation()
        setupDrawer()
        setupBottomNavigation()
        applyAppearance()
        refineDashboard()
        refreshDashboard()
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) {
            applyAppearance()
            refineDashboard()
            refreshDashboard()
        }
    }

    private fun setupToolbar() {
        binding.toolbar.inflateMenu(R.menu.toolbar_menu)
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_notifications) {
                startActivity(Intent(this, NotificationsActivity::class.java))
                true
            } else false
        }
    }

    private fun setupAppearance() {
        binding.lightButton.setOnClickListener { setMode("light", AppCompatDelegate.MODE_NIGHT_NO) }
        binding.systemButton.setOnClickListener { setMode("system", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) }
        binding.darkButton.setOnClickListener { setMode("dark", AppCompatDelegate.MODE_NIGHT_YES) }
    }

    private fun setMode(mode: String, night: Int) {
        prefs.edit().putString("mode", mode).apply()
        AppCompatDelegate.setDefaultNightMode(night)
    }

    private fun setupCloudCards() {
        val providers = arrayOf("Google Drive", "OneDrive", "Dropbox", "MEGA", "Box", "WebDAV")
        for (i in 0 until binding.cloudProviderRow.childCount) {
            val provider = providers.getOrElse(i) { "Cloud" }
            val card = binding.cloudProviderRow.getChildAt(i)
            card.setOnClickListener { openCloud(provider) }
            findButtons(card).forEach { button ->
                styleGradient(button)
                button.setOnClickListener { openCloud(provider) }
            }
        }
    }

    private fun findButtons(parent: View): List<MaterialButton> {
        val result = mutableListOf<MaterialButton>()
        if (parent is MaterialButton) result += parent
        if (parent is ViewGroup) for (i in 0 until parent.childCount) result += findButtons(parent.getChildAt(i))
        return result
    }

    private fun styleGradient(button: MaterialButton) {
        button.background = gradient(intArrayOf(Color.rgb(112, 74, 235), Color.rgb(43, 177, 232)), 14f)
        button.setTextColor(Color.WHITE)
        button.strokeWidth = 0
        button.minHeight = dp(28)
        button.setPadding(dp(12), 0, dp(12), 0)
    }

    private fun setupNavigation() {
        binding.premiumBanner.setOnClickListener { openPremium() }
        binding.foldersCard.setOnClickListener { startActivity(Intent(this, SyncPairsActivity::class.java)) }
        binding.syncSetupCard.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        binding.syncNowButton.setOnClickListener { startActivity(Intent(this, SyncPairsActivity::class.java)) }
        binding.cloudSwipeHint.setOnClickListener { startActivity(Intent(this, CloudAccountsActivity::class.java)) }
    }

    private fun setupDrawer() {
        binding.toolbar.setNavigationOnClickListener { binding.drawerLayout.openDrawer(binding.navigationView) }
        binding.navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> binding.mainScrollView.smoothScrollTo(0, 0)
                R.id.nav_folders -> startActivity(Intent(this, SyncPairsActivity::class.java))
                R.id.nav_cloud -> startActivity(Intent(this, CloudAccountsActivity::class.java))
                R.id.nav_history -> startActivity(Intent(this, HistoryActivity::class.java))
                R.id.nav_automatic -> startActivity(Intent(this, SettingsActivity::class.java))
                R.id.nav_backup -> startActivity(Intent(this, ExternalStorageActivity::class.java))
                R.id.nav_usage -> startActivity(Intent(this, CloudAccountsActivity::class.java))
                R.id.nav_help -> startActivity(Intent(this, ContactActivity::class.java))
                R.id.nav_about -> startActivity(Intent(this, AboutActivity::class.java))
                R.id.nav_upgrade -> openPremium()
            }
            binding.navigationView.setCheckedItem(item.itemId)
            binding.drawerLayout.closeDrawers()
            true
        }
        binding.navigationView.layoutParams = binding.navigationView.layoutParams.apply { width = dp(248) }
        binding.navigationView.setItemVerticalPadding(dp(3))
        binding.navigationView.setItemHorizontalPadding(dp(11))
        binding.navigationView.setItemIconPadding(dp(10))
    }

    private fun openPremium() {
        startActivity(Intent(this, PremiumActivity::class.java))
    }

    private fun setupBottomNavigation() {
        binding.bottomNav.selectedItemId = R.id.bottom_home
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.bottom_home -> { binding.mainScrollView.smoothScrollTo(0, 0); true }
                R.id.bottom_sync -> { startActivity(Intent(this, SyncPairsActivity::class.java)); true }
                R.id.bottom_history -> { startActivity(Intent(this, HistoryActivity::class.java)); true }
                R.id.bottom_settings -> { startActivity(Intent(this, SettingsActivity::class.java)); true }
                else -> false
            }
        }
    }

    /** Applies the compact visual system without changing the functional view hierarchy. */
    private fun refineDashboard() {
        if (!::binding.isInitialized) return
        val density = resources.displayMetrics.density
        val content = binding.contentLayout
        content.setPadding(dp(14), 0, dp(14), dp(10))

        // Tight, consistent vertical rhythm.
        for (i in 1 until content.childCount) {
            val child = content.getChildAt(i)
            val lp = child.layoutParams as? ViewGroup.MarginLayoutParams ?: continue
            lp.topMargin = when (i) {
                1 -> 0
                else -> dp(8)
            }
            child.layoutParams = lp
        }

        binding.toolbar.layoutParams = binding.toolbar.layoutParams.apply { height = dp(52) }
        binding.premiumBanner.layoutParams = binding.premiumBanner.layoutParams.apply { height = dp(156) }
        binding.syncStatusCard.layoutParams = binding.syncStatusCard.layoutParams.apply { height = dp(178) }
        binding.gradientProgress.layoutParams = binding.gradientProgress.layoutParams.apply { width = dp(78); height = dp(78) }
        binding.syncNowButton.layoutParams = binding.syncNowButton.layoutParams.apply { height = dp(38) }
        binding.syncNowButton.cornerRadius = dp(19)
        binding.syncNowButton.setTextSize(10f)

        // Keep cloud accounts horizontally browsable but substantially lighter.
        binding.cloudAccountsScroll.layoutParams = binding.cloudAccountsScroll.layoutParams.apply { height = dp(136) }
        for (i in 0 until binding.cloudProviderRow.childCount) {
            val card = binding.cloudProviderRow.getChildAt(i)
            card.layoutParams = card.layoutParams.apply {
                height = dp(130)
                width = dp(138)
            }
            (card as? MaterialCardView)?.radius = dp(16).toFloat()
            findButtons(card).forEach { it.minHeight = dp(26); it.cornerRadius = dp(13) }
        }

        // Slightly smaller secondary typography keeps the dashboard information-dense.
        binding.syncStatusText.setTextSize(14f)
        binding.syncSubtitle.setTextSize(9f)
        binding.lastSyncText.setTextSize(8f)
        binding.cloudStorageSubtitle.setTextSize(11f)
        binding.cloudSwipeHint.setTextSize(9f)

        // Avoid accidental oversized child layouts inherited from older versions.
        density.hashCode() // keep density local for the layout pass above
    }

    private fun applyAppearance() {
        val mode = prefs.getString("mode", "system") ?: "system"
        val dark = when (mode) {
            "dark" -> true
            "light" -> false
            else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
        val bg = if (dark) Color.rgb(7, 11, 20) else Color.rgb(247, 249, 253)
        val surface = if (dark) Color.rgb(14, 21, 34) else Color.WHITE
        val outline = if (dark) Color.rgb(38, 51, 73) else Color.rgb(225, 229, 237)
        val text = if (dark) Color.WHITE else Color.rgb(31, 38, 54)
        val secondary = if (dark) Color.rgb(154, 167, 188) else Color.rgb(103, 113, 132)

        binding.root.setBackgroundColor(bg)
        binding.mainScrollView.setBackgroundColor(bg)
        binding.toolbar.setTitleTextColor(text)
        binding.cloudStorageSubtitle.setTextColor(text)
        binding.cloudSwipeHint.setTextColor(secondary)
        binding.syncStatusText.setTextColor(text)
        binding.syncSubtitle.setTextColor(secondary)
        binding.lastSyncText.setTextColor(secondary)

        listOf(binding.syncStatusCard, binding.foldersCard, binding.syncSetupCard).forEach { styleCard(it, surface, outline) }
        for (i in 0 until binding.cloudProviderRow.childCount) {
            (binding.cloudProviderRow.getChildAt(i) as? MaterialCardView)?.let { styleCard(it, surface, outline) }
        }

        binding.premiumBanner.background = gradient(
            if (dark) intArrayOf(Color.rgb(54, 39, 123), Color.rgb(30, 104, 172))
            else intArrayOf(Color.rgb(80, 58, 180), Color.rgb(37, 139, 205)), 20f
        )

        listOf(binding.lightButton, binding.systemButton, binding.darkButton).forEach { button ->
            val selected = when (mode) {
                "light" -> button == binding.lightButton
                "dark" -> button == binding.darkButton
                else -> button == binding.systemButton
            }
            button.background = if (selected) gradient(intArrayOf(Color.rgb(112, 74, 235), Color.rgb(43, 177, 232)), 50f)
            else solid(Color.TRANSPARENT, 50f)
            button.setTextColor(if (selected) Color.WHITE else secondary)
        }

        binding.bottomNav.setBackgroundColor(surface)
        val drawerColors = intArrayOf(0xFF6C3FEA.toInt(), 0xFF4D8DFF.toInt(), 0xFF2DC9A3.toInt(), 0xFF38A6F2.toInt(), 0xFFFF9F43.toInt(), 0xFFFFC83D.toInt(), 0xFF8B5CF6.toInt(), 0xFF2AB7C9.toInt(), 0xFF7C4DFF.toInt(), 0xFFFFC83D.toInt())
        for (i in 0 until binding.navigationView.menu.size()) binding.navigationView.menu.getItem(i).icon?.let { DrawableCompat.setTint(it, drawerColors[i % drawerColors.size]) }
        val bottomColors = intArrayOf(0xFF7C4DFF.toInt(), 0xFF3F83F8.toInt(), 0xFF22B8CF.toInt(), 0xFF6875F5.toInt())
        for (i in 0 until binding.bottomNav.menu.size()) binding.bottomNav.menu.getItem(i).icon?.let { DrawableCompat.setTint(it, bottomColors[i % bottomColors.size]) }
    }

    private fun refreshDashboard() {
        val connected = appPrefs.getStringSet("connected_cloud_providers", emptySet()) ?: emptySet()
        val google = connected.contains("Google Drive")
        val email = appPrefs.getString("google_drive_account_email", "") ?: ""
        val last = SyncHistoryManager.get(this).firstOrNull()
        binding.syncStatusText.text = "Ready to sync"
        if (last == null) {
            binding.lastSyncText.text = "Last sync: Never"
            binding.syncSubtitle.text = if (google) "Google Drive connected • Ready to sync" else "Connect a cloud account to begin"
        } else {
            binding.lastSyncText.text = "Last sync: ${SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(last.timestamp))}"
            binding.syncSubtitle.text = "${last.filesChanged} changed • ↑${last.uploadedFiles} ↓${last.downloadedFiles}"
        }
        if (google) executor.execute {
            try {
                val quota = DriveClient(this).quotaText()
                runOnUiThread {
                    if (!isFinishing) {
                        val card = binding.cloudProviderRow.getChildAt(0) as? ViewGroup
                        val texts = mutableListOf<TextView>()
                        if (card != null) collectText(card, texts)
                        texts.firstOrNull { it.text.toString() != "Google Drive" }?.text = "${if (email.isBlank()) "Connected" else email}\n$quota"
                    }
                }
            } catch (_: Exception) { }
        }
    }

    private fun collectText(parent: ViewGroup, out: MutableList<TextView>) {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child is TextView && child !is MaterialButton) out += child
            if (child is ViewGroup) collectText(child, out)
        }
    }

    private fun openCloud(provider: String) {
        appPrefs.edit().putString("selected_cloud_provider", provider).apply()
        startActivity(Intent(this, CloudAccountsActivity::class.java))
    }

    private fun styleCard(card: MaterialCardView, surface: Int, outline: Int) {
        card.setCardBackgroundColor(surface)
        card.strokeColor = outline
        card.strokeWidth = dp(1)
        card.cardElevation = 0f
        card.radius = dp(18).toFloat()
    }

    private fun gradient(colors: IntArray, radius: Float) = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors).apply { cornerRadius = radius * resources.displayMetrics.density }
    private fun solid(color: Int, radius: Float) = GradientDrawable().apply { setColor(color); cornerRadius = radius * resources.displayMetrics.density }
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() { executor.shutdownNow(); super.onDestroy() }
}
