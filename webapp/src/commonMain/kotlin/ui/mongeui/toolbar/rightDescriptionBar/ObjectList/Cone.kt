package ui.mongeui.toolbar.rightDescriptionBar.ObjectList

import draw.mongescreen.labels.clearSelection
import model.ProjectionMode
import model.classes.ConicalSurface3D
import monge.input.selection.toggleSelectionAxo
import monge.input.selection.toggleSelectionAxoSegment
import monge.input.selection.toggleSelectionBokorys
import monge.input.selection.toggleSelectionBokorysConic
import monge.input.selection.toggleSelectionBokorysSegment
import monge.input.selection.toggleSelectionConic2DFamilyByParentId
import monge.input.selection.toggleSelection
import monge.input.selection.toggleSelectionConic3D
import monge.input.selection.toggleSelectionNarysConic
import monge.input.selection.toggleSelectionNarysSegment
import monge.input.selection.toggleSelectionPoint3D
import monge.input.selection.toggleSelectionPudorys
import monge.input.selection.toggleSelectionPudorysConic
import monge.input.selection.toggleSelectionPudorysSegment
import state.MongeState

fun buildConeChildren(
    state: MongeState,
    cone: ConicalSurface3D,
    clearAllOnClick: Boolean,
): List<UiTreeItem> {

    val coneKey = "cone:${cone.id}"
    fun showChild(showInAxo: Boolean): Boolean =
        state.projectionMode != ProjectionMode.AXO || showInAxo

    val selected3DConicId: String? =
        state.selectedConicsPudorys.firstOrNull()?.let { it.parent?.id ?: it.parentId }
            ?: state.selectedConicsNarys.firstOrNull()?.let { it.parent?.id ?: it.parentId }
            ?: state.selectedConicsBokorys.firstOrNull()?.let { it.parent?.id ?: it.parentId }
            ?: state.selectedConicsAxo.firstOrNull()?.let { it.parent?.id ?: it.parentId }
    val resolved = buildList<ChildResolved> {

        // --- A) Vrchol (3D bod) ---
        val apex3D = state.sharedPoints3D.firstOrNull { it.id == cone.apexId }
        if (apex3D != null) {
            add(
                ChildResolved(
                    kind = ChildKind.POINT,
                    key = "apex3d:${apex3D.id}",
                    sortIndex = sortKeyDesc(apex3D.creationIndex),
                    name = "Vrchol ${apex3D.name}",
                    color = apex3D.color,
                    is3D = true,
                    superscript = apex3D.superscript,
                    isSelected = { state.selectedPoints3D.any { it.id == apex3D.id } },
                    onClick = {
                        if (clearAllOnClick) clearSelection(state)
                        toggleSelectionPoint3D(apex3D, state)
                    }
                )
            )
        } else {
            // volitelné fallback (kdyby se apex3D nepodařilo dohledat)
            cone.apexProjPudorysId?.let { pid ->
                val p = state.pointsPudorys.firstOrNull { it.id == pid }
                if (p != null) {
                    add(
                        ChildResolved(
                            kind = ChildKind.POINT,
                            key = "apex2d:p:${p.id}",
                            sortIndex = sortKeyDesc(p.effectiveCreationIndex),
                            name = "Vrchol₁ ${p.name}",
                            color = p.color,
                            is3D = false,
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
            cone.apexProjNarysId?.let { nid ->
                val p = state.pointsNarys.firstOrNull { it.id == nid }
                if (p != null) {
                    add(
                        ChildResolved(
                            kind = ChildKind.POINT,
                            key = "apex2d:n:${p.id}",
                            sortIndex = sortKeyDesc(p.effectiveCreationIndex),
                            name = "Vrchol₂ ${p.name}",
                            color = p.color,
                            is3D = false,
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
        cone.apexProjPudorysId?.let { pid ->
            val p = state.pointsPudorys.firstOrNull { it.id == pid }
            if (p != null && showChild(p.showInAxo)) {
                add(
                    ChildResolved(
                        kind = ChildKind.POINT,
                        key = "apex2d:p:${p.id}",
                        sortIndex = sortKeyDesc(p.effectiveCreationIndex),
                        name = "Vrchol₁ ${p.name ?: ""}",
                        color = p.color,
                        is3D = false,
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
        cone.apexProjNarysId?.let { nid ->
            val p = state.pointsNarys.firstOrNull { it.id == nid }
            if (p != null && showChild(p.showInAxo)) {
                add(
                    ChildResolved(
                        kind = ChildKind.POINT,
                        key = "apex2d:n:${p.id}",
                        sortIndex = sortKeyDesc(p.effectiveCreationIndex),
                        name = "Vrchol₂ ${p.name ?: ""}",
                        color = p.color,
                        is3D = false,
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
        cone.apexProjBokorysId?.let { bid ->
            val p = state.pointsBokorys.firstOrNull { it.id == bid }
            if (p != null && showChild(p.showInAxo)) {
                add(
                    ChildResolved(
                        kind = ChildKind.POINT,
                        key = "apex2d:b:${p.id}",
                        sortIndex = sortKeyDesc(p.effectiveCreationIndex),
                        name = "Vrchol₃ ${p.name ?: ""}",
                        color = p.color,
                        is3D = false,
                        superscript = p.localSuperscript,
                        isSelected = { state.selectedPointsBokorys.contains(p) },
                        onClick = {
                            if (clearAllOnClick) clearSelection(state)
                            toggleSelectionBokorys(p, state)
                        }
                    )
                )
            }
        }
        cone.apexProjAxoId?.let { aid ->
            val p = state.pointsAxo.firstOrNull { it.id == aid }
            if (p != null && showAxoProjectionChildren(state) && showChild(p.showInAxo)) {
                add(
                    ChildResolved(
                        kind = ChildKind.POINT,
                        key = "apex2d:a:${p.id}",
                        sortIndex = sortKeyDesc(p.effectiveCreationIndex),
                        name = "Vrcholₐ ${p.name ?: ""}",
                        color = p.color,
                        is3D = false,
                        superscript = p.localSuperscript,
                        isSelected = { state.selectedPointsAxo.contains(p) },
                        onClick = {
                            if (clearAllOnClick) clearSelection(state)
                            toggleSelectionAxo(p, state)
                        }
                    )
                )
            }
        }

        // --- B) Podstavná kuželosečka (3D) ---
        val directrix3D = state.conics3D.firstOrNull { it.id == cone.directrixId }
        if (directrix3D != null) {
            val type = getConicType3D(directrix3D.matrix) // nebo své getConicType3D
            add(
                ChildResolved(
                    kind = ChildKind.CONIC,
                    key = "dir3d:${directrix3D.id}",
                    sortIndex = sortKeyDesc(directrix3D.creationIndex),
                    name = "Podstava: $type ${directrix3D.name}",
                    color = directrix3D.color,
                    is3D = true,
                    isSelected = {selected3DConicId == directrix3D.id },
                    onClick = {
                        if (clearAllOnClick) clearSelection(state)
                        toggleSelectionConic3D(directrix3D, state)
                    }
                )
            )
        }
        state.conicsPudorys
            .filter { it.parent?.id == cone.directrixId || it.parentId == cone.directrixId }
            .filter { showChild(it.showInAxo) }
            .forEach { c ->
                add(
                    ChildResolved(
                        kind = ChildKind.CONIC,
                        key = "dir2d:p:${c.id}",
                        sortIndex = sortKeyDesc(c.effectiveCreationIndex),
                        name = "Podstava₁ ${c.name}",
                        color = c.color,
                        is3D = false,
                        isSelected = { state.selectedConicsPudorys.contains(c) },
                        onClick = {
                            if (clearAllOnClick) clearSelection(state)
                            toggleSelectionPudorysConic(c, state)
                        }
                    )
                )
            }
        state.conicsNarys
            .filter { it.parent?.id == cone.directrixId || it.parentId == cone.directrixId }
            .filter { showChild(it.showInAxo) }
            .forEach { c ->
                add(
                    ChildResolved(
                        kind = ChildKind.CONIC,
                        key = "dir2d:n:${c.id}",
                        sortIndex = sortKeyDesc(c.effectiveCreationIndex),
                        name = "Podstava₂ ${c.name}",
                        color = c.color,
                        is3D = false,
                        isSelected = { state.selectedConicsNarys.contains(c) },
                        onClick = {
                            if (clearAllOnClick) clearSelection(state)
                            toggleSelectionNarysConic(c, state)
                        }
                    )
                )
            }
        state.conicsBokorys
            .filter { it.parent?.id == cone.directrixId || it.parentId == cone.directrixId }
            .filter { showChild(it.showInAxo) }
            .forEach { c ->
                add(
                    ChildResolved(
                        kind = ChildKind.CONIC,
                        key = "dir2d:b:${c.id}",
                        sortIndex = sortKeyDesc(c.effectiveCreationIndex),
                        name = "Podstava₃ ${c.name}",
                        color = c.color,
                        is3D = false,
                        isSelected = { state.selectedConicsBokorys.contains(c) },
                        onClick = {
                            if (clearAllOnClick) clearSelection(state)
                            toggleSelectionBokorysConic(c, state)
                        }
                    )
                )
            }
        state.conicsAxo
            .filter { it.parent?.id == cone.directrixId || it.parentId == cone.directrixId }
            .filter { showAxoProjectionChildren(state) }
            .filter { showChild(it.showInAxo) }
            .forEach { c ->
                add(
                    ChildResolved(
                        kind = ChildKind.CONIC,
                        key = "dir2d:a:${c.id}",
                        sortIndex = sortKeyDesc(c.effectiveCreationIndex),
                        name = "Podstavaₐ ${c.name}",
                        color = c.color,
                        is3D = false,
                        isSelected = { state.selectedConicsAxo.contains(c) },
                        onClick = {
                            if (clearAllOnClick) clearSelection(state)
                            val parentId = c.parent?.id ?: c.parentId
                            if (parentId != null) toggleSelectionConic2DFamilyByParentId(parentId, state)
                        }
                    )
                )
            }

        // --- C) Obrysové úsečky (2D, bez parenta) ---
        state.segmentsPudorys
            .filter { it.id in cone.edgeSegIdsPudorys2D || (it.isConicalSilhouette && it.conicalSurfaceId == cone.id) }
            .filter { showChild(it.showInAxo) }
            .forEachIndexed { i, s ->
            add(
                ChildResolved(
                    kind = ChildKind.SEGMENT,
                    key = "edgeSeg2d:p:${s.id}",
                    sortIndex = sortKeyDesc(s.effectiveCreationIndex) + i,
                    name = (s.name?.ifBlank { "Obrysová úsečka" } ?: "Obrysová úsečka") + "₁",
                    color = s.color,
                    is3D = false,
                    isSelected = { state.selectedSegmentsPudorys.contains(s) },
                    onClick = {
                        if (clearAllOnClick) clearSelection(state)
                        toggleSelectionPudorysSegment(s, state)
                    }
                )
            )
        }

        state.segmentsNarys
            .filter { it.id in cone.edgeSegIdsNarys2D || (it.isConicalSilhouette && it.conicalSurfaceId == cone.id) }
            .filter { showChild(it.showInAxo) }
            .forEachIndexed { i, s ->
            add(
                ChildResolved(
                    kind = ChildKind.SEGMENT,
                    key = "edgeSeg2d:n:${s.id}",
                    sortIndex = sortKeyDesc(s.effectiveCreationIndex) + i,
                    name = (s.name?.ifBlank { "Obrysová úsečka" } ?: "Obrysová úsečka") + "₂",
                    color = s.color,
                    is3D = false,
                    isSelected = { state.selectedSegmentsNarys.contains(s) },
                    onClick = {
                        if (clearAllOnClick) clearSelection(state)
                        toggleSelectionNarysSegment(s, state)
                    }
                )
            )
        }
        state.segmentsBokorys
            .filter { it.id in cone.edgeSegIdsBokorys2D || (it.isConicalSilhouette && it.conicalSurfaceId == cone.id) }
            .filter { showChild(it.showInAxo) }
            .forEachIndexed { i, s ->
            add(
                ChildResolved(
                    kind = ChildKind.SEGMENT,
                    key = "edgeSeg2d:b:${s.id}",
                    sortIndex = sortKeyDesc(s.effectiveCreationIndex) + i,
                    name = (s.name?.ifBlank { "Obrysová úsečka" } ?: "Obrysová úsečka") + "₃",
                    color = s.color,
                    is3D = false,
                    isSelected = { state.selectedSegmentsBokorys.contains(s) },
                    onClick = {
                        if (clearAllOnClick) clearSelection(state)
                        toggleSelectionBokorysSegment(s, state)
                    }
                )
            )
        }
        state.segmentsAxo
            .filter { it.id in cone.edgeSegIdsAxo2D || (it.isConicalSilhouette && it.conicalSurfaceId == cone.id) }
            .filter { showAxoProjectionChildren(state) }
            .filter { showChild(it.showInAxo) }
            .forEachIndexed { i, s ->
            add(
                ChildResolved(
                    kind = ChildKind.SEGMENT,
                    key = "edgeSeg2d:a:${s.id}",
                    sortIndex = sortKeyDesc(s.effectiveCreationIndex) + i,
                    name = (s.name?.ifBlank { "Obrysová úsečka" } ?: "Obrysová úsečka") + "ₐ",
                    color = s.color,
                    is3D = false,
                    isSelected = { state.selectedSegmentsAxo.contains(s) },
                    onClick = {
                        if (clearAllOnClick) clearSelection(state)
                        toggleSelectionAxoSegment(s, state)
                    }
                )
            )
        }

        cone.edgePointIdsPudorys2D.forEachIndexed { i, pid ->
            val p = state.pointsPudorys.firstOrNull { it.id == pid } ?: return@forEachIndexed
            if (!showChild(p.showInAxo)) return@forEachIndexed
            add(
                ChildResolved(
                    kind = ChildKind.POINT,
                    key = "edgePoint2d:p:${p.id}",
                    sortIndex = sortKeyDesc(p.effectiveCreationIndex) + i,
                    name = "Bod obrysu₁",
                    color = p.color,
                    is3D = false,
                    superscript = p.localSuperscript,
                    isSelected = { state.selectedPointsPudorys.contains(p) },
                    onClick = {
                        if (clearAllOnClick) clearSelection(state)
                        toggleSelectionPudorys(p, state)
                    }
                )
            )
        }
        cone.edgePointIdsNarys2D.forEachIndexed { i, pid ->
            val p = state.pointsNarys.firstOrNull { it.id == pid } ?: return@forEachIndexed
            if (!showChild(p.showInAxo)) return@forEachIndexed
            add(
                ChildResolved(
                    kind = ChildKind.POINT,
                    key = "edgePoint2d:n:${p.id}",
                    sortIndex = sortKeyDesc(p.effectiveCreationIndex) + i,
                    name = "Bod obrysu₂",
                    color = p.color,
                    is3D = false,
                    superscript = p.localSuperscript,
                    isSelected = { state.selectedPointsNarys.contains(p) },
                    onClick = {
                        if (clearAllOnClick) clearSelection(state)
                        toggleSelection(p, state)
                    }
                )
            )
        }
        cone.edgePointIdsBokorys2D.forEachIndexed { i, pid ->
            val p = state.pointsBokorys.firstOrNull { it.id == pid } ?: return@forEachIndexed
            if (!showChild(p.showInAxo)) return@forEachIndexed
            add(
                ChildResolved(
                    kind = ChildKind.POINT,
                    key = "edgePoint2d:b:${p.id}",
                    sortIndex = sortKeyDesc(p.effectiveCreationIndex) + i,
                    name = "Bod obrysu₃",
                    color = p.color,
                    is3D = false,
                    superscript = p.localSuperscript,
                    isSelected = { state.selectedPointsBokorys.contains(p) },
                    onClick = {
                        if (clearAllOnClick) clearSelection(state)
                        toggleSelectionBokorys(p, state)
                    }
                )
            )
        }
        cone.edgePointIdsAxo2D.forEachIndexed { i, pid ->
            val p = state.pointsAxo.firstOrNull { it.id == pid } ?: return@forEachIndexed
            if (!showAxoProjectionChildren(state)) return@forEachIndexed
            if (!showChild(p.showInAxo)) return@forEachIndexed
            add(
                ChildResolved(
                    kind = ChildKind.POINT,
                    key = "edgePoint2d:a:${p.id}",
                    sortIndex = sortKeyDesc(p.effectiveCreationIndex) + i,
                    name = "Bod obrysuₐ",
                    color = p.color,
                    is3D = false,
                    superscript = p.localSuperscript,
                    isSelected = { state.selectedPointsAxo.contains(p) },
                    onClick = {
                        if (clearAllOnClick) clearSelection(state)
                        toggleSelectionAxo(p, state)
                    }
                )
            )
        }
    }

    // deduplikace (spíš jen pro jistotu)
    val unique = resolved.distinctBy { it.key }

    // řazení: POINTS -> CONIC -> SEGMENTS (podle kind ordinal)
    val sortedChildren = unique
        .sortedWith(compareBy<ChildResolved>({ it.kind.ordinal }, { it.sortIndex }))

    return sortedChildren.map { r ->
        UiTreeItem(
            key = "$coneKey/${r.key}",
            sortIndex = r.sortIndex,
            name = r.name,
            color = r.color,
            is3D = r.is3D,
            superscript = r.superscript,
            subscript = r.subscript,
            isSelected = r.isSelected,
            onClick = r.onClick,
            children = emptyList()
        )
    }
}
