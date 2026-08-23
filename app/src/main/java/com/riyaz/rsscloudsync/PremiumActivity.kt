package com.riyaz.rsscloudsync

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.graphics.Typeface
import android.view.Gravity
import android.widget.TableRow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.riyaz.rsscloudsync.databinding.ActivityFeatureListBinding

class PremiumActivity : AppCompatActivity() {
    private lateinit var binding: ActivityFeatureListBinding

    private data class Feature(val icon: String, val name: String)

    private val freeFeatures = listOf(
        Feature("↔", "Two-way Sync"), Feature("▶", "Manual sync"), Feature("▣", "1 synced folder pair"),
        Feature("☁", "Google Drive"), Feature("◷", "Basic sync history"), Feature("◐", "Light / Dark / System"), Feature("A", "Ads")
    )

    private val premiumFeatures = listOf(
        Feature("✓", "Everything in Free"), Feature("▣", "Multiple folder pairs"), Feature("↑", "Upload only"),
        Feature("↥", "Upload mirror"), Feature("⌫", "Upload then delete"), Feature("↓", "Download only"),
        Feature("↧", "Download mirror"), Feature("⌫", "Download then delete"), Feature("⟳", "Automatic sync"),
        Feature("⚡", "Instant upload"), Feature("◷", "Advanced scheduling"), Feature("≡", "Advanced file filtering"),
        Feature("★", "Priority sync"), Feature("☷", "Extended sync history"), Feature("✓", "No ads")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFeatureListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Compare plans"
        binding.pageTitle.text = "FREE  vs  PREMIUM"
        binding.pageSubtitle.text = "Everything you need to choose the right sync plan"
        buildTable(binding.freeFeatureTable, "FREE", freeFeatures)
        buildTable(binding.premiumFeatureTable, "PREMIUM", premiumFeatures)
        applyThemeText()
    }

    private fun buildTable(table: android.widget.TableLayout, heading: String, features: List<Feature>) {
        table.removeAllViews()
        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val primary = if (dark) Color.rgb(247,248,252) else Color.rgb(35,36,45)
        val secondary = if (dark) Color.rgb(174,181,198) else Color.rgb(103,106,120)
        val accent = if (heading == "PREMIUM") Color.rgb(156,94,221) else Color.rgb(91,79,190)
        val header = TableRow(this).apply {
            setPadding(dp(14), dp(11), dp(14), dp(11))
            setBackgroundColor(if (dark) Color.rgb(29,34,47) else Color.rgb(246,245,252))
        }
        header.addView(cell("FEATURE", 1f, true, Gravity.START, primary, accent, ""))
        header.addView(cell(heading, 0.36f, true, Gravity.CENTER, primary, accent, ""))
        table.addView(header)
        features.forEachIndexed { index, feature ->
            val row = TableRow(this).apply {
                setPadding(dp(12), dp(6), dp(12), dp(6))
                if (index % 2 == 0) setBackgroundColor(if (dark) Color.rgb(24,29,40) else Color.rgb(250,250,253))
            }
            row.addView(cell(feature.name, 1f, false, Gravity.START, primary, accent, feature.icon))
            row.addView(cell("✓", 0.36f, true, Gravity.CENTER, primary, Color.rgb(49,184,128), ""))
            table.addView(row)
        }
    }

    private fun cell(text: String, weight: Float, bold: Boolean, gravity: Int, primary: Int, accent: Int, icon: String): TextView = TextView(this).apply {
        this.text = if (icon.isEmpty()) text else "$icon  $text"
        this.gravity = gravity or Gravity.CENTER_VERTICAL
        textSize = if (bold) 11.5f else 11f
        setTextColor(if (bold) accent else primary)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(2), 0, dp(2), 0)
        layoutParams = TableRow.LayoutParams(0, dp(42), weight)
    }

    private fun applyThemeText() {
        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        binding.root.setBackgroundColor(if (dark) Color.rgb(9,12,20) else Color.rgb(246,247,251))
        binding.pageTitle.setTextColor(if (dark) Color.WHITE else Color.rgb(28,30,39))
        binding.pageSubtitle.setTextColor(if (dark) Color.rgb(174,181,198) else Color.rgb(104,107,122))
        binding.toolbar.setTitleTextColor(if (dark) Color.WHITE else Color.rgb(28,30,39))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
