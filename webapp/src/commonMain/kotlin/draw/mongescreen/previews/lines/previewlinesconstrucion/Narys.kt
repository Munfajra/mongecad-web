package draw.mongescreen.previews.lines.previewlinesconstrucion

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import draw.mongescreen.axoPreviewDashPatternPx
import geometry.clipInfiniteLineToQuad
import draw.mongescreen.objects.orth.clipLineAboveX12
import draw.mongescreen.objects.orth.clipLineBelowX12
import model.VisibleQuad
import model.classes.Point3DNarys
import utils.dotProduct
import utils.toScreenOld
import kotlin.math.abs
import kotlin.math.sqrt

//nárys preview přímka (bod - bod)
fun DrawScope.drawDashedPreviewLineNarys(
    start: Point3DNarys,
    cursorWorld: Offset,
    scale: Float,
    canvasOffset: Offset,
    dashLength: Float = 10f,
    gapLength: Float = 10f,
    strokeWidth: Float = 1f,
    color: Color = Color.Red,
    clipToAboveZ: Boolean = false
) {
    val startX = start.x
    val startZ = start.z
    val endX = cursorWorld.x
    val endZ = cursorWorld.y

    val dirX = endX - startX
    val dirZ = endZ - startZ
    val dirLen = sqrt(dirX * dirX + dirZ * dirZ)
    if (dirLen == 0f) return

    val unitX = dirX / dirLen
    val unitZ = dirZ / dirLen

    val widthInWorld = (size.width / scale + abs(canvasOffset.x / scale)) * 2f
    val heightInWorld = (size.height / scale + abs(canvasOffset.y / scale)) * 2f
    val maxLen = sqrt(widthInWorld * widthInWorld + heightInWorld * heightInWorld)

    val rawA = Offset(startX - unitX * maxLen, startZ - unitZ * maxLen)
    val rawB = Offset(startX + unitX * maxLen, startZ + unitZ * maxLen)

    val (worldA, worldB) = if (clipToAboveZ) {
        clipLineAboveX12(rawA, rawB) ?: return
    } else {
        rawA to rawB
    }

    val screenA = Offset(
        worldA.x * scale + canvasOffset.x,
        -worldA.y * scale + canvasOffset.y
    )

    val screenB = Offset(
        worldB.x * scale + canvasOffset.x,
        -worldB.y * scale + canvasOffset.y
    )

    val screenDir = screenB - screenA
    val screenLen = screenDir.getDistance()
    if (screenLen == 0f) return

    val dx = screenDir.x / screenLen
    val dy = screenDir.y / screenLen

    var current = screenA
    var drawn = 0f

    while (drawn < screenLen) {
        val segmentStart = current
        val segmentEnd = Offset(
            current.x + dx * dashLength,
            current.y + dy * dashLength
        )

        drawLine(
            color = color,
            start = segmentStart,
            end = segmentEnd,
            strokeWidth = strokeWidth,
            alpha = 0.7f
        )

        current = Offset(
            current.x + dx * (dashLength + gapLength),
            current.y + dy * (dashLength + gapLength)
        )
        drawn += dashLength + gapLength
    }
}
//nárys preview PARALLEL přímka
fun DrawScope.drawDashedParallelLinePreviewNarys(
    through: Point3DNarys,
    direction: Offset,
    scale: Float,
    canvasOffset: Offset,
    dashLength: Float = 10f,
    gapLength: Float = 10f,
    strokeWidth: Float = 1f,
    color: Color = Color.Red,
    clipToAboveZ: Boolean = false // nový volitelný parametr
) {
    val dirLen = direction.getDistance()
    if (dirLen == 0f) return

    val unitX = direction.x / dirLen
    val unitY = direction.y / dirLen
    val widthInWorld = (size.width / scale + abs(canvasOffset.x / scale)) * 2f
    val heightInWorld = (size.height / scale + abs(canvasOffset.y / scale)) * 2f
    val maxLen = sqrt(widthInWorld * widthInWorld + heightInWorld * heightInWorld)

    val rawA = Offset(through.x - unitX * maxLen, through.z - unitY * maxLen)
    val rawB = Offset(through.x + unitX * maxLen, through.z + unitY * maxLen)

    val (worldA, worldB) = if (clipToAboveZ) {
        clipLineAboveX12(rawA, rawB) ?: return
    } else {
        rawA to rawB
    }

    val screenA = Offset(
        worldA.x * scale + canvasOffset.x,
        -worldA.y * scale + canvasOffset.y
    )
    val screenB = Offset(
        worldB.x * scale + canvasOffset.x,
        -worldB.y * scale + canvasOffset.y
    )

    val screenDir = screenB - screenA
    val screenLen = screenDir.getDistance()
    if (screenLen == 0f) return

    val dx = screenDir.x / screenLen
    val dy = screenDir.y / screenLen

    var current = screenA
    var drawn = 0f

    while (drawn < screenLen) {
        val segmentStart = current
        val segmentEnd = Offset(
            current.x + dx * dashLength,
            current.y + dy * dashLength
        )

        drawLine(
            color = color,
            start = segmentStart,
            end = segmentEnd,
            strokeWidth = strokeWidth,
            alpha = 0.7f
        )

        current = Offset(
            current.x + dx * (dashLength + gapLength),
            current.y + dy * (dashLength + gapLength)
        )
        drawn += dashLength + gapLength
    }
}
//segment preview NÁrys
fun DrawScope.drawDashedSegmentPreviewNarys(
    start: Point3DNarys,
    cursorWorld: Offset,
    scale: Float,
    canvasOffset: Offset,
    dashLength: Float = 10f,
    gapLength: Float = 10f,
    strokeWidth: Float = 1f,
    color: Color = Color.Red,
    alpha: Float = 0.7f
) {
    val startScreen = Offset(start.x, -start.z).toScreenOld(scale, canvasOffset)
    val endScreen = cursorWorld.toScreenOld(scale, canvasOffset)

    val dir = endScreen - startScreen
    val len = dir.getDistance()
    if (len < 1e-6f) return

    val dx = dir.x / len
    val dy = dir.y / len

    var current = startScreen
    var drawn = 0f

    while (drawn < len) {
        val segmentEnd = Offset(
            current.x + dx * dashLength,
            current.y + dy * dashLength
        )
        drawLine(
            color = color,
            start = current,
            end = segmentEnd,
            strokeWidth = strokeWidth,
            alpha = alpha
        )
        current = Offset(
            current.x + dx * (dashLength + gapLength),
            current.y + dy * (dashLength + gapLength)
        )
        drawn += dashLength + gapLength
    }
}


// Vyříznuto: drawDashedPreviewLineNarysAxo, drawDashedParallelLinePreviewNarysAxo – axo varianty náhledů; web axonometrii nekreslí.