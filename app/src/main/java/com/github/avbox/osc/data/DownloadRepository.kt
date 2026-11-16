package com.github.avbox.osc.data

import kotlinx.coroutines.flow.Flow

interface DownloadRepository {

    suspend fun getAll(): List<DownloadTask>

    fun observeAll(): Flow<List<DownloadTask>>

    fun observeByVod(sourceKey: String?, vodId: String?): Flow<List<DownloadTask>>

    suspend fun findEpisode(cid: String?, sourceKey: String?, vodId: String?, playFlag: String?, episodeName: String?): DownloadTask?

    suspend fun enqueue(tasks: List<DownloadTask>): List<Long>

    suspend fun updateState(id: Int, state: DownloadState, errorMessage: String? = null)

    suspend fun updateProgress(id: Int, downloaded: Long, total: Long)

    suspend fun updateCompleted(id: Int, filePath: String?)

    suspend fun pendingForResume(): List<DownloadTask>

    suspend fun delete(id: Int)

    suspend fun clearCompleted()

    suspend fun clearAll()
}

internal class RoomDownloadRepository(
    private val tasks: () -> DownloadTaskDao,
) : DownloadRepository {

    override suspend fun getAll(): List<DownloadTask> = tasks().getAll()

    override fun observeAll(): Flow<List<DownloadTask>> = tasks().observeAll()

    override fun observeByVod(sourceKey: String?, vodId: String?): Flow<List<DownloadTask>> =
        tasks().observeByVod(CurrentSubscription.cid(), sourceKey, vodId)

    override suspend fun findEpisode(cid: String?, sourceKey: String?, vodId: String?, playFlag: String?, episodeName: String?): DownloadTask? =
        tasks().findEpisode(cid, sourceKey, vodId, playFlag, episodeName)

    override suspend fun enqueue(tasks: List<DownloadTask>): List<Long> {
        val dao = this.tasks()
        val result = ArrayList<Long>(tasks.size)
        for (task in tasks) {
            result.add(dao.insert(task))
        }
        return result
    }

    override suspend fun updateState(id: Int, state: DownloadState, errorMessage: String?) {
        tasks().updateState(id, state.ordinal, errorMessage, System.currentTimeMillis())
    }

    override suspend fun updateProgress(id: Int, downloaded: Long, total: Long) {
        tasks().updateProgress(id, downloaded, total, System.currentTimeMillis())
    }

    override suspend fun updateCompleted(id: Int, filePath: String?) {
        tasks().updateCompleted(id, filePath, DownloadState.COMPLETED.ordinal, System.currentTimeMillis())
    }

    override suspend fun pendingForResume(): List<DownloadTask> = tasks().getByStates(
        listOf(DownloadState.DOWNLOADING.ordinal, DownloadState.WAITING.ordinal, DownloadState.PREPARING.ordinal),
    )

    override suspend fun delete(id: Int) {
        tasks().deleteById(id)
    }

    override suspend fun clearCompleted() {
        tasks().deleteCompleted(DownloadState.COMPLETED.ordinal)
    }

    override suspend fun clearAll() {
        tasks().deleteAll()
    }
}
