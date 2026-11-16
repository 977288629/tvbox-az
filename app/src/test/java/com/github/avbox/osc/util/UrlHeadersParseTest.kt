package com.github.avbox.osc.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlHeadersParseTest {

    @Test
    fun plainUrlPassesThrough() {
        val parsed = UrlHeaders.parse("http://vd4.bdstatic.com/a.mp4?v_from_s=hnb")
        assertEquals("http://vd4.bdstatic.com/a.mp4?v_from_s=hnb", parsed.url)
    }

    @Test
    fun urlWithAppendedHeadersKeepsHttp() {
        val encoded = java.net.URLEncoder.encode("{\"User-Agent\":\"Mozilla/5.0 (Linux; Android 4.4.2) Chrome/30.0 dumedia/7.74.1.3\"}", "UTF-8")
        val raw = "http://vd4.bdstatic.com/a.mp4?v_from_s=hnb" + "@Headers=" + encoded
        val parsed = UrlHeaders.parse(raw)
        assertTrue("url=" + parsed.url, parsed.url.startsWith("http://"))
        assertTrue("ua=" + parsed.headers["User-Agent"], parsed.headers.containsKey("User-Agent"))
    }
}
