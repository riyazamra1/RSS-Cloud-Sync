package com.riyaz.rsscloudsync

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.util.ArrayDeque

/**
 * Real Google Drive sync operations. Local files are addressed through the
 * Android Storage Access Framework and Drive files through DriveClient IDs.
 */
class GoogleDriveSyncEngine(
    private val context: Context,
    private val resolver: ContentResolver
) {
    enum class Direction { UPLOAD_ONLY, DOWNLOAD_ONLY, TWO_WAY }

    data class Progress(
        val processed: Int,
        val total: Int,
        val uploaded: Int,
        val downloaded: Int,
        val changed: Int,
        val failed: Int,
        val bytes: Long,
        val currentPath: String
    )

    data class Result(
        val processed: Int,
        val uploaded: Int,
        val downloaded: Int,
        val changed: Int,
        val failed: Int,
        val bytes: Long
    )

    private data class LocalItem(
        val uri: Uri,
        val path: String,
        val name: String,
        val mime: String,
        val size: Long,
        val modified: Long
    )

    private data class RemoteItem(
        val entry: DriveClient.Entry,
        val path: String,
        val directory: Boolean
    )

    @Volatile
    private var cancelled = false

    fun cancel() { cancelled = true }

    fun sync(
        localTree: Uri,
        driveFolderId: String,
        direction: Direction,
        listener: ((Progress) -> Unit)? = null
    ): Result {
        cancelled = false
        val drive = DriveClient(context)
        val local = indexLocal(localTree)
        val remote = indexDrive(drive, driveFolderId)
        val paths = LinkedHashSet<String>().apply {
            addAll(local.keys)
            addAll(remote.keys.filter { !remote.getValue(it).directory })
        }
        val files = paths.filter { local[it] != null || (remote[it]?.directory != true) }

        var processed = 0
        var uploaded = 0
        var downloaded = 0
        var changed = 0
        var failed = 0
        var bytes = 0L

        for (path in files) {
            if (cancelled) break
            try {
                val l = local[path]
                val r = remote[path]
                when (direction) {
                    Direction.UPLOAD_ONLY -> if (l != null && (r == null || isLocalNewer(l, r))) {
                        val parent = remoteFolderForPath(drive, driveFolderId, path)
                        bytes += drive.upload(l.uri, parent, l.name, l.mime)
                        uploaded++
                        changed++
                    }
                    Direction.DOWNLOAD_ONLY -> if (r != null && l == null) {
                        bytes += downloadToLocal(drive, localTree, r, path)
                        downloaded++
                        changed++
                    }
                    Direction.TWO_WAY -> when {
                        l != null && r == null -> {
                            val parent = remoteFolderForPath(drive, driveFolderId, path)
                            bytes += drive.upload(l.uri, parent, l.name, l.mime)
                            uploaded++
                            changed++
                        }
                        l == null && r != null -> {
                            bytes += downloadToLocal(drive, localTree, r, path)
                            downloaded++
                            changed++
                        }
                        l != null && r != null && (l.size != r.entry.size || l.modified != r.entry.modified) -> {
                            if (isLocalNewer(l, r)) {
                                val parent = remoteFolderForPath(drive, driveFolderId, path)
                                bytes += drive.upload(l.uri, parent, l.name, l.mime)
                                uploaded++
                            } else {
                                bytes += downloadToLocal(drive, localTree, r, path)
                                downloaded++
                            }
                            changed++
                        }
                    }
                }
            } catch (_: Exception) {
                failed++
            }
            processed++
            listener?.invoke(Progress(processed, files.size, uploaded, downloaded, changed, failed, bytes, path))
        }

        return Result(processed, uploaded, downloaded, changed, failed, bytes)
    }

    private fun indexLocal(tree: Uri): Map<String, LocalItem> {
        val result = LinkedHashMap<String, LocalItem>()
        val queue = ArrayDeque<Pair<String, String>>()
        queue.add("" to DocumentsContract.getTreeDocumentId(tree))
        while (queue.isNotEmpty() && !cancelled) {
            val (parentPath, parentId) = queue.removeFirst()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
            resolver.query(
                children,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED
                ), null, null, null
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                val modCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                while (c.moveToNext()) {
                    val id = c.getString(idCol)
                    val name = c.getString(nameCol) ?: "Unnamed"
                    val mime = c.getString(mimeCol) ?: "application/octet-stream"
                    val path = if (parentPath.isBlank()) name else "$parentPath/$name"
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        queue.add(path to id)
                    } else {
                        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                        val size = if (c.isNull(sizeCol)) 0L else c.getLong(sizeCol)
                        val modified = if (c.isNull(modCol)) 0L else c.getLong(modCol)
                        result[path] = LocalItem(uri, path, name, mime, size, modified)
                    }
                }
            }
        }
        return result
    }

    private fun indexDrive(drive: DriveClient, rootId: String): Map<String, RemoteItem> {
        val result = LinkedHashMap<String, RemoteItem>()
        val queue = ArrayDeque<Pair<String, String>>()
        queue.add("" to rootId)
        while (queue.isNotEmpty() && !cancelled) {
            val (parentPath, parentId) = queue.removeFirst()
            for (entry in drive.listChildren(parentId)) {
                val path = if (parentPath.isBlank()) entry.name else "$parentPath/${entry.name}"
                val directory = entry.mimeType == DriveClient.FOLDER_MIME
                result[path] = RemoteItem(entry, path, directory)
                if (directory) queue.add(path to entry.id)
            }
        }
        return result
    }

    private fun remoteFolderForPath(drive: DriveClient, rootId: String, path: String): String {
        val parent = path.substringBeforeLast('/', "")
        return drive.ensureFolderPath(rootId, parent)
    }

    private fun downloadToLocal(
        drive: DriveClient,
        localTree: Uri,
        remote: RemoteItem,
        path: String
    ): Long {
        val parentPath = path.substringBeforeLast('/', "")
        val parentId = ensureLocalFolder(localTree, parentPath)
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(localTree, parentId)
        val existing = findLocalChild(localTree, parentId, remote.entry.name)
        val target = existing ?: DocumentsContract.createDocument(
            resolver,
            parentUri,
            remote.entry.mimeType.ifBlank { "application/octet-stream" },
            remote.entry.name
        ) ?: throw IllegalStateException("Unable to create local file $path")

        resolver.openOutputStream(target, "wt").use { output ->
            if (output == null) throw IllegalStateException("Unable to open local file $path")
            return drive.download(remote.entry, output)
        }
    }

    private fun ensureLocalFolder(tree: Uri, path: String): String {
        var current = DocumentsContract.getTreeDocumentId(tree)
        for (part in path.split('/').filter { it.isNotBlank() }) {
            current = findLocalChild(tree, current, part) ?: run {
                val parent = DocumentsContract.buildDocumentUriUsingTree(tree, current)
                val created = DocumentsContract.createDocument(
                    resolver,
                    parent,
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    part
                ) ?: throw IllegalStateException("Unable to create local folder $part")
                DocumentsContract.getDocumentId(created)
            }
        }
        return current
    }

    private fun findLocalChild(tree: Uri, parentId: String, name: String): String? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        resolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            ), null, null, null
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            while (c.moveToNext()) if (c.getString(nameCol) == name) return c.getString(idCol)
        }
        return null
    }

    private fun isLocalNewer(local: LocalItem, remote: RemoteItem): Boolean =
        local.modified > remote.entry.modified || local.size != remote.entry.size
}
