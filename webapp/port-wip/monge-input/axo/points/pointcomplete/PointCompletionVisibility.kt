package monge.input.axo.points.pointcomplete

import androidx.compose.ui.graphics.Color
import model.axo.AxoMode
import model.classes.Point2DProjection
import model.classes.Point3DAxo
import model.classes.Point3DBokorys
import model.classes.Point3DNarys
import model.classes.Point3DPudorys
import monge.input.axo.lines.linecomplete.ProjectionKind

fun pointCompletionTargetKind(mode: AxoMode): ProjectionKind? {
    return when (mode) {
        AxoMode.AXO_PUDORYS -> ProjectionKind.PUDORYS
        AxoMode.AXO_NARYS -> ProjectionKind.NARYS
        AxoMode.AXO_BOKORYS -> ProjectionKind.BOKORYS
        AxoMode.NORMAL_2D -> ProjectionKind.AXO
    }
}

fun pointBaseName(point: Point2DProjection, suffix: String): String {
    return point.parent?.name
        ?: point.name?.removeSuffix(suffix)
        ?: "A"
}

fun pointColor(point: Point2DProjection): Color {
    return when (point) {
        is Point3DPudorys -> point.color
        is Point3DNarys -> point.color
        is Point3DBokorys -> point.color
        is Point3DAxo -> point.color
    }
}

fun pointWidth(point: Point2DProjection): Float {
    return when (point) {
        is Point3DPudorys -> point.width
        is Point3DNarys -> point.width
        is Point3DBokorys -> point.width
        is Point3DAxo -> point.width
    }
}

fun pointSuperscript(point: Point2DProjection): String? {
    return when (point) {
        is Point3DPudorys -> point.superscript
        is Point3DNarys -> point.superscript
        is Point3DBokorys -> point.superscript
        is Point3DAxo -> point.superscript
    }
}

fun applyCompletedPointProjectionVisibility(
    visibleKinds: Set<ProjectionKind>,
    pudorys: Point3DPudorys? = null,
    narys: Point3DNarys? = null,
    bokorys: Point3DBokorys? = null,
    axo: Point3DAxo? = null
) {
    pudorys?.setAxoVisibility(ProjectionKind.PUDORYS in visibleKinds)
    narys?.setAxoVisibility(ProjectionKind.NARYS in visibleKinds)
    bokorys?.setAxoVisibility(ProjectionKind.BOKORYS in visibleKinds)
    axo?.setAxoVisibility(ProjectionKind.AXO in visibleKinds)
}

fun Point3DPudorys.setAxoVisibility(visible: Boolean) {
    showInAxoInitial = visible
    showInAxo = visible
}

fun Point3DNarys.setAxoVisibility(visible: Boolean) {
    showInAxoInitial = visible
    showInAxo = visible
}

fun Point3DBokorys.setAxoVisibility(visible: Boolean) {
    showInAxoInitial = visible
    showInAxo = visible
}

fun Point3DAxo.setAxoVisibility(visible: Boolean) {
    showInAxoInitial = visible
    showInAxo = visible
}
