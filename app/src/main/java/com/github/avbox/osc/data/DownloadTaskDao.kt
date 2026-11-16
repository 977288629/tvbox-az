package com.github.avbox.osc.data

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadTaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: DownloadTask): Long

    @Query("select * from downloadTask where `id`=:id")
    suspend fun getById(id: Int): DownloadTask?

    @Query("select * from downloadTask where `cid`=:cid and `sourceKey`=:sourceKey and `vodId`=:vodId and ((`playFlag` is null and :playFlag is null) or `playFlag`=:playFlag) and `episodeName`=:episodeName limit 1")
    suspend fun findEpisode(cid: String?, sourceKey: String?, vodId: String?, playFlag: String?, episodeName: String?): DownloadTask?

    @Query("select * from downloadTask order by createTime desc")
    suspend fun getAll(): List<DownloadTask>

    @Query("select * from downloadTask order by createTime desc")
    fun observeAll(): Flow<List<DownloadTask>>

    @Query("select * from downloadTask where `cid`=:cid and `sourceKey`=:sourceKey and `vodId`=:vodId order by episodeIndex")
    fun observeByVod(cid: String?, sourceKey: String?, vodId: String?): Flow<List<DownloadTask>>

    @Query("select * from downloadTask where `state` in (:states) order by createTime")
    suspend fun getByStates(states: List<Int>): List<DownloadTask>

    @Query("update downloadTask set `state`=:state, `errorMessage`=:errorMessage, `updateTime`=:updateTime where `id`=:id")
    suspend fun updateState(id: Int, state: Int, errorMessage: String?, updateTime: Long)

    @Query("update downloadTask set `downloadedBytes`=:downloaded, `totalBytes`=:total, `updateTime`=:updateTime where `id`=:id")
    suspend fun updateProgress(id: Int, downloaded: Long, total: Long, updateTime: Long)

    @Query("update downloadTask set `filePath`=:filePath, `state`=:state, `updateTime`=:updateTime where `id`=:id")
    suspend fun updateCompleted(id: Int, filePath: String?, state: Int, updateTime: Long)

    @Query("delete from downloadTask where `id`=:id")
    suspend fun deleteById(id: Int)

    @Query("delete from downloadTask where `state`=:completed")
    suspend fun deleteCompleted(completed: Int)

    @Query("delete from downloadTask")
    suspend fun deleteAll()

    @Delete
    suspend fun delete(task: DownloadTask): Int
}
