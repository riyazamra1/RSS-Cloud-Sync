package com.riyaz.rsscloudsync

import android.app.Activity
import android.app.Application
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.navigation.NavigationView
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.util.concurrent.Executors

/** Dashboard polish and lightweight cloud-account presentation layer. */
class RssUiApplication : Application() {
    private lateinit var syncPrefs: SharedPreferences
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate() {
        super.onCreate()
        syncPrefs = getSharedPreferences("rss_cloud_sync", MODE_PRIVATE)
        ScheduledSyncWorker.ensureScheduledForSavedPairs(this)
        syncPrefs.registerOnSharedPreferenceChangeListener { prefs, key ->
            if (key == "schedule_mode" || key == "active_pair_id" || key == "folder_pair_enabled") {
                val pairId = prefs.getString("active_pair_id", null)
                if (pairId != null && prefs.getString("schedule_mode", "Save only") == "Schedule now" && prefs.getBoolean("folder_pair_enabled", true)) {
                    ScheduledSyncWorker.schedule(this, pairId)
                } else if (pairId != null) {
                    ScheduledSyncWorker.cancel(this, pairId)
                }
            }
        }
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityPostCreated(activity: Activity, state: Bundle?) {
                if (activity is MainActivity) activity.window.decorView.post {
                    polishDashboard(activity)
                    buildCloudGrid(activity)
                }
            }
            override fun onActivityCreated(a: Activity, s: Bundle?) = Unit
            override fun onActivityStarted(a: Activity) = Unit
            override fun onActivityResumed(a: Activity) = Unit
            override fun onActivityPaused(a: Activity) = Unit
            override fun onActivityStopped(a: Activity) = Unit
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) = Unit
            override fun onActivityDestroyed(a: Activity) = Unit
        })
    }

    private fun polishDashboard(activity: MainActivity) {
        val root = activity.window.decorView
        val density = activity.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        fun id(name: String) = activity.resources.getIdentifier(name, "id", activity.packageName)
        val light = (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES
        val surface = if (light) Color.WHITE else Color.rgb(15, 22, 36)
        val background = if (light) Color.rgb(247, 248, 252) else Color.rgb(7, 11, 20)
        val outline = if (light) Color.rgb(225, 228, 236) else Color.rgb(38, 51, 73)

        root.findViewById<ViewGroup>(id("mainScrollView"))?.setBackgroundColor(background)
        root.findViewById<NavigationView>(id("navigationView"))?.apply {
            setBackgroundColor(surface); elevation = 0f; itemIconTintList = null
            menu.setGroupDividerEnabled(true); setItemVerticalPadding(dp(3)); setItemHorizontalPadding(dp(10)); setItemIconPadding(dp(9)); setItemIconSize(dp(22))
        }
        root.findViewById<BottomNavigationView>(id("bottomNav"))?.apply { itemIconTintList = null }
        root.findViewById<ViewGroup>(id("appearanceCard"))?.let { selector ->
            selector.background = GradientDrawable().apply { cornerRadius = dp(22).toFloat(); setColor(surface); setStroke(dp(1), outline) }
            selector.layoutParams = selector.layoutParams.apply { height = dp(48) }; selector.requestLayout()
        }
        setHeight<MaterialCardView>(root, activity, "premiumBanner", 184)
        setHeight<MaterialCardView>(root, activity, "syncStatusCard", 204)
        setHeight<MaterialCardView>(root, activity, "foldersCard", 88)
        setHeight<MaterialCardView>(root, activity, "syncSetupCard", 88)
        setHeight<BottomNavigationView>(root, activity, "bottomNav", 66)
        root.findViewById<ViewGroup>(id("contentLayout"))?.let { content ->
            for (i in 0 until content.childCount) {
                val child = content.getChildAt(i); val lp = child.layoutParams
                if (lp is ViewGroup.MarginLayoutParams && lp.topMargin > dp(10)) { lp.topMargin = dp(8); child.layoutParams = lp }
            }
        }
        root.findViewById<MaterialButton>(id("syncNowButton"))?.let { it.layoutParams = it.layoutParams.apply { width = ViewGroup.LayoutParams.MATCH_PARENT; height = dp(44) }; it.requestLayout() }
        root.findViewById<Toolbar>(id("toolbar"))?.let { toolbar ->
            if (toolbar.menu.findItem(R.id.action_notifications) == null) {
                toolbar.inflateMenu(R.menu.main_toolbar_menu)
                toolbar.setOnMenuItemClickListener { item: MenuItem ->
                    if (item.itemId == R.id.action_notifications) { activity.startActivity(android.content.Intent(activity, NotificationsActivity::class.java)); true } else false
                }
            }
        }
    }

    private fun buildCloudGrid(activity: MainActivity) {
        val root = activity.window.decorView
        fun id(name: String) = activity.resources.getIdentifier(name, "id", activity.packageName)
        val scroll = root.findViewById<ViewGroup>(id("cloudAccountsScroll")) ?: return
        if (scroll.getTag(id("cloudAccountsScroll")) == "grid-ready") return
        val row = root.findViewById<ViewGroup>(id("cloudProviderRow")) ?: return
        val parent = scroll.parent as? ViewGroup ?: return
        val cards = (0 until row.childCount).map { row.getChildAt(it) }
        if (cards.isEmpty()) return
        val density = activity.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val screen = activity.resources.displayMetrics.widthPixels
        val cellWidth = ((screen - dp(44)) / 2).coerceAtLeast(dp(120))
        val providers = listOf("Google Drive", "OneDrive", "Dropbox", "MEGA", "Box", "WebDAV")
        val grid = GridLayout(activity).apply { columnCount = 2; useDefaultMargins = false; alignmentMode = GridLayout.ALIGN_BOUNDS }
        cards.forEachIndexed { index, card ->
            row.removeView(card)
            card.layoutParams = GridLayout.LayoutParams().apply {
                width = cellWidth; height = dp(138); setMargins(dp(2), dp(2), dp(2), dp(6))
                rowSpec = GridLayout.spec(index / 2); columnSpec = GridLayout.spec(index % 2)
            }
            (card as? MaterialCardView)?.let { it.radius = dp(18).toFloat(); it.cardElevation = dp(1).toFloat() }
            val inner = (card as? ViewGroup)?.getChildAt(0) as? ViewGroup
            inner?.let {
                it.setPadding(dp(8), dp(8), dp(8), dp(8))
                if (it.findViewWithTag<LinearProgressIndicator>("rss-cloud-progress") == null) {
                    val progress = LinearProgressIndicator(activity).apply {
                        tag = "rss-cloud-progress"; max = 100; progress = 0; isIndeterminate = false
                        trackThickness = dp(4); setTrackCornerRadius(dp(4))
                    }
                    val buttonIndex = (0 until it.childCount).firstOrNull { n -> it.getChildAt(n) is MaterialButton } ?: it.childCount
                    it.addView(progress, buttonIndex, LinearLayout.LayoutParams(-1, dp(4)).apply { topMargin = dp(4) })
                }
                findProviderStatus(it)?.let { status -> status.setTextSize(8f) }
            }
            grid.addView(card)
            updateCardState(activity, card, providers.getOrElse(index) { "Cloud" })
        }
        val index = parent.indexOfChild(scroll)
        parent.removeView(scroll)
        parent.addView(grid, index, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
        scroll.setTag(id("cloudAccountsScroll"), "grid-ready")
        loadGoogleDashboardQuota(activity, grid, providers)
    }

    private fun updateCardState(activity: Activity, card: View, provider: String) {
        val prefs = activity.getSharedPreferences("rss_cloud_sync", MODE_PRIVATE)
        val connected = prefs.getStringSet("connected_cloud_providers", emptySet())?.contains(provider) == true
        val inner = card as? ViewGroup ?: return
        val status = findProviderStatus(inner) ?: return
        status.text = if (connected) "Connected" else "Not connected"
        val progress = inner.findViewWithTag<LinearProgressIndicator>("rss-cloud-progress")
        progress?.setProgressCompat(0, false)
    }

    private fun loadGoogleDashboardQuota(activity: MainActivity, grid: GridLayout, providers: List<String>) {
        val connected = activity.getSharedPreferences("rss_cloud_sync", MODE_PRIVATE).getStringSet("connected_cloud_providers", emptySet())?.contains("Google Drive") == true
        if (!connected) return
        executor.execute {
            try {
                val (used, limit, _) = DriveClient(activity).quota()
                val percent = if (limit > 0L) ((used.toDouble() / limit.toDouble()) * 100.0).coerceIn(0.0, 100.0).toInt() else 0
                activity.getSharedPreferences("rss_cloud_sync", MODE_PRIVATE).edit().putLong("google_quota_used", used).putLong("google_quota_limit", limit).apply()
                activity.runOnUiThread {
                    if (activity.isFinishing) return@runOnUiThread
                    val card = grid.getChildAt(0) as? ViewGroup ?: return@runOnUiThread
                    val progress = card.findViewWithTag<LinearProgressIndicator>("rss-cloud-progress")
                    progress?.setProgressCompat(percent, true)
                    findProviderStatus(card)?.text = "${formatBytes(used)} used • ${if (limit > 0) formatBytes(limit) else "Unlimited"}"
                }
            } catch (_: Exception) { }
        }
    }

    private fun findProviderStatus(parent: ViewGroup): TextView? {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child is TextView && child !is MaterialButton && child.text.toString() in listOf("Not connected", "Connected", "Ready to connect")) return child
            if (child is ViewGroup) findProviderStatus(child)?.let { return it }
        }
        return null
    }

    private fun formatBytes(value: Long): String {
        if (value < 1024L) return "$value B"
        if (value < 1024L * 1024L) return String.format(java.util.Locale.getDefault(), "%.2f KB", value / 1024.0)
        if (value < 1024L * 1024L * 1024L) return String.format(java.util.Locale.getDefault(), "%.2f MB", value / (1024.0 * 1024.0))
        if (value < 1024L * 1024L * 1024L * 1024L) return String.format(java.util.Locale.getDefault(), "%.2f GB", value / (1024.0 * 1024.0 * 1024.0))
        return String.format(java.util.Locale.getDefault(), "%.2f TB", value / (1024.0 * 1024.0 * 1024.0 * 1024.0))
    }

    private inline fun <reified T : ViewGroup> setHeight(root: View, activity: Activity, idName: String, heightDp: Int) {
        root.findViewById<T>(activity.resources.getIdentifier(idName, "id", activity.packageName))?.let { view ->
            view.layoutParams = view.layoutParams.apply { height = (heightDp * activity.resources.displayMetrics.density).toInt() }; view.requestLayout()
        }
    }

    override fun onTerminate() { executor.shutdownNow(); super.onTerminate() }
}