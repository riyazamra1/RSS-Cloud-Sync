package com.riyaz.rsscloudsync

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.util.ArrayDeque

/** SAF sync engine with indexed comparison, nested-folder creation, cancellation and detailed statistics. */
class SyncEngine(private val resolver: ContentResolver, private val context: Context) {
    enum class Direction { TWO_WAY, UPLOAD_ONLY, UPLOAD_MIRROR, UPLOAD_THEN_DELETE, DOWNLOAD_ONLY, DOWNLOAD_MIRROR, DOWNLOAD_THEN_DELETE }

    data class Progress(
        val filesProcessed: Int,
        val totalFiles: Int,
        val filesChanged: Int,
        val uploadedFiles: Int,
        val downloadedFiles: Int,
        val videoFiles: Int,
        val audioFiles: Int,
        val documentFiles: Int,
        val otherFiles: Int,
        val bytesTransferred: Long,
        val currentPath: String
    )

    data class Result(
        val filesProcessed: Int,
        val filesChanged: Int,
        val uploadedFiles: Int,
        val downloadedFiles: Int,
        val videoFiles: Int,
        val audioFiles: Int,
        val documentFiles: Int,
        val otherFiles: Int,
        val bytesTransferred: Long,
        val cancelled: Boolean,
        val error: String? = null
    )

    @Volatile private var cancelled = false
    fun cancel() { cancelled = true }

    fun sync(sourceTree: Uri, targetTree: Uri, direction: Direction, listener: ((Progress) -> Unit)? = null): Result {
        cancelled = false
        val started = System.currentTimeMillis()
        return try {
            val source = index(sourceTree)
            val target = index(targetTree)
            if (cancelled) return recordAndReturn(started, direction, Result(0, 0, 0, 0, 0, 0, 0, 0, 0, true))
            val result = when (direction) {
                Direction.TWO_WAY -> twoWay(sourceTree, targetTree, source, target, listener)
                Direction.UPLOAD_ONLY -> oneWay(sourceTree, targetTree, source, target, false, listener, false)
                Direction.UPLOAD_MIRROR -> oneWay(sourceTree, targetTree, source, target, true, listener, false)
                Direction.UPLOAD_THEN_DELETE -> oneWay(sourceTree, targetTree, source, target, false, listener, true)
                Direction.DOWNLOAD_ONLY -> oneWay(targetTree, sourceTree, target, source, false, listener, false)
                Direction.DOWNLOAD_MIRROR -> oneWay(targetTree, sourceTree, target, source, true, listener, false)
                Direction.DOWNLOAD_THEN_DELETE -> oneWay(targetTree, sourceTree, target, source, false, listener, true)
            }
            recordAndReturn(started, direction, result)
        } catch (e: Exception) {
            recordAndReturn(started, direction, Result(0, 0, 0, 0, 0, 0, 0, 0, 0, cancelled, e.message ?: "Sync failed"))
        }
    }

    private fun recordAndReturn(started: Long, direction: Direction, result: Result): Result {
        SyncHistoryManager.add(context, SyncHistoryManager.Entry(
            timestamp = System.currentTimeMillis(), direction = direction.name,
            filesProcessed = result.filesProcessed, filesChanged = result.filesChanged,
            uploadedFiles = result.uploadedFiles, downloadedFiles = result.downloadedFiles,
            videoFiles = result.videoFiles, audioFiles = result.audioFiles,
            documentFiles = result.documentFiles, otherFiles = result.otherFiles,
            bytesTransferred = result.bytesTransferred,
            durationMs = System.currentTimeMillis() - started,
            success = result.error == null && !result.cancelled,
            message = result.error ?: if (result.cancelled) "Cancelled" else "Sync completed"
        ))
        return result
    }

    private data class Stats(
        var processed: Int = 0, var changed: Int = 0, var uploaded: Int = 0, var downloaded: Int = 0,
        var video: Int = 0, var audio: Int = 0, var documents: Int = 0, var other: Int = 0, var bytes: Long = 0
    ) {
        fun addCategory(name: String) {
            when (category(name)) {
                Category.VIDEO -> video++
                Category.AUDIO -> audio++
                Category.DOCUMENT -> documents++
                Category.OTHER -> other++
            }
        }
    }

    private enum class Category { VIDEO, AUDIO, DOCUMENT, OTHER }

    private fun category(name: String): Category = when (name.substringAfterLast('.', "").lowercase()) {
        "mp4", "mkv", "mov", "avi", "webm", "3gp", "m4v", "flv", "wmv", "mpeg", "mpg" -> Category.VIDEO
        "mp3", "wav", "m4a", "aac", "flac", "ogg", "opus", "wma", "amr" -> Category.AUDIO
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "rtf", "odt", "ods", "odp", "epub" -> Category.DOCUMENT
        else -> Category.OTHER
    }

    private fun oneWay(sourceTree: Uri, targetTree: Uri, source: Map<String, Item>, target: Map<String, Item>, mirror: Boolean, listener: ((Progress) -> Unit)?, deleteSourceAfterCopy: Boolean): Result {
        val stats = Stats()
        val files = source.filterValues { !it.directory }
        for ((path, item) in files) {
            if (cancelled) return result(stats, true)
            val existing = target[path]
            if (existing == null || item.size != existing.size || item.modified > existing.modified) {
                stats.bytes += copyFile(sourceTree, targetTree, item, path)
                stats.changed++
                stats.uploaded++
                stats.addCategory(item.name)
                if (deleteSourceAfterCopy) delete(sourceTree, item.id)
            }
            stats.processed++
            listener?.invoke(progress(stats, files.size, path))
        }
        if (mirror) for ((path, item) in target) if (!item.directory && !source.containsKey(path)) {
            if (cancelled) return result(stats, true)
            delete(targetTree, item.id)
            stats.changed++
        }
        return result(stats, false)
    }

    private fun twoWay(sourceTree: Uri, targetTree: Uri, source: Map<String, Item>, target: Map<String, Item>, listener: ((Progress) -> Unit)?): Result {
        val stats = Stats()
        val paths = LinkedHashSet<String>().apply { addAll(source.keys); addAll(target.keys) }
        val filePaths = paths.filter { !(source[it]?.directory == true || target[it]?.directory == true) }
        for (path in filePaths) {
            if (cancelled) return result(stats, true)
            val a = source[path]; val b = target[path]
            when {
                a != null && b == null -> {
                    stats.bytes += copyFile(sourceTree, targetTree, a, path)
                    stats.changed++; stats.uploaded++; stats.addCategory(a.name)
                }
                b != null && a == null -> {
                    stats.bytes += copyFile(targetTree, sourceTree, b, path)
                    stats.changed++; stats.downloaded++; stats.addCategory(b.name)
                }
                a != null && b != null && (a.size != b.size || a.modified != b.modified) -> {
                    if (a.modified >= b.modified) {
                        stats.bytes += copyFile(sourceTree, targetTree, a, path)
                        stats.uploaded++; stats.addCategory(a.name)
                    } else {
                        stats.bytes += copyFile(targetTree, sourceTree, b, path)
                        stats.downloaded++; stats.addCategory(b.name)
                    }
                    stats.changed++
                }
            }
            stats.processed++
            listener?.invoke(progress(stats, filePaths.size, path))
        }
        return result(stats, false)
    }

    private fun progress(stats: Stats, total: Int, path: String) = Progress(
        stats.processed, total, stats.changed, stats.uploaded, stats.downloaded,
        stats.video, stats.audio, stats.documents, stats.other, stats.bytes, path
    )

    private fun result(stats: Stats, cancelled: Boolean) = Result(
        stats.processed, stats.changed, stats.uploaded, stats.downloaded,
        stats.video, stats.audio, stats.documents, stats.other, stats.bytes, cancelled
    )

    private data class Item(val id: String, val name: String, val mimeType: String, val size: Long, val modified: Long, val directory: Boolean)

    private fun index(tree: Uri): Map<String, Item> {
        val result = LinkedHashMap<String, Item>()
        val queue = ArrayDeque<Pair<String, String>>()
        queue.add("" to DocumentsContract.getTreeDocumentId(tree))
        while (queue.isNotEmpty()) {
            if (cancelled) break
            val (parentPath, parentId) = queue.removeFirst()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
            resolver.query(children, arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            ), null, null, null)?.use { c ->
                val id = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val name = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mime = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val size = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                val modified = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                while (c.moveToNext()) {
                    val childId = c.getString(id)
                    val childName = c.getString(name) ?: "Unnamed"
                    val path = if (parentPath.isEmpty()) childName else "$parentPath/$childName"
                    val childMime = c.getString(mime) ?: "application/octet-stream"
                    val dir = childMime == DocumentsContract.Document.MIME_TYPE_DIR
                    result[path] = Item(childId, childName, childMime, if (c.isNull(size)) 0 else c.getLong(size), if (c.isNull(modified)) 0 else c.getLong(modified), dir)
                    if (dir) queue.add(path to childId)
                }
            }
        }
        return result
    }

    private fun copyFile(sourceTree: Uri, targetTree: Uri, item: Item, path: String): Long {
        val sourceUri = DocumentsContract.buildDocumentUriUsingTree(sourceTree, item.id)
        val parentPath = path.substringBeforeLast('/', "")
        val parentId = ensureFolderPath(targetTree, parentPath)
        val existing = findChild(targetTree, parentId, item.name)
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(targetTree, parentId)
        val targetUri = if (existing != null) {
            DocumentsContract.buildDocumentUriUsingTree(targetTree, existing)
        } else {
            DocumentsContract.createDocument(resolver, parentUri, item.mimeType, item.name)
                ?: throw IllegalStateException("Unable to create target file: $path")
        }
        resolver.openInputStream(sourceUri).use { input ->
            resolver.openOutputStream(targetUri, "wt").use { output ->
                if (input == null || output == null) throw IllegalStateException("Unable to open file: $path")
                BufferedInputStream(input).use { inBuf -> BufferedOutputStream(output).use { outBuf ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (!cancelled) {
                        val n = inBuf.read(buffer)
                        if (n < 0) break
                        outBuf.write(buffer, 0, n)
                        total += n
                    }
                    outBuf.flush()
                    if (cancelled) throw SyncCancelledException()
                    return total
                }}
            }
        }
    }

    private fun ensureFolderPath(tree: Uri, path: String): String {
        var current = DocumentsContract.getTreeDocumentId(tree)
        if (path.isEmpty()) return current
        for (part in path.split('/').filter { it.isNotEmpty() }) {
            current = findChild(tree, current, part) ?: run {
                val parentUri = DocumentsContract.buildDocumentUriUsingTree(tree, current)
                DocumentsContract.createDocument(resolver, parentUri, DocumentsContract.Document.MIME_TYPE_DIR, part)?.let { created ->
                    DocumentsContract.getDocumentId(created)
                } ?: throw IllegalStateException("Unable to create target folder: $part")
            }
        }
        return current
    }

    private fun findChild(tree: Uri, parentId: String, name: String): String? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            val id = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val n = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            while (c.moveToNext()) if (c.getString(n) == name) return c.getString(id)
        }
        return null
    }

    private fun delete(tree: Uri, id: String) {
        DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, id))
    }

    private class SyncCancelledException : Exception("Sync cancelled")
}
