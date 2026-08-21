package com.riyaz.rsscloudsync

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.riyaz.rsscloudsync.databinding.ActivityFolderSyncBinding
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class FolderSyncActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFolderSyncBinding

    private val scanExecutor: ExecutorService =
        Executors.newSingleThreadExecutor()

    @Volatile
    private var isScanning = false

    private val folderPicker =
        registerForActivityResult(
            ActivityResultContracts.OpenDocumentTree()
        ) { uri: Uri? ->

            if (uri != null) {

                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (e: SecurityException) {

                    Toast.makeText(
                        this,
                        "Unable to keep folder permission",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                val preferences = getSharedPreferences(
                    "rss_cloud_sync",
                    MODE_PRIVATE
                )

                preferences.edit()
                    .putString(
                        "sync_folder_uri",
                        uri.toString()
                    )
                    .apply()

                showSelectedFolder(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding =
            ActivityFolderSyncBinding.inflate(layoutInflater)

        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Sync Folders"

        binding.chooseFolderButton.setOnClickListener {

            if (!isScanning) {
                folderPicker.launch(null)
            }
        }

        binding.scanFolderButton.setOnClickListener {

            if (!isScanning) {
                scanSelectedFolder()
            }
        }

        loadSavedFolder()
    }

    private fun loadSavedFolder() {

        val preferences = getSharedPreferences(
            "rss_cloud_sync",
            MODE_PRIVATE
        )

        val savedUri = preferences.getString(
            "sync_folder_uri",
            null
        )

        if (savedUri != null) {

            showSelectedFolder(
                Uri.parse(savedUri)
            )

        } else {

            binding.selectedFolderText.text =
                "No folder selected"
        }
    }

    private fun showSelectedFolder(uri: Uri) {

        binding.selectedFolderText.text =
            uri.toString()
    }

    private fun scanSelectedFolder() {

        val preferences = getSharedPreferences(
            "rss_cloud_sync",
            MODE_PRIVATE
        )

        val savedUri = preferences.getString(
            "sync_folder_uri",
            null
        )

        if (savedUri == null) {

            binding.folderScanResult.text =
                "Please choose a folder first."

            return
        }

        val folderUri = Uri.parse(savedUri)

        isScanning = true

        binding.scanFolderButton.isEnabled = false
        binding.chooseFolderButton.isEnabled = false

        binding.folderScanResult.text =
            "Scanning folder...\nPlease wait."

        scanExecutor.execute {

            val result =
                scanFolder(folderUri)

            runOnUiThread {

                if (isFinishing || isDestroyed) {
                    return@runOnUiThread
                }

                isScanning = false

                binding.scanFolderButton.isEnabled = true
                binding.chooseFolderButton.isEnabled = true

                if (result.errorMessage != null) {

                    binding.folderScanResult.text =
                        result.errorMessage

                    Toast.makeText(
                        this,
                        "Folder scan failed",
                        Toast.LENGTH_SHORT
                    ).show()

                } else {

                    binding.folderScanResult.text =
                        "Files: ${result.fileCount}\n" +
                                "Folders: ${result.folderCount}\n" +
                                "Total size: " +
                                formatFileSize(
                                    result.totalSize
                                )

                    Toast.makeText(
                        this,
                        "Folder details updated",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun scanFolder(
        treeUri: Uri
    ): FolderScanResult {

        var fileCount = 0
        var folderCount = 0
        var totalSize = 0L

        try {

            val rootDocumentId =
                DocumentsContract.getTreeDocumentId(
                    treeUri
                )

            val foldersToScan =
                ArrayDeque<String>()

            foldersToScan.add(rootDocumentId)

            while (foldersToScan.isNotEmpty()) {

                val currentFolderId =
                    foldersToScan.removeFirst()

                val childrenUri =
                    DocumentsContract
                        .buildChildDocumentsUriUsingTree(
                            treeUri,
                            currentFolderId
                        )

                contentResolver.query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_SIZE
                    ),
                    null,
                    null,
                    null
                )?.use { cursor ->

                    val idColumn =
                        cursor.getColumnIndex(
                            DocumentsContract
                                .Document
                                .COLUMN_DOCUMENT_ID
                        )

                    val mimeColumn =
                        cursor.getColumnIndex(
                            DocumentsContract
                                .Document
                                .COLUMN_MIME_TYPE
                        )

                    val sizeColumn =
                        cursor.getColumnIndex(
                            DocumentsContract
                                .Document
                                .COLUMN_SIZE
                        )

                    if (
                        idColumn < 0 ||
                        mimeColumn < 0 ||
                        sizeColumn < 0
                    ) {
                        return FolderScanResult(
                            fileCount = fileCount,
                            folderCount = folderCount,
                            totalSize = totalSize,
                            errorMessage =
                                "Unable to read folder information."
                        )
                    }

                    while (cursor.moveToNext()) {

                        val childDocumentId =
                            cursor.getString(
                                idColumn
                            )

                        val mimeType =
                            cursor.getString(
                                mimeColumn
                            )

                        if (
                            mimeType ==
                            DocumentsContract
                                .Document
                                .MIME_TYPE_DIR
                        ) {

                            folderCount++

                            foldersToScan.add(
                                childDocumentId
                            )

                        } else {

                            fileCount++

                            if (
                                !cursor.isNull(
                                    sizeColumn
                                )
                            ) {

                                val fileSize =
                                    cursor.getLong(
                                        sizeColumn
                                    )

                                if (fileSize > 0) {
                                    totalSize += fileSize
                                }
                            }
                        }
                    }
                }
            }

        } catch (e: Exception) {

            return FolderScanResult(
                fileCount = fileCount,
                folderCount = folderCount,
                totalSize = totalSize,
                errorMessage =
                    "Unable to read folder details.\n" +
                            e.message.orEmpty()
            )
        }

        return FolderScanResult(
            fileCount = fileCount,
            folderCount = folderCount,
            totalSize = totalSize
        )
    }

    private fun formatFileSize(
        bytes: Long
    ): String {

        if (bytes <= 0) {
            return "0 B"
        }

        val units = arrayOf(
            "B",
            "KB",
            "MB",
            "GB",
            "TB"
        )

        var size = bytes.toDouble()
        var unitIndex = 0

        while (
            size >= 1024 &&
            unitIndex < units.size - 1
        ) {

            size /= 1024
            unitIndex++
        }

        return String.format(
            Locale.getDefault(),
            "%.2f %s",
            size,
            units[unitIndex]
        )
    }

    override fun onSupportNavigateUp(): Boolean {

        finish()

        return true
    }

    override fun onDestroy() {

        scanExecutor.shutdownNow()

        super.onDestroy()
    }
}

data class FolderScanResult(
    val fileCount: Int,
    val folderCount: Int,
    val totalSize: Long,
    val errorMessage: String? = null
)