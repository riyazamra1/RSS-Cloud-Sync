package com.riyaz.rsscloudsync

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale

class DriveClient(private val context: Context) {
    data class Entry(val id: String, val name: String, val mimeType: String, val size: Long, val modified: Long, val parentId: String)

    companion object {
        const val FOLDER_MIME = "application/vnd.google-apps.folder"
        const val ROOT_ID = "root"
    }

    private fun token(): String {
        val account = GoogleDriveAuthManager.currentAccount(context)
            ?: throw IllegalStateException("Google Drive account is not connected")
        return GoogleDriveAuthManager.accessToken(context, account)
    }

    private fun request(method: String, url: String, body: ByteArray? = null, contentType: String? = null): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.setRequestProperty("Authorization", "Bearer ${token()}")
        connection.setRequestProperty("Accept", "application/json")
        connection.connectTimeout = 30_000
        connection.readTimeout = 120_000
        if (body != null) {
            connection.doOutput = true
            if (contentType != null) connection.setRequestProperty("Content-Type", contentType)
            connection.outputStream.use { it.write(body) }
        }
        val code = connection.responseCode
        val data = (if (code in 200..299) connection.inputStream else connection.errorStream)?.use { it.readBytes() } ?: ByteArray(0)
        connection.disconnect()
        if (code !in 200..299) throw IllegalStateException("Google Drive error $code: ${String(data).take(300)}")
        return data
    }

    fun listChildren(parentId: String): List<Entry> {
        val output = ArrayList<Entry>()
        var pageToken: String? = null
        do {
            val query = URLEncoder.encode("'$parentId' in parents and trashed = false", "UTF-8")
            val suffix = pageToken?.let { "&pageToken=${enc(it)}" } ?: ""
            val url = "https://www.googleapis.com/drive/v3/files?q=$query&pageSize=1000&fields=nextPageToken,files(id,name,mimeType,size,modifiedTime,parents)&orderBy=folder,name$suffix"
            val json = JSONObject(String(request("GET", url)))
            val files = json.optJSONArray("files") ?: JSONArray()
            for (i in 0 until files.length()) {
                val file = files.getJSONObject(i)
                output += Entry(
                    file.getString("id"),
                    file.optString("name"),
                    file.optString("mimeType"),
                    file.optLong("size", 0L),
                    parseTime(file.optString("modifiedTime")),
                    parentId
                )
            }
            pageToken = json.optString("nextPageToken").ifBlank { null }
        } while (pageToken != null)
        return output
    }

    fun findChild(parentId: String, name: String): Entry? = listChildren(parentId).firstOrNull { it.name == name }

    fun createFolder(parentId: String, name: String): String {
        val existing = findChild(parentId, name)
        if (existing != null) {
            if (existing.mimeType != FOLDER_MIME) throw IllegalStateException("Drive item '$name' is not a folder")
            return existing.id
        }
        val metadata = JSONObject().apply {
            put("name", name)
            put("mimeType", FOLDER_MIME)
            put("parents", JSONArray().put(parentId))
        }.toString().toByteArray()
        return JSONObject(String(request("POST", "https://www.googleapis.com/drive/v3/files?fields=id,name,mimeType,parents", metadata, "application/json; charset=UTF-8"))).getString("id")
    }

    fun ensureFolderPath(rootId: String, path: String): String {
        var current = rootId
        for (part in path.split('/').filter { it.isNotBlank() }) current = createFolder(current, part)
        return current
    }

    fun quota(): Triple<Long, Long, Long> {
        val quota = JSONObject(String(request("GET", "https://www.googleapis.com/drive/v3/about?fields=storageQuota"))).optJSONObject("storageQuota")
            ?: return Triple(0L, 0L, 0L)
        return Triple(
            quota.optLong("usage", 0L),
            quota.optLong("limit", 0L),
            quota.optLong("usageInDrive", 0L)
        )
    }

    fun quotaText(): String {
        val (usage, limit, _) = quota()
        return if (limit > 0L) {
            "Used: ${formatBytes(usage)} • Free: ${formatBytes((limit - usage).coerceAtLeast(0L))} • Total: ${formatBytes(limit)}"
        } else {
            "Used: ${formatBytes(usage)} • Total: Unlimited"
        }
    }

    fun upload(
        uri: Uri,
        parentId: String,
        name: String,
        mime: String,
        existingId: String? = null,
        onBytes: ((Long) -> Unit)? = null
    ): Long {
        return if (existingId != null) {
            updateMedia(existingId, uri, mime, onBytes)
        } else {
            uploadNew(uri, parentId, name, mime, onBytes)
        }
    }

    private fun uploadNew(uri: Uri, parentId: String, name: String, mime: String, onBytes: ((Long) -> Unit)?): Long {
        val boundary = "rss-${System.currentTimeMillis()}"
        val metadata = JSONObject().apply {
            put("name", name)
            put("mimeType", mime)
            put("parents", JSONArray().put(parentId))
        }.toString()
        val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open local file")
        val connection = URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,size,mimeType,modifiedTime").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Authorization", "Bearer ${token()}")
        connection.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
        connection.connectTimeout = 30_000
        connection.readTimeout = 120_000
        var total = 0L
        input.use { source ->
            connection.outputStream.use { output ->
                output.write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n".toByteArray())
                output.write("--$boundary\r\nContent-Type: $mime\r\n\r\n".toByteArray())
                BufferedInputStream(source).use { inputStream ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = inputStream.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        total += count
                        onBytes?.invoke(total)
                    }
                }
                output.write("\r\n--$boundary--\r\n".toByteArray())
            }
        }
        val code = connection.responseCode
        val response = (if (code in 200..299) connection.inputStream else connection.errorStream)?.use { it.readBytes() } ?: ByteArray(0)
        connection.disconnect()
        if (code !in 200..299) throw IllegalStateException("Google Drive upload error $code: ${String(response).take(300)}")
        return total
    }

    private fun updateMedia(fileId: String, uri: Uri, mime: String, onBytes: ((Long) -> Unit)?): Long {
        val connection = URL("https://www.googleapis.com/upload/drive/v3/files/${enc(fileId)}?uploadType=media&fields=id,size,mimeType,modifiedTime").openConnection() as HttpURLConnection
        connection.requestMethod = "PATCH"
        connection.doOutput = true
        connection.setRequestProperty("Authorization", "Bearer ${token()}")
        connection.setRequestProperty("Content-Type", mime)
        connection.connectTimeout = 30_000
        connection.readTimeout = 120_000
        var total = 0L
        context.contentResolver.openInputStream(uri)?.use { source ->
            connection.outputStream.use { output ->
                BufferedInputStream(source).use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        total += count
                        onBytes?.invoke(total)
                    }
                }
            }
        } ?: throw IllegalStateException("Cannot open local file")
        val code = connection.responseCode
        val response = (if (code in 200..299) connection.inputStream else connection.errorStream)?.use { it.readBytes() } ?: ByteArray(0)
        connection.disconnect()
        if (code !in 200..299) throw IllegalStateException("Google Drive update error $code: ${String(response).take(300)}")
        return total
    }

    fun download(entry: Entry, output: OutputStream, onBytes: ((Long) -> Unit)? = null): Long {
        if (entry.mimeType.startsWith("application/vnd.google-apps.")) {
            throw IllegalStateException("Google Workspace files cannot be downloaded as binary files")
        }
        val connection = URL("https://www.googleapis.com/drive/v3/files/${enc(entry.id)}?alt=media").openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer ${token()}")
        connection.connectTimeout = 30_000
        connection.readTimeout = 120_000
        if (connection.responseCode !in 200..299) throw IllegalStateException("Google Drive download error ${connection.responseCode}")
        var total = 0L
        BufferedInputStream(connection.inputStream).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
                total += count
                onBytes?.invoke(total)
            }
        }
        output.flush()
        connection.disconnect()
        return total
    }

    fun delete(id: String) { request("DELETE", "https://www.googleapis.com/drive/v3/files/${enc(id)}") }

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
    private fun parseTime(value: String): Long = try {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).parse(value)?.time ?: 0L
    } catch (_: Exception) { 0L }

    private fun formatBytes(value: Long): String {
        if (value < 1024L) return "$value B"
        if (value < 1024L * 1024L) return String.format(Locale.getDefault(), "%.1f KB", value / 1024.0)
        if (value < 1024L * 1024L * 1024L) return String.format(Locale.getDefault(), "%.1f MB", value / (1024.0 * 1024.0))
        if (value < 1024L * 1024L * 1024L * 1024L) return String.format(Locale.getDefault(), "%.2f GB", value / (1024.0 * 1024.0 * 1024.0))
        return String.format(Locale.getDefault(), "%.2f TB", value / (1024.0 * 1024.0 * 1024.0 * 1024.0))
    }
}
