package com.riyaz.rsscloudsync

import android.app.Activity
import android.app.Application
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.navigation.NavigationView

/**
 * Final dashboard polish layer. It deliberately stays small and only adjusts
 * presentation after the existing MainActivity layout has been inflated.
 */
class RssUiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityPostCreated(activity: Activity, state: Bundle?) {
                if (activity is MainActivity) {
                    activity.window.decorView.post { polishDashboard(activity) }
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
        val surface = if (light) Color.rgb(255, 255, 255) else Color.rgb(15, 22, 36)
        val background = if (light) Color.rgb(247, 248, 252) else Color.rgb(7, 11, 20)
        val outline = if (light) Color.rgb(225, 228, 236) else Color.rgb(38, 51, 73)

        root.findViewById<ViewGroup>(id("mainScrollView"))?.setBackgroundColor(background)
        root.findViewById<ViewGroup>(id("appearanceCard"))?.let { selector ->
            selector.background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(surface)
                setStroke(dp(1), outline)
            }
            selector.layoutParams = selector.layoutParams.apply { height = dp(48) }
            selector.requestLayout()
        }

        // Keep the reference proportions. The old polish layer was shrinking
        // these cards so aggressively that the dashboard looked broken.
        setHeight<MaterialCardView>(root, activity, "premiumBanner", 190)
        setHeight<MaterialCardView>(root, activity, "syncStatusCard", 214)
        setHeight<MaterialCardView>(root, activity, "foldersCard", 94)
        setHeight<MaterialCardView>(root, activity, "syncSetupCard", 94)
        setHeight<ViewGroup>(root, activity, "cloudAccountsScroll", 166)
        setHeight<BottomNavigationView>(root, activity, "bottomNav", 70)

        root.findViewById<ViewGroup>(id("cloudProviderRow"))?.let { row ->
            for (i in 0 until row.childCount) {
                row.getChildAt(i).layoutParams = row.getChildAt(i).layoutParams.apply {
                    width = dp(142)
                    height = dp(158)
                }
                row.getChildAt(i).requestLayout()
            }
        }

        root.findViewById<MaterialButton>(id("syncNowButton"))?.let {
            it.layoutParams = it.layoutParams.apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = dp(44)
            }
            it.requestLayout()
        }

        // Material NavigationView and BottomNavigationView apply a default
        // monochrome tint unless explicitly disabled. Keep our existing
        // per-item colorful RSS icons intact.
        root.findViewById<NavigationView>(id("navigationView"))?.apply {
            itemIconTintList = null
            menu.setGroupDividerEnabled(true)
        }
        root.findViewById<BottomNavigationView>(id("bottomNav"))?.apply {
            itemIconTintList = null
        }
    }

    private inline fun <reified T : ViewGroup> setHeight(
        root: View,
        activity: Activity,
        idName: String,
        heightDp: Int
    ) {
        root.findViewById<T>(activity.resources.getIdentifier(idName, "id", activity.packageName))?.let { view ->
            view.layoutParams = view.layoutParams.apply {
                height = (heightDp * activity.resources.displayMetrics.density).toInt()
            }
            view.requestLayout()
        }
    }
}
