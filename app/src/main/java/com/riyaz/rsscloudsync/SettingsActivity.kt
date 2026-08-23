package com.riyaz.rsscloudsync

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("appearance", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
    }

    private fun buildUi(): View {
        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val bg = if (dark) Color.rgb(9,12,20) else Color.rgb(246,247,251)
        val surface = if (dark) Color.rgb(20,24,35) else Color.WHITE
        val primary = if (dark) Color.WHITE else Color.rgb(28,30,39)
        val secondary = if (dark) Color.rgb(174,181,198) else Color.rgb(104,107,122)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        val toolbar = MaterialToolbar(this).apply { title = "Settings"; setTitleTextColor(primary); setNavigationIcon(android.R.drawable.ic_media_previous); setNavigationOnClickListener { finish() } }
        root.addView(toolbar, LinearLayout.LayoutParams(-1, dp(58)))
        val scroll = android.widget.ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(28)) }
        content.addView(section("APPEARANCE", primary, secondary))
        val modeCard = card(surface)
        val modes = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(10), dp(16), dp(10)) }
        addMode(modes, "☀  Light", "Use a bright interface", "light", primary, secondary)
        addMode(modes, "⚙  System", "Follow Android appearance", "system", primary, secondary)
        addMode(modes, "◐  Dark", "Use a dark interface", "dark", primary, secondary)
        modeCard.addView(modes); content.addView(modeCard)
        content.addView(section("SYNC", primary, secondary))
        val syncCard = card(surface)
        val syncBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8)) }
        addSwitch(syncBox, "Automatic sync", "Allow background synchronization", true, primary, secondary)
        addSwitch(syncBox, "Sync over mobile data", "Allow sync when Wi-Fi is unavailable", false, primary, secondary)
        addSwitch(syncBox, "Sync notifications", "Show sync status notifications", true, primary, secondary)
        syncCard.addView(syncBox); content.addView(syncCard)
        content.addView(section("APP", primary, secondary))
        val appCard = card(surface)
        val appBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8)) }
        addRow(appBox, "About RSS CLOUD SYNC", "Version and app information", primary, secondary)
        addRow(appBox, "Privacy", "Your data and permissions", primary, secondary)
        appCard.addView(appBox); content.addView(appCard)
        scroll.addView(content); root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f)); return root
    }

    private fun section(title: String, primary: Int, secondary: Int) = TextView(this).apply { text = title; setTextColor(secondary); textSize = 11f; setTypeface(typeface, 1); letterSpacing = .08f; setPadding(dp(4), dp(14), dp(4), dp(8)) }
    private fun card(color: Int) = MaterialCardView(this).apply { setCardBackgroundColor(color); radius = dp(22).toFloat(); cardElevation = 0f; strokeWidth = dp(1); strokeColor = Color.argb(45, 128,128,150) }
    private fun addMode(parent: LinearLayout, title: String, sub: String, mode: String, primary: Int, secondary: Int) {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(8), 0, dp(8)); isClickable = true }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(this).apply { text = title; textSize = 14f; setTextColor(primary); setTypeface(typeface, 1) })
        texts.addView(TextView(this).apply { text = sub; textSize = 11f; setTextColor(secondary); setPadding(0, dp(2), 0, 0) })
        row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        val radio = android.widget.RadioButton(this).apply { isChecked = prefs.getString("mode", "system") == mode; buttonTintList = android.content.res.ColorStateList.valueOf(Color.rgb(111,85,198)); setOnClickListener { setMode(mode) } }
        row.addView(radio, LinearLayout.LayoutParams(dp(48), dp(48)))
        row.setOnClickListener { setMode(mode) }; parent.addView(row)
    }
    private fun setMode(mode: String) { prefs.edit().putString("mode", mode).apply(); AppCompatDelegate.setDefaultNightMode(when(mode) { "dark" -> AppCompatDelegate.MODE_NIGHT_YES; "light" -> AppCompatDelegate.MODE_NIGHT_NO; else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM }); recreate() }
    private fun addSwitch(parent: LinearLayout, title: String, sub: String, checked: Boolean, primary: Int, secondary: Int) { val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(8), 0, dp(8)) }; val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; texts.addView(TextView(this).apply { text = title; textSize = 14f; setTextColor(primary); setTypeface(typeface,1) }); texts.addView(TextView(this).apply { text = sub; textSize = 11f; setTextColor(secondary) }); row.addView(texts, LinearLayout.LayoutParams(0,-2,1f)); row.addView(SwitchMaterial(this).apply { isChecked = checked }); parent.addView(row) }
    private fun addRow(parent: LinearLayout, title: String, sub: String, primary: Int, secondary: Int) { val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(10), 0, dp(10)) }; row.addView(TextView(this).apply { text=title; textSize=14f; setTextColor(primary); setTypeface(typeface,1) }); row.addView(TextView(this).apply { text=sub; textSize=11f; setTextColor(secondary); setPadding(0,dp(2),0,0) }); parent.addView(row) }
    private fun dp(v:Int)= (v*resources.displayMetrics.density).toInt()
}
