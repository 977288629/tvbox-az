package com.github.avbox.osc.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleSimilarityTest {

    @Test
    fun normalizeStripsQualityAndYear() {
        assertEquals("后会友期芒碁", TitleSimilarity.normalize("后会友期（2026）芒碁4K"))
        assertEquals("后会友期慕尼", TitleSimilarity.normalize("后会友期（2026）慕尼4K"))
        assertEquals("多喜一家人", TitleSimilarity.normalize("多喜一家人[更新至04集]"))
    }

    @Test
    fun similarTitlesAboveThreshold() {
        val a = "后会友期（2026）芒碁4K"
        val b = "后会友期（2026）慕尼4K"
        assertTrue("similarity=${TitleSimilarity.similarity(a, b)}", TitleSimilarity.similarity(a, b) >= TitleSimilarity.AcceptThreshold)
    }

    @Test
    fun dedupeMergesVariants() {
        val list = listOf(
            "后会友期（2026）芒碁4K",
            "后会友期（2026）慕尼4K",
            "后会友期 4K",
            "完全不同的另一部剧",
        )
        val result = TitleSimilarity.dedupe(list) { it }
        assertTrue("deduped=${result.size}", result.size == 2)
        assertTrue(result.contains("完全不同的另一部剧"))
    }

    @Test
    fun reorderWithSitePrefixStillMerged() {
        val a = "后会友期（2026）芒碁4K"
        val b = "慕尼 4K 后会友期"
        assertTrue("similarity=${TitleSimilarity.similarity(a, b)}", TitleSimilarity.similarity(a, b) >= TitleSimilarity.AcceptThreshold)
    }

    @Test
    fun differentTitlesNotMerged() {
        assertTrue(TitleSimilarity.similarity("多喜一家人", "后会友期") < TitleSimilarity.AcceptThreshold)
    }
}
