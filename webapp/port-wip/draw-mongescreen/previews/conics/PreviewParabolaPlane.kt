package draw.mongescreen.previews.conics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import draw.mongescreen.objects.orth.conics.drawConicParabolaNarys
import draw.mongescreen.objects.orth.conics.drawConicParabolaPudorys
import model.*
import model.classes.ConicSectionNarys
import model.classes.ConicSectionPudorys
import model.classes.Matrix3x3
import model.classes.Plane3D
import model.classes.PlaneEquation
import model.classes.projectToXY
import model.classes.projectToXZ
import monge.input.ConicArcs.single.getLogicalCursorNarys
import monge.input.planeobjects.conicsections.canonizeParabolaFrame
import monge.input.planeobjects.conicsections.computeXYDegeneracyFromVF
import monge.input.planeobjects.conicsections.extractVertexAndFocusFromConic
import monge.input.planeobjects.conicsections.liftConicToPlaneFromNarys
import monge.input.planeobjects.conicsections.liftConicToPlaneFromPudorys
import monge.input.planeobjects.conicsections.vertexFocus3DFromLocalConic
import state.MongeState
import utils.getLogicalCursor
import utils.toScreenOld
import kotlin.math.abs
import kotlin.math.max
import monge.input.conixections.computeParabolaFromVertexAndFocus as computeParabolaProjectionFromVertexAndFocus
import monge.input.conixections.computeParabolaFromVertexAndFocusNarys as computeParabolaProjectionFromVertexAndFocusNarys


fun DrawScope.drawParabolaConstructionPreviewBothViews(
    state: MongeState,
    snappedPointLogical: Offset?
) {
    when (state.projectionPhase) {
        "pudorys_focus" -> {
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

            val p1 = state.pendingPoint1 ?: return
            val focus2D = cursor            // místo focus2D=logical
            val plane = state.selectedPlaneForCircle ?: state.selectedPlanes.lastOrNull() ?: return
            val eq = plane.equation ?: return

        drawParabolaPreview(
            vertex2D = p1,
            focus2D = focus2D,
            plane = plane,
            eq = eq,
            canvasOffset = state.canvasOffset,
            scale = state.scale,
            state = state
        )
    }
    "narys_focus" ->{
        drawParabolaConstructionPreviewFromNarys(state, snappedPointLogical)
    }}
}

fun DrawScope.drawParabolaPreview(
    vertex2D: Offset,
    focus2D: Offset,
    plane: Plane3D,
    eq: PlaneEquation,
    canvasOffset: Offset,
    scale: Float,
    state: MongeState
) {
    val dx = focus2D.x - vertex2D.x
    val dy = focus2D.y - vertex2D.y
    if (abs(dx) < 1e-3f && abs(dy) < 1e-3f) return

    val EPS = 1e-6f
    if (abs(eq.c) < EPS) return
    val activeConic = computeParabolaProjectionFromVertexAndFocus(vertex2D, focus2D)
    val sourcePudorys = ConicSectionPudorys(
        a = activeConic.a, b = activeConic.b, c = activeConic.c,
        d = activeConic.d, e = activeConic.e, f = activeConic.f,
        rawName = "",
        localColor = Color.LightGray,
        lineStyle = LineStyle.Dashed
    )
    val conic3D = canonizeParabolaFrame(liftConicToPlaneFromPudorys(sourcePudorys, plane) ?: return)
    val vertexFocus3D = vertexFocus3DFromLocalConic(conic3D)

    // Aktivní půdorys se konstruuje přímo z vrcholu a ohniska výsledné paraboly.
    drawRedCross(vertex2D, state)
    drawDashedLine(vertex2D, focus2D, Color.Gray, state = state)
    drawConicParabolaPudorys(
        vertex = vertex2D,
        focus  = focus2D,
        canvasOffset = state.canvasOffset,
        scale = state.scale,
        color = Color.LightGray,
        strokeWidth = 1f,
        lineStyle = LineStyle.Dashed,
        tStep = 1f
    )
    if (state.projectionMode == ProjectionMode.KOTO) return
    // ── XZ (nárys): speciální degenerace pro |b|≈0, jinak standardní extrakce
    val isPerpToNarys = abs(eq.b) < EPS
    if (isPerpToNarys) {
        val (v3, f3) = vertexFocus3D ?: return
        val deg = computeXZDegeneracyFromVF(eq, v3, f3)
        if (deg.isLine) {
            // nekonečná přímka v XZ (náhledově čárkovaně)
            val span = max(size.width, size.height) / max(1e-6f, scale)
            val A = deg.origin - deg.dir * span
            val B = deg.origin + deg.dir * span
            drawLine(
                color = Color.LightGray,
                start = A.toScreenOld(scale, canvasOffset),
                end   = B.toScreenOld(scale, canvasOffset),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 12f), 0f)
            )
        } else {
            // polopřímka v XZ (degenerateRay = true)
            drawConicParabolaNarys(
                vertex = deg.origin,
                focus  = deg.origin + deg.dir,
                canvasOffset = canvasOffset,
                scale = scale,
                color = Color.LightGray,
                strokeWidth = 1f,
                lineStyle = LineStyle.Dashed,
                tStep = 1f,
                degenerateRay = true,
                rayExtendFactor = 2f
            )
        }
        return
    }

    // běžný XZ případ
    runCatching {
        val coeffsXZ = Matrix3x3.toCoefficients(conic3D.projectToXZ())
        val (vN, fN) = extractVertexAndFocusFromConic(
            a = coeffsXZ[0], b = coeffsXZ[1], c = coeffsXZ[2],
            d = coeffsXZ[3], e = coeffsXZ[4], f = coeffsXZ[5]
        ) ?: return@runCatching
        drawConicParabolaNarys(
            vertex = Offset(vN.x, vN.y),
            focus  = Offset(fN.x, fN.y),
            canvasOffset = canvasOffset,
            scale = scale,
            color = Color.LightGray,
            strokeWidth = 1f,
            lineStyle = LineStyle.Dashed,
            tStep = 1f
        )
    }.onFailure {
        println("⚠️ XZ preview: ${it.message}")
    }
}


fun DrawScope.drawParabolaConstructionPreviewFromNarys(
    state: MongeState,
    snappedPointLogical: Offset?
) {
    val cursorLogical = getLogicalCursorNarys(
        snappedPointLogical,
        state.cursorPosition,
        state.canvasOffset,
        state.scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection
    )

    // NÁRYS: display (x,-z) -> native (x,z)
    val vertexDisp = state.pendingPoint1 ?: return
    val vertexN    = Offset(vertexDisp.x, vertexDisp.y)  // native
    val cursorN    = Offset(cursorLogical.x, cursorLogical.y) // native

    val plane = state.selectedPlaneForCircle ?: return
    val eq    = plane.equation ?: return

    val EPS = 1e-6f
    // XZ-lift: y spočteme z rovnice roviny, potřebujeme |b|>0.
    if (abs(eq.b) < EPS) {
        return
    }
    val activeConic = computeParabolaProjectionFromVertexAndFocusNarys(vertexN, cursorN)
    val sourceNarys = ConicSectionNarys(
        a = activeConic.a, b = activeConic.b, c = activeConic.c,
        d = activeConic.d, e = activeConic.e, f = activeConic.f,
        rawName = "",
        localColor = Color.LightGray,
        lineStyle = LineStyle.Dashed
    )
    val conic3D = canonizeParabolaFrame(liftConicToPlaneFromNarys(sourceNarys, plane) ?: return)
    val vertexFocus3D = vertexFocus3DFromLocalConic(conic3D)

    // Aktivní nárys se konstruuje přímo z vrcholu a ohniska výsledné paraboly.
    drawRedCross(Offset(vertexN.x, -vertexN.y), state)
    drawDashedLine(Offset(vertexN.x, -vertexN.y), Offset(cursorN.x, -cursorN.y), Color.Gray, state = state)
    drawConicParabolaNarys(
        vertex       = vertexN,
        focus        = cursorN,
        canvasOffset = state.canvasOffset,
        scale        = state.scale,
        color        = Color.LightGray,
        strokeWidth  = 1f,
        lineStyle    = LineStyle.Dashed,
        tStep        = 1f
    )

    // === XY (půdorys) ===
    val isPerpToPudorys = abs(eq.c) < EPS
    if (isPerpToPudorys) {
        // degenerace v XY
        val (v3, f3) = vertexFocus3D ?: return
        val deg = computeXYDegeneracyFromVF(eq, v3, f3)
        if (deg.isLine) {
            val span = max(size.width, size.height) / max(1e-6f, state.scale)
            val A = deg.origin - deg.dir * span
            val B = deg.origin + deg.dir * span
            drawLine(
                color = Color.LightGray,
                start = A.toScreenOld(state.scale, state.canvasOffset),
                end   = B.toScreenOld(state.scale, state.canvasOffset),
                strokeWidth = 1f,
                pathEffect  = PathEffect.dashPathEffect(floatArrayOf(3f, 12f), 0f)
            )
        } else {
            drawConicParabolaPudorys(
                vertex = deg.origin,
                focus  = deg.origin + deg.dir,
                canvasOffset = state.canvasOffset,
                scale = state.scale,
                color = Color.LightGray,
                strokeWidth = 1f,
                lineStyle = LineStyle.Dashed,
                tStep = 1f,
                degenerateRay = true,
                rayExtendFactor = 2f
            )
        }
    } else {
        runCatching {
            val coeffsXY = Matrix3x3.toCoefficients(conic3D.projectToXY())
            val (vP, fP) = extractVertexAndFocusFromConic(
                a = coeffsXY[0], b = coeffsXY[1], c = coeffsXY[2],
                d = coeffsXY[3], e = coeffsXY[4], f = coeffsXY[5]
            ) ?: return@runCatching

            drawConicParabolaPudorys(
                vertex = vP,
                focus  = fP,
                canvasOffset = state.canvasOffset,
                scale = state.scale,
                color = Color.LightGray,
                strokeWidth = 1f,
                lineStyle = LineStyle.Dashed,
                tStep = 1f
            )
        }.onFailure {
            println("⚠️ XY preview (P): ${it.message}")
        }
    }
}

data class DegXZ(val origin: Offset, val dir: Offset, val isLine: Boolean)

fun computeXZDegeneracyFromVF(eq: PlaneEquation, v3: Offset3D, f3: Offset3D): DegXZ {
    val EPS = 1e-6f
    val nrm = Offset3D(eq.a, eq.b, eq.c).normalize()
    val axis = (f3 - v3).normalized()

    // směr průsečnice roviny s XZ: uDir ∥ n × e_y  (= (c,0,-a))
    val uDir = run {
        val cross = nrm.cross(Offset3D(0f, 1f, 0f))
        var d = Offset(cross.x, cross.z)
        val L = d.getDistance()
        if (L < EPS) d = Offset(1f, 0f) else d /= L
        d
    }

    // vektor v rovině kolmý na osu: nInPlane = n × axis
    val nInPlane = nrm.cross(axis).normalized()

    // rozklad projekce do XZ:
    // lineární (∝ t) a kvadratická (∝ t^2) složka na XZ
    val uXZ = Offset(nInPlane.x, nInPlane.z)
    val vXZ = Offset(axis.x,     axis.z)
    val p   = (f3 - v3).length().coerceAtLeast(EPS)

    val alpha = uXZ.x * uDir.x + uXZ.y * uDir.y
    val beta = (vXZ.x * uDir.x + vXZ.y * uDir.y) / (4f * p)

    if (abs(beta) < 1e-8f) {
        val origin = Offset(v3.x, v3.z)
        return DegXZ(origin, uDir, true)
    }

    val tStar = -alpha / (2f * beta)
    val sMin = alpha * tStar + beta * tStar * tStar

    val finalDir = if (beta < 0f) -uDir else uDir
    val originXZ = Offset(v3.x, v3.z) + finalDir * sMin

    return DegXZ(originXZ, finalDir, false)
}
