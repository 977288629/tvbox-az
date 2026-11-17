package com.github.avbox.osc.data

data class RemotePlaybackSnapshot(
    val title: String? = null,
    val episode: String? = null,
    val flag: String? = null,
    val index: Int = 0,
    val flags: List<String> = emptyList(),
    val episodes: List<String> = emptyList(),
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val playing: Boolean = false,
    val speed: Float = 1f,
    val volume: Int = 100,
    val muted: Boolean = false,
)

interface RemoteControlPort {

    fun snapshot(): RemotePlaybackSnapshot?

    fun togglePlay(): Boolean

    fun seekTo(positionMs: Long): Boolean

    fun playAdjacent(forward: Boolean): Boolean

    fun playEpisodeAt(index: Int): Boolean

    fun setSpeed(speed: Float): Boolean

    fun setVolume(percent: Int): Int

    fun toggleMute(): Boolean
}

object RemoteControlPorts {

    @Volatile
    var port: RemoteControlPort? = null
}
