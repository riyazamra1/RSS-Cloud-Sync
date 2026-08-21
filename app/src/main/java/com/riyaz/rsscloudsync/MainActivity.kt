package com.riyaz.rsscloudsync

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.riyaz.rsscloudsync.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val prefs by lazy {
        getSharedPreferences("appearance", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, true)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupAppearanceButtons()
        setupGradientButtons()
        setupNavigation()
        applyAppearance()

        binding.progressCircle.setProgress(72f, false)
    }

    // ---------------------------------------------------------
    // APPEARANCE
    // ---------------------------------------------------------

    private fun setupAppearanceButtons() {

        binding.lightButton.setOnClickListener {
            saveAppearance("light")

            AppCompatDelegate.setDefaultNightMode(
                AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        binding.systemButton.setOnClickListener {
            saveAppearance("system")

            AppCompatDelegate.setDefaultNightMode(
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            )
        }

        binding.darkButton.setOnClickListener {
            saveAppearance("dark")

            AppCompatDelegate.setDefaultNightMode(
                AppCompatDelegate.MODE_NIGHT_YES
            )
        }
    }

    private fun saveAppearance(value: String) {
        prefs.edit()
            .putString("mode", value)
            .apply()
    }

    private fun applyAppearance() {

        val mode = prefs.getString("mode", "system") ?: "system"

        when (mode) {

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

        updateAppearanceButtons()
    }

    private fun updateAppearanceButtons() {

        val buttons = listOf(
            binding.lightButton,
            binding.systemButton,
            binding.darkButton
        )

        buttons.forEach { button ->

            if (button.isSelected) {

                button.background = createGradientDrawable(
                    intArrayOf(
                        Color.rgb(119, 82, 255),
                        Color.rgb(236, 78, 177)
                    ),
                    50f
                )

                button.setTextColor(Color.WHITE)

            } else {

                button.background = createSolidDrawable(
                    Color.TRANSPARENT,
                    50f
                )

                button.setTextColor(
                    Color.rgb(30, 36, 55)
                )
            }
        }
    }

    // ---------------------------------------------------------
    // GRADIENT BUTTONS
    // ---------------------------------------------------------

    private fun setupGradientButtons() {

        applyGradient(binding.syncNowButton)

        applyGradient(binding.googleConnectButton)
        applyGradient(binding.oneDriveConnectButton)
        applyGradient(binding.dropboxConnectButton)
    }

    private fun applyGradient(button: MaterialButton) {

        button.background = createGradientDrawable(
            intArrayOf(
                Color.rgb(116, 78, 255),
                Color.rgb(234, 78, 180)
            ),
            60f
        )

        button.setTextColor(Color.WHITE)

        button.setOnTouchListener { view, event ->

            when (event.action) {

                android.view.MotionEvent.ACTION_DOWN -> {
                    view.alpha = 0.82f
                }

                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    view.alpha = 1f
                }
            }

            false
        }
    }

    private fun createGradientDrawable(
        colors: IntArray,
        radius: Float
    ): GradientDrawable {

        return GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            colors
        ).apply {
            cornerRadius = radius
        }
    }

    private fun createSolidDrawable(
        color: Int,
        radius: Float
    ): GradientDrawable {

        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
        }
    }

    // ---------------------------------------------------------
    // NAVIGATION
    // ---------------------------------------------------------

    private fun setupNavigation() {

        binding.foldersCard.setOnClickListener {

            startActivity(
                Intent(
                    this,
                    FolderSyncActivity::class.java
                )
            )
        }

        binding.syncSetupCard.setOnClickListener {

            startActivity(
                Intent(
                    this,
                    SyncSetupActivity::class.java
                )
            )
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
            binding.lastSyncText.text = "Last sync: Just now"

            binding.progressCircle.animateProgress(
                72f,
                100f
            )
        }
    }

    private fun showCloudMessage(message: String) {

        android.widget.Toast
            .makeText(
                this,
                message,
                android.widget.Toast.LENGTH_SHORT
            )
            .show()
    }
}