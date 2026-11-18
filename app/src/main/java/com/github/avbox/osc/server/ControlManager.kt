package com.github.avbox.osc.server

import android.content.Context

import com.github.avbox.osc.util.AppLog
import com.github.avbox.osc.util.LocalAddress

import java.io.IOException

class ControlManager private constructor() {
    @Volatile
    private var httpServer: RemoteServer? = null

    fun getAddress(local: Boolean): String {
        if (httpServer?.isStarting() != true) {
            startServer()
        }
        val server = httpServer ?: return ""
        if (!server.isStarting()) return ""
        return if (local) server.loadAddress() else server.serverAddress()
    }

    fun startServer() = synchronized(this) {
        if (httpServer?.isStarting() == true) {
            AppLog.i("echo-server startServer skipped, already starting")
            return
        }
        val context = requireNotNull(hostContext) { "control manager context not initialized" }
        var attempts = 0
        do {
            attempts++
            val server = RemoteServer(RemoteServer.serverPort, context)
            httpServer = server
            try {
                server.start()
                AppLog.i("echo-server started on port " + RemoteServer.serverPort + " attempts=" + attempts)
                onProxyPortReady?.invoke(RemoteServer.serverPort)
                break
            } catch (ex: IOException) {
                AppLog.i("echo-server bind fail port=" + RemoteServer.serverPort + " err=" + ex.message)
                RemoteServer.serverPort++
                server.stop()
            }
        } while (RemoteServer.serverPort < 9999)
    }

    fun startedPort(): Int {
        return if (httpServer?.isStarting() == true) RemoteServer.serverPort else 0
    }

    fun stopServer() {
        httpServer?.let {
            if (it.isStarting()) it.stop()
        }
        httpServer = null
    }

    companion object {
        @Volatile
        private var instance: ControlManager? = null

        @JvmField
        var hostContext: Context? = null

        @Volatile
        var onProxyPortReady: ((Int) -> Unit)? = null

        @JvmStatic
        fun get(): ControlManager {
            if (instance == null) {
                synchronized(ControlManager::class.java) {
                    if (instance == null) {
                        instance = ControlManager()
                    }
                }
            }
            return requireNotNull(instance) { "control manager instance missing" }
        }

        @JvmStatic
        fun init(context: Context) {
            hostContext = context
            LocalAddress.provider = { get().getAddress(true) }
        }
    }
}
