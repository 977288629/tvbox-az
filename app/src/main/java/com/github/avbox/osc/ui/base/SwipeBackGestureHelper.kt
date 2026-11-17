package com.github.avbox.osc.ui.base

import android.app.Activity
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * 左边缘右滑返回手势层: 跟手平移内容视图, 松手过阈值触发 finish(), 不足则回弹。
 * 挂在 decorView 上, 不侵入业务布局; 横向位移超过纵向位移才视为有效手势, 避免劫持纵向滚动。
 */
class SwipeBackGestureHelper(
    private val activity: Activity,
    private val onBackTriggered: () -> Unit,
) {

    companion object {
        private const val EDGE_WIDTH_DP = 24f
        private const val FINISH_RATIO = 0.45f
        private const val MIN_FLING_VELOCITY_DP = 1400f
        private const val TOUCH_SLOP_DP = 8f
        private const val ANIM_MS = 200L
    }

    private val density = activity.resources.displayMetrics.density
    private val edgeWidthPx = (EDGE_WIDTH_DP * density).toInt()
    private val touchSlopPx = (TOUCH_SLOP_DP * density).toInt()
    private val flingVelocityPx = MIN_FLING_VELOCITY_DP * density

    private var tracking = false
    private var gestureActive = false
    private var startX = 0f
    private var startY = 0f
    private var lastX = 0f

    private var content: View? = null
    private val overlay: View = View(activity).apply {
        layoutParams = ViewGroup.LayoutParams(0, 0)
        isClickable = false
        isLongClickable = false
    }

    fun attach() {
        val decor = activity.window.decorView as? ViewGroup ?: return
        if (decor.findViewWithTag<View>("swipe_back_overlay") != null) return
        overlay.tag = "swipe_back_overlay"
        overlay.setOnTouchListener { _, event -> handleTouch(event) }
        decor.addView(
            overlay,
            FrameLayout.LayoutParams(
                edgeWidthPx,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.START,
            ),
        )
        activity.window.attributes = activity.window.attributes
    }

    fun detach() {
        (overlay.parent as? ViewGroup)?.removeView(overlay)
    }

    private fun contentRoot(): View? {
        val decor = activity.window.decorView as? ViewGroup ?: return null
        if (content != null && content!!.parent === decor) return content
        for (i in 0 until decor.childCount) {
            val child = decor.getChildAt(i)
            if (child !== overlay) {
                content = child
                return child
            }
        }
        return null
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                tracking = true
                gestureActive = false
                startX = event.rawX
                startY = event.rawY
                lastX = startX
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!tracking) return false
                val dx = event.rawX - startX
                val dy = event.rawY - startY
                if (!gestureActive) {
                    if (abs(dx) > touchSlopPx || abs(dy) > touchSlopPx) {
                        gestureActive = abs(dx) > abs(dy) && dx > 0
                    }
                    if (!gestureActive) {
                        tracking = abs(dy) <= touchSlopPx * 2
                        return gestureActive
                    }
                }
                lastX = event.rawX
                applyOffset(dx.coerceAtLeast(0f))
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!tracking) return false
                tracking = false
                if (!gestureActive) return false
                gestureActive = false
                val dx = lastX - startX
                val settled = dx > activity.resources.displayMetrics.widthPixels * FINISH_RATIO
                if (event.actionMasked == MotionEvent.ACTION_UP && settled) {
                    animateOut { finishNow() }
                } else {
                    animateBack()
                }
                return true
            }
        }
        return false
    }

    private fun applyOffset(dx: Float) {
        val target = contentRoot() ?: return
        target.translationX = dx
    }

    private fun animateOut(onEnd: () -> Unit) {
        val target = contentRoot() ?: run { onEnd(); return }
        val width = activity.resources.displayMetrics.widthPixels.toFloat()
        val from = target.translationX
        val start = System.currentTimeMillis()
        val runnable = object : Runnable {
            override fun run() {
                val t = ((System.currentTimeMillis() - start).toFloat() / ANIM_MS).coerceIn(0f, 1f)
                target.translationX = from + (width - from) * t
                if (t < 1f) {
                    target.postOnAnimation(this)
                } else {
                    onEnd()
                }
            }
        }
        target.postOnAnimation(runnable)
    }

    private fun animateBack() {
        val target = contentRoot() ?: return
        val from = target.translationX
        val start = System.currentTimeMillis()
        val runnable = object : Runnable {
            override fun run() {
                val t = ((System.currentTimeMillis() - start).toFloat() / ANIM_MS).coerceIn(0f, 1f)
                target.translationX = from * (1f - t)
                if (t < 1f) {
                    target.postOnAnimation(this)
                }
            }
        }
        target.postOnAnimation(runnable)
    }

    private fun finishNow() {
        onBackTriggered()
    }
}
