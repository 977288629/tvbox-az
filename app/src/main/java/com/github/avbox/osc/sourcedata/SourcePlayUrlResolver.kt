package com.github.avbox.osc.sourcedata

import com.github.avbox.osc.download.PlayUrlResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

class SourcePlayUrlResolver : PlayUrlResolver {

    override suspend fun resolve(
        sourceKey: String?,
        playFlag: String?,
        progressKey: String?,
        rawUrl: String,
    ): String? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(TimeoutMs) {
            val viewModel = SourceViewModel()
            val deferred = async(Dispatchers.IO) {
                viewModel.playResult.flow.first { it != null }
            }
            viewModel.getPlay(sourceKey, playFlag, progressKey, rawUrl, null)
            val json = deferred.await() ?: return@withTimeoutOrNull null
            if (!json.isNull("msg") && json.optString("msg").isNotEmpty()) return@withTimeoutOrNull null
            val url = playableUrl(json.opt("url")) ?: return@withTimeoutOrNull null
            if (!url.startsWith("http://") && !url.startsWith("https://")) return@withTimeoutOrNull null
            val headers = SourceHelper.extractPlayHeaders(json)
            com.github.avbox.osc.util.AppLog.i("echo-dl-resolve: url=" + url.take(120) + " headers=" + (if (headers == null) "none" else headers.keys.toString()))
            if (headers.isNullOrEmpty() || url.contains("@Headers=")) {
                url
            } else {
                url + "@Headers=" + java.net.URLEncoder.encode(JSONObject(headers).toString(), "UTF-8")
            }
        }
    }

    private fun playableUrl(raw: Any?): String? = when (raw) {
        is String -> {
            if (raw.startsWith("[")) {
                try {
                    playableUrl(JSONArray(raw))
                } catch (ignored: Throwable) {
                    raw
                }
            } else {
                raw
            }
        }
        is JSONArray -> {
            var found: String? = null
            for (i in 0 until raw.length()) {
                val item = raw.optString(i)
                if (item.startsWith("http://") || item.startsWith("https://")) {
                    found = item
                    break
                }
            }
            found
        }
        else -> null
    }

    private companion object {
        const val TimeoutMs = 20_000L
    }
}
