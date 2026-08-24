package com.riyaz.rsscloudsync

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.riyaz.rsscloudsync.databinding.ActivityExternalStorageBinding

class ExternalStorageActivity : AppCompatActivity() {
    private lateinit var binding: ActivityExternalStorageBinding

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            Toast.makeText(this, "Folder permission could not be saved", Toast.LENGTH_SHORT).show()
        }
        getSharedPreferences("rss_cloud_sync", MODE_PRIVATE).edit()
            .putString("external_storage_uri", uri.toString())
            .apply()
        showFolder(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExternalStorageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "External storage"

        binding.chooseExternalButton.setOnClickListener { folderPicker.launch(null) }
        binding.clearExternalButton.setOnClickListener {
            getSharedPreferences("rss_cloud_sync", MODE_PRIVATE).edit()
                .remove("external_storage_uri").apply()
            binding.selectedExternalText.text = "No external folder selected"
            binding.externalStatusText.text = "Choose a folder to make it available for sync."
        }
        loadSavedFolder()
    }

    private fun loadSavedFolder() {
        val saved = getSharedPreferences("rss_cloud_sync", MODE_PRIVATE)
            .getString("external_storage_uri", null)
        if (saved == null) {
            binding.selectedExternalText.text = "No external folder selected"
        } else {
            showFolder(Uri.parse(saved))
        }
    }

    private fun showFolder(uri: Uri) {
        binding.selectedExternalText.text = uri.toString()
        binding.externalStatusText.text = "Folder access is ready for sync."
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
