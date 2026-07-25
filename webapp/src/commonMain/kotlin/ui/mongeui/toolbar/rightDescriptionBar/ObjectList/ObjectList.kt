package ui.mongeui.toolbar.rightDescriptionBar.ObjectList

import monge.input.selection.isConic3DSelected
import monge.input.selection.selectConic3DProjections
import monge.input.intersections.INTERSECTION_RESULT_COLOR
import monge.input.intersections.intersectionGroupedIds
import monge.input.intersections.selectIntersectionGroup
import model.SOR_BOKORYS_MERIDIAN_ID_PREFIX
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import ui.resources.painterResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import draw.mongescreen.labels.clearSelection
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import model.*
import model.classes.*
import monge.input.ConicArcs.associated.arcEllipseNarys3D
import monge.input.ConicArcs.associated.arcEllipsePudorys3D
import monge.input.ConicArcs.associated.arcHyperbolaNarys3D
import monge.input.ConicArcs.associated.arcHyperbolaPudorys3D
import monge.input.ConicArcs.associated.arcParabolaNarys3D
import monge.input.ConicArcs.associated.arcParabolaPudorys3D

import monge.input.ConicArcs.single.arcEllipseNarys
import monge.input.ConicArcs.single.arcEllipsePudorys
import monge.input.ConicArcs.single.arcHyperbolaNarys
import monge.input.ConicArcs.single.arcHyperbolaPudorys
import monge.input.ConicArcs.single.arcParabolaNarys
import monge.input.ConicArcs.single.arcParabolaPudorys
import monge.input.ConicArcs.single.decideConicNarys
import monge.input.ConicArcs.single.decideConicPudorys






import monge.input.selection.*
import serialization.SettingsManager
import model.classes.isAxisProjection
import model.classes.isAxoPlane
import state.MongeState
import ui.mongeui.toolbar.rightDescriptionBar.isProjectedLinePoint
import ui.mongeui.toolbar.rightDescriptionBar.isProjectedLinePointOf
import ui.theme.LocalMongeDimens
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

sealed class ObjectListIcon {
    data object Point : ObjectListIcon()
    data object Line : ObjectListIcon()
    data object Segment : ObjectListIcon()
    data object Plane : ObjectListIcon()
    data object Cone : ObjectListIcon()
    data object Sphere : ObjectListIcon()
    data object Cylinder : ObjectListIcon()
    data object Arc : ObjectListIcon()
    data object Prism : ObjectListIcon()
    data object Pyramid : ObjectListIcon()
    data class Polygon(val sides: Int) : ObjectListIcon()
    data object Circle : ObjectListIcon()
    data object Ellipse : ObjectListIcon()
    data object Parabola : ObjectListIcon()
    data object Hyperbola : ObjectListIcon()
    data object SolidOfRevolution : ObjectListIcon()
    data object Curve : ObjectListIcon()
    data object Intersection : ObjectListIcon()
    data object Fallback : ObjectListIcon()
}

data class UiTreeItem(
    val key: String,
    val sortIndex: Long,
    val name: String,
    val color: Color,
    val is3D: Boolean,
    val superscript: String? = null,
    val subscript: String? = null,
    val icon: ObjectListIcon? = null,
    val isSelected: () -> Boolean,
    val onClick: () -> Unit,
    val children: List<UiTreeItem> = emptyList(),
)

data class UiRow(
    val item: UiTreeItem,
    val depth: Int,
    val isExpandable: Boolean,
    val isExpanded: Boolean,
    // barva kořenového (top-level) předka – používá se pro vodicí linky, aby bylo
    // na první pohled jasné, ke kterému objektu daný child/grandchild patří
    val rootColor: Color,
    // true pro potomky uzlu, který se právě zabaluje – řádek zůstává chvíli
    // v seznamu, aby mohl doanimovat zmizení (fade + shrink) místo náhlého vymizení
    val isClosing: Boolean,
)
fun flattenTree(
    roots: List<UiTreeItem>,
    expandedKeys: SnapshotStateList<String>,
    collapsingKeys: List<String>,
): List<UiRow> {
    val out = ArrayList<UiRow>(roots.size * 2)

    fun dfs(node: UiTreeItem, depth: Int, rootColor: Color, ancestorClosing: Boolean) {
        val expandable = node.children.isNotEmpty()
        val expanded = expandable && expandedKeys.contains(node.key)
        // uzel je "zavírající se" buď proto, že se právě zabalil on sám (je v collapsingKeys),
        // nebo proto, že se zabaluje některý z jeho předků – v obou případech mají jeho
        // potomci ještě chvíli zůstat vykreslení kvůli exit animaci
        val nodeClosing = ancestorClosing || collapsingKeys.contains(node.key)

        out += UiRow(
            item = node,
            depth = depth,
            isExpandable = expandable,
            isExpanded = expanded,
            rootColor = rootColor,
            isClosing = ancestorClosing
        )

        if (expandable && (expanded || nodeClosing)) {
            node.children
                .sortedWith(
                    compareByDescending<UiTreeItem> { it.sortIndex }
                        .thenBy { it.name }
                )
                .forEach { child -> dfs(child, depth + 1, rootColor, nodeClosing) }
        }
    }

    roots
        .sortedWith(
            compareByDescending<UiTreeItem> { it.sortIndex }
                .thenBy { it.name }
        )
        .forEach { dfs(it, 0, it.color, false) }

    return out
}
fun sortKeyDesc(idx: Long): Long =
    if (idx < 0L) Long.MIN_VALUE else idx

private fun conicDiscriminant2D(a: Float, b: Float, c: Float): Float = b * b - 4f * a * c

private fun isHyperbola2D(a: Float, b: Float, c: Float): Boolean =
    conicDiscriminant2D(a, b, c) > 1e-5f

fun objectListIconFromText(key: String, name: String): ObjectListIcon {
    val text = "$key $name".lowercase()
    return when {
        text.startsWith("pt") || text.contains("bod") -> ObjectListIcon.Point
        text.contains("přímka") || text.contains("primka") || text.contains("line:") || text.contains("helpline") -> ObjectListIcon.Line
        text.contains("úsečka") || text.contains("usecka") || text.contains("seg:") || text.contains("segment") -> ObjectListIcon.Segment
        text.contains("stopa") || text.contains("rovina") || text.contains("plane:") || text.contains("trace:") -> ObjectListIcon.Plane
        text.contains("kužel") || text.contains("cone:") -> ObjectListIcon.Cone
        text.contains("koule") || text.contains("kulová") || text.contains("sphere:") -> ObjectListIcon.Sphere
        text.contains("válec") || text.contains("cyl:") -> ObjectListIcon.Cylinder
        text.contains("oblouk") || text.contains("arc:") -> ObjectListIcon.Arc
        text.contains("křivka") || text.contains("curve") -> ObjectListIcon.Curve
        text.contains("rotační plocha") || text.contains("sor:") -> ObjectListIcon.SolidOfRevolution
        text.contains("kružnice") || text.contains("circle:") -> ObjectListIcon.Circle
        text.contains("elipsa") -> ObjectListIcon.Ellipse
        text.contains("parabola") -> ObjectListIcon.Parabola
        text.contains("hyperbola") -> ObjectListIcon.Hyperbola
        text.contains("trojúhelník") -> ObjectListIcon.Polygon(3)
        text.contains("čtverec") -> ObjectListIcon.Polygon(4)
        text.contains("úhelník") -> {
            val sides = Regex("""(\d+)-úhelník""").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 6
            ObjectListIcon.Polygon(sides)
        }
        else -> ObjectListIcon.Fallback
    }
}

@Composable
fun ObjectListTypeIcon(
    icon: ObjectListIcon,
    color: Color,
    modifier: Modifier = Modifier,
    ui: Float = SettingsManager.current.UIscale / 75f
) {
    val sizeModifier = modifier.size(16f * ui.dp)
    when (icon) {
        ObjectListIcon.Point -> CrossObjectIcon(color, sizeModifier)
        is ObjectListIcon.Polygon -> PolygonObjectIcon(icon.sides, color, sizeModifier)
        ObjectListIcon.Fallback -> Box(
            modifier = sizeModifier
                .clip(RoundedCornerShape(50))
                .background(color)
        )
        else -> Image(
            painter = painterResource(icon.resourcePath()),
            contentDescription = null,
            colorFilter = ColorFilter.tint(color),
            modifier = sizeModifier
        )
    }
}

private fun ObjectListIcon.resourcePath(): String {

    return when (this) {
        ObjectListIcon.Line -> "icons/primka.svg"
        ObjectListIcon.Segment -> "icons/usecka.svg"
        ObjectListIcon.Plane -> "icons/rovina.svg"
        ObjectListIcon.Cone -> "icons/cone.svg"
        ObjectListIcon.Sphere -> "icons/sphere.svg"
        ObjectListIcon.Cylinder -> "icons/cylinder.svg"
        ObjectListIcon.Arc -> "icons/spline.svg"
        ObjectListIcon.Prism -> "icons/rectangular-prism.svg"
        ObjectListIcon.Pyramid -> "icons/brand-prisma.svg"
        ObjectListIcon.Circle -> "icons/circle.svg"
        ObjectListIcon.Ellipse -> "icons/ellipse.svg"
        ObjectListIcon.Parabola -> "icons/parabola.svg"
        ObjectListIcon.Hyperbola -> "icons/hyperbola.svg"
        ObjectListIcon.SolidOfRevolution -> "icons/rot.svg"
        ObjectListIcon.Curve -> "icons/curves.svg"
        ObjectListIcon.Intersection -> "icons/intersect.svg"
        else -> "icons/point.svg"
    }

}

@Composable
private fun CrossObjectIcon(color: Color, modifier: Modifier) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = size.minDimension * 0.16f)
        val pad = size.minDimension * 0.16f
        drawLine(color, Offset(size.width / 2f, pad), Offset(size.width / 2f, size.height - pad), strokeWidth = stroke.width)
        drawLine(color, Offset(pad, size.height / 2f), Offset(size.width - pad, size.height / 2f), strokeWidth = stroke.width)
    }
}

@Composable
private fun PolygonObjectIcon(sides: Int, color: Color, modifier: Modifier) {
    Canvas(modifier = modifier) {
        val count = sides.coerceAtLeast(3)
        val radius = min(size.width, size.height) * 0.42f
        val center = Offset(size.width / 2f, size.height / 2f)
        val path = Path()
        repeat(count) { i ->
            val angle = (-kotlin.math.PI / 2.0 + i * 2.0 * kotlin.math.PI / count).toFloat()
            val point = Offset(center.x + cos(angle) * radius, center.y + sin(angle) * radius)
            if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        path.close()
        drawPath(path, color, style = Stroke(width = size.minDimension * 0.12f))
    }
}

// Umožňuje vybrat kuželosečku/kružnici pro rozestavěnou konstrukci CONICARC/CONICARCAS
// i kliknutím v ObjectListu, ne jen klikem do výkresu.
// Klik do canvasu dělá v jednom handleClick dva kroky najednou (viz MongeClickHandlers.kt):
// 1) decideConicPudorys/Narys přepne "..._start" fázi na "..._elip_start"/"..._par_start"/...
// 2) arc*Pudorys/Narys ihned zpracuje tuto novou fázi a uzamkne activeConicIdForArc (fáze "..._arc_hold").
// Z ObjectListu je potřeba oba kroky zopakovat ručně, jinak zůstane activeConicIdForArc = null
// a navazující klik do výkresu (bod A oblouku) je no-op.
private fun advanceConicArcSelectionIfNeeded(state: MongeState) {
    if (!state.isConicArcSelectionMode()) return
    // AXO má vlastní dispatcher (podle state.axoMode, ne state.mongeMode) - deleguj na něj,
    // ať se nezdvojuje logika fází pudorys/narys/bokorys/axo (viz monge/input/axo/ConicArcsAxo.kt).
    if (state.projectionMode == ProjectionMode.AXO) {
        when (state.drawobjects) {
            Mongeobjects.CONICARC -> Unit
            Mongeobjects.CONICARCAS -> Unit
            else -> {}
        }
        return
    }
    when {
        state.mongeMode == DrawingModeMonge.PUDORYS && state.projectionPhase == "pudorys_start" -> {
            decideConicPudorys(state)
            when (state.drawobjects) {
                Mongeobjects.CONICARC -> {
                    arcEllipsePudorys(state, null, Offset.Zero)
                    arcParabolaPudorys(state, null, Offset.Zero)
                    arcHyperbolaPudorys(state, null, Offset.Zero)
                }
                Mongeobjects.CONICARCAS -> {
                    arcEllipsePudorys3D(state, null, Offset.Zero)
                    arcParabolaPudorys3D(state, null, Offset.Zero)
                    arcHyperbolaPudorys3D(state, null, Offset.Zero)
                }
                else -> {}
            }
        }
        state.mongeMode == DrawingModeMonge.NARYS && state.projectionPhase == "narys_start" -> {
            decideConicNarys(state)
            when (state.drawobjects) {
                Mongeobjects.CONICARC -> {
                    arcEllipseNarys(state, null, Offset.Zero)
                    arcParabolaNarys(state, null, Offset.Zero)
                    arcHyperbolaNarys(state, null, Offset.Zero)
                }
                Mongeobjects.CONICARCAS -> {
                    arcEllipseNarys3D(state, null, Offset.Zero)
                    arcParabolaNarys3D(state, null, Offset.Zero)
                    arcHyperbolaNarys3D(state, null, Offset.Zero)
                }
                else -> {}
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ObjectList(state: MongeState, clearAllOnClick: Boolean)
    {

        val revolutionAxis3DIds: Set<String> =
            state.solidsOfRevolutionNarys.asSequence()
                .map { it.axisLine3DId }
                .filter { it.isNotBlank() }
                .toSet()
        LocalMongeColors.current
        val dimens = LocalMongeDimens.current
        val listState = rememberLazyListState()
        val showAxoProjections = showAxoProjectionChildren(state)
        val groupedIntersectionIds = intersectionGroupedIds(state)
        val ruledDirectrixIds = state.ruledSurfaces.flatMap { surface ->
            listOfNotNull(
                surface.firstBoundaryDirectrix.objectId,
                surface.secondBoundaryDirectrix.objectId,
                surface.thirdDirectrix?.objectId,
            ) + surface.generatorLineIds
        }.toSet()
        val ruledPlaneIds = state.ruledSurfaces.mapNotNull { it.directorPlaneId }.toSet()
        val meridianObjectIdsNarys: Set<String> =
            state.solidsOfRevolutionNarys.asSequence()
                .flatMap { (it.meridianIdsNarys + it.mirroredMeridianIdsNarys).asSequence() }
                .toSet()
        val meridianObjectIdsPudorys: Set<String> =
            state.solidsOfRevolutionPudorys.asSequence()
                .flatMap { (it.meridianIdsPudorys + it.mirroredMeridianIdsPudorys).asSequence() }
                .toSet()

        val meridianCircleIdsNarys2D: Set<String> =
            state.circlesNarys.asSequence()
                .map { it.id }
                .filter { it in meridianObjectIdsNarys }
                .toSet()
        val meridianCircleIdsPudorys2D: Set<String> =
            state.circlesPudorys.asSequence()
                .map { it.id }
                .filter { it in meridianObjectIdsPudorys }
                .toSet()

        val revolutionCircleIdsPudorys2D: Set<String> =
            state.solidsOfRevolutionNarys.asSequence()
                .flatMap { it.circleIdsPudorys.asSequence() }
                .toSet()

        val revolutionCircleIdsNarys2D: Set<String> =
            state.solidsOfRevolutionNarys.asSequence()
                .flatMap { it.circleIdsNarys.asSequence() }
                .toSet()
// Segmenty v nárysu, které jsou použité v meridiánu
        val meridianSegIdsNarys: Set<String> =
            state.segmentsNarys.asSequence()
                .map { it.id }
                .filter { it in meridianObjectIdsNarys }
                .toSet()
        val meridianSegIdsPudorys: Set<String> =
            state.segmentsPudorys.asSequence()
                .map { it.id }
                .filter { it in meridianObjectIdsPudorys}
                .toSet()

// (volitelně) pokud meridián obsahuje i helpSegmentsNarys
        val meridianHelpSegIdsNarys: Set<String> =
            state.helpSegmentsNarys.asSequence()
                .map { it.id }
                .filter { it in meridianObjectIdsNarys }
                .toSet()
        val meridianHelpSegIdsPudorys: Set<String> =
            state.helpSegmentsPudorys.asSequence()
                .map { it.id }
                .filter { it in meridianObjectIdsPudorys }
                .toSet()

        val meridianAllSegIdsNarys = meridianSegIdsNarys + meridianHelpSegIdsNarys
        val meridianAllSegIdsPudorys = meridianSegIdsPudorys + meridianHelpSegIdsPudorys
// 3D parent ID segmentů, které jsou v meridiánu (jen ty, co parent mají)
        val meridianSeg3DIds: Set<String> =
            (state.segmentsNarys.asSequence() + state.helpSegmentsNarys.asSequence())
                .filter { it.id in meridianObjectIdsNarys }
                .mapNotNull { it.parent?.id }
                .toSet()
        val meridianSeg3DIdsPud: Set<String> =
            (state.segmentsPudorys.asSequence() + state.helpSegmentsPudorys.asSequence())
                .filter { it.id in meridianObjectIdsPudorys }
                .mapNotNull { it.parent?.id }
                .toSet()
        val meridianSeg3DIdsAll = meridianSeg3DIds + meridianSeg3DIdsPud
        val polygonSeg3DIds: Set<String> =
            state.polygons3D.flatMap { it.segmentIds3D }.toHashSet()
        val solidSeg3DIds: Set<String> =
            state.segmentSolids3D.flatMap { it.segmentIds3D }.toHashSet()
        val solidPolygonIds: Set<String> =
            state.segmentSolids3D.flatMap { it.polygonIds }.toHashSet()
        val intersectionPolygonIds = intersectionPolygonIds(state)
        val groupedSeg3DIds = polygonSeg3DIds + solidSeg3DIds

        val polygonSegPudIds: Set<String> =
            state.polygons3D.flatMap { it.segmentIdsPudorys }.toHashSet()

        val polygonSegNarIds: Set<String> =
            state.polygons3D.flatMap { it.segmentIdsNarys }.toHashSet()
        val polygonSegAxoIds: Set<String> =
            state.polygons3D.flatMap { it.segmentIdsAxo }.toHashSet()
        val listedSegments =
            (state.segmentsBokorys
                .asSequence()
                .filterNot { it.parent?.id in groupedIntersectionIds.segment3DIds || it.parentId in groupedIntersectionIds.segment3DIds }
                .filterNot { it.isConicalSilhouette }
                .filterNot { it.id.startsWith(SOR_BOKORYS_MERIDIAN_ID_PREFIX) }
                .filterNot { it.parent?.id in meridianSeg3DIdsAll }
                .filterNot { seg2d ->
                    seg2d.parent?.id?.let { it in groupedSeg3DIds } ?: (seg2d.id in polygonSegPudIds)
                }
                .map {
                    val name = it.parent?.name?.ifBlank { null } ?: it.name?.ifBlank { null } ?: "Úsečka"
                    ListedSegment(name, it.parent, it.color, ListProjectionType.BOKORYS, source = it)
                }+
                    state.segmentsPudorys
                        .asSequence()
                        .filterNot { it.parent?.id in groupedIntersectionIds.segment3DIds || it.parentId in groupedIntersectionIds.segment3DIds }
                        .filterNot { it.isConicalSilhouette }
                        .filterNot { seg2d ->
                            seg2d.parent?.id?.let { it in groupedSeg3DIds } ?: (seg2d.id in polygonSegPudIds)
                        }
                        .filterNot { seg2d ->
                            (seg2d.id in meridianAllSegIdsPudorys) || (seg2d.parent?.id in meridianSeg3DIdsAll)
                        }
                        .map {
                            val name = it.parent?.name?.ifBlank { null } ?: it.name?.ifBlank { null } ?: "Úsečka"
                            ListedSegment(name, it.parent, it.color, ListProjectionType.PUDORYS, source = it)
                        }
                            +
                            state.segmentsNarys
                                .asSequence()
                                .filterNot { it.parent?.id in groupedIntersectionIds.segment3DIds || it.parentId in groupedIntersectionIds.segment3DIds }
                                .filterNot { it.isConicalSilhouette }
                                .filterNot { seg2d ->
                                    seg2d.parent?.id?.let { it in groupedSeg3DIds } ?: (seg2d.id in polygonSegNarIds)
                                }
                                .filterNot { seg2d ->
                                    // ✅ meridián: buď je přímo v seznamu ID (standalone), nebo patří parentem do meridiánu
                                    (seg2d.id in meridianAllSegIdsNarys) ||
                                            (seg2d.parent?.id in meridianSeg3DIdsAll)
                                }
                                .map {
                                    val name = it.parent?.name?.ifBlank { null } ?: it.name?.ifBlank { null } ?: "Úsečka"
                                    ListedSegment(name, it.parent, it.color, ListProjectionType.NARYS, source = it)
                                }
                    +
                    (if (showAxoProjections) state.segmentsAxo.asSequence() else emptySequence())
                        .filterNot { it.parent?.id in groupedIntersectionIds.segment3DIds || it.parentId in groupedIntersectionIds.segment3DIds }
                        .filterNot { it.isConicalSilhouette }
                        .filterNot { seg2d ->
                            seg2d.parent?.id?.let { it in groupedSeg3DIds } ?: (seg2d.id in polygonSegAxoIds)
                        }
                        .filterNot { it.parent?.id in meridianSeg3DIdsAll }
                        .map {
                            val name = it.parent?.name?.ifBlank { null } ?: it.name?.ifBlank { null } ?: "Úsečka"
                            ListedSegment(name, it.parent, it.color, ListProjectionType.AXO, source = it)
                        }
                    )
                // ✅ nejnovější první (kvůli distinctByParentOf)
                .sortedByDescending { ls ->
                    when (val src = ls.source) {
                        is Segment2DPudorys -> sortKeyDesc(src.effectiveCreationIndex)
                        is Segment2DNarys -> sortKeyDesc(src.effectiveCreationIndex)
                        is Segment2DAxo -> sortKeyDesc(src.effectiveCreationIndex)
                        is Segment2DBokorys -> sortKeyDesc(src.effectiveCreationIndex)
                        else -> Long.MIN_VALUE
                    }
                }
                .toList()
                .distinctByParentOf { it.parent }

        val listedLines = (
                state.lines3DPudorys
                    .asSequence()
                    .filterNot { (it.parent?.id ?: it.parentId) in ruledDirectrixIds }
                    .filterNot { it.parent?.id in groupedIntersectionIds.line3DIds || it.parentId in groupedIntersectionIds.line3DIds }
                    .filterNot(::isAxisProjection)
                    .filterNot { proj ->
                        val pid = proj.parent?.id
                        pid != null && pid in revolutionAxis3DIds
                    }

                    .mapNotNull { proj ->
                        val name = proj.name ?: return@mapNotNull null
                        ListedLine(name, proj.parent, proj.color, ListProjectionType.PUDORYS, source = proj)
                    }
                        +
                        state.lines3DNarys
                            .asSequence()
                            .filterNot { (it.parent?.id ?: it.parentId) in ruledDirectrixIds }
                            .filterNot { it.parent?.id in groupedIntersectionIds.line3DIds || it.parentId in groupedIntersectionIds.line3DIds }
                            .filterNot(::isAxisProjection)
                            .filterNot { proj ->
                                val pid = proj.parent?.id
                                pid != null && pid in revolutionAxis3DIds
                            }
                            .mapNotNull { proj ->
                                val name = proj.name ?: return@mapNotNull null
                                ListedLine(name, proj.parent, proj.color, ListProjectionType.NARYS, source = proj)
                            }
                        +
                        state.lines3DBokorys
                            .asSequence()
                            .filterNot { (it.parent?.id ?: it.parentId) in ruledDirectrixIds }
                            .filterNot { it.parent?.id in groupedIntersectionIds.line3DIds || it.parentId in groupedIntersectionIds.line3DIds }
                            .filterNot(::isAxisProjection)
                            .filterNot { proj ->
                                val pid = proj.parent?.id
                                pid != null && pid in revolutionAxis3DIds
                            }
                            .mapNotNull { proj ->
                                val name = proj.name ?: return@mapNotNull null
                                ListedLine(name, proj.parent, proj.color, ListProjectionType.BOKORYS, source = proj)
                            }
                        +
                        (if (showAxoProjections) state.lines3DAxo.asSequence() else emptySequence())
                            .filterNot { (it.parent?.id ?: it.parentId) in ruledDirectrixIds }
                            .filterNot { it.parent?.id in groupedIntersectionIds.line3DIds || it.parentId in groupedIntersectionIds.line3DIds }
                            .filterNot(::isAxisProjection)
                            .mapNotNull { proj ->
                                val name = proj.name ?: return@mapNotNull null
                                ListedLine(name, proj.parent, proj.color, ListProjectionType.AXO, source = proj)
                            }
                )
            .sortedByDescending { ll ->
                when (val src = ll.source) {
                    is Line3DProjectionPudorys -> sortKeyDesc(src.effectiveCreationIndex)
                    is Line3DProjectionNarys -> sortKeyDesc(src.effectiveCreationIndex)
                    is Line3DProjectionBokorys -> sortKeyDesc(src.effectiveCreationIndex)
                    is Line3DProjectionAxo -> sortKeyDesc(src.effectiveCreationIndex)
                    else -> Long.MIN_VALUE
                }
            }
            .toList()
            .distinctByParentOf { it.parent }

        val curvePointIdsNarys2D: Set<String> =
            state.curvesNarys
                .asSequence()
                .filter { it.parentId == null }

                .flatMap { it.pointIds.asSequence() } // ← to jsou ID Point3DNarys
                .toSet()
        val curvePointIdsPudorys2D: Set<String> =
            state.curvesPudorys.asSequence()
                .filter { it.parentId == null }               // jen 2D standalone
                .flatMap { it.points.asSequence() }
                .filterIsInstance<CurvePudRef.P>()
                .map { it.pointId }
                .toSet()
        val curvePointIdsBokorys2D: Set<String> =
            state.curvesBokorys
                .asSequence()
                .filter { it.parentId == null }
                .flatMap { it.pointIds.asSequence() }
                .toSet()
        // 3D parent IDs bodů v libovolné standalone 2D křivce (pudorys/narys/bokorys)
        // Slouží k filtrování axo projekcí téhož 3D bodu z hlavního listu
        val curvePoint3DParentIds: Set<String> = buildSet {
            state.curvesPudorys.filter { it.parentId == null }
                .flatMap { it.points }.filterIsInstance<CurvePudRef.P>()
                .forEach { ref -> state.pointsPudorys.firstOrNull { it.id == ref.pointId }?.parent?.id?.let(::add) }
            state.curvesNarys.filter { it.parentId == null }
                .flatMap { it.pointIds }
                .forEach { pid -> state.pointsNarys.firstOrNull { it.id == pid }?.parent?.id?.let(::add) }
            state.curvesBokorys.filter { it.parentId == null }
                .flatMap { it.pointIds }
                .forEach { pid -> state.pointsBokorys.firstOrNull { it.id == pid }?.parent?.id?.let(::add) }
        }
        val polygonPoints3DIds: Set<String> =
            state.polygons3D.flatMap { it.vertexPointIds }.toHashSet()
        val segmentEndpointPoint3DIds: Set<String> =
            state.segments3D.asSequence()
                .flatMap { sequenceOf(it.start.id, it.end.id) }
                .toSet()
        val curvePoints3DIds: Set<String> =
            state.curves3D.asSequence()
                .flatMap { it.pointIds.asSequence() }   // pointIds jsou 3D p ids
                .toSet()
        val polygonPointsPudIds: Set<String> =
            state.polygons3D.flatMap { it.vertexPointIdsPudorys}.toHashSet()
        val conic3dIdsInRevolutions = computeConic3DIdsUsedByRevolutions(state)
        val conic3dIdsInSurfaces = buildSet {
            state.conicalSurfaces.forEach { add(it.directrixId) }
            state.cylindricalSurfaces.forEach {
                add(it.directrixId)
                it.upperConicId?.let(::add)
            }
        }
        val polygonPointsNarIds: Set<String> =
            state.polygons3D.flatMap { it.vertexPointIdsNarys }.toHashSet()

        val listedPoints = (
                state.pointsPudorys
                    .asSequence()
                    .filterNot { it.parent?.id in groupedIntersectionIds.point3DIds }
                    .filterNot { isProjectedLinePoint(it) }
                    .filterNot { point2d ->
                        val parentId = point2d.parent?.id
                        when {
                            parentId != null -> (parentId in polygonPoints3DIds) ||
                                    (parentId in segmentEndpointPoint3DIds) ||
                                    (parentId in curvePoints3DIds) ||
                                    (point2d.id in curvePointIdsPudorys2D)
                            else -> (point2d.id in polygonPointsPudIds) || (point2d.id in curvePointIdsPudorys2D)
                        }
                    }
                    .map {
                        ListedPoint(
                            it.name,
                            it.parent,
                            it.isSegmentEndpoint,
                            isProjectedLine = it.isProjectedLine,
                            projectionType = ListProjectionType.PUDORYS,
                            source = it,
                            id = it.id,
                            color = it.color
                        )
                    }
                        +
                        state.pointsNarys
                            .asSequence()
                            .filterNot { it.parent?.id in groupedIntersectionIds.point3DIds }
                            .filterNot { isProjectedLinePoint(it) }
                            .filterNot { point2d ->
                                val parentId = point2d.parent?.id
                                when {
                                    parentId != null -> (parentId in polygonPoints3DIds) ||
                                            (parentId in segmentEndpointPoint3DIds) ||
                                            (parentId in curvePoints3DIds) ||
                                            (point2d.id in curvePointIdsNarys2D)
                                    else -> (point2d.id in polygonPointsNarIds) || (point2d.id in curvePointIdsNarys2D)
                                }
                            }
                            .map {
                                ListedPoint(
                                    it.name,
                                    it.parent,
                                    it.isSegmentEndpoint,
                                    isProjectedLine = it.isProjectedLine,
                                    projectionType = ListProjectionType.NARYS,
                                    source = it,
                                    color = it.color
                                )
                            }
                +
                        state.pointsBokorys
                            .asSequence()
                            .filterNot { it.parent?.id in groupedIntersectionIds.point3DIds }
                            .filterNot { isProjectedLinePoint(it) }
                            .filterNot { point2d ->
                                val parentId = point2d.parent?.id
                                when {
                                    parentId != null -> (parentId in segmentEndpointPoint3DIds) ||
                                            (parentId in curvePoints3DIds) ||
                                            (point2d.id in curvePointIdsBokorys2D)
                                    else -> point2d.id in curvePointIdsBokorys2D
                                }
                            }
                            .map {
                                ListedPoint(
                                    it.name,
                                    it.parent,
                                    it.isSegmentEndpoint,
                                    isProjectedLine = it.isProjectedLine,
                                    projectionType = ListProjectionType.BOKORYS,
                                    source = it,
                                    color = it.color
                                )
                            }
                +
                        (if (showAxoProjections) state.pointsAxo.asSequence() else emptySequence())
                            .filterNot { it.parent?.id in groupedIntersectionIds.point3DIds }
                            .filterNot { isProjectedLinePoint(it) }
                            .filterNot { point2d ->
                                val parentId = point2d.parent?.id
                                parentId != null && (
                                        (parentId in segmentEndpointPoint3DIds) ||
                                                (parentId in curvePoints3DIds) ||
                                                (parentId in curvePoint3DParentIds)
                                        )
                            }
                            .map {
                                ListedPoint(
                                    it.name,
                                    it.parent,
                                    it.isSegmentEndpoint,
                                    isProjectedLine = it.isProjectedLine,
                                    projectionType = ListProjectionType.AXO,
                                    source = it,
                                    color = it.color
                                )
                            }
                )
            .filter { !it.isSegmentEndpoint }
            .filter { !it.isProjectedLine }

            // ✅ nejdřív řazení podle "stáří objektu"
            .sortedByDescending { lp ->
                when (val src = lp.source) {
                    is Point3DPudorys -> sortKeyDesc(src.effectiveCreationIndex)
                    is Point3DNarys -> sortKeyDesc(src.effectiveCreationIndex)
                    is Point3DBokorys -> sortKeyDesc(src.effectiveCreationIndex)
                    is Point3DAxo -> sortKeyDesc(src.effectiveCreationIndex)
                    else -> Long.MIN_VALUE
                }
            }.toList()
            .distinctByParentOf { it.parent }


        val mixedItems =
            buildList {
                // --- PRŮNIKY ---
                state.intersectionGroups.forEach { group ->
                    add(
                        UiTreeItem(
                            key = "intersection:${group.id}",
                            sortIndex = sortKeyDesc(group.creationIndex),
                            name = group.displayName,
                            color = group.parts.firstNotNullOfOrNull { part ->
                                when (part.kind) {
                                    IntersectionPartKind.POINT3D ->
                                        state.sharedPoints3D.firstOrNull { it.id == part.id }?.color
                                    IntersectionPartKind.LINE3D ->
                                        state.lines3D.firstOrNull { it.id == part.id }?.color
                                    IntersectionPartKind.SEGMENT3D ->
                                        state.segments3D.firstOrNull { it.id == part.id }?.color
                                    IntersectionPartKind.CONIC3D ->
                                        state.conics3D.firstOrNull { it.id == part.id }?.color
                                    IntersectionPartKind.CURVE3D ->
                                        state.curves3D.firstOrNull { it.id == part.id }?.color
                                }
                            } ?: INTERSECTION_RESULT_COLOR,
                            is3D = true,
                            icon = ObjectListIcon.Intersection,
                            isSelected = { state.selectedIntersectionGroupId == group.id },
                            onClick = {
                                selectIntersectionGroup(state, group, clearAllOnClick)
                            },
                            children = buildIntersectionGroupChildren(
                                state,
                                group,
                                clearAllOnClick
                            )
                        )
                    )
                }
                // KUŽELY – web tuhle featuru nemá.

                // --- KUŽELY ---
                state.conicalSurfaces.forEach { cone ->
                    add(
                        UiTreeItem(
                            key = "cone:${cone.id}",
                            sortIndex = sortKeyDesc(cone.creationIndex),
                            name = "Kužel ${cone.name}",
                            color = cone.color,
                            is3D = true,
                            isSelected = { state.selectedCone.contains(cone) },
                            onClick = {
                                if (clearAllOnClick) clearSelection(state)
                                selectConicalSurface(cone, state)

                            },
                            children = emptyList() // kvadriky web nemá – bez potomků
                        )
                    )
                }
                // -- ROT PLOCHY
                state.solidsOfRevolutionNarys.forEach { solid ->
                    add(
                        UiTreeItem(
                            key = "sor:${solid.id}",
                            sortIndex = sortKeyDesc(solid.creationIndex),
                            name = "Rotační plocha ${solid.name}", // dej si hezčí label
                            color = solid.color,
                            is3D = true,
                            isSelected = { state.selectedSolidOfRevolutionId == solid.id },
                            onClick = {


                            },
                            children = emptyList()
                        )
                    )
                }
                state.solidsOfRevolutionPudorys.forEach { solid ->
                    add(
                        UiTreeItem(
                            key = "sor:${solid.id}",
                            sortIndex = sortKeyDesc(solid.creationIndex),
                            name = "Rotační plocha ${solid.name}", // dej si hezčí label
                            color = solid.color,
                            is3D = true,
                            isSelected = { state.selectedSolidOfRevolutionId == solid.id },
                            onClick = {


                            },
                            children = emptyList()
                        )
                    )
                }
                val arcIdsInRevolutions = computeArcIdsUsedByRevolutions(state)
                val arcIdsInRevolutionsPud = computeArcIdsUsedByRevolutionsPud(state)
                // --- OBLOUKY (NÁRYS) – jen ty, co nejsou součást rotačních ploch ---
                state.arcsNarys
                    .asSequence()
                    .filter { it.id !in arcIdsInRevolutions }
                    .forEach { a ->
                        val nm = a.name.ifBlank { "Oblouk" }
                        add(
                            UiTreeItem(
                                key = "arc:n:${a.id}",
                                sortIndex = sortKeyDesc(a.creationIndex),
                                name = "Oblouk₂ $nm",
                                color = a.color,
                                is3D = false,
                                isSelected = { state.selectedArcsNarys.contains(a) }, // nebo podle IDs
                                onClick = {
                                    if (clearAllOnClick) clearSelection(state)
                                    toggleSelectionNarysArc(a, state)
                                }
                            )
                        )
                    }
                state.arcsPudorys
                    .asSequence()
                    .filter { it.id !in arcIdsInRevolutionsPud}
                    .forEach { a ->
                        val isFullCircle = a.isFullCircle()
                        val nm = when {
                            isFullCircle -> "Kružnice ${a.name}".trim()
                            state.projectionMode == ProjectionMode.AXO -> a.name.ifBlank { "Oblouk" }
                            else -> a.name.ifBlank { "Oblouk\u2081" }
                        }
                        add(
                            UiTreeItem(
                                key = "arc:n:${a.id}",
                                sortIndex = sortKeyDesc(a.creationIndex),
                                name = nm,
                                color = a.color,
                                is3D = false,
                                icon = if (isFullCircle) ObjectListIcon.Circle else null,
                                isSelected = { state.selectedArcsPudorys.contains(a) }, // nebo podle IDs
                                onClick = {
                                    if (clearAllOnClick) clearSelection(state)
                                    toggleSelectionPudorysArc(a, state)
                                }
                            )
                        )
                    }
                state.arcsBokorys
                    .asSequence()
                    // SoR bokorysné meridiánové oblouky se vypisují jako děti rotační plochy
                    // (buildSolidOfRevolutionChildren), proto je z hlavního seznamu vynech.
                    .filter { !it.id.startsWith(SOR_BOKORYS_MERIDIAN_ID_PREFIX) }
                    .forEach { a ->
                        val nm = a.name.ifBlank { "Oblouk" }
                        add(
                            UiTreeItem(
                                key = "arc:b:${a.id}",
                                sortIndex = sortKeyDesc(a.creationIndex),
                                name = "Oblouk₃ $nm",
                                color = a.color,
                                is3D = false,
                                isSelected = { state.selectedArcsBokorys.contains(a) },
                                onClick = {
                                    if (clearAllOnClick) clearSelection(state)
                                    toggleSelectionBokorysArc(a, state)
                                }
                            )
                        )
                    }
                state.arcsAxoOverlay
                    .asSequence()
                    .forEach { a ->
                        val isFullCircle = a.isFullCircle()
                        val nm = when {
                            isFullCircle -> "Kružnice ${a.name}".trim()
                            else -> a.name.ifBlank { "Oblouk" }
                        }
                        add(
                            UiTreeItem(
                                key = "arc:ao:${a.id}",
                                sortIndex = sortKeyDesc(a.creationIndex),
                                name = nm,
                                color = a.color,
                                is3D = false,
                                icon = if (isFullCircle) ObjectListIcon.Circle else null,
                                isSelected = { state.selectedArcsAxoOverlay.contains(a) },
                                onClick = {
                                    if (clearAllOnClick) clearSelection(state)
                                    toggleSelectionAxoOverlayArc(a, state)
                                }
                            )
                        )
                }


                // SOLIDY Z 3D ÚSEČEK – web tuhle featuru nemá.

                // --- POLYGONY (✅ rozbalitelné: děti = úsečky + body, ze kterých polygon stojí) ---
                state.polygons3D
                    .filterNot { it.id in solidPolygonIds || it.id in intersectionPolygonIds }
                    .forEach { poly ->
                    val name = polygonDisplayName(state, poly)

                    val children = buildPolygonChildren(
                        state = state,
                        poly = poly,
                        clearAllOnClick = clearAllOnClick
                    )

                    add(
                        UiTreeItem(
                            key = "poly:${poly.id}",
                            sortIndex = sortKeyDesc(poly.creationIndex),
                            name = name,
                            color = poly.color,
                            is3D = true,
                            icon = ObjectListIcon.Polygon(poly.n),
                            isSelected = { state.selectedPolygons.any { it.id == poly.id } },
                            onClick = {
                                if (clearAllOnClick) clearSelection(state)
                                togglePolygonSelection(state, poly.id, clearOthers = false)
                            },
                            children = children
                        )
                    )
                }

                // VÁLCE – web tuhle featuru nemá.

                // --- ROVINY ---
                state.planes3D.filterNot(::isAxoPlane).filterNot { it.id in ruledPlaneIds }.forEach { plane ->
                    add(
                        UiTreeItem(
                            key = "plane:${plane.id}",
                            sortIndex = sortKeyDesc(plane.creationIndex),
                            name = "Rovina ${plane.name}",
                            color = plane.color,
                            is3D = true,
                            isSelected = { state.selectedPlanes.any { it.id == plane.id } },
                            onClick = {
                                if (clearAllOnClick) clearSelection(state)
                                toggleSelectionPlane(plane, state)

                            },
                            children = emptyList()
                        )
                    )
                }

                // --- 3D KŘIVKY ---
                state.curves3D
                    .filterNot { it.id in groupedIntersectionIds.curve3DIds }
                    .filterNot { it.id in ruledDirectrixIds }
                    .forEach { c ->
                    add(
                        UiTreeItem(
                            key = "curve3d:${c.id}",
                            sortIndex = sortKeyDesc(c.creationIndex),
                            name = "Křivka ${c.name}",
                            color = c.color,
                            is3D = true,
                            isSelected = { state.selectedCurve3DId == c.id },
                            onClick = {
                                if (clearAllOnClick) clearSelection(state)
                                toggleSelectionCurve3D(state, c.id)
                            },
                            children = buildCurve3DChildren(state, c, clearAllOnClick)
                        )
                    )
                }
                // --- KŘIVKY PŮDORYS (jen bez parenta) ---
                state.curvesPudorys
                    .asSequence()
                    .filter { it.parentId == null }
                    .filterNot {it.id in meridianObjectIdsPudorys }
                    .forEach { c ->
                        add(
                            UiTreeItem(
                                key = "curveP:${c.id}",
                                sortIndex = sortKeyDesc(c.effectiveCreationIndex),
                                name = "Křivka₁ ${c.effectiveName}",
                                color = c.effectiveColor,
                                is3D = false,
                                isSelected = { state.selectedCurvePudorysId == c.id },
                                onClick = {
                                    if (clearAllOnClick) clearSelection(state)
                                    toggleSelectionCurvePudorys(state, c.id)

                                },
                                children = buildCurvePudorysChildren(state, c, clearAllOnClick)
                            )
                        )
                    }
                // --- KŘIVKY NÁRYS (jen bez parenta) ---
                state.curvesNarys
                    .asSequence()
                    .filter { it.parentId == null }
                    .filterNot {it.id in meridianObjectIdsNarys}
                    .forEach { c ->
                        add(
                            UiTreeItem(
                                key = "curveN:${c.id}",
                                sortIndex = sortKeyDesc(c.effectiveCreationIndex),
                                name = "Křivka₂ ${c.effectiveName}",
                                color = c.effectiveColor,
                                is3D = false,
                                isSelected = { state.selectedCurveNarysId == c.id },
                                onClick = {
                                    if (clearAllOnClick) clearSelection(state)
                                    toggleSelectionCurveNarys(state, c.id)
                                },
                                children = buildCurveNarysChildren(state, c, clearAllOnClick)
                            )
                        )
                    }
                // --- KŘIVKY BOKORYS (jen bez parenta) ---
                state.curvesBokorys
                    .asSequence()
                    .filter { it.parentId == null }
                    .forEach { c ->
                        add(
                            UiTreeItem(
                                key = "curveB:${c.id}",
                                sortIndex = sortKeyDesc(c.effectiveCreationIndex),
                                name = "Křivka₃ ${c.effectiveName}",
                                color = c.effectiveColor,
                                is3D = false,
                                isSelected = { state.selectedCurveBokorysId == c.id },
                                onClick = {
                                    if (clearAllOnClick) clearSelection(state)
                                    toggleSelectionCurveBokorys(state, c.id)
                                },
                                children = buildCurveBokorysChildren(state, c, clearAllOnClick)
                            )
                        )
                    }
                // --- KULOVÉ PLOCHY ---
                state.spheres3D.forEach { sphere ->
                    val nm = sphere.name.ifBlank { "Kulová plocha" }
                    add(
                        UiTreeItem(
                            key = "sphere:${sphere.id}",
                            sortIndex = sortKeyDesc(sphere.creationIndex),
                            name = "Kulová plocha $nm",
                            color = sphere.color,
                            is3D = true,
                            isSelected = { state.selectedSpheres3D.contains(sphere) },
                            onClick = {
                                if (clearAllOnClick) clearSelection(state)
                                toggleSelectionSphere3D(sphere, state)


                            },
                            children = buildSphereChildren(state, sphere, clearAllOnClick)
                        )
                    )
                }

                // --- 3D KUŽELOSEČKY ---
                state.conics3D
                    .asSequence()
                    .filterNot { it.id in ruledDirectrixIds }
                    .filterNot { it.id in groupedIntersectionIds.conic3DIds }
                    .filterNot { it.isDirectrixOfAnyCone }
                    .filterNot { it.id in conic3dIdsInRevolutions }   // ✅ vyloučit rotační
                    .forEach { conic3D ->
                        val type = conicType3DByProjections(state, conic3D)
                        val nm = "$type ${conic3D.name}"
                        add(
                            UiTreeItem(
                                key = "conic3d:${conic3D.id}",
                                sortIndex = sortKeyDesc(conic3D.creationIndex),
                                name = nm,
                                color = conic3D.color,
                                is3D = true,
                                isSelected = { isConic3DSelected(state, conic3D.id) }, // doporučuju přes projekce
                                onClick = {
                                    if (clearAllOnClick) clearSelection(state)
                                    selectConic3DProjections(state, conic3D.id, clearAllOnClick = false) // nebo tvoje volba
                                    advanceConicArcSelectionIfNeeded(state)

                                },
                                children = emptyList()
                            )
                        )
                    }
                // --- 2D KUŽELOSEČKY (bez parenta) ---
                state.conicsPudorys
                    .asSequence()
                    .filter { it.parent == null && (it.parentId ?: "") !in conic3dIdsInSurfaces }
                    .filterNot { c -> state.spheres3D.any { it.id == c.parentId } }
                    .filterNot {it.id in meridianObjectIdsPudorys}
                    .forEach { c ->
                        val type = when {
                            state.hyperbolaInputsPudorys.containsKey(c.id) -> "Hyperbola"
                            isHyperbola2D(c.a, c.b, c.c) -> "Hyperbola"
                            state.conicInputPointsPudorys[c.id]?.third == Offset.Unspecified -> "Parabola"
                            else -> "Elipsa"
                        }
                        add(
                            UiTreeItem(
                                key = "conic2d:p:${c.id}",
                                sortIndex = sortKeyDesc(c.effectiveCreationIndex),
                                name = "$type ${c.name}",
                                color = c.localColor ?: Color.Black,
                                is3D = false,
                                isSelected = { state.selectedConicsPudorys.contains(c) },
                                onClick = {
                                    if (clearAllOnClick) clearSelection(state)
                                    toggleSelectionPudorysConic(c, state)
                                    advanceConicArcSelectionIfNeeded(state)
                                },
                                children = emptyList()
                            )
                        )
                    }

                state.conicsNarys
                    .asSequence()
                    .filter { it.parent == null && (it.parentId ?: "") !in conic3dIdsInSurfaces }
                    .filterNot { c -> state.spheres3D.any { it.id == c.parentId } }
                    .filterNot {it.id in meridianObjectIdsNarys}
                    .forEach { c ->
                        val type = when {
                            state.hyperbolaInputsNarys.containsKey(c.id) -> "Hyperbola"
                            isHyperbola2D(c.a, c.b, c.c) -> "Hyperbola"
                            state.conicInputPointsNarys[c.id]?.third == Offset.Unspecified -> "Parabola"
                            else -> "Elipsa"
                        }
                        add(
                            UiTreeItem(
                                key = "conic2d:n:${c.id}",
                                sortIndex = sortKeyDesc(c.effectiveCreationIndex),
                                name = "$type ${c.name}",
                                color = c.localColor ?: Color.Black,
                                is3D = false,
                                isSelected = { state.selectedConicsNarys.contains(c) },
                                onClick = {
                                    if (clearAllOnClick) clearSelection(state)
                                    toggleSelectionNarysConic(c, state)
                                    advanceConicArcSelectionIfNeeded(state)
                                },
                                children = emptyList()
                            )
                        )
                    }

                // --- KRUŽNICE (2D, bez parenta, bez helpCircle) ---
                state.conicsBokorys
                    .asSequence()
                    .filter { it.parent == null && (it.parentId ?: "") !in conic3dIdsInSurfaces }
                    .filterNot { c -> state.spheres3D.any { it.id == c.parentId } }
                    .forEach { c ->
                        val type = when {
                            state.hyperbolaInputsBokorys.containsKey(c.id) -> "Hyperbola"
                            isHyperbola2D(c.a, c.b, c.c) -> "Hyperbola"
                            state.conicInputPointsBokorys[c.id]?.third == Offset.Unspecified -> "Parabola"
                            else -> "Elipsa"
                        }
                        add(
                            UiTreeItem(
                                key = "conic2d:b:${c.id}",
                                sortIndex = sortKeyDesc(c.effectiveCreationIndex),
                                name = "$type ${c.name}",
                                color = c.localColor ?: Color.Black,
                                is3D = false,
                                isSelected = { state.selectedConicsBokorys.contains(c) },
                                onClick = {
                                    if (clearAllOnClick) clearSelection(state)
                                    toggleSelectionBokorysConic(c, state)
                                },
                                children = emptyList()
                            )
                        )
                    }

                state.circlesPudorys
                    .asSequence()
                    .filter { !it.isHelpCircle }
                    .filter { it.parentId.isNullOrBlank() }
                    .filterNot {it.id in meridianCircleIdsPudorys2D }
                    .filterNot { it.id in revolutionCircleIdsPudorys2D }
                    .forEach { c ->
                        val nm = c.name.ifBlank { "Kružnice" }
                        add(
                            UiTreeItem(
                                key = "circle:p:${c.id}",
                                sortIndex = sortKeyDesc(c.effectiveCreationIndex),
                                name = "Kružnice $nm",
                                color = c.localColor ?: Color.Black,
                                is3D = false,
                                isSelected = { state.selectedCirclesPudorys.contains(c) },
                                onClick = {
                                    if (clearAllOnClick) clearSelection(state)
                                    toggleSelectionPudorysCircle(c, state)
                                    advanceConicArcSelectionIfNeeded(state)
                                },
                                children = emptyList()
                            )
                        )
                    }

                state.circlesNarys
                    .asSequence()
                    .filter { !it.isHelpCircle }
                    .filter { it.parentId.isNullOrBlank() }
                    .filterNot { it.id in meridianCircleIdsNarys2D }        // ✅ meridián
                    .filterNot { it.id in revolutionCircleIdsNarys2D }
                    .forEach { c ->
                        val nm = c.name.ifBlank { "Kružnice" }
                        add(
                            UiTreeItem(
                                key = "circle:n:${c.id}",
                                sortIndex = sortKeyDesc(c.effectiveCreationIndex),
                                name = "Kružnice $nm",
                                color = c.localColor ?: Color.Black,
                                is3D = false,
                                isSelected = { state.selectedCirclesNarys.contains(c) },
                                onClick = {
                                    if (clearAllOnClick) clearSelection(state)
                                    toggleSelectionNarysCircle(c, state)
                                    advanceConicArcSelectionIfNeeded(state)
                                },
                                children = emptyList()
                            )
                        )
                    }

                // --- POMOCNÉ PŘÍMKY (NÁRYS) ---
                state.helpLineNarys
                    .asSequence()
                    .filterNot { it.id == "axisZ" }
                    .filter { it.name != "" }
                    .forEach { line ->
                        add(
                            UiTreeItem(
                                key = "helpline:n:${line.id}",
                                sortIndex = sortKeyDesc(line.creationIndex),
                                name = "P. přímka ${line.name}",
                                color = line.color,
                                is3D = false,
                                superscript = line.localSuperscript,
                                subscript = line.lowerSuperscript,
                                isSelected = { state.selectedLinesNarys.contains(line) },
                                onClick = {
                                    if (clearAllOnClick) clearSelection(state)
                                    toggleSelectionNarysLine(line, state)
                                },
                                children = emptyList()
                            )
                        )
                    }

                // --- POMOCNÉ PŘÍMKY (PŮDORYS) ---
                state.helpLinePudorys
                    .asSequence()
                    .filterNot { it.id == "axisX" || it.id == "axisY" || it.id == "axisZ" }
                    .filter { it.name != "" }
                    .forEach { line ->
                        add(
                            UiTreeItem(
                                key = "helpline:p:${line.id}",
                                sortIndex = sortKeyDesc(line.creationIndex),
                                name = "P. přímka ${line.name}",
                                color = line.color,
                                is3D = false,
                                superscript = line.localSuperscript,
                                subscript = line.lowerSuperscript,
                                isSelected = { state.selectedLinesPudorys.contains(line) },
                                onClick = {
                                    if (clearAllOnClick) clearSelection(state)
                                    toggleSelectionPudorysLine(line, state)
                                },
                                children = emptyList()
                            )
                        )
                    }

                // --- STOPY (samostatné, bez parenta) ---
                state.lineTracesPudorys
                    .asSequence()
                    .filter { it.parent == null }
                    .forEach { trace ->
                        val sup = trace.localName ?: "Stopa"
                        add(
                            UiTreeItem(
                                key = "trace:p:${trace.id}",
                                sortIndex = sortKeyDesc(trace.effectiveCreationIndex),
                                name = "Stopa p₁",
                                color = trace.color,
                                is3D = false,
                                superscript = sup,
                                isSelected = { state.selectedLinesPudorys.contains(trace) },
                                onClick = {
                                    if (clearAllOnClick) clearSelection(state)
                                    toggleSelectionPudorysLine(trace, state)
                                },
                                children = emptyList()
                            )
                        )
                    }

                state.lineTracesNarys
                    .asSequence()
                    .filter { it.parent == null }
                    .forEach { trace ->
                        val sup = trace.localName ?: "Stopa"
                        add(
                            UiTreeItem(
                                key = "trace:n:${trace.id}",
                                sortIndex = sortKeyDesc(trace.effectiveCreationIndex),
                                name = "Stopa n₂",
                                color = trace.color,
                                is3D = false,
                                superscript = sup,
                                isSelected = { state.selectedLinesNarys.contains(trace) },
                                onClick = {
                                    if (clearAllOnClick) clearSelection(state)
                                    toggleSelectionNarysLine(trace, state)
                                },
                                children = emptyList()
                            )
                        )
                    }

                // --- POMOCNÉ BODY ---
                state.aidPointsLogical
                    .asSequence()
                    .filter { !it.name.isNullOrBlank() }
                    .filter { it.id != "origin" }
                    .forEach { p ->
                        add(
                            UiTreeItem(
                                key = "aid:${p.id}",
                                sortIndex = sortKeyDesc(p.creationIndex),
                                name = "Pom. bod ${p.name ?: ""}",
                                color = p.color,
                                is3D = false,
                                superscript = p.upperSuperscript,
                                subscript = p.lowerSuperscript,
                                isSelected = { state.selectedAidPointIds.contains(p.id) },
                                onClick = {
                                    if (clearAllOnClick) {
                                        clearSelection(state)
                                        state.selectedAidPointIds.add(p.id)
                                    } else {
                                        toggleSelectionAidPoint(p, state)
                                    }
                                },
                                children = emptyList()
                            )
                        )
                    }
                if (state.projectionMode == ProjectionMode.AXO){
                    state.axoOverlayPoints
                        .asSequence()
                        .filter { !it.name.isNullOrBlank() }
                        .forEach { p ->
                            add(
                                UiTreeItem(
                                    key = "aid:${p.id}",
                                    sortIndex = sortKeyDesc(p.creationIndex),
                                    name = "Bod ${p.name ?: ""}",
                                    color = p.color,
                                    superscript = p.upper,
                                    subscript = p.lower,
                                    isSelected = { state.selectedAOPointIds.contains(p.id) },
                                    is3D = false,
                                    onClick = {
                                        if (clearAllOnClick) {
                                            clearSelection(state)
                                            state.selectedAOPointIds.add(p.id)
                                        } else {
                                            toggleSelectionOverlayPoint(p, state)
                                        }
                                    }
                                )
                            )
                        }
                }

                // --- BODY ---
                listedPoints.forEach { point ->
                    val src = point.source

                    val sortIdx = when (src) {
                        is Point3DPudorys -> sortKeyDesc(src.effectiveCreationIndex)
                        is Point3DNarys -> sortKeyDesc(src.effectiveCreationIndex)
                        is Point3DBokorys -> sortKeyDesc(src.effectiveCreationIndex)
                        is Point3DAxo -> sortKeyDesc(src.effectiveCreationIndex)
                        else -> Long.MAX_VALUE
                    }

                    val fullName = if (point.parent == null) {
                        val index = when (point.projectionType) {
                            ListProjectionType.PUDORYS -> "₁"
                            ListProjectionType.NARYS   -> "₂"
                            ListProjectionType.BOKORYS ->"₃"
                            ListProjectionType.AXO      -> "ₐ"
                        }
                        "Bod ${point.name}$index"
                    } else {
                        "Bod ${(point.parent as? Point3D)?.name ?: point.name}"
                    }

                    val sup = (src as? Point2DProjection)?.localSuperscript
                    val parentId = (point.parent as? Point3D)?.id

                    val key = when (src) {
                        is Point3DPudorys -> if (parentId != null) "pt3d:p:$parentId" else "pt2d:p:${src.id}"
                        is Point3DNarys -> if (parentId != null) "pt3d:n:$parentId" else "pt2d:n:${src.id}"
                        is Point3DBokorys -> if (parentId != null) "pt3d:b:$parentId" else "pt2d:b:${src.id}"
                        is Point3DAxo -> if (parentId != null) "pt3d:a:$parentId" else "pt2d:a:${src.id}"
                        else              -> "pt:unknown"
                    }

                    add(
                        UiTreeItem(
                            key = key,
                            sortIndex = sortIdx,
                            name = fullName,
                            color = point.color,
                            is3D = point.parent != null,
                            superscript = sup,
                            isSelected = {
                                isPointSelected(point.parent as? Point3D, point.source, point.projectionType, state)
                            },
                            onClick = {
                                if (clearAllOnClick) clearSelection(state)

                                if (point.parent != null) {
                                    val allProjections =
                                        state.pointsPudorys.filter { it.parent == point.parent } +
                                                state.pointsNarys.filter { it.parent == point.parent }+
                                                state.pointsBokorys.filter { it.parent == point.parent }+
                                                if (showAxoProjections) state.pointsAxo.filter { it.parent == point.parent } else emptyList()

                                    allProjections.forEach {
                                        when (it) {
                                            is Point3DPudorys -> toggleSelectionPudorys(it, state)
                                            is Point3DNarys -> toggleSelection(it, state)
                                            is Point3DBokorys -> toggleSelectionBokorys(it,state)
                                            is Point3DAxo -> toggleSelectionAxo(it,state)
                                        }
                                    }
                                } else {
                                    when (point.projectionType) {
                                        ListProjectionType.PUDORYS ->
                                            toggleSelectionPudorys(point.source as Point3DPudorys, state)
                                        ListProjectionType.NARYS ->
                                            toggleSelection(point.source as Point3DNarys, state)

                                        ListProjectionType.BOKORYS ->
                                            toggleSelectionBokorys(point.source as Point3DBokorys, state)
                                        ListProjectionType.AXO ->
                                            toggleSelectionAxo(point.source as Point3DAxo, state)
                                    }
                                }
                            },
                            children = emptyList()
                        )
                    )
                }

                // --- PŘÍMKY ---
                listedLines.forEach { line ->
                    val src = line.source

                    val sortIdx = when (src) {
                        is Line3DProjectionPudorys -> sortKeyDesc(src.effectiveCreationIndex)
                        is Line3DProjectionNarys -> sortKeyDesc(src.effectiveCreationIndex)
                        is Line3DProjectionBokorys -> sortKeyDesc(src.effectiveCreationIndex)
                        is Line3DProjectionAxo -> sortKeyDesc(src.effectiveCreationIndex)
                        else -> Long.MAX_VALUE
                    }

                    val fullName = if (line.parent == null) {
                        when(src){
                            is Line3DProjectionPudorys ->    "Přímka ${line.name}"
                            is Line3DProjectionNarys ->    "Přímka ${line.name}"
                            is Line3DProjectionBokorys ->    "Přímka ${line.name}"
                            is Line3DProjectionAxo ->    "Přímka ${line.name}"
                            else -> "Přímka"
                        }


                    } else {
                        "Přímka ${(line.parent as? Line3D)?.name ?: line.name}"
                    }

                    val superStr = (src as? Line2DProjection)?.superscript

                    val parentId = (line.parent as? Line3D)?.id ?: "nop"
                    val key = when (src) {
                        is Line3DProjectionPudorys -> "line:p:${src.id}:$parentId"
                        is Line3DProjectionNarys -> "line:n:${src.id}:$parentId"
                        is Line3DProjectionBokorys -> "line:b:${src.id}:$parentId"
                        is Line3DProjectionAxo -> "line:a:${src.id}:$parentId"
                        else -> "line:unknown:$parentId"
                    }

                    add(
                        UiTreeItem(
                            key = key,
                            sortIndex = sortIdx,
                            name = fullName,
                            color = line.color,
                            is3D = line.parent != null,
                            superscript = superStr,
                            isSelected = { isLineSelected(line.parent as? Line3D, line.source, line.projectionType, state) },
                            onClick = {
                                if (clearAllOnClick) clearSelection(state)

                                val parentLine = line.parent as? Line3D
                                if (parentLine != null) {
                                    val allProjections =
                                        state.lines3DPudorys.filter { it.parent?.id == parentLine.id || it.parentId == parentLine.id } +
                                                state.lines3DNarys.filter { it.parent?.id == parentLine.id || it.parentId == parentLine.id } +
                                                state.lines3DBokorys.filter { it.parent?.id == parentLine.id || it.parentId == parentLine.id } +
                                                if (showAxoProjections) state.lines3DAxo.filter { it.parent?.id == parentLine.id || it.parentId == parentLine.id } else emptyList()

                                    allProjections.forEach {
                                        when (it) {
                                            is Line3DProjectionPudorys -> toggleSelectionPudorysLine(it, state)
                                            is Line3DProjectionNarys -> toggleSelectionNarysLine(it, state)
                                            is Line3DProjectionBokorys -> toggleSelectionBokorysLine(it, state)
                                            is Line3DProjectionAxo -> toggleSelectionAxoLine(it,state)
                                        }
                                    }

                                    projectedLinePointsFor(parentLine, state).forEach {
                                        toggleProjectedLinePointSelection(it, state)
                                    }
                                } else {
                                    when (line.projectionType) {
                                        ListProjectionType.PUDORYS ->
                                            toggleSelectionPudorysLine(line.source as Line3DProjectionPudorys, state)
                                        ListProjectionType.NARYS ->
                                            toggleSelectionNarysLine(line.source as Line3DProjectionNarys, state)

                                        ListProjectionType.BOKORYS ->
                                            toggleSelectionBokorysLine(line.source as Line3DProjectionBokorys, state)

                                        ListProjectionType.AXO ->
                                            toggleSelectionAxoLine(line.source as Line3DProjectionAxo, state)
                                    }
                                }

                            },
                            children = emptyList()
                        )
                    )
                }

                // --- ÚSEČKY ---
                listedSegments.forEach { segment ->
                    val src = segment.source

                    val sortIdx = when (src) {
                        is Segment2DPudorys -> sortKeyDesc(src.effectiveCreationIndex)
                        is Segment2DNarys -> sortKeyDesc(src.effectiveCreationIndex)
                        is Segment2DBokorys -> sortKeyDesc(src.effectiveCreationIndex)
                        is Segment2DAxo -> sortKeyDesc(src.effectiveCreationIndex)
                        else -> Long.MAX_VALUE
                    }

                    val fullName = when (src){
                        is Segment2DPudorys -> segment.name.ifBlank { "Úsečka\u2081" }
                        is Segment2DNarys -> segment.name.ifBlank { "Úsečka\u2082" }
                        is Segment2DBokorys -> segment.name.ifBlank { "Úsečka\u2083" }
                        is Segment2DAxo -> segment.name.ifBlank { "Úsečkaₐ" }
                        else ->segment.name.ifBlank { "Úsečka" }
                    }

                    val parentId = (segment.parent as? Segment3D)?.id ?: "nop"
                    val key = when (src) {
                        is Segment2DPudorys -> "seg:p:${src.id}:$parentId"
                        is Segment2DNarys -> "seg:n:${src.id}:$parentId"
                        is Segment2DBokorys -> "seg:b:${src.id}:$parentId"
                        is Segment2DAxo -> "seg:dx:${src.id}:$parentId"
                        else -> "seg:unknown:$parentId"
                    }

                    add(
                        UiTreeItem(
                            key = key,
                            sortIndex = sortIdx,
                            name = if (segment.parent != null)fullName.removeSuffix("\u2083").removeSuffix("\u2081").removeSuffix("\u2082") else fullName,
                            color = segment.color,
                            is3D = segment.parent != null,
                            isSelected = { isSegmentSelected(segment.parent as? Segment3D, segment.source, segment.projectionType, state) },
                            onClick = {
                                if (clearAllOnClick) clearSelection(state)

                                if (segment.parent != null) {
                                    val allProjections =
                                        state.segmentsPudorys.filter { it.parent == segment.parent } +
                                                state.segmentsNarys.filter { it.parent == segment.parent }+
                                                state.segmentsBokorys.filter { it.parent == segment.parent } +
                                                if (showAxoProjections) state.segmentsAxo.filter { it.parent == segment.parent } else emptyList()

                                    allProjections.forEach {
                                        when (it) {
                                            is Segment2DPudorys -> toggleSelectionPudorysSegment(it, state)
                                            is Segment2DNarys -> toggleSelectionNarysSegment(it, state)
                                            is Segment2DAxo -> toggleSelectionAxoSegment(it, state)
                                            is Segment2DBokorys -> toggleSelectionBokorysSegment(it, state)
                                            else -> {}
                                        }
                                    }
                                } else {
                                    when (segment.projectionType) {
                                        ListProjectionType.PUDORYS ->
                                            toggleSelectionPudorysSegment(segment.source as Segment2DPudorys, state)
                                        ListProjectionType.NARYS ->
                                            toggleSelectionNarysSegment(segment.source as Segment2DNarys, state)

                                        ListProjectionType.BOKORYS ->
                                            toggleSelectionBokorysSegment(segment.source as Segment2DBokorys, state)

                                        ListProjectionType.AXO ->
                                            toggleSelectionAxoSegment(segment.source as Segment2DAxo, state)
                                    }
                                }
                            },
                            children = if (segment.parent==null){
                                when (segment.projectionType){
                                ListProjectionType.PUDORYS -> buildSegmentPudorysChildren(state,segment.source as Segment2DPudorys,clearAllOnClick)
                                    ListProjectionType.NARYS -> buildSegmentNarysChildren(state, segment.source as Segment2DNarys, clearAllOnClick)
                                    ListProjectionType.BOKORYS -> buildSegmentBokorysChildren(state, segment.source as Segment2DBokorys, clearAllOnClick)
                                    ListProjectionType.AXO -> buildSegmentAxoChildren(state, segment.source as Segment2DAxo, clearAllOnClick)
                                }
                            }else {
                                val seg3D = segment.parent as? Segment3D
                                if (seg3D != null) buildSegment3DChildren(state, seg3D, clearAllOnClick) else emptyList()
                            }
                        )
                    )
                }
            }
        val sorted = mixedItems.sortedByDescending{ it.sortIndex }
        var wasAtTop by remember { mutableStateOf(true) }

        LaunchedEffect(listState) {
            snapshotFlow { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
                .distinctUntilChanged()
                .collectLatest { atTop -> wasAtTop = atTop }
        }

// 2) triggeruj podle změny prvního itemu (insert na začátek)
        val topKey = sorted.firstOrNull()?.key

        LaunchedEffect(topKey) {
            if (wasAtTop) {
                // počkej na další frame/layout, pak teprve scroll
                withFrameNanos { }
                listState.scrollToItem(0, 0)
            }
        }

        Box {
            // klíče uzlů, které se právě zabalují – dočasně drží jejich potomky
            // v seznamu, aby doanimovali zmizení (viz onToggleExpand níže)
            val collapsingKeys = remember { mutableStateListOf<String>() }
            val collapseScope = rememberCoroutineScope()

            val rows = remember(mixedItems, state.expandedObjectListKeys, collapsingKeys) {
                flattenTree(mixedItems, state.expandedObjectListKeys, collapsingKeys)
            }

            // Klíče řádků, které už jednou doanimovaly nástup – LazyColumn při rychlém
            // scrollu velkých stromů komponuje/dekomponuje řádky mimo viewport nanovo,
            // takže bez téhle paměti by se enter animace (fade+expand) přehrávala znovu
            // při každém scrollnutí položky zpět do viewportu, což scroll citelně "cuká".
            // Tahle množina žije mimo scope jednotlivé lazy položky, takže scroll ji nemaže.
            val animatedOnceKeys = remember { mutableSetOf<String>() }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().padding(end = dimens.md),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(dimens.xs)
            ) {
                items(items = rows, key = { it.item.key }) { row ->
                    val color = if (row.item.color == Color.Black && LocalMongeColors.current.isDark) Color.White else row.item.color
                    val guideColor = if (row.rootColor == Color.Black && LocalMongeColors.current.isDark) Color.White else row.rootColor

                    // ✅ animace rozbalení/sbalení: nově vzniklé child řádky jemně "vyjedou"
                    // shora dolů; při zabalení řádek chvíli zůstane a doanimuje zmizení
                    // (řízeno přes row.isClosing, viz flattenTree/collapsingKeys výše)
                    val alreadyAnimated = row.item.key in animatedOnceKeys
                    val visibleState = remember(row.item.key) {
                        MutableTransitionState(alreadyAnimated).apply { targetState = true }
                    }
                    LaunchedEffect(row.item.key) {
                        animatedOnceKeys.add(row.item.key)
                    }
                    LaunchedEffect(row.isClosing) {
                        visibleState.targetState = !row.isClosing
                    }
                    AnimatedVisibility(
                        visibleState = visibleState,
                        enter = fadeIn(tween(160)) + expandVertically(
                            animationSpec = tween(180),
                            expandFrom = Alignment.Top
                        ),
                        exit = fadeOut(tween(120)) + shrinkVertically(
                            animationSpec = tween(150),
                            shrinkTowards = Alignment.Top
                        )
                        // ⚠️ NEpřidávej zpět Modifier.animateItemPlacement() zde – v kombinaci
                        // s AnimatedVisibility uvnitř LazyColumn(items(key=...)) způsobuje pád
                        // ArrayIndexOutOfBoundsException v LazyListItemAnimator, když se seznam
                        // řádků (rows) skokově změní (typicky vyprázdní), viz crash report.
                    ) {
                        InspectorObjectListItem(
                            name = row.item.name,
                            color = color,
                            is3D = row.item.is3D,
                            subtitle = if (row.item.is3D) "3D" else null,
                            superscript = row.item.superscript,
                            subscript = row.item.subscript,
                            icon = row.item.icon ?: objectListIconFromText(row.item.key, row.item.name),
                            isSelected = row.item.isSelected(),
                            depth = row.depth,               // ✅
                            guideColor = guideColor,         // ✅
                            isExpandable = row.isExpandable, // ✅
                            isExpanded = row.isExpanded,     // ✅
                            onToggleExpand = {
                                val k = row.item.key
                                if (state.expandedObjectListKeys.contains(k)) {
                                    state.expandedObjectListKeys.remove(k)
                                    if (!collapsingKeys.contains(k)) collapsingKeys.add(k)
                                    // až se potomci zabalí a zmizí ze `rows`, ať se při dalším
                                    // rozbalení jejich enter animace přehraje znovu (jinak by
                                    // po prvním rozbalení navždy "přeskakovala", viz animatedOnceKeys)
                                    animatedOnceKeys.removeAll { it.startsWith("$k/") }
                                    collapseScope.launch {
                                        delay(220)
                                        collapsingKeys.remove(k)
                                    }
                                } else {
                                    collapsingKeys.remove(k)
                                    state.expandedObjectListKeys.add(k)
                                }
                            },
                            onClick = row.item.onClick
                        )
                    }
                }
            }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(listState),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(8.dp)
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun InspectorObjectListItem(
    name: String,
    color: Color,
    isSelected: Boolean,
    is3D: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null,
    superscript: String? = null,
    subscript: String? = null,
    icon: ObjectListIcon = ObjectListIcon.Fallback,

    depth: Int = 0,
    guideColor: Color = Color.Gray,
    isExpandable: Boolean = false,
    isExpanded: Boolean = false,
    onToggleExpand: (() -> Unit)? = null,
) {
    val ui = SettingsManager.current.UIscale/75f
    val colors = LocalMongeColors.current
    val dimens = LocalMongeDimens.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var isHovered by remember { mutableStateOf(false) }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is HoverInteraction.Enter -> isHovered = true
                is HoverInteraction.Exit  -> isHovered = false
            }
        }
    }

    val shape = RoundedCornerShape(dimens.radiusMd)
    val targetBg = when {
        isSelected -> colors.selected.copy(alpha = if (colors.isDark) 0.20f else 0.12f)
        isPressed  -> colors.base.copy(alpha = 0.14f)
        isHovered  -> colors.hover.copy(alpha = if (colors.isDark) 0.16f else 0.09f)
        else       -> Color.Transparent
    }
    val bg by animateColorAsState(targetBg, label = "objectListItemBg")
    val scale by animateFloatAsState(if (isPressed) 0.99f else 1f, label = "objectListItemScale")
    val resolvedSubtitle = subtitle ?: if (is3D) "3D" else null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min) // umožní vodicím linkám vyplnit přesně výšku řádku
            .heightIn(min = dimens.leftToolWidth)
            .clip(shape)
            .background(bg, shape)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .pointerHoverIcon(PointerIcon.Hand)
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .padding(horizontal = dimens.sm, vertical = dimens.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ✅ vodicí linky stromu – jedna tenká čára za každou úroveň zanoření,
        // obarvená podle kořenového (top-level) objektu, aby bylo hned vidět
        // ke kterému objektu daný child/grandchild patří
        if (depth > 0) {
            val guideLineColor = guideColor.copy(alpha = if (colors.isDark) 0.32f else 0.24f)
            repeat(depth) {
                Box(
                    modifier = Modifier
                        .width(dimens.lg)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(guideLineColor)
                    )
                }
            }
        }

        // ✅ disclosure triangle (klik jen na šipku) – plynulá rotace + hover zvýraznění
        if (isExpandable) {
            val arrowInteractionSource = remember { MutableInteractionSource() }
            val isArrowHovered by arrowInteractionSource.collectIsHoveredAsState()

            val arrowRotation by animateFloatAsState(
                targetValue = if (isExpanded) 90f else 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "objectListArrowRotation"
            )
            val arrowHoverBg by animateColorAsState(
                targetValue = if (isArrowHovered) colors.hover.copy(alpha = if (colors.isDark) 0.30f else 0.16f) else Color.Transparent,
                label = "objectListArrowHoverBg"
            )
            val arrowScale by animateFloatAsState(
                targetValue = if (isArrowHovered) 1.15f else 1f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "objectListArrowScale"
            )

            Box(
                modifier = Modifier
                    .size(dimens.iconSm)
                    .clip(CircleShape)
                    .background(arrowHoverBg)
                    .hoverable(arrowInteractionSource)
                    .clickable(indication = null, interactionSource = arrowInteractionSource) {
                        onToggleExpand?.invoke()
                    },
                contentAlignment = Alignment.Center
            ) {
                val arrowColor = colors.text.copy(alpha = if (isArrowHovered) 0.95f else 0.75f)
                Canvas(
                    modifier = Modifier
                        .size(dimens.iconSm)
                        .graphicsLayer {
                            rotationZ = arrowRotation
                            scaleX = arrowScale
                            scaleY = arrowScale
                        }
                ) {
                    val arrow = Path().apply {
                        moveTo(size.width * 0.36f, size.height * 0.24f)
                        lineTo(size.width * 0.72f, size.height * 0.50f)
                        lineTo(size.width * 0.36f, size.height * 0.76f)
                        close()
                    }
                    drawPath(arrow, arrowColor)
                }
            }
            Spacer(Modifier.width(dimens.xs))
        } else {
            Spacer(Modifier.width(dimens.iconSm + dimens.xs)) // zarovnání s řádky co šipku mají
        }

        ObjectListTypeIcon(icon = icon, color = color, modifier = Modifier.size(dimens.iconSm))
        Spacer(Modifier.width(dimens.sm))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            NameWithSupSubText(
                name = name,
                superscript = superscript,
                subscript = subscript,
                isSelected = isSelected,
                colors = colors
            )
            if (!resolvedSubtitle.isNullOrBlank()) {
                Text(
                    text = resolvedSubtitle,
                    fontSize = 11.sp,
                    color = Color.Red,
                    maxLines = 1
                )
            }
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .width(dimens.xs)
                    .height(dimens.iconSm)
                    .clip(RoundedCornerShape(dimens.radiusSm))
                    .background(colors.selected.copy(alpha = 0.55f))
                    .alpha(0.95f)
            )
        }
    }
}

fun <T> List<T>.distinctByParentOf(parentSelector: (T) -> Any?): List<T> {
    val seen = mutableSetOf<Any?>()
    return this.filter {
        val key = parentSelector(it) ?: it
        seen.add(key)
    }
}

private fun buildIntersectionGroupChildren(
    state: MongeState,
    group: IntersectionGroup,
    clearAllOnClick: Boolean,
): List<UiTreeItem> {
    val intersectionPolygons = intersectionPolygonsForGroup(state, group)
    val polygonSegmentIds = intersectionPolygons
        .flatMap { it.segmentIds3D }
        .toSet()

    return group.parts.mapNotNull { part ->
        when (part.kind) {
            IntersectionPartKind.POINT3D -> {
                val point = state.sharedPoints3D.firstOrNull { it.id == part.id } ?: return@mapNotNull null
                UiTreeItem(
                    key = "intersection:${group.id}:point:${point.id}",
                    sortIndex = sortKeyDesc(point.creationIndex),
                    name = "Bod ${point.name}",
                    color = point.color,
                    is3D = true,
                    icon = ObjectListIcon.Point,
                    isSelected = { state.selectedPoints3D.any { it.id == point.id } },
                    onClick = {
                        if (clearAllOnClick) clearSelection(state)
                        toggleSelectionPoint3D(point, state)
                    }
                )
            }
            IntersectionPartKind.LINE3D -> {
                val line = state.lines3D.firstOrNull { it.id == part.id } ?: return@mapNotNull null
                UiTreeItem(
                    key = "intersection:${group.id}:line:${line.id}",
                    sortIndex = sortKeyDesc(line.creationIndex),
                    name = "Přímka ${line.name}",
                    color = line.color,
                    is3D = true,
                    icon = ObjectListIcon.Line,
                    isSelected = { state.selectedLines3D.any { it.id == line.id } },
                    onClick = {
                        if (clearAllOnClick) clearSelection(state)
                        toggleSelectionLine3D(line, state)
                    }
                )
            }
            IntersectionPartKind.SEGMENT3D -> {
                if (part.id in polygonSegmentIds) return@mapNotNull null
                val segment = state.segments3D.firstOrNull { it.id == part.id } ?: return@mapNotNull null
                UiTreeItem(
                    key = "intersection:${group.id}:segment:${segment.id}",
                    sortIndex = sortKeyDesc(segment.creationIndex),
                    name = segment.name.ifBlank { "Úsečka" },
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
            IntersectionPartKind.CONIC3D -> {
                val conic = state.conics3D.firstOrNull { it.id == part.id } ?: return@mapNotNull null
                val type = conicType3DByProjections(state, conic)
                UiTreeItem(
                    key = "intersection:${group.id}:conic:${conic.id}",
                    sortIndex = sortKeyDesc(conic.creationIndex),
                    name = "$type ${conic.name}",
                    color = conic.color,
                    is3D = true,
                    icon = when (type) {
                        "Hyperbola" -> ObjectListIcon.Hyperbola
                        "Parabola" -> ObjectListIcon.Parabola
                        "Kružnice" -> ObjectListIcon.Circle
                        else -> ObjectListIcon.Ellipse
                    },
                    isSelected = { isConic3DSelected(state, conic.id) },
                    onClick = {
                        if (clearAllOnClick) clearSelection(state)
                        selectConic3DProjections(state, conic.id, clearAllOnClick = false)
                    }
                )
            }
            IntersectionPartKind.CURVE3D -> {
                val curve = state.curves3D.firstOrNull { it.id == part.id } ?: return@mapNotNull null
                UiTreeItem(
                    key = "intersection:${group.id}:curve:${curve.id}",
                    sortIndex = sortKeyDesc(curve.creationIndex),
                    name = "Křivka ${curve.name}",
                    color = curve.color,
                    is3D = true,
                    icon = ObjectListIcon.Curve,
                    isSelected = { state.selectedCurve3DId == curve.id },
                    onClick = {
                        if (clearAllOnClick) clearSelection(state)
                        toggleSelectionCurve3D(state, curve.id)
                    },
                    children = buildCurve3DChildren(state, curve, clearAllOnClick)
                )
            }
        }
    } + intersectionPolygons.map { poly ->
        UiTreeItem(
            key = "intersection:${group.id}:polygon:${poly.id}",
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
            children = buildPolygonChildren(state, poly, clearAllOnClick)
        )
    }
}

fun intersectionPolygonIds(state: MongeState): Set<String> =
    state.intersectionGroups
        .flatMap { group -> rawIntersectionPolygonsForGroup(state, group) }
        .mapTo(mutableSetOf()) { it.id }

fun intersectionPolygonsForGroup(state: MongeState, group: IntersectionGroup): List<RegularPolygon3D> =
    rawIntersectionPolygonsForGroup(state, group)
        .groupBy { polygon -> polygon.segmentIds3D.toSet() }
        .values
        .map { polygons ->
            polygons.maxWith(
                compareBy<RegularPolygon3D> { polygonObjectListChildScore(it) }
                    .thenBy { it.creationIndex }
            )
        }

private fun rawIntersectionPolygonsForGroup(state: MongeState, group: IntersectionGroup): List<RegularPolygon3D> {
    val groupSegmentIds = group.parts
        .asSequence()
        .filter { it.kind == IntersectionPartKind.SEGMENT3D }
        .map { it.id }
        .toSet()
    if (groupSegmentIds.isEmpty()) return emptyList()

    return state.polygons3D.filter { polygon ->
        polygon.segmentIds3D.isNotEmpty() && polygon.segmentIds3D.all { it in groupSegmentIds }
    }
}

private fun polygonObjectListChildScore(polygon: RegularPolygon3D): Int =
    polygon.vertexPointIds.size +
        polygon.vertexPointIdsPudorys.size +
        polygon.vertexPointIdsNarys.size +
        polygon.segmentIds3D.size +
        polygon.segmentIdsPudorys.size +
        polygon.segmentIdsNarys.size +
        polygon.segmentIdsAxo.size

/**
 * Spolehlivá klasifikace 3D kuželosečky pro ObjectList: hyperbola/parabola se určí z input map
 * jejích průmětů (jako v SelectionInfo), kružnice/elipsa z 3D matice. Vyhýbá se numericky
 * nestabilnímu diskriminantu u parabol (getConicType3D míchal parabolu s elipsou/hyperbolou).
 */
fun conicType3DByProjections(state: MongeState, conic3D: ConicSection3D): String {
    fun match(c: ConicSection2D): Boolean = c.parentId == conic3D.id || c.parent?.id == conic3D.id
    val pud = state.conicsPudorys.firstOrNull { match(it) }
    val nar = state.conicsNarys.firstOrNull { match(it) }
    val bok = state.conicsBokorys.firstOrNull { match(it) }
    val axo = state.conicsAxo.firstOrNull { match(it) }
    if ((pud != null && state.hyperbolaInputsPudorys.containsKey(pud.id)) ||
        (nar != null && state.hyperbolaInputsNarys.containsKey(nar.id)) ||
        (bok != null && state.hyperbolaInputsBokorys.containsKey(bok.id)) ||
        (axo != null && state.hyperbolaInputsAxo.containsKey(axo.id))
    ) return "Hyperbola"
    if ((pud != null && state.conicInputPointsPudorys[pud.id]?.third == Offset.Unspecified) ||
        (nar != null && state.conicInputPointsNarys[nar.id]?.third == Offset.Unspecified) ||
        (bok != null && state.conicInputPointsBokorys[bok.id]?.third == Offset.Unspecified) ||
        (axo != null && state.conicInputPointsAxo[axo.id]?.third == Offset.Unspecified)
    ) return "Parabola"
    return when (getConicType3D(conic3D.matrix)) {
        "Hyperbola" -> "Hyperbola"
        "Kružnice" -> "Kružnice"
        else -> "Elipsa"
    }
}
fun getConicType3D(matrix: Matrix3x3): String {
    // čti jako Double kvůli přesnosti
    val A = matrix.m00.toDouble()
    val B = (2.0 * matrix.m01.toDouble())
    val C = matrix.m11.toDouble()

    // měřítko pro relativní eps (kvadratická část)
    val s = maxOf(1.0, abs(A), abs(B), abs(C))

    // diskriminant
    val D = B * B - 4.0 * A * C

    // tolerance: relativně k s^2, protože D je řádu "kvadrát"
    val eps = 1e-10 * (s * s)

    // kružnice: A≈C a B≈0, taky relativně
    if (abs(A - C) <= 1e-10 * s && abs(B) <= 1e-10 * s) {
        return "Kružnice"
    }

    return when {
        D > eps  -> "Hyperbola"
        D < -eps -> "Elipsa"
        else     -> "Parabola"
    }
}
private fun ensureSelectPudorysSegment(seg: Segment2DPudorys, state: MongeState) {
    val isSelected = state.selectedSegmentsPudorys.contains(seg)
    if (!isSelected) toggleSelectionPudorysSegment(seg, state)
}
private fun ensureSelectNarysSegment(seg: Segment2DNarys, state: MongeState) {
    val isSelected = state.selectedSegmentsNarys.contains(seg)
    if (!isSelected) toggleSelectionNarysSegment(seg, state)
}
private fun ensureSelectBokorysSegment(seg: Segment2DBokorys, state: MongeState) {
    val isSelected = state.selectedSegmentsBokorys.contains(seg)
    if (!isSelected) toggleSelectionBokorysSegment(seg, state)
}
private fun ensureSelectAxoSegment(seg: Segment2DAxo, state: MongeState) {
    val isSelected = state.selectedSegmentsAxo.contains(seg)
    if (!isSelected) toggleSelectionAxoSegment(seg, state)
}
private fun ensureSelectPoint3D(p: Point3D, state: MongeState) {
    val isSelected = state.selectedPoints3D.any { it.id == p.id }
    if (!isSelected) toggleSelectionPoint3D(p, state)
}
private fun ensureSelectPudorysPoint(p: Point3DPudorys, state: MongeState) {
    val isSelected = state.selectedPointsPudorys.contains(p)
    if (!isSelected) toggleSelectionPudorys(p, state)
}
private fun ensureSelectNarysPoint(p: Point3DNarys, state: MongeState) {
    val isSelected = state.selectedPointsNarys.contains(p)
    if (!isSelected) toggleSelection(p, state)
}
private fun ensureSelectBokorysPoint(p: Point3DBokorys, state: MongeState) {
    val isSelected = state.selectedPointsBokorys.contains(p)
    if (!isSelected) toggleSelectionBokorys(p, state)
}
private fun ensureSelectAxoPoint(p: Point3DAxo, state: MongeState) {
    val isSelected = state.selectedPointsAxo.contains(p)
    if (!isSelected) toggleSelectionAxo(p, state)
}
private fun ensureSelectNarysConic(p: ConicSectionNarys, state: MongeState) {
    val isSelected = state.selectedConicsNarys.contains(p)
    if (!isSelected) toggleSelectionNarysConic(p, state)
}
private fun ensureSelectPudorysConic(p: ConicSectionPudorys, state: MongeState) {
    val isSelected = state.selectedConicsPudorys.contains(p)
    if (!isSelected) toggleSelectionPudorysConic(p, state)
}
private fun ensureSelectBokorysConic(p: ConicSectionBokorys, state: MongeState) {
    val isSelected = state.selectedConicsBokorys.contains(p)
    if (!isSelected) toggleSelectionBokorysConic(p, state)
}
private fun ensureSelectAxoConic(p: ConicSectionAxo, state: MongeState) {
    val isSelected = state.selectedConicsAxo.contains(p)
    if (!isSelected) state.selectedConicsAxo.add(p)
}


fun selectConicalSurface(surface: ConicalSurface3D, state: MongeState) {
    fun showChild(showInAxo: Boolean): Boolean =
        state.projectionMode != ProjectionMode.AXO || showInAxo

    // 2D projekce řídicí kuželosečky (podle parenta na ConicSection3D)
    state.conicsPudorys
        .filter { it.parent?.id == surface.directrixId || it.parentId == surface.directrixId }
        .filter { showChild(it.showInAxo) }
        .forEach { ensureSelectPudorysConic(it, state) }

    state.conicsNarys
        .filter { it.parent?.id == surface.directrixId || it.parentId == surface.directrixId }
        .filter { showChild(it.showInAxo) }
        .forEach { ensureSelectNarysConic(it, state) }

    // siluetové úsečky
    state.conicsBokorys
        .filter { it.parent?.id == surface.directrixId || it.parentId == surface.directrixId }
        .filter { showChild(it.showInAxo) }
        .forEach { ensureSelectBokorysConic(it, state) }
    state.conicsAxo
        .filter { it.parent?.id == surface.directrixId || it.parentId == surface.directrixId }
        .filter { showAxoProjectionChildren(state) }
        .filter { showChild(it.showInAxo) }
        .forEach { ensureSelectAxoConic(it, state) }

    state.segmentsPudorys
        .filter { it.id in surface.edgeSegIdsPudorys2D || (it.isConicalSilhouette && it.conicalSurfaceId == surface.id) }
        .filter { showChild(it.showInAxo) }
        .forEach { ensureSelectPudorysSegment(it, state) }

    state.segmentsNarys
        .filter { it.id in surface.edgeSegIdsNarys2D || (it.isConicalSilhouette && it.conicalSurfaceId == surface.id) }
        .filter { showChild(it.showInAxo) }
        .forEach { ensureSelectNarysSegment(it, state) }
    state.segmentsBokorys
        .filter { it.id in surface.edgeSegIdsBokorys2D || (it.isConicalSilhouette && it.conicalSurfaceId == surface.id) }
        .filter { showChild(it.showInAxo) }
        .forEach { ensureSelectBokorysSegment(it, state) }
    state.segmentsAxo
        .filter { it.id in surface.edgeSegIdsAxo2D || (it.isConicalSilhouette && it.conicalSurfaceId == surface.id) }
        .filter { showAxoProjectionChildren(state) }
        .filter { showChild(it.showInAxo) }
        .forEach { ensureSelectAxoSegment(it, state) }

    // projekce vrcholu (pokud existují)
    val apex3D = state.sharedPoints3D.find { it.id == surface.apexId }
    if (apex3D != null) {
        ensureSelectPoint3D(apex3D, state)
    }
    state.pointsPudorys
        .filter { it.id == surface.apexProjPudorysId || it.id in surface.edgePointIdsPudorys2D }
        .filter { showChild(it.showInAxo) }
        .forEach { ensureSelectPudorysPoint(it, state) }
    state.pointsNarys
        .filter { it.id == surface.apexProjNarysId || it.id in surface.edgePointIdsNarys2D }
        .filter { showChild(it.showInAxo) }
        .forEach { ensureSelectNarysPoint(it, state) }
    state.pointsBokorys
        .filter { it.id == surface.apexProjBokorysId || it.id in surface.edgePointIdsBokorys2D }
        .filter { showChild(it.showInAxo) }
        .forEach { ensureSelectBokorysPoint(it, state) }
    state.pointsAxo
        .filter { it.id == surface.apexProjAxoId || it.id in surface.edgePointIdsAxo2D }
        .filter { showAxoProjectionChildren(state) }
        .filter { showChild(it.showInAxo) }
        .forEach { ensureSelectAxoPoint(it, state) }

    if (surface !in state.selectedCone) state.selectedCone += surface
}
fun selectCylindricalSurface(surface: CylindricalSurface3D, state: MongeState) {
    fun showChild(showInAxo: Boolean): Boolean =
        state.projectionMode != ProjectionMode.AXO || showInAxo

    val conicIds = buildSet {
        add(surface.directrixId)
        surface.lowerConicId?.let(::add)
        surface.upperConicId?.let(::add)
    }

    fun matchConic(pid: String?): Boolean = pid in conicIds

    state.conicsPudorys
        .filter { matchConic(it.parent?.id ?: it.parentId) }
        .filter { showChild(it.showInAxo) }
        .forEach { ensureSelectPudorysConic(it, state) }
    state.conicsNarys
        .filter { matchConic(it.parent?.id ?: it.parentId) }
        .filter { showChild(it.showInAxo) }
        .forEach { ensureSelectNarysConic(it, state) }
    state.conicsBokorys
        .filter { matchConic(it.parent?.id ?: it.parentId) }
        .filter { showChild(it.showInAxo) }
        .forEach { ensureSelectBokorysConic(it, state) }
    state.conicsAxo
        .filter { matchConic(it.parent?.id ?: it.parentId) }
        .filter { showAxoProjectionChildren(state) }
        .filter { showChild(it.showInAxo) }
        .forEach { ensureSelectAxoConic(it, state) }

    state.segmentsPudorys
        .filter { it.id in surface.edgeSegIdsPudorys2D }
        .filter { showChild(it.showInAxo) }
        .forEach { ensureSelectPudorysSegment(it, state) }
    state.segmentsNarys
        .filter { it.id in surface.edgeSegIdsNarys2D }
        .filter { showChild(it.showInAxo) }
        .forEach { ensureSelectNarysSegment(it, state) }
    state.segmentsBokorys
        .filter { it.id in surface.edgeSegIdsBokorys2D }
        .filter { showChild(it.showInAxo) }
        .forEach { ensureSelectBokorysSegment(it, state) }
    state.segmentsAxo
        .filter { it.id in surface.edgeSegIdsAxo2D }
        .filter { showAxoProjectionChildren(state) }
        .filter { showChild(it.showInAxo) }
        .forEach { ensureSelectAxoSegment(it, state) }

    state.pointsPudorys
        .filter { it.id in surface.edgePointIdsPudorys2D }
        .filter { showChild(it.showInAxo) }
        .forEach { ensureSelectPudorysPoint(it, state) }
    state.pointsNarys
        .filter { it.id in surface.edgePointIdsNarys2D }
        .filter { showChild(it.showInAxo) }
        .forEach { ensureSelectNarysPoint(it, state) }
    state.pointsBokorys
        .filter { it.id in surface.edgePointIdsBokorys2D }
        .filter { showChild(it.showInAxo) }
        .forEach { ensureSelectBokorysPoint(it, state) }
    state.pointsAxo
        .filter { it.id in surface.edgePointIdsAxo2D }
        .filter { showAxoProjectionChildren(state) }
        .filter { showChild(it.showInAxo) }
        .forEach { ensureSelectAxoPoint(it, state) }

    if (surface !in state.selectedCylinder) state.selectedCylinder += surface
}
@Composable
fun NameWithSupSubText(
    name: String,
    superscript: String?,
    subscript: String?,
    isSelected: Boolean,
    colors: MongeColorsState, // nebo tvoje barvy
    modifier: Modifier = Modifier
) {
    val ui = SettingsManager.current.UIscale/75f
    val hasSup = !superscript.isNullOrBlank()
    val hasSub = !subscript.isNullOrBlank()
    val showSlot = hasSup || hasSub

    val slotId = "idxSlot"

    // šířku slotu klidně uprav; když chceš dynamiku, dá se to měřit, ale to už je heavy.
    val slotWidth = if (showSlot) 18f*ui.sp else 0.sp
    val slotHeight = 16f*ui.sp

    val inline = if (showSlot) {
        mapOf(
            slotId to InlineTextContent(
                placeholder = Placeholder(
                    width = slotWidth,
                    height = slotHeight,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (hasSup) {
                        Text(
                            text = superscript!!,
                            fontSize = 8f*ui.sp,
                            color = colors.text.copy(alpha = 0.85f),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(y = (-2f*ui).dp)
                        )
                    }
                    if (hasSub) {
                        Text(
                            text = subscript!!,
                            fontSize = 8f*ui.sp,
                            color = colors.text.copy(alpha = 0.85f),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .offset(y = (2f*ui).dp)
                        )
                    }
                }
            }
        )
    } else emptyMap()

    Text(
        modifier = modifier,
        text = buildAnnotatedString {
            append(name)
            if (showSlot) appendInlineContent(slotId, "[idx]")
        },
        inlineContent = inline,
        color = colors.text,
        fontSize = 15f*ui.sp,
        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
    )
}
fun compactLetters(names: List<String>): String {
    val letters = names.map { extractBaseLetter(it) }
    if (letters.isEmpty()) return ""
    return if (letters.size <= 4) letters.joinToString("") else "${letters.first()}…${letters.last()}"
}
fun polygonDisplayName(state: MongeState, poly: RegularPolygon3D): String {
    val names = poly.vertexPointIds.mapNotNull { id ->
        state.sharedPoints3D.firstOrNull { it.id == id }?.name
    }
    val lettersCompact = compactLetters(names)

    val n = when (poly.n) {
        3 -> "Trojúhelník"
        4 -> "Čtverec"
        else -> "${poly.n}-úhelník"
    }

    return if (lettersCompact.isNotEmpty()) {
        "$n $lettersCompact"
    } else {
        "Mnohoúhelník"
    }
}
fun extractBaseLetter(name: String): Char {
    // vezmeme první velké písmeno A–Z v názvu; fallback = první znak
    for (ch in name) if (ch in 'A'..'Z') return ch
    return name.firstOrNull() ?: '?'
}
fun isPointSelected(parent: Point3D?, src: Any, proj: ListProjectionType,state: MongeState): Boolean {
    if (parent != null) {
        return state.selectedPointsPudorys.any { it.parent == parent } ||
                state.selectedPointsNarys.any { it.parent == parent }||
                state.selectedPointsBokorys.any { it.parent == parent }||
                (showAxoProjectionChildren(state) && state.selectedPointsAxo.any{it.parent == parent})
    }
    return when (proj) {
        ListProjectionType.PUDORYS -> state.selectedPointsPudorys.contains(src as Point3DPudorys)
        ListProjectionType.NARYS   -> state.selectedPointsNarys.contains(src as Point3DNarys)
        ListProjectionType.BOKORYS -> state.selectedPointsBokorys.contains(src as Point3DBokorys)
        ListProjectionType.AXO -> state.selectedPointsAxo.contains(src as Point3DAxo)
    }
}

private fun projectedLinePointsFor(parentLine: Line3D, state: MongeState): List<Any> =
    buildList {
        state.pointsPudorys.filter { isProjectedLinePointOf(it, parentLine) }.forEach(::add)
        state.pointsNarys.filter { isProjectedLinePointOf(it, parentLine) }.forEach(::add)
        state.pointsBokorys.filter { isProjectedLinePointOf(it, parentLine) }.forEach(::add)
        if (showAxoProjectionChildren(state)) {
            state.pointsAxo.filter { isProjectedLinePointOf(it, parentLine) }.forEach(::add)
        }
    }

private fun isProjectedLinePointSelected(parentLine: Line3D, state: MongeState): Boolean =
    state.selectedPointsPudorys.any { isProjectedLinePointOf(it, parentLine) } ||
            state.selectedPointsNarys.any { isProjectedLinePointOf(it, parentLine) } ||
            state.selectedPointsBokorys.any { isProjectedLinePointOf(it, parentLine) } ||
            (showAxoProjectionChildren(state) && state.selectedPointsAxo.any { isProjectedLinePointOf(it, parentLine) })

private fun toggleProjectedLinePointSelection(point: Any, state: MongeState) {
    when (point) {
        is Point3DPudorys -> toggleSelectionPudorys(point, state)
        is Point3DNarys -> toggleSelection(point, state)
        is Point3DBokorys -> toggleSelectionBokorys(point, state)
        is Point3DAxo -> toggleSelectionAxo(point, state)
    }
}

fun isLineSelected(
    parent: Line3D?,
    src: Any?,
    proj: ListProjectionType,
    state: MongeState
): Boolean {
    val selectedIds =
        state.selectedLinesPudorys.map { it.id }.toSet() +
                state.selectedLinesNarys.map { it.id }.toSet()+state.selectedLinesBokorys.map { it.id }.toSet() + state.selectedLinesAxo.map { it.id }.toSet()

    if (parent != null) {
        val pid = parent.id
        val projIds = buildSet {
            state.lines3DPudorys.filter { it.parent?.id == pid || it.parentId == pid }.forEach { add(it.id) }
            state.lines3DNarys.filter { it.parent?.id == pid || it.parentId == pid }.forEach { add(it.id) }
            state.lines3DBokorys.filter { it.parent?.id == pid || it.parentId == pid }.forEach { add(it.id) }
            if (showAxoProjectionChildren(state)) {
                state.lines3DAxo.filter { it.parent?.id == pid || it.parentId == pid }.forEach { add(it.id) }
            }
        }

        return projIds.any { it in selectedIds } || isProjectedLinePointSelected(parent, state)
    }

    // 2D samostatná přímka
    return when (proj) {
        ListProjectionType.PUDORYS ->
            (src as? Line3DProjectionPudorys)?.id in selectedIds

        ListProjectionType.NARYS ->
            (src as? Line3DProjectionNarys)?.id in selectedIds

        ListProjectionType.BOKORYS ->
            (src as? Line3DProjectionBokorys)?.id in selectedIds

        ListProjectionType.AXO ->
            (src as? Line3DProjectionAxo)?.id in selectedIds
    }
}
fun isSegmentSelected(parent: Segment3D?, src: Any, proj: ListProjectionType, state: MongeState): Boolean {
    if (parent != null) {
        return state.selectedSegmentsPudorys.any { it.parent == parent } ||
                state.selectedSegmentsNarys.any { it.parent == parent }
    }
    return when (proj) {
        ListProjectionType.PUDORYS -> state.selectedSegmentsPudorys.contains(src as Segment2DPudorys)
        ListProjectionType.NARYS   -> state.selectedSegmentsNarys.contains(src as Segment2DNarys)
        ListProjectionType.BOKORYS -> state.selectedSegmentsBokorys.contains(src as Segment2DBokorys)
        ListProjectionType.AXO -> state.selectedSegmentsAxo.contains(src as Segment2DAxo)
    }
}
enum class ChildKind { POINT, SEGMENT, CONIC, ARC, CURVE }


data class ChildResolved(
    val kind: ChildKind,
    val key: String,
    val sortIndex: Long,
    val name: String,
    val color: Color,
    val is3D: Boolean,
    val superscript: String? = null,
    val subscript: String? = null,
    val icon: ObjectListIcon? = null,
    val isSelected: () -> Boolean,
    val onClick: () -> Unit
)

fun computeArcIdsUsedByRevolutions(state: MongeState): Set<String> {
    val used = HashSet<String>()
    state.solidsOfRevolutionNarys.forEach { solid ->
        solid.meridianIdsNarys.forEach(used::add)
        solid.mirroredMeridianIdsNarys.forEach(used::add)
    }
    return used
}
fun computeArcIdsUsedByRevolutionsPud(state: MongeState): Set<String> {
    val used = HashSet<String>()
    state.solidsOfRevolutionPudorys.forEach { solid ->
        solid.meridianIdsPudorys.forEach(used::add)
        solid.mirroredMeridianIdsPudorys.forEach(used::add)
    }
    return used
}
fun computeConic3DIdsUsedByRevolutions(state: MongeState): Set<String> {
    val used = HashSet<String>()

    // pomocné: z id projekce v P/N přidej parent conic3D id
    fun addParentFromPudorys(id: String) {
        state.conicsPudorys.firstOrNull { it.id == id }?.let { c ->
            (c.parent?.id ?: c.parentId)?.let(used::add)
            return
        }
        state.circlesPudorys.firstOrNull { it.id == id }?.let { c ->
            c.parentId?.let(used::add)
        }
    }

    fun addParentFromNarys(id: String) {
        state.conicsNarys.firstOrNull { it.id == id }?.let { c ->
            (c.parent?.id ?: c.parentId)?.let(used::add)
            return
        }
        state.circlesNarys.firstOrNull { it.id == id }?.let { c ->
            c.parentId?.let(used::add)
        }
    }

    state.solidsOfRevolutionNarys.forEach { solid ->
        solid.circleIdsPudorys.forEach(::addParentFromPudorys)
        solid.circleIdsNarys.forEach(::addParentFromNarys)
    }
    state.solidsOfRevolutionPudorys.forEach { solid ->
        solid.circleIdsPudorys.forEach(::addParentFromPudorys)
        solid.circleIdsNarys.forEach(::addParentFromNarys)
    }

    return used
}
