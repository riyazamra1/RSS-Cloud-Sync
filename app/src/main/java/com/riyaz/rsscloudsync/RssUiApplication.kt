package com.riyaz.rsscloudsync

import android.app.Activity
import android.app.Application
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.navigation.NavigationView

/** Existing dashboard polish layer. Keeps the current project intact. */
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
        fun id(name: String) = activity.resources.getIdentifier(name, "id", activity.packageName)
        val light = (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES
        val surface = if (light) Color.WHITE else Color.rgb(15, 22, 36)
        val background = if (light) Color.rgb(247, 248, 252) else Color.rgb(7, 11, 20)
        val outline = if (light) Color.rgb(225, 228, 236) else Color.rgb(38, 51, 73)

        root.findViewById<ViewGroup>(id("mainScrollView"))?.setBackgroundColor(background)
        root.findViewById<NavigationView>(id("navigationView"))?.apply {
            setBackgroundColor(surface)
            elevation = 0f
            itemIconTintList = null
            menu.setGroupDividerEnabled(true)
            setItemVerticalPadding(dp(3))
            setItemHorizontalPadding(dp(10))
            setItemIconPadding(dp(9))
            setItemIconSize(dp(22))
        }
        root.findViewById<BottomNavigationView>(id("bottomNav"))?.apply { itemIconTintList = null }
        root.findViewById<ViewGroup>(id("appearanceCard"))?.let { selector ->
            selector.background = GradientDrawable().apply { cornerRadius = dp(22).toFloat(); setColor(surface); setStroke(dp(1), outline) }
            selector.layoutParams = selector.layoutParams.apply { height = dp(48) }
            selector.requestLayout()
        }

        setHeight<MaterialCardView>(root, activity, "premiumBanner", 184)
        setHeight<MaterialCardView>(root, activity, "syncStatusCard", 204)
        setHeight<MaterialCardView>(root, activity, "foldersCard", 88)
        setHeight<MaterialCardView>(root, activity, "syncSetupCard", 88)
        setHeight<ViewGroup>(root, activity, "cloudAccountsScroll", 158)
        setHeight<BottomNavigationView>(root, activity, "bottomNav", 66)

        // Tighten only the large section gaps. Keep internal card spacing intact.
        root.findViewById<ViewGroup>(id("contentLayout"))?.let { content ->
            for (i in 0 until content.childCount) {
                val child = content.getChildAt(i)
                val lp = child.layoutParams
                if (lp is ViewGroup.MarginLayoutParams && lp.topMargin > dp(10)) {
                    lp.topMargin = dp(8)
                    child.layoutParams = lp
                }
            }
        }

        root.findViewById<ViewGroup>(id("cloudProviderRow"))?.let { row ->
            for (i in 0 until row.childCount) {
                row.getChildAt(i).layoutParams = row.getChildAt(i).layoutParams.apply { width = dp(142); height = dp(154) }
                row.getChildAt(i).requestLayout()
            }
        }
        root.findViewById<MaterialButton>(id("syncNowButton"))?.let {
            it.layoutParams = it.layoutParams.apply { width = ViewGroup.LayoutParams.MATCH_PARENT; height = dp(44) }
            it.requestLayout()
        }

        root.findViewById<Toolbar>(id("toolbar"))?.let { toolbar ->
            if (toolbar.menu.findItem(R.id.action_notifications) == null) {
                toolbar.inflateMenu(R.menu.main_toolbar_menu)
                toolbar.setOnMenuItemClickListener { item: MenuItem ->
                    if (item.itemId == R.id.action_notifications) {
                        activity.startActivity(android.content.Intent(activity, NotificationsActivity::class.java)); true
                    } else false
                }
            }
        }
    }

    private inline fun <reified T : ViewGroup> setHeight(root: View, activity: Activity, idName: String, heightDp: Int) {
        root.findViewById<T>(activity.resources.getIdentifier(idName, "id", activity.packageName))?.let { view ->
            view.layoutParams = view.layoutParams.apply { height = (heightDp * activity.resources.displayMetrics.density).toInt() }
            view.requestLayout()
        }
    }
}
