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
        compactDashboard()
        refreshDashboard()
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) {
            applyAppearance()
            compactDashboard()
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
        button.background = gradient(intArrayOf(Color.rgb(124, 61, 237), Color.rgb(38, 181, 235)), 50f)
        button.setTextColor(Color.WHITE)
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

    private fun compactDashboard() {
        if (!::binding.isInitialized) return
        val density = resources.displayMetrics.density
        val compactTop = (6 * density).toInt()
        for (i in 1 until binding.contentLayout.childCount) {
            val child = binding.contentLayout.getChildAt(i)
            val lp = child.layoutParams as? ViewGroup.MarginLayoutParams ?: continue
            if (lp.topMargin > compactTop) {
                lp.topMargin = when (i) {
                    1 -> 0
                    else -> compactTop
                }
                child.layoutParams = lp
            }
        }
    }

    private fun applyAppearance() {
        val mode = prefs.getString("mode", "system") ?: "system"
        val dark = when (mode) {
            "dark" -> true
            "light" -> false
            else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
        val bg = if (dark) Color.rgb(7, 11, 20) else Color.rgb(247, 248, 252)
        val surface = if (dark) Color.rgb(15, 22, 36) else Color.WHITE
        val outline = if (dark) Color.rgb(38, 51, 73) else Color.rgb(225, 228, 236)
        val text = if (dark) Color.WHITE else Color.rgb(37, 43, 58)
        val secondary = if (dark) Color.rgb(154, 167, 188) else Color.rgb(111, 119, 137)
        binding.root.setBackgroundColor(bg)
        binding.mainScrollView.setBackgroundColor(bg)
        binding.toolbar.setTitleTextColor(text)
        binding.cloudStorageSubtitle.setTextColor(text)
        binding.cloudSwipeHint.setTextColor(secondary)
        binding.syncStatusText.setTextColor(text)
        binding.syncSubtitle.setTextColor(secondary)
        binding.lastSyncText.setTextColor(secondary)
        listOf(binding.syncStatusCard, binding.foldersCard, binding.syncSetupCard).forEach { styleCard(it, surface, outline) }
        for (i in 0 until binding.cloudProviderRow.childCount) (binding.cloudProviderRow.getChildAt(i) as? MaterialCardView)?.let { styleCard(it, surface, outline) }
        binding.premiumBanner.background = gradient(intArrayOf(Color.rgb(72, 39, 177), Color.rgb(39, 119, 225)), 22f)
        listOf(binding.lightButton, binding.systemButton, binding.darkButton).forEach { button ->
            val selected = when (mode) { "light" -> button == binding.lightButton; "dark" -> button == binding.darkButton; else -> button == binding.systemButton }
            button.background = if (selected) gradient(intArrayOf(Color.rgb(125, 49, 235), Color.rgb(39, 190, 235)), 50f) else solid(Color.TRANSPARENT, 50f)
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
        card.strokeWidth = 1
        card.cardElevation = 0f
        card.radius = 20f * resources.displayMetrics.density
    }

    private fun gradient(colors: IntArray, radius: Float) = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors).apply { cornerRadius = radius * resources.displayMetrics.density }
    private fun solid(color: Int, radius: Float) = GradientDrawable().apply { setColor(color); cornerRadius = radius * resources.displayMetrics.density }
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() { executor.shutdownNow(); super.onDestroy() }
}
