package monge.input.tools

import utils.System
import androidx.compose.ui.geometry.Offset
import serialization.commitSnapshot
import model.*
import model.classes.Arc2DNarys
import model.classes.Arc2DPudorys
import model.classes.Point3DNarys
import model.classes.Point3DPudorys
import state.MongeState
import ui.mongeui.toolbar.repeatCons
import ui.mongeui.toolbar.updateConstructionInfo
import ui.resetStavu
import utils.allocIndex
import utils.getLogicalCursor
import kotlin.math.atan2
import kotlin.math.hypot

fun handleArcs(  cursor: Offset,
                 snappedPointLogical: Offset?,
                 canvasOffset: Offset,
                 scale: Float,
                 state: MongeState) {
    if (state.mongeMode == DrawingModeMonge.PUDORYS &&
        state.drawobjects == Mongeobjects.ARC) {

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

        when {
            state.arc.arcCenterPudorys == null -> {
                state.arc.arcCenterPudorys = Point3DPudorys(logical.x, logical.y, "?")
                println("🌀 Zvolen střed oblouku.")
                state.deferSelectionUntil = System.currentTimeMillis() + 100
            }

            state.arc.arcRadiusPointPudorys == null -> {
                state.arc.arcRadiusPointPudorys = Point3DPudorys(logical.x, logical.y, "?")
                println("📏 Zvolen bod určující poloměr.")
                state.deferSelectionUntil = System.currentTimeMillis() + 100
                state.arc.isSnappingToArc = true
            }

            state.arc.arcStartPointPudorys == null -> {
                state.arc.arcStartPointPudorys = Point3DPudorys(logical.x, logical.y, "?")
                println("▶️ Zvolen počáteční bod oblouku.")
                state.deferSelectionUntil = System.currentTimeMillis() + 100
            }

            else -> {
                val center = state.arc.arcCenterPudorys!!
                val radiusPoint = state.arc.arcRadiusPointPudorys!!
                val startPoint = state.arc.arcStartPointPudorys!!
                val endPoint = logical


                val radius = hypot(radiusPoint.x - center.x, radiusPoint.y - center.y)

                val color = state.currentHelpLineStyleSettings.color

                val strokeWidth = when (state.projectionMode) {
                    ProjectionMode.PLANE -> state.currentLineStyleSettings.strokeWidth
                    else -> state.currentHelpLineStyleSettings.strokeWidth
                }
                val style = when (state.projectionMode) {
                    ProjectionMode.PLANE -> state.currentLineStyleSettings.style
                    else -> state.currentHelpLineStyleSettings.style
                }

                val startRad = angleRadPudorysGeom(center, startPoint.x, startPoint.y)
                val endRad   = angleRadPudorysGeom(center, endPoint.x, endPoint.y)

                val arc = Arc2DPudorys(
                    center = center,
                    radius = radius,
                    startRad = startRad,
                    endRad = endRad,
                    color = color,
                    lineStyle = style,
                    strokeWidth = strokeWidth,
                    clockwise = state.arc.arcDirectionClockwise,
                    creationIndex = allocIndex(state)
                )

                state.arc.isSnappingToArc = false
                state.arcsPudorys += arc
                println("✅ Oblouk vytvořen. sweep=${arc.sweepSigned()} rad")
                commitSnapshot(state)

                // Reset
                state.deferSelectionUntil = System.currentTimeMillis() + 100
                state.arc.arcCenterPudorys = null
                state.arc.arcRadiusPointPudorys = null
                state.arc.arcStartPointPudorys = null
                state.arc.arcPreviewEndPudorys = null
                repeatCons(state)
                updateConstructionInfo(state)
                resetStavu(state)
            }
        }
    }

    if (state.mongeMode == DrawingModeMonge.NARYS &&
        state.drawobjects == Mongeobjects.ARC
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

        when {
            state.arc.arcCenterNarys == null -> {
                state.arc.arcCenterNarys = Point3DNarys(logical.x, logicalZ, "?")
                println("🌀 Zvolen střed oblouku.")
                state.deferSelectionUntil = System.currentTimeMillis() + 100
            }
            state.arc.arcRadiusPointNarys == null -> {
                state.arc.arcRadiusPointNarys = Point3DNarys(logical.x, logicalZ, "?")
                println("📏 Zvolen bod určující poloměr.")
                state.deferSelectionUntil = System.currentTimeMillis() + 100
                state.arc.isSnappingToArcNarysOnly = true
            }
            state.arc.arcStartPointNarys == null -> {
                state.arc.arcStartPointNarys = Point3DNarys(logical.x, logicalZ, "?")
                println("▶️ Zvolen počáteční bod oblouku.")
                state.deferSelectionUntil = System.currentTimeMillis() + 100
            }
            else -> {
                val center = state.arc.arcCenterNarys!!
                val radiusPoint = state.arc.arcRadiusPointNarys!!
                val startPoint = state.arc.arcStartPointNarys!!
                val endPointZ = -logical.y
                val endPoint = Point3DNarys(logical.x, endPointZ, "?")

                val dx1 = startPoint.x - center.x
                val dz1 = startPoint.z - center.z
                val dx2 = endPoint.x - center.x
                val dz2 = endPoint.z - center.z
                val radius = hypot(radiusPoint.x - center.x, radiusPoint.z - center.z)

                val color = when (state.projectionMode) {
                    ProjectionMode.PLANE -> state.currentLineStyleSettings.color
                    else -> state.currentHelpLineStyleSettings.color
                }
                val strokeWidth = when (state.projectionMode) {
                    ProjectionMode.PLANE -> state.currentLineStyleSettings.strokeWidth
                    else -> state.currentHelpLineStyleSettings.strokeWidth
                }
                val style =when (state.projectionMode) {
                    ProjectionMode.PLANE -> state.currentLineStyleSettings.style
                    else -> state.currentHelpLineStyleSettings.style
                }


                val startRad = atan2(dz1, dx1)
                val endRad   = atan2(dz2, dx2)


                val arc = Arc2DNarys(
                    center = center,
                    radius = radius,
                    startRad = startRad,
                    endRad = endRad,
                    color = color,
                    lineStyle = style,
                    strokeWidth = strokeWidth,
                    clockwise = state.arc.arcDirectionClockwise,
                    creationIndex = allocIndex(state)
                )

                state.arc.isSnappingToArcNarysOnly = false
                state.arcsNarys += arc
                println("✅ Oblouk vytvořen.")
                commitSnapshot(state)

                // Reset
                state.arc.arcCenterNarys = null
                state.arc.arcRadiusPointNarys = null
                state.arc.arcStartPointNarys = null
                state.arc.arcPreviewEndNarys = null
                state.deferSelectionUntil = System.currentTimeMillis() + 100
                repeatCons(state)
                updateConstructionInfo(state)
                resetStavu(state)
            }
        }
    }


}
private fun angleRadPudorysGeom(center: Point3DPudorys, x: Float, y: Float): Float {
    val dx = x - center.x
    val dy = y - center.y
    // ✅ flip Y pouze pro úhly: screen(y dolů) -> math(y nahoru)
    return atan2(-dy, dx)
}