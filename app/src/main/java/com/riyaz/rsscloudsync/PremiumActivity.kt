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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFeatureListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Premium"

        binding.pageTitle.text = "FREE vs PREMIUM"
        binding.pageSubtitle.text = "1 user • 1 device"
        buildComparisonTable()
    }

    private fun buildComparisonTable() {
        val rows = listOf(
            "Two-way Sync" to Pair(true, true),
            "Manual Sync" to Pair(true, true),
            "Upload only" to Pair(false, true),
            "Upload mirror" to Pair(false, true),
            "Upload then delete" to Pair(false, true),
            "Download only" to Pair(false, true),
            "Download mirror" to Pair(false, true),
            "Download then delete" to Pair(false, true),
            "Multiple folder pairs" to Pair(false, true),
            "Automatic sync" to Pair(false, true),
            "Advanced scheduling" to Pair(false, true),
            "Advanced file filtering" to Pair(false, true),
            "Priority sync" to Pair(false, true),
            "Extended sync history" to Pair(false, true),
            "No ads" to Pair(false, true)
        )

        binding.featureTable.removeAllViews()
        addHeaderRow()
        rows.forEachIndexed { index, (feature, access) ->
            addFeatureRow(feature, access.first, access.second, index % 2 == 0)
        }
    }

    private fun addHeaderRow() {
        val row = TableRow(this).apply {
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        row.addView(cell("FEATURE", 1.7f, true, Gravity.START))
        row.addView(cell("FREE", 0.8f, true, Gravity.CENTER))
        row.addView(cell("PREMIUM", 1.0f, true, Gravity.CENTER))
        binding.featureTable.addView(row)
    }

    private fun addFeatureRow(feature: String, free: Boolean, premium: Boolean, alternate: Boolean) {
        val row = TableRow(this).apply {
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setBackgroundColor(if (alternate) Color.argb(18, 100, 90, 180) else Color.TRANSPARENT)
        }
        row.addView(cell(feature, 1.7f, false, Gravity.START))
        row.addView(cell(if (free) "✓" else "—", 0.8f, true, Gravity.CENTER))
        row.addView(cell(if (premium) "✓" else "—", 1.0f, true, Gravity.CENTER))
        binding.featureTable.addView(row)
    }

    private fun cell(text: String, weight: Float, bold: Boolean, gravity: Int): TextView {
        return TextView(this).apply {
            this.text = text
            this.gravity = gravity or Gravity.CENTER_VERTICAL
            textSize = if (bold) 12f else 11.5f
            setTextColor(if (text == "✓") Color.rgb(43, 166, 116) else Color.rgb(95, 98, 112))
            if (bold && text != "✓") setTextColor(Color.rgb(55, 52, 90))
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = TableRow.LayoutParams(0, dp(42), weight)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
