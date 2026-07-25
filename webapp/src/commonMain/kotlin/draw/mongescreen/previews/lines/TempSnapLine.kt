package draw.mongescreen.previews.lines

import monge.input.axo.lines.normalizedOrNull
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

import model.LineStyle
import model.VisibleQuad
import model.classes.Point3DBokorys
import model.classes.Point3DNarys
import model.classes.Point3DPudorys
import model.classes.TempSnapSpace

import state.MongeState

fun DrawScope.drawTempSnapLineAxoOverlay( state: MongeState ) {
    val temp = state.tempLine ?: return
    if (temp.space != TempSnapSpace.AO_OVERLAY) return
    val dir = temp.direction.normalizedOrNull() ?: return
    val p = temp.point

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
    Unit
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
    Unit
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
    Unit
}
