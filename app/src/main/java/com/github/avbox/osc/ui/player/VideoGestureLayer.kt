package com.github.avbox.osc.ui.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.github.avbox.osc.player.gesture.GestureVerdict
import com.github.avbox.osc.player.gesture.VERBOSE_GESTURE_LOG
import com.github.avbox.osc.player.gesture.VideoGestureHandler
import com.github.avbox.osc.player.gesture.VideoGestureSession
import com.github.avbox.osc.util.AppLog
import kotlinx.coroutines.withTimeoutOrNull

fun Modifier.videoGestureLayer(
    handler: VideoGestureHandler,
    sessionProvider: (IntSize, downY: Float) -> VideoGestureSession?,
    onTapPending: () -> Unit = {},
): Modifier = composed {
    var size = IntSize.Zero
    this
        .onSizeChanged { size = it }
        .pointerInput(handler) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = true)
                val snapshot = sessionProvider(size, down.position.y) ?: return@awaitEachGesture
                if (handler.beginSession(snapshot, down.position.x, down.position.y) ==
                    GestureVerdict.IGNORE
                ) {
                    return@awaitEachGesture
                }

                var firstUp = false
                var sawMove = false
                var lastMoveAt = 0L
                val longPressWon = withTimeoutOrNull(handler.longPressTimeoutMs) {
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Final)
                        val c = e.changes.firstOrNull { it.id == down.id } ?: continue
                        if (!c.pressed) {
                            firstUp = true
                            return@withTimeoutOrNull false
                        }
                        val pressedCount = e.changes.count { it.pressed }
                        if (pressedCount >= 2 && !snapshot.locked && !snapshot.previewMode) {
                            val pts = e.changes.filter { it.pressed }.map { it.position }
                            val dist = if (pts.size >= 2) {
                                kotlin.math.hypot(
                                    (pts[0].x - pts[1].x).toDouble(),
                                    (pts[0].y - pts[1].y).toDouble(),
                                ).toFloat()
                            } else 0f
                            if (dist > 0f) {
                                if (handler.isZooming) handler.onZoomMove(dist) else handler.beginZoom(dist)
                            }
                            e.changes.forEach { ch -> if (ch.id != down.id) ch.consume() }
                            continue
                        }
                        sawMove = true
                        lastMoveAt = System.currentTimeMillis()
                        if (!c.isConsumed) handler.onMove(c.position.x, c.position.y, 8f)
                        if (handler.hasMoved) return@withTimeoutOrNull false
                    }
                    @Suppress("UNREACHABLE_CODE") true
                }

                if (longPressWon == null && !firstUp) {
                    handler.maybeLongPress()
                }

                if (!firstUp) {
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Final)
                        if (handler.isZooming) {
                            val pressedCount = e.changes.count { it.pressed }
                            if (pressedCount >= 2) {
                                val pts = e.changes.filter { it.pressed }.map { it.position }
                                val dist = if (pts.size >= 2) {
                                    kotlin.math.hypot(
                                        (pts[0].x - pts[1].x).toDouble(),
                                        (pts[0].y - pts[1].y).toDouble(),
                                    ).toFloat()
                                } else 0f
                                if (dist > 0f) handler.onZoomMove(dist)
                                e.changes.forEach { ch -> if (ch.id != down.id) ch.consume() }
                                continue
                            }
                            break
                        }
                        val c = e.changes.firstOrNull { it.id == down.id } ?: break
                        if (!c.pressed) break
                        sawMove = true
                        lastMoveAt = System.currentTimeMillis()
                        if (!c.isConsumed) {
                            if (handler.onMove(c.position.x, c.position.y, 8f)) c.consume()
                        }
                    }
                }

                val quietMs = System.currentTimeMillis() - lastMoveAt
                val cancelled = !handler.isLongPressing && (!sawMove || quietMs > CANCEL_QUIET_MS)
                if (VERBOSE_GESTURE_LOG) {
                    AppLog.i(
                        "echo-gesture: sawMove=" + sawMove + " quietMs=" + quietMs +
                            " mode=" + handler.currentMode + " cancelled=" + cancelled +
                            " fromTopBand=" + snapshot.fromTopBand,
                    )
                }

                val result = handler.endSession(cancelled, System.currentTimeMillis())

                if (result == VideoGestureHandler.EndResult.TAP_PENDING) {
                    onTapPending()
                }
            }
        }
}

private const val CANCEL_QUIET_MS = 50L
