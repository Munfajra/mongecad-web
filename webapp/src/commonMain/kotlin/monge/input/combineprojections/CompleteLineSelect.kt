package monge.input.combineprojections

import serialization.commitSnapshot
import model.CompletionRequest
import model.DrawingModeMonge
import model.Mongeobjects
import model.Point3D
import model.classes.Line3DProjectionNarys
import model.classes.Line3DProjectionPudorys
import model.classes.Point3DNarys
import model.classes.Point3DPudorys
import state.MongeState
import utils.allocIndex
import utils.combineProjectionsToLine3D
import utils.update2DSnapshots
import kotlin.math.abs

private const val LINE_COMPLETION_EPS = 1e-6f

fun startLine3DCompletion(state: MongeState) {
    val pudorys = state.selectedLinesPudorys.firstOrNull() as? Line3DProjectionPudorys
    val narys = state.selectedLinesNarys.firstOrNull() as? Line3DProjectionNarys

    when {
        pudorys != null -> {

            state.completionPending = CompletionRequest.Line3DFromProjections(
                existing = pudorys,
                expect = DrawingModeMonge.NARYS
            )
            println("ℹ️ Označ nárys pro doplnění přímky.")
            state.mongeMode= DrawingModeMonge.NARYS
            state.drawobjects = Mongeobjects.NONE
        }

        narys != null -> {
            state.completionPending = CompletionRequest.Line3DFromProjections(
                existing = narys,
                expect = DrawingModeMonge.PUDORYS
            )
            println("ℹ️ Označ půdorys pro doplnění přímky.")
            state.mongeMode= DrawingModeMonge.PUDORYS
            state.drawobjects = Mongeobjects.NONE
        }

        else -> {
            println("⚠️ Označ nejprve jednu projekci přímky.")
        }
    }
}
fun handleCombineProjections(state: MongeState, clicked: Any) {
    val pending = state.completionPending
    if (pending is CompletionRequest.Line3DFromProjections) {

    when (pending.expect) {
        DrawingModeMonge.NARYS -> {
            val pudorys = pending.existing as Line3DProjectionPudorys
            val narys = clicked as? Line3DProjectionNarys ?: return
            if (!validateLineCompletionProjections(state, pudorys, narys)) return
            val nameBase = pudorys.localName?.removeSuffix("₁") ?: "a"
            val sup = pudorys.localSuperscript ?: narys.localSuperscript ?: ""
            val line3D = combineProjectionsToLine3D(pudorys, narys, nameBase,sup,state)

            // horní index dál přebírají projekce z parenta (localSuperscript = null)
            val updatedP = pudorys.copy(parent = line3D, parentId = line3D.id, localName = "$nameBase₁", localSuperscript = null)
            val updatedN = narys.copy(parent = line3D, parentId = line3D.id, localName = "$nameBase₂", localSuperscript = null)

            state.lines3DPudorys.replace(pudorys, updatedP)
            state.lines3DNarys.replace(narys, updatedN)
            state.lines3D.add(line3D)

            state.selectedLinesPudorys.clear()
            state.selectedLinesNarys.clear()
            state.selectedLinesPudorys += updatedP
            state.selectedLinesNarys += updatedN

            state.completionPending = null
            update2DSnapshots(state)
            println("✅ Přímka doplněna.")
            commitSnapshot(state)
        }

        DrawingModeMonge.PUDORYS -> {
            val narys = pending.existing as Line3DProjectionNarys
            val pudorys = clicked as? Line3DProjectionPudorys ?: return
            if (!validateLineCompletionProjections(state, pudorys, narys)) return
            val nameBase = narys.localName?.removeSuffix("₂") ?: "a"
            val sup = narys.localSuperscript ?: pudorys.localSuperscript ?: ""
            val line3D = combineProjectionsToLine3D(pudorys, narys, nameBase,sup,state)

            // horní index dál přebírají projekce z parenta (localSuperscript = null)
            val updatedP = pudorys.copy(parent = line3D, parentId = line3D.id, localName = "$nameBase₁", localSuperscript = null)
            val updatedN = narys.copy(parent = line3D, parentId = line3D.id, localName = "$nameBase₂", localSuperscript = null)

            state.lines3DPudorys.replace(pudorys, updatedP)
            state.lines3DNarys.replace(narys, updatedN)
            state.lines3D.add(line3D)

            state.selectedLinesPudorys.clear()
            state.selectedLinesNarys.clear()
            state.selectedLinesPudorys += updatedP
            state.selectedLinesNarys += updatedN

            state.completionPending = null
            update2DSnapshots(state)
            println("✅ Přímka doplněna.")
            commitSnapshot(state)
        }
    }
    }
    if (pending is CompletionRequest.Point3DFromProjections) {
        when (pending.expect) {
            DrawingModeMonge.NARYS -> {
                val pudorys = pending.existing as? Point3DPudorys ?: return
                val narys = clicked as? Point3DNarys ?: return
                if (abs(pudorys.x - narys.x) > 0.01f) {
                    println("❌ Body nemají stejnou x souřadnici. Nelze je spojit do 3D bodu.")
                    state.completionPending = null
                    return

                }
                val name = pudorys.name?.ifBlank { narys.name }?.ifBlank { "?" }

                val point3D = Point3D(
                    x = pudorys.x,
                    y = pudorys.y,
                    z = narys.z,
                    name = name!!,
                    creationIndex = allocIndex(state)
                )

                val updatedP = pudorys.copy(parent = point3D, name = "$name₁")
                val updatedN = narys.copy(parent = point3D, name = "$name₂")

                state.pointsPudorys.replace(pudorys, updatedP)
                state.pointsNarys.replace(narys, updatedN)
                state.sharedPoints3D.add(point3D)

                state.selectedPointsPudorys.clear()
                state.selectedPointsNarys.clear()
                state.selectedPointsPudorys += updatedP
                state.selectedPointsNarys += updatedN

                state.completionPending = null
                update2DSnapshots(state)
                println("✅ Bod doplněn.")
                commitSnapshot(state)
            }

            DrawingModeMonge.PUDORYS -> {
                val narys = pending.existing as? Point3DNarys ?: return
                val pudorys = clicked as? Point3DPudorys ?: return
                if (abs(pudorys.x - narys.x) > 0.01f) {
                    println("❌ Body nemají stejnou x souřadnici. Nelze je spojit do 3D bodu.")
                    state.completionPending = null
                    return
                }
                val name = narys.name?.ifBlank { pudorys.name }?.ifBlank { "?" }

                val point3D = Point3D(
                    x = pudorys.x,
                    y = pudorys.y,
                    z = narys.z,
                    name = name!!,
                    creationIndex = allocIndex(state)
                )

                val updatedP = pudorys.copy(parent = point3D, name = "$name₁")
                val updatedN = narys.copy(parent = point3D, name = "$name₂")

                state.pointsPudorys.replace(pudorys, updatedP)
                state.pointsNarys.replace(narys, updatedN)
                state.sharedPoints3D.add(point3D)

                state.selectedPointsPudorys.clear()
                state.selectedPointsNarys.clear()
                state.selectedPointsPudorys += updatedP
                state.selectedPointsNarys += updatedN

                state.completionPending = null
                update2DSnapshots(state)
                println("✅ Bod doplněn.")
                commitSnapshot(state)
            }
        }
    }

}

private fun validateLineCompletionProjections(
    state: MongeState,
    pudorys: Line3DProjectionPudorys,
    narys: Line3DProjectionNarys
): Boolean {
    val pudorysPerpendicularToX = abs(pudorys.direction.x) < LINE_COMPLETION_EPS
    val narysPerpendicularToX = abs(narys.direction.x) < LINE_COMPLETION_EPS
    if (!pudorysPerpendicularToX && !narysPerpendicularToX) return true

    val message =
        if (pudorysPerpendicularToX && narysPerpendicularToX &&
            abs(pudorys.point.x - narys.point.x) < LINE_COMPLETION_EPS
        ) {
            "Tyto průměty neurčují jednoznačně prostorovou přímku. Oba leží nad stejnou souřadnicí x, a proto určují rovinu, ve které může ležet nekonečně mnoho přímek."
        } else {
            "Přímka má průmět kolmý na osu x₁₂. Z těchto dvou čárových průmětů nelze jednoznačně sestavit prostorovou přímku."
        }

    showLineCompletionError(state, message)
    return false
}

fun <T> MutableList<T>.replace(old: T, new: T) {
    val i = indexOf(old)
    if (i != -1) this[i] = new
}
