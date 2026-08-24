package com.riyaz.rsscloudsync

import android.app.Activity
import android.app.Application
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.ViewGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.button.MaterialButton

class RssUiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityPostCreated(activity: Activity, state: Bundle?) { if (activity is MainActivity) activity.window.decorView.post { polishDashboard(activity) } }
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
        val surface = if (light) Color.rgb(249, 251, 255) else Color.rgb(15, 22, 36)
        val outline = if (light) Color.rgb(225, 230, 238) else Color.rgb(38, 51, 73)

        root.findViewById<ViewGroup>(activity.resources.getIdentifier("appearanceCard", "id", activity.packageName))?.let { v ->
            v.background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(surface); setStroke(dp(1), outline) }
            v.layoutParams = v.layoutParams.apply { height = dp(42) }
        }
        setHeight<MaterialCardView>(root, activity, "premiumBanner", 118)
        setHeight<MaterialCardView>(root, activity, "syncStatusCard", 112)
        setHeight<MaterialCardView>(root, activity, "foldersCard", 96)
        setHeight<MaterialCardView>(root, activity, "syncSetupCard", 96)
        setHeight<ViewGroup>(root, activity, "cloudAccountsScroll", 122)

        root.findViewById<ViewGroup>(activity.resources.getIdentifier("cloudProviderRow", "id", activity.packageName))?.let { row ->
            for (i in 0 until row.childCount) {
                row.getChildAt(i).layoutParams = row.getChildAt(i).layoutParams.apply { width = dp(158); height = dp(114) }
                row.getChildAt(i).requestLayout()
            }
        }
        root.findViewById<MaterialButton>(activity.resources.getIdentifier("syncNowButton", "id", activity.packageName))?.let {
            it.layoutParams = it.layoutParams.apply { width = dp(170); height = dp(44) }
            it.requestLayout()
        }
    }

    private inline fun <reified T : ViewGroup> setHeight(root: android.view.View, activity: Activity, idName: String, heightDp: Int) {
        root.findViewById<T>(activity.resources.getIdentifier(idName, "id", activity.packageName))?.let { view ->
            view.layoutParams = view.layoutParams.apply { height = (heightDp * activity.resources.displayMetrics.density).toInt() }
            view.requestLayout()
        }
    }
}
