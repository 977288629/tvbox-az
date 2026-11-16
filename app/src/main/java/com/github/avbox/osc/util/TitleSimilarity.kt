package com.github.avbox.osc.util

import java.util.Locale

object TitleSimilarity {

    const val AcceptThreshold = 0.6

    private const val Threshold = AcceptThreshold

    private val NoiseWords = listOf(
        "电影", "电视剧", "综艺", "动漫", "纪录片",
        "hd", "4k", "8k", "1080p", "720p", "2160p",
        "国语", "粤语", "英语", "韩语", "日语", "中字", "字幕",
        "更新至", "集全", "完结", "连载", "全集",
        "高清", "超清", "蓝光", "正版", "独播", "首播",
    )

    private val BracketPattern = Regex("[（(\\[【][^）)\\]】]*[）)\\]】]")
    private val SymbolPattern = Regex("[\\s\\p{Z}\\p{P}\\p{S}]")

    @JvmStatic
    fun normalize(title: String?): String {
        if (title == null) return ""
        var result = title.lowercase(Locale.ROOT).replace(BracketPattern, "")
        for (word in NoiseWords) result = result.replace(word, "")
        return result.replace(SymbolPattern, "")
    }

    @JvmStatic
    fun similarity(a: String?, b: String?): Double {
        val na = normalize(a)
        val nb = normalize(b)
        if (na == nb) return if (na.isEmpty()) 0.0 else 1.0
        if (na.isEmpty() || nb.isEmpty()) return 0.0
        if (na.contains(nb) || nb.contains(na)) return 0.85
        val longestCommon = longestCommonSubstring(na, nb)
        val shorter = minOf(na.length, nb.length)
        if (shorter > 0 && longestCommon >= shorter * 0.6) return 0.85
        return jaccard(na, nb) * 0.25 + sequence(na, nb) * 0.35 + edit(na, nb) * 0.4
    }

    private fun longestCommonSubstring(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        val dp = Array(m + 1) { IntArray(n + 1) }
        var best = 0
        for (i in 1..m) {
            for (j in 1..n) {
                if (a[i - 1] == b[j - 1]) {
                    dp[i][j] = dp[i - 1][j - 1] + 1
                    if (dp[i][j] > best) best = dp[i][j]
                }
            }
        }
        return best
    }

    private fun jaccard(a: String, b: String): Double {
        val setA = a.toSet()
        val setB = b.toSet()
        val union = setA + setB
        if (union.isEmpty()) return 0.0
        return (setA intersect setB).size.toDouble() / union.size
    }

    private fun sequence(a: String, b: String): Double {
        val longer = maxOf(a.length, b.length)
        if (longer == 0) return 0.0
        val m = a.length
        val n = b.length
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 1..m) {
            for (j in 1..n) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) {
                    dp[i - 1][j - 1] + 1
                } else {
                    maxOf(dp[i - 1][j], dp[i][j - 1])
                }
            }
        }
        return dp[m][n].toDouble() / longer
    }

    private fun edit(a: String, b: String): Double {
        val m = a.length
        val n = b.length
        if (m == 0 || n == 0) return 0.0
        var prev = IntArray(n + 1) { it }
        var curr = IntArray(n + 1)
        for (i in 1..m) {
            curr[0] = i
            for (j in 1..n) {
                curr[j] = if (a[i - 1] == b[j - 1]) {
                    prev[j - 1]
                } else {
                    1 + minOf(prev[j], curr[j - 1], prev[j - 1])
                }
            }
            val swap = prev
            prev = curr
            curr = swap
        }
        return 1.0 - prev[n].toDouble() / maxOf(m, n)
    }

    @JvmStatic
    fun <T> dedupe(items: List<T>, threshold: Double = Threshold, titleOf: (T) -> String?): List<T> {
        val result = ArrayList<T>(items.size)
        val representatives = ArrayList<String>()
        for (item in items) {
            val title = titleOf(item)
            val normalized = normalize(title)
            if (title.isNullOrEmpty() || normalized.isEmpty()) {
                result.add(item)
                continue
            }
            val duplicate = representatives.any { similarity(it, normalized) >= threshold }
            if (!duplicate) {
                representatives.add(normalized)
                result.add(item)
            }
        }
        return result
    }
}
