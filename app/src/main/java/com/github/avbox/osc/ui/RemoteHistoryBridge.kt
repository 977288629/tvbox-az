package com.github.avbox.osc.ui

import android.content.Intent
import android.os.Bundle

import com.github.avbox.osc.api.AppBootstrap
import com.github.avbox.osc.bean.VodInfo
import com.github.avbox.osc.data.AppGraph
import com.github.avbox.osc.data.PlaybackProgress
import com.github.avbox.osc.data.RemoteHistoryItem
import com.github.avbox.osc.data.RemoteHistoryPort
import com.github.avbox.osc.data.RemoteHistoryPorts
import com.github.avbox.osc.ui.settings.UiSettingsState
import com.github.avbox.osc.util.AppContextHolder
import com.github.avbox.osc.util.AppLog
import com.github.avbox.osc.util.HistoryHelper

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class RemoteHistoryBridge : RemoteHistoryPort {

    override fun recent(limit: Int): List<RemoteHistoryItem> {
        if (UiSettingsState.config.incognito || HistoryHelper.isIncognito()) return emptyList()
        return runBlocking {
            val cid = AppBootstrap.subscriptionCid.value
            val vods = AppGraph.historyRepository.observeVodRecords(cid, limit).first()
            val percents = PlaybackProgress.percents.value
            vods.mapNotNull { toItem(it, percents) }
        }
    }

    override fun playRecord(sourceKey: String, vodId: String): Boolean {
        if (vodId.isEmpty()) return false
        val context = AppContextHolder.context() ?: return false
        val record = runBlocking {
            AppGraph.historyRepository.getVodInfo(sourceKey, vodId)
        }
        val bundle = Bundle()
        bundle.putString("id", vodId)
        bundle.putString("sourceKey", sourceKey)
        bundle.putString("title", record?.name)
        bundle.putString("picture", record?.pic)
        bundle.putBoolean("collect", false)
        val intent = Intent(context, DetailActivity::class.java)
            .putExtras(bundle)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return try {
            context.startActivity(intent)
            true
        } catch (th: Throwable) {
            AppLog.e(TAG + " playRecord failed: " + th.message)
            false
        }
    }

    override fun pushSearch(text: String): Boolean {
        val query = text.trim()
        if (query.isEmpty()) return false
        val context = AppContextHolder.context() ?: return false
        return try {
            if (query.startsWith("http://") || query.startsWith("https://")) {
                val playIntent = Intent(context, LocalPlayActivity::class.java)
                    .putExtra(LocalPlayActivity.EXTRA_PATH, query)
                    .putExtra(LocalPlayActivity.EXTRA_NAME, query.substringAfterLast('/'))
                    .putExtra(LocalPlayActivity.EXTRA_SOURCE, "")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                context.startActivity(playIntent)
            } else {
                val bundle = Bundle()
                bundle.putString("title", query)
                context.startActivity(
                    Intent(context, SearchActivity::class.java)
                        .putExtras(bundle)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                )
            }
            true
        } catch (th: Throwable) {
            AppLog.e(TAG + " pushSearch failed: " + th.message)
            false
        }
    }

    private fun toItem(vod: VodInfo, percents: Map<String, Int>): RemoteHistoryItem? {
        val id = vod.id ?: return null
        val series = vod.seriesMap?.get(vod.playFlag)
        return RemoteHistoryItem(
            vodId = id,
            sourceKey = vod.sourceKey ?: "",
            title = vod.name ?: "",
            pic = vod.pic,
            updateTime = vod.updateTime,
            flag = vod.playFlag,
            episodeIndex = vod.playIndex,
            episodeCount = series?.size ?: 0,
            episodeName = series?.getOrNull(vod.playIndex)?.name,
            percent = percents[PlaybackProgress.key(vod.sourceKey, id)] ?: 0,
        )
    }

    companion object {

        private const val TAG = "RemoteHistory"

        fun install() {
            if (RemoteHistoryPorts.port == null) {
                RemoteHistoryPorts.port = RemoteHistoryBridge()
            }
        }
    }
}
