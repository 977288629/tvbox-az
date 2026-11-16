package com.github.avbox.osc.net.download

import com.github.avbox.osc.net.HttpClients
import com.github.avbox.osc.util.AppLog
import com.github.avbox.osc.util.UrlHeaders
import com.github.catvod.net.OkHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object FileDownloader {

    private const val BufferSize = 16 * 1024
    private const val ProgressIntervalMs = 200L
    private const val MaxRetries = 3
    private const val DefaultExt = "mp4"

    @Volatile
    private var client: okhttp3.OkHttpClient? = null

    private fun httpClient(): okhttp3.OkHttpClient {
        val current = client
        if (current != null) return current
        val created = HttpClients.defaultClient ?: OkHttp.client()
        client = created
        return created
    }

    suspend fun download(
        rawUrl: String,
        dest: File,
        tag: String,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val parsed = UrlHeaders.parse(rawUrl)
        val url = parsed.url
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            AppLog.i("echo-dl-fetch-bad: raw=" + rawUrl.take(200) + " clean=" + url.take(200))
            throw IOException("unsupported download url")
        }
        var attempt = 0
        while (true) {
            try {
                return@withContext execute(url, parsed.headers, dest, tag, onProgress)
            } catch (throwable: Throwable) {
                if (throwable is IOException && attempt < MaxRetries && isActive) {
                    attempt++
                    delay(1000L * attempt)
                    continue
                }
                throw throwable
            }
        }
        @Suppress("UNREACHABLE_CODE")
        throw IOException("download failed")
    }

    private suspend fun execute(
        url: String,
        headers: Map<String, String>,
        dest: File,
        tag: String,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): File {
        val tmp = File(dest.parentFile, "${dest.name}.tmp")
        val existing = if (tmp.exists()) tmp.length() else 0L
        val builder = Request.Builder().url(url).tag(tag)
        headers.forEach { (key, value) -> builder.header(key, value) }
        if (existing > 0) builder.header("Range", "bytes=$existing-")
        val request = builder.build()
        val call = httpClient().newCall(request)

        val result = suspendCancellableCoroutine<Result> { cont ->
            call.enqueue(object : okhttp3.Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { resp ->
                        try {
                            if (!resp.isSuccessful && resp.code != 206) {
                                throw IOException("http ${resp.code}")
                            }
                            val body = resp.body ?: throw IOException("empty body")
                            val append = resp.code == 206 && existing > 0
                            val total = if (append) existing + body.contentLength() else body.contentLength()
                            var downloaded = if (append) existing else 0L
                            dest.parentFile?.mkdirs()
                            val stream = body.byteStream()
                            val out = FileOutputStream(tmp, append)
                            val buffer = ByteArray(BufferSize)
                            var lastReport = 0L
                            try {
                                while (true) {
                                    val read = stream.read(buffer)
                                    if (read <= 0) break
                                    out.write(buffer, 0, read)
                                    downloaded += read
                                    val now = System.currentTimeMillis()
                                    if (now - lastReport >= ProgressIntervalMs) {
                                        lastReport = now
                                        onProgress(downloaded, total)
                                    }
                                }
                            } finally {
                                out.flush()
                                out.close()
                                stream.close()
                            }
                            if (downloaded <= 0L) throw IOException("empty file")
                            cont.resume(Result(downloaded, total))
                        } catch (throwable: Throwable) {
                            if (cont.isActive) cont.resumeWithException(throwable)
                        }
                    }
                }
            })
            cont.invokeOnCancellation { call.cancel() }
        }

        onProgress(result.downloaded, result.total)
        if (!tmp.renameTo(dest)) {
            if (dest.exists()) dest.delete()
            val renamed = tmp.renameTo(dest)
            if (!renamed) throw IOException("rename failed")
        }
        return dest
    }

    fun cancel(tag: String) {
        try {
            OkHttp.cancel(tag)
        } catch (ignored: Throwable) {
            AppLog.d("FileDownloader", "cancel failed")
        }
    }

    fun extOf(url: String): String {
        val clean = UrlHeaders.parse(url).url
        val name = clean.substringBefore("?").substringAfterLast('/')
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext == "m3u8") return "ts"
        return if (ext.length in 1..5 && ext.all { it in 'a'..'z' || it in '0'..'9' }) ext else DefaultExt
    }

    fun usedSize(file: File): Long = if (file.exists()) file.length() else 0L

    class Result(
        @JvmField val downloaded: Long,
        @JvmField val total: Long,
    )
}
