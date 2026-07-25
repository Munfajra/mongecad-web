package monge.input.combineprojections

import utils.withoutProjectionSuffixes
import utils.withSuffixOnce
import dialogs.nameInput.withSuffixOnce
import dialogs.nameInput.withoutProjectionSuffixes
import androidx.compose.ui.graphics.Color
import serialization.commitSnapshot
import model.CompletionRequest
import model.DrawingModeMonge
import model.Mongeobjects
import model.Point3D
import model.ProjectionType
import model.classes.Point3DNarys
import model.classes.Point3DPudorys
import state.MongeState
import ui.mongeui.toolbar.setProjectionPhase
import utils.allocIndex
import utils.update2DSnapshots

fun CompletePointAdd(state: MongeState) {

    state.completionPending = null
    state.reusingExistingProjection = false
    state.inputName = ""
    state.isNameConfirmed = false
    state.pendingX = null
    state.pendingY = null
    state.pendingZ = null
    state.projectionPhase = when (state.mongeMode) {
        DrawingModeMonge.PUDORYS -> "pudorys_start"
        DrawingModeMonge.NARYS -> "narys_start"
    }
    val pudorys = state.selectedPointsPudorys.firstOrNull()
    val narys = state.selectedPointsNarys.firstOrNull()

    when {
        pudorys != null -> {
            state.pendingX = pudorys.x
            state.pendingY = pudorys.y
            state.isNameConfirmed = false
            state.drawobjects = Mongeobjects.POINTS
            state.projekcnityp = ProjectionType.ASSOCIATED
            setProjectionPhase("pudorys_to_narys_point", state)
            state.pendingMongeModeChange = DrawingModeMonge.NARYS
            state.completionPending = CompletionRequest.Point3DFromProjections(
                existing = pudorys,
                expect = DrawingModeMonge.NARYS
            )
            state.reusingExistingProjection = true // ✅ důležité
        }
        narys != null -> {
            state.pendingX = narys.x
            state.pendingZ = narys.z
            state.isNameConfirmed = false
            state.drawobjects = Mongeobjects.POINTS
            state.projekcnityp = ProjectionType.ASSOCIATED
            setProjectionPhase("narys_to_pudorys_point", state)
            state.pendingMongeModeChange = DrawingModeMonge.PUDORYS
            state.completionPending = CompletionRequest.Point3DFromProjections(
                existing = narys,
                expect = DrawingModeMonge.PUDORYS
            )
            state.reusingExistingProjection = true // ✅ důležité
        }
    }
}
fun NarysFinalizePointAuto(state: MongeState) {
    if (state.projectionPhase == "narys_finalize_auto") {
        if (state.pendingX != null && state.pendingY != null && state.pendingZ != null) {
            val x = state.pendingX!!
            val y = state.pendingY!!
            val z = state.pendingZ!!

            val selected = (state.completionPending as? CompletionRequest.Point3DFromProjections)
                ?.existing as? Point3DPudorys
            val existing = selected
                ?.let { selectedPoint -> state.pointsPudorys.firstOrNull { it.id == selectedPoint.id } }
                ?: state.pointsPudorys.find { it.x == x && it.y == y }
            val color = existing?.color ?: Color.Black
            val nameBase = existing?.name?.withoutProjectionSuffixes()?.ifBlank { "?" } ?: "?"

            val point3D = Point3D(x, y, z, nameBase, creationIndex = allocIndex(state), color = color)

            if (existing != null) {
                val index = state.pointsPudorys.indexOf(existing)
                state.pointsPudorys[index] = existing.copy(parent = point3D, name = nameBase.withSuffixOnce("₁"))
            } else {
                println("⚠️ Půdorysný bod nebyl nalezen při automatickém sdružení.")
            }

            state.pointsNarys.add(
                Point3DNarys(
                    x,
                    z,
                    nameBase.withSuffixOnce("₂"),
                    parent = point3D,
                    creationIndex = allocIndex(state)
                )
            )
            state.sharedPoints3D.add(point3D)
            commitSnapshot(state)
            update2DSnapshots(state)
            setProjectionPhase("pudorys_start", state)
            state.mongeMode = DrawingModeMonge.PUDORYS
            state.pendingX = null
            state.pendingY = null
            state.pendingZ = null
            state.inputName = ""
            state.isNameConfirmed = false
            state.reusingExistingProjection = false
            state.completionPending = null

            println("✅ 3D bod doplněn automaticky: $nameBase")

        } else {
            println("❌ Chyba: neúplná data ve fázi narys_finalize_auto")
        }
    }
}
fun PudorysFinalizePointAuto(state: MongeState) {
    if (state.projectionPhase == "pudorys_finalize_auto") {
        if (state.pendingX != null && state.pendingY != null && state.pendingZ != null) {
            val x = state.pendingX!!
            val y = state.pendingY!!
            val z = state.pendingZ!!

            val selected = (state.completionPending as? CompletionRequest.Point3DFromProjections)
                ?.existing as? Point3DNarys
            val existing = selected
                ?.let { selectedPoint -> state.pointsNarys.firstOrNull { it.id == selectedPoint.id } }
                ?: state.pointsNarys.find { it.x == x && it.z == z }
            val color = existing?.color ?: Color.Black
            val nameBase = existing?.name?.withoutProjectionSuffixes()?.ifBlank { "?" } ?: "?"

            val point3D = Point3D(x, y, z, nameBase, creationIndex = allocIndex(state), color = color)

            if (existing != null) {
                val index = state.pointsNarys.indexOf(existing)
                state.pointsNarys[index] = existing.copy(parent = point3D, name = nameBase.withSuffixOnce("₂"))
            } else {
                println("⚠️ Nárysný bod nebyl nalezen při automatickém sdružení.")
            }

            state.pointsPudorys.add(
                Point3DPudorys(
                    x,
                    y,
                    nameBase.withSuffixOnce("₁"),
                    parent = point3D,
                    creationIndex = allocIndex(state)
                )
            )
            state.sharedPoints3D.add(point3D)

            update2DSnapshots(state)
            commitSnapshot(state)
            setProjectionPhase("narys_start", state)
            state.mongeMode = DrawingModeMonge.NARYS
            state.pendingX = null
            state.pendingY = null
            state.pendingZ = null
            state.inputName = ""
            state.isNameConfirmed = false
            state.reusingExistingProjection = false
            state.completionPending = null

            println("✅ 3D bod doplněn automaticky: $nameBase")

        } else {
            println("❌ Chyba: neúplná data ve fázi narys_finalize_auto")
        }
    }
}
