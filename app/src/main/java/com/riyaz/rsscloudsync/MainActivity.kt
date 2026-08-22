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
import androidx.core.view.WindowCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
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
        setupCloudCards()
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
        binding.lightButton.setOnClickListener {
            setMode("light", AppCompatDelegate.MODE_NIGHT_NO)
        }
        binding.systemButton.setOnClickListener {
            setMode("system", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
        binding.darkButton.setOnClickListener {
            setMode("dark", AppCompatDelegate.MODE_NIGHT_YES)
        }
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

        val background = if (dark) Color.rgb(11, 14, 24) else Color.rgb(247, 249, 255)
        val surface = if (dark) Color.rgb(22, 25, 37) else Color.WHITE
        val text = if (dark) Color.rgb(245, 245, 250) else Color.rgb(24, 22, 36)
        val secondary = if (dark) Color.rgb(178, 177, 194) else Color.rgb(105, 101, 120)
        val outline = if (dark) Color.rgb(54, 57, 72) else Color.rgb(226, 222, 234)

        binding.root.setBackgroundColor(background)
        binding.mainScrollView.setBackgroundColor(background)
        binding.toolbar.setTitleTextColor(text)
        binding.cloudStorageSubtitle.setTextColor(text)
        binding.cloudSwipeHint.setTextColor(secondary)
        binding.syncStatusText.setTextColor(text)
        binding.syncSubtitle.setTextColor(secondary)
        binding.lastSyncText.setTextColor(secondary)

        binding.syncStatusCard.apply {
            setCardBackgroundColor(surface)
            strokeColor = outline
            cardElevation = 0f
        }

        for (i in 0 until binding.cloudProviderRow.childCount) {
            val card = binding.cloudProviderRow.getChildAt(i) as? MaterialCardView ?: continue
            card.setCardBackgroundColor(surface)
            card.strokeColor = outline
            card.cardElevation = 0f
        }

        binding.foldersCard.apply {
            setCardBackgroundColor(surface)
            strokeColor = outline
            cardElevation = 0f
        }
        binding.syncSetupCard.apply {
            setCardBackgroundColor(surface)
            strokeColor = outline
            cardElevation = 0f
        }

        binding.premiumBanner.background = gradient(
            if (dark) {
                intArrayOf(Color.rgb(39, 14, 94), Color.rgb(93, 28, 174))
            } else {
                intArrayOf(Color.rgb(91, 34, 217), Color.rgb(116, 57, 226))
            },
            26f
        )

        val selectedText = Color.WHITE
        val unselectedText = if (dark) Color.rgb(215, 213, 225) else Color.rgb(34, 31, 46)

        val appearanceButtons = listOf(
            binding.lightButton,
            binding.systemButton,
            binding.darkButton
        )

        appearanceButtons.forEach { button ->
            val selected = when (mode) {
                "light" -> button == binding.lightButton
                "dark" -> button == binding.darkButton
                else -> button == binding.systemButton
            }
            button.background = if (selected) {
                gradient(intArrayOf(Color.rgb(122, 73, 235), Color.rgb(216, 70, 174)), 50f)
            } else {
                solid(Color.TRANSPARENT, 50f)
            }
            button.setTextColor(if (selected) selectedText else unselectedText)
        }
    }

    private fun setupGradientButtons() {
        applyGradient(binding.syncNowButton)
        applyGradient(binding.googleDriveConnectButton)
        applyGradient(binding.oneDriveConnectButton)
        applyGradient(binding.dropboxConnectButton)
    }

    private fun applyGradient(button: MaterialButton) {
        button.background = gradient(
            intArrayOf(Color.rgb(116, 73, 232), Color.rgb(214, 72, 175)),
            60f
        )
        button.setTextColor(Color.WHITE)
    }

    private fun setupCloudCards() {
        val providers = arrayOf("Google Drive", "OneDrive", "Dropbox", "MEGA", "Box", "WebDAV")
        val row = binding.cloudProviderRow

        for (index in 0 until row.childCount) {
            val card = row.getChildAt(index) as? MaterialCardView ?: continue
            val provider = providers.getOrElse(index) { "Cloud" }

            card.isClickable = true
            card.isFocusable = true
            card.setOnClickListener { openCloud(provider) }
            setButtonsInsideCard(card, provider)
        }
    }

    private fun setButtonsInsideCard(parent: ViewGroup, provider: String) {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            when (child) {
                is MaterialButton -> child.setOnClickListener {
                    openCloud(provider)
                }
                is ViewGroup -> setButtonsInsideCard(child, provider)
            }
        }
    }

    private fun setupNavigation() {
        binding.foldersCard.setOnClickListener {
            startActivity(Intent(this, FolderSyncActivity::class.java))
        }
        binding.syncSetupCard.setOnClickListener {
            openAutomaticSync()
        }
        binding.syncNowButton.setOnClickListener {
            binding.syncStatusText.text = "Sync complete"
            binding.syncSubtitle.text = "Everything is up to date"
            binding.lastSyncText.text = "Last sync: Just now"
            binding.gradientProgress.setProgress(100f, true)
        }
    }

    private fun setupDrawer() {
        binding.toolbar.setNavigationOnClickListener {
            binding.drawerLayout.openDrawer(binding.navigationView)
        }

        binding.navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> binding.mainScrollView.smoothScrollTo(0, 0)
                R.id.nav_folders -> startActivity(Intent(this, FolderSyncActivity::class.java))
                R.id.nav_automatic -> openAutomaticSync()
                R.id.nav_cloud -> startActivity(Intent(this, CloudAccountsActivity::class.java))
                R.id.nav_external -> startActivity(Intent(this, FolderSyncActivity::class.java))
                R.id.nav_free -> startActivity(Intent(this, FreeFeaturesActivity::class.java))
                R.id.nav_free_manual -> startActivity(Intent(this, FreeFeaturesActivity::class.java))
                R.id.nav_premium -> startActivity(Intent(this, PremiumActivity::class.java))
                R.id.nav_unlock -> startActivity(Intent(this, PremiumActivity::class.java))
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
                R.id.bottom_home -> {
                    binding.mainScrollView.smoothScrollTo(0, 0)
                    true
                }
                R.id.bottom_sync -> {
                    startActivity(Intent(this, FolderSyncActivity::class.java))
                    true
                }
                R.id.bottom_cloud -> {
                    startActivity(Intent(this, CloudAccountsActivity::class.java))
                    true
                }
                R.id.bottom_premium -> {
                    startActivity(Intent(this, PremiumActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun openAutomaticSync() {
        if (appPrefs.getBoolean("premium_unlocked", false)) {
            startActivity(Intent(this, SyncSetupActivity::class.java))
        } else {
            startActivity(Intent(this, PremiumActivity::class.java))
        }
    }

    private fun openCloud(provider: String) {
        appPrefs.edit().putString("selected_cloud_provider", provider).apply()
        startActivity(Intent(this, CloudAccountsActivity::class.java))
    }

    private fun gradient(colors: IntArray, radius: Float): GradientDrawable {
        return GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors).apply {
            cornerRadius = radius
        }
    }

    private fun solid(color: Int, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
        }
    }
}