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
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
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
        val bg = if (dark) Color.rgb(7, 11, 20) else Color.rgb(245, 247, 251)
        val surface = if (dark) Color.rgb(15, 22, 36) else Color.WHITE
        val surface2 = if (dark) Color.rgb(19, 29, 46) else Color.rgb(249, 251, 255)
        val primary = if (dark) Color.rgb(248, 250, 255) else Color.rgb(18, 25, 38)
        val secondary = if (dark) Color.rgb(151, 166, 188) else Color.rgb(103, 113, 132)
        val outline = if (dark) Color.rgb(39, 54, 77) else Color.rgb(225, 230, 238)
        val accent = Color.rgb(54, 174, 231)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }

        val toolbar = MaterialToolbar(this).apply {
            title = "Settings"
            setTitleTextColor(primary)
            setNavigationIcon(android.R.drawable.ic_media_previous)
            setNavigationIconTint(primary)
            setNavigationOnClickListener { finish() }
            elevation = dp(6).toFloat()
            setBackgroundColor(surface)
        }
        root.addView(toolbar, LinearLayout.LayoutParams(-1, dp(60)))

        val scroll = ScrollView(this).apply { clipToPadding = false }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(36))
        }

        val hero = MaterialCardView(this).apply {
            setCardBackgroundColor(surface2)
            radius = dp(24).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            strokeColor = outline
        }
        val heroBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
        }
        heroBox.addView(TextView(this).apply {
            text = "RSS CLOUD SYNC"
            textSize = 12f
            letterSpacing = .14f
            setTextColor(accent)
            setTypeface(typeface, Typeface.BOLD)
        })
        heroBox.addView(TextView(this).apply {
            text = "Make syncing work your way"
            textSize = 22f
            setTextColor(primary)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(5), 0, dp(3))
        })
        heroBox.addView(TextView(this).apply {
            text = "Appearance, sync behavior and app preferences"
            textSize = 13f
            setTextColor(secondary)
        })
        hero.addView(heroBox)
        content.addView(hero, marginParams(bottom = 10))

        content.addView(section("APPEARANCE", secondary))
        val appearance = card(surface, outline)
        val modes = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(7), dp(16), dp(7))
        }
        addMode(modes, "☀", "Light", "Bright interface", "light", primary, secondary, accent)
        addMode(modes, "◐", "System", "Follow Android appearance", "system", primary, secondary, accent)
        addMode(modes, "◑", "Dark", "Deep cloud workspace", "dark", primary, secondary, accent)
        appearance.addView(modes)
        content.addView(appearance, marginParams(bottom = 10))

        content.addView(section("SYNC BEHAVIOR", secondary))
        val sync = card(surface, outline)
        val syncBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(7), dp(16), dp(7))
        }
        addSwitch(syncBox, "Automatic sync", "Keep selected folders synchronized in background", "automatic", true, primary, secondary, accent)
        addSwitch(syncBox, "Mobile data", "Allow synchronization without Wi-Fi", "mobile_data", false, primary, secondary, accent)
        addSwitch(syncBox, "Sync notifications", "Show completion and error notifications", "notifications", true, primary, secondary, accent)
        sync.addView(syncBox)
        content.addView(sync, marginParams(bottom = 10))

        content.addView(section("APP", secondary))
        val app = card(surface, outline)
        val appBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(7), dp(16), dp(7))
        }
        addInfoRow(appBox, "ⓘ", "About RSS CLOUD SYNC", "Version and app information", primary, secondary, accent)
        addInfoRow(appBox, "⌁", "Privacy & permissions", "Manage app access and privacy", primary, secondary, accent)
        addInfoRow(appBox, "?", "Help & support", "Get help with cloud syncing", primary, secondary, accent)
        app.addView(appBox)
        content.addView(app)

        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    private fun section(title: String, color: Int) = TextView(this).apply {
        text = title
        textSize = 11f
        letterSpacing = .12f
        setTextColor(color)
        setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(4), dp(9), dp(4), dp(7))
    }

    private fun card(color: Int, outline: Int) = MaterialCardView(this).apply {
        setCardBackgroundColor(color)
        radius = dp(22).toFloat()
        cardElevation = 0f
        strokeWidth = dp(1)
        strokeColor = outline
    }

    private fun addMode(parent: LinearLayout, icon: String, title: String, sub: String, mode: String, primary: Int, secondary: Int, accent: Int) {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(60)
            isClickable = true
            setPadding(0, dp(5), 0, dp(5))
        }
        val iconView = TextView(this).apply {
            text = icon
            textSize = 19f
            gravity = Gravity.CENTER
            setTextColor(accent)
            setTypeface(typeface, Typeface.BOLD)
        }
        row.addView(iconView, LinearLayout.LayoutParams(dp(40), dp(48)))
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        texts.addView(TextView(this).apply {
            text = title
            textSize = 15f
            setTextColor(primary)
            setTypeface(typeface, Typeface.BOLD)
        })
        texts.addView(TextView(this).apply {
            text = sub
            textSize = 12f
            setTextColor(secondary)
            setPadding(0, dp(2), 0, 0)
        })
        row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        val radio = android.widget.RadioButton(this).apply {
            isChecked = prefs.getString("mode", "system") == mode
            buttonTintList = android.content.res.ColorStateList.valueOf(accent)
            setOnClickListener { setMode(mode) }
        }
        row.addView(radio, LinearLayout.LayoutParams(dp(48), dp(48)))
        row.setOnClickListener { setMode(mode) }
        parent.addView(row)
    }

    private fun setMode(mode: String) {
        prefs.edit().putString("mode", mode).apply()
        AppCompatDelegate.setDefaultNightMode(when (mode) {
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        })
        recreate()
    }

    private fun addSwitch(parent: LinearLayout, title: String, sub: String, key: String, default: Boolean, primary: Int, secondary: Int, accent: Int) {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(66)
            setPadding(0, dp(5), 0, dp(5))
        }
        val icon = TextView(this).apply {
            text = when (key) { "automatic" -> "↻"; "mobile_data" -> "⌁"; else -> "●" }
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(accent)
        }
        row.addView(icon, LinearLayout.LayoutParams(dp(40), dp(48)))
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(this).apply { text = title; textSize = 15f; setTextColor(primary); setTypeface(typeface, Typeface.BOLD) })
        texts.addView(TextView(this).apply { text = sub; textSize = 12f; setTextColor(secondary); setPadding(0, dp(2), 0, 0) })
        row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        val toggle = SwitchMaterial(this).apply {
            isChecked = settings.getBoolean(key, default)
            trackTintList = ContextCompat.getColorStateList(this@SettingsActivity, android.R.color.transparent)
            setOnCheckedChangeListener { _, checked -> settings.edit().putBoolean(key, checked).apply() }
        }
        row.addView(toggle, LinearLayout.LayoutParams(dp(54), dp(48)))
        parent.addView(row)
    }

    private fun addInfoRow(parent: LinearLayout, iconText: String, title: String, sub: String, primary: Int, secondary: Int, accent: Int) {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(62)
            setPadding(0, dp(5), 0, dp(5))
        }
        row.addView(TextView(this).apply {
            text = iconText
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(accent)
        }, LinearLayout.LayoutParams(dp(40), dp(48)))
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(this).apply { text = title; textSize = 15f; setTextColor(primary); setTypeface(typeface, Typeface.BOLD) })
        texts.addView(TextView(this).apply { text = sub; textSize = 12f; setTextColor(secondary); setPadding(0, dp(2), 0, 0) })
        row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(TextView(this).apply { text = "›"; textSize = 24f; setTextColor(secondary); gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(32), dp(48)))
        parent.addView(row)
    }

    private fun marginParams(bottom: Int): LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(bottom)) }
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
