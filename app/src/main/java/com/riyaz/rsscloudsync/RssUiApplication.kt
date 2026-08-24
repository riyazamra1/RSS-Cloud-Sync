package com.riyaz.rsscloudsync

import android.app.Activity
import android.app.Application
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.ViewGroup
import com.google.android.material.card.MaterialCardView

class RssUiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityPostCreated(activity: Activity, state: Bundle?) {
                if (activity is MainActivity) activity.window.decorView.post { polishDashboard(activity) }
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
        val light = (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES
        val surfaceContainer = if (light) Color.rgb(249, 251, 255) else Color.rgb(15, 22, 36)
        val outline = if (light) Color.rgb(225, 230, 238) else Color.rgb(38, 51, 73)
        val appearance = root.findViewById<ViewGroup>(activity.resources.getIdentifier("appearanceCard", "id", activity.packageName))
        appearance?.setBackgroundColor(Color.TRANSPARENT)
        appearance?.background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(surfaceContainer); setStroke(dp(1), outline) }
        appearance?.layoutParams?.let { it.height = dp(42); appearance.layoutParams = it }

        root.findViewById<MaterialCardView>(activity.resources.getIdentifier("premiumBanner", "id", activity.packageName))?.layoutParams?.let {
            it.height = dp(128); it as ViewGroup.LayoutParams; root.findViewById<MaterialCardView>(activity.resources.getIdentifier("premiumBanner", "id", activity.packageName)).layoutParams = it
        }
        root.findViewById<MaterialCardView>(activity.resources.getIdentifier("syncStatusCard", "id", activity.packageName))?.layoutParams?.let {
            it.height = dp(122); root.findViewById<MaterialCardView>(activity.resources.getIdentifier("syncStatusCard", "id", activity.packageName)).layoutParams = it
        }
        root.findViewById<MaterialCardView>(activity.resources.getIdentifier("foldersCard", "id", activity.packageName))?.layoutParams?.let {
            it.height = dp(104); root.findViewById<MaterialCardView>(activity.resources.getIdentifier("foldersCard", "id", activity.packageName)).layoutParams = it
        }
        root.findViewById<MaterialCardView>(activity.resources.getIdentifier("syncSetupCard", "id", activity.packageName))?.layoutParams?.let {
            it.height = dp(104); root.findViewById<MaterialCardView>(activity.resources.getIdentifier("syncSetupCard", "id", activity.packageName)).layoutParams = it
        }
        root.findViewById<ViewGroup>(activity.resources.getIdentifier("cloudAccountsScroll", "id", activity.packageName))?.layoutParams?.let {
            it.height = dp(126); root.findViewById<ViewGroup>(activity.resources.getIdentifier("cloudAccountsScroll", "id", activity.packageName)).layoutParams = it
        }
        root.findViewById<ViewGroup>(activity.resources.getIdentifier("cloudProviderRow", "id", activity.packageName))?.let { row ->
            for (i in 0 until row.childCount) {
                val child = row.getChildAt(i)
                child.layoutParams = child.layoutParams.apply { width = dp(148); height = dp(118) }
                child.requestLayout()
            }
        }
        root.findViewById<com.google.android.material.button.MaterialButton>(activity.resources.getIdentifier("syncNowButton", "id", activity.packageName))?.let {
            it.layoutParams = it.layoutParams.apply { width = dp(160); height = dp(42) }
            it.requestLayout()
        }
    }
}
