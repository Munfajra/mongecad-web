package monge.input.quadrics.spheres

import androidx.compose.ui.geometry.Offset
import serialization.commitSnapshot
import model.*
import model.classes.ConicSectionNarys
import model.classes.ConicSectionPudorys
import model.classes.SphereSurface3D
import monge.input.conixections.conjugateDiameterInputFromRadii
import monge.input.ConicArcs.single.getLogicalCursorNarys
import state.MongeState
import ui.mongeui.toolbar.repeatCons
import ui.mongeui.toolbar.setProjectionPhase
import utils.allocIndex
import utils.getLogicalCursor
import kotlin.math.hypot

fun startSphereFromSelection(state: MongeState) {
    when (state.mongeMode) {
        DrawingModeMonge.PUDORYS -> {
            val sel = state.selectedPointsPudorys
            val p = sel.singleOrNull()
                ?: return println("⚠️ Pro sféru vyber přesně 1 bod v půdorysu.")
            val parent3D = p.parent ?: return println("⚠️ Vybraný bod nemá parent Point3D.")
            state.pendingPoint1 = Offset(p.x, p.y)
            state.pendingId1 = parent3D.id
            state.projectionPhase = "sphere_radius_pick_pudorys"
        }
        DrawingModeMonge.NARYS -> {
            val sel = state.selectedPointsNarys
            val p = sel.singleOrNull()
                ?: return println("⚠️ Pro sféru vyber přesně 1 bod v nárysu.")
            val parent3D = p.parent ?: return println("⚠️ Vybraný bod nemá parent Point3D.")
            state.pendingPoint1 = Offset(p.x, p.z)
            state.pendingId1 = parent3D.id
            state.projectionPhase = "sphere_radius_pick_narys"
        }
    }
}

// Axonometrická větev konstrukce koule se na web neportuje (web nemá AXO mód).


fun finalizeSphereConstruction(state: MongeState, snappedPointLogical: Offset?) {
    val center2D   = state.pendingPoint1 ?: return
    val center3DId = state.pendingId1 ?: return

    // odečti r ve správném pohledu
    val cursorLogical = when (state.mongeMode) {
        DrawingModeMonge.NARYS -> getLogicalCursorNarys(
            snappedPointLogical,
            state.cursorPosition,
            state.canvasOffset,
            state.scale,
            state.canvasWidth,
            state.canvasHeight,
            state.xAxisDirection
        )
        DrawingModeMonge.PUDORYS -> getLogicalCursor(
            snappedPointLogical,
            state.cursorPosition,
            state.canvasOffset,
            state.scale,
            state.canvasWidth,
            state.canvasHeight,
            state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
            state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
        )
    }
    val r = hypot(cursorLogical.x - center2D.x, cursorLogical.y - center2D.y)
    if (r <= 0f) return

    // 1) 3D sféra
    val sphere = SphereSurface3D(
        centerPoint3DId = center3DId,
        radius = r,
        name = "σ",
        color = state.currentLineStyleSettings.color,
        strokeWidth = state.currentLineStyleSettings.strokeWidth, creationIndex = allocIndex(state)
    )
    state.spheres3D.add(sphere)

    // 2) najdi 2D středy v obou pohledech (projekce téhož 3D bodu)
    val cenP = state.pointsPudorys.find { it.parent?.id == center3DId }
    val cenN = state.pointsNarys.find  { it.parent?.id == center3DId }

    // 3) Průmět v PŮDORYSU: sféra se ukládá jako běžná kuželosečka s p1/p2/p3.
    cenP?.let { cp ->
        val x0 = cp.x
        val y0 = cp.y
        val A = 1f
        val B = 0f
        val C = 1f
        val D = -2f * x0
        val E = -2f * y0
        val F = x0 * x0 + y0 * y0 - r * r

        val conicP = ConicSectionPudorys(
            a = A,
            b = B,
            c = C,
            d = D,
            e = E,
            f = F,
            rawName = "k",
            localColor = sphere.color,
            parent = null,
            parentId = sphere.id,
            isHelpCircle = false,
            strokeWidth = sphere.strokeWidth ?: state.currentLineStyleSettings.strokeWidth,
            creationIndex = allocIndex(state)
        )
        state.conicsPudorys += conicP
        val center = Offset(x0, y0)
        state.conicInputPointsPudorys[conicP.id] = conjugateDiameterInputFromRadii(
            center,
            center + Offset(r, 0f),
            center + Offset(0f, r)
        )
    }

    // 4) Průmět v NÁRYSU: koeficienty používají z, input body obrazové y = -z.
    cenN?.let { cn ->
        val x0 = cn.x
        val z0 = cn.z
        val A = 1f
        val B = 0f
        val C = 1f
        val D = -2f * x0
        val E = -2f * z0
        val F = x0 * x0 + z0 * z0 - r * r

        val conicN = ConicSectionNarys(
            a = A,
            b = B,
            c = C,
            d = D,
            e = E,
            f = F,
            rawName = "k",
            localColor = sphere.color,
            parent = null,
            parentId = sphere.id,
            isHelpCircle = false,
            strokeWidth = sphere.strokeWidth ?: state.currentLineStyleSettings.strokeWidth,
            creationIndex = allocIndex(state)
        )
        state.conicsNarys += conicN
        val center = Offset(x0, -z0)
        state.conicInputPointsNarys[conicN.id] = conjugateDiameterInputFromRadii(
            center,
            center + Offset(r, 0f),
            center + Offset(0f, -r)
        )
    }

    // koule je kompletní (3D + oba průměty) → teprve teď commit jako jeden undo krok
    commitSnapshot(state)

    // 5) úklid stavů sféry
    state.pendingPoint1 = null
    state.pendingId1 = null
    state.projectionPhase = null
    state.triggerRedraw++
    state.spherePreviewRadius = null
    when (state.mongeMode) {
        DrawingModeMonge.NARYS -> setProjectionPhase("narys_start", state)
        DrawingModeMonge.PUDORYS -> setProjectionPhase("pudorys_start", state)
    }

    repeatCons(state)
}
