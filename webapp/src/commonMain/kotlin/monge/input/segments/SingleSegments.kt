package monge.input.segments

import utils.System
import androidx.compose.ui.geometry.Offset
import serialization.commitSnapshot
import model.*
import model.classes.HelpSegmentNarys
import model.classes.HelpSegmentPudorys
import model.classes.Point3DNarys
import model.classes.Point3DPudorys
import model.classes.Segment2DNarys
import model.classes.Segment2DPudorys
import state.MongeState
import ui.mongeui.toolbar.repeatCons
import ui.mongeui.toolbar.updateConstructionInfo
import ui.resetStavu
import utils.allocIndex
import utils.getLogicalCursor

fun handleSingleSegmentNarysClick (
    cursor: Offset,
    snappedPointLogical: Offset?,
    canvasOffset: Offset,
    scale: Float,
    state: MongeState
) {
    val logical = getLogicalCursor(
        snappedPointLogical,
        cursor,
        canvasOffset,
        scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
        state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    )
    val logicalZ = -logical.y

    if (state.segmentStartNarys == null) {
        // První klik – začátek úsečky
        val start =
            Point3DNarys(logical.x, logicalZ, name = "", isSegmentEndpoint = true, creationIndex = allocIndex(state))
        state.segmentStartNarys = start
        state.deferSelectionUntil = System.currentTimeMillis() + 100
        println("🔹 První bod úsečky nárys (dočasný): $start")
    } else {
        // Druhý klik – konec úsečky
        val end =
            Point3DNarys(logical.x, logicalZ, name = "", isSegmentEndpoint = true, creationIndex = allocIndex(state))
        val start = state.segmentStartNarys!!
        // Teprve teď přidáme body do seznamu
        if (state.projekcnityp == ProjectionType.SINGLE) {

            val start = state.segmentStartNarys!!
            val end = Point3DNarys(
                logical.x,
                logicalZ,
                name = "",
                isSegmentEndpoint = true,
                creationIndex = allocIndex(state)
            )

            val style = state.currentLineStyleSettings

            val segment = Segment2DNarys(
                start = start,
                end = end,
                name = "",
                localLineStyle = style.style,
                localStrokeWidth = style.strokeWidth,
                localColor = style.color, creationIndex = allocIndex(state)
            )
// ⚠️ Přiřaď parentSegment po vytvoření segmentu
            start.parentSegment = segment
            end.parentSegment = segment
            state.pointsNarys.add(start)
            state.pointsNarys.add(end)
            state.segmentsNarys.add(segment)

            commitSnapshot(state)

        }
        if (state.projekcnityp== ProjectionType.AUXILIARY){
            val style = state.currentHelpLineStyleSettings
            val segment = HelpSegmentNarys(
                start = start,
                end = end,
                name = "",
                localLineStyle = style.style,
                localStrokeWidth = style.strokeWidth,
                localColor = style.color, creationIndex = allocIndex(state)
            )
            state.helpSegmentsNarys.add(segment)
            commitSnapshot(state)

        }

        state.deferSelectionUntil = System.currentTimeMillis() + 100
        repeatCons(state)
        resetStavu(state)
        updateConstructionInfo(state)
        println("🔸 Úsečka vytvořena (nárys)")
        state.segmentStartNarys = null
    }
}
fun handleSingleSegmentPudorysClick(
    cursor: Offset,
    snappedPointLogical: Offset?,
    canvasOffset: Offset,
    scale: Float,
    state: MongeState
){
    val logical = getLogicalCursor(
        snappedPointLogical,
        cursor,
        canvasOffset,
        scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
        state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    )
    if (state.segmentStartPudorys == null) {
        // První klik – začátek úsečky
        val start =
            Point3DPudorys(logical.x, logical.y, name = "", isSegmentEndpoint = true, creationIndex = allocIndex(state))
        state.segmentStartPudorys = start
        state.deferSelectionUntil = System.currentTimeMillis() + 100
        println("🔹 První bod úsečky (půdorys): $start")
        state.consInfo.value = "Umístěte druhý bod úsečky"
    } else {

        // Druhý klik – konec úsečky
        val end =
            Point3DPudorys(logical.x, logical.y, name = "", isSegmentEndpoint = true, creationIndex = allocIndex(state))
        val start = state.segmentStartPudorys!!

        if (state.projekcnityp == ProjectionType.SINGLE) {
            val style = state.currentLineStyleSettings

            val start = state.segmentStartPudorys!!
            val end = Point3DPudorys(
                logical.x,
                logical.y,
                name = "",
                isSegmentEndpoint = true,
                creationIndex = allocIndex(state)
            )

            val segment = Segment2DPudorys(
                start = start,
                end = end,
                name = "",
                localLineStyle = style.style,
                localStrokeWidth = style.strokeWidth,
                localColor = style.color, creationIndex = allocIndex(state)
            )
            // 🧠 Nastavení parentSegment bez copy()
            start.parentSegment = segment
            end.parentSegment = segment
            val startPoint = state.pointsPudorys.find { it.x == start.x && it.y == start.y }
            if (startPoint == null) { state.pointsPudorys.add(start)}
            val endPoint = state.pointsPudorys.find { it.x == end.x && it.y == end.y }
            if (endPoint == null) { state.pointsPudorys.add(end)}

            state.segmentsPudorys.add(segment)

            commitSnapshot(state)

        }
        if (state.projekcnityp== ProjectionType.AUXILIARY){
            val style = state.currentHelpLineStyleSettings
            val segment = HelpSegmentPudorys(
                start = start,
                end = end,
                name = "",
                localLineStyle = style.style,
                localStrokeWidth = style.strokeWidth,
                localColor = style.color, creationIndex = allocIndex(state)
            )
            addHelpSegmentPudorysAndDetectPlanePolygon(state, segment)
            commitSnapshot(state)

        }
        println("🔸 Úsečka vytvořena (půdorys)")
        state.segmentStartPudorys = null
        repeatCons(state)
        updateConstructionInfo(state)
        state.deferSelectionUntil = System.currentTimeMillis() + 100
        resetStavu(state)
    }
}
