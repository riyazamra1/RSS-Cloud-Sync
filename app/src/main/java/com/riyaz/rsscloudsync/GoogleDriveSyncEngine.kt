package com.riyaz.rsscloudsync

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.util.ArrayDeque

/** Real Google Drive sync operations for SAF local sources. */
class GoogleDriveSyncEngine(
    private val context: Context,
    private val resolver: ContentResolver
) {
    enum class Direction { UPLOAD_ONLY, UPLOAD_MIRROR, UPLOAD_THEN_DELETE, DOWNLOAD_ONLY, DOWNLOAD_MIRROR, DOWNLOAD_THEN_DELETE, TWO_WAY }
    data class Progress(val processed: Int, val total: Int, val uploaded: Int, val downloaded: Int, val changed: Int, val failed: Int, val bytes: Long, val currentPath: String)
    data class Result(val processed: Int, val uploaded: Int, val downloaded: Int, val changed: Int, val failed: Int, val bytes: Long)
    private data class LocalItem(val uri: Uri, val path: String, val name: String, val mime: String, val size: Long, val modified: Long)
    private data class RemoteItem(val entry: DriveClient.Entry, val path: String, val directory: Boolean)
    @Volatile private var cancelled = false
    fun cancel() { cancelled = true }
    fun isCancelled(): Boolean = cancelled

    fun sync(localTree: Uri, driveFolderId: String, direction: Direction, listener: ((Progress) -> Unit)? = null): Result {
        cancelled = false
        val drive = DriveClient(context)
        val local = indexLocal(localTree)
        val remote = indexDrive(drive, driveFolderId)
        val paths = LinkedHashSet<String>().apply { addAll(local.keys); addAll(remote.keys.filter { !remote.getValue(it).directory }) }
        val files = paths.filter { local[it] != null || remote[it]?.directory != true }
        var processed = 0; var uploaded = 0; var downloaded = 0; var changed = 0; var failed = 0; var bytes = 0L
        for (path in files) {
            if (cancelled) break
            try {
                val l = local[path]; val r = remote[path]
                when (direction) {
                    Direction.UPLOAD_ONLY, Direction.UPLOAD_MIRROR, Direction.UPLOAD_THEN_DELETE -> if (l != null && (r == null || isLocalNewer(l, r))) {
                        bytes += drive.upload(l.uri, remoteFolderForPath(drive, driveFolderId, path), l.name, l.mime, r?.entry?.id); uploaded++; changed++
                        if (direction == Direction.UPLOAD_THEN_DELETE) deleteLocal(l.uri)
                    }
                    Direction.DOWNLOAD_ONLY, Direction.DOWNLOAD_MIRROR, Direction.DOWNLOAD_THEN_DELETE -> if (r != null && (l == null || isRemoteNewer(l, r))) {
                        bytes += downloadToLocal(drive, localTree, r, path); downloaded++; changed++
                        if (direction == Direction.DOWNLOAD_THEN_DELETE && l != null) deleteLocal(l.uri)
                    }
                    Direction.TWO_WAY -> when {
                        l != null && r == null -> { bytes += drive.upload(l.uri, remoteFolderForPath(drive, driveFolderId, path), l.name, l.mime); uploaded++; changed++ }
                        l == null && r != null -> { bytes += downloadToLocal(drive, localTree, r, path); downloaded++; changed++ }
                        l != null && r != null && (l.size != r.entry.size || l.modified != r.entry.modified) -> {
                            if (isLocalNewer(l, r)) { bytes += drive.upload(l.uri, remoteFolderForPath(drive, driveFolderId, path), l.name, l.mime, r.entry.id); uploaded++ }
                            else { bytes += downloadToLocal(drive, localTree, r, path); downloaded++ }
                            changed++
                        }
                    }
                }
            } catch (_: Exception) { failed++ }
            processed++
            listener?.invoke(Progress(processed, files.size, uploaded, downloaded, changed, failed, bytes, path))
        }
        if (!cancelled && direction == Direction.UPLOAD_MIRROR) for ((path, item) in remote) if (!item.directory && !local.containsKey(path)) try { drive.delete(item.entry.id); changed++ } catch (_: Exception) { failed++ }
        if (!cancelled && direction == Direction.DOWNLOAD_MIRROR) for ((path, item) in local) if (!remote.containsKey(path)) try { deleteLocal(item.uri); changed++ } catch (_: Exception) { failed++ }
        return Result(processed, uploaded, downloaded, changed, failed, bytes)
    }

    fun uploadSelectedFiles(files: List<Uri>, driveFolderId: String, listener: ((Progress) -> Unit)? = null): Result {
        cancelled = false
        val drive = DriveClient(context)
        var processed = 0; var uploaded = 0; var failed = 0; var changed = 0; var bytes = 0L
        for (uri in files) {
            if (cancelled) break
            try {
                val name = queryName(uri); val mime = resolver.getType(uri) ?: "application/octet-stream"; val existing = drive.findChild(driveFolderId, name)
                bytes += drive.upload(uri, driveFolderId, name, mime, existing?.id); uploaded++; changed++
            } catch (_: Exception) { failed++ }
            processed++; listener?.invoke(Progress(processed, files.size, uploaded, 0, changed, failed, bytes, queryName(uri)))
        }
        return Result(processed, uploaded, 0, changed, failed, bytes)
    }

    private fun queryName(uri: Uri): String = resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else "file" } ?: "file"
    private fun indexLocal(tree: Uri): Map<String, LocalItem> {
        val result = LinkedHashMap<String, LocalItem>(); val queue = ArrayDeque<Pair<String, String>>(); queue.add("" to DocumentsContract.getTreeDocumentId(tree))
        while (queue.isNotEmpty() && !cancelled) {
            val (parentPath, parentId) = queue.removeFirst(); val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
            resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED), null, null, null)?.use { c ->
                val idCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID); val nameCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME); val mimeCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE); val sizeCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE); val modCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                while (c.moveToNext()) {
                    val id = c.getString(idCol); val name = c.getString(nameCol) ?: "Unnamed"; val mime = c.getString(mimeCol) ?: "application/octet-stream"; val path = if (parentPath.isBlank()) name else "$parentPath/$name"
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) queue.add(path to id) else result[path] = LocalItem(DocumentsContract.buildDocumentUriUsingTree(tree, id), path, name, mime, if (c.isNull(sizeCol)) 0L else c.getLong(sizeCol), if (c.isNull(modCol)) 0L else c.getLong(modCol))
                }
            }
        }
        return result
    }
    private fun indexDrive(drive: DriveClient, rootId: String): Map<String, RemoteItem> {
        val result = LinkedHashMap<String, RemoteItem>(); val queue = ArrayDeque<Pair<String, String>>(); queue.add("" to rootId)
        while (queue.isNotEmpty() && !cancelled) { val (parentPath, parentId) = queue.removeFirst(); for (entry in drive.listChildren(parentId)) { val path = if (parentPath.isBlank()) entry.name else "$parentPath/${entry.name}"; val directory = entry.mimeType == DriveClient.FOLDER_MIME; result[path] = RemoteItem(entry, path, directory); if (directory) queue.add(path to entry.id) } }
        return result
    }
    private fun remoteFolderForPath(drive: DriveClient, rootId: String, path: String): String = drive.ensureFolderPath(rootId, path.substringBeforeLast('/', ""))
    private fun downloadToLocal(drive: DriveClient, localTree: Uri, remote: RemoteItem, path: String): Long {
        val parentId = ensureLocalFolder(localTree, path.substringBeforeLast('/', "")); val parentUri = DocumentsContract.buildDocumentUriUsingTree(localTree, parentId); val existing = findLocalChild(localTree, parentId, remote.entry.name)
        val target = existing?.let { DocumentsContract.buildDocumentUriUsingTree(localTree, it) } ?: DocumentsContract.createDocument(resolver, parentUri, remote.entry.mimeType.ifBlank { "application/octet-stream" }, remote.entry.name) ?: throw IllegalStateException("Unable to create local file $path")
        resolver.openOutputStream(target, "wt").use { output -> if (output == null) throw IllegalStateException("Unable to open local file $path"); return drive.download(remote.entry, output) }
    }
    private fun ensureLocalFolder(tree: Uri, path: String): String {
        var current = DocumentsContract.getTreeDocumentId(tree)
        for (part in path.split('/').filter { it.isNotBlank() }) current = findLocalChild(tree, current, part) ?: run { val created = DocumentsContract.createDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, current), DocumentsContract.Document.MIME_TYPE_DIR, part) ?: throw IllegalStateException("Unable to create local folder $part"); DocumentsContract.getDocumentId(created) }
        return current
    }
    private fun findLocalChild(tree: Uri, parentId: String, name: String): String? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c -> val idCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID); val nameCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME); while (c.moveToNext()) if (c.getString(nameCol) == name) return c.getString(idCol) }
        return null
    }
    private fun deleteLocal(uri: Uri) { if (!DocumentsContract.deleteDocument(resolver, uri)) throw IllegalStateException("Unable to delete local file") }
    private fun isLocalNewer(local: LocalItem, remote: RemoteItem): Boolean = local.modified > remote.entry.modified || local.size != remote.entry.size
    private fun isRemoteNewer(local: LocalItem, remote: RemoteItem): Boolean = remote.entry.modified > local.modified || remote.entry.size != local.size
}
