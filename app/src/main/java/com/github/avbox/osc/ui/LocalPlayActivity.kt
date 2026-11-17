package com.github.avbox.osc.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView
import com.github.avbox.osc.R
import com.github.avbox.osc.bean.VodInfo
import com.github.avbox.osc.player.PageHost
import com.github.avbox.osc.player.PlaybackSession
import com.github.avbox.osc.ui.base.BaseActivity
import com.github.avbox.osc.ui.player.PlayContainer
import com.github.avbox.osc.ui.theme.AVBoxTheme
import com.github.avbox.osc.ui.theme.enableTransparentEdgeToEdge
import com.github.avbox.osc.util.PermissionHelper
import java.io.File

class LocalPlayActivity : BaseActivity(), PageHost {

    private val pipMode = androidx.compose.runtime.mutableStateOf(false)

    override fun onUserLeaveHint() {
        if (container?.isPlaybackActive() == true) {
            enterPip()
            return
        }
        super.onUserLeaveHint()
    }

    private fun enterPip() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
        val params = android.app.PictureInPictureParams.Builder()
            .setAspectRatio(android.util.Rational(16, 9))
            .build()
        try {
            enterPictureInPictureMode(params)
        } catch (e: IllegalStateException) {
            android.util.Log.e("LocalPlayActivity", e.message ?: "pip failed")
        }
    }

    override fun onPictureInPictureModeChanged(isInPip: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPip, newConfig)
        pipMode.value = isInPip
    }

    private var container: PlayContainer? = null

    companion object {
        internal const val EXTRA_PATH = "path"
        internal const val EXTRA_NAME = "name"
        internal const val EXTRA_SOURCE = "sourceKey"

        fun start(context: Context, path: String, name: String, sourceKey: String) {
            context.startActivity(
                Intent(context, LocalPlayActivity::class.java)
                    .putExtra(EXTRA_PATH, path)
                    .putExtra(EXTRA_NAME, name)
                    .putExtra(EXTRA_SOURCE, sourceKey),
            )
        }
    }

    override fun shouldRefreshAutoSize(): Boolean = true

    override fun hideSysBar() {
    }

    override fun init() {
        enableTransparentEdgeToEdge()
        val c = PlayContainer(this).also { it.pageHostRef = this }
        container = c
        findViewById<ComposeView>(R.id.compose_view).setContent {
            AVBoxTheme {
                AndroidView(factory = { c }, modifier = Modifier.fillMaxSize())
            }
        }
        applyPlaybackFromIntent()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyPlaybackFromIntent()
    }

    private fun applyPlaybackFromIntent() {
        val path = intent.getStringExtra(EXTRA_PATH).orEmpty()
        val name = intent.getStringExtra(EXTRA_NAME).orEmpty()
        val sourceKey = intent.getStringExtra(EXTRA_SOURCE).orEmpty()
        val networkUrl = path.startsWith("http://") || path.startsWith("https://")
        if (path.isEmpty() || (!networkUrl && !File(path).exists())) {
            toast(getString(R.string.download_file_missing))
            finish()
            return
        }
        val uri = if (networkUrl) path else Uri.fromFile(File(path)).toString()
        val flag = "local"
        val title = name.ifEmpty { File(path).name }
        val vod = VodInfo(
            id = path,
            name = title,
            playFlag = flag,
            playIndex = 0,
            playNote = title,
            seriesFlags = arrayListOf(VodInfo.VodSeriesFlag(flag)),
            seriesMap = linkedMapOf<String?, MutableList<VodInfo.VodSeries>>(
                flag to mutableListOf(VodInfo.VodSeries(title, uri)),
            ),
        )
        container?.setData(PlaybackSession(vod, sourceKey, false))
    }

    override fun context(): Context = this

    override fun isPageAlive(): Boolean = !isFinishing && !isDestroyed

    override fun runOnUi(action: Runnable) {
        if (isPageAlive()) runOnUiThread(action)
    }

    override fun toast(text: CharSequence) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    override fun launchLocalSubtitlePicker() {
    }

    override fun requestNotificationPermission() {
        PermissionHelper.requestNotificationIfNeeded(this)
    }

    override fun showEpisodeSheet() {
    }

    override fun onPlaybackLinesExhausted(): Boolean = false

    override fun onResume() {
        super.onResume()
        container?.hostResume()
    }

    override fun onPause() {
        container?.hostPause()
        super.onPause()
    }

    override fun onDestroy() {
        container?.hostDestroy()
        container = null
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (container?.onBackPressed() == true) return
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }
}
