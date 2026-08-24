package com.riyaz.rsscloudsync

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.io.IOException
import java.util.ArrayDeque

/** Result of one synchronization run. */
data class SyncResult(
    val copied: Int = 0,
    val deleted: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    val message: String = ""
)

/**
 * Lightweight Storage Access Framework sync engine.
 * The local folder is the primary folder selected in Sync Folders and the
 * external folder acts as the second endpoint until cloud-provider adapters
 * are connected.
 */
class SyncEngine(private val resolver: ContentResolver) {

    fun run(local: Uri, external: Uri, direction: String): SyncResult {
        return when (direction) {
            "Two-way Sync" -> twoWay(local, external)
            "Upload only" -> copyTree(local, external, deleteSource = false)
            "Upload mirror" -> mirror(local, external)
            "Upload then delete" -> copyTree(local, external, deleteSource = true)
            "Download only" -> copyTree(external, local, deleteSource = false)
            "Download mirror" -> mirror(external, local)
            "Download then delete" -> copyTree(external, local, deleteSource = true)
            else -> SyncResult(message = "Unknown sync direction")
        }
    }

    private fun twoWay(a: Uri, b: Uri): SyncResult {
        var result = SyncResult()
        val aFiles = index(a)
        val bFiles = index(b)
        val all = (aFiles.keys + bFiles.keys).toSortedSet()

        for (path in all) {
            val left = aFiles[path]
            val right = bFiles[path]
            try {
                when {
                    left == null && right != null -> result = result.copy(copied = result.copied + copyFile(b, a, right))
                    right == null && left != null -> result = result.copy(copied = result.copied + copyFile(a, b, left))
                    left != null && right != null -> {
                        val delta = left.modified - right.modified
                        if (kotlin.math.abs(delta) > 1500L || left.size != right.size) {
                            if (left.modified >= right.modified) {
                                copyFile(a, b, left)
                            } else {
                                copyFile(b, a, right)
                            }
                            result = result.copy(copied = result.copied + 1)
                        } else {
                            result = result.copy(skipped = result.skipped + 1)
                        }
                    }
                }
            } catch (_: Exception) {
                result = result.copy(failed = result.failed + 1)
            }
        }
        return result.copy(message = summary(result))
    }

    private fun mirror(source: Uri, target: Uri): SyncResult {
        var result = copyTree(source, target, deleteSource = false)
        val sourcePaths = index(source).keys
        val targetFiles = index(target)
        for ((path, file) in targetFiles) {
            if (!sourcePaths.contains(path)) {
                try {
                    delete(file.uri)
                    result = result.copy(deleted = result.deleted + 1)
                } catch (_: Exception) {
                    result = result.copy(failed = result.failed + 1)
                }
            }
        }
        return result.copy(message = summary(result))
    }

    private fun copyTree(source: Uri, target: Uri, deleteSource: Boolean): SyncResult {
        var result = SyncResult()
        for ((_, file) in index(source)) {
            try {
                copyFile(source, target, file)
                result = result.copy(copied = result.copied + 1)
                if (deleteSource) {
                    delete(file.uri)
                    result = result.copy(deleted = result.deleted + 1)
                }
            } catch (_: Exception) {
                result = result.copy(failed = result.failed + 1)
            }
        }
        return result.copy(message = summary(result))
    }

    private fun index(root: Uri): Map<String, Entry> {
        val result = linkedMapOf<String, Entry>()
        val queue = ArrayDeque<Pair<String, String>>()
        val rootId = DocumentsContract.getTreeDocumentId(root)
        queue.add(rootId to "")
        while (queue.isNotEmpty()) {
            val (id, prefix) = queue.removeFirst()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(root, id)
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
                val modifiedCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                while (c.moveToNext()) {
                    val childId = c.getString(idCol)
                    val name = c.getString(nameCol) ?: "unnamed"
                    val path = if (prefix.isEmpty()) name else "$prefix/$name"
                    val mime = c.getString(mimeCol)
                    val childUri = DocumentsContract.buildDocumentUriUsingTree(root, childId)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        queue.add(childId to path)
                    } else {
                        result[path] = Entry(childUri, mime, c.getLongOrZero(sizeCol), c.getLongOrZero(modifiedCol))
                    }
                }
            }
        }
        return result
    }

    private fun copyFile(sourceRoot: Uri, targetRoot: Uri, entry: Entry): Int {
        val path = entry.pathName(index(sourceRoot), entry.uri) ?: throw IOException("Source path unavailable")
        val parts = path.split('/').filter { it.isNotEmpty() }
        if (parts.isEmpty()) throw IOException("Invalid path")
        var parentId = DocumentsContract.getTreeDocumentId(targetRoot)
        for (i in 0 until parts.lastIndex) {
            parentId = findOrCreateDirectory(targetRoot, parentId, parts[i])
        }
        val targetChildren = DocumentsContract.buildChildDocumentsUriUsingTree(targetRoot, parentId)
        var existingId: String? = null
        resolver.query(targetChildren, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            while (c.moveToNext()) if (c.getString(1) == parts.last()) existingId = c.getString(0)
        }
        val targetUri = if (existingId != null) {
            DocumentsContract.buildDocumentUriUsingTree(targetRoot, existingId!!)
        } else {
            DocumentsContract.createDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(targetRoot, parentId), entry.mime, parts.last())
                ?: throw IOException("Cannot create target file")
        }
        resolver.openInputStream(entry.uri).use { input ->
            resolver.openOutputStream(targetUri, "w").use { output ->
                if (input == null || output == null) throw IOException("Cannot open file")
                input.copyTo(output, DEFAULT_BUFFER)
            }
        }
        return 1
    }

    private fun findOrCreateDirectory(root: Uri, parentId: String, name: String): String {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(root, parentId)
        resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(1) == name && c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) return c.getString(0)
            }
        }
        val created = DocumentsContract.createDocument(
            resolver,
            DocumentsContract.buildDocumentUriUsingTree(root, parentId),
            DocumentsContract.Document.MIME_TYPE_DIR,
            name
        ) ?: throw IOException("Cannot create directory")
        return DocumentsContract.getDocumentId(created)
    }

    private fun delete(uri: Uri) {
        if (!DocumentsContract.deleteDocument(resolver, uri)) throw IOException("Delete failed")
    }

    private fun summary(result: SyncResult): String =
        "Copied ${result.copied}, deleted ${result.deleted}, skipped ${result.skipped}, failed ${result.failed}"

    private data class Entry(val uri: Uri, val mime: String?, val size: Long, val modified: Long) {
        fun pathName(map: Map<String, Entry>, uri: Uri): String? = map.entries.firstOrNull { it.value.uri == uri }?.key
    }

    private fun android.database.Cursor.getLongOrZero(index: Int): Long = if (isNull(index)) 0L else getLong(index)

    companion object { private const val DEFAULT_BUFFER = 64 * 1024 }
}
