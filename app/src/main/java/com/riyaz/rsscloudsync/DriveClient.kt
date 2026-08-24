package com.riyaz.rsscloudsync

import android.content.Context
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.min

class DriveClient(private val context: Context) {
    data class Entry(val id: String, val name: String, val mimeType: String, val size: Long, val modified: Long, val parentId: String)
    companion object { const val FOLDER_MIME = "application/vnd.google-apps.folder"; const val ROOT_ID = "root" }

    private fun token(): String {
        val account = GoogleDriveAuthManager.currentAccount(context) ?: throw IllegalStateException("Google Drive account is not connected")
        return GoogleDriveAuthManager.accessToken(context, account)
    }

    private fun request(method: String, url: String, body: ByteArray? = null, contentType: String? = null): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method; c.setRequestProperty("Authorization", "Bearer ${token()}"); c.setRequestProperty("Accept", "application/json"); c.connectTimeout = 30000; c.readTimeout = 120000
        if (body != null) { c.doOutput = true; if (contentType != null) c.setRequestProperty("Content-Type", contentType); c.outputStream.use { it.write(body) } }
        val code = c.responseCode
        val data = (if (code in 200..299) c.inputStream else c.errorStream)?.use { it.readBytes() } ?: ByteArray(0)
        c.disconnect()
        if (code !in 200..299) throw IllegalStateException("Google Drive error $code: ${String(data).take(300)}")
        return data
    }

    fun listChildren(parentId: String): List<Entry> {
        val out = ArrayList<Entry>(); var pageToken: String? = null
        do {
            val q = URLEncoder.encode("'$parentId' in parents and trashed = false", "UTF-8")
            val suffix = pageToken?.let { "&pageToken=${enc(it)}" } ?: ""
            val url = "https://www.googleapis.com/drive/v3/files?q=$q&pageSize=1000&fields=nextPageToken,files(id,name,mimeType,size,modifiedTime,parents)&orderBy=folder,name$suffix"
            val json = JSONObject(String(request("GET", url))); val files = json.optJSONArray("files") ?: org.json.JSONArray()
            for (i in 0 until files.length()) { val o = files.getJSONObject(i); out += Entry(o.getString("id"), o.optString("name"), o.optString("mimeType"), o.optLong("size", 0L), parseTime(o.optString("modifiedTime")), parentId) }
            pageToken = json.optString("nextPageToken").ifBlank { null }
        } while (pageToken != null)
        return out
    }

    fun quotaText(): String {
        val q = JSONObject(String(request("GET", "https://www.googleapis.com/drive/v3/about?fields=storageQuota"))).optJSONObject("storageQuota") ?: return "Connected"
        val limit = q.optLong("limit", -1L); val usage = q.optLong("usage", 0L)
        return if (limit > 0) "Used: ${formatBytes(usage)} • Free: ${formatBytes((limit - usage).coerceAtLeast(0))} • Total: ${formatBytes(limit)}" else "Used: ${formatBytes(usage)} • Total: Unlimited"
    }

    fun upload(uri: android.net.Uri, parentId: String, name: String, mime: String, onBytes: ((Long) -> Unit)? = null): Long {
        val boundary = "rss-${System.currentTimeMillis()}"; val meta = JSONObject().apply { put("name", name); put("mimeType", mime); put("parents", org.json.JSONArray().put(parentId)) }.toString()
        val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open local file")
        val c = URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,size,mimeType").openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true; c.setRequestProperty("Authorization", "Bearer ${token()}"); c.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
        var total = 0L
        c.outputStream.use { out ->
            out.write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n".toByteArray()); out.write("--$boundary\r\nContent-Type: $mime\r\n\r\n".toByteArray())
            BufferedInputStream(input).use { ins -> val buf = ByteArray(64 * 1024); while (true) { val n = ins.read(buf); if (n < 0) break; out.write(buf, 0, n); total += n; onBytes?.invoke(total) } }
            out.write("\r\n--$boundary--\r\n".toByteArray())
        }
        val code = c.responseCode; val response = (if (code in 200..299) c.inputStream else c.errorStream)?.use { it.readBytes() } ?: ByteArray(0); c.disconnect()
        if (code !in 200..299) throw IllegalStateException("Google Drive upload error $code: ${String(response).take(300)}")
        return total
    }

    fun download(entry: Entry, output: OutputStream, onBytes: ((Long) -> Unit)? = null): Long {
        val c = URL("https://www.googleapis.com/drive/v3/files/${enc(entry.id)}?alt=media").openConnection() as HttpURLConnection
        c.requestMethod = "GET"; c.setRequestProperty("Authorization", "Bearer ${token()}"); c.connectTimeout = 30000; c.readTimeout = 120000
        if (c.responseCode !in 200..299) throw IllegalStateException("Google Drive download error ${c.responseCode}")
        var total = 0L
        BufferedInputStream(c.inputStream).use { input -> val buffer = ByteArray(64 * 1024); while (true) { val n = input.read(buffer); if (n < 0) break; output.write(buffer, 0, n); total += n; onBytes?.invoke(total) } }
        output.flush(); c.disconnect(); return total
    }

    fun delete(id: String) { request("DELETE", "https://www.googleapis.com/drive/v3/files/${enc(id)}") }
    private fun enc(v: String) = URLEncoder.encode(v, "UTF-8")
    private fun parseTime(v: String): Long = try { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).parse(v)?.time ?: 0L } catch (_: Exception) { 0L }
    private fun formatBytes(v: Long): String { if (v < 1024) return "$v B"; val units = arrayOf("KB", "MB", "GB", "TB"); val power = min(units.lastIndex, kotlin.math.log(v.toDouble(), 1024.0).toInt()); return String.format(Locale.getDefault(), "%.2f %s", v / Math.pow(1024.0, power.toDouble()), units[power]) }
}
