package draw.mongescreen.previews.conicsarcs

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import draw.mongescreen.objects.orth.conics.drawEllipseArcFromDiameters
import model.*
import monge.input.ConicArcs.associated.*
import monge.input.ConicArcs.single.projectToEllipseFromDiameters
import state.MongeState
import utils.getLogicalCursor

fun DrawScope.drawEllipseArcNarys(state: MongeState, snappedPointLogical: Offset?) {
    if ((state.drawobjects == Mongeobjects.CONICARC || state.drawobjects == Mongeobjects.CONICARCAS) || state.mongeMode == DrawingModeMonge.NARYS) {
        if (state.projectionPhase != "narys_ellipse_arc" && state.projectionPhase != "narys_ellipse_arc_hold" ) return

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
        val conicId = state.activeConicIdForArc ?: return
        val inputs = state.conicInputPointsNarys[conicId] ?: return
        if (inputs.third == Offset.Unspecified || state.pendingArcA == null) return

        val (p1, p2, p3) = inputs
        val Aproj = projectToEllipseFromDiameters(p1, p2, p3, state.pendingArcA!!)
        val Bproj = projectToEllipseFromDiameters(p1, p2, p3, cursor)


        // náhled oblouku
        val stroke = (state.selectedConicsNarys.lastOrNull()?.strokeWidth ?: 1f) + 9f
        drawEllipseArcFromDiameters(
            p1 = p1, p2 = p2, p3 = p3,
            A = Aproj, B = Bproj,
            mode = state.activeArcMode ?: state.ellipseArcMode[conicId] ?: ArcMode.SHORTEST,
            scale = state.scale,
            canvasOffset = state.canvasOffset,
            color = Color.Red,
            strokeWidth = stroke,
            lineStyle = LineStyle.Solid
        )
    }
}
fun DrawScope.drawEllipseArcPudorys(state: MongeState, snappedPointLogical: Offset?) {
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
    if ((state.drawobjects == Mongeobjects.CONICARC||state.drawobjects == Mongeobjects.CONICARCAS)&&
        state.mongeMode == DrawingModeMonge.PUDORYS) {

        val conic = state.activeConicIdForArc
        val conicwidth = state.conicsPudorys.find { it.id==conic }?: return
        val inputs = conic?.let { state.conicInputPointsPudorys[it] }
        val A = state.pendingArcA

        if (conic != null && inputs != null && inputs.third != Offset.Unspecified && A != null) {
            val (p1, p2, p3) = inputs

            // ⬅️ Promítneme oba body na elipsu:
            val Aproj = projectToEllipseFromDiameters(p1, p2, p3, A)
            val Bproj = projectToEllipseFromDiameters(p1, p2, p3, cursor)

            drawEllipseArcFromDiameters(
                p1 = p1, p2 = p2, p3 = p3,
                A = Aproj, B = Bproj,
                mode = state.activeArcMode ?: state.ellipseArcMode[conic] ?: ArcMode.SHORTEST,
                scale = state.scale,
                canvasOffset = state.canvasOffset,
                color = Color.Red,
                strokeWidth = conicwidth.strokeWidth + 9f,
                lineStyle = LineStyle.Solid
            )
        }
    }
}
data class ArcPreview(
    val pConicId: String?, val nConicId: String?,
    val A_p: Offset?, val B_p: Offset?,
    val A_n: Offset?, val B_n: Offset?,
    val mode: ArcMode? = null
)

private fun computeEllipseArcPreview(state: MongeState, cursorLogical: Offset): ArcPreview {
    // PŮDORYS-FIRST
    if (state.projectionPhase ==  "pudorys_elip3d_arc_hold"||state.projectionPhase ==  "pudorys_elip3d_arc") {
        val conicP = state.activeConicIdForArc ?: return ArcPreview(null,null,null,null,null,null)
        val inputsP = state.conicInputPointsPudorys[conicP] ?: return ArcPreview(null,null,null,null,null,null)
        val (p1,p2,p3) = inputsP
        if (p3 == Offset.Unspecified) return ArcPreview(null,null,null,null,null,null)

        val parentId = state.activeParentConic3DIdForEllipseArc ?: return ArcPreview(null,null,null,null,null,null)
        val parent = state.conics3D.firstOrNull { it.id == parentId } ?: return ArcPreview(null,null,null,null,null,null)

        val A_p = state.pendingArcA_3D ?: return ArcPreview(null,null,null,null,null,null)
        val B_p = projectToEllipseFromDiameters(p1,p2,p3,cursorLogical)
        val mode = state.activeArcMode
            ?: state.ellipseArcMode[conicP]
            ?: ArcMode.SHORTEST

        // lift P -> 3D -> N (x,z)
        val liftA = liftPudorysPointTo3DAndNarys(A_p.x, A_p.y, parent) ?: return ArcPreview(null,null,null,null,null,null)
        val liftB = liftPudorysPointTo3DAndNarys(B_p.x, B_p.y, parent) ?: return ArcPreview(null,null,null,null,null,null)
        var (_, A_n) = liftA
        var (_, B_n) = liftB

        // sestra v N + sign-fix + dosnap
        val nId = state.findNarysConicIdByParent(parent.id)
        if (nId != null) {
            val A_fixed = state.snapToNarysEllipseFixSign(nId, A_n)
            val B_fixed = state.snapToNarysEllipseFixSign(nId, B_n)
            val (n1,n2,n3) = state.conicInputPointsNarys[nId]!!
            A_n = projectToEllipseFromDiameters(n1,n2,n3,A_fixed)
            B_n = projectToEllipseFromDiameters(n1,n2,n3,B_fixed)
        }

        return ArcPreview(conicP, nId, A_p, B_p, A_n, B_n, mode)
    }

    // NÁRYS-FIRST
    if (state.projectionPhase == "narys_elip3d_arc_hold"||state.projectionPhase == "narys_elip3d_arc") {
        val conicN = state.activeConicIdForArc?: return ArcPreview(null,null,null,null,null,null)
        val inputsN = state.conicInputPointsNarys[conicN] ?: return ArcPreview(null,null,null,null,null,null)
        val (n1,n2,n3) = inputsN
        if (n3 == Offset.Unspecified) return ArcPreview(null,null,null,null,null,null)

        val parentId = state.activeParentConic3DIdForEllipseArc ?: return ArcPreview(null,null,null,null,null,null)
        val parent = state.conics3D.firstOrNull { it.id == parentId } ?: return ArcPreview(null,null,null,null,null,null)

        val A_n = state.pendingArcA_3D ?: return ArcPreview(null,null,null,null,null,null)
        val B_n = projectToEllipseFromDiameters(n1,n2,n3,cursorLogical)
        val mode = state.activeArcMode
            ?: state.ellipseArcMode[conicN]
            ?: ArcMode.SHORTEST

        // POZOR: tvoje konvence – do liftu posíláš (x, -y) protože y = -z
        val (_, A_p_raw) = liftNarysPointTo3DAndPudorys(A_n.x, -A_n.y, parent) ?: return ArcPreview(null,null,null,null,null,null)
        val (_, B_p_raw) = liftNarysPointTo3DAndPudorys(B_n.x, -B_n.y, parent) ?: return ArcPreview(null,null,null,null,null,null)

        // sestra v P + sign-fix + dosnap
        var A_p = A_p_raw
        var B_p = B_p_raw
        val pId = state.findPudorysConicIdByParent(parent.id)
        if (pId != null) {
            val A_fixed = state.snapToPudorysEllipseFixSign(pId, A_p_raw)
            val B_fixed = state.snapToPudorysEllipseFixSign(pId, B_p_raw)
            val (p1,p2,p3) = state.conicInputPointsPudorys[pId]!!
            A_p = projectToEllipseFromDiameters(p1,p2,p3,A_fixed)
            B_p = projectToEllipseFromDiameters(p1,p2,p3,B_fixed)
        }

        return ArcPreview(pId, conicN, A_p, B_p, A_n, B_n, mode)
    }

    return ArcPreview(null,null,null,null,null,null)
}
fun DrawScope.drawEllipseArcPreviewPudorys(state: MongeState, snappedPointLogical: Offset?) {
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
    if (state.drawobjects != Mongeobjects.CONICARC && state.drawobjects != Mongeobjects.CONICARCAS) return

    val prev = computeEllipseArcPreview(state, cursor)
    val conicId = prev.pConicId ?: return
    val A = prev.A_p ?: return
    val B = prev.B_p ?: return

    val inputs = state.conicInputPointsPudorys[conicId] ?: return
    val (p1,p2,p3) = inputs
    if (p3 == Offset.Unspecified) return

    drawEllipseArcFromDiameters(
        p1 = p1, p2 = p2, p3 = p3,
        A = A, B = B,
        mode = prev.mode ?: state.ellipseArcMode[conicId] ?: ArcMode.SHORTEST,
        scale = state.scale,
        canvasOffset = state.canvasOffset,
        color = Color.Red,
        strokeWidth = (state.selectedConicsPudorys.lastOrNull()?.strokeWidth ?: 1f) + 9f,
        lineStyle = LineStyle.Solid
    )
}
fun DrawScope.drawEllipseArcPreviewNarys(state: MongeState, snappedPointLogical: Offset?) {
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
    if (state.drawobjects != Mongeobjects.CONICARC && state.drawobjects != Mongeobjects.CONICARCAS) return

    val prev = computeEllipseArcPreview(state, cursor)
    val conicId = prev.nConicId ?: return
    val A = prev.A_n ?: return
    val B = prev.B_n ?: return

    val inputs = state.conicInputPointsNarys[conicId] ?: return
    val (n1,n2,n3) = inputs
    if (n3 == Offset.Unspecified) return

    drawEllipseArcFromDiameters(
        p1 = n1, p2 = n2, p3 = n3,
        A = A, B = B,
        mode = prev.mode ?: state.ellipseArcMode[conicId] ?: ArcMode.SHORTEST,
        scale = state.scale,
        canvasOffset = state.canvasOffset,
        color = Color.Red,
        strokeWidth = (state.selectedConicsNarys.lastOrNull()?.strokeWidth ?: 1f) + 9f,
        lineStyle = LineStyle.Solid
    )
}
