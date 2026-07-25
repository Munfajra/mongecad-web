package draw.mongescreen.previews.lines

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import draw.mongescreen.objects.axo.drawInfiniteAOLineOnScreen
import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedParallelLinePreviewBokorysAxo
import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedParallelLinePreviewNarysAxo
import draw.mongescreen.previews.lines.previewlinesconstrucion.drawDashedParallelLinePreviewPudorysAxo
import model.LineStyle
import model.VisibleQuad
import model.classes.Point3DBokorys
import model.classes.Point3DNarys
import model.classes.Point3DPudorys
import model.classes.TempSnapSpace
import monge.input.axo.lines.normalizedOrNull
import state.MongeState

fun DrawScope.drawTempSnapLineAxoOverlay( state: MongeState ) {
    val temp = state.tempLine ?: return
    if (temp.space != TempSnapSpace.AO_OVERLAY) return
    val dir = temp.direction.normalizedOrNull() ?: return
    val p = temp.point
    drawInfiniteAOLineOnScreen(
        state = state,
        throughLocal = p,
        directionLocal = dir,
        color = Color.Gray,
        lineWidth = 1f,
        lineStyle = LineStyle.Dashed,
        pxPerPt = 1f,
        alpha = 0.7f,
        previewDashPattern = true )
}
fun DrawScope.drawTempSnapLinePudorys( state: MongeState,visibleQuad: VisibleQuad? ) {
    if (visibleQuad == null) return
    val temp = state.tempLine ?: return
    if (temp.space != TempSnapSpace.PUDORYS) return
    val dir = temp.direction.normalizedOrNull() ?: return
    val p = Point3DPudorys(
        temp.point.x,
        temp.point.y,
    )
    drawDashedParallelLinePreviewPudorysAxo(
        through = p,
        direction = dir,
        scale = state.scale,
        visibleQuad = visibleQuad,


        )
}
fun DrawScope.drawTempSnapLineNarys( state: MongeState,visibleQuad: VisibleQuad?) {
    if (visibleQuad == null) return
    val temp = state.tempLine ?: return
    if (temp.space != TempSnapSpace.NARYS) return
    val dir = temp.direction.normalizedOrNull() ?: return
    val p = Point3DNarys(
        temp.point.x,
        temp.point.y,
    )
    drawDashedParallelLinePreviewNarysAxo(
        through = p,
        direction = dir,
        scale = state.scale,
        visibleQuad = visibleQuad,


        )
}
fun DrawScope.drawTempSnapLineBokorys( state: MongeState,visibleQuad: VisibleQuad?) {
    if (visibleQuad == null) return
    val temp = state.tempLine ?: return
    if (temp.space != TempSnapSpace.BOKORYS) return
    val dir = temp.direction.normalizedOrNull() ?: return
    val p = Point3DBokorys(
        temp.point.x,
        temp.point.y,
    )
    drawDashedParallelLinePreviewBokorysAxo(
        through = p,
        direction = dir,
        scale = state.scale,
        visibleQuad = visibleQuad,


        )
}
