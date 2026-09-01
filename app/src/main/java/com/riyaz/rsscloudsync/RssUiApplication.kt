package com.riyaz.rsscloudsync

import android.app.Activity
import android.app.Application
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.gridlayout.widget.GridLayout
import com.google.android.material.card.MaterialCardView
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator

class RssUiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
    }

    fun applyCloudAccountsGrid(activity: Activity, root: View) {
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
        parent.addView(grid, index, scroll.layoutParams)
        grid.setTag(id("cloudAccountsScroll"), "grid-ready")
    }

    private fun updateCardState(activity: Activity, card: View, provider: String) {
        // Existing UI state handling is intentionally lightweight; provider-specific logic remains elsewhere.
    }

    private fun findProviderStatus(root: ViewGroup): android.widget.TextView? = null

    private fun id(name: String): Int = resources.getIdentifier(name, "id", packageName)
}
