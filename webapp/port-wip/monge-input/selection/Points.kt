package monge.input.selection

import utils.System
import model.CompletionRequest
import model.DrawingModeMonge
import model.Mongeobjects
import model.Point3D
import model.ProjectionMode
import model.classes.Point3DAxo
import model.classes.Point3DBokorys
import model.classes.Point3DNarys
import model.classes.Point3DPudorys
import monge.input.combineprojections.handleCombineProjections
import monge.input.combineprojections.handlePlaneFromTracePickPointClick
import monge.input.planeobjects.planelift.decideObjectForLift
import monge.input.quadrics.conicalsurface.handleConicalToolClick
import monge.input.solids.handlePyramidToolClick
import state.MongeState
import kotlin.math.abs


fun toggleSelection(
    point: Point3DNarys,
    state: MongeState
) {
    val tolerance = 0.01f
    val existing = state.selectedPointsNarys.find {
        abs(it.x - point.x) < tolerance &&
                abs(it.z - point.z) < tolerance
    }

    val pending = state.completionPending
    if (state.drawobjects == Mongeobjects.CONE){
        if(point.parent!=null){
        state.pendingApex3DId = point.parent!!.id
        handleConicalToolClick(state)
    }}
    if (state.drawobjects == Mongeobjects.PYRAMID){
        if(point.parent!=null){
        state.pendingPyramidApexId = point.parent!!.id
        handlePyramidToolClick(state, state.projectionMode == ProjectionMode.AXO)
    }}
    if (
        pending is CompletionRequest.Point3DFromProjections &&
        pending.expect == DrawingModeMonge.NARYS
    ) {
        handleCombineProjections(state, point)
        return
    }

    if (existing != null) {
        state.selectedPointsNarys.remove(existing)
        println("🟡 Odznačen bod v NÁRYSU: ${point.name} ${point.localSuperscript}, (x=${point.x}, z=${point.z})")

    } else {
        state.selectedPointsNarys.add(point)
        decideObjectForLift(state)
        advancePendingLineOrPointConstruction(state)
        println("🟢 Označen bod v NÁRYSU: ${point.name}${point.localSuperscript}, (x=${point.x}, z=${point.z})")
    }


}

//označení bodu nárys
fun handleSelectionNarysPoint(
    state: MongeState
) {  if (System.currentTimeMillis() < state.deferSelectionUntil) {
    println("⛔️ Výběr zablokován časovým štítem")
    return
}
    val snapped = state.snappedPointNarys?: return

    val snappedX = snapped.x
    val snappedZ = snapped.z

    val existing = state.pointsNarys.find {
        abs(it.x - snappedX) < 0.01f && abs(it.z - snappedZ) < 0.01f
    }
    val allowed = state.drawobjects == Mongeobjects.NONE|| state.drawobjects== Mongeobjects.PLANE_LIFT || (state.drawobjects == Mongeobjects.CONE || state.drawobjects == Mongeobjects.SPHERE)
    if (!allowed) return
    if (existing != null) {


        if (state.isShiftPressed) {
            toggleSelection(existing,state)
            println("Vybrán bod v NÁRYSU (se shiftem): ${existing.name} ${existing.localSuperscript}(x=${existing.x}, z=${existing.z})")
            state.deferSelectionUntil = System.currentTimeMillis() + 100
        } else {
            if ((state.drawobjects == Mongeobjects.CONE || state.drawobjects == Mongeobjects.SPHERE) && existing.parent==null) return
            if (System.currentTimeMillis() >= state.deferSelectionUntil) {
            state.selectedPointsNarys.clear()
            state.selectedPointsPudorys.clear()}
            state.selectedPointsNarys.add(existing)
            decideObjectForLift(state)
            println("Vybrán bod v NÁRYSU (bez shiftu): ${existing.name}${existing.localSuperscript} (x=${existing.x}, z=${existing.z})")
            state.deferSelectionUntil = System.currentTimeMillis() + 100
            val pending = state.completionPending
            if (pending is CompletionRequest.Point3DFromProjections &&
                pending.expect == DrawingModeMonge.NARYS
            ) {
                handleCombineProjections(state, existing)
                return
            }
        }
    }
}


//označení bodu půdorys
fun toggleSelectionPudorys(
    point: Point3DPudorys,
    state: MongeState
) {
    val tolerance = 0.01f
    val existing = state.selectedPointsPudorys.find {
        abs(it.x - point.x) < tolerance &&
                abs(it.y - point.y) < tolerance
    }
    val pending = state.completionPending
    if (
        pending is CompletionRequest.Point3DFromProjections &&
        pending.expect == DrawingModeMonge.PUDORYS
    ) {
        handleCombineProjections(state, point)
        return
    }

    if (existing != null) {

        state.selectedPointsPudorys.remove(existing)
        println("🟡 Odznačen bod v PŮDORYSU: ${point.name}, (x=${point.x}, y=${point.y})")
        state.deferSelectionUntil = System.currentTimeMillis() + 100
    } else {

        state.selectedPointsPudorys.add(point)
        decideObjectForLift(state)
        advancePendingLineOrPointConstruction(state)
        println("🟢 Označen bod v PŮDORYSU: ${point.name}, (x=${point.x}, y=${point.y})")
        state.deferSelectionUntil = System.currentTimeMillis() + 100
    }
    if (state.drawobjects == Mongeobjects.CONE){
        if(point.parent!=null){
            state.pendingApex3DId = point.parent!!.id
            handleConicalToolClick(state)
        }}
    if (state.drawobjects == Mongeobjects.PYRAMID){
        if(point.parent!=null){
            state.pendingPyramidApexId = point.parent!!.id
            handlePyramidToolClick(state, state.projectionMode == ProjectionMode.AXO)
        }}
    state.deferSelectionUntil = System.currentTimeMillis() + 100
    handlePlaneFromTracePickPointClick(state)
}



fun handleSelectionPudorysPoint(
    state: MongeState
) {
    if (System.currentTimeMillis() < state.deferSelectionUntil) {
        println("⛔️ Výběr zablokován časovým štítem")
        return
    }
    val allowed = state.drawobjects == Mongeobjects.NONE ||
            (state.drawobjects == Mongeobjects.SPHERE || state.drawobjects == Mongeobjects.PLANE_LIFT)
    if (!allowed) return



        val existing = state.snappedPointPudorys

        if (existing != null) {
            if (state.isShiftPressed) {
                toggleSelectionPudorys(existing, state)
                println("Vybrán bod v PŮDORYSU (se shiftem): ${existing.name}${existing.localSuperscript} (x=${existing.x}, y=${existing.y})")
                state.deferSelectionUntil = System.currentTimeMillis() + 100
            } else {
                if ((state.drawobjects == Mongeobjects.CONE || state.drawobjects == Mongeobjects.SPHERE) && existing.parent==null) return
                if (System.currentTimeMillis() >= state.deferSelectionUntil) {
                state.selectedPointsPudorys.clear()
                state.selectedPointsNarys.clear()}
                state.selectedPointsPudorys.add(existing)
                decideObjectForLift(state)
                state.deferSelectionUntil = System.currentTimeMillis() + 100
                val pending = state.completionPending
                if (
                    pending is CompletionRequest.Point3DFromProjections &&
                    pending.expect == DrawingModeMonge.PUDORYS
                ) {
                    handleCombineProjections(state, existing)
                    return
                }



                println("Vybrán bod v PŮDORYSU (bez shiftu): ${existing.name}${existing.localSuperscript} (x=${existing.x}, y=${existing.y})")
            }
        }
}
fun toggleSelectionPoint3D (p: Point3D, state: MongeState) {
    state.selectedPoints3D.add(p)
   val pudorys=  state.pointsPudorys.firstOrNull(){it.parent?.id==p.id}
    val narys = state.pointsNarys.firstOrNull(){it.parent?.id==p.id}
    val bokorys = state.pointsBokorys.firstOrNull(){it.parent?.id==p.id}
    val axo = state.pointsAxo.firstOrNull(){it.parent?.id==p.id}
    if (pudorys != null) {toggleSelectionPudorys(pudorys,state)}
    if (narys != null) {toggleSelection(narys,state)}
    if (bokorys!= null){toggleSelectionBokorys(bokorys,state)}
    if (axo != null) {toggleSelectionAxo(axo,state)}
}
//označení bodu půdorys
fun toggleSelectionBokorys(
    point: Point3DBokorys,
    state: MongeState
) {
    val tolerance = 0.01f
    val existing = state.selectedPointsBokorys.find {
        abs(it.y - point.y) < tolerance &&
                abs(it.z - point.z) < tolerance
    }
    val pending = state.completionPending


    if (existing != null) {

        state.selectedPointsBokorys.remove(existing)
        println("🟡 Odznačen bod v BOKORYSU: ${point.name}, (x=${point.y}, y=${point.z})")
        state.deferSelectionUntil = System.currentTimeMillis() + 100
    } else {

        state.selectedPointsBokorys.add(point)
        decideObjectForLift(state)
        advancePendingLineOrPointConstruction(state)
        println("🟢 Označen bod v BOKORYSU: ${point.name}, (x=${point.y}, y=${point.z})")
        state.deferSelectionUntil = System.currentTimeMillis() + 100
    }
    if (state.drawobjects == Mongeobjects.CONE){
        if(point.parent!=null){
            state.pendingApex3DId = point.parent!!.id
            handleConicalToolClick(state)
        }}
    if (state.drawobjects == Mongeobjects.PYRAMID){
        if(point.parent!=null){
            state.pendingPyramidApexId = point.parent!!.id
            handlePyramidToolClick(state, state.projectionMode == ProjectionMode.AXO)
        }}
    state.deferSelectionUntil = System.currentTimeMillis() + 100
    handlePlaneFromTracePickPointClick(state)
}

//označení bodu nárys
fun handleSelectionBokorysPoint(
    state: MongeState
) {  if (System.currentTimeMillis() < state.deferSelectionUntil) {
    println("⛔️ Výběr zablokován časovým štítem")
    return
}
    val snapped = state.snappedPointBokorys?: return

    val snappedX = snapped.y
    val snappedZ = snapped.z

    val existing = state.pointsBokorys.find {
        abs(it.y - snappedX) < 0.01f && abs(it.z - snappedZ) < 0.01f
    }
    val allowed = state.drawobjects == Mongeobjects.NONE|| state.drawobjects== Mongeobjects.PLANE_LIFT || (state.drawobjects == Mongeobjects.CONE || state.drawobjects == Mongeobjects.SPHERE)
    if (!allowed) return
    if (existing != null) {


        if (state.isShiftPressed) {
            toggleSelectionBokorys(existing,state)
            println("Vybrán bod v BOKORYSU (se shiftem): ${existing.name} ${existing.localSuperscript}(x=${existing.y}, z=${existing.z})")
            state.deferSelectionUntil = System.currentTimeMillis() + 100
        } else {
            if ((state.drawobjects == Mongeobjects.CONE || state.drawobjects == Mongeobjects.SPHERE) && existing.parent==null) return
            if (System.currentTimeMillis() >= state.deferSelectionUntil) {
                state.selectedPointsNarys.clear()
                state.selectedPointsPudorys.clear()
                state.selectedPointsBokorys.clear()
            }
            state.selectedPointsBokorys.add(existing)
            decideObjectForLift(state)
            println("Vybrán bod v NÁRYSU (bez shiftu): ${existing.name}${existing.localSuperscript} (x=${existing.y}, z=${existing.z})")
            state.deferSelectionUntil = System.currentTimeMillis() + 100
                   }
    }
}

fun toggleSelectionAxo(
    point: Point3DAxo,
    state: MongeState
) {
    val tolerance = 0.01f
    val existing = state.selectedPointsAxo.find {
        abs(it.x - point.x) < tolerance &&
                abs(it.y - point.y) < tolerance
    }
    val pending = state.completionPending


    if (existing != null) {

        state.selectedPointsAxo.remove(existing)
        println("🟡 Odznačen bod v AXO: ${point.name}, (x=${point.x}, y=${point.y})")
        state.deferSelectionUntil = System.currentTimeMillis() + 100
    } else {

        state.selectedPointsAxo.add(point)
        decideObjectForLift(state)
        advancePendingLineOrPointConstruction(state)
        println("🟢 Označen bod v AXO: ${point.name}, (x=${point.y}, y=${point.y})")
        state.deferSelectionUntil = System.currentTimeMillis() + 100
    }
    if (state.drawobjects == Mongeobjects.CONE){
        if(point.parent!=null){
            state.pendingApex3DId = point.parent!!.id
            handleConicalToolClick(state)
        }}
    if (state.drawobjects == Mongeobjects.PYRAMID){
        if(point.parent!=null){
            state.pendingPyramidApexId = point.parent!!.id
            handlePyramidToolClick(state, state.projectionMode == ProjectionMode.AXO)
        }}
    state.deferSelectionUntil = System.currentTimeMillis() + 100
    handlePlaneFromTracePickPointClick(state)
}
fun handleSelectionAxoPoint(state: MongeState, isShiftPressed: Boolean = false) {
    if (System.currentTimeMillis() < state.deferSelectionUntil) {
        println("⛔️ Výběr zablokován časovým štítem")
        return
    }
    val snapped = state.snappedAxoPoint?: return

    val snappedX = snapped.x
    val snappedY = snapped.y

    val existing = state.pointsAxo.find {
        abs(it.x - snappedX) < 0.01f && abs(it.y - snappedY) < 0.01f
    }
    val allowed = state.drawobjects == Mongeobjects.NONE|| state.drawobjects== Mongeobjects.PLANE_LIFT || (state.drawobjects == Mongeobjects.CONE || state.drawobjects == Mongeobjects.SPHERE)
    if (!allowed) return
    if (existing != null) {


        if (isShiftPressed) {
            toggleSelectionAxo(existing,state)
            println("Vybrán bod v Axo (se shiftem): ${existing.name} ${existing.localSuperscript}(x=${existing.x}, y=${existing.y})")
            state.deferSelectionUntil = System.currentTimeMillis() + 100
        } else {
            if ((state.drawobjects == Mongeobjects.CONE || state.drawobjects == Mongeobjects.SPHERE) && existing.parent==null) return
            if (System.currentTimeMillis() >= state.deferSelectionUntil) {
                state.selectedPointsNarys.clear()
                state.selectedPointsPudorys.clear()
                state.selectedPointsBokorys.clear()
                state.selectedPointsAxo.clear()
            }
            state.selectedPointsAxo.add(existing)
            decideObjectForLift(state)
            println("Vybrán bod v Axo (bez shiftu): ${existing.name}${existing.localSuperscript} (x=${existing.x}, y=${existing.y})")
            state.deferSelectionUntil = System.currentTimeMillis() + 100
        }
    }
}