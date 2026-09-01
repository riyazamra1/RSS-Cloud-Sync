package com.riyaz.rsscloudsync

import android.content.ContentResolver
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Streaming, chunked and retry-aware Google Drive media transfer helper. */
class DriveResumableTransfer(
    private val resolver: ContentResolver,
    private val accessToken: String
) {
    companion object {
        private const val CHUNK_SIZE = 256 * 1024
        private const val CONNECT_TIMEOUT = 30_000
        private const val READ_TIMEOUT = 120_000
        private const val MAX_RETRIES = 5
    }

    fun startSession(parentId: String?, name: String?, mimeType: String, existingId: String? = null): String {
        val url = if (existingId == null) {
            "https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable"
        } else {
            "https://www.googleapis.com/upload/drive/v3/files/${enc(existingId)}?uploadType=resumable"
        }
        val metadata = JSONObject().apply {
            if (name != null) put("name", name)
            put("mimeType", mimeType)
            if (parentId != null) put("parents", JSONArray().put(parentId))
        }.toString().toByteArray()
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = if (existingId == null) "POST" else "PATCH"
            doOutput = true
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("X-Upload-Content-Type", mimeType)
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
        }
        try {
            connection.outputStream.use { it.write(metadata) }
            val code = connection.responseCode
            if (code !in 200..299) {
                val body = connection.errorStream?.use { it.readBytes() } ?: ByteArray(0)
                throw IllegalStateException("Google Drive resumable session error $code: ${String(body).take(300)}")
            }
            return connection.getHeaderField("Location")
                ?: throw IllegalStateException("Google Drive did not return an upload session URL")
        } finally {
            connection.disconnect()
        }
    }

    fun upload(uri: Uri, uploadUrl: String, mimeType: String, totalBytes: Long, onBytes: ((Long) -> Unit)? = null, isCancelled: (() -> Boolean)? = null): Long {
        resolver.openInputStream(uri)?.use { input ->
            return uploadStream(input, uploadUrl, mimeType, totalBytes, onBytes, isCancelled)
        } ?: throw IllegalStateException("Cannot open local file")
    }

    private fun uploadStream(
        source: InputStream,
        uploadUrl: String,
        mimeType: String,
        totalBytes: Long,
        onBytes: ((Long) -> Unit)?,
        isCancelled: (() -> Boolean)?
    ): Long {
        BufferedInputStream(source, CHUNK_SIZE).use { input ->
            val buffer = ByteArray(CHUNK_SIZE)
            var sent = 0L

            while (sent < totalBytes) {
                if (isCancelled?.invoke() == true) throw TransferCancelledException()

                val chunkStart = sent
                val wanted = minOf(buffer.size.toLong(), totalBytes - chunkStart).toInt()
                var read = 0
                while (read < wanted) {
                    val count = input.read(buffer, read, wanted - read)
                    if (count < 0) break
                    read += count
                }
                if (read == 0) throw IllegalStateException("Local file ended before expected size")

                var acknowledged = chunkStart
                var attempt = 0
                while (acknowledged < chunkStart + read) {
                    if (isCancelled?.invoke() == true) throw TransferCancelledException()
                    val offsetInBuffer = (acknowledged - chunkStart).toInt()
                    val remaining = read - offsetInBuffer
                    val connection = (URL(uploadUrl).openConnection() as HttpURLConnection).apply {
                        requestMethod = "PUT"
                        doOutput = true
                        setRequestProperty("Authorization", "Bearer $accessToken")
                        setRequestProperty("Content-Type", mimeType)
                        setRequestProperty("Content-Length", remaining.toString())
                        setRequestProperty("Content-Range", "bytes $acknowledged-${acknowledged + remaining - 1}/$totalBytes")
                        connectTimeout = CONNECT_TIMEOUT
                        readTimeout = READ_TIMEOUT
                    }

                    var shouldRetry = false
                    try {
                        connection.outputStream.use { it.write(buffer, offsetInBuffer, remaining) }
                        when (val code = connection.responseCode) {
                            in 200..299 -> {
                                acknowledged = chunkStart + read
                                sent = acknowledged
                                onBytes?.invoke(sent)
                            }
                            308 -> {
                                val range = connection.getHeaderField("Range")
                                val serverEnd = range?.substringAfter('-')?.toLongOrNull()
                                acknowledged = when {
                                    serverEnd != null -> (serverEnd + 1).coerceIn(chunkStart, chunkStart + read)
                                    else -> queryUploadOffset(uploadUrl, totalBytes, chunkStart)
                                }
                                if (acknowledged > chunkStart + read) {
                                    throw IllegalStateException("Google Drive acknowledged bytes beyond current chunk")
                                }
                                sent = acknowledged
                                onBytes?.invoke(sent)
                            }
                            429, in 500..599 -> {
                                shouldRetry = true
                            }
                            else -> {
                                val body = connection.errorStream?.use { it.readBytes() } ?: ByteArray(0)
                                throw IllegalStateException("Google Drive upload error $code: ${String(body).take(300)}")
                            }
                        }
                    } catch (_: IOException) {
                        shouldRetry = true
                    } finally {
                        connection.disconnect()
                    }

                    if (shouldRetry) {
                        if (++attempt > MAX_RETRIES) {
                            throw IllegalStateException("Google Drive upload failed after $MAX_RETRIES retries")
                        }
                        acknowledged = queryUploadOffset(uploadUrl, totalBytes, acknowledged)
                            .coerceIn(chunkStart, chunkStart + read)
                        sent = acknowledged
                        onBytes?.invoke(sent)
                        Thread.sleep(500L shl (attempt - 1))
                    }
                }
            }
            return sent
        }
    }

    /** Ask Drive how many bytes the resumable session has already received. */
    private fun queryUploadOffset(uploadUrl: String, totalBytes: Long, fallback: Long): Long {
        val connection = (URL(uploadUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            doOutput = true
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Length", "0")
            setRequestProperty("Content-Range", "bytes */$totalBytes")
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
        }
        return try {
            when (val code = connection.responseCode) {
                308 -> connection.getHeaderField("Range")?.substringAfter('-')?.toLongOrNull()?.plus(1) ?: fallback
                in 200..299 -> totalBytes
                else -> fallback
            }
        } catch (_: IOException) {
            fallback
        } finally {
            connection.disconnect()
        }
    }

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")

    class TransferCancelledException : IllegalStateException("Transfer cancelled")
}
