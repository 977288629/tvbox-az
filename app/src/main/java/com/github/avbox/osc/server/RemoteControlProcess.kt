package com.github.avbox.osc.server

import android.content.Context

import com.github.avbox.osc.data.RemoteControlPort
import com.github.avbox.osc.data.RemoteControlPorts
import com.github.avbox.osc.data.RemoteHistoryItem
import com.github.avbox.osc.data.RemoteHistoryPorts
import com.github.avbox.osc.data.RemotePlaybackSnapshot
import com.github.avbox.osc.util.AppLog
import com.google.gson.JsonArray
import com.google.gson.JsonObject

import fi.iki.elonen.NanoHTTPD

class RemoteControlProcess(private val hostContext: Context) : RequestProcess {

    override fun isRequest(session: NanoHTTPD.IHTTPSession, fileName: String?): Boolean {
        return fileName == "/remote" || fileName == "/api"
    }

    override fun doResponse(
        session: NanoHTTPD.IHTTPSession,
        fileName: String?,
        params: MutableMap<String, String>?,
        files: MutableMap<String, String>?,
    ): NanoHTTPD.Response {
        return try {
            if (fileName == "/remote") return servePage()
            serveApi(params)
        } catch (th: Throwable) {
            AppLog.e("remote-control error: " + th.message)
            json(NanoHTTPD.Response.Status.INTERNAL_ERROR, errorBody(th.message))
        }
    }

    private fun servePage(): NanoHTTPD.Response {
        val stream = hostContext.assets.open("remote.html")
        val response = NanoHTTPD.newChunkedResponse(NanoHTTPD.Response.Status.OK, "text/html; charset=utf-8", stream)
        response.addHeader("Cache-Control", "no-store")
        return response
    }

    private fun serveApi(params: MutableMap<String, String>?): NanoHTTPD.Response {
        val port = RemoteControlPorts.port
        val action = params?.get("action").orEmpty()
        if (action == "history") {
            val limit = params?.get("limit")?.toIntOrNull()?.coerceIn(1, 100) ?: 20
            val items = RemoteHistoryPorts.port?.recent(limit) ?: emptyList()
            return jsonOk(historyBody(items))
        }
        if (action == "playRecord") {
            RemoteHistoryPorts.port?.playRecord(
                params?.get("sourceKey").orEmpty(),
                params?.get("vodId").orEmpty(),
            )
        } else if (action == "pushSearch") {
            val ok = RemoteHistoryPorts.port?.pushSearch(params?.get("text").orEmpty()) ?: false
            if (!ok) return jsonOk(errorBody("device not ready"))
        } else if (action != "get") {
            if (port == null) return jsonOk(errorBody("engine not ready"))
            dispatch(port, action, params)
        }
        val snapshot = port?.snapshot()
        return jsonOk(okBody(snapshot))
    }

    private fun dispatch(port: RemoteControlPort, action: String, params: MutableMap<String, String>?) {
        when (action) {
            "toggle" -> port.togglePlay()
            "seek" -> params?.get("pos")?.toLongOrNull()?.let { port.seekTo(Math.max(0L, it)) }
            "next" -> port.playAdjacent(true)
            "prev" -> port.playAdjacent(false)
            "play" -> params?.get("index")?.toIntOrNull()?.let { port.playEpisodeAt(it) }
            "speed" -> params?.get("value")?.toFloatOrNull()?.let { port.setSpeed(it) }
            "vol" -> params?.get("value")?.toIntOrNull()?.let { port.setVolume(it) }
            "mute" -> port.toggleMute()
        }
    }

    private fun historyBody(items: List<RemoteHistoryItem>): JsonObject {
        val body = JsonObject()
        body.addProperty("code", 0)
        val list = JsonArray()
        for (item in items) {
            val obj = JsonObject()
            obj.addProperty("vodId", item.vodId)
            obj.addProperty("sourceKey", item.sourceKey)
            obj.addProperty("title", item.title)
            obj.addProperty("pic", item.pic)
            obj.addProperty("updateTime", item.updateTime)
            obj.addProperty("flag", item.flag)
            obj.addProperty("episodeIndex", item.episodeIndex)
            obj.addProperty("episodeCount", item.episodeCount)
            obj.addProperty("episodeName", item.episodeName)
            obj.addProperty("percent", item.percent)
            list.add(obj)
        }
        body.add("data", list)
        return body
    }

    private fun okBody(snapshot: RemotePlaybackSnapshot?): JsonObject {
        val body = JsonObject()
        body.addProperty("code", 0)
        if (snapshot == null) {
            body.add("data", com.google.gson.JsonNull.INSTANCE)
            return body
        }
        val data = JsonObject()
        data.addProperty("title", snapshot.title)
        data.addProperty("episode", snapshot.episode)
        data.addProperty("flag", snapshot.flag)
        data.addProperty("index", snapshot.index)
        data.addProperty("positionMs", snapshot.positionMs)
        data.addProperty("durationMs", snapshot.durationMs)
        data.addProperty("playing", snapshot.playing)
        data.addProperty("speed", snapshot.speed)
        data.addProperty("volume", snapshot.volume)
        data.addProperty("muted", snapshot.muted)
        val flags = JsonArray()
        for (item in snapshot.flags) flags.add(item)
        data.add("flags", flags)
        val episodes = JsonArray()
        for (item in snapshot.episodes) episodes.add(item)
        data.add("episodes", episodes)
        body.add("data", data)
        return body
    }

    private fun errorBody(message: String?): JsonObject {
        val body = JsonObject()
        body.addProperty("code", -1)
        body.addProperty("msg", message ?: "unknown")
        return body
    }

    private fun jsonOk(body: JsonObject): NanoHTTPD.Response =
        json(NanoHTTPD.Response.Status.OK, body)

    private fun json(status: NanoHTTPD.Response.Status, body: JsonObject): NanoHTTPD.Response {
        val response = NanoHTTPD.newFixedLengthResponse(status, "application/json; charset=utf-8", body.toString())
        response.addHeader("Cache-Control", "no-store")
        return response
    }
}
