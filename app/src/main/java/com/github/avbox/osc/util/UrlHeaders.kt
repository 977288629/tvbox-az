package com.github.avbox.osc.util

import com.google.gson.JsonParser
import java.net.URLDecoder

object UrlHeaders {

    @JvmStatic
    fun parse(raw: String?): Parsed {
        if (raw.isNullOrEmpty() || raw.startsWith("data:")) return Parsed(raw.orEmpty())
        if (!raw.contains('@')) return Parsed(raw)

        val url = raw.split("@")[0]
        if (url.isEmpty()) return Parsed(raw)

        fun grab(key: String): String? =
            if (raw.contains("$key=")) raw.split("$key=")[1].split("@")[0] else null

        val headers = LinkedHashMap<String, String>()
        fun put(key: String, value: String?) {
            if (value.isNullOrEmpty()) return
            if (!HeaderGuard.isSendable(key, value)) return
            headers[key] = value
        }

        grab("@Headers")?.let { json ->
            try {
                val decoded = URLDecoder.decode(json, "UTF-8")
                val element = JsonParser.parseString(decoded)
                if (element.isJsonObject) {
                    val obj = element.asJsonObject
                    for (key in obj.keySet()) {
                        val value = obj.get(key).asString
                        put(key, value)
                    }
                }
            } catch (ignored: Throwable) {
            }
        }
        put("Cookie", grab("@Cookie"))
        put("User-Agent", grab("@User-Agent"))
        put("Referer", grab("@Referer"))
        return Parsed(url, headers)
    }

    class Parsed(
        @JvmField val url: String,
        @JvmField val headers: Map<String, String> = emptyMap(),
    )
}
