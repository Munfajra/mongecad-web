package ui.mongeui.toolbar.rightDescriptionBar.ObjectList

import draw.mongescreen.labels.clearSelection
import model.classes.RegularPolygon3D
import model.classes.PlanePolygon2D
import monge.input.selection.toggleSelectionAidPoint
import monge.input.selection.toggleSelection
import monge.input.selection.toggleSelectionNarysSegment
import monge.input.selection.toggleSelectionPoint3D
import monge.input.selection.toggleSelectionPudorys
import monge.input.selection.toggleSelectionPudorysSegment
import monge.input.selection.toggleSelectionSegment3D
import state.MongeState

fun buildPolygonChildren(
    state: MongeState,
    poly: RegularPolygon3D,
    clearAllOnClick: Boolean,
): List<UiTreeItem> {
    val polyKey = "poly:${poly.id}"

    // --- 1) body: z ID projekcí -> parent 3D (pokud existuje), jinak projekce ---
    val pointResolved = buildList<ChildResolved> {
        // Půdorys body
        poly.vertexPointIdsPudorys.forEach { pid ->
            val p = state.pointsPudorys.firstOrNull { it.id == pid } ?: return@forEach
            val parent = p.parent
            if (parent != null) {
                add(
                    ChildResolved(
                        kind = ChildKind.POINT,
                        key = "pt3d:${parent.id}",
                        sortIndex = sortKeyDesc(parent.creationIndex),
                        name = "Bod ${parent.name}",
                        color = parent.color,
                        is3D = true,
                        icon = ObjectListIcon.Point,
                        superscript = parent.superscript,
                        isSelected = { state.selectedPoints3D.any { it.id == parent.id } },
                        onClick = {
                            if (clearAllOnClick) clearSelection(state)
                            // vyber parent bodu (stejně jako jinde)
                            toggleSelectionPoint3D(parent, state)
                        }
                    )
                )
            } else {
                // standalone projekce
                add(
                    ChildResolved(
                        kind = ChildKind.POINT,
                        key = "pt2d:p:${p.id}",
                        sortIndex = sortKeyDesc(p.effectiveCreationIndex),
                        name = "Bod ${p.name}₁",
                        color = p.color,
                        is3D = false,
                        icon = ObjectListIcon.Point,
                        superscript = p.localSuperscript,
                        isSelected = { state.selectedPointsPudorys.contains(p) },
                        onClick = {
                            if (clearAllOnClick) clearSelection(state)
                            toggleSelectionPudorys(p, state)
                        }
                    )
                )
            }
        }

        // Nárys body
        poly.vertexPointIdsNarys.forEach { pid ->
            val p = state.pointsNarys.firstOrNull { it.id == pid } ?: return@forEach
            val parent = p.parent
            if (parent != null) {
                add(
                    ChildResolved(
                        kind = ChildKind.POINT,
                        key = "pt3d:${parent.id}",
                        sortIndex = sortKeyDesc(parent.creationIndex),
                        name = "Bod ${parent.name}",
                        color = parent.color,
                        is3D = true,
                        icon = ObjectListIcon.Point,
                        superscript = parent.superscript,
                        isSelected = { state.selectedPoints3D.any { it.id == parent.id } },
                        onClick = {
                            if (clearAllOnClick) clearSelection(state)
                            toggleSelectionPoint3D(parent, state)
                        }
                    )
                )
            } else {
                add(
                    ChildResolved(
                        kind = ChildKind.POINT,
                        key = "pt2d:n:${p.id}",
                        sortIndex = sortKeyDesc(p.effectiveCreationIndex),
                        name = "Bod ${p.name}₂",
                        color = p.color,
                        is3D = false,
                        icon = ObjectListIcon.Point,
                        superscript = p.localSuperscript,
                        isSelected = { state.selectedPointsNarys.contains(p) },
                        onClick = {
                            if (clearAllOnClick) clearSelection(state)
                            toggleSelection(p, state)
                        }
                    )
                )
            }
        }
    }

    // Body: prioritně ber z autoritativního 3D seznamu vrcholů (vertexPointIds), protože
    // vertexPointIdsPudorys/Narys mohou být neúplné (u stěn tělesa projekce míří na
    // koincidenční 3D bod → firstOrNull{parent==pointId} vrátí null a vrchol vypadne).
    // Fallback na 2D resolvaci jen když polygon nemá 3D vrcholy.
    val uniquePoints: List<ChildResolved> = if (poly.vertexPointIds.isNotEmpty()) {
        poly.vertexPointIds
            .mapNotNull { vid -> state.sharedPoints3D.firstOrNull { it.id == vid } }
            .map { p3 ->
                ChildResolved(
                    kind = ChildKind.POINT,
                    key = "pt3d:${p3.id}",
                    sortIndex = sortKeyDesc(p3.creationIndex),
                    name = "Bod ${p3.name}",
                    color = p3.color,
                    is3D = true,
                    icon = ObjectListIcon.Point,
                    superscript = p3.superscript,
                    isSelected = { state.selectedPoints3D.any { it.id == p3.id } },
                    onClick = {
                        if (clearAllOnClick) clearSelection(state)
                        toggleSelectionPoint3D(p3, state)
                    }
                )
            }
            .distinctBy { it.key }
    } else {
        // deduplikace bodů: když existuje parent, zobrazit ho jen jednou
        pointResolved.distinctBy { it.key } // pt3d:<id> nebo pt2d:p:<id> apod.
    }

    // --- 2) úsečky: obdobně ---
    val polygonSegment3DIds = poly.segmentIds3D.toSet()
    val segResolved = buildList<ChildResolved> {
        poly.segmentIds3D.forEach { sid ->
            val segment = state.segments3D.firstOrNull { it.id == sid } ?: return@forEach
            add(
                ChildResolved(
                    kind = ChildKind.SEGMENT,
                    key = "seg3d:${segment.id}",
                    sortIndex = sortKeyDesc(segment.creationIndex),
                    name = segment.name.ifBlank { "Úsečka" },
                    color = segment.color,
                    is3D = true,
                    icon = ObjectListIcon.Segment,
                    isSelected = { state.selectedSegments3D.any { it.id == segment.id } },
                    onClick = {
                        if (clearAllOnClick) clearSelection(state)
                        toggleSelectionSegment3D(segment, state)
                    }
                )
            )
        }

        // Půdorys úsečky
        poly.segmentIdsPudorys.forEach { sid ->
            val s = state.segmentsPudorys.firstOrNull { it.id == sid } ?: return@forEach
            val parent = s.parent
            if (parent?.id in polygonSegment3DIds) return@forEach
            if (parent != null) {
                add(
                    ChildResolved(
                        kind = ChildKind.SEGMENT,
                        key = "seg3d:${parent.id}",
                        sortIndex = sortKeyDesc(parent.creationIndex),
                        name = parent.name.ifBlank { "Úsečka" },
                        color = parent.color,
                        is3D = true,
                        icon = ObjectListIcon.Segment,
                        isSelected = { state.selectedSegments3D.any { it.id == parent.id } },
                        onClick = {
                            if (clearAllOnClick) clearSelection(state)
                            toggleSelectionSegment3D(parent, state)
                        }
                    )
                )
            } else {
                add(
                    ChildResolved(
                        kind = ChildKind.SEGMENT,
                        key = "seg2d:p:${s.id}",
                        sortIndex = sortKeyDesc(s.effectiveCreationIndex),
                        name = s.name?.ifBlank { "Úsečka" } ?: "Úsečka",
                        color = s.color,
                        is3D = false,
                        icon = ObjectListIcon.Segment,
                        isSelected = { state.selectedSegmentsPudorys.contains(s) },
                        onClick = {
                            if (clearAllOnClick) clearSelection(state)
                            toggleSelectionPudorysSegment(s, state)
                        }
                    )
                )
            }
        }

        // Nárys úsečky
        poly.segmentIdsNarys.forEach { sid ->
            val s = state.segmentsNarys.firstOrNull { it.id == sid } ?: return@forEach
            val parent = s.parent
            if (parent?.id in polygonSegment3DIds) return@forEach
            if (parent != null) {
                add(
                    ChildResolved(
                        kind = ChildKind.SEGMENT,
                        key = "seg3d:${parent.id}",
                        sortIndex = sortKeyDesc(parent.creationIndex),
                        name = parent.name.ifBlank { "Úsečka" },
                        color = parent.color,
                        is3D = true,
                        icon = ObjectListIcon.Segment,
                        isSelected ={ state.selectedSegments3D.any { it.id == parent.id } },
                        onClick = {
                            if (clearAllOnClick) clearSelection(state)
                            toggleSelectionSegment3D(parent, state)
                        }
                    )
                )
            } else {
                add(
                    ChildResolved(
                        kind = ChildKind.SEGMENT,
                        key = "seg2d:n:${s.id}",
                        sortIndex = sortKeyDesc(s.effectiveCreationIndex),
                        name = s.name?.ifBlank { "Úsečka" } ?: "Úsečka",
                        color = s.color,
                        is3D = false,
                        icon = ObjectListIcon.Segment,
                        isSelected = { state.selectedSegmentsNarys.contains(s) },
                        onClick = {
                            if (clearAllOnClick) clearSelection(state)
                            toggleSelectionNarysSegment(s, state)
                        }
                    )
                )
            }
        }
    }

    val uniqueSegs = segResolved.distinctBy { it.key }

    // --- 3) seřazení a převod na UiTreeItem ---
    val sortedChildren = (uniquePoints + uniqueSegs)
        .sortedWith(compareBy<ChildResolved>({ it.kind.ordinal }, { it.sortIndex }))

    return sortedChildren.map { r ->
        UiTreeItem(
            key = "$polyKey/${r.key}",     // ✅ key v listu unikátní i napříč celým UI
            sortIndex = r.sortIndex,
            name = r.name,
            color = r.color,
            is3D = r.is3D,
            superscript = r.superscript,
            subscript = r.subscript,
            icon = r.icon,
            isSelected = r.isSelected,
            onClick = r.onClick,
            children = emptyList()
        )
    }
}

fun buildPlanePolygonChildren(
    state: MongeState,
    polygon: PlanePolygon2D,
    clearAllOnClick: Boolean,
): List<UiTreeItem> {
    val polygonKey = "poly2d:${polygon.id}"

    fun resolvePoint(pointId: String) =
        state.pointsPudorys.firstOrNull { it.id == pointId }
            ?: state.helpSegmentsPudorys.asSequence()
                .filter { it.id in polygon.segmentIdsPudorys }
                .flatMap { sequenceOf(it.start, it.end) }
                .firstOrNull { it.id == pointId }

    val pointChildren = if (polygon.vertexAidPointIds.isNotEmpty()) {
        polygon.vertexAidPointIds.mapNotNull { pointId ->
            val point = state.aidPointsLogical.firstOrNull { it.id == pointId }
                ?: return@mapNotNull null
            UiTreeItem(
                key = "$polygonKey/aid:${point.id}",
                sortIndex = sortKeyDesc(point.creationIndex),
                name = point.name?.takeIf { it.isNotBlank() }?.let { "Bod $it" } ?: "Bod",
                color = point.color,
                is3D = false,
                superscript = point.upperSuperscript,
                subscript = point.lowerSuperscript,
                icon = ObjectListIcon.Point,
                isSelected = { point.id in state.selectedAidPointIds },
                onClick = {
                    if (clearAllOnClick) clearSelection(state)
                    toggleSelectionAidPoint(point, state)
                }
            )
        }
    } else {
        polygon.vertexPointIdsPudorys.mapNotNull { pointId ->
            val point = resolvePoint(pointId) ?: return@mapNotNull null
            UiTreeItem(
                key = "$polygonKey/pt2d:p:${point.id}",
                sortIndex = sortKeyDesc(point.effectiveCreationIndex),
                name = point.name?.takeIf { it.isNotBlank() }?.let { "Bod $it" } ?: "Bod",
                color = point.color,
                is3D = false,
                superscript = point.localSuperscript,
                icon = ObjectListIcon.Point,
                isSelected = { state.selectedPointsPudorys.any { it.id == point.id } },
                onClick = {
                    if (clearAllOnClick) clearSelection(state)
                    toggleSelectionPudorys(point, state)
                }
            )
        }
    }

    val segmentChildren = polygon.segmentIdsPudorys.mapNotNull { segmentId ->
        val segment = state.helpSegmentsPudorys.firstOrNull { it.id == segmentId }
            ?: return@mapNotNull null
        UiTreeItem(
            key = "$polygonKey/helpseg:p:${segment.id}",
            sortIndex = sortKeyDesc(segment.creationIndex),
            name = segment.name?.takeIf { it.isNotBlank() }?.let { "Úsečka $it" } ?: "Úsečka",
            color = segment.color,
            is3D = false,
            icon = ObjectListIcon.Segment,
            isSelected = { state.selectedSegmentsPudorys.any { it.id == segment.id } },
            onClick = {
                if (clearAllOnClick) clearSelection(state)
                toggleSelectionPudorysSegment(segment, state)
            }
        )
    }

    return pointChildren + segmentChildren
}
