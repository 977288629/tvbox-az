package com.github.avbox.osc.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.avbox.osc.util.KV
import com.github.avbox.osc.util.KVKeys
import com.github.avbox.osc.ui.components.VodCardStyle

data class UiSettingsConfig(
    val navAnimationDisabled: Boolean,
    val navLiveHidden: Boolean,
    val defaultLoadLive: Boolean,
    val collectColumns: Int,
    val vodCardStyle: VodCardStyle,
    val hideStatusBar: Boolean,
    val gestureControlDisabled: Boolean,
    val incognito: Boolean,
    val longPressSpeed: Int,
    val musicAutoOpenPage: Boolean,
    val dohIndex: Int,
    val downloadWifiOnly: Boolean,
    val downloadConcurrency: Int,
    val followNotify: Boolean,
)

object UiSettingsState {

    private var current by mutableStateOf(load())

    val config: UiSettingsConfig get() = current

    private fun load(): UiSettingsConfig = UiSettingsConfig(
        navAnimationDisabled = KV.get(KVKeys.NAV_ANIMATION_DISABLED, false),
        navLiveHidden = KV.get(KVKeys.NAV_LIVE_HIDDEN, false),
        defaultLoadLive = KV.get(KVKeys.DEFAULT_LOAD_LIVE, false),
        collectColumns = KV.get(KVKeys.COLLECT_COLUMNS, 3),
        vodCardStyle = VodCardStyle.of(KV.get(KVKeys.VOD_CARD_STYLE, 0)),
        hideStatusBar = KV.get(KVKeys.HIDE_STATUS_BAR, false),
        gestureControlDisabled = KV.get(KVKeys.GESTURE_CONTROL_DISABLED, false),
        incognito = KV.get(KVKeys.INCOGNITO, false),
        longPressSpeed = KV.get(KVKeys.LONG_PRESS_SPEED, KVKeys.LONG_PRESS_SPEED_DEFAULT),
        musicAutoOpenPage = KV.get(KVKeys.MUSIC_AUTO_OPEN_PAGE, true),
        dohIndex = KV.get(KVKeys.DOH_URL, 0),
        downloadWifiOnly = KV.get(KVKeys.DOWNLOAD_WIFI_ONLY, false),
        downloadConcurrency = KV.get(KVKeys.DOWNLOAD_CONCURRENCY, 3).coerceIn(1, 3),
        followNotify = KV.get(KVKeys.FOLLOW_NOTIFY, true),
    )

    fun reload() {
        current = load()
    }

    var navAnimationDisabled: Boolean
        get() = current.navAnimationDisabled
        set(value) {
            KV.put(KVKeys.NAV_ANIMATION_DISABLED, value)
            current = current.copy(navAnimationDisabled = value)
        }

    var followNotify: Boolean
        get() = current.followNotify
        set(value) {
            KV.put(KVKeys.FOLLOW_NOTIFY, value)
            current = current.copy(followNotify = value)
        }

    var navLiveHidden: Boolean
        get() = current.navLiveHidden
        set(value) {
            KV.put(KVKeys.NAV_LIVE_HIDDEN, value)
            current = current.copy(navLiveHidden = value)
        }

    var defaultLoadLive: Boolean
        get() = current.defaultLoadLive
        set(value) {
            KV.put(KVKeys.DEFAULT_LOAD_LIVE, value)
            current = current.copy(defaultLoadLive = value)
        }

    var collectColumns: Int
        get() = current.collectColumns
        set(value) {
            KV.put(KVKeys.COLLECT_COLUMNS, value)
            current = current.copy(collectColumns = value)
        }

    var vodCardStyle: VodCardStyle
        get() = current.vodCardStyle
        set(value) {
            KV.put(KVKeys.VOD_CARD_STYLE, value.ordinal)
            current = current.copy(vodCardStyle = value)
        }

    var hideStatusBar: Boolean
        get() = current.hideStatusBar
        set(value) {
            KV.put(KVKeys.HIDE_STATUS_BAR, value)
            current = current.copy(hideStatusBar = value)
        }

    var gestureControlDisabled: Boolean
        get() = current.gestureControlDisabled
        set(value) {
            KV.put(KVKeys.GESTURE_CONTROL_DISABLED, value)
            current = current.copy(gestureControlDisabled = value)
        }

    var incognito: Boolean
        get() = current.incognito
        set(value) {
            KV.put(KVKeys.INCOGNITO, value)
            current = current.copy(incognito = value)
        }

    var longPressSpeed: Int
        get() = current.longPressSpeed
        set(value) {
            KV.put(KVKeys.LONG_PRESS_SPEED, value)
            current = current.copy(longPressSpeed = value)
        }

    var musicAutoOpenPage: Boolean
        get() = current.musicAutoOpenPage
        set(value) {
            KV.put(KVKeys.MUSIC_AUTO_OPEN_PAGE, value)
            current = current.copy(musicAutoOpenPage = value)
        }

    var dohIndex: Int
        get() = current.dohIndex
        set(value) {
            KV.put(KVKeys.DOH_URL, value)
            current = current.copy(dohIndex = value)
        }

    var downloadWifiOnly: Boolean
        get() = current.downloadWifiOnly
        set(value) {
            KV.put(KVKeys.DOWNLOAD_WIFI_ONLY, value)
            current = current.copy(downloadWifiOnly = value)
        }

    var downloadConcurrency: Int
        get() = current.downloadConcurrency
        set(value) {
            val next = value.coerceIn(1, 3)
            KV.put(KVKeys.DOWNLOAD_CONCURRENCY, next)
            current = current.copy(downloadConcurrency = next)
        }
}
