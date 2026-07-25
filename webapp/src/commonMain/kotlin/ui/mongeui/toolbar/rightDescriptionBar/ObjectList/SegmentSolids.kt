package ui.mongeui.toolbar.rightDescriptionBar.ObjectList

import draw.mongescreen.labels.clearSelection
import model.classes.SegmentSolid3D
import monge.input.selection.togglePolygonSelection
import monge.input.selection.toggleSelectionPoint3D
import monge.input.selection.toggleSelectionSegment3D
import state.MongeState

fun buildSegmentSolidChildren(
    state: MongeState,
    solid: SegmentSolid3D,
    clearAllOnClick: Boolean
): List<UiTreeItem> {
    val solidKey = "segmentsolid:${solid.id}"
    val solidPolygonIds = solid.polygonIds.toSet()
    val polygons = state.polygons3D.filter { it.id in solidPolygonIds }
    val polygonSegmentIds = polygons.flatMap { it.segmentIds3D }.toHashSet()
    val segmentById = state.segments3D.associateBy { it.id }
    val pointById = state.sharedPoints3D.associateBy { it.id }

    // --- VRCHOLY tělesa: zvlášť jako přímé děti (klik = výběr/přejmenování) ---
    val vertexChildren = solid.vertexPointIds
        .asSequence()
        .distinct()
        .mapNotNull { pointById[it] }
        .map { p ->
            UiTreeItem(
                key = "$solidKey/vertex:pt3d:${p.id}",
                sortIndex = sortKeyDesc(p.creationIndex),
                name = "Bod ${p.name}",
                color = p.color,
                is3D = true,
                icon = ObjectListIcon.Point,
                superscript = p.superscript,
                isSelected = { state.selectedPoints3D.any { it.id == p.id } },
                onClick = {
                    if (clearAllOnClick) clearSelection(state)
                    toggleSelectionPoint3D(p, state)
                },
                children = emptyList()
            )
        }
        .toList()

    val polygonChildren = polygons.map { poly ->
        UiTreeItem(
            key = "$solidKey/poly:${poly.id}",
            sortIndex = sortKeyDesc(poly.creationIndex),
            name = polygonDisplayName(state, poly),
            color = poly.color,
            is3D = true,
            icon = ObjectListIcon.Polygon(poly.n),
            isSelected = { state.selectedPolygons.any { it.id == poly.id } },
            onClick = {
                if (clearAllOnClick) clearSelection(state)
                togglePolygonSelection(state, poly.id, clearOthers = false)
            },
            children = buildPolygonChildren(
                state = state,
                poly = poly,
                clearAllOnClick = clearAllOnClick
            )
        )
    }

    val looseSegmentChildren = solid.segmentIds3D
        .asSequence()
        .filterNot { it in polygonSegmentIds }
        .mapNotNull { segmentById[it] }
        .map { segment ->
            UiTreeItem(
                key = "$solidKey/seg:${segment.id}",
                sortIndex = sortKeyDesc(segment.creationIndex),
                name = segment.name.ifBlank { "Hrana" },
                color = segment.color,
                is3D = true,
                icon = ObjectListIcon.Segment,
                isSelected = { state.selectedSegments3D.any { it.id == segment.id } },
                onClick = {
                    if (clearAllOnClick) clearSelection(state)
                    toggleSelectionSegment3D(segment, state)
                },
                children = buildSegment3DChildren(state, segment, clearAllOnClick)
            )
        }
        .toList()

    // Pevné pořadí skupin: 1) body (vrcholy), 2) mnohoúhelníky (stěny), 3) ostatní (hrany).
    // flattenTree řadí children globálně sestupně podle sortIndex, tak jen zvedneme
    // sortIndex podle skupiny (v rámci skupiny zůstává původní pořadí dle creation indexu).
    fun UiTreeItem.inGroup(rank: Long): UiTreeItem =
        copy(sortIndex = rank * GROUP_SORT_STRIDE + sortIndex.coerceIn(0L, GROUP_SORT_STRIDE - 1L))

    return vertexChildren.map { it.inGroup(2L) } +
        polygonChildren.map { it.inGroup(1L) } +
        looseSegmentChildren.map { it.inGroup(0L) }
}

// Odstup mezi skupinami children v ObjectListu – větší než jakýkoli reálný creation index,
// aby se skupiny nepromíchaly při globálním řazení podle sortIndex.
private const val GROUP_SORT_STRIDE = 1_000_000_000_000L
