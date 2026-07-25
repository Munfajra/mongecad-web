package model.classes
import androidx.compose.ui.geometry.Offset
import model.Offset3D
data class LineTrimRange(
    val start: Float,
    val end: Float
) {
    val min: Float get() = kotlin.math.min(start, end)
    val max: Float get() = kotlin.math.max(start, end)
    fun contains(t: Float, eps: Float = 1e-4f): Boolean =
        t in (min - eps)..(max + eps)
    fun normalized(): LineTrimRange = if (start <= end) this else LineTrimRange(end, start)
}
interface CustomTrimmedLine2D {
    val customTrimRange: LineTrimRange?
}
enum class LineTrimPickView {
    PUDORYS,
    NARYS,
    BOKORYS,
    AXO
}
data class PendingLineTrimPick(
    val targetId: String,
    val parentLineId: String?,
    val view: LineTrimPickView,
    val firstParam: Float? = null
)
fun Offset.lineParamAt(
    linePoint: Offset,
    lineDir: Offset
): Float? {
    val lenSq = lineDir.getDistanceSquared()
    if (lenSq < 1e-9f) return null
    val delta = this - linePoint
    return (delta.x * lineDir.x + delta.y * lineDir.y) / lenSq
}
fun clipSegmentByParamRange(
    segment: Pair<Offset, Offset>,
    linePoint: Offset,
    lineDir: Offset,
    range: LineTrimRange?
): Pair<Offset, Offset>? {
    range ?: return segment
    val aT = segment.first.lineParamAt(linePoint, lineDir) ?: return null
    val bT = segment.second.lineParamAt(linePoint, lineDir) ?: return null
    val dT = bT - aT
    if (kotlin.math.abs(dT) < 1e-9f) {
        return if (range.contains(aT)) segment else null
    }
    val lo = range.min
    val hi = range.max
    val segLo = kotlin.math.min(aT, bT)
    val segHi = kotlin.math.max(aT, bT)
    val clipLo = kotlin.math.max(segLo, lo)
    val clipHi = kotlin.math.min(segHi, hi)
    if (clipLo > clipHi) return null
    fun pointAtLineParam(t: Float): Offset {
        val s = (t - aT) / dT
        return segment.first + (segment.second - segment.first) * s
    }
    return pointAtLineParam(clipLo) to pointAtLineParam(clipHi)
}
fun remapLineTrimRange(
    range: LineTrimRange?,
    sourcePoint: Offset,
    sourceDir: Offset,
    targetPoint: Offset,
    targetDir: Offset
): LineTrimRange? {
    range ?: return null
    val sourceA = sourcePoint + sourceDir * range.start
    val sourceB = sourcePoint + sourceDir * range.end
    val targetA = sourceA.lineParamAt(targetPoint, targetDir) ?: return null
    val targetB = sourceB.lineParamAt(targetPoint, targetDir) ?: return null
    return LineTrimRange(targetA, targetB).normalized()
}
fun intersectLineTrimRanges(ranges: List<LineTrimRange?>): LineTrimRange? {
    val nonNull = ranges.filterNotNull()
    if (nonNull.isEmpty()) return null
    val lo = nonNull.maxOf { it.min }
    val hi = nonNull.minOf { it.max }
    return if (lo <= hi) LineTrimRange(lo, hi) else null
}
fun Line3D.pointAtParam(t: Float): Offset3D =
    Offset3D(
        start.x + direction.x * t,
        start.y + direction.y * t,
        start.z + direction.z * t
    )
fun Line3D.paramAtPoint(point: Offset3D): Float? {
    val lenSq = direction dot direction
    if (lenSq < 1e-9f) return null
    val delta = point - start.toOffset3D()
    return (delta dot direction) / lenSq
}
private fun parentProjectionParamAt(
    projectedPoint: Offset,
    parentStart2D: Offset,
    parentDir2D: Offset
): Float? {
    if (parentDir2D.getDistanceSquared() < 1e-9f) return null
    return projectedPoint.lineParamAt(parentStart2D, parentDir2D)
}
fun Line3DProjectionPudorys.parentParamAtProjectionPoint(projectedPoint: Offset): Float? {
    val p = parent ?: return null
    return parentProjectionParamAt(
        projectedPoint = projectedPoint,
        parentStart2D = Offset(p.start.x, p.start.y),
        parentDir2D = Offset(p.direction.x, p.direction.y)
    )
}
fun Line3DProjectionNarys.parentParamAtProjectionPoint(projectedPoint: Offset): Float? {
    val p = parent ?: return null
    return parentProjectionParamAt(
        projectedPoint = projectedPoint,
        parentStart2D = Offset(p.start.x, p.start.z),
        parentDir2D = Offset(p.direction.x, p.direction.z)
    )
}
fun Line3DProjectionBokorys.parentParamAtProjectionPoint(projectedPoint: Offset): Float? {
    val p = parent ?: return null
    return parentProjectionParamAt(
        projectedPoint = projectedPoint,
        parentStart2D = Offset(p.start.y, p.start.z),
        parentDir2D = Offset(p.direction.y, p.direction.z)
    )
}
fun Line3DProjectionPudorys.trimLocalLine(): Pair<Offset, Offset>? {
    parent?.let { p -> return Offset(p.start.x, p.start.y) to Offset(p.direction.x, p.direction.y) }
    return Offset(point.x, point.y) to direction
}
fun Line3DProjectionNarys.trimLocalLine(): Pair<Offset, Offset>? {
    parent?.let { p -> return Offset(p.start.x, p.start.z) to Offset(p.direction.x, p.direction.z) }
    return Offset(point.x, point.z) to direction
}
fun Line3DProjectionBokorys.trimLocalLine(): Pair<Offset, Offset>? {
    parent?.let { p -> return Offset(p.start.y, p.start.z) to Offset(p.direction.y, p.direction.z) }
    return Offset(point.y, point.z) to direction
}
private fun pudorysTrimLocalLine(line: Any): Pair<Offset, Offset>? =
    when (line) {
        is Line3DProjectionPudorys -> line.trimLocalLine()
        is HelpLinePudorys -> Offset(line.point.x, line.point.y) to line.direction
        else -> null
    }
private fun narysTrimLocalLine(line: Any): Pair<Offset, Offset>? =
    when (line) {
        is Line3DProjectionNarys -> line.trimLocalLine()
        is HelpLineNarys -> Offset(line.point.x, line.point.z) to line.direction
        else -> null
    }
private fun bokorysTrimLocalLine(line: Any): Pair<Offset, Offset>? =
    when (line) {
        is Line3DProjectionBokorys -> line.trimLocalLine()
        else -> null
    }
fun clipPudorysSegmentByCustomTrim(
    segment: Pair<Offset, Offset>,
    line: Any
): Pair<Offset, Offset>? {
    val range = (line as? CustomTrimmedLine2D)?.customTrimRange ?: return segment
    val (linePoint, lineDir) = pudorysTrimLocalLine(line) ?: return segment
    return clipSegmentByParamRange(segment, linePoint, lineDir, range)
}
fun clipNarysSegmentByCustomTrim(
    segment: Pair<Offset, Offset>,
    line: Any
): Pair<Offset, Offset>? {
    val range = (line as? CustomTrimmedLine2D)?.customTrimRange ?: return segment
    val (linePoint, lineDir) = narysTrimLocalLine(line) ?: return segment
    return clipSegmentByParamRange(segment, linePoint, lineDir, range)
}
fun clipBokorysSegmentByCustomTrim(
    segment: Pair<Offset, Offset>,
    line: Any
): Pair<Offset, Offset>? {
    val range = (line as? CustomTrimmedLine2D)?.customTrimRange ?: return segment
    val (linePoint, lineDir) = bokorysTrimLocalLine(line) ?: return segment
    return clipSegmentByParamRange(segment, linePoint, lineDir, range)
}
fun lineTrimContainsLocalPoint(
    range: LineTrimRange?,
    linePoint: Offset,
    lineDir: Offset,
    point: Offset,
    eps: Float = 1e-4f
): Boolean {
    range ?: return true
    val t = point.lineParamAt(linePoint, lineDir) ?: return false
    return range.contains(t, eps)
}
fun pudorysLineTrimContainsPoint(line: Any, point: Offset): Boolean {
    val range = (line as? CustomTrimmedLine2D)?.customTrimRange ?: return true
    val (linePoint, lineDir) = pudorysTrimLocalLine(line) ?: return true
    return lineTrimContainsLocalPoint(range, linePoint, lineDir, point)
}
fun narysLineTrimContainsPoint(line: Any, point: Offset): Boolean {
    val range = (line as? CustomTrimmedLine2D)?.customTrimRange ?: return true
    val (linePoint, lineDir) = narysTrimLocalLine(line) ?: return true
    return lineTrimContainsLocalPoint(range, linePoint, lineDir, point)
}
fun bokorysLineTrimContainsPoint(line: Any, point: Offset): Boolean {
    val range = (line as? CustomTrimmedLine2D)?.customTrimRange ?: return true
    val (linePoint, lineDir) = bokorysTrimLocalLine(line) ?: return true
    return lineTrimContainsLocalPoint(range, linePoint, lineDir, point)
}
