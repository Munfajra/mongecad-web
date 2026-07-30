package monge.input.ruledsurface
import utils.replaceAll

import androidx.compose.ui.graphics.Color
import model.classes.RuledSurface3D
import model.classes.RuledSurfaceDirectrixKind
import model.Mongeobjects
import state.MongeState

/** Dočasné zelené zvýraznění řídicího objektu během tříkrokové konstrukce. */
fun isPendingRuledSurfaceDirectrix(state: MongeState, objectId: String?): Boolean =
    objectId != null &&
        state.drawobjects == Mongeobjects.RULED_SURFACE &&
        state.pendingRuledSurfaceDirectrices.any { it.objectId == objectId }

/** Přebarví řídicí křivky; řídicí rovinu a definiční kouli konoidu nechává beze změny. */
fun recolorRuledSurfaceDirectrices(state: MongeState, surface: RuledSurface3D, color: Color) {
    val refs = listOfNotNull(
        surface.firstBoundaryDirectrix,
        surface.secondBoundaryDirectrix,
        surface.thirdDirectrix,
    ).distinctBy { it.objectId }

    for (ref in refs) when (ref.kind) {
        RuledSurfaceDirectrixKind.LINE -> {
            val index = state.lines3D.indexOfFirst { it.id == ref.objectId }
            if (index < 0) continue
            val updated = state.lines3D[index].copy(color = color)
            state.lines3D[index] = updated
            state.lines3DPudorys.replaceAll { old ->
                if ((old.parent?.id ?: old.parentId) == updated.id) old.copy(parent = updated, parentId = updated.id).also { it.showInAxo = old.showInAxo } else old
            }
            state.lines3DNarys.replaceAll { old ->
                if ((old.parent?.id ?: old.parentId) == updated.id) old.copy(parent = updated, parentId = updated.id).also { it.showInAxo = old.showInAxo } else old
            }
            state.lines3DBokorys.replaceAll { old ->
                if ((old.parent?.id ?: old.parentId) == updated.id) old.copy(parent = updated, parentId = updated.id).also { it.showInAxo = old.showInAxo } else old
            }
            state.lines3DAxo.replaceAll { old ->
                if ((old.parent?.id ?: old.parentId) == updated.id) old.copy(parent = updated, parentId = updated.id).also { it.showInAxo = old.showInAxo } else old
            }
            state.selectedLines3D.replaceAll { if (it.id == updated.id) updated else it }
        }

        RuledSurfaceDirectrixKind.CURVE -> {
            val index = state.curves3D.indexOfFirst { it.id == ref.objectId }
            if (index < 0) continue
            val updated = state.curves3D[index].copy(color = color)
            state.curves3D[index] = updated
            state.curvesPudorys.replaceAll { old ->
                if (old.parentId == updated.id || old.parent?.id == updated.id) old.copy(parent = updated, parentId = updated.id).also { it.showInAxo = old.showInAxo } else old
            }
            state.curvesNarys.replaceAll { old ->
                if (old.parentId == updated.id || old.parent?.id == updated.id) old.copy(parent = updated, parentId = updated.id).also { it.showInAxo = old.showInAxo } else old
            }
            state.curvesBokorys.replaceAll { old ->
                if (old.parentId == updated.id || old.parent?.id == updated.id) old.copy(parent = updated, parentId = updated.id).also { it.showInAxo = old.showInAxo } else old
            }
            state.curvesAxo.replaceAll { old ->
                if (old.parentId == updated.id || old.parent?.id == updated.id) old.copy(parent = updated, parentId = updated.id).also { it.showInAxo = old.showInAxo } else old
            }
        }

        RuledSurfaceDirectrixKind.CONIC -> {
            val index = state.conics3D.indexOfFirst { it.id == ref.objectId }
            if (index < 0) continue
            val old3D = state.conics3D[index]
            val updated = old3D.copy(color = color).apply { directrixOfSurfaceIds = old3D.directrixOfSurfaceIds }
            state.conics3D[index] = updated
            state.conicsPudorys.replaceAll { old ->
                if ((old.parent?.id ?: old.parentId) == updated.id) old.copy(parent = updated, parentId = updated.id).also { it.showInAxo = old.showInAxo } else old
            }
            state.conicsNarys.replaceAll { old ->
                if ((old.parent?.id ?: old.parentId) == updated.id) old.copy(parent = updated, parentId = updated.id).also { it.showInAxo = old.showInAxo } else old
            }
            state.conicsBokorys.replaceAll { old ->
                if ((old.parent?.id ?: old.parentId) == updated.id) old.copy(parent = updated, parentId = updated.id).also { it.showInAxo = old.showInAxo } else old
            }
            state.conicsAxo.replaceAll { old ->
                if ((old.parent?.id ?: old.parentId) == updated.id) old.copy(parent = updated, parentId = updated.id).also { it.showInAxo = old.showInAxo } else old
            }
        }

        // Koule je definiční plocha kulového konoidu, nikoli jeho obarvovaná
        // krajní křivka. Její vlastní styl proto zůstává nezávislý.
        RuledSurfaceDirectrixKind.SPHERE -> Unit
    }
}
