package com.riyaz.rsscloudsync

import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.riyaz.rsscloudsync.databinding.ActivityMainBinding

private var mainUiPolished = false

val ActivityMainBinding.upgradeBannerButton: TextView
    get() {
        val button = root.findViewById<TextView>(R.id.upgradeBannerButton)
        if (!mainUiPolished) {
            mainUiPolished = true
            button.isClickable = true
            button.isFocusable = true
            button.setOnClickListener {
                root.context.startActivity(android.content.Intent(root.context, PremiumActivity::class.java))
            }

            // Compact the dashboard vertically without changing the card proportions.
            listOf(syncStatusCard, foldersCard, syncSetupCard).forEach { card ->
                (card.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                    it.topMargin = dp(root, 6)
                    card.layoutParams = it
                }
            }
            (cloudAccountsScroll.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                it.topMargin = dp(root, 4)
                cloudAccountsScroll.layoutParams = it
            }
            (cloudStorageSubtitle.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                it.topMargin = dp(root, 7)
                cloudStorageSubtitle.layoutParams = it
            }

            // Keep the drawer narrow, but give the branded logo more presence.
            navigationView.layoutParams = navigationView.layoutParams.apply { width = dp(root, 250) }
            if (navigationView.headerCount > 0) {
                val header = navigationView.getHeaderView(0)
                header.layoutParams = header.layoutParams.apply { height = dp(root, 128) }
                if (header is ViewGroup) {
                    for (i in 0 until header.childCount) {
                        val child = header.getChildAt(i)
                        if (child is ViewGroup) {
                            child.layoutParams = child.layoutParams.apply { height = dp(root, 108) }
                            for (j in 0 until child.childCount) {
                                val logo = child.getChildAt(j)
                                if (logo is FrameLayout) {
                                    logo.layoutParams = logo.layoutParams.apply {
                                        width = dp(root, 94)
                                        height = dp(root, 94)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return button
    }

private fun dp(view: android.view.View, value: Int): Int =
    (value * view.resources.displayMetrics.density).toInt()
