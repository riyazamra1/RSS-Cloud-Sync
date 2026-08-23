package com.riyaz.rsscloudsync

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import com.riyaz.rsscloudsync.databinding.ActivityCloudAccountsBinding

class CloudAccountsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCloudAccountsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCloudAccountsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Cloud accounts"

        connect(binding.googleDriveConnect, "https://accounts.google.com/ServiceLogin")
        connect(binding.oneDriveConnect, "https://login.live.com/")
        connect(binding.dropboxConnect, "https://www.dropbox.com/login")
        connect(binding.megaConnect, "https://mega.nz/login")
        connect(binding.boxConnect, "https://account.box.com/login")
        binding.webDavConnect.setOnClickListener { startActivity(Intent(this, SyncSetupActivity::class.java)) }

        animateRows()
    }

    private fun connect(view: View, url: String) {
        view.setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    private fun animateRows() {
        val root = binding.root as? ViewGroup ?: return
        root.alpha = 0f
        root.translationY = 8f.dp()
        root.animate().alpha(1f).translationY(0f).setDuration(280L).start()
        animateProviderCards(root)
    }

    private fun animateProviderCards(parent: ViewGroup) {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child is com.google.android.material.card.MaterialCardView) {
                child.alpha = 0f
                child.translationY = 10f.dp()
                child.animate().alpha(1f).translationY(0f).setStartDelay((i * 45L).coerceAtMost(260L)).setDuration(240L).start()
            }
            if (child is ViewGroup) animateProviderCards(child)
        }
    }

    private fun Float.dp(): Float = this * resources.displayMetrics.density

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
