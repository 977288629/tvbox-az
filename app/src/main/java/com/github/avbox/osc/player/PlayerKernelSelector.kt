package com.github.avbox.osc.player

import com.github.avbox.osc.util.AppLog

import java.util.LinkedHashMap

object PlayerKernelSelector {

    const val KERNEL_MEDIA3 = 2
    const val KERNEL_MPV = 3

    private val failedMpv = object : LinkedHashMap<String, Long>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean =
            size > MAX_ENTRIES
    }

    @Synchronized
    fun prefer(url: String, headers: Map<String, String>?, userKernel: Int): Int {
        val key = sourceKey(url)
        if (failedMpv.containsKey(key)) {
            AppLog.i("kernel-select: avoid MPV (known bad source) " + key.take(60))
            return KERNEL_MEDIA3
        }
        if (needsMedia3(url, headers)) {
            AppLog.i("kernel-select: prefer Media3 (protected/dash url)")
            return KERNEL_MEDIA3
        }
        return userKernel
    }

    @Synchronized
    fun markMpvFailed(url: String) {
        val key = sourceKey(url)
        failedMpv[key] = System.currentTimeMillis()
        AppLog.i("kernel-select: mark MPV failed " + key.take(60))
    }

    @Synchronized
    fun clearFailures() {
        failedMpv.clear()
    }

    private fun needsMedia3(url: String, headers: Map<String, String>?): Boolean {
        val lower = url.lowercase()
        if (lower.contains("data:application/dash+xml") || lower.contains(".mpd") ||
            lower.contains("type=mpd")
        ) return true
        if (lower.startsWith("clan://") || lower.startsWith("file://") ||
            lower.startsWith("content://")
        ) return true
        if (!headers.isNullOrEmpty()) {
            for (name in PROTECTED_HEADERS) {
                if (headers.keys.any { it.equals(name, ignoreCase = true) }) return true
            }
        }
        return false
    }

    private fun sourceKey(url: String): String {
        val cut = url.indexOf('?')
        return if (cut >= 0) url.substring(0, cut) else url
    }

    private val PROTECTED_HEADERS = listOf("referer", "origin", "user-agent", "cookie")

    private const val MAX_ENTRIES = 40
}
