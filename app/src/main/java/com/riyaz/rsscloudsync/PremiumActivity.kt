package com.riyaz.rsscloudsync

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.TableRow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.riyaz.rsscloudsync.databinding.ActivityFeatureListBinding

class PremiumActivity : AppCompatActivity() {
    private lateinit var binding: ActivityFeatureListBinding

    private val freeFeatures = listOf(
        "Two-way Sync", "Manual sync", "1 synced folder pair", "Google Drive", "Basic sync history", "Light / Dark / System", "Ads"
    )

    private val premiumFeatures = listOf(
        "Everything in Free", "Multiple folder pairs", "Upload only", "Upload mirror", "Upload then delete", "Download only", "Download mirror", "Download then delete", "Automatic sync", "Instant upload", "Advanced scheduling", "Advanced file filtering", "Priority sync", "Extended sync history", "No ads"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFeatureListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Premium"
        binding.pageTitle.text = "FREE vs PREMIUM"
        binding.pageSubtitle.text = "Compare plans • 1 user • 1 device"
        buildTable(binding.freeFeatureTable, "FREE", freeFeatures)
        buildTable(binding.premiumFeatureTable, "PREMIUM", premiumFeatures)
    }

    private fun buildTable(table: android.widget.TableLayout, heading: String, features: List<String>) {
        table.removeAllViews()
        val header = TableRow(this).apply { setPadding(dp(14), dp(12), dp(14), dp(12)) }
        header.addView(cell("FEATURE", 1f, true, Gravity.START, false))
        header.addView(cell(heading, 0.42f, true, Gravity.CENTER, false))
        table.addView(header)
        features.forEachIndexed { index, feature ->
            val row = TableRow(this).apply {
                setPadding(dp(14), dp(9), dp(14), dp(9))
                if (index % 2 == 0) setBackgroundColor(Color.argb(14, 100, 90, 180))
            }
            row.addView(cell(feature, 1f, false, Gravity.START, false))
            row.addView(cell("✓", 0.42f, true, Gravity.CENTER, true))
            table.addView(row)
        }
    }

    private fun cell(text: String, weight: Float, bold: Boolean, gravity: Int, check: Boolean): TextView = TextView(this).apply {
        this.text = text
        this.gravity = gravity or Gravity.CENTER_VERTICAL
        textSize = if (bold) 11.5f else 11f
        setTextColor(if (check) Color.rgb(45, 166, 118) else if (bold) Color.rgb(80, 74, 145) else Color.rgb(100, 103, 116))
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        layoutParams = TableRow.LayoutParams(0, dp(40), weight)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
