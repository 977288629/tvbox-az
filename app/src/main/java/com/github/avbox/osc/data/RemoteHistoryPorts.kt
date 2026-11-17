package com.github.avbox.osc.data

data class RemoteHistoryItem(
    val vodId: String,
    val sourceKey: String,
    val title: String,
    val pic: String? = null,
    val updateTime: Long = 0,
    val flag: String? = null,
    val episodeIndex: Int = 0,
    val episodeCount: Int = 0,
    val episodeName: String? = null,
    val percent: Int = 0,
)

interface RemoteHistoryPort {

    fun recent(limit: Int): List<RemoteHistoryItem>

    fun playRecord(sourceKey: String, vodId: String): Boolean

    fun pushSearch(text: String): Boolean
}

object RemoteHistoryPorts {

    @Volatile
    var port: RemoteHistoryPort? = null
}
