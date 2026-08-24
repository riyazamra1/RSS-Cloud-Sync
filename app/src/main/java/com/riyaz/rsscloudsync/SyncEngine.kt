package com.riyaz.rsscloudsync

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.util.ArrayDeque

/** SAF-based sync engine. Builds one index per side, supports cancellation and progress. */
class SyncEngine(private val resolver: ContentResolver, private val context: Context) {
    enum class Direction { TWO_WAY, UPLOAD_ONLY, UPLOAD_MIRROR, UPLOAD_THEN_DELETE, DOWNLOAD_ONLY, DOWNLOAD_MIRROR, DOWNLOAD_THEN_DELETE }

    data class Progress(val filesProcessed: Int, val totalFiles: Int, val filesChanged: Int, val bytesTransferred: Long, val currentPath: String)
    data class Result(val filesProcessed: Int, val filesChanged: Int, val bytesTransferred: Long, val cancelled: Boolean, val error: String? = null)

    @Volatile private var cancelled = false
    fun cancel() { cancelled = true }

    fun sync(sourceTree: Uri, targetTree: Uri, direction: Direction, listener: ((Progress) -> Unit)? = null): Result {
        cancelled = false
        val started = System.currentTimeMillis()
        return try {
            val source = index(sourceTree)
            val target = index(targetTree)
            if (cancelled) return Result(0, 0, 0, true)
            val result = when (direction) {
                Direction.TWO_WAY -> twoWay(sourceTree, targetTree, source, target, listener)
                Direction.UPLOAD_ONLY -> oneWay(sourceTree, targetTree, source, target, false, listener)
                Direction.UPLOAD_MIRROR -> oneWay(sourceTree, targetTree, source, target, true, listener)
                Direction.UPLOAD_THEN_DELETE -> oneWay(sourceTree, targetTree, source, target, false, listener, true)
                Direction.DOWNLOAD_ONLY -> oneWay(targetTree, sourceTree, target, source, false, listener)
                Direction.DOWNLOAD_MIRROR -> oneWay(targetTree, sourceTree, target, source, true, listener)
                Direction.DOWNLOAD_THEN_DELETE -> oneWay(targetTree, sourceTree, target, source, false, listener, true)
            }
            SyncHistoryManager.add(context, SyncHistoryManager.Entry(System.currentTimeMillis(), direction.name, result.filesProcessed, result.filesChanged, result.bytesTransferred, System.currentTimeMillis() - started, result.error == null && !result.cancelled, result.error ?: if (result.cancelled) "Cancelled" else "Completed"))
            result
        } catch (e: Exception) {
            val result = Result(0, 0, 0, cancelled, e.message ?: "Sync failed")
            SyncHistoryManager.add(context, SyncHistoryManager.Entry(System.currentTimeMillis(), direction.name, 0, 0, 0, System.currentTimeMillis() - started, false, result.error ?: "Sync failed"))
            result
        }
    }

    private fun oneWay(sourceTree: Uri, targetTree: Uri, source: Map<String, Item>, target: Map<String, Item>, mirror: Boolean, listener: ((Progress) -> Unit)?, deleteSourceAfterCopy: Boolean = false): Result {
        var processed = 0; var changed = 0; var bytes = 0L
        val files = source.filterValues { !it.directory }
        for ((path, item) in files) {
            if (cancelled) return Result(processed, changed, bytes, true)
            val existing = target[path]
            if (existing == null || item.size != existing.size || item.modified > existing.modified) {
                bytes += copyFile(sourceTree, targetTree, item, path)
                changed++
                if (deleteSourceAfterCopy) delete(sourceTree, item.id)
            }
            processed++
            listener?.invoke(Progress(processed, files.size, changed, bytes, path))
        }
        if (mirror) for ((path, item) in target) if (!item.directory && !source.containsKey(path)) { if (cancelled) return Result(processed, changed, bytes, true); delete(targetTree, item.id); changed++ }
        return Result(processed, changed, bytes, false)
    }

    private fun twoWay(sourceTree: Uri, targetTree: Uri, source: Map<String, Item>, target: Map<String, Item>, listener: ((Progress) -> Unit)?): Result {
        var processed = 0; var changed = 0; var bytes = 0L
        val paths = LinkedHashSet<String>().apply { addAll(source.keys); addAll(target.keys) }
        for (path in paths) {
            if (cancelled) return Result(processed, changed, bytes, true)
            val a = source[path]; val b = target[path]
            when {
                a != null && !a.directory && b == null -> { bytes += copyFile(sourceTree, targetTree, a, path); changed++ }
                b != null && !b.directory && a == null -> { bytes += copyFile(targetTree, sourceTree, b, path); changed++ }
                a != null && b != null && !a.directory && !b.directory && (a.size != b.size || a.modified != b.modified) -> { if (a.modified >= b.modified) bytes += copyFile(sourceTree, targetTree, a, path) else bytes += copyFile(targetTree, sourceTree, b, path); changed++ }
            }
            processed++; listener?.invoke(Progress(processed, paths.size, changed, bytes, path))
        }
        return Result(processed, changed, bytes, false)
    }

    private data class Item(val id: String, val name: String, val size: Long, val modified: Long, val directory: Boolean)

    private fun index(tree: Uri): Map<String, Item> {
        val result = LinkedHashMap<String, Item>()
        val queue = ArrayDeque<Pair<String, String>>()
        queue.add("" to DocumentsContract.getTreeDocumentId(tree))
        while (queue.isNotEmpty()) {
            if (cancelled) break
            val (parentPath, parentId) = queue.removeFirst()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
            resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED), null, null, null)?.use { c ->
                val id = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID); val name = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME); val mime = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE); val size = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE); val modified = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                while (c.moveToNext()) {
                    val childId = c.getString(id); val childName = c.getString(name) ?: "Unnamed"; val path = if (parentPath.isEmpty()) childName else "$parentPath/$childName"; val dir = c.getString(mime) == DocumentsContract.Document.MIME_TYPE_DIR
                    result[path] = Item(childId, childName, if (c.isNull(size)) 0 else c.getLong(size), if (c.isNull(modified)) 0 else c.getLong(modified), dir)
                    if (dir) queue.add(path to childId)
                }
            }
        }
        return result
    }

    private fun copyFile(sourceTree: Uri, targetTree: Uri, item: Item, path: String): Long {
        val sourceUri = DocumentsContract.buildDocumentUriUsingTree(sourceTree, item.id)
        val parentPath = path.substringBeforeLast('/', "")
        val parentId = if (parentPath.isEmpty()) DocumentsContract.getTreeDocumentId(targetTree) else findDocumentId(targetTree, parentPath) ?: throw IllegalStateException("Target folder not found: $parentPath")
        val existing = findChild(targetTree, parentId, item.name)
        val targetUri = if (existing != null) DocumentsContract.buildDocumentUriUsingTree(targetTree, existing) else DocumentsContract.createDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(targetTree, parentId), "application/octet-stream", item.name) ?: throw IllegalStateException("Unable to create target file: $path")
        resolver.openInputStream(sourceUri).use { input -> resolver.openOutputStream(targetUri, "wt").use { output ->
            if (input == null || output == null) throw IllegalStateException("Unable to open file: $path")
            val inBuf = BufferedInputStream(input); val outBuf = BufferedOutputStream(output); val buffer = ByteArray(64 * 1024); var total = 0L
            while (!cancelled) { val n = inBuf.read(buffer); if (n < 0) break; outBuf.write(buffer, 0, n); total += n }
            outBuf.flush(); return total
        } }
    }

    private fun findDocumentId(tree: Uri, path: String): String? { var current = DocumentsContract.getTreeDocumentId(tree); if (path.isEmpty()) return current; for (part in path.split('/')) current = findChild(tree, current, part) ?: return null; return current }

    private fun findChild(tree: Uri, parentId: String, name: String): String? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c -> val id = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID); val n = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME); while (c.moveToNext()) if (c.getString(n) == name) return c.getString(id) }
        return null
    }

    private fun delete(tree: Uri, id: String) { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, id)) }
}
