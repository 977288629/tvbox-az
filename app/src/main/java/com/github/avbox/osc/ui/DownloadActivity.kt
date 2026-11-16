package com.github.avbox.osc.ui

import android.content.Context
import android.content.Intent
import androidx.compose.ui.platform.ComposeView
import com.github.avbox.osc.R
import com.github.avbox.osc.ui.base.BaseActivity
import com.github.avbox.osc.ui.page.DownloadScreen
import com.github.avbox.osc.ui.page.DownloadUiItem
import com.github.avbox.osc.ui.theme.AVBoxTheme
import com.github.avbox.osc.ui.theme.enableTransparentEdgeToEdge

class DownloadActivity : BaseActivity() {

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, DownloadActivity::class.java))
        }
    }

    override fun shouldRefreshAutoSize(): Boolean = true

    override fun hideSysBar() {
    }

    override fun init() {
        enableTransparentEdgeToEdge()
        findViewById<ComposeView>(R.id.compose_view).setContent {
            AVBoxTheme {
                DownloadScreen(
                    onNavigateBack = { finish() },
                    onPlay = { item -> play(item) },
                )
            }
        }
    }

    private fun play(item: DownloadUiItem) {
        val path = item.filePath ?: return
        LocalPlayActivity.start(this, path, item.name, item.sourceKey.orEmpty())
    }
}
