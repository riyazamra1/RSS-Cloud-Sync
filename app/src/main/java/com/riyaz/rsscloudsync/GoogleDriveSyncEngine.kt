package com.riyaz.rsscloudsync

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.util.ArrayDeque

/**
 * Google Drive synchronization engine.
 *
 * Keeps file indexing off the UI thread, caches remote folder IDs during a run,
 * reports progress after every operation, and never performs a delete until
 * the corresponding transfer has completed successfully.
 */
class GoogleDriveSyncEngine(
    private val context: Context,
    private val resolver: ContentResolver
) {
    enum class Direction {
        UPLOAD_ONLY,
        UPLOAD_MIRROR,
        UPLOAD_THEN_DELETE,
        DOWNLOAD_ONLY,
        DOWNLOAD_MIRROR,
        DOWNLOAD_THEN_DELETE,
        TWO_WAY
    }

    data class Options(
        val excludeHiddenFiles: Boolean = true,
        val excludeSubfolders: Boolean = false,
        val deleteEmptySubfolders: Boolean = false
    )

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
        val bytes: Long,
        val cancelled: Boolean = false
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

    fun cancel() {
        cancelled = true
    }

    fun isCancelled(): Boolean = cancelled

    fun sync(
        localTree: Uri,
        driveFolderId: String,
        direction: Direction,
        options: Options = Options(),
        listener: ((Progress) -> Unit)? = null
    ): Result {
        cancelled = false
        val drive = DriveClient(context)

        // Build both indexes once. This avoids repeatedly walking SAF/Drive while
        // processing individual files and is especially important for large folders.
        val local = indexLocal(localTree, options)
        if (cancelled) return Result(0, 0, 0, 0, 0, 0, cancelled = true)
        val remote = indexDrive(drive, driveFolderId)
        if (cancelled) return Result(0, 0, 0, 0, 0, 0, cancelled = true)

        val paths = LinkedHashSet<String>().apply {
            addAll(local.keys)
            addAll(remote.keys.filter { !remote.getValue(it).directory })
        }
        val files = paths.filter { local[it] != null || remote[it]?.directory != true }

        // Cache Drive folder IDs for the entire run. The previous implementation
        // repeatedly called listChildren/createFolder for every uploaded file.
        val remoteFolderCache = HashMap<String, String>()
        remoteFolderCache[""] = driveFolderId

        var processed = 0
        var uploaded = 0
        var downloaded = 0
        var changed = 0
        var failed = 0
        var bytes = 0L

        fun report(path: String) {
            processed++
            listener?.invoke(
                Progress(processed, files.size, uploaded, downloaded, changed, failed, bytes, path)
            )
        }

        for (path in files) {
            if (cancelled) break

            try {
                val localItem = local[path]
                val remoteItem = remote[path]

                when (direction) {
                    Direction.UPLOAD_ONLY -> {
                        // Upload Only means local is authoritative. If the local
                        // file exists, upload/update it regardless of remote age.
                        if (localItem != null) {
                            bytes += drive.upload(
                                localItem.uri,
                                remoteFolderForPath(drive, driveFolderId, path, remoteFolderCache),
                                localItem.name,
                                localItem.mime,
                                remoteItem?.entry?.id
                            )
                            uploaded++
                            changed++
                        }
                    }

                    Direction.UPLOAD_MIRROR -> {
                        if (localItem != null && (remoteItem == null || isLocalNewer(localItem, remoteItem))) {
                            bytes += drive.upload(
                                localItem.uri,
                                remoteFolderForPath(drive, driveFolderId, path, remoteFolderCache),
                                localItem.name,
                                localItem.mime,
                                remoteItem?.entry?.id
                            )
                            uploaded++
                            changed++
                        }
                    }

                    Direction.UPLOAD_THEN_DELETE -> {
                        if (localItem != null) {
                            bytes += drive.upload(
                                localItem.uri,
                                remoteFolderForPath(drive, driveFolderId, path, remoteFolderCache),
                                localItem.name,
                                localItem.mime,
                                remoteItem?.entry?.id
                            )
                            uploaded++
                            changed++
                            // Delete only after Drive accepted the complete upload.
                            deleteLocal(localItem.uri)
                        }
                    }

                    Direction.DOWNLOAD_ONLY -> {
                        // Download Only means remote is authoritative. If the
                        // remote file exists, download/update it regardless of age.
                        if (remoteItem != null) {
                            bytes += downloadToLocal(drive, localTree, remoteItem, path)
                            downloaded++
                            changed++
                        }
                    }

                    Direction.DOWNLOAD_MIRROR -> {
                        if (remoteItem != null && (localItem == null || isRemoteNewer(localItem, remoteItem))) {
                            bytes += downloadToLocal(drive, localTree, remoteItem, path)
                            downloaded++
                            changed++
                        }
                    }

                    Direction.DOWNLOAD_THEN_DELETE -> {
                        if (remoteItem != null) {
                            bytes += downloadToLocal(drive, localTree, remoteItem, path)
                            downloaded++
                            changed++
                            // Delete only after the local file was fully written.
                            drive.delete(remoteItem.entry.id)
                        }
                    }

                    Direction.TWO_WAY -> {
                        when {
                            localItem != null && remoteItem == null -> {
                                bytes += drive.upload(
                                    localItem.uri,
                                    remoteFolderForPath(drive, driveFolderId, path, remoteFolderCache),
                                    localItem.name,
                                    localItem.mime
                                )
                                uploaded++
                                changed++
                            }

                            localItem == null && remoteItem != null -> {
                                bytes += downloadToLocal(drive, localTree, remoteItem, path)
                                downloaded++
                                changed++
                            }

                            localItem != null && remoteItem != null && isDifferent(localItem, remoteItem) -> {
                                if (isLocalNewer(localItem, remoteItem)) {
                                    bytes += drive.upload(
                                        localItem.uri,
                                        remoteFolderForPath(drive, driveFolderId, path, remoteFolderCache),
                                        localItem.name,
                                        localItem.mime,
                                        remoteItem.entry.id
                                    )
                                    uploaded++
                                } else {
                                    bytes += downloadToLocal(drive, localTree, remoteItem, path)
                                    downloaded++
                                }
                                changed++
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                failed++
            }

            report(path)
        }

        // Mirror operations are destructive. They run only after the normal
        // transfer pass and only when the transfer itself was not cancelled.
        if (!cancelled && direction == Direction.UPLOAD_MIRROR) {
            for ((_, item) in remote) {
                if (cancelled) break
                if (!item.directory && !local.containsKey(item.path)) {
                    try {
                        drive.delete(item.entry.id)
                        changed++
                    } catch (_: Exception) {
                        failed++
                    }
                }
            }
        }

        if (!cancelled && direction == Direction.DOWNLOAD_MIRROR) {
            for ((_, item) in local) {
                if (cancelled) break
                if (!remote.containsKey(item.path)) {
                    try {
                        deleteLocal(item.uri)
                        changed++
                    } catch (_: Exception) {
                        failed++
                    }
                }
            }
        }

        if (!cancelled && options.deleteEmptySubfolders) {
            deleteEmptyLocalFolders(localTree)
        }

        return Result(
            processed = processed,
            uploaded = uploaded,
            downloaded = downloaded,
            changed = changed,
            failed = failed,
            bytes = bytes,
            cancelled = cancelled
        )
    }

    fun uploadSelectedFiles(
        files: List<Uri>,
        driveFolderId: String,
        listener: ((Progress) -> Unit)? = null
    ): Result {
        cancelled = false
        val drive = DriveClient(context)
        var processed = 0
        var uploaded = 0
        var failed = 0
        var changed = 0
        var bytes = 0L

        for (uri in files) {
            if (cancelled) break
            val name = queryName(uri)
            try {
                val mime = resolver.getType(uri) ?: "application/octet-stream"
                val existing = drive.findChild(driveFolderId, name)
                bytes += drive.upload(uri, driveFolderId, name, mime, existing?.id)
                uploaded++
                changed++
            } catch (_: Exception) {
                failed++
            }
            processed++
            listener?.invoke(
                Progress(processed, files.size, uploaded, 0, changed, failed, bytes, name)
            )
        }

        return Result(processed, uploaded, 0, changed, failed, bytes, cancelled)
    }

    private fun queryName(uri: Uri): String =
        resolver.query(
            uri,
            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use {
            if (it.moveToFirst()) it.getString(0) else "file"
        } ?: "file"

    private fun indexLocal(tree: Uri, options: Options): Map<String, LocalItem> {
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
                ),
                null,
                null,
                null
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                val modCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (c.moveToNext()) {
                    val id = c.getString(idCol)
                    val name = c.getString(nameCol) ?: "Unnamed"
                    if (options.excludeHiddenFiles && name.startsWith('.')) continue

                    val mime = c.getString(mimeCol) ?: "application/octet-stream"
                    val path = if (parentPath.isBlank()) name else "$parentPath/$name"

                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (!options.excludeSubfolders) queue.add(path to id)
                    } else {
                        result[path] = LocalItem(
                            uri = DocumentsContract.buildDocumentUriUsingTree(tree, id),
                            path = path,
                            name = name,
                            mime = mime,
                            size = if (c.isNull(sizeCol)) 0L else c.getLong(sizeCol),
                            modified = if (c.isNull(modCol)) 0L else c.getLong(modCol)
                        )
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
                if (cancelled) break
                val path = if (parentPath.isBlank()) entry.name else "$parentPath/${entry.name}"
                val directory = entry.mimeType == DriveClient.FOLDER_MIME
                result[path] = RemoteItem(entry, path, directory)
                if (directory) queue.add(path to entry.id)
            }
        }
        return result
    }

    private fun remoteFolderForPath(
        drive: DriveClient,
        rootId: String,
        path: String,
        cache: MutableMap<String, String>
    ): String {
        val folderPath = path.substringBeforeLast('/', "")
        if (folderPath.isBlank()) return rootId
        cache[folderPath]?.let { return it }

        var parent = rootId
        val built = StringBuilder()
        for (part in folderPath.split('/').filter { it.isNotBlank() }) {
            if (built.isNotEmpty()) built.append('/')
            built.append(part)
            val key = built.toString()
            parent = cache[key] ?: drive.createFolder(parent, part).also { cache[key] = it }
        }
        return parent
    }

    private fun downloadToLocal(
        drive: DriveClient,
        localTree: Uri,
        remote: RemoteItem,
        path: String
    ): Long {
        val parentId = ensureLocalFolder(localTree, path.substringBeforeLast('/', ""))
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(localTree, parentId)
        val existing = findLocalChild(localTree, parentId, remote.entry.name)
        val target = existing?.let {
            DocumentsContract.buildDocumentUriUsingTree(localTree, it)
        } ?: DocumentsContract.createDocument(
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
                val created = DocumentsContract.createDocument(
                    resolver,
                    DocumentsContract.buildDocumentUriUsingTree(tree, current),
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
            ),
            null,
            null,
            null
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            while (c.moveToNext()) {
                if (c.getString(nameCol) == name) return c.getString(idCol)
            }
        }
        return null
    }

    private fun deleteLocal(uri: Uri) {
        if (!DocumentsContract.deleteDocument(resolver, uri)) {
            throw IllegalStateException("Unable to delete local file")
        }
    }

    private fun deleteEmptyLocalFolders(tree: Uri) {
        val root = DocumentsContract.getTreeDocumentId(tree)
        deleteEmptyChildren(tree, root)
    }

    private fun deleteEmptyChildren(tree: Uri, parentId: String): Boolean {
        var empty = true
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val dirs = ArrayList<String>()

        resolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            ),
            null,
            null,
            null
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val mimeCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (c.moveToNext()) {
                val id = c.getString(idCol)
                if (c.getString(mimeCol) == DocumentsContract.Document.MIME_TYPE_DIR) dirs.add(id)
                else empty = false
            }
        }

        for (id in dirs) {
            if (deleteEmptyChildren(tree, id)) {
                try {
                    DocumentsContract.deleteDocument(
                        resolver,
                        DocumentsContract.buildDocumentUriUsingTree(tree, id)
                    )
                } catch (_: Exception) {
                    // Provider may refuse directory deletion; leave it intact.
                }
            } else {
                empty = false
            }
        }
        return empty
    }

    private fun isDifferent(local: LocalItem, remote: RemoteItem): Boolean =
        local.size != remote.entry.size || local.modified != remote.entry.modified

    private fun isLocalNewer(local: LocalItem, remote: RemoteItem): Boolean =
        when {
            local.modified > remote.entry.modified -> true
            local.modified < remote.entry.modified -> false
            else -> local.size != remote.entry.size
        }

    private fun isRemoteNewer(local: LocalItem, remote: RemoteItem): Boolean =
        when {
            remote.entry.modified > local.modified -> true
            remote.entry.modified < local.modified -> false
            else -> remote.entry.size != local.size
        }
}
