package com.github.avbox.osc.player

import android.os.Handler
import android.os.Looper

import com.github.avbox.osc.data.RemoteControlPort
import com.github.avbox.osc.data.RemotePlaybackSnapshot
import com.github.avbox.osc.util.AppLog

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RemoteControlBridge : RemoteControlPort {

    private val uiHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var muted = false

    private var lastVolumePercent = 100

    private fun <T> onUiBlocking(block: () -> T): T? {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        val latch = CountDownLatch(1)
        var result: T? = null
        uiHandler.post {
            try {
                result = block()
            } catch (th: Throwable) {
                AppLog.e(TAG, th)
            } finally {
                latch.countDown()
            }
        }
        try {
            latch.await(WAIT_SECONDS, TimeUnit.SECONDS)
        } catch (ignored: InterruptedException) {
        }
        return result
    }

    private fun withEngine(block: (PlaybackEngine, PlaybackController, PlaybackViewBridge) -> Unit): Boolean {
        return onUiBlocking {
            val engine = PlaybackService.peek() ?: return@onUiBlocking false
            val controller = engine.controller()
            val bridge = engine.headlessBridge()
            block(engine, controller, bridge)
            true
        } ?: false
    }

    private fun buildSnapshot(): RemotePlaybackSnapshot? {
        val engine = PlaybackService.peek() ?: return null
        val controller = engine.controller()
        val vod = controller.vod() ?: return null
        val bridge = engine.headlessBridge()
        val seriesMap = vod.seriesMap
        val flag = vod.playFlag
        val episodes = seriesMap?.get(flag)?.mapNotNull { it.name } ?: emptyList()
        val speed = try {
            controller.playerCfg()?.optDouble("sp", 1.0)?.toFloat() ?: 1f
        } catch (ignored: Throwable) {
            1f
        }
        val volumePercent = ((engine.player().mediaPlayer?.volume ?: 1f) * 100f).toInt()
        return RemotePlaybackSnapshot(
            title = vod.name ?: "",
            episode = controller.currentSeries(flag, vod.playIndex)?.name ?: "",
            flag = flag ?: "",
            index = vod.playIndex,
            flags = seriesMap?.keys?.map { it ?: "" } ?: emptyList(),
            episodes = episodes,
            positionMs = bridge.currentPosition(),
            durationMs = bridge.duration(),
            playing = bridge.isPlaying(),
            speed = speed,
            volume = volumePercent,
            muted = volumePercent <= 0,
        )
    }

    override fun snapshot(): RemotePlaybackSnapshot? = onUiBlocking { buildSnapshot() }

    override fun togglePlay(): Boolean {
        return withEngine { engine, _, bridge ->
            if (bridge.isPlaying()) engine.pauseFromMediaSession() else engine.resumeFromMediaSession()
        }
    }

    override fun seekTo(positionMs: Long): Boolean {
        return withEngine { engine, _, _ ->
            engine.seekFromMediaSession(positionMs)
        }
    }

    override fun playAdjacent(forward: Boolean): Boolean {
        return withEngine { _, controller, _ ->
            controller.playAdjacentEpisode(forward)
        }
    }

    override fun playEpisodeAt(index: Int): Boolean {
        return withEngine { _, controller, _ ->
            controller.playEpisodeAt(index)
        }
    }

    override fun setSpeed(speed: Float): Boolean {
        if (speed <= 0f || speed > 5f) return false
        return withEngine { engine, controller, bridge ->
            val applied = controller.commitPlayerCfg({ it.put("sp", speed.toDouble()) }, persist = true)
            if (applied && bridge.playState().isInPlaybackState) {
                try {
                    engine.player().setSpeed(speed)
                } catch (th: Throwable) {
                    AppLog.e(TAG, th)
                }
            }
        }
    }

    override fun setVolume(percent: Int): Int {
        val clamped = Math.max(0, Math.min(100, percent))
        return onUiBlocking {
            val kernel = PlaybackService.peek()?.player()?.mediaPlayer ?: return@onUiBlocking clamped
            val target = clamped / 100f
            kernel.setVolume(target, target)
            if (clamped > 0) {
                muted = false
                lastVolumePercent = clamped
            }
            clamped
        } ?: clamped
    }

    override fun toggleMute(): Boolean {
        val result = onUiBlocking {
            val kernel = PlaybackService.peek()?.player()?.mediaPlayer ?: return@onUiBlocking null
            val vol = kernel.volume
            if (vol > 0f) {
                val percent = Math.round(vol * 100f)
                if (percent > 0) lastVolumePercent = percent
                kernel.setVolume(0f, 0f)
                muted = true
            } else {
                val restore = Math.max(1, lastVolumePercent) / 100f
                kernel.setVolume(restore, restore)
                muted = false
            }
            muted
        }
        return result ?: muted
    }

    companion object {

        private const val TAG = "RemoteControl"

        private const val WAIT_SECONDS = 2L
    }
}
