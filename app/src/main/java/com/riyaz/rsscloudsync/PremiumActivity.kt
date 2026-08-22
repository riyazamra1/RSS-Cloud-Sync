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
        binding.pageTitle.text = "PREMIUM"
        binding.pageSubtitle.text = "Unlock the complete RSS CLOUD SYNC toolkit."
        binding.featureList.text = "✓  Everything in FREE\n\n✓  Upload only\n✓  Upload mirror\n✓  Upload then delete\n✓  Download only\n✓  Download mirror\n✓  Download then delete\n\n✓  Automatic sync and scheduling\n✓  Multiple folder pairs\n✓  Advanced file filtering\n✓  Priority sync\n✓  Extended sync history\n✓  No ads"
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}