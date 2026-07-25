package draw.mongescreen.previews.lines.previewlinesconstrucion

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import draw.mongescreen.axoPreviewDashPatternPx
import draw.mongescreen.objects.axo.clipInfiniteLineToQuad
import draw.mongescreen.objects.orth.clipLineBelowX12
import model.VisibleQuad
import model.classes.Point3DBokorys
import utils.dotProduct

fun DrawScope.drawDashedPreviewLineBokorysAxo(
    start: Point3DBokorys,
    cursorWorld: Offset,
    visibleQuad: VisibleQuad,
    color: Color = Color.Red,
    strokeWidth: Float = 1f,
    scale: Float = 1f

)
{
    val rawStart = Offset(start.y, start.z)
    val rawDir = cursorWorld - rawStart

    val dirLen = rawDir.getDistance()
    if (dirLen < 1e-6f) return

    val dir = if (rawDir.y < 0f) -rawDir else rawDir
    val fixedStart = if (rawDir.y < 0f) rawStart + rawDir else rawStart
    val unitDir = dir / dir.getDistance()

    val segment = clipInfiniteLineToQuad(
        point = fixedStart,
        dir = unitDir,
        quad = visibleQuad
    ) ?: return

    val a = segment.first
    val b = segment.second

    val pattern = axoPreviewDashPatternPx()
    val intervals = pattern.intervals
    val patternLenPx = pattern.len

    val base = Offset(0f, 0f)
    val distanceToA = (a - base).dotProduct(unitDir)
    val phase = ((distanceToA % patternLenPx) + patternLenPx) % patternLenPx

    val path = Path().apply {
        moveTo(a.x, a.y)
        lineTo(b.x, b.y)
    }

    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = strokeWidth/scale,
            pathEffect = PathEffect.dashPathEffect(intervals, phase),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        ),
        alpha = 0.7f
    )
}
fun DrawScope.drawDashedParallelLinePreviewBokorysAxo(
    through: Point3DBokorys,
    direction: Offset,
    visibleQuad: VisibleQuad,
    color: Color = Color.Gray,
    strokeWidth: Float = 1f,
    scale: Float = 1f,
    clipToBelowX12: Boolean = false
) {
    val dirLen = direction.getDistance()
    if (dirLen < 1e-6f) return

    val rawDir = direction
    val dir = if (rawDir.y < 0f) -rawDir else rawDir
    val fixedStart = if (rawDir.y < 0f) {
        Offset(through.y, through.z) + rawDir
    } else {
        Offset(through.y, through.z)
    }

    val unitDir = dir / dir.getDistance()

    val clipped = clipInfiniteLineToQuad(
        point = fixedStart,
        dir = unitDir,
        quad = visibleQuad
    ) ?: return

    var a = clipped.first
    var b = clipped.second

    if (clipToBelowX12) {
        val clippedBelow = clipLineBelowX12(a, b) ?: return
        a = clippedBelow.first
        b = clippedBelow.second
    }

    val pattern = axoPreviewDashPatternPx()
    val intervals = pattern.intervals
    val patternLenPx = pattern.len

    val base = Offset(0f, 0f)
    val distanceToA = (a - base).dotProduct(unitDir)
    val phase = ((distanceToA % patternLenPx) + patternLenPx) % patternLenPx

    val path = Path().apply {
        moveTo(a.x, a.y)
        lineTo(b.x, b.y)
    }

    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = strokeWidth / scale,
            pathEffect = PathEffect.dashPathEffect(intervals, phase),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        ),
        alpha = 0.7f
    )
}
