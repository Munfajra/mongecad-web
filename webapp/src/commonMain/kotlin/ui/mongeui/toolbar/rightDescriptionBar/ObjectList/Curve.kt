package ui.mongeui.toolbar.rightDescriptionBar.ObjectList

import draw.mongescreen.labels.clearSelection
import model.classes.Curve3D
import model.classes.CurveBokorys
import model.classes.CurveNarys
import model.classes.CurvePudRef
import model.classes.CurvePudorys
import monge.input.selection.toggleSelection
import monge.input.selection.toggleSelectionAidPoint
import monge.input.selection.toggleSelectionAxo
import monge.input.selection.toggleSelectionBokorys
import monge.input.selection.toggleSelectionPudorys
import state.MongeState

private fun isPoint3DSelectedViaProjections(state: MongeState, point3dId: String): Boolean {
    // pokud máš přímo selectedPoints3D, můžeš přidat i tohle:
    if (state.selectedPoints3D.any { it.id == point3dId }) return true

    if (state.selectedPointsPudorys.any { it.parent?.id == point3dId }) return true
    if (state.selectedPointsNarys.any { it.parent?.id == point3dId }) return true
    return false
}

private fun selectPoint3DProjections(state: MongeState, point3dId: String, clear: Boolean) {
    if (clear) clearSelection(state)

    val pP = state.pointsPudorys.firstOrNull { it.parent?.id == point3dId }
    val pN = state.pointsNarys.firstOrNull { it.parent?.id == point3dId }
    val pA = if (showAxoProjectionChildren(state)) {
        state.pointsAxo.firstOrNull { it.parent?.id == point3dId }
    } else {
        null
    }

    if (pP != null && !state.selectedPointsPudorys.contains(pP)) {
        toggleSelectionPudorys(pP, state)
    }
    if (pN != null && !state.selectedPointsNarys.contains(pN)) {
        toggleSelection(pN, state)
    }
    if (pA != null && !state.selectedPointsAxo.contains(pA)) {
        toggleSelectionAxo(pA, state)
    }
}
fun buildCurve3DChildren(
    state: MongeState,
    curve: Curve3D,
    clearAllOnClick: Boolean,
): List<UiTreeItem> {

    val curveKey = "curve3d:${curve.id}"

    val children = buildList<UiTreeItem> {
        curve.pointIds.forEachIndexed { idx, pid ->
            // tady předpokládám, že pointIds jsou ID 3D bodů:
            val p3d = state.sharedPoints3D.firstOrNull { it.id == pid } ?: return@forEachIndexed

            add(
                UiTreeItem(
                    key = "$curveKey/pt3d:${p3d.id}",
                    sortIndex = sortKeyDesc(p3d.creationIndex) + idx, // zachová pořadí v křivce
                    name = "Bod ${p3d.name}",
                    color = p3d.color,
                    is3D = true,
                    superscript = p3d.superscript,
                    isSelected = { isPoint3DSelectedViaProjections(state, p3d.id) },
                    onClick = { selectPoint3DProjections(state, p3d.id, clearAllOnClick) },
                    children = emptyList()
                )
            )
        }
    }

    return children
}
fun buildCurveNarysChildren(
    state: MongeState,
    curve: CurveNarys,
    clearAllOnClick: Boolean,
): List<UiTreeItem> {

    val curveKey = "curveN:${curve.id}"

    return curve.pointIds.mapIndexedNotNull { idx, pid ->
        val p = state.pointsNarys.firstOrNull { it.id == pid } ?: return@mapIndexedNotNull null

        UiTreeItem(
            key = "$curveKey/pt2d:n:${p.id}",
            sortIndex = sortKeyDesc(p.effectiveCreationIndex) + idx, // zachová pořadí v křivce
            name = if (p.parent != null) "Bod ${p.parent?.name ?: p.name}" else "Bod ${p.name}₂",
            color = p.color,
            is3D = false,
            superscript = p.localSuperscript,
            isSelected = { state.selectedPointsNarys.contains(p) },
            onClick = {
                if (clearAllOnClick) clearSelection(state)
                toggleSelection(p, state)
            },
            children = emptyList()
        )
    }
}
fun buildCurveBokorysChildren(
    state: MongeState,
    curve: CurveBokorys,
    clearAllOnClick: Boolean,
): List<UiTreeItem> {
    val curveKey = "curveB:${curve.id}"

    return curve.pointIds.mapIndexedNotNull { idx, pid ->
        val p = state.pointsBokorys.firstOrNull { it.id == pid } ?: return@mapIndexedNotNull null

        UiTreeItem(
            key = "$curveKey/pt2d:b:${p.id}",
            sortIndex = sortKeyDesc(p.effectiveCreationIndex) + idx,
            name = if (p.parent != null) "Bod ${p.parent?.name ?: p.name}" else "Bod ${p.name}₃",
            color = p.color,
            is3D = false,
            superscript = p.localSuperscript,
            isSelected = { state.selectedPointsBokorys.contains(p) },
            onClick = {
                if (clearAllOnClick) clearSelection(state)
                toggleSelectionBokorys(p, state)
            },
            children = emptyList()
        )
    }
}

fun buildCurvePudorysChildren(
    state: MongeState,
    curve: CurvePudorys,
    clearAllOnClick: Boolean,
): List<UiTreeItem> {

    val curveKey = "curveP:${curve.id}"

    return curve.points.mapIndexedNotNull { idx, ref ->
        when (ref) {
            is CurvePudRef.P -> {
                val p = state.pointsPudorys.firstOrNull { it.id == ref.pointId } ?: return@mapIndexedNotNull null
                UiTreeItem(
                    key = "$curveKey/pt2d:p:${p.id}",
                    sortIndex = sortKeyDesc(p.effectiveCreationIndex) + idx,
                    name = if (p.parent != null) "Bod ${p.parent?.name ?: p.name}" else "Bod ${p.name}₁",
                    color = p.color,
                    is3D = false,
                    superscript = p.localSuperscript,
                    isSelected = { state.selectedPointsPudorys.contains(p) },
                    onClick = {
                        if (clearAllOnClick) clearSelection(state)
                        toggleSelectionPudorys(p, state)
                    },
                    children = emptyList()
                )
            }

            is CurvePudRef.A -> {
                val a = state.aidPointsLogical.firstOrNull { it.id == ref.aidId } ?: return@mapIndexedNotNull null
                val nm = a.name?.takeIf { it.isNotBlank() } ?: "Pom. bod"
                UiTreeItem(
                    key = "$curveKey/aid:${a.id}",
                    sortIndex = sortKeyDesc(a.creationIndex) + idx,
                    name = nm,
                    color = a.color,
                    is3D = false,
                    superscript = a.upperSuperscript,
                    subscript = a.lowerSuperscript,
                    isSelected = { state.selectedAidPointIds.contains(a.id) },
                    onClick = {
                        if (clearAllOnClick) clearSelection(state)
                        toggleSelectionAidPoint(a, state) // nebo tvoje logika pro aidpointy
                    },
                    children = emptyList()
                )
            }
        }
    }
}
