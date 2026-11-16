package com.github.avbox.osc.data

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

import java.io.Serializable

enum class DownloadType {
    HTTP, M3U8
}

enum class DownloadState {
    WAITING, PREPARING, DOWNLOADING, PAUSED,
    MERGING, COMPLETED, FAILED, CANCELLED;

    companion object {
        fun of(value: Int): DownloadState = entries.getOrElse(value) { WAITING }
    }
}

@Entity(tableName = "downloadTask")
class DownloadTask : Serializable {
    @PrimaryKey(autoGenerate = true)
    var id: Int = 0

    @JvmField
    @ColumnInfo(name = "cid")
    var cid: String? = null

    @JvmField
    @ColumnInfo(name = "sourceKey")
    var sourceKey: String? = null

    @JvmField
    @ColumnInfo(name = "vodId")
    var vodId: String? = null

    @JvmField
    @ColumnInfo(name = "vodName")
    var vodName: String? = null

    @JvmField
    @ColumnInfo(name = "vodPic")
    var vodPic: String? = null

    @JvmField
    @ColumnInfo(name = "episodeName")
    var episodeName: String? = null

    @JvmField
    @ColumnInfo(name = "playFlag")
    var playFlag: String? = null

    @JvmField
    @ColumnInfo(name = "episodeIndex")
    var episodeIndex: Int = 0

    @JvmField
    @ColumnInfo(name = "episodeUrl")
    var episodeUrl: String? = null

    @JvmField
    @ColumnInfo(name = "downloadType")
    var downloadType: Int = DownloadType.HTTP.ordinal

    @JvmField
    @ColumnInfo(name = "state")
    var state: Int = DownloadState.WAITING.ordinal

    @JvmField
    @ColumnInfo(name = "downloadedBytes")
    var downloadedBytes: Long = 0

    @JvmField
    @ColumnInfo(name = "totalBytes")
    var totalBytes: Long = 0

    @JvmField
    @ColumnInfo(name = "filePath")
    var filePath: String? = null

    @JvmField
    @ColumnInfo(name = "errorMessage")
    var errorMessage: String? = null

    @JvmField
    @ColumnInfo(name = "retryCount")
    var retryCount: Int = 0

    @JvmField
    @ColumnInfo(name = "createTime")
    var createTime: Long = 0

    @JvmField
    @ColumnInfo(name = "updateTime")
    var updateTime: Long = 0

    fun type(): DownloadType = DownloadType.entries.getOrElse(downloadType) { DownloadType.HTTP }

    fun status(): DownloadState = DownloadState.of(state)

    fun key(): String = listOf(cid.orEmpty(), sourceKey.orEmpty(), vodId.orEmpty(), episodeName.orEmpty()).joinToString("|")
}
