package com.riyaz.rsscloudsync

import android.content.ContentResolver
import android.net.Uri
import android.os.CancellationSignal
import android.provider.DocumentsContract
import java.util.ArrayDeque

/**
 * Iterative Storage Access Framework scanner designed for large directory trees.
 * It never recurses on the Kotlin call stack and can cancel an active provider query.
 */
class SafFileScanner(
    private val resolver: ContentResolver
) {
    data class FileItem(
        val uri: Uri,
        val path: String,
        val name: String,
        val mimeType: String,
        val size: Long,
        val modified: Long
    )

    data class Options(
        val excludeHiddenFiles: Boolean = true,
        val excludeSubfolders: Boolean = false
    )

    data class Result(
        val files: Map<String, FileItem>,
        val folders: Int,
        val cancelled: Boolean
    )

    @Volatile
    private var cancelled = false

    fun cancel() {
        cancelled = true
    }

    fun scan(
        treeUri: Uri,
        options: Options = Options(),
        onFolderScanned: ((folders: Int, files: Int) -> Unit)? = null
    ): Result {
        cancelled = false
        val files = LinkedHashMap<String, FileItem>()
        val queue = ArrayDeque<Pair<String, String>>()
        queue.add("" to DocumentsContract.getTreeDocumentId(treeUri))
        var folders = 0

        while (queue.isNotEmpty() && !cancelled) {
            val (parentPath, parentId) = queue.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            val signal = CancellationSignal()

            if (cancelled) {
                signal.cancel()
                break
            }

            resolver.query(childrenUri, PROJECTION, null, null, null, signal)?.use { cursor ->
                val idColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val modifiedColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                if (idColumn < 0 || nameColumn < 0 || mimeColumn < 0 || sizeColumn < 0 || modifiedColumn < 0) {
                    throw IllegalStateException("Storage provider returned incomplete file metadata")
                }

                while (cursor.moveToNext() && !cancelled) {
                    val documentId = cursor.getString(idColumn)
                    val name = cursor.getString(nameColumn) ?: "Unnamed"
                    if (options.excludeHiddenFiles && name.startsWith('.')) continue

                    val mimeType = cursor.getString(mimeColumn) ?: "application/octet-stream"
                    val path = if (parentPath.isEmpty()) name else "$parentPath/$name"

                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        folders++
                        if (!options.excludeSubfolders) {
                            queue.addLast(path to documentId)
                        }
                    } else {
                        files[path] = FileItem(
                            uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId),
                            path = path,
                            name = name,
                            mimeType = mimeType,
                            size = if (cursor.isNull(sizeColumn)) 0L else cursor.getLong(sizeColumn),
                            modified = if (cursor.isNull(modifiedColumn)) 0L else cursor.getLong(modifiedColumn)
                        )
                    }
                }
            }

            onFolderScanned?.invoke(folders, files.size)
        }

        return Result(files, folders, cancelled)
    }

    companion object {
        private val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
    }
}
