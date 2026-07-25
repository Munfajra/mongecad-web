package utils

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import model.*
import model.classes.*
import model.classes.Line
import state.MongeState
import kotlin.math.PI
import kotlin.math.atan2

fun getLogicalCursor(
    snapped: Offset?,
    cursor: Offset,
    canvasOffset: Offset,
    scale: Float,
    canvasWidth: Float,
    canvasHeight: Float,
    flipX: Boolean,
    flipY: Boolean
): Offset {
    if (snapped != null) return snapped

    val cursorScreen = cursorToScreen(
        cursor = cursor,
        canvasWidth = canvasWidth,
        canvasHeight = canvasHeight,
        flipX = flipX,
        flipY = flipY
    )

    return (cursorScreen - canvasOffset) / scale
}



fun cursorToScreen(
    cursor: Offset,
    canvasWidth: Float,
    canvasHeight: Float,
    flipX: Boolean,
    flipY: Boolean
): Offset {
    val x = if (flipX) canvasWidth - cursor.x else cursor.x
    val y = if (flipY) canvasHeight - cursor.y else cursor.y
    return Offset(x, y)
}

// projectToScreen(...) používá FloatBuffer/IntBuffer z gluUnProject –
// je to čistě OpenGL cesta, kterou web nemá, takže tu nepokračuje.
fun Offset.toScreenOld(scale: Float, offset: Offset): Offset { return (this ) * scale + offset }
fun Offset.toScreen(
    scale: Float,
    offset: Offset,
    canvasHeight: Float,
    state: MongeState,
    canvasWidth: Float
): Offset {
    if (state.projectionMode != ProjectionMode.AXO) {
        val x = if (state.xAxisDirection == XAxisDirection.POSITIVE_LEFT) canvasWidth - (this.x * scale + offset.x)
        else this.x * scale + offset.x
        val y =
            if (state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP) canvasHeight - (this.y * scale + offset.y)
            else this.y * scale + offset.y
        return Offset(x, y)
    }
    else {
        val axoOrigin = state.basis?.origin?: return Offset.Zero
        return (axoOrigin + this).toScreenOld(scale, state.canvasOffset)
    }
}
fun Offset.fromScreen(
    scale: Float,
    offset: Offset,
    projectionMode: ProjectionMode,
    canvasHeight: Float
): Offset {
    return if (projectionMode == ProjectionMode.PLANE) {
        val xWorld = (this.x - offset.x) / scale
        val yWorld = ((canvasHeight - this.y) - offset.y) / scale
        Offset(xWorld, yWorld)
    } else {
        (this - offset) / scale
    }
}
fun combineProjectionsToLine3D(
    pudorys: Line3DProjectionPudorys,
    narys: Line3DProjectionNarys,
    name: String,
    sup: String, state: MongeState
): Line3D {
    val x = pudorys.point.x

    // Najdi odpovídající body na obou projekcích pro zvolenou x-ovou souřadnici
    val y = pudorys.point.y + (x - pudorys.point.x) * (pudorys.direction.y / pudorys.direction.x)
    val z = narys.point.z + (x - narys.point.x) * (narys.direction.y / narys.direction.x)

    val base = Point3D(x, y, z,name)

    // Najdi bod na přímce v půdorysu se stejným x posunem o 1
    val x2 = x + 1f
    val y2 = pudorys.point.y + (x2 - pudorys.point.x) * (pudorys.direction.y / pudorys.direction.x)
    val z2 = narys.point.z + (x2 - narys.point.x) * (narys.direction.y / narys.direction.x)

    val dir = Offset3D(
        x = x2 - x,
        y = y2 - y,
        z = z2 - z
    )

    val line3D = Line3D(
        start = base,
        direction = dir,
        name = name,
        superscript = sup,
        color = pudorys.localColor ?: Color.Black,
        strokeWidth = pudorys.localStrokeWidth ?: 1f,
        lineStyle = pudorys.localLineStyle ?: LineStyle.Solid, creationIndex = allocIndex(state)
    )

    return line3D.copy(
        customTrimRange = inheritedTrimRangeForCombinedLine(
            line3D = line3D,
            pudorys = pudorys,
            narys = narys
        )
    )
}
fun allocIndex(state: MongeState): Long = state.nextCreationIndex++

private fun inheritedTrimRangeForCombinedLine(
    line3D: Line3D,
    pudorys: Line3DProjectionPudorys?,
    narys: Line3DProjectionNarys?
): LineTrimRange? {
    val pRange = pudorys?.let {
        remapLineTrimRange(
            range = it.customTrimRange,
            sourcePoint = Offset(it.point.x, it.point.y),
            sourceDir = it.direction,
            targetPoint = Offset(line3D.start.x, line3D.start.y),
            targetDir = Offset(line3D.direction.x, line3D.direction.y)
        )
    }

    val nRange = narys?.let {
        remapLineTrimRange(
            range = it.customTrimRange,
            sourcePoint = Offset(it.point.x, it.point.z),
            sourceDir = it.direction,
            targetPoint = Offset(line3D.start.x, line3D.start.z),
            targetDir = Offset(line3D.direction.x, line3D.direction.z)
        )
    }

    return intersectLineTrimRanges(listOf(pRange, nRange))
}

fun cursorToWorld(cursor: Offset, canvasOffset: Offset, scale: Float): Offset {
    return ((cursor - canvasOffset) / scale)
}
fun Offset.dotProduct(other: Offset): Float {
    return this.x * other.x + this.y * other.y
}

fun update2DSnapshots(state: MongeState) {
    state.pointsPudorysSnapshot.clear()
    state.pointsPudorysSnapshot.addAll(state.pointsPudorys)

    state.pointsNarysSnapshot.clear()
    state.pointsNarysSnapshot.addAll(state.pointsNarys)
}
fun projectPointOntoLine(p: Offset, line: Line): Offset {
    val a = line.point
    val d = line.direction
    val t = ((p - a).dotProduct(d)) / d.getDistanceSquared()
    return a + d * t
}
fun projectPointOntoLine(p: Offset, linePoint: Offset, lineDir: Offset): Offset {
    val ap = p - linePoint
    val d = lineDir
    val t = (ap.x * d.x + ap.y * d.y) / (d.x * d.x + d.y * d.y)
    return linePoint + Offset(d.x * t, d.y * t)
}

fun isAngleBetween(angle: Float, start: Float, end: Float): Boolean {
    val a = normalizeAngle(angle)
    val s = normalizeAngle(start)
    val e = normalizeAngle(end)
    return if (s <= e) {
        a in s..e
    } else {
        a >= s || a <= e
    }
}
fun angleBetween(v1: Offset, v2: Offset): Float {
    val dot = v1.dotProduct(v2)
    val det = v1.x * v2.y - v1.y * v2.x
    return atan2(det, dot) // vrací úhel v radiánech, v intervalu (-π, π]
}
fun normalizeAngle(angle: Float): Float {
    var a = angle
    while (a < 0f) a += (2 * PI).toFloat()
    while (a >= (2 * PI).toFloat()) a -= (2 * PI).toFloat()
    return a
}
fun Offset.normalize(): Offset {
    val len = this.getDistance()
    return if (len > 1e-6f) this / len else Offset.Zero
}
fun Offset.dot(other: Offset): Float {
    return this.x * other.x + this.y * other.y
}
