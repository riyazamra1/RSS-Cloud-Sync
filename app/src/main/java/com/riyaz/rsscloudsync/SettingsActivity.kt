package com.riyaz.rsscloudsync

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("appearance", MODE_PRIVATE) }
    private val settings by lazy { getSharedPreferences("rss_settings", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
    }

    private fun buildUi(): View {
        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val bg = if (dark) Color.rgb(5, 9, 17) else Color.rgb(244, 247, 251)
        val surface = if (dark) Color.rgb(13, 20, 33) else Color.WHITE
        val surface2 = if (dark) Color.rgb(17, 27, 43) else Color.rgb(249, 251, 255)
        val primary = if (dark) Color.rgb(248, 250, 255) else Color.rgb(18, 25, 38)
        val secondary = if (dark) Color.rgb(145, 160, 182) else Color.rgb(103, 113, 132)
        val outline = if (dark) Color.rgb(35, 49, 70) else Color.rgb(225, 230, 238)
        val accent = Color.rgb(55, 185, 235)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        val toolbar = MaterialToolbar(this).apply {
            title = "Settings"
            setTitleTextColor(primary)
            setNavigationIcon(android.R.drawable.ic_menu_revert)
            setNavigationIconTint(primary)
            setNavigationOnClickListener { finish() }
            elevation = 0f
            setBackgroundColor(bg)
        }
        root.addView(toolbar, LinearLayout.LayoutParams(-1, dp(56)))

        val scroll = ScrollView(this).apply { clipToPadding = false; isVerticalScrollBarEnabled = false }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(4), dp(16), dp(28)) }

        content.addView(section("APPEARANCE", secondary))
        val appearance = card(surface, outline)
        val modes = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(4), dp(14), dp(4)) }
        addMode(modes, "☀", "Light", "Bright interface", "light", primary, secondary, accent)
        addMode(modes, "◐", "System", "Follow Android appearance", "system", primary, secondary, accent)
        addMode(modes, "◑", "Dark", "Deep cloud workspace", "dark", primary, secondary, accent)
        appearance.addView(modes); content.addView(appearance, marginParams(10))

        content.addView(section("SYNC & AUTOMATION", secondary))
        val sync = card(surface, outline)
        val syncBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(4), dp(14), dp(4)) }
        addSwitch(syncBox, "Automatic sync", "Keep selected folders synchronized in background", "automatic", true, primary, secondary, accent)
        addSwitch(syncBox, "Mobile data", "Allow synchronization without Wi-Fi", "mobile_data", true, primary, secondary, accent)
        addSwitch(syncBox, "Sync notifications", "Show completion and error notifications", "notifications", true, primary, secondary, accent)
        addSwitch(syncBox, "Sync on app start", "Check for changes when RSS CLOUD SYNC opens", "sync_start", true, primary, secondary, accent)
        sync.addView(syncBox); content.addView(sync, marginParams(10))

        content.addView(section("CLOUD & FILES", secondary))
        val cloud = card(surface, outline)
        val cloudBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(4), dp(14), dp(4)) }
        addInfoRow(cloudBox, "☁", "Cloud accounts", "Manage connected providers", primary, secondary, accent) { startActivity(android.content.Intent(this, CloudAccountsActivity::class.java)) }
        addInfoRow(cloudBox, "▣", "Default sync folder", "Choose the local folder", primary, secondary, accent) { startActivity(android.content.Intent(this, FolderSyncActivity::class.java)) }
        addInfoRow(cloudBox, "◷", "Sync history", "View recent sync results", primary, secondary, accent) { startActivity(android.content.Intent(this, HistoryActivity::class.java)) }
        cloud.addView(cloudBox); content.addView(cloud, marginParams(10))

        content.addView(section("APP & SUPPORT", secondary))
        val app = card(surface, outline)
        val appBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(4), dp(14), dp(4)) }
        addInfoRow(appBox, "ⓘ", "About RSS CLOUD SYNC", "Version and app information", primary, secondary, accent) { startActivity(android.content.Intent(this, AboutActivity::class.java)) }
        addInfoRow(appBox, "⌁", "Privacy & permissions", "View permissions and privacy information", primary, secondary, accent) { showPrivacy() }
        addInfoRow(appBox, "?", "Help & support", "Contact Razeen Secure Solution", primary, secondary, accent) { startActivity(android.content.Intent(this, ContactActivity::class.java)) }
        app.addView(appBox); content.addView(app)

        scroll.addView(content); root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f)); return root
    }

    private fun section(title: String, color: Int) = TextView(this).apply { text = title; textSize = 10f; letterSpacing = .14f; setTextColor(color); setTypeface(typeface, Typeface.BOLD); setPadding(dp(4), dp(8), dp(4), dp(6)) }

    private fun card(color: Int, outline: Int) = MaterialCardView(this).apply { setCardBackgroundColor(color); radius = dp(20).toFloat(); cardElevation = 0f; strokeWidth = dp(1); strokeColor = outline }

    private fun addMode(parent: LinearLayout, icon: String, title: String, sub: String, mode: String, primary: Int, secondary: Int, accent: Int) {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(58); setPadding(0, dp(2), 0, dp(2)); isClickable = true }
        row.addView(TextView(this).apply { text = icon; textSize = 19f; gravity = Gravity.CENTER; setTextColor(accent) }, LinearLayout.LayoutParams(dp(40), dp(48)))
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        texts.addView(TextView(this).apply { text = title; textSize = 14f; setTextColor(primary); setTypeface(typeface, Typeface.BOLD) })
        texts.addView(TextView(this).apply { text = sub; textSize = 11f; setTextColor(secondary) })
        row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        val radio = android.widget.RadioButton(this).apply { isChecked = prefs.getString("mode", "system") == mode; buttonTintList = android.content.res.ColorStateList.valueOf(accent) }
        row.addView(radio, LinearLayout.LayoutParams(dp(48), dp(48)))
        val choose = View.OnClickListener { setMode(mode) }; row.setOnClickListener(choose); radio.setOnClickListener(choose); parent.addView(row)
    }

    private fun setMode(mode: String) {
        prefs.edit().putString("mode", mode).apply()
        AppCompatDelegate.setDefaultNightMode(when (mode) { "dark" -> AppCompatDelegate.MODE_NIGHT_YES; "light" -> AppCompatDelegate.MODE_NIGHT_NO; else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM })
    }

    private fun addSwitch(parent: LinearLayout, title: String, sub: String, key: String, default: Boolean, primary: Int, secondary: Int, accent: Int) {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(62); setPadding(0, dp(3), 0, dp(3)) }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        texts.addView(TextView(this).apply { text = title; textSize = 14f; setTextColor(primary); setTypeface(typeface, Typeface.BOLD) })
        texts.addView(TextView(this).apply { text = sub; textSize = 11f; setTextColor(secondary) })
        row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(SwitchMaterial(this).apply { isChecked = settings.getBoolean(key, default); setOnCheckedChangeListener { _, checked -> settings.edit().putBoolean(key, checked).apply() } }, LinearLayout.LayoutParams(dp(54), dp(48)))
        parent.addView(row)
    }

    private fun addInfoRow(parent: LinearLayout, iconText: String, title: String, sub: String, primary: Int, secondary: Int, accent: Int, action: () -> Unit) {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(60); setPadding(0, dp(3), 0, dp(3)); isClickable = true; setOnClickListener { action() } }
        row.addView(TextView(this).apply { text = iconText; textSize = 18f; gravity = Gravity.CENTER; setTextColor(accent) }, LinearLayout.LayoutParams(dp(40), dp(48)))
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        texts.addView(TextView(this).apply { text = title; textSize = 14f; setTextColor(primary); setTypeface(typeface, Typeface.BOLD) })
        texts.addView(TextView(this).apply { text = sub; textSize = 11f; setTextColor(secondary) })
        row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(TextView(this).apply { text = "›"; textSize = 22f; setTextColor(secondary); gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(30), dp(48)))
        parent.addView(row)
    }

    private fun showPrivacy() { MaterialAlertDialogBuilder(this).setTitle("Privacy & permissions").setMessage("RSS CLOUD SYNC uses Android folder permissions only for folders you explicitly select. Cloud provider access will use the provider's official authentication flow. No password should be stored in the app.").setPositiveButton("OK", null).show() }
    private fun marginParams(bottom: Int) = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(bottom)) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
