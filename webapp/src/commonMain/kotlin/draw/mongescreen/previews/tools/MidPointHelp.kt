package draw.mongescreen.previews.tools

import monge.input.axo.points.screenToAxoOverlayLocal
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedSegmentPreviewPudorys
import model.LineStyle
import model.Mongeobjects
import model.XAxisDirection
import model.YAxisDirectionPlane
import model.classes.Point3DPudorys

import state.MongeState
import utils.getLogicalCursor
import utils.toScreenOld


fun DrawScope.midPointPreview(state: MongeState, snappedPointLogical: Offset?){
    val cursor = getLogicalCursor(
        snappedPointLogical,
        state.cursorPosition,
        state.canvasOffset,
        state.scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
        state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    )
    // ▼ PREVIEW pro nástroj „Střed“ (AID_MIDPOINT)
    if (
        state.drawobjects == Mongeobjects.AID_MIDPOINT &&
        state.midpointPoint1 != null

    ) {
        val p1       = state.midpointPoint1!!          // logický Offset 1. bodu
        val p2       = cursor         // kurzor v logických
        val midpoint = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)

        // 1) červená přerušovaná spojnice (půdorys)
        drawDashedSegmentPreviewPudorys(
            start        = Point3DPudorys(p1.x, p1.y, name = ""), // jen obal → x,y
            cursorWorld  = p2,
            scale        = state.scale,
            canvasOffset = state.canvasOffset,
            dashLength   = 8f,
            gapLength    = 6f,
            strokeWidth  = 1f,
            color        = Color.Red,
            alpha        = 0.7f
        )

        // 2) křížek ve středu
        val screenMid = midpoint.toScreenOld(state.scale, state.canvasOffset)
        val sizePx    = 12f          // polovina velikosti (logická → screen je už hotový)
        drawLine(
            color = Color.Red,
            start = screenMid - Offset(sizePx, 0f),
            end   = screenMid + Offset(sizePx, 0f),
            strokeWidth = 2f
        )
        drawLine(
            color = Color.Red,
            start = screenMid - Offset(0f, sizePx),
            end   = screenMid + Offset(0f, sizePx),
            strokeWidth = 2f
        )
    }


}
fun DrawScope.midPointPreviewAxo(state: MongeState){
    val basis = state.basis ?: return

    val cursor = state.snappedPointLogical
        ?: screenToAxoOverlayLocal(state.cursorPosition, state, basis)
    // ▼ PREVIEW pro nástroj „Střed“ (AID_MIDPOINT)
    if (
        state.drawobjects == Mongeobjects.AID_MIDPOINT &&
        state.midpointPoint1 != null

    ) {
        val p1       = state.midpointPoint1!!          // logický Offset 1. bodu
        val p2       = cursor         // kurzor v logických
        val midpoint = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)

        // 1) červená přerušovaná spojnice (půdorys)


        // 2) křížek ve středu

    }


}