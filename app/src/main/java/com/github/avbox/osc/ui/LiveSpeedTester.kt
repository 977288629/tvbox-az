package com.github.avbox.osc.ui

import com.github.avbox.osc.net.HttpClients

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import okhttp3.Request

import java.util.concurrent.TimeUnit

internal object LiveSpeedTester {

    internal const val RESULT_UNTESTED = 0
    internal const val RESULT_FAILED = -1
    internal const val RESULT_UNSUPPORTED = -2

    private const val PROBE_TIMEOUT_MS = 4000L

    internal suspend fun probe(url: String, headers: Map<String, String>?): Int = withContext(Dispatchers.IO) {
        val lower = url.trim().lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return@withContext RESULT_UNSUPPORTED
        }
        val base = HttpClients.defaultClient ?: return@withContext RESULT_FAILED
        val builder = Request.Builder().url(url).get()
        for ((key, value) in headers.orEmpty()) builder.header(key, value)
        val start = System.currentTimeMillis()
        try {
            val client = base.newBuilder()
                .connectTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .callTimeout(PROBE_TIMEOUT_MS + 500L, TimeUnit.MILLISECONDS)
                .build()
            client.newCall(builder.build()).execute().use { response ->
                if (response.isSuccessful || response.code in 300..399) {
                    (System.currentTimeMillis() - start).toInt()
                } else {
                    RESULT_FAILED
                }
            }
        } catch (th: Throwable) {
            RESULT_FAILED
        }
    }
}
