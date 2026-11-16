package com.github.avbox.osc.net.download

import com.github.avbox.osc.util.AppLog
import com.github.avbox.osc.util.UrlHeaders
import com.github.catvod.net.OkHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object M3u8Downloader {

    private const val MaxConcurrent = 6

    suspend fun download(
        rawUrl: String,
        dest: File,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val parsed = UrlHeaders.parse(rawUrl)
        val masterUrl = parsed.url
        val headers = parsed.headers
        val manifest = fetchText(masterUrl, headers)
        val variant = resolveVariant(manifest, masterUrl)
        if (variant != null) {
            val variantText = fetchText(variant.first, headers)
            return@withContext downloadSegments(variantText, variant.first, headers, dest, onProgress)
        }
        downloadSegments(manifest, masterUrl, headers, dest, onProgress)
    }

    private fun resolveVariant(manifest: String, baseUrl: String): Pair<String, Map<String, String>>? {
        val lines = manifest.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.none { it.startsWith("#EXT-X-STREAM-INF") }) return null
        var bestBandwidth = -1L
        var bestUrl: String? = null
        for (i in lines.indices) {
            if (!lines[i].startsWith("#EXT-X-STREAM-INF")) continue
            val bandwidth = Regex("BANDWIDTH=(\\d+)").find(lines[i])?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val next = lines.getOrNull(i + 1) ?: continue
            if (next.startsWith("#")) continue
            if (bandwidth > bestBandwidth) {
                bestBandwidth = bandwidth
                bestUrl = next
            }
        }
        if (bestUrl == null) return null
        return absoluteUrl(baseUrl, bestUrl) to emptyMap()
    }

    private suspend fun downloadSegments(
        manifest: String,
        baseUrl: String,
        headers: Map<String, String>,
        dest: File,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): File {
        val items = parseSegments(manifest, baseUrl)
        if (items.isEmpty()) throw IllegalStateException("no m3u8 segments")
        val key = parseKey(manifest, baseUrl, headers)
        val tempDir = File(dest.parentFile, "${dest.name}.parts")
        tempDir.mkdirs()
        val total = items.size
        var completed = 0
        coroutineScope {
            items.chunked(MaxConcurrent).forEach { chunk ->
                chunk.map { segment ->
                    async(Dispatchers.IO) {
                        val part = File(tempDir, "seg_${segment.index}.ts")
                        if (!part.exists() || part.length() <= 0L) {
                            val bytes = fetchBytes(segment.url, headers)
                            val plain = if (key != null) decrypt(bytes, key) else bytes
                            FileOutputStream(part).use { it.write(plain) }
                        }
                        synchronized(this@M3u8Downloader) { completed++ }
                        onProgress(completed.toLong(), total.toLong())
                    }
                }.awaitAll()
            }
        }
        dest.parentFile?.mkdirs()
        FileOutputStream(dest).use { out ->
            items.forEach { segment ->
                val part = File(tempDir, "seg_${segment.index}.ts")
                if (part.exists()) {
                    val buffer = ByteArray(16 * 1024)
                    part.inputStream().use { input ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            out.write(buffer, 0, read)
                        }
                    }
                }
            }
        }
        tempDir.deleteRecursively()
        onProgress(total.toLong(), total.toLong())
        return dest
    }

    private fun parseSegments(manifest: String, baseUrl: String): List<Segment> {
        val result = ArrayList<Segment>()
        var index = 0
        for (raw in manifest.lines()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            result.add(Segment(index++, absoluteUrl(baseUrl, line)))
        }
        return result
    }

    private fun parseKey(manifest: String, baseUrl: String, headers: Map<String, String>): KeyInfo? {
        val line = manifest.lines().firstOrNull { it.trim().startsWith("#EXT-X-KEY") } ?: return null
        if (line.contains("METHOD=NONE", ignoreCase = true)) return null
        if (!line.contains("METHOD=AES-128", ignoreCase = true)) {
            throw IllegalStateException("unsupported m3u8 encryption")
        }
        val uri = Regex("URI=\"([^\"]+)\"").find(line)?.groupValues?.get(1) ?: return null
        val iv = Regex("IV=0x([0-9a-fA-F]+)").find(line)?.groupValues?.get(1)
        val keyBytes = fetchBytes(absoluteUrl(baseUrl, uri), headers)
        val ivBytes = if (iv != null && iv.length == 32) hexBytes(iv) else keyBytes
        return KeyInfo(keyBytes, ivBytes)
    }

    private fun decrypt(data: ByteArray, key: KeyInfo): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key.bytes, "AES"), IvParameterSpec(key.iv))
        return cipher.doFinal(data)
    }

    private fun fetchText(url: String, headers: Map<String, String>): String =
        String(fetchBytes(url, headers), Charsets.UTF_8)

    private fun fetchBytes(url: String, headers: Map<String, String>): ByteArray {
        val builder = Request.Builder().url(url)
        headers.forEach { (key, value) -> builder.header(key, value) }
        val response = OkHttp.client().newCall(builder.build()).execute()
        response.use { resp ->
            if (!resp.isSuccessful) throw IOException("http ${resp.code}")
            return requireNotNull(resp.body) { "empty body" }.bytes()
        }
    }

    private fun absoluteUrl(base: String, ref: String): String = try {
        URL(URL(base), ref).toString()
    } catch (ignored: Throwable) {
        ref
    }

    private fun hexBytes(value: String): ByteArray {
        val result = ByteArray(value.length / 2)
        for (i in result.indices) {
            result[i] = value.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return result
    }

    private class Segment(
        @JvmField val index: Int,
        @JvmField val url: String,
    )

    private class KeyInfo(
        @JvmField val bytes: ByteArray,
        @JvmField val iv: ByteArray,
    )
}
