package com.riyaz.rsscloudsync

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import com.google.android.material.button.MaterialButton
import com.riyaz.rsscloudsync.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val prefs by lazy { getSharedPreferences("appearance", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupAppearanceButtons()
        setupGradientButtons()
        setupNavigation()
        applyAppearance()
        binding.gradientProgress.setProgress(72f, false)
    }

    private fun setupAppearanceButtons() {
        binding.lightButton.setOnClickListener {
            prefs.edit().putString("mode", "light").apply()
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
        binding.systemButton.setOnClickListener {
            prefs.edit().putString("mode", "system").apply()
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
        binding.darkButton.setOnClickListener {
            prefs.edit().putString("mode", "dark").apply()
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }
    }

    private fun applyAppearance() {
        when (prefs.getString("mode", "system")) {
            "light" -> {
                binding.lightButton.isSelected = true
                binding.systemButton.isSelected = false
                binding.darkButton.isSelected = false
            }
            "dark" -> {
                binding.lightButton.isSelected = false
                binding.systemButton.isSelected = false
                binding.darkButton.isSelected = true
            }
            else -> {
                binding.lightButton.isSelected = false
                binding.systemButton.isSelected = true
                binding.darkButton.isSelected = false
            }
        }
        listOf(binding.lightButton, binding.systemButton, binding.darkButton).forEach { button ->
            button.background = if (button.isSelected) {
                gradient(intArrayOf(Color.rgb(119, 82, 255), Color.rgb(236, 78, 177)), 50f)
            } else {
                solid(Color.TRANSPARENT, 50f)
            }
            button.setTextColor(if (button.isSelected) Color.WHITE else Color.rgb(30, 36, 55))
        }
    }

    private fun setupGradientButtons() {
        applyGradient(binding.syncNowButton)
        applyGradient(binding.googleDriveConnectButton)
        applyGradient(binding.oneDriveConnectButton)
        applyGradient(binding.dropboxConnectButton)
    }

    private fun applyGradient(button: MaterialButton) {
        button.background = gradient(intArrayOf(Color.rgb(116, 78, 255), Color.rgb(234, 78, 180)), 60f)
        button.setTextColor(Color.WHITE)
        button.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> view.alpha = 0.82f
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.alpha = 1f
            }
            false
        }
    }

    private fun gradient(colors: IntArray, radius: Float) =
        GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors).apply { cornerRadius = radius }

    private fun solid(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
    }

    private fun setupNavigation() {
        binding.foldersCard.setOnClickListener {
            startActivity(Intent(this, FolderSyncActivity::class.java))
        }
        binding.syncSetupCard.setOnClickListener {
            startActivity(Intent(this, SyncSetupActivity::class.java))
        }
        binding.cloudManageText.setOnClickListener {
            startActivity(Intent(this, SyncSetupActivity::class.java))
        }
        binding.googleDriveCard.setOnClickListener {
            showCloudMessage("Google Drive connection coming next")
        }
        binding.oneDriveCard.setOnClickListener {
            showCloudMessage("OneDrive connection coming next")
        }
        binding.dropboxCard.setOnClickListener {
            showCloudMessage("Dropbox connection coming next")
        }
        binding.syncNowButton.setOnClickListener {
            binding.syncStatusText.text = "Sync complete"
            binding.syncSubtitle.text = "Everything is up to date"
            binding.lastSyncText.text = "Last sync: Just now"
            binding.gradientProgress.setProgress(100f, true)
        }
    }

    private fun showCloudMessage(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }
}