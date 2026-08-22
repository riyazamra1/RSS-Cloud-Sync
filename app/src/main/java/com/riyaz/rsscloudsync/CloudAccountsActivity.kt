package com.riyaz.rsscloudsync

import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
    }

    private fun connect(view: android.view.View, url: String) {
        view.setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}