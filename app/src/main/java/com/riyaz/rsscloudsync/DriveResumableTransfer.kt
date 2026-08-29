package com.riyaz.rsscloudsync

import android.content.ContentResolver
import android.net.Uri
import java.io.BufferedInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Streaming, resumable Google Drive media transfer helper. */
class DriveResumableTransfer(
    private val resolver: ContentResolver,
    private val accessToken: String
) {
    companion object {
        private const val CHUNK_SIZE = 256 * 1024
        private const val CONNECT_TIMEOUT = 30_000
        private const val READ_TIMEOUT = 120_000
    }

    fun upload(
        uri: Uri,
        uploadUrl: String,
        mimeType: String,
        totalBytes: Long,
        onBytes: ((Long) -> Unit)? = null,
        isCancelled: (() -> Boolean)? = null
    ): Long {
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
                val wanted = minOf(buffer.size.toLong(), totalBytes - sent).toInt()
                var read = 0
                while (read < wanted) {
                    val count = input.read(buffer, read, wanted - read)
                    if (count < 0) break
                    read += count
                }
                if (read == 0) throw IllegalStateException("Local file ended before expected size")

                var attempt = 0
                while (true) {
                    if (isCancelled?.invoke() == true) throw TransferCancelledException()
                    val connection = (URL(uploadUrl).openConnection() as HttpURLConnection).apply {
                        requestMethod = "PUT"
                        doOutput = true
                        setRequestProperty("Authorization", "Bearer $accessToken")
                        setRequestProperty("Content-Type", mimeType)
                        setRequestProperty("Content-Length", read.toString())
                        setRequestProperty("Content-Range", "bytes $sent-${sent + read - 1}/$totalBytes")
                        connectTimeout = CONNECT_TIMEOUT
                        readTimeout = READ_TIMEOUT
                    }
                    try {
                        connection.outputStream.use { it.write(buffer, 0, read) }
                        val code = connection.responseCode
                        if (code in 200..299) {
                            sent += read
                            onBytes?.invoke(sent)
                            break
                        }
                        if (code == 308) {
                            val range = connection.getHeaderField("Range")
                            val serverEnd = range?.substringAfter('-')?.toLongOrNull()
                            if (serverEnd != null && serverEnd + 1 > sent) sent = serverEnd + 1
                            onBytes?.invoke(sent)
                            break
                        }
                        if (code == 429 || code in 500..599) {
                            if (++attempt > 4) throw IllegalStateException("Google Drive upload error $code")
                            Thread.sleep(500L shl (attempt - 1))
                            continue
                        }
                        throw IllegalStateException("Google Drive upload error $code")
                    } finally {
                        connection.disconnect()
                    }
                }
            }
            return sent
        }
    }

    class TransferCancelledException : IllegalStateException("Transfer cancelled")
}
