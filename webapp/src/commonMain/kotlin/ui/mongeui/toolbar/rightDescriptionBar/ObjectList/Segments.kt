package ui.mongeui.toolbar.rightDescriptionBar.ObjectList

import draw.mongescreen.labels.clearSelection
import model.Point3D
import model.classes.*
import monge.input.selection.toggleSelectionAidPoint
import monge.input.selection.toggleSelectionAxo
import monge.input.selection.toggleSelectionBokorys
import monge.input.selection.toggleSelectionPudorys
import monge.input.selection.toggleSelectionPoint3D
import monge.input.selection.toggleSelection
import state.MongeState

fun buildSegmentPudorysChildren(
    state: MongeState,
    seg: Segment2DPudorys,
    clearAllOnClick: Boolean,
): List<UiTreeItem> {

    val segKey = "segP:${seg.id}"
    val pt = state.pointsPudorys.filter { p ->
        (p.parentSegment?.id == seg.id) ||
                (p.id == seg.start.id) ||
                (p.id == seg.end.id)
    }
    return pt.mapIndexedNotNull { idx, ref ->

        val p = state.pointsPudorys.firstOrNull { it.id == ref.id } ?: return@mapIndexedNotNull null
        UiTreeItem(
            key = "$segKey/pt2d:p:${p.id}",
            sortIndex = sortKeyDesc(p.effectiveCreationIndex) + idx,
            name = "Bod ${p.name}₁",
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
}

fun buildSegmentNarysChildren(
    state: MongeState,
    seg: Segment2DNarys,
    clearAllOnClick: Boolean,
): List<UiTreeItem> {
    val segKey = "segN:${seg.id}"
    val pts = state.pointsNarys.filter { p ->
        (p.parentSegment?.id == seg.id) || (p.id == seg.start.id) || (p.id == seg.end.id)
    }
    return pts.mapIndexedNotNull { idx, ref ->
        val p = state.pointsNarys.firstOrNull { it.id == ref.id } ?: return@mapIndexedNotNull null
        UiTreeItem(
            key = "$segKey/pt2d:n:${p.id}",
            sortIndex = sortKeyDesc(p.creationIndex) + idx,
            name = "Bod ${p.name}₂",
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

fun buildSegmentBokorysChildren(
    state: MongeState,
    seg: Segment2DBokorys,
    clearAllOnClick: Boolean,
): List<UiTreeItem> {
    val segKey = "segB:${seg.id}"
    val pts = state.pointsBokorys.filter { p ->
        (p.parentSegment?.id == seg.id) || (p.id == seg.start.id) || (p.id == seg.end.id)
    }
    return pts.mapIndexedNotNull { idx, ref ->
        val p = state.pointsBokorys.firstOrNull { it.id == ref.id } ?: return@mapIndexedNotNull null
        UiTreeItem(
            key = "$segKey/pt2d:b:${p.id}",
            sortIndex = sortKeyDesc(p.effectiveCreationIndex) + idx,
            name = "Bod ${p.name}₃",
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

fun buildSegmentAxoChildren(
    state: MongeState,
    seg: Segment2DAxo,
    clearAllOnClick: Boolean,
): List<UiTreeItem> {
    val segKey = "segA:${seg.id}"
    val pts = state.pointsAxo.filter { p ->
        (p.parentSegment?.id == seg.id) || (p.id == seg.start.id) || (p.id == seg.end.id)
    }
    return pts.mapIndexedNotNull { idx, ref ->
        val p = state.pointsAxo.firstOrNull { it.id == ref.id } ?: return@mapIndexedNotNull null
        UiTreeItem(
            key = "$segKey/pt2d:a:${p.id}",
            sortIndex = sortKeyDesc(p.effectiveCreationIndex) + idx,
            name = "Bod ${p.name}ₐ",
            color = p.color,
            is3D = false,
            superscript = p.localSuperscript,
            isSelected = { state.selectedPointsAxo.contains(p) },
            onClick = {
                if (clearAllOnClick) clearSelection(state)
                toggleSelectionAxo(p, state)
            },
            children = emptyList()
        )
    }
}

fun buildSegment3DChildren(
    state: MongeState,
    seg3D: Segment3D,
    clearAllOnClick: Boolean,
): List<UiTreeItem> {
    val segKey = "seg3d:${seg3D.id}"
    val aliveById = state.sharedPoints3D.associateBy { it.id }
    val pts = listOfNotNull(
        aliveById[seg3D.start.id],
        aliveById[seg3D.end.id]
    )
    return pts.mapIndexed { idx, p ->
        UiTreeItem(
            key = "$segKey/pt3d:${p.id}",
            sortIndex = sortKeyDesc(p.creationIndex) + idx,
            name = "Bod ${p.name}",
            color = p.color,
            is3D = true,
            superscript = p.superscript,
            isSelected = { state.selectedPoints3D.any { it.id == p.id } },
            onClick = {
                if (clearAllOnClick) clearSelection(state)
                toggleSelectionPoint3D(p, state)
            },
            children = emptyList()
        )
    }
}
