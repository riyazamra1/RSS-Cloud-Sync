package com.riyaz.rsscloudsync

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.riyaz.rsscloudsync.databinding.ActivityFeatureListBinding

class FreeFeaturesActivity : AppCompatActivity() {
    private lateinit var binding: ActivityFeatureListBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFeatureListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "FREE FEATURES"
        binding.pageTitle.text = "FREE"
        binding.pageSubtitle.text = "Simple, lightweight syncing with no unnecessary features."
        binding.featureList.text = "✓  Two-way Sync\n\n✓  Manual Sync\n\n✓  Light / System / Dark appearance\n\n✓  Basic sync status"
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}