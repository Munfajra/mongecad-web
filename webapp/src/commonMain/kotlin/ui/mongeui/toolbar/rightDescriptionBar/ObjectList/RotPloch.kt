package ui.mongeui.toolbar.rightDescriptionBar.ObjectList

import monge.input.selection.isConic3DSelected
import monge.input.selection.selectConic3DProjections
import model.SOR_BOKORYS_MERIDIAN_ID_PREFIX
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import draw.mongescreen.labels.clearSelection
import model.classes.HelpSegmentNarys
import model.classes.Segment2DNarys
import model.SolidOfRevolutionNarys
import model.SolidOfRevolutionPudorys
import model.classes.CurveAxo
import model.classes.CurveBokorys
import model.classes.HelpSegmentPudorys
import model.classes.Segment2DPudorys
import monge.input.selection.*
import state.MongeState

private const val SOR_AXO_CONTOUR_ID_PREFIX = "sorAxoContour"

private data class ResolvedObj(
    val key: String,
    val sortIndex: Long,
    val name: String,
    val color: Color,
    val is3D: Boolean,
    val icon: ObjectListIcon? = null,
    val superscript: String? = null,
    val subscript: String? = null,
    val isSelected: () -> Boolean,
    val onClick: () -> Unit,
)

private fun cleanTypedName(type: String, rawName: String, suffix: String = ""): String {
    val cleaned = rawName.trim()
    if (cleaned.startsWith(type, ignoreCase = true)) return cleaned + suffix
    return if (cleaned.isBlank()) "$type$suffix" else "$type $cleaned$suffix"
}
private fun MutableList<ChildResolved>.addSoRAxoContourChildren(
    state: MongeState,
    solidId: String,
    clearAllOnClick: Boolean
) {
    state.curvesAxo
        .asSequence()
        .filter { it.parentId == solidId && it.id.startsWith(SOR_AXO_CONTOUR_ID_PREFIX) }
        .filter { it.showInAxo }
        .forEachIndexed { index, curve: CurveAxo ->
            add(
                ChildResolved(
                    kind = ChildKind.CURVE,
                    key = "curveAxo:${curve.id}",
                    sortIndex = sortKeyDesc(curve.effectiveCreationIndex) + index,
                    name = curve.name.ifBlank { "AXO obrys" },
                    color = curve.effectiveColor,
                    is3D = false,
                    icon = ObjectListIcon.Curve,
                    isSelected = { state.selectedCurveAxoId == curve.id || state.selectedSolidOfRevolutionId == solidId },
                    onClick = {
                        if (clearAllOnClick) clearSelection(state)
                        state.selectedCurveAxoId = curve.id
                    }
                )
            )
        }
}

private fun MutableList<ChildResolved>.addSoRBokorysMeridianChildren(
    state: MongeState,
    solidId: String,
    clearAllOnClick: Boolean
) {
    state.curvesBokorys
        .asSequence()
        .filter { it.parentId == solidId && it.id.startsWith(SOR_BOKORYS_MERIDIAN_ID_PREFIX) }
        .filter { it.showInAxo }
        .forEachIndexed { index, curve: CurveBokorys ->
            add(
                ChildResolved(
                    kind = ChildKind.CURVE,
                    key = "curveBokorys:${curve.id}",
                    sortIndex = sortKeyDesc(curve.effectiveCreationIndex) + index,
                    name = curve.name.ifBlank { "Bokorysný meridián" },
                    color = curve.effectiveColor,
                    is3D = false,
                    icon = ObjectListIcon.Curve,
                    isSelected = { state.selectedCurveBokorysId == curve.id || state.selectedSolidOfRevolutionId == solidId },
                    onClick = {
                        if (clearAllOnClick) clearSelection(state)
                        state.selectedCurveBokorysId = curve.id
                    }
                )
            )
        }

    state.arcsBokorys
        .asSequence()
        .filter { it.id.startsWith(SOR_BOKORYS_MERIDIAN_ID_PREFIX) && it.id.contains(solidId) }
        .filter { it.showInAxo }
        .forEachIndexed { index, arc ->
            add(
                ChildResolved(
                    kind = ChildKind.ARC,
                    key = "arcBokorys:${arc.id}",
                    sortIndex = sortKeyDesc(arc.creationIndex) + index,
                    name = arc.name.ifBlank { "Bokorysný meridián" },
                    color = arc.color,
                    is3D = false,
                    icon = ObjectListIcon.Arc,
                    isSelected = { state.selectedArcsBokorys.any { it.id == arc.id } || state.selectedSolidOfRevolutionId == solidId },
                    onClick = {
                        if (clearAllOnClick) clearSelection(state)
                        toggleSelectionBokorysArc(arc, state)
                    }
                )
            )
        }

    state.segmentsBokorys
        .asSequence()
        .filter { it.id.startsWith(SOR_BOKORYS_MERIDIAN_ID_PREFIX) && it.id.contains(solidId) }
        .filter { it.showInAxo }
        .forEachIndexed { index, seg ->
            add(
                ChildResolved(
                    kind = ChildKind.SEGMENT,
                    key = "segBokorys:${seg.id}",
                    sortIndex = sortKeyDesc(seg.effectiveCreationIndex) + index,
                    name = (seg.name ?: "").ifBlank { "Bokorysný meridián" },
                    color = seg.color,
                    is3D = false,
                    icon = ObjectListIcon.Segment,
                    isSelected = { state.selectedSegmentsBokorys.any { it.id == seg.id } || state.selectedSolidOfRevolutionId == solidId },
                    onClick = {
                        if (clearAllOnClick) clearSelection(state)
                        toggleSelectionBokorysSegment(seg, state)
                    }
                )
            )
        }
}

private fun isNarysObjectVisible(state: MongeState, id: String): Boolean =
    state.segmentsNarys.any { it.id == id && it.showInAxo } ||
        state.arcsNarys.any { it.id == id && it.showInAxo } ||
        state.conicsNarys.any { it.id == id && it.showInAxo } ||
        state.curvesNarys.any { it.id == id && it.showInAxo } ||
        state.circlesNarys.any { it.id == id && it.showInAxo }

private fun isPudorysObjectVisible(state: MongeState, id: String): Boolean =
    state.segmentsPudorys.any { it.id == id && it.showInAxo } ||
        state.arcsPudorys.any { it.id == id && it.showInAxo } ||
        state.conicsPudorys.any { it.id == id && it.showInAxo } ||
        state.curvesPudorys.any { it.id == id && it.showInAxo } ||
        state.circlesPudorys.any { it.id == id && it.showInAxo }

private fun isConicProjectionVisible(state: MongeState, conic3dId: String): Boolean =
    state.conicsPudorys.any { (it.parent?.id ?: it.parentId) == conic3dId && it.showInAxo } ||
        state.conicsNarys.any { (it.parent?.id ?: it.parentId) == conic3dId && it.showInAxo } ||
        state.conicsBokorys.any { (it.parent?.id ?: it.parentId) == conic3dId && it.showInAxo } ||
        state.conicsAxo.any { (it.parent?.id ?: it.parentId) == conic3dId && it.showInAxo }

private fun resolveNarysObjectById(
    state: MongeState,
    id: String,
    clearAllOnClick: Boolean,
): ResolvedObj? {

    // 1) ÚSEČKY (Segment2DNarys)
    val segments = state.segmentsNarys + state.helpSegmentsNarys
    segments.firstOrNull { it.id == id }?.let { s ->
        return ResolvedObj(
            key = "seg2d:n:${s.id}",
            sortIndex = when (s){
                is Segment2DNarys -> sortKeyDesc(s.effectiveCreationIndex)
                is HelpSegmentNarys -> (sortKeyDesc(s.creationIndex))
                else -> {0}
            },
            name = (s.name?.ifBlank { "Úsečka" } ?: "Úsečka") + "₂",
            color = s.color,
            is3D = false,
            icon = ObjectListIcon.Segment,
            isSelected = { state.selectedSegmentsNarys.contains(s) },
            onClick = {
                if (clearAllOnClick) clearSelection(state)
                toggleSelectionNarysSegment(s, state)
            }
        )
    }

    // 2) KRUŽNICE (Circle Narys) – pokud máš circle list ve state
    state.circlesNarys.firstOrNull { it.id == id }?.let { c ->
        return ResolvedObj(
            key = "circle:n:${c.id}",
            sortIndex = sortKeyDesc(c.effectiveCreationIndex),
            name = cleanTypedName("Kružnice", c.name, "₂"),
            color = c.localColor ?: Color.Black,
            is3D = false,
            icon = ObjectListIcon.Circle,
            isSelected = { state.selectedCirclesNarys.contains(c) },
            onClick = {
                if (clearAllOnClick) clearSelection(state)
                toggleSelectionNarysCircle(c, state)
            }
        )
    }

    // 3) KUŽELOSEČKY (Conic Narys) – pokud máš conicsNarys
    state.conicsNarys.firstOrNull { it.id == id }?.let { c ->
        val type = when {
            state.hyperbolaInputsNarys.containsKey(c.id) -> "Hyperbola"
            state.conicInputPointsNarys[c.id]?.third == Offset.Unspecified -> "Parabola"
            else -> "Elipsa"
        }
        return ResolvedObj(
            key = "conic2d:n:${c.id}",
            sortIndex = sortKeyDesc(c.effectiveCreationIndex),
            name = "$type ${c.name}₂",
            color = c.localColor ?: Color.Black,
            is3D = false,
            icon = when (type) {
                "Hyperbola" -> ObjectListIcon.Hyperbola
                "Parabola" -> ObjectListIcon.Parabola
                else -> ObjectListIcon.Ellipse
            },
            isSelected = { state.selectedConicsNarys.contains(c) },
            onClick = {
                if (clearAllOnClick) clearSelection(state)
                toggleSelectionNarysConic(c, state)
            }
        )
    }

    // 4) KŘIVKY (Curve Narys) – pokud máš curvesNarys
    state.curvesNarys.firstOrNull { it.id == id }?.let { c ->
        return ResolvedObj(
            key = "curve2d:n:${c.id}",
            sortIndex = sortKeyDesc(c.creationIndex),
            name = "Křivka₂ ${c.name}",
            color = c.color,
            is3D = false,
            icon = ObjectListIcon.Curve,
            isSelected = { state.selectedCurveNarysId == c.id },
            onClick = {
                if (clearAllOnClick) clearSelection(state)
                toggleSelectionCurveNarys(state, c.id)
            }
        )
    }


    return null
}
private fun resolvePudorysObjectById(
    state: MongeState,
    id: String,
    clearAllOnClick: Boolean,
): ResolvedObj? {

    // 1) ÚSEČKY (Segment2DNarys)
    val segments = state.segmentsPudorys + state.helpSegmentsPudorys
    segments.firstOrNull { it.id == id }?.let { s ->
        return ResolvedObj(
            key = "seg2d:n:${s.id}",
            sortIndex = when (s){
                is Segment2DPudorys -> sortKeyDesc(s.effectiveCreationIndex)
                is HelpSegmentPudorys -> (sortKeyDesc(s.creationIndex))
                else -> {0}
            },
            name = (s.name?.ifBlank { "Úsečka" } ?: "Úsečka") + "₂",
            color = s.color,
            is3D = false,
            icon = ObjectListIcon.Segment,
            isSelected = { state.selectedSegmentsPudorys.contains(s) },
            onClick = {
                if (clearAllOnClick) clearSelection(state)
                toggleSelectionPudorysSegment(s, state)
            }
        )
    }

    // 2) KRUŽNICE (Circle Narys) – pokud máš circle list ve state
    state.circlesPudorys.firstOrNull { it.id == id }?.let { c ->
        return ResolvedObj(
            key = "circle:n:${c.id}",
            sortIndex = sortKeyDesc(c.effectiveCreationIndex),
            name = cleanTypedName("Kružnice", c.name, "₂"),
            color = c.localColor ?: Color.Black,
            is3D = false,
            icon = ObjectListIcon.Circle,
            isSelected = { state.selectedCirclesPudorys.contains(c) },
            onClick = {
                if (clearAllOnClick) clearSelection(state)
                toggleSelectionPudorysCircle(c, state)
            }
        )
    }

    // 3) KUŽELOSEČKY (Conic Narys) – pokud máš conicsNarys
    state.conicsPudorys.firstOrNull { it.id == id }?.let { c ->
        val type = when {
            state.hyperbolaInputsPudorys.containsKey(c.id) -> "Hyperbola"
            state.conicInputPointsPudorys[c.id]?.third == Offset.Unspecified -> "Parabola"
            else -> "Elipsa"
        }
        return ResolvedObj(
            key = "conic2d:n:${c.id}",
            sortIndex = sortKeyDesc(c.effectiveCreationIndex),
            name = "$type ${c.name}₂",
            color = c.localColor ?: Color.Black,
            is3D = false,
            icon = when (type) {
                "Hyperbola" -> ObjectListIcon.Hyperbola
                "Parabola" -> ObjectListIcon.Parabola
                else -> ObjectListIcon.Ellipse
            },
            isSelected = { state.selectedConicsPudorys.contains(c) },
            onClick = {
                if (clearAllOnClick) clearSelection(state)
                toggleSelectionPudorysConic(c, state)
            }
        )
    }

    // 4) KŘIVKY (Curve Narys) – pokud máš curvesNarys
    state.curvesPudorys.firstOrNull { it.id == id }?.let { c ->
        return ResolvedObj(
            key = "curve2d:n:${c.id}",
            sortIndex = sortKeyDesc(c.creationIndex),
            name = "Křivka ${c.name}",
            color = c.color,
            is3D = false,
            icon = ObjectListIcon.Curve,
            isSelected = { state.selectedCurvePudorysId == c.id },
            onClick = {
                if (clearAllOnClick) clearSelection(state)
                toggleSelectionCurvePudorys(state, c.id)
            }
        )
    }


    return null
}

fun buildSolidOfRevolutionChildren(
    state: MongeState,
    solid: SolidOfRevolutionNarys,
    clearAllOnClick: Boolean,
): List<UiTreeItem> {

    val solidKey = "sor:${solid.id}"
    val children = buildList<ChildResolved> {

        // A) OSA (3D line)
        val axis3D = state.lines3D.firstOrNull { it.id == solid.axisLine3DId }
        if (axis3D != null) {
            add(
                ChildResolved(
                    kind = ChildKind.SEGMENT, // nebo udělej ChildKind.LINE, pokud chceš
                    key = "axis3d:${axis3D.id}",
                    sortIndex = sortKeyDesc(axis3D.creationIndex),
                    name = "Osa ${axis3D.name}",
                    color = axis3D.color,
                    is3D = true,
                    icon = ObjectListIcon.Line,
                    superscript = axis3D.superscript,
                    isSelected = { state.selectedLines3D.any { it.id == axis3D.id } },
                    onClick = {
                        if (clearAllOnClick) clearSelection(state)
                        toggleSelectionLine3D(axis3D, state) // uprav na svoji funkci
                    }
                )
            )
        }
        val arcIdsInRevolutions = (solid.meridianIdsNarys + solid.mirroredMeridianIdsNarys).toSet()
// --- OBLOUKY (NÁRYS) – jen ty, co nejsou součást rotačních ploch ---
        state.arcsNarys
            .asSequence()
            .filter { it.id in arcIdsInRevolutions }
            .filter { it.showInAxo }
            .forEach { a ->
                val nm = cleanTypedName("Oblouk", a.name, "₂")
                add(
                    ChildResolved(
                        key = "arc:n:${a.id}",
                        sortIndex = sortKeyDesc(a.creationIndex),
                        name = nm,
                        color = a.color,
                        is3D = false,
                        icon = ObjectListIcon.Arc,
                        isSelected = { state.selectedArcsNarys.contains(a) }, // nebo podle IDs
                        onClick = {
                            if (clearAllOnClick) clearSelection(state)
                            toggleSelectionNarysArc(a, state)
                        },
                        kind = ChildKind.ARC
                    )
                )
            }

        // B) POLEDNÍK (NÁRYS) + případně zrcadlený
        val allMeridianIds = solid.meridianIdsNarys + solid.mirroredMeridianIdsNarys
        allMeridianIds.forEach { oid ->
            if (!isNarysObjectVisible(state, oid)) return@forEach
            val r = resolveNarysObjectById(state, oid, clearAllOnClick) ?: return@forEach
            add(
                ChildResolved(
                    kind = ChildKind.POINT, // jen kvůli řazení – klidně zaveď ChildKind.MERIDIAN
                    key = "mer:n:${r.key}",
                    sortIndex = r.sortIndex,
                    name = "Poledník: ${r.name}",
                    color = r.color,
                    is3D = r.is3D,
                    superscript = r.superscript,
                    subscript = r.subscript,
                    icon = r.icon,
                    isSelected = r.isSelected,
                    onClick = r.onClick
                )
            )
        }

        addSoRAxoContourChildren(state, solid.id, clearAllOnClick)
        addSoRBokorysMeridianChildren(state, solid.id, clearAllOnClick)

        // C+D) kružnice generované – sjednotit do 3D kuželoseček (parent)
        run {
            val parentConicIds = linkedSetOf<String>() // pořadí + dedup

            fun addParentFromPudorys(id: String) {
                // primárně jsou to projekce 3D kuželoseček (elipsy atd.)
                val con = state.conicsPudorys.firstOrNull { it.id == id }
                if (con != null) {
                    (con.parent?.id ?: con.parentId)?.let(parentConicIds::add)
                    // nebo: con.parentId?.let(parentConicIds::add)
                    return
                }

                // fallback: kdyby někdy opravdu šlo o Circle2D
                val cir = state.circlesPudorys.firstOrNull { it.id == id } ?: return
                cir.parentId?.let(parentConicIds::add)
            }

            fun addParentFromNarys(id: String) {
                val con = state.conicsNarys.firstOrNull { it.id == id }
                if (con != null) {
                    (con.parent?.id ?: con.parentId)?.let(parentConicIds::add)
                    return
                }

                val cir = state.circlesNarys.firstOrNull { it.id == id } ?: return
                cir.parentId?.let(parentConicIds::add)
            }

            // 1) z ID projekcí vytáhni parent 3D conic
            solid.circleIdsPudorys.forEach(::addParentFromPudorys)
            solid.circleIdsNarys.forEach(::addParentFromNarys)

            // 2) vytvoř položky jako 3D conic
            parentConicIds.forEach { conic3dId ->
                if (!isConicProjectionVisible(state, conic3dId)) return@forEach
                val conic3D = state.conics3D.firstOrNull { it.id == conic3dId } ?: return@forEach
                val label = cleanTypedName("Kružnice", conic3D.name)

                add(
                    ChildResolved(
                        kind = ChildKind.CONIC,
                        key = "conic3d:${conic3D.id}",
                        sortIndex = sortKeyDesc(conic3D.creationIndex),
                        name = label,
                        color = conic3D.color,
                        is3D = true,
                        icon = ObjectListIcon.Circle,
                        isSelected = { isConic3DSelected(state, conic3D.id) },
                        onClick = {
                            if (clearAllOnClick) clearSelection(state)
                            toggleSelectionConic3D(conic3D, state)
                        }
                    )
                )
            }
        }
    }

    val unique = children.distinctBy { it.key }
    val sorted = unique.sortedWith(compareBy<ChildResolved>({ it.kind.ordinal }, { it.sortIndex }))

    return sorted.map { r ->
        UiTreeItem(
            key = "$solidKey/${r.key}",
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
fun buildSolidOfRevolutionChildren(
    state: MongeState,
    solid: SolidOfRevolutionPudorys,
    clearAllOnClick: Boolean,
): List<UiTreeItem> {

    val solidKey = "sor:${solid.id}"
    val children = buildList<ChildResolved> {

        // A) OSA (3D line)
        val axis3D = state.lines3D.firstOrNull { it.id == solid.axisLine3DId }
        if (axis3D != null) {
            add(
                ChildResolved(
                    kind = ChildKind.SEGMENT,
                    key = "axis3d:${axis3D.id}",
                    sortIndex = sortKeyDesc(axis3D.creationIndex),
                    name = "Osa ${axis3D.name}",
                    color = axis3D.color,
                    is3D = true,
                    icon = ObjectListIcon.Line,
                    superscript = axis3D.superscript,
                    isSelected = { state.selectedLines3D.any { it.id == axis3D.id } },
                    onClick = {
                        if (clearAllOnClick) clearSelection(state)
                        toggleSelectionLine3D(axis3D, state) // uprav na svoji funkci
                    }
                )
            )
        }
        val arcIdsInRevolutions = (solid.meridianIdsPudorys + solid.mirroredMeridianIdsPudorys).toSet()
// --- OBLOUKY (NÁRYS) – jen ty, co nejsou součást rotačních ploch ---
        state.arcsPudorys
            .asSequence()
            .filter { it.id in arcIdsInRevolutions }
            .filter { it.showInAxo }
            .forEach { a ->
                val nm = cleanTypedName("Oblouk", a.name, "₁")
                add(
                    ChildResolved(
                        key = "arc:n:${a.id}",
                        sortIndex = sortKeyDesc(a.creationIndex),
                        name = nm,
                        color = a.color,
                        is3D = false,
                        icon = ObjectListIcon.Arc,
                        isSelected = { state.selectedArcsPudorys.contains(a) }, // nebo podle IDs
                        onClick = {
                            if (clearAllOnClick) clearSelection(state)
                            toggleSelectionPudorysArc(a, state)
                        },
                        kind = ChildKind.ARC
                    )
                )
            }

        // B) POLEDNÍK (NÁRYS) + případně zrcadlený
        val allMeridianIds = solid.meridianIdsPudorys + solid.mirroredMeridianIdsPudorys
        allMeridianIds.forEach { oid ->
            if (!isPudorysObjectVisible(state, oid)) return@forEach
            val r = resolvePudorysObjectById(state, oid, clearAllOnClick) ?: return@forEach
            add(
                ChildResolved(
                    kind = ChildKind.POINT, // jen kvůli řazení – klidně zaveď ChildKind.MERIDIAN
                    key = "mer:n:${r.key}",
                    sortIndex = r.sortIndex,
                    name = "Poledník: ${r.name}",
                    color = r.color,
                    is3D = r.is3D,
                    superscript = r.superscript,
                    subscript = r.subscript,
                    icon = r.icon,
                    isSelected = r.isSelected,
                    onClick = r.onClick
                )
            )
        }

        addSoRAxoContourChildren(state, solid.id, clearAllOnClick)
        addSoRBokorysMeridianChildren(state, solid.id, clearAllOnClick)

        // C+D) kružnice generované – sjednotit do 3D kuželoseček (parent)
        run {
            val parentConicIds = linkedSetOf<String>() // pořadí + dedup

            fun addParentFromPudorys(id: String) {
                // primárně jsou to projekce 3D kuželoseček (elipsy atd.)
                val con = state.conicsPudorys.firstOrNull { it.id == id }
                if (con != null) {
                    (con.parent?.id ?: con.parentId)?.let(parentConicIds::add)
                    // nebo: con.parentId?.let(parentConicIds::add)
                    return
                }

                // fallback: kdyby někdy opravdu šlo o Circle2D
                val cir = state.circlesPudorys.firstOrNull { it.id == id } ?: return
                cir.parentId?.let(parentConicIds::add)
            }

            fun addParentFromNarys(id: String) {
                val con = state.conicsNarys.firstOrNull { it.id == id }
                if (con != null) {
                    (con.parent?.id ?: con.parentId)?.let(parentConicIds::add)
                    return
                }

                val cir = state.circlesNarys.firstOrNull { it.id == id } ?: return
                cir.parentId?.let(parentConicIds::add)
            }

            // 1) z ID projekcí vytáhni parent 3D conic
            solid.circleIdsPudorys.forEach(::addParentFromPudorys)
            solid.circleIdsNarys.forEach(::addParentFromNarys)

            // 2) vytvoř položky jako 3D conic
            parentConicIds.forEach { conic3dId ->
                if (!isConicProjectionVisible(state, conic3dId)) return@forEach
                val conic3D = state.conics3D.firstOrNull { it.id == conic3dId } ?: return@forEach
                val label = cleanTypedName("Kružnice", conic3D.name)

                add(
                    ChildResolved(
                        kind = ChildKind.CONIC,
                        key = "conic3d:${conic3D.id}",
                        sortIndex = sortKeyDesc(conic3D.creationIndex),
                        name = label,
                        color = conic3D.color,
                        is3D = true,
                        icon = ObjectListIcon.Circle,
                        isSelected = { isConic3DSelected(state, conic3D.id) },
                        onClick = {
                            if (clearAllOnClick) clearSelection(state)
                            toggleSelectionConic3D(conic3D, state)
                        }
                    )
                )
            }
        }
    }

    val unique = children.distinctBy { it.key }
    val sorted = unique.sortedWith(compareBy<ChildResolved>({ it.kind.ordinal }, { it.sortIndex }))

    return sorted.map { r ->
        UiTreeItem(
            key = "$solidKey/${r.key}",
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
fun selectSolidOfRevolutionAll(
    state: MongeState,
    solid: SolidOfRevolutionNarys,
    clearAllOnClick: Boolean,
) {
    if (clearAllOnClick) clearSelection(state)

    // 1) vyber samotnou plochu
    state.selectedSolidOfRevolutionId = solid.id // nebo add do listu – podle tebe


    // 3) poledník (narys ids + mirrored)
    val allMeridianIds = solid.meridianIdsNarys + solid.mirroredMeridianIdsNarys
    allMeridianIds.forEach { oid ->
        // a) segmenty narys
        state.segmentsNarys.firstOrNull { it.id == oid }?.let { s ->
            if (!state.selectedSegmentsNarys.contains(s)) toggleSelectionNarysSegment(s, state)
            return@forEach
        }
        state.helpSegmentsNarys.firstOrNull { it.id == oid }?.let { s ->
            if (!state.selectedSegmentsNarys.contains(s)) toggleSelectionNarysSegment(s, state)
            return@forEach
        }
        // b) conics narys
        state.conicsNarys.firstOrNull { it.id == oid }?.let { c ->
            if (!state.selectedConicsNarys.contains(c)) toggleSelectionNarysConic(c, state)
            return@forEach
        }
        // c) circles narys
        state.circlesNarys.firstOrNull { it.id == oid }?.let { c ->
            if (!state.selectedCirclesNarys.contains(c)) toggleSelectionNarysCircle(c, state)
            return@forEach
        }
        // d) curves narys
        state.curvesNarys.firstOrNull { it.id == oid }?.let { c ->
            if (state.selectedCurveNarysId != c.id) toggleSelectionCurveNarys(state, c.id)
            return@forEach
        }
        state.arcsNarys.firstOrNull { it.id == oid }?.let { a ->
            if (!state.selectedArcsNarys.contains(a)) state.selectedArcsNarys.add(a)
            return@forEach
        }
    }
// 4) kružnice (půdorys + nárys) -> sjednotit do 3D kuželoseček (parentů)
    run {
        val parentIds = linkedSetOf<String>()

        solid.circleIdsPudorys.forEach { cid ->
            val c = state.conicsPudorys.firstOrNull { it.id == cid } ?: return@forEach
            c.parent?.id?.let(parentIds::add)
        }

        solid.circleIdsNarys.forEach { cid ->
            val cN = state.conicsNarys.firstOrNull { it.id == cid }
            if (cN != null) {
                cN.parent?.id?.let(parentIds::add)
                return@forEach
            }
            val circleN = state.circlesNarys.firstOrNull { it.id == cid } ?: return@forEach
            circleN.parentId?.let(parentIds::add)
        }

        parentIds.forEach { pid ->
            // clearAllOnClick = false, protože už jsi clear udělal nahoře
            selectConic3DProjections(state, pid, clearAllOnClick = false)
        }
    }

    state.arcsBokorys
        .filter { it.id.startsWith(SOR_BOKORYS_MERIDIAN_ID_PREFIX) && it.id.contains(solid.id) }
        .forEach { arc ->
            if (state.selectedArcsBokorys.none { it.id == arc.id }) state.selectedArcsBokorys.add(arc)
        }
    state.segmentsBokorys
        .filter { it.id.startsWith(SOR_BOKORYS_MERIDIAN_ID_PREFIX) && it.id.contains(solid.id) }
        .forEach { segment ->
            if (state.selectedSegmentsBokorys.none { it.id == segment.id }) state.selectedSegmentsBokorys.add(segment)
        }

}
fun selectSolidOfRevolutionAll(
    state: MongeState,
    solid: SolidOfRevolutionPudorys,
    clearAllOnClick: Boolean,
) {
    if (clearAllOnClick) clearSelection(state)

    // 1) vyber samotnou plochu
    state.selectedSolidOfRevolutionId = solid.id



    // 3) poledník (narys ids + mirrored)
    val allMeridianIds = solid.meridianIdsPudorys + solid.mirroredMeridianIdsPudorys
    allMeridianIds.forEach { oid ->
        // a) segmenty narys
        state.segmentsPudorys.firstOrNull { it.id == oid }?.let { s ->
            if (!state.selectedSegmentsPudorys.contains(s)) toggleSelectionPudorysSegment(s, state)
            return@forEach
        }
        state.helpSegmentsPudorys.firstOrNull { it.id == oid }?.let { s ->
            if (!state.selectedSegmentsPudorys.contains(s)) toggleSelectionPudorysSegment(s, state)
            return@forEach
        }
        // b) conics narys
        state.conicsPudorys.firstOrNull { it.id == oid }?.let { c ->
            if (!state.selectedConicsPudorys.contains(c)) toggleSelectionPudorysConic(c, state)
            return@forEach
        }
        // c) circles narys
        state.circlesPudorys.firstOrNull { it.id == oid }?.let { c ->
            if (!state.selectedCirclesPudorys.contains(c)) toggleSelectionPudorysCircle(c, state)
            return@forEach
        }
        // d) curves narys
        state.curvesPudorys.firstOrNull { it.id == oid }?.let { c ->
            if (state.selectedCurvePudorysId != c.id) toggleSelectionCurvePudorys(state, c.id)
            return@forEach
        }
        state.arcsPudorys.firstOrNull { it.id == oid }?.let { a ->
            if (!state.selectedArcsPudorys.contains(a)) state.selectedArcsPudorys.add(a)
            return@forEach
        }
    }
// 4) kružnice (půdorys + nárys) -> sjednotit do 3D kuželoseček (parentů)
    run {
        val parentIds = linkedSetOf<String>()

        solid.circleIdsPudorys.forEach { cid ->
            val c = state.conicsPudorys.firstOrNull { it.id == cid } ?: return@forEach
            c.parent?.id?.let(parentIds::add)
        }

        solid.circleIdsNarys.forEach { cid ->
            val cN = state.conicsNarys.firstOrNull { it.id == cid }
            if (cN != null) {
                cN.parent?.id?.let(parentIds::add)
                return@forEach
            }
            val circleN = state.circlesNarys.firstOrNull { it.id == cid } ?: return@forEach
            circleN.parentId?.let(parentIds::add)
        }

        parentIds.forEach { pid ->
            // clearAllOnClick = false, protože už jsi clear udělal nahoře
            selectConic3DProjections(state, pid, clearAllOnClick = false)
        }
    }

    state.arcsBokorys
        .filter { it.id.startsWith(SOR_BOKORYS_MERIDIAN_ID_PREFIX) && it.id.contains(solid.id) }
        .forEach { arc ->
            if (state.selectedArcsBokorys.none { it.id == arc.id }) state.selectedArcsBokorys.add(arc)
        }
    state.segmentsBokorys
        .filter { it.id.startsWith(SOR_BOKORYS_MERIDIAN_ID_PREFIX) && it.id.contains(solid.id) }
        .forEach { segment ->
            if (state.selectedSegmentsBokorys.none { it.id == segment.id }) state.selectedSegmentsBokorys.add(segment)
        }

}
