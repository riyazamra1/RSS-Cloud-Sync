package com.riyaz.rsscloudsync

import android.os.Bundle
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
        supportActionBar?.title = "PREMIUM"

        binding.pageTitle.text = "FREE vs PREMIUM"
        binding.pageSubtitle.text = "1 user • 1 device"

        binding.featureList.text = """
FREE

✓ Two-way sync
✓ Manual sync
✓ Basic sync status


PREMIUM

✓ Everything in FREE
✓ Upload only
✓ Upload mirror
✓ Upload then delete
✓ Download only
✓ Download mirror
✓ Download then delete
✓ Automatic sync and scheduling
✓ Multiple folder pairs
✓ Advanced file filtering
✓ Priority sync
✓ Extended sync history
✓ No ads


PREMIUM MEMBERSHIP

1 user • 1 device

Monthly
Rs. 500 / month

Annual
Rs. 4,500 / year

One-time
Rs. 12,500 lifetime

Choose the plan that works best for you.
""".trimIndent()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
