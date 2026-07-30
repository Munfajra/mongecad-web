package ui.mongeui.toolbar.rightDescriptionBar.ObjectList

import draw.mongescreen.labels.clearSelection
import model.classes.RuledSurface3D
import model.classes.RuledSurfaceDirectrixKind
import model.classes.RuledSurfaceDirectrixRef
import monge.input.selection.toggleSelectionConic3D
import monge.input.selection.toggleSelectionCurveAxo
import monge.input.selection.toggleSelectionCurveBokorys
import monge.input.selection.toggleSelectionCurve3D
import monge.input.selection.toggleSelectionCurveNarys
import monge.input.selection.toggleSelectionCurvePudorys
import monge.input.selection.toggleSelectionLine3D
import monge.input.selection.toggleSelectionPlane
import monge.input.selection.toggleSelectionSphere3D
import monge.input.ruledsurface.handleRuledSurfaceSelection
import state.MongeState

fun buildRuledSurfaceChildren(
    state: MongeState,
    surface: RuledSurface3D,
    clearAllOnClick: Boolean,
): List<UiTreeItem> = buildList {
    fun addDirectrix(ref: RuledSurfaceDirectrixRef, label: String) {
        when (ref.kind) {
            RuledSurfaceDirectrixKind.LINE -> state.lines3D.firstOrNull { it.id == ref.objectId }?.let { line ->
                add(UiTreeItem(
                    key = "ruled:${surface.id}/line:${line.id}", sortIndex = sortKeyDesc(line.creationIndex),
                    name = "$label: Přímka ${line.name}", color = line.color, is3D = true,
                    icon = ObjectListIcon.Line,
                    isSelected = { state.selectedRuledSurfaceId == surface.id || state.selectedLines3D.any { it.id == line.id } },
                    onClick = { if (clearAllOnClick) clearSelection(state); toggleSelectionLine3D(line, state) },
                ))
            }
            RuledSurfaceDirectrixKind.CURVE -> state.curves3D.firstOrNull { it.id == ref.objectId }?.let { curve ->
                add(UiTreeItem(
                    key = "ruled:${surface.id}/curve:${curve.id}", sortIndex = sortKeyDesc(curve.creationIndex),
                    name = "$label: Křivka ${curve.name}", color = curve.color, is3D = true,
                    icon = ObjectListIcon.Curve,
                    isSelected = { state.selectedRuledSurfaceId == surface.id || state.selectedCurve3DId == curve.id },
                    onClick = { if (clearAllOnClick) clearSelection(state); toggleSelectionCurve3D(state, curve.id) },
                ))
            }
            RuledSurfaceDirectrixKind.CONIC -> state.conics3D.firstOrNull { it.id == ref.objectId }?.let { conic ->
                add(UiTreeItem(
                    key = "ruled:${surface.id}/conic:${conic.id}", sortIndex = sortKeyDesc(conic.creationIndex),
                    name = "$label: Kuželosečka ${conic.name}", color = conic.color, is3D = true,
                    icon = ObjectListIcon.Ellipse,
                    isSelected = {
                        state.selectedRuledSurfaceId == surface.id ||
                            state.selectedConicsPudorys.any { (it.parent?.id ?: it.parentId) == conic.id } ||
                            state.selectedConicsNarys.any { (it.parent?.id ?: it.parentId) == conic.id }
                    },
                    onClick = {
                        if (clearAllOnClick) clearSelection(state)
                        toggleSelectionConic3D(conic, state)
                        handleRuledSurfaceSelection(state)
                    },
                ))
            }
            RuledSurfaceDirectrixKind.SPHERE -> state.spheres3D.firstOrNull { it.id == ref.objectId }?.let { sphere ->
                add(UiTreeItem(
                    key = "ruled:${surface.id}/sphere:${sphere.id}", sortIndex = sortKeyDesc(sphere.creationIndex),
                    name = "$label: Kulová plocha ${sphere.name}", color = sphere.color, is3D = true,
                    icon = ObjectListIcon.Ellipse,
                    isSelected = {
                        state.selectedRuledSurfaceId == surface.id || state.selectedSpheres3D.any { it.id == sphere.id }
                    },
                    onClick = {
                        if (clearAllOnClick) clearSelection(state)
                        toggleSelectionSphere3D(sphere, state)
                        handleRuledSurfaceSelection(state)
                    },
                ))
            }
        }
    }

    addDirectrix(surface.firstBoundaryDirectrix, "1. řídicí")
    if (surface.thirdDirectrix != null) {
        addDirectrix(surface.thirdDirectrix, "2. řídicí")
        addDirectrix(surface.secondBoundaryDirectrix, "3. řídicí")
    } else {
        addDirectrix(surface.secondBoundaryDirectrix, "2. řídicí")
    }
    surface.directorPlaneId?.let { id ->
        (state.planes3D.firstOrNull { it.id == id } ?: model.classes.referencePlaneById(id))?.let { plane ->
            add(UiTreeItem(
                key = "ruled:${surface.id}/plane:${plane.id}", sortIndex = sortKeyDesc(plane.creationIndex),
                name = "Řídicí rovina ${plane.name}", color = plane.color, is3D = true,
                icon = ObjectListIcon.Plane,
                isSelected = { state.selectedRuledSurfaceId == surface.id || state.selectedPlanes.any { it.id == plane.id } },
                onClick = { if (clearAllOnClick) clearSelection(state); toggleSelectionPlane(plane, state) },
            ))
        }
    }

    surface.generatorLineIds.forEachIndexed { index, lineId ->
        val line = state.lines3D.firstOrNull { it.id == lineId } ?: return@forEachIndexed
        add(UiTreeItem(
            key = "ruled:${surface.id}/generator:$lineId", sortIndex = sortKeyDesc(line.creationIndex),
            name = "Tvořice ${index + 1}", color = line.color, is3D = true,
            icon = ObjectListIcon.Line,
            isSelected = { state.selectedRuledSurfaceId == surface.id || state.selectedLines3D.any { it.id == lineId } },
            onClick = { if (clearAllOnClick) clearSelection(state); toggleSelectionLine3D(line, state) },
        ))
    }

    surface.outlineCurveIdPudorys?.let { id -> state.curvesPudorys.firstOrNull { it.id == id } }?.let { curve ->
        add(UiTreeItem(
            key = "ruled:${surface.id}/outline:${curve.id}", sortIndex = sortKeyDesc(surface.creationIndex),
            name = "Obrys₁", color = curve.color, is3D = false, icon = ObjectListIcon.Curve,
            isSelected = { state.selectedCurvePudorysId == curve.id },
            onClick = { if (clearAllOnClick) clearSelection(state); toggleSelectionCurvePudorys(state, curve.id, selectParentObject = false) },
        ))
    }
    surface.outlineCurveIdNarys?.let { id -> state.curvesNarys.firstOrNull { it.id == id } }?.let { curve ->
        add(UiTreeItem(
            key = "ruled:${surface.id}/outline:${curve.id}", sortIndex = sortKeyDesc(surface.creationIndex) - 1,
            name = "Obrys₂", color = curve.color, is3D = false, icon = ObjectListIcon.Curve,
            isSelected = { state.selectedCurveNarysId == curve.id },
            onClick = { if (clearAllOnClick) clearSelection(state); toggleSelectionCurveNarys(state, curve.id, selectParentObject = false) },
        ))
    }
    surface.outlineCurveIdBokorys?.let { id -> state.curvesBokorys.firstOrNull { it.id == id } }?.let { curve ->
        add(UiTreeItem(
            key = "ruled:${surface.id}/outline:${curve.id}", sortIndex = sortKeyDesc(surface.creationIndex) - 2,
            name = "Obrys₃", color = curve.color, is3D = false, icon = ObjectListIcon.Curve,
            isSelected = { state.selectedCurveBokorysId == curve.id },
            onClick = { if (clearAllOnClick) clearSelection(state); toggleSelectionCurveBokorys(state, curve.id, selectParentObject = false) },
        ))
    }
    surface.outlineCurveIdAxo?.let { id -> state.curvesAxo.firstOrNull { it.id == id } }?.let { curve ->
        add(UiTreeItem(
            key = "ruled:${surface.id}/outline:${curve.id}", sortIndex = sortKeyDesc(surface.creationIndex) - 3,
            name = "Obrysₐ", color = curve.color, is3D = false, icon = ObjectListIcon.Curve,
            isSelected = { state.selectedCurveAxoId == curve.id },
            onClick = { if (clearAllOnClick) clearSelection(state); toggleSelectionCurveAxo(state, curve.id, selectParentObject = false) },
        ))
    }
}
