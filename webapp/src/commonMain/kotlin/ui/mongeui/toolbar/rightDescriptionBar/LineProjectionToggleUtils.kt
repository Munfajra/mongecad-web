package ui.mongeui.toolbar.rightDescriptionBar

import androidx.compose.ui.geometry.Offset
import serialization.commitSnapshot
import model.classes.Line3D
import model.classes.Line3DProjectionBokorys
import model.classes.Line3DProjectionNarys
import model.classes.Line3DProjectionPudorys
import model.classes.Point3DAxo
import model.classes.Point3DBokorys
import model.classes.Point3DNarys
import model.classes.Point3DPudorys

import state.MongeState
import utils.allocIndex
import kotlin.math.abs

private const val PROJECTED_LINE_POINT_EPS = 0.0001f

private fun isZeroProjection(a: Float, b: Float): Boolean =
    abs(a) < PROJECTED_LINE_POINT_EPS && abs(b) < PROJECTED_LINE_POINT_EPS

private fun linePointName(parentLine: Line3D): String? =
    parentLine.name.takeIf { it.isNotBlank() }

fun findProjectedPudorysPoint(state: MongeState, parentLine: Line3D): Point3DPudorys? =
    state.pointsPudorys.firstOrNull {
        isProjectedLinePointOf(it, parentLine)
    }

fun findProjectedNarysPoint(state: MongeState, parentLine: Line3D): Point3DNarys? =
    state.pointsNarys.firstOrNull {
        isProjectedLinePointOf(it, parentLine)
    }

fun findProjectedBokorysPoint(state: MongeState, parentLine: Line3D): Point3DBokorys? =
    state.pointsBokorys.firstOrNull {
        isProjectedLinePointOf(it, parentLine)
    }

fun findProjectedAxoPoint(state: MongeState, parentLine: Line3D): Point3DAxo? =
    state.pointsAxo.firstOrNull {
        isProjectedLinePointOf(it, parentLine)
    }

fun projectedLineIdOf(point: Any?): String? =
    when (point) {
        is Point3DPudorys -> point.pendingParentLineId ?: point.parentLine?.id
        is Point3DNarys -> point.pendingParentLineId ?: point.parentLine?.id
        is Point3DBokorys -> point.pendingParentLineId ?: point.parentLine?.id
        is Point3DAxo -> point.pendingParentLineId ?: point.parentLine?.id
        else -> null
    }

fun isProjectedLinePoint(point: Any?): Boolean =
    projectedLineIdOf(point) != null

fun isProjectedLinePointOf(point: Any?, parentLine: Line3D): Boolean =
    projectedLineIdOf(point) == parentLine.id

fun projectedLineOfPoint(state: MongeState, point: Any?): Line3D? {
    val parent = when (point) {
        is Point3DPudorys -> point.parentLine
        is Point3DNarys -> point.parentLine
        is Point3DBokorys -> point.parentLine
        is Point3DAxo -> point.parentLine
        else -> null
    }

    return parent ?: projectedLineIdOf(point)?.let { id ->
        state.lines3D.firstOrNull { it.id == id }
    }
}

fun showPudorysLineProjection(state: MongeState, parentLine: Line3D): Boolean =
    state.lines3DPudorys.firstOrNull { it.parent?.id == parentLine.id }?.showInAxo
        ?: findProjectedPudorysPoint(state, parentLine)?.showInAxo
        ?: false

fun showNarysLineProjection(state: MongeState, parentLine: Line3D): Boolean =
    state.lines3DNarys.firstOrNull { it.parent?.id == parentLine.id }?.showInAxo
        ?: findProjectedNarysPoint(state, parentLine)?.showInAxo
        ?: false

fun showBokorysLineProjection(state: MongeState, parentLine: Line3D): Boolean =
    state.lines3DBokorys.firstOrNull { it.parent?.id == parentLine.id }?.showInAxo
        ?: findProjectedBokorysPoint(state, parentLine)?.showInAxo
        ?: false

fun showAxoLineProjection(state: MongeState, parentLine: Line3D): Boolean =
    state.lines3DAxo.firstOrNull { it.parent?.id == parentLine.id }?.showInAxo
        ?: findProjectedAxoPoint(state, parentLine)?.showInAxo
        ?: false

fun setPudorysLineProjectionVisible(state: MongeState, parentLine: Line3D, checked: Boolean) {
    val line = state.lines3DPudorys.firstOrNull { it.parent?.id == parentLine.id }
    val point = findProjectedPudorysPoint(state, parentLine)

    when {
        line != null -> line.showInAxo = checked
        point != null -> point.showInAxo = checked
        checked -> {
            if (isZeroProjection(parentLine.direction.x, parentLine.direction.y)) {
                state.pointsPudorys.add(
                    Point3DPudorys(
                        x = parentLine.start.x,
                        y = parentLine.start.y,
                        name = linePointName(parentLine),
                        localColor = parentLine.color,
                        localWidth = parentLine.strokeWidth,
                        parentLine = parentLine,
                        pendingParentLineId = parentLine.id,
                        creationIndex = allocIndex(state),
                        showInAxoInitial = true
                    )
                )
            } else {
                state.lines3DPudorys.add(
                    Line3DProjectionPudorys(
                        point = Point3DPudorys(x = parentLine.start.x, y = parentLine.start.y),
                        direction = Offset(parentLine.direction.x, parentLine.direction.y),
                        parent = parentLine,
                        parentId = parentLine.id,
                        creationIndex = allocIndex(state),
                        showInAxoInitial = true
                    )
                )
            }
        }
    }

    commitSnapshot(state)
}

fun setNarysLineProjectionVisible(state: MongeState, parentLine: Line3D, checked: Boolean) {
    val line = state.lines3DNarys.firstOrNull { it.parent?.id == parentLine.id }
    val point = findProjectedNarysPoint(state, parentLine)

    when {
        line != null -> line.showInAxo = checked
        point != null -> point.showInAxo = checked
        checked -> {
            if (isZeroProjection(parentLine.direction.x, parentLine.direction.z)) {
                state.pointsNarys.add(
                    Point3DNarys(
                        x = parentLine.start.x,
                        z = parentLine.start.z,
                        name = linePointName(parentLine),
                        localColor = parentLine.color,
                        localWidth = parentLine.strokeWidth,
                        parentLine = parentLine,
                        pendingParentLineId = parentLine.id,
                        creationIndex = allocIndex(state),
                        showInAxoInitial = true
                    )
                )
            } else {
                state.lines3DNarys.add(
                    Line3DProjectionNarys(
                        point = Point3DNarys(x = parentLine.start.x, z = parentLine.start.z),
                        direction = Offset(parentLine.direction.x, parentLine.direction.z),
                        parent = parentLine,
                        parentId = parentLine.id,
                        creationIndex = allocIndex(state),
                        showInAxoInitial = true
                    )
                )
            }
        }
    }

    commitSnapshot(state)
}

fun setBokorysLineProjectionVisible(state: MongeState, parentLine: Line3D, checked: Boolean) {
    val line = state.lines3DBokorys.firstOrNull { it.parent?.id == parentLine.id }
    val point = findProjectedBokorysPoint(state, parentLine)

    when {
        line != null -> line.showInAxo = checked
        point != null -> point.showInAxo = checked
        checked -> {
            if (isZeroProjection(parentLine.direction.y, parentLine.direction.z)) {
                state.pointsBokorys.add(
                    Point3DBokorys(
                        y = parentLine.start.y,
                        z = parentLine.start.z,
                        name = linePointName(parentLine),
                        localColor = parentLine.color,
                        localWidth = parentLine.strokeWidth,
                        parentLine = parentLine,
                        pendingParentLineId = parentLine.id,
                        creationIndex = allocIndex(state),
                        showInAxoInitial = true
                    )
                )
            } else {
                state.lines3DBokorys.add(
                    Line3DProjectionBokorys(
                        point = Point3DBokorys(y = parentLine.start.y, z = parentLine.start.z),
                        direction = Offset(parentLine.direction.y, parentLine.direction.z),
                        parent = parentLine,
                        parentId = parentLine.id,
                        creationIndex = allocIndex(state),
                        showInAxoInitial = true
                    )
                )
            }
        }
    }

    commitSnapshot(state)
}

// setAxoLineProjectionVisible(...) – přepínání viditelnosti axo průmětu přímky;
// web axonometrii nekreslí, takže tenhle přepínač nemá co ovládat.
fun setAxoLineProjectionVisible(state: MongeState, parentLine: Line3D, checked: Boolean) = Unit
