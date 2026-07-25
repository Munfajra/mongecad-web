package ui.mongeui.toolbar.rightDescriptionBar.ObjectList

import draw.mongescreen.labels.clearSelection
import model.classes.CylindricalSurface3D
import monge.input.selection.toggleSelectionAxoSegment
import monge.input.selection.toggleSelectionBokorysSegment
import monge.input.selection.toggleSelectionConic3D
import monge.input.selection.toggleSelectionNarysSegment
import monge.input.selection.toggleSelectionPudorysSegment
import state.MongeState

fun buildCylinderChildren(
    state: MongeState,
    cyl: CylindricalSurface3D,
    clearAllOnClick: Boolean,
): List<UiTreeItem> {

    val cylKey = "cyl:${cyl.id}"
    val resolved = buildList<ChildResolved> {
        val cylId= cyl.id



        // --- B) Podstavné kuželosečky – 3D (volitelné) ---
        fun addBaseConic(id: String?, label: String) {
            if (id.isNullOrBlank()) return
            val c3d = state.conics3D.firstOrNull { it.id == id } ?: return
            val type = getConicType3D(c3d.matrix)
            add(
                ChildResolved(
                    kind = ChildKind.CONIC,
                    key = "base3d:$label:${c3d.id}",
                    sortIndex = sortKeyDesc(c3d.creationIndex),
                    name = "$label: $type ${c3d.name}",
                    color = c3d.color,
                    is3D = true,
                    isSelected = {
                        state.selectedConicsNarys.any { (it.parent?.id ?: it.parentId) == c3d.id } ||
                                state.selectedConicsPudorys.any { (it.parent?.id ?: it.parentId) == c3d.id } ||
                                state.selectedConicsBokorys.any { (it.parent?.id ?: it.parentId) == c3d.id }
                    },
                    onClick = {
                        if (clearAllOnClick) clearSelection(state)
                        toggleSelectionConic3D(c3d, state)
                    }
                )
            )
        }

        addBaseConic(cyl.lowerConicId, "Dolní")
        addBaseConic(cyl.upperConicId, "Horní")

        // --- C) Obrysové úsečky (2D, bez parenta) ---
        cyl.edgeSegIdsPudorys2D.forEachIndexed { i, sid ->
            val s = state.segmentsPudorys.firstOrNull { it.id == sid } ?: return@forEachIndexed
            if (!s.showInAxo) return@forEachIndexed
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

        cyl.edgeSegIdsNarys2D.forEachIndexed { i, sid ->
            val s = state.segmentsNarys.firstOrNull { it.id == sid } ?: return@forEachIndexed
            if (!s.showInAxo) return@forEachIndexed
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

        cyl.edgeSegIdsBokorys2D.forEachIndexed { i, sid ->
            val s = state.segmentsBokorys.firstOrNull { it.id == sid } ?: return@forEachIndexed
            if (!s.showInAxo) return@forEachIndexed
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

        cyl.edgeSegIdsAxo2D.forEachIndexed { i, sid ->
            val s = state.segmentsAxo.firstOrNull { it.id == sid } ?: return@forEachIndexed
            if (!showAxoProjectionChildren(state)) return@forEachIndexed
            if (!s.showInAxo) return@forEachIndexed
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
    }

    // deduplikace + řazení (CONIC před SEGMENT, nebo naopak – jak chceš)
    val unique = resolved.distinctBy { it.key }

    val sortedChildren = unique
        .sortedWith(compareBy<ChildResolved>({ it.kind.ordinal }, { it.sortIndex }))

    return sortedChildren.map { r ->
        UiTreeItem(
            key = "$cylKey/${r.key}", // unikátní napříč listem
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
