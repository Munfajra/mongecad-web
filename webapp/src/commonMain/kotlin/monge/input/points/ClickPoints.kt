package monge.input.points

import utils.System
import androidx.compose.ui.geometry.Offset
import model.*
import model.classes.AidPointLogical
import model.classes.Line3D
import model.classes.Plane3D
import monge.input.combineprojections.NarysFinalizePointAuto
import monge.input.combineprojections.PudorysFinalizePointAuto
import monge.input.planeobjects.conicsections.planeEquationFromPlane3D
import state.MongeState
import ui.mongeui.toolbar.repeatCons
import ui.mongeui.toolbar.setProjectionPhase
import ui.resetStavu
import utils.allocIndex
import utils.getLogicalCursor

fun handleSinglePudorysPoint(
    cursor: Offset,
    snappedPointLogical: Offset?,
    canvasOffset: Offset,
    scale: Float,
    state: MongeState
) {
    if (state.projectionPhase!="pudorys_start") return
    val logical = getLogicalCursor(
        snappedPointLogical,
        cursor,
        canvasOffset,
        scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
        state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    )
    state.pendingX = logical.x
    state.pendingY = logical.y
    state.inputName = ""
    if (state.projectionMode == ProjectionMode.KOTO) {
        setProjectionPhase("KOTO_point", state)
        state.isNameConfirmed = false
        state.isKotaConfirmed = false
        state.inputKota=""
        state.deferSelectionUntil = System.currentTimeMillis() + 100
    }
else {

        setProjectionPhase("single_pudorys", state)
        state.isNameConfirmed = false
        state.deferSelectionUntil = System.currentTimeMillis() + 100
    }
}
fun handleSingleNarysPoint(
    cursor: Offset,
    snappedPointLogical: Offset?,
    canvasOffset: Offset,
    scale: Float,
    state: MongeState
) {
    val logical = getLogicalCursor(
        snappedPointLogical,
        cursor,
        canvasOffset,
        scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
        state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    )
    val logicalZ = -logical.y

    state.pendingX = logical.x
    state.pendingZ = logicalZ
    state.inputName = ""
    setProjectionPhase("single_narys", state)
    state.isNameConfirmed = false
    state.deferSelectionUntil = System.currentTimeMillis() + 100
}

//sdružené body (start půdorys)
fun handleCombinedPointProjectionPudorysFirst(
    cursor: Offset,
    snappedPointLogical: Offset?,
    canvasOffset: Offset,
    scale: Float,
    state: MongeState
) {
    if (
        state.mongeMode == DrawingModeMonge.PUDORYS &&
        state.drawobjects == Mongeobjects.POINTS &&
        state.projectionPhase == "pudorys_start" &&
        state.projekcnityp== ProjectionType.ASSOCIATED
    ) {
        val logical = getLogicalCursor(
            snappedPointLogical,
            cursor,
            canvasOffset,
            scale,
            state.canvasWidth,
            state.canvasHeight,
            state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
            state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
        )

        state.pendingX = logical.x
        state.pendingY = logical.y
        state.isNameConfirmed = false
        setProjectionPhase("pudorys_to_narys_point", state)
        state.pendingMongeModeChange = DrawingModeMonge.NARYS
        state.deferSelectionUntil = System.currentTimeMillis() + 100


        println("Průmět bodu – půdorys: x=${state.pendingX}, y=${ state.pendingY}")
    }
}

fun handleCombinedPointProjectionNarysSecond(
    cursor: Offset,
    snappedPointLogical: Offset?,
    canvasOffset: Offset,
    scale: Float,
    state: MongeState
) {
    if (
        state.mongeMode == DrawingModeMonge.NARYS &&
        state.drawobjects == Mongeobjects.POINTS &&
        state.projectionPhase == "pudorys_to_narys_point" &&
        state.pendingX != null &&
        state.pendingY != null &&
        !state.isNameConfirmed&&
        state.projekcnityp== ProjectionType.ASSOCIATED
    ) {
        val logical = getLogicalCursor(
            snappedPointLogical,
            cursor,
            canvasOffset,
            scale,
            state.canvasWidth,
            state.canvasHeight,
            state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
            state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
        )

        state.pendingZ = -logical.y
        if (state.reusingExistingProjection) {
            setProjectionPhase("narys_finalize_auto", state)
            NarysFinalizePointAuto(state)
        } else {
            setProjectionPhase("narys_finalize", state)
        }
        state.isNameConfirmed = false
        state.deferSelectionUntil = System.currentTimeMillis() + 100

    }
}

//sdružené body (start nárys)
fun handleCombinedPointProjectionNarysFirst(
    cursor: Offset,
    snappedPointLogical: Offset?,
    canvasOffset: Offset,
    scale: Float,
    state: MongeState
) {
    val logical = getLogicalCursor(
        snappedPointLogical,
        cursor,
        canvasOffset,
        scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
        state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    )

    if (
        state.mongeMode == DrawingModeMonge.NARYS &&
        state.drawobjects == Mongeobjects.POINTS &&
        state.projectionPhase == "narys_start"&&
        state.projekcnityp== ProjectionType.ASSOCIATED
    ) {
        state.pendingX = logical.x
        state.pendingZ = -logical.y
        state.isNameConfirmed = false
        setProjectionPhase("narys_to_pudorys_point", state)
        state.pendingMongeModeChange = DrawingModeMonge.PUDORYS
        state.deferSelectionUntil = System.currentTimeMillis() + 100
        println("Průmět bodu – nárys: x=${logical.x}, z=${-logical.y}")
    }

    if (
        state.mongeMode == DrawingModeMonge.PUDORYS &&
        state.drawobjects == Mongeobjects.POINTS &&
        state.projectionPhase == "narys_to_pudorys_point" &&
        state.projekcnityp== ProjectionType.ASSOCIATED
    ) {
        state.pendingY = logical.y
        if (state.reusingExistingProjection) {
            setProjectionPhase("pudorys_finalize_auto", state)
            PudorysFinalizePointAuto(state)
        } else {
            setProjectionPhase("pudorys_finalize", state)
        }
        state.isNameConfirmed = false
        state.deferSelectionUntil = System.currentTimeMillis() + 100

        println("Průmět bodu – půdorys: y=${logical.y}")
    }
}

fun handleAidPoint (cursor: Offset,
                        snappedPointLogical: Offset?,
                        canvasOffset: Offset,
                        scale: Float,
                        state: MongeState){
    val logical = getLogicalCursor(
        snappedPointLogical,
        cursor,
        canvasOffset,
        scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
        state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    )
    if (state.drawobjects == Mongeobjects.POINTS && state.projekcnityp== ProjectionType.AUXILIARY)
    {
        val point = AidPointLogical(
            logical.x,
            logical.y,
            color = state.currentHelpLineStyleSettings.color,
            creationIndex = allocIndex(state)
        )

        state.pendingAidPoint = point
        state.deferSelectionUntil = System.currentTimeMillis() + 100
        state.aidPointsLogical.add(point)
        repeatCons(state)
    }
}
private fun addPointInSelectedPlaneFromPudorysXY(
    state: MongeState,
    plane: Plane3D,
    xyLogical: Offset   // (x,y) v logických souřadnicích půdorysu
) {
    val (x, y) = xyLogical.x to xyLogical.y

    val eq = planeEquationFromPlane3D(plane)
    val eps = 1e-6f

    // Zakázat "rovina kolmá na půdorysnu" (tj. c ~ 0 => nelze jednoznačně spočítat z)
    if (kotlin.math.abs(eq.c) < eps) {
        println("⚠️ Nelze: rovina je (téměř) kolmá na půdorysnu (c≈0), z není jednoznačné.")
        return
    }

    val z = -(eq.a * x + eq.b * y + eq.d) / eq.c
    state.pendingX = x
    state.pendingY = y
    state.pendingZ = z

    setProjectionPhase("koto_plane_finalize", state)
    state.isNameConfirmed = false

    state.deferSelectionUntil = System.currentTimeMillis() + 100
    println("✅ Přidán bod v rovině ${plane.name}: (${x}, ${y}, ${z})")
}
private fun addPointOnSelectedLineFromPudorysXY(
    state: MongeState,
    line: Line3D,
    xyLogical: Offset   // (x,y) v logických souřadnicích půdorysu
) {
    val eps = 1e-6f

    val xClick = xyLogical.x
    val yClick = xyLogical.y

    val p0 = line.start       // Point3D (x,y,z)
    val d  = line.direction    // Offset3D (x,y,z)

    // 2D projekce přímky v půdorysu
    val a2 = Offset(p0.x, p0.y)
    val dir2 = Offset(d.x, d.y)

    val denom2 = dir2.x * dir2.x + dir2.y * dir2.y

    if (denom2 < eps) {
        println("⚠️ Nelze: přímka má v půdorysu degenerovanou projekci (je kolmá na půdorysnu).")
        resetStavu(state)
        return
    }

    // 1️⃣ Kolmý průmět kliknutého bodu na 2D projekci přímky
    val ap = Offset(xClick, yClick) - a2
    val t2 = (ap.x * dir2.x + ap.y * dir2.y) / denom2

    val xOnLine = a2.x + dir2.x * t2
    val yOnLine = a2.y + dir2.y * t2

    // 2️⃣ Parametr t ve 3D
    val t = t2

    val zOnLine = p0.z + d.z * t

    // 3️⃣ Ulož do pending (stejný pattern jako rovina)
    state.pendingX = xOnLine
    state.pendingY = yOnLine
    state.pendingZ = zOnLine
    state.isNameConfirmed = false
    setProjectionPhase("koto_plane_finalize", state)


    state.deferSelectionUntil = System.currentTimeMillis() + 100

    println("✅ Přidán bod na přímce ${line.name}: ($xOnLine, $yOnLine, $zOnLine)")
}


fun handleClick_AssociatedPointInPlane_PickXY(state: MongeState, xyLogical: Offset) {
    val plane = state.selectedPlaneForCircle ?: return

    // tady vytvoříš Point3D + Pud + Nar + parenty
    addPointInSelectedPlaneFromPudorysXY(state, plane, xyLogical)

}
fun handleClick_AssociatedPointOnLine_PickXY(state: MongeState, xyLogical: Offset) {
    val line = state.selectedLineForPoint ?: return

    // tady vytvoříš Point3D + Nar + parenty
    addPointOnSelectedLineFromPudorysXY(state, line, xyLogical)

}
fun handleClick_AssociatedPointInPlane_SelectPlane(state: MongeState, clickedPlane: Plane3D) {
    // krok 1: ulož rovinu a přepni fázi
    state.selectedPlaneForCircle= clickedPlane
    setProjectionPhase("plane_point_pick_xy", state)

    // tady NIC dalšího nekonstruuj
}
fun handleClick_AssociatedPointOnLine_SelectLine(state: MongeState, clickedLine: Line3D) {
    // krok 1: ulož rovinu a přepni fázi
    state.selectedLineForPoint= clickedLine
    setProjectionPhase("line_point_pick_xy", state)

    // tady NIC dalšího nekonstruuj
}
fun handleGetKotaPoint(
    snappedPointLogical: Offset?,
    state: MongeState,
    cursorWorld: Offset
) {
    val logical = getLogicalCursor(
            snappedPointLogical,
            cursorWorld,
            state.canvasOffset,
            state.scale,
            state.canvasWidth,
            state.canvasHeight,
            state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
            state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
        )
        when (state.projectionPhase) {
          "get_kota" -> {
                state.pendingPoint1 = logical
                setProjectionPhase("get_kota_p1", state)
                println("🟢 První bod uložen")
            }

            "get_kota_p1" -> {
                val p1 = state.pendingPoint1 ?: return
                val dist = (logical - p1).getDistance()*0.1f
                state.pendingDistance = dist
                state.inputKota=dist.toString()
                state.isKotaConfirmed=false
                state.isNameConfirmed=false

                setProjectionPhase("KOTO_point", state)
            }
        }
}