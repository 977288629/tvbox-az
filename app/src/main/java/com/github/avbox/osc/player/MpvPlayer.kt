package com.github.avbox.osc.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.SurfaceHolder

import com.github.avbox.osc.player.core.KernelPlayer
import com.github.avbox.osc.player.core.PlayState
import com.github.avbox.osc.player.core.PlaybackStateMachine
import com.github.avbox.osc.util.AppLog

import dev.jdtech.mpv.MPVLib

import kotlinx.coroutines.flow.StateFlow

class MpvPlayer(private val context: Context) : KernelPlayer() {

    private val machine = PlaybackStateMachine()
    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var mpv: MPVLib? = null

    @Volatile
    private var sourceUrl: String = ""

    @Volatile
    private var sourceHeaders: Map<String, String>? = null

    @Volatile
    private var looping = false

    @Volatile
    private var currentSpeed = 1f

    @Volatile
    private var cachedPosition = 0L

    @Volatile
    private var cachedDuration = 0L

    @Volatile
    private var cachedBuffered = 0

    private var surface: Surface? = null

    private val observer = object : MPVLib.EventObserver {
        override fun eventProperty(property: String) {
            onMain { if (property == "pause") dispatchPauseChange() }
        }

        override fun eventProperty(property: String, value: Long) {
            when (property) {
                "time-pos" -> cachedPosition = value * 1000L
                "duration" -> cachedDuration = value * 1000L
            }
        }

        override fun eventProperty(property: String, value: Boolean) {
            onMain { if (property == "pause") dispatchPauseChange() }
        }

        override fun eventProperty(property: String, value: String) = Unit

        override fun eventProperty(property: String, value: Double) {
            when (property) {
                "time-pos" -> cachedPosition = (value * 1000).toLong()
                "duration" -> cachedDuration = (value * 1000).toLong()
            }
        }

        override fun event(event: Int) {
            onMain { handleEvent(event) }
        }

        private fun handleEvent(event: Int) {
            when (event) {
                MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED -> {
                    applyStartPosition()
                    machine.onPrepared()
                    playerEventListener?.onPrepared()
                }
                MPVLib.MpvEvent.MPV_EVENT_PLAYBACK_RESTART -> {
                    machine.onRenderingStart()
                    playerEventListener?.onInfo(MEDIA_INFO_RENDERING_START, 0)
                    playerEventListener?.onKernelPlayStateChanged()
                }
                MPVLib.MpvEvent.MPV_EVENT_VIDEO_RECONFIG -> {
                    val w = getPropInt("video-params/w") ?: 0
                    val h = getPropInt("video-params/h") ?: 0
                    if (w > 0 && h > 0) playerEventListener?.onVideoSizeChanged(w, h)
                }
                MPVLib.MpvEvent.MPV_EVENT_END_FILE -> {
                    val eof = getPropBoolean("eof-reached") ?: false
                    if (eof) {
                        machine.onCompletion()
                        playerEventListener?.onCompletion()
                    } else {
                        AppLog.i(TAG + " end-file without eof -> treat as load error")
                        if (sourceUrl.isNotEmpty()) PlayerKernelSelector.markMpvFailed(sourceUrl)
                        machine.onError()
                        playerEventListener?.onError()
                    }
                }
            }
        }
    }


    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else handler.post(block)
    }

    private fun dispatchPauseChange() {
        val paused = getPropBoolean("pause") ?: false
        if (paused) {
            machine.onPauseRequested()
        } else {
            machine.onPlayRequested()
        }
        playerEventListener?.onKernelPlayStateChanged()
    }

    private fun applyStartPosition() {
        if (startPosition > 0 && !isStartPositionApplied()) {
            seekTo(startPosition)
            markStartPositionApplied()
        }
    }

    override fun initPlayer() {
        releaseMpv()
        try {
            val instance = requireNotNull(MPVLib.create(context.applicationContext)) { "mpv create failed" }
            mpv = instance
            instance.init()
            instance.addObserver(observer)
            instance.setOptionString("vo", "gpu")
            instance.setOptionString("ao", "audiotrack")
            instance.setOptionString("hwdec", "auto-safe")
            instance.setOptionString("keep-open", "no")
            instance.setOptionString("force-window", "yes")
            instance.setOptionString("idle", "yes")
            instance.observeProperty("time-pos", MPVLib.MpvFormat.MPV_FORMAT_INT64)
            instance.observeProperty("duration", MPVLib.MpvFormat.MPV_FORMAT_INT64)
            instance.observeProperty("pause", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
            surface?.let { instance.attachSurface(it) }
            AppLog.i(TAG + " init OK (libmpv full, vo=gpu, hwdec=auto-safe)")
        } catch (th: Throwable) {
            AppLog.e(TAG + " init failed: " + th.message)
            machine.onError()
            playerEventListener?.onError()
        }
    }

    override fun setDataSource(path: String, headers: Map<String, String>?) {
        sourceUrl = path
        sourceHeaders = headers
    }

    override fun prepareAsync() {
        val instance = mpv ?: return
        machine.onPrepareRequested()
        try {
            val headerString = sourceHeaders?.map { "${it.key}: ${it.value}" }?.joinToString(",")
            if (!headerString.isNullOrEmpty()) {
                instance.setOptionString("http-header-fields", headerString)
            }
            AppLog.i(TAG + " loadfile: " + sourceUrl.take(100))
            instance.command(arrayOf("loadfile", sourceUrl, "replace"))
        } catch (th: Throwable) {
            AppLog.e(TAG + " prepare failed: " + th.message)
            machine.onError()
            playerEventListener?.onError()
        }
    }

    override fun start(): Boolean {
        mpv?.setPropertyBoolean("pause", false)
        machine.onPlayRequested()
        return true
    }

    override fun pause(): Boolean {
        mpv?.setPropertyBoolean("pause", true)
        machine.onPauseRequested()
        return true
    }

    override fun stop(): Boolean {
        mpv?.command(arrayOf("stop"))
        machine.onStopRequested()
        return true
    }

    override fun reset() {
        mpv?.command(arrayOf("playlist-remove", "current"))
        cachedPosition = 0L
        cachedDuration = 0L
        machine.onReset()
    }

    override val isPlaying: Boolean
        get() = mpv?.let { (getPropBoolean("pause") == false) && machine.currentState.isInPlaybackState } ?: false

    override val playState: PlayState
        get() = machine.currentState

    override val stateFlow: StateFlow<PlayState>
        get() = machine.state

    override fun seekTo(time: Long) {
        mpv?.command(arrayOf("seek", (time / 1000f).toString(), "absolute"))
        cachedPosition = time
    }

    override fun release() {
        releaseMpv()
        machine.onReset()
    }

    private fun releaseMpv() {
        mpv?.let { instance ->
            try {
                instance.removeObserver(observer)
                instance.destroy()
            } catch (th: Throwable) {
                AppLog.e(TAG + " release failed: " + th.message)
            }
        }
        mpv = null
    }

    override val currentPosition: Long
        get() = getPropDouble("time-pos")?.let { (it * 1000).toLong() } ?: cachedPosition

    override val duration: Long
        get() = getPropDouble("duration")?.let { (it * 1000).toLong() } ?: cachedDuration

    override val bufferedPercentage: Int
        get() = getPropInt("demuxer-cache-state")?.let { cachedBuffered } ?: cachedBuffered

    override fun setSurface(surface: Surface?) {
        this.surface = surface
        if (surface != null) mpv?.attachSurface(surface) else mpv?.detachSurface()
    }

    override fun setDisplay(holder: SurfaceHolder?) {
        setSurface(holder?.surface)
    }

    override fun detachVideoSurface() {
        surface = null
        mpv?.detachSurface()
    }

    override fun setVolume(leftVolume: Float, rightVolume: Float) {
        val value = ((leftVolume + rightVolume) / 2f * 100f).toInt()
        mpv?.setPropertyInt("volume", value)
    }

    override val volume: Float
        get() = (getPropInt("volume") ?: 100) / 100f

    override fun setLooping(isLooping: Boolean) {
        looping = isLooping
        mpv?.setPropertyString("loop-file", if (isLooping) "inf" else "no")
    }

    override fun setOptions() {
        setSpeedSafe(currentSpeed)
    }

    override fun setSpeed(speed: Float) {
        currentSpeed = speed
        setSpeedSafe(speed)
    }

    override val speed: Float
        get() = getPropDouble("speed")?.toFloat() ?: currentSpeed

    override val tcpSpeed: Long
        get() = 0L

    private fun setSpeedSafe(value: Float) {
        try {
            mpv?.setPropertyDouble("speed", value.toDouble())
        } catch (th: Throwable) {
            AppLog.e(TAG + " setSpeed failed: " + th.message)
        }
    }

    private fun getPropInt(name: String): Int? = try {
        mpv?.getPropertyInt(name)
    } catch (th: Throwable) {
        null
    }

    private fun getPropDouble(name: String): Double? = try {
        mpv?.getPropertyDouble(name)
    } catch (th: Throwable) {
        null
    }

    private fun getPropBoolean(name: String): Boolean? = try {
        mpv?.getPropertyBoolean(name)
    } catch (th: Throwable) {
        null
    }

    companion object {
        private const val TAG = "MpvPlayer"
    }
}
