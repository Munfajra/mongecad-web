package ui.mongeui.toolbar.rightDescriptionBar.ObjectList

import draw.mongescreen.labels.clearSelection
import model.ProjectionMode
import model.classes.ConicSectionAxo
import model.classes.ConicSectionBokorys
import model.classes.ConicSectionNarys
import model.classes.ConicSectionPudorys
import model.classes.SphereSurface3D
import state.MongeState

fun buildSphereChildren(
    state: MongeState,
    sphere: SphereSurface3D,
    clearAllOnClick: Boolean,
): List<UiTreeItem> {
    val sphereKey = "sphere:${sphere.id}"
    fun showChild(showInAxo: Boolean): Boolean =
        state.projectionMode != ProjectionMode.AXO || showInAxo

    val resolved = buildList<ChildResolved> {
        state.conicsPudorys
            .filter { it.parentId == sphere.id }
            .filter { showChild(it.showInAxo) }
            .forEach { c ->
                add(sphereConicChild("p", "Půdorys", c, state.selectedConicsPudorys.contains(c)) {
                    if (clearAllOnClick) clearSelection(state)
                    if (state.selectedConicsPudorys.contains(c)) state.selectedConicsPudorys.remove(c)
                    else state.selectedConicsPudorys.add(c)
                })
            }

        state.conicsNarys
            .filter { it.parentId == sphere.id }
            .filter { showChild(it.showInAxo) }
            .forEach { c ->
                add(sphereConicChild("n", "Nárys", c, state.selectedConicsNarys.contains(c)) {
                    if (clearAllOnClick) clearSelection(state)
                    if (state.selectedConicsNarys.contains(c)) state.selectedConicsNarys.remove(c)
                    else state.selectedConicsNarys.add(c)
                })
            }

        state.conicsBokorys
            .filter { it.parentId == sphere.id }
            .filter { showChild(it.showInAxo) }
            .forEach { c ->
                add(sphereConicChild("b", "Bokorys", c, state.selectedConicsBokorys.contains(c)) {
                    if (clearAllOnClick) clearSelection(state)
                    if (state.selectedConicsBokorys.contains(c)) state.selectedConicsBokorys.remove(c)
                    else state.selectedConicsBokorys.add(c)
                })
            }

        state.conicsAxo
            .filter { it.parentId == sphere.id }
            .filter { showAxoProjectionChildren(state) }
            .filter { showChild(it.showInAxo) }
            .forEach { c ->
                val label = when {
                    c.rawName.endsWith("_obrys") -> "Axo obrys"
                    c.rawName.endsWith("_rovnik") -> "Axo rovník"
                    c.rawName.endsWith("_polednik_xz") -> "Axo poledník xz"
                    c.rawName.endsWith("_polednik_yz") -> "Axo poledník yz"
                    else -> "Axo průmět"
                }
                add(sphereConicChild("a", label, c, state.selectedConicsAxo.contains(c)) {
                    if (clearAllOnClick) clearSelection(state)
                    if (state.selectedConicsAxo.contains(c)) state.selectedConicsAxo.remove(c)
                    else state.selectedConicsAxo.add(c)
                })
            }
    }

    return resolved
        .distinctBy { it.key }
        .sortedWith(compareBy<ChildResolved>({ it.kind.ordinal }, { it.sortIndex }))
        .map { r ->
            UiTreeItem(
                key = "$sphereKey/${r.key}",
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

private fun sphereConicChild(
    projectionKey: String,
    label: String,
    conic: ConicSectionPudorys,
    selected: Boolean,
    onClick: () -> Unit,
) = ChildResolved(
    kind = ChildKind.CONIC,
    key = "sphereConic:$projectionKey:${conic.id}",
    sortIndex = sortKeyDesc(conic.effectiveCreationIndex),
    name = label,
    color = conic.color,
    is3D = false,
    isSelected = { selected },
    onClick = onClick
)

private fun sphereConicChild(
    projectionKey: String,
    label: String,
    conic: ConicSectionNarys,
    selected: Boolean,
    onClick: () -> Unit,
) = ChildResolved(
    kind = ChildKind.CONIC,
    key = "sphereConic:$projectionKey:${conic.id}",
    sortIndex = sortKeyDesc(conic.effectiveCreationIndex),
    name = label,
    color = conic.color,
    is3D = false,
    isSelected = { selected },
    onClick = onClick
)

private fun sphereConicChild(
    projectionKey: String,
    label: String,
    conic: ConicSectionBokorys,
    selected: Boolean,
    onClick: () -> Unit,
) = ChildResolved(
    kind = ChildKind.CONIC,
    key = "sphereConic:$projectionKey:${conic.id}",
    sortIndex = sortKeyDesc(conic.effectiveCreationIndex),
    name = label,
    color = conic.color,
    is3D = false,
    isSelected = { selected },
    onClick = onClick
)

private fun sphereConicChild(
    projectionKey: String,
    label: String,
    conic: ConicSectionAxo,
    selected: Boolean,
    onClick: () -> Unit,
) = ChildResolved(
    kind = ChildKind.CONIC,
    key = "sphereConic:$projectionKey:${conic.id}",
    sortIndex = sortKeyDesc(conic.effectiveCreationIndex),
    name = label,
    color = conic.color,
    is3D = false,
    isSelected = { selected },
    onClick = onClick
)
