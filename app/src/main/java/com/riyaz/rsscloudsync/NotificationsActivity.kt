package com.riyaz.rsscloudsync

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import android.Manifest
import android.content.pm.PackageManager

class NotificationsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        findViewById<View>(R.id.enableNotificationsButton).setOnClickListener { requestNotifications() }
        findViewById<View>(R.id.clearNotificationsButton).setOnClickListener { finish() }
        refreshState()
    }

    override fun onResume() { super.onResume(); if (!isFinishing) refreshState() }

    private fun refreshState() {
        val status = findViewById<TextView>(R.id.notificationStatus)
        val enabled = if (android.os.Build.VERSION.SDK_INT >= 33) ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED else true
        status.text = if (enabled) "Notifications are enabled" else "Notifications are disabled. Enable them to receive sync updates."
        findViewById<View>(R.id.enableNotificationsButton).visibility = if (enabled) View.GONE else View.VISIBLE
    }

    private fun requestNotifications() {
        if (android.os.Build.VERSION.SDK_INT >= 33) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7001)
    }
}
