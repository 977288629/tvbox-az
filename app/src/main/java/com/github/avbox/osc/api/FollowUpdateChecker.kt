package com.github.avbox.osc.api

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

import androidx.core.app.NotificationCompat

import com.github.avbox.osc.R
import com.github.avbox.osc.bean.SourceBean
import com.github.avbox.osc.data.AppGraph
import com.github.avbox.osc.data.VodFollow
import com.github.avbox.osc.ui.DetailActivity
import com.github.avbox.osc.util.AppContextHolder
import com.github.avbox.osc.util.AppLog
import com.github.avbox.osc.util.BoundedCall
import com.github.avbox.osc.util.KV
import com.github.avbox.osc.util.LanguageManager
import com.github.catvod.crawler.SpiderReaper

import java.util.concurrent.Callable

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object FollowUpdateChecker {

    private const val CHANNEL_ID = "follow_update"
    private const val KV_LAST_CHECK = "follow_check_last_ms"
    private const val KV_EPISODE_PREFIX = "follow_ep_"

    private const val CHECK_INTERVAL_MS = 4 * 60 * 60 * 1000L
    private const val DETAIL_TIMEOUT_MS = 12_000L
    private const val PARALLEL_BATCH = 4

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun onAppReady() {
        val context = AppContextHolder.context() ?: return
        createChannel(context)
        scope.launch { runCheck(context) }
    }

    private suspend fun runCheck(context: Context) {
        try {
            val last = KV.get(KV_LAST_CHECK, 0L) as? Long ?: 0L
            val now = System.currentTimeMillis()
            if (now - last < CHECK_INTERVAL_MS) {
                AppLog.i("echo-follow-check skip, last=" + (now - last) / 60000 + "min ago")
                return
            }
            KV.put(KV_LAST_CHECK, now)
            val follows = AppGraph.followRepository.observeAll().first()
            if (follows.isEmpty()) {
                AppLog.i("echo-follow-check empty, done")
                return
            }
            AppLog.i("echo-follow-check start, follows=" + follows.size)
            var notified = 0
            for (batch in follows.chunked(PARALLEL_BATCH)) {
                val results = coroutineScope {
                    batch.map { follow -> async { checkOne(context, follow) } }.awaitAll()
                }
                notified += results.count { it }
            }
            AppLog.i("echo-follow-check done, notified=" + notified)
        } catch (th: Throwable) {
            AppLog.e("echo-follow-check failed: " + th.message)
        }
    }

    private suspend fun checkOne(context: Context, follow: VodFollow): Boolean {
        val sourceKey = follow.sourceKey ?: return false
        val vodId = follow.vodId ?: return false
        if (sourceKey.isEmpty() || vodId.isEmpty()) return false
        val bean = ApiConfig.get().getSource(sourceKey) ?: return false
        val count = fetchEpisodeCount(bean, vodId)
        if (count == null || count <= 0) {
            AppLog.i("echo-follow-check no-count name=" + follow.name.orEmpty() + " source=" + sourceKey)
            return false
        }
        val known = KV.get(KV_EPISODE_PREFIX + follow.id, -1) as Int
        if (known < 0) {
            KV.put(KV_EPISODE_PREFIX + follow.id, count)
            AppLog.i("echo-follow-check baseline name=" + follow.name.orEmpty() + " episodes=" + count)
            return false
        }
        if (count <= known) return false
        KV.put(KV_EPISODE_PREFIX + follow.id, count)
        AppLog.i("echo-follow-check update found name=" + follow.name.orEmpty() + " " + known + " -> " + count)
        return notify(context, follow, count)
    }

    private suspend fun fetchEpisodeCount(bean: SourceBean, vodId: String): Int? {
        return try {
            val json = BoundedCall.call(Callable<String> {
                val sp = ApiConfig.get().getCSP(bean)
                val ids = ArrayList<String>()
                ids.add(vodId)
                SpiderReaper.track(sp) { sp.detailContent(ids) }
            }, DETAIL_TIMEOUT_MS, "echo-follow-check-" + bean.key)
            parseEpisodeCount(json.orEmpty(), bean.type)
        } catch (th: Throwable) {
            AppLog.i("echo-follow-check detail failed source=" + bean.key + " err=" + th.message)
            null
        }
    }

    fun parseEpisodeCount(body: String, type: Int): Int? {
        if (body.isBlank()) return null
        return try {
            val playUrl = if (type == 0) parseXmlPlayUrl(body) else parseJsonPlayUrl(body)
            if (playUrl.isNullOrEmpty()) return null
            val firstLine = playUrl.split("\\$\\$\\$")[0]
            val count = firstLine.split("#").count { it.isNotBlank() }
            if (count > 0) count else null
        } catch (th: Throwable) {
            null
        }
    }

    private fun parseJsonPlayUrl(body: String): String? {
        val obj = org.json.JSONObject(body)
        var playUrl = obj.optString("vod_play_url", "")
        if (playUrl.isNotEmpty()) return playUrl
        val list = obj.optJSONArray("list")
        val first = list?.optJSONObject(0)
        return first?.optString("vod_play_url", "")?.takeIf { it.isNotEmpty() }
    }

    private fun parseXmlPlayUrl(body: String): String? {
        val match = Regex("<dd[^>]*>(.*?)</dd>", RegexOption.DOT_MATCHES_ALL).find(body)
        return match?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun notify(context: Context, follow: VodFollow, count: Int): Boolean {
        return try {
            val intent = Intent(context, DetailActivity::class.java)
                .putExtra("id", follow.vodId.orEmpty())
                .putExtra("sourceKey", follow.sourceKey.orEmpty())
                .putExtra("title", follow.name)
                .putExtra("picture", follow.pic)
                .putExtra("collect", false)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pending = PendingIntent.getActivity(
                context,
                follow.id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val localized = LanguageManager.localized(context)
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_tab_following)
                .setContentTitle(follow.name ?: "")
                .setContentText(localized.getString(R.string.follow_notify_text, count))
                .setAutoCancel(true)
                .setContentIntent(pending)
                .build()
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(TAG_NOTIFY, follow.id, notification)
            true
        } catch (th: Throwable) {
            AppLog.i("echo-follow-check notify failed: " + th.message)
            false
        }
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val localized = LanguageManager.localized(context)
        val channel = NotificationChannel(
            CHANNEL_ID,
            localized.getString(R.string.follow_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private const val TAG_NOTIFY = "follow_update"
}
