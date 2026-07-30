package monge.input.ruledsurface

import draw.mongescreen.labels.clearSelection
import model.Mongeobjects
import model.classes.Line3D
import model.classes.Line3DProjectionBokorys
import model.classes.Line3DProjectionNarys
import model.classes.Line3DProjectionPudorys
import model.classes.Plane3D
import model.classes.RuledSurface3D
import model.classes.RuledSurfaceDirectrixKind
import model.classes.RuledSurfaceDirectrixRef
import serialization.commitSnapshot
import state.MongeState
import ui.mongeui.toolbar.repeatCons
import ui.mongeui.toolbar.setProjectionPhase
import utils.allocIndex

private const val RULED_SURFACE_PICK_PHASE = "ruled_surface_directrix_select"

fun startRuledSurfaceTool(state: MongeState) {
    clearRuledSurfaceConstruction(state)
    clearSelection(state)
    state.drawobjects = Mongeobjects.RULED_SURFACE
    setProjectionPhase(RULED_SURFACE_PICK_PHASE, state)
}

fun clearRuledSurfaceConstruction(state: MongeState) {
    state.pendingRuledSurfaceDirectrices.clear()
    state.pendingRuledSurfaceDirectorPlaneId = null
    state.pendingRuledSurfaceDirectorPlaneEquation = null
}

/**
 * Spotřebuje právě označený 3D objekt. Volá se po běžném výběru objektu z
 * plátna i ze seznamu objektů.
 */
fun handleRuledSurfaceSelection(state: MongeState) {
    if (state.drawobjects != Mongeobjects.RULED_SURFACE) return

    val selectedPlane = state.selectedPlanes.lastOrNull()
    if (selectedPlane != null) {
        addDirectorPlane(state, selectedPlane)
        return
    }

    val directrix = gatherSelectedDirectrices(state).firstOrNull {
        it !in state.pendingRuledSurfaceDirectrices
    } ?: return

    state.pendingRuledSurfaceDirectrices.add(directrix)
    finishOrUpdate(state)
}

fun ruledSurfaceSelectionInfo(state: MongeState): String {
    val picked = state.pendingRuledSurfaceDirectrices.size +
            if (state.pendingRuledSurfaceDirectorPlaneId == null) 0 else 1
    return "Přímková plocha: vyberte 3D přímku, křivku, kuželosečku nebo kulovou plochu" +
            " (a jednu řídicí rovinu pro konoid). Vybráno: $picked/3."
}

private fun addDirectorPlane(state: MongeState, plane: Plane3D) {
    val equation = plane.equation
    if (equation == null) {
        state.consInfo.value = "Vybraná řídicí rovina nemá rovnici."
        return
    }
    val planeId = plane.id
    val current = state.pendingRuledSurfaceDirectorPlaneId
    when {
        current == planeId -> return
        current != null -> {
            state.consInfo.value = "Konoid může mít pouze jednu řídicí rovinu."
            return
        }
        else -> {
            state.pendingRuledSurfaceDirectorPlaneId = planeId
            // Základní průmětny nejsou součástí state.planes3D, proto jejich
            // normálu musíme převzít přímo z právě označeného Plane3D.
            state.pendingRuledSurfaceDirectorPlaneEquation = equation
            finishOrUpdate(state)
        }
    }
}

private fun finishOrUpdate(state: MongeState) {
    val directrices = state.pendingRuledSurfaceDirectrices
    val planeId = state.pendingRuledSurfaceDirectorPlaneId
    val pickedCount = directrices.size + if (planeId == null) 0 else 1

    if (pickedCount < 3) {
        state.consInfo.value = ruledSurfaceSelectionInfo(state)
        return
    }

    val validGeneral = planeId == null && directrices.size == 3 &&
        directrices.none { it.kind == RuledSurfaceDirectrixKind.SPHERE }
    val validConoid = planeId != null && directrices.size == 2 &&
        (directrices.none { it.kind == RuledSurfaceDirectrixKind.SPHERE } ||
            directrices.map { it.kind }.toSet() == setOf(
                RuledSurfaceDirectrixKind.LINE,
                RuledSurfaceDirectrixKind.SPHERE,
            ))
    if (!validGeneral && !validConoid) {
        state.consInfo.value = "Neplatná kombinace řídicích objektů přímkové plochy."
        return
    }

    val settings = state.currentLineStyleSettings
    val surface = RuledSurface3D(
        firstBoundaryDirectrix = directrices[0],
        secondBoundaryDirectrix = if (validGeneral) directrices[2] else directrices[1],
        thirdDirectrix = if (validGeneral) directrices[1] else null,
        directorPlaneId = planeId,
        directorPlaneEquation = state.pendingRuledSurfaceDirectorPlaneEquation,
        color = settings.color,
        wireWidth = settings.strokeWidth,
        creationIndex = allocIndex(state),
    )
    captureRuledSurfaceGeometry(state, surface)
    ruledSurfaceDefinitionError(state, surface)?.let { message ->
        clearRuledSurfaceConstruction(state)
        clearSelection(state)
        state.constructionErrorMessage = message
        state.showConstructionErrorDialog = true
        state.consInfo.value = ruledSurfaceSelectionInfo(state)
        state.triggerRedraw++
        return
    }
    state.ruledSurfaces.add(surface)
    createRuledSurfaceOutlines(state, surface)
    syncRuledSurfaceGeneratorLines(state, surface)
    clearRuledSurfaceConstruction(state)
    commitSnapshot(state)

    state.consInfo.value = when {
        directrices.any { it.kind == RuledSurfaceDirectrixKind.SPHERE } -> "Kulový konoid byl vytvořen."
        validConoid -> "Konoid byl vytvořen."
        else -> "Přímková plocha byla vytvořena."
    }
    repeatCons(state)
}

private fun gatherSelectedDirectrices(state: MongeState): List<RuledSurfaceDirectrixRef> {
    val result = LinkedHashSet<RuledSurfaceDirectrixRef>()

    fun addLine(line: Line3D?) {
        if (line != null) {
            result += RuledSurfaceDirectrixRef(line.id, RuledSurfaceDirectrixKind.LINE)
        }
    }

    fun lineById(id: String?): Line3D? =
        id?.let { parentId -> state.lines3D.firstOrNull { it.id == parentId } }

    state.selectedLines3D.forEach(::addLine)
    state.selectedLinesPudorys.forEach { projection ->
        addLine((projection as? Line3DProjectionPudorys)?.parent ?: lineById(projection.parentId))
    }
    state.selectedLinesNarys.forEach { projection ->
        addLine((projection as? Line3DProjectionNarys)?.parent ?: lineById(projection.parentId))
    }
    state.selectedLinesBokorys.forEach { projection ->
        addLine((projection as? Line3DProjectionBokorys)?.parent ?: lineById(projection.parentId))
    }
    state.selectedLinesAxo.forEach { projection ->
        addLine(projection.parent ?: lineById(projection.parentId))
    }

    state.selectedCurve3DId
        ?.takeIf { id -> state.curves3D.any { it.id == id } }
        ?.let { id -> result += RuledSurfaceDirectrixRef(id, RuledSurfaceDirectrixKind.CURVE) }

    val selectedConicParentIds = buildSet {
        state.selectedConicsPudorys.mapNotNullTo(this) { it.parent?.id ?: it.parentId }
        state.selectedConicsNarys.mapNotNullTo(this) { it.parent?.id ?: it.parentId }
        state.selectedConicsBokorys.mapNotNullTo(this) { it.parent?.id ?: it.parentId }
        state.selectedConicsAxo.mapNotNullTo(this) { it.parent?.id ?: it.parentId }
    }
    selectedConicParentIds
        .filter { id -> state.conics3D.any { it.id == id } }
        .forEach { id -> result += RuledSurfaceDirectrixRef(id, RuledSurfaceDirectrixKind.CONIC) }

    state.selectedSpheres3D.forEach { sphere ->
        result += RuledSurfaceDirectrixRef(sphere.id, RuledSurfaceDirectrixKind.SPHERE)
    }

    return result.toList()
}
