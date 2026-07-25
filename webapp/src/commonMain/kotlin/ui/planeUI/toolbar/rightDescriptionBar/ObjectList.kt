package ui.planeUI.toolbar.rightDescriptionBar

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import draw.mongescreen.labels.clearSelection
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import model.*
import model.classes.ConicSectionBokorys
import model.classes.ConicSectionNarys
import model.classes.ConicSectionPudorys
import model.classes.HelpSegmentPudorys
import model.classes.Point2DProjection
import model.classes.Point3DAxo
import model.classes.Point3DBokorys
import model.classes.Point3DNarys
import model.classes.Point3DPudorys
import model.classes.PlanePolygon2D
import model.classes.RegularPolygon3D
import model.classes.Segment2DBokorys
import model.classes.Segment2DNarys
import model.classes.Segment2DPudorys
import model.classes.Segment3D
import monge.input.selection.*
import state.MongeState
import ui.mongeui.toolbar.rightDescriptionBar.ObjectList.InspectorObjectListItem
import ui.mongeui.toolbar.rightDescriptionBar.ObjectList.ObjectListIcon
import ui.mongeui.toolbar.rightDescriptionBar.ObjectList.UiTreeItem
import ui.mongeui.toolbar.rightDescriptionBar.ObjectList.buildPolygonChildren
import ui.mongeui.toolbar.rightDescriptionBar.ObjectList.buildPlanePolygonChildren
import ui.mongeui.toolbar.rightDescriptionBar.ObjectList.distinctByParentOf
import ui.mongeui.toolbar.rightDescriptionBar.ObjectList.flattenTree
import ui.mongeui.toolbar.rightDescriptionBar.ObjectList.objectListIconFromText
import ui.mongeui.toolbar.rightDescriptionBar.ObjectList.sortKeyDesc


private fun conicIconFromPlaneType(type: String): ObjectListIcon = when (type.lowercase()) {
    "hyperbola" -> ObjectListIcon.Hyperbola
    "parabola" -> ObjectListIcon.Parabola
    else -> ObjectListIcon.Ellipse
}

@Composable
fun ObjectListPlane(state: MongeState, clearAllOnClick: Boolean) {
    val listState = rememberLazyListState()

    fun isPointSelected(parent: Point3D?, src: Any, proj: ListProjectionType): Boolean {
        if (parent != null) {
            return state.selectedPointsPudorys.any { it.parent == parent } ||
                    state.selectedPointsNarys.any { it.parent == parent }
        }
        return when (proj) {
            ListProjectionType.PUDORYS -> state.selectedPointsPudorys.contains(src as Point3DPudorys)
            ListProjectionType.NARYS   -> state.selectedPointsNarys.contains(src as Point3DNarys)
            ListProjectionType.BOKORYS -> state.selectedPointsBokorys.contains(src as Point3DBokorys)
            else            -> false
        }
    }


    fun isSegmentSelected(parent: Segment3D?, src: Any, proj: ListProjectionType): Boolean {
        if (parent != null) {
            return state.selectedSegmentsPudorys.any { it.parent == parent } ||
                    state.selectedSegmentsNarys.any { it.parent == parent }
        }
        return when (proj) {
            ListProjectionType.PUDORYS -> state.selectedSegmentsPudorys.contains(src as Segment2DPudorys)
            ListProjectionType.NARYS   -> state.selectedSegmentsNarys.contains(src as Segment2DNarys)
            ListProjectionType.BOKORYS -> state.selectedSegmentsBokorys.contains(src as Segment2DBokorys)
            else            -> false
        }
    }

    fun isConicSelected2D(src: Any, proj: ListProjectionType): Boolean = when (proj) {
        ListProjectionType.PUDORYS -> state.selectedConicsPudorys.contains(src as ConicSectionPudorys)
        ListProjectionType.NARYS   -> state.selectedConicsNarys.contains(src as ConicSectionNarys)
        ListProjectionType.BOKORYS -> state.selectedConicsBokorys.contains(src as ConicSectionBokorys)
        else            -> false
    }

    fun isCircleSelected(src: Any, proj: ListProjectionType): Boolean = when (proj) {
        ListProjectionType.PUDORYS -> state.selectedCirclesPudorys.contains(src as ConicSectionPudorys)
        ListProjectionType.NARYS   -> state.selectedCirclesNarys.contains(src as ConicSectionNarys)
        ListProjectionType.BOKORYS -> state.selectedCirclesBokorys.contains(src as ConicSectionBokorys)
        else            -> false
    }


    // -------------------------
    // 1) připrav filtrovací sety
    // -------------------------
    val polygonPoints3DIds: Set<String> =
        state.polygons3D.flatMap { it.vertexPointIds }.toHashSet()
    val polygonPointsPudIds: Set<String> =
        state.polygons3D.flatMap { it.vertexPointIdsPudorys }.toHashSet()
    val polygonPointsNarIds: Set<String> =
        state.polygons3D.flatMap { it.vertexPointIdsNarys }.toHashSet()

    val polygonSeg3DIds: Set<String> =
        state.polygons3D.flatMap { it.segmentIds3D }.toHashSet()
    val polygonSegPudIds: Set<String> =
        state.polygons3D.flatMap { it.segmentIdsPudorys }.toHashSet()
    val polygonSegNarIds: Set<String> =
        state.polygons3D.flatMap { it.segmentIdsNarys }.toHashSet()
    val planePolygonPointIds: Set<String> =
        state.planePolygons2D.flatMap { it.vertexPointIdsPudorys }.toHashSet()
    val planePolygonAidPointIds: Set<String> =
        state.planePolygons2D.flatMap { it.vertexAidPointIds }.toHashSet()
    val planePolygonSegmentIds: Set<String> =
        state.planePolygons2D.flatMap { it.segmentIdsPudorys }.toHashSet()

    // -------------------------
    // 2) listedPoints / listedLines / listedSegments (sorted před distinct)
    // -------------------------
    val listedPoints = (
            state.pointsPudorys
                .asSequence()
                .filterNot { p ->
                    p.id in planePolygonPointIds ||
                        (p.parent?.id?.let { it in polygonPoints3DIds } ?: (p.id in polygonPointsPudIds))
                }
                .map { ListedPoint(it.name, it.parent, it.isSegmentEndpoint, isProjectedLine = it.isProjectedLine, ListProjectionType.PUDORYS, it, id = it.id, color = it.color) }
                    +
                    state.pointsNarys
                        .asSequence()
                        .filterNot { p -> p.parent?.id?.let { it in polygonPoints3DIds } ?: (p.id in polygonPointsNarIds) }
                        .map { ListedPoint(it.name, it.parent, it.isSegmentEndpoint, isProjectedLine = it.isProjectedLine, ListProjectionType.NARYS, it, color = it.color) }
            )
        .filter { !it.isSegmentEndpoint }
        .filter { !it.isProjectedLine }
        .sortedByDescending { lp ->
            when (val src = lp.source) {
                is Point3DPudorys -> sortKeyDesc(src.effectiveCreationIndex)
                is Point3DNarys -> sortKeyDesc(src.effectiveCreationIndex)
                else -> Long.MIN_VALUE
            }
        }
        .toList()
        .distinctByParentOf { it.parent }

    val listedSegments =
        (
                state.segmentsPudorys
                    .asSequence()
                    .filterNot { it.isConicalSilhouette }
                    .filterNot { seg2d -> seg2d.parent?.id?.let { it in polygonSeg3DIds } ?: (seg2d.id in polygonSegPudIds) }
                    .map {
                        val nm = it.parent?.name?.ifBlank { null } ?: it.name?.ifBlank { null } ?: "Úsečka"
                        ListedSegment(nm, it.parent, it.color, ListProjectionType.PUDORYS, source = it)
                    }
                        +
                        state.segmentsNarys
                            .asSequence()
                            .filterNot { it.isConicalSilhouette }
                            .filterNot { seg2d -> seg2d.parent?.id?.let { it in polygonSeg3DIds } ?: (seg2d.id in polygonSegNarIds) }
                            .map {
                                val nm = it.parent?.name?.ifBlank { null } ?: it.name?.ifBlank { null } ?: "Úsečka"
                                ListedSegment(nm, it.parent, it.color, ListProjectionType.NARYS, source = it)
                            }
                )
            .sortedByDescending { ls ->
                when (val src = ls.source) {
                    is Segment2DPudorys -> sortKeyDesc(src.effectiveCreationIndex)
                    is Segment2DNarys -> sortKeyDesc(src.effectiveCreationIndex)
                    else -> Long.MIN_VALUE
                }
            }
            .toList()
            .distinctByParentOf { it.parent }

    // -------------------------
    // 3) mixedItems (globálně promíchané)
    // -------------------------
    fun polygonDisplayName(poly: RegularPolygon3D): String {
        fun extractBaseLetter(name: String): Char {
            for (ch in name) if (ch in 'A'..'Z') return ch
            return name.firstOrNull() ?: '?'
        }
        fun compactLetters(names: List<String>): String {
            val letters = names.map { extractBaseLetter(it) }
            if (letters.isEmpty()) return ""
            return if (letters.size <= 4) letters.joinToString("") else "${letters.first()}…${letters.last()}"
        }

        val names = poly.vertexPointIds.mapNotNull { id ->
            state.sharedPoints3D.firstOrNull { it.id == id }?.name
        }
        val lettersCompact = compactLetters(names)

        val n = when (poly.n) {
            3 -> "Trojúhelník"
            4 -> "Čtverec"
            else -> "${poly.n}-úhelník"
        }

        return if (lettersCompact.isNotEmpty()) "$n $lettersCompact" else "Mnohoúhelník"
    }

    fun planePolygonDisplayName(poly: PlanePolygon2D): String {
        val names = if (poly.vertexAidPointIds.isNotEmpty()) {
            poly.vertexAidPointIds.mapNotNull { pointId ->
                state.aidPointsLogical.firstOrNull { it.id == pointId }?.name
            }.filter { !it.isNullOrBlank() }
        } else {
            poly.vertexPointIdsPudorys.mapNotNull { pointId ->
                state.pointsPudorys.firstOrNull { it.id == pointId }?.name
                    ?: state.helpSegmentsPudorys.asSequence()
                        .filter { it.id in poly.segmentIdsPudorys }
                        .flatMap { sequenceOf(it.start, it.end) }
                        .firstOrNull { it.id == pointId }
                        ?.name
            }.filter { !it.isNullOrBlank() }
        }
        val compact = when {
            names.isEmpty() -> ""
            names.size <= 4 -> names.joinToString("")
            else -> "${names.first()}…${names.last()}"
        }
        val type = when (poly.n) {
            3 -> "Trojúhelník"
            4 -> "Čtyřúhelník"
            else -> "${poly.n}-úhelník"
        }
        return if (compact.isBlank()) type else "$type $compact"
    }

    val mixedItems = buildList<UiTreeItem> {

        // --- POLYGONY (rozbalitelné: děti = úsečky + body, ze kterých polygon stojí) ---
        state.polygons3D.forEach { poly ->
            add(
                UiTreeItem(
                    key = "poly:${poly.id}",
                    sortIndex = sortKeyDesc(poly.creationIndex),
                    name = polygonDisplayName(poly),
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
            )
        }
        state.planePolygons2D.forEach { poly ->
            add(
                UiTreeItem(
                    key = "poly2d:${poly.id}",
                    sortIndex = sortKeyDesc(poly.creationIndex),
                    name = planePolygonDisplayName(poly),
                    color = poly.color,
                    is3D = false,
                    icon = ObjectListIcon.Polygon(poly.n),
                    isSelected = { state.selectedPlanePolygons2D.any { it.id == poly.id } },
                    onClick = {
                        if (clearAllOnClick) clearSelection(state)
                        togglePlanePolygonSelection(state, poly.id, clearOthers = false)
                    },
                    children = buildPlanePolygonChildren(state, poly, clearAllOnClick)
                )
            )
        }

        // --- BODY ---
        listedPoints.forEach { point ->
            val src = point.source
            val sortIdx = when (src) {
                is Point3DPudorys -> sortKeyDesc(src.effectiveCreationIndex)
                is Point3DNarys -> sortKeyDesc(src.effectiveCreationIndex)
                else -> Long.MIN_VALUE
            }

            val fullName = if (point.parent == null) {
                "Bod ${point.name?.removeSuffix("\u2081")}".removeSuffix("\u2081")
            } else {
                "Bod ${(point.parent as? Point3D)?.name ?: point.name}"
            }

            val sup = (src as? Point2DProjection)?.localSuperscript
            val parentId = (point.parent as? Point3D)?.id

            val key = when (src) {
                is Point3DPudorys -> if (parentId != null) "pt3d:p:$parentId" else "pt2d:p:${src.id}"
                is Point3DNarys -> if (parentId != null) "pt3d:n:$parentId" else "pt2d:n:${src.id}"
                else              -> "pt:unknown"
            }

            add(
                UiTreeItem(
                    is3D = false,
                    key = key,
                    sortIndex = sortIdx,
                    name = fullName,
                    color = point.color,
                    superscript = sup,
                    icon = ObjectListIcon.Point,
                    isSelected = { isPointSelected(point.parent as? Point3D, point.source, point.projectionType) },
                    onClick = {
                        if (clearAllOnClick) clearSelection(state)

                        if (point.parent != null) {
                            val allProjections =
                                state.pointsPudorys.filter { it.parent == point.parent } +
                                        state.pointsNarys.filter { it.parent == point.parent }

                            allProjections.forEach {
                                when (it) {
                                    is Point3DPudorys -> toggleSelectionPudorys(it, state)
                                    is Point3DNarys -> toggleSelection(it, state)
                                    is Point3DBokorys -> toggleSelectionBokorys(it, state)
                                    is Point3DAxo -> toggleSelectionAxo(it,state)
                                }
                            }
                        } else {
                            when (point.projectionType) {
                                ListProjectionType.PUDORYS -> toggleSelectionPudorys(point.source as Point3DPudorys, state)
                                ListProjectionType.NARYS   -> toggleSelection(point.source as Point3DNarys, state)
                                else -> {}
                            }
                        }
                    }
                )
            )
        }
// --- POMOCNÉ ÚSEČKY (PŮDORYS) ---
        state.helpSegmentsPudorys
            .asSequence()
            .filterNot { it.id in planePolygonSegmentIds }
            .forEach { seg ->

                val label = seg.name?.let { "Úsečka $it" } ?: "Úsečka"


                add(
                    UiTreeItem(
                        is3D = false,
                        key = "helpseg:p:${seg.id}",
                        sortIndex = sortKeyDesc(seg.creationIndex), // nebo seg.effectiveCreationIndex pokud existuje
                        name = label.removeSuffix("\u2081").removeSuffix("\u2082"),
                        color = seg.color,
                        icon = ObjectListIcon.Segment,
                        isSelected = { state.selectedSegmentsPudorys.contains(seg) },
                        onClick = {
                            if (clearAllOnClick) clearSelection(state)
                            toggleSelectionPudorysSegment(seg, state) // pokud je to Segment2DPudorys
                        }
                    )
                )
            }

        // --- POMOCNÉ PŘÍMKY (půdorys) ---
        state.helpLinePudorys
            .asSequence()
            .filterNot { it.id == "axisX" || it.id == "axisY" }
            .filter { it.name != "" }
            .forEach { line ->
                add(
                    UiTreeItem(
                        is3D = false,
                        key = "helpline:p:${line.id}",
                        sortIndex = sortKeyDesc(line.creationIndex),
                        name = "Přímka ${line.name}",
                        color = line.color,
                        superscript = line.localSuperscript,
                        subscript = line.lowerSuperscript,
                        icon = ObjectListIcon.Line,
                        isSelected = { state.selectedLinesPudorys.contains(line) },
                        onClick = {
                            if (clearAllOnClick) clearSelection(state)
                            toggleSelectionPudorysLine(line, state)
                        }
                    )
                )
            }

        // --- ÚSEČKY ---
        listedSegments.forEach { segment ->
            val src = segment.source
            val sortIdx = when (src) {
                is HelpSegmentPudorys -> sortKeyDesc(src.creationIndex)
                else -> Long.MIN_VALUE
            }

            val fullName = segment.name.removeSuffix("\u2081").removeSuffix("\u2082")
            val parentId = (segment.parent as? Segment3D)?.id ?: "nop"
            val key = when (src) {
                is HelpSegmentPudorys -> "seg:p:${src.id}:$parentId"
                else -> "seg:unknown:$parentId"
            }

            add(
                UiTreeItem(
                    is3D = false,
                    key = key,
                    sortIndex = sortIdx,
                    name = fullName,
                    color = segment.color,
                    icon = ObjectListIcon.Segment,
                    isSelected = { isSegmentSelected(segment.parent as? Segment3D, segment.source, segment.projectionType) },
                    onClick = {
                        if (clearAllOnClick) clearSelection(state)

                        if (segment.parent != null) {
                            val allProjections =
                                state.helpSegmentsPudorys.filter { it.parent == segment.parent } +
                                        state.segmentsNarys.filter { it.parent == segment.parent }

                            allProjections.forEach {
                                when (it) {
                                    is HelpSegmentPudorys -> toggleSelectionPudorysSegment(it, state)
                                    is Segment2DNarys -> toggleSelectionNarysSegment(it, state)
                                    else -> {}
                                }
                            }
                        } else {
                            when (segment.projectionType) {
                                ListProjectionType.PUDORYS -> toggleSelectionPudorysSegment(segment.source as Segment2DPudorys, state)
                                ListProjectionType.NARYS   -> toggleSelectionNarysSegment(segment.source as Segment2DNarys, state)
                                else -> {}
                            }
                        }
                    }
                )
            )
        }

        // --- KUŽELOSEČKY 2D ---
        (state.conicsPudorys.asSequence().filter { it.parent == null }.map {
            val type = when {
                state.hyperbolaInputsPudorys.containsKey(it.id) -> "Hyperbola"
                state.conicInputPointsPudorys[it.id]?.third == Offset.Unspecified -> "Parabola"
                else -> "Elipsa"
            }
            UiTreeItem(
                is3D = false,
                key = "conic2d:p:${it.id}",
                sortIndex = sortKeyDesc(it.effectiveCreationIndex),
                name = "$type ${it.name}".removeSuffix("\u2081"),
                color = it.localColor ?: Color.Black,
                icon = conicIconFromPlaneType(type),
                isSelected = { isConicSelected2D(it, ListProjectionType.PUDORYS) },
                onClick = {
                    if (clearAllOnClick) clearSelection(state)
                    toggleSelectionPudorysConic(it, state)
                }
            )
        } + state.conicsNarys.asSequence().filter { it.parent == null }.map {
            val type = when {
                state.hyperbolaInputsNarys.containsKey(it.id) -> "Hyperbola"
                state.conicInputPointsNarys[it.id]?.third == Offset.Unspecified -> "Parabola"
                else -> "Elipsa"
            }
            UiTreeItem(
                is3D = false,
                key = "conic2d:n:${it.id}",
                sortIndex = sortKeyDesc(it.effectiveCreationIndex),
                name = "$type ${it.name}".removeSuffix("\u2081"),
                color = it.localColor ?: Color.Black,
                icon = conicIconFromPlaneType(type),
                isSelected = { isConicSelected2D(it, ListProjectionType.NARYS) },
                onClick = {
                    if (clearAllOnClick) clearSelection(state)
                    toggleSelectionNarysConic(it, state)
                }
            )
        }).forEach { add(it) }

        // --- KRUŽNICE 2D ---
        (state.circlesPudorys.asSequence()
            .filter { it.isHelpCircle }
            .filter { it.parentId.isNullOrBlank() }
            .map { c ->
                UiTreeItem(
                    is3D = false,
                    key = "circle:p:${c.id}",
                    sortIndex = sortKeyDesc(c.effectiveCreationIndex),
                    name = "Kružnice ${c.name.ifBlank { "Kružnice" }}".removeSuffix("\u2081"),
                    color = c.localColor ?: Color.Black,
                    icon = ObjectListIcon.Circle,
                    isSelected = { isCircleSelected(c, ListProjectionType.PUDORYS) },
                    onClick = {
                        if (clearAllOnClick) clearSelection(state)
                        toggleSelectionPudorysCircle(c, state)
                    }
                )
            } + state.circlesNarys.asSequence()
            .filter { !it.isHelpCircle }
            .filter { it.parentId.isNullOrBlank() }
            .map { c ->
                UiTreeItem(
                    is3D = false,
                    key = "circle:n:${c.id}",
                    sortIndex = sortKeyDesc(c.effectiveCreationIndex),
                    name = "Kružnice ${c.name.ifBlank { "Kružnice" }}",
                    color = c.localColor ?: Color.Black,
                    icon = ObjectListIcon.Circle,
                    isSelected = { isCircleSelected(c, ListProjectionType.NARYS) },
                    onClick = {
                        if (clearAllOnClick) clearSelection(state)
                        toggleSelectionNarysCircle(c, state)
                    }
                )
            }).forEach { add(it) }

        // --- POMOCNÉ BODY ---
        state.aidPointsLogical
            .asSequence()
            .filter { it.id != "origin" }
            .filterNot { it.id in planePolygonAidPointIds }
            .forEach { p ->
                add(
                    UiTreeItem(
                        is3D = false,
                        key = "aid:${p.id}",
                        sortIndex = sortKeyDesc(p.creationIndex),
                        name = if (p.name.isNullOrBlank()) "Bod" else "Bod ${p.name}",
                        color = p.color,
                        superscript = p.upperSuperscript,
                        subscript = p.lowerSuperscript,
                        icon = ObjectListIcon.Point,
                        isSelected = { state.selectedAidPointIds.contains(p.id) },
                        onClick = {
                            if (clearAllOnClick) {
                                clearSelection(state)
                                state.selectedAidPointIds.add(p.id)
                            } else {
                                toggleSelectionAidPoint(p, state)
                            }
                        }
                    )
                )
            }

    }

    val sorted = mixedItems.sortedByDescending { it.sortIndex }

    // --- sticky-top jako u hlavního listu ---
    var wasAtTop by remember { mutableStateOf(true) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
            .distinctUntilChanged()
            .collectLatest { wasAtTop = it }
    }

    val topKey = sorted.firstOrNull()?.key
    LaunchedEffect(topKey) {
        if (wasAtTop) {
            withFrameNanos { }
            listState.scrollToItem(0, 0)
        }
    }

    Box {
        // klíče uzlů, které se právě zabalují – dočasně drží jejich potomky
        // v seznamu, aby doanimovali zmizení (viz onToggleExpand níže)
        val collapsingKeys = remember { mutableStateListOf<String>() }
        val collapseScope = rememberCoroutineScope()

        val rows = remember(sorted, state.expandedObjectListKeys, collapsingKeys) {
            flattenTree(sorted, state.expandedObjectListKeys, collapsingKeys)
        }

        // viz stejný komentář v ui/mongeui/.../ObjectList/ObjectList.kt – bez tohohle by se
        // enter animace přehrávala znovu při každém scrollnutí řádku zpět do viewportu
        val animatedOnceKeys = remember { mutableSetOf<String>() }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(items = rows, key = { it.item.key }) { row ->
                val color = if (row.item.color == Color.Black && LocalMongeColors.current.isDark) Color.White else row.item.color
                val guideColor = if (row.rootColor == Color.Black && LocalMongeColors.current.isDark) Color.White else row.rootColor

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
                    // ⚠️ NEpřidávej Modifier.animateItemPlacement() – viz vysvětlení u hlavního
                    // ObjectListu (ui/mongeui/.../ObjectList/ObjectList.kt), padá s AIOOBE.
                ) {
                    InspectorObjectListItem(
                        name = row.item.name,
                        color = color,
                        is3D = row.item.is3D,
                        superscript = row.item.superscript,
                        subscript = row.item.subscript,
                        icon = row.item.icon ?: objectListIconFromText(row.item.key, row.item.name),
                        isSelected = row.item.isSelected(),
                        depth = row.depth,
                        guideColor = guideColor,
                        isExpandable = row.isExpandable,
                        isExpanded = row.isExpanded,
                        onToggleExpand = {
                            val k = row.item.key
                            if (state.expandedObjectListKeys.contains(k)) {
                                state.expandedObjectListKeys.remove(k)
                                if (!collapsingKeys.contains(k)) collapsingKeys.add(k)
                                // viz stejný komentář v ui/mongeui/.../ObjectList/ObjectList.kt
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
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(8.dp)
        )
    }
}
