package com.github.avbox.osc.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * “猫头+摄像机”品牌加载动画（描边绘制 → 分层填充 → 高光弹出，循环往复）。
 * 坐标系与 160 单位 viewBox 的 SVG 设计稿一致，整体按组件尺寸等比缩放。
 */
private const val ViewBox = 160f

private const val StrokeWidthUnits = 2.5f

private enum class PartTone { INK, PAPER }

private class LoaderPart(
    val path: Path,
    val tone: PartTone,
    /** 相对 2.5s 循环的填充延迟（0..1），描边不受延迟影响（与设计稿一致）。 */
    val fillDelay: Float,
)

private fun catHeadPath(): Path = Path().apply {
    moveTo(34f, 108f)
    cubicTo(30f, 70f, 38f, 38f, 46f, 28f)
    cubicTo(50f, 24f, 62f, 40f, 70f, 52f)
    cubicTo(74f, 56f, 86f, 56f, 90f, 52f)
    cubicTo(98f, 40f, 110f, 24f, 114f, 28f)
    cubicTo(122f, 38f, 130f, 70f, 126f, 108f)
    close()
}

private fun roundedRectPath(x: Float, y: Float, w: Float, h: Float, r: Float): Path = Path().apply {
    addRoundRect(RoundRect(x, y, x + w, y + h, CornerRadius(r, r)))
}

private fun circlePath(cx: Float, cy: Float, r: Float): Path = Path().apply {
    addOval(Rect(center = Offset(cx, cy), radius = r))
}

private fun viewfinderPath(): Path = Path().apply {
    moveTo(118f, 88f)
    lineTo(138f, 80f)
    lineTo(138f, 108f)
    lineTo(118f, 100f)
    close()
}

private fun buildLoaderParts(): List<LoaderPart> = listOf(
    LoaderPart(catHeadPath(), PartTone.INK, fillDelay = 0.04f),
    LoaderPart(roundedRectPath(30f, 80f, 90f, 28f, 7f), PartTone.PAPER, fillDelay = 0.08f),
    LoaderPart(roundedRectPath(33f, 83f, 84f, 22f, 5f), PartTone.INK, fillDelay = 0.12f),
    LoaderPart(viewfinderPath(), PartTone.INK, fillDelay = 0.12f),
    LoaderPart(circlePath(75f, 94f, 10f), PartTone.PAPER, fillDelay = 0.16f),
    LoaderPart(circlePath(75f, 94f, 4.5f), PartTone.INK, fillDelay = 0.20f),
)

private fun plateau(progress: Float): Float = when {
    progress <= 0.25f -> 0f
    progress >= 0.75f -> 1f
    else -> (progress - 0.25f) / 0.5f
}

private fun DrawScope.drawLoaderPart(
    part: LoaderPart,
    cycle: Float,
    inkColor: Color,
    paperColor: Color,
    stroke: Stroke,
    measure: PathMeasure,
    scratch: Path,
) {
    val tone = when (part.tone) {
        PartTone.INK -> inkColor
        PartTone.PAPER -> paperColor
    }
    val local = if (part.fillDelay >= 1f) 0f else (cycle - part.fillDelay) / (1f - part.fillDelay)
    val alpha = plateau(local.coerceIn(0f, 1f))
    if (alpha > 0f) drawPath(part.path, tone, alpha = alpha)
    if (cycle > 0f) {
        measure.setPath(part.path, false)
        val length = measure.length
        scratch.rewind()
        if (length > 0f && measure.getSegment(0f, length * cycle, scratch, true)) {
            drawPath(scratch, inkColor, style = stroke)
        }
    }
}

@Composable
fun AvBoxLoader(
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    inkColor: Color = MaterialTheme.colorScheme.onSurface,
    paperColor: Color = MaterialTheme.colorScheme.surface,
) {
    val transition = rememberInfiniteTransition(label = "avboxLoader")
    // 描边+填充主循环：2.5s 正播 + 2.5s 逆播（对应 CSS animation alternate）。
    val cycle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2500, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "avboxLoaderCycle",
    )
    // 上下浮动：3s 一个来回。
    val floatPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "avboxLoaderFloat",
    )

    val parts = remember { buildLoaderParts() }
    val measure = remember { PathMeasure() }
    val scratch = remember { Path() }

    Canvas(
        modifier = modifier
            .graphicsLayer {
                val unitPx = 160.dp.toPx()
                translationY = -floatPhase * 4.dp.toPx() * (this.size.height / unitPx)
            },
    ) {
        val stroke = Stroke(width = StrokeWidthUnits, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val scaleFactor = this.size.minDimension / ViewBox
        scale(scaleFactor, scaleFactor, pivot = Offset.Zero) {
            parts.forEach { part ->
                drawLoaderPart(part, cycle, inkColor, paperColor, stroke, measure, scratch)
            }
            // 高光点：循环末段弹出（scale 0.8 → 1）。
            val pop = ((cycle - 0.85f) / 0.15f).coerceIn(0f, 1f)
            if (pop > 0f) {
                val radius = 1.6f * (0.8f + 0.2f * pop)
                drawCircle(paperColor, radius = radius, center = Offset(73f, 92f), alpha = pop)
            }
        }
    }
}
