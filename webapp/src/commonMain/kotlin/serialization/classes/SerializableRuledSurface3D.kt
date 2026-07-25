package serialization.classes

import serialization.toSerializable
import kotlinx.serialization.Serializable
import model.Offset3D
import model.classes.PlaneEquation
import model.classes.RuledSurface3D
import model.classes.RuledSurfaceDirectrixKind
import model.classes.RuledSurfaceDirectrixRef
import model.classes.RuledSurfaceDirectrixSnapshot
import serialization.SerializableColor

@Serializable
data class SerializableRuledSurfaceDirectrixRef(
    val objectId: String,
    val kind: RuledSurfaceDirectrixKind,
)

@Serializable
data class SerializableRuledSurfaceDirectrixSnapshot(
    val reference: SerializableRuledSurfaceDirectrixRef,
    val components: List<List<Offset3D>>,
    val closed: Boolean,
    val line: SerializableLine3D? = null,
    val conic: SerializableConicSection3D? = null,
    val sphereCenter: Offset3D? = null,
    val sphereRadius: Float? = null,
    val ellipseArcParams: Pair<Float, Float>? = null,
    val ellipseArcEnds: Pair<Offset3D, Offset3D>? = null,
    val parabolaArcParams: Pair<Float, Float>? = null,
    val parabolaArcEnds: Pair<Offset3D, Offset3D>? = null,
    val hyperbolaArcParams: Pair<Pair<Float, Float>?, Pair<Float, Float>?>? = null,
    val hyperbolaArcEnds: Pair<Pair<Offset3D, Offset3D>?, Pair<Offset3D, Offset3D>?>? = null,
)

@Serializable
data class SerializableRuledSurface3D(
    val id: String,
    val name: String,
    val firstBoundaryDirectrix: SerializableRuledSurfaceDirectrixRef,
    val secondBoundaryDirectrix: SerializableRuledSurfaceDirectrixRef,
    val thirdDirectrix: SerializableRuledSurfaceDirectrixRef? = null,
    val directrixOrderVersion: Int = 0,
    val directorPlaneId: String? = null,
    val directrixSnapshots: List<SerializableRuledSurfaceDirectrixSnapshot> = emptyList(),
    val directorPlaneEquation: PlaneEquation? = null,
    val generatorCount: Int,
    val generatorTrimStartEnabled: Boolean = true,
    val generatorTrimEndEnabled: Boolean = true,
    val generatorTrimStart: Float = 0f,
    val generatorTrimEnd: Float = 1f,
    val generatorOverhangStart: Float = 100f,
    val generatorOverhangEnd: Float = 100f,
    val color: SerializableColor,
    val wireWidth: Float,
    val fillFaces: Boolean = false,
    val outlineCurveIdPudorys: String? = null,
    val outlineCurveIdNarys: String? = null,
    val outlineCurveIdBokorys: String? = null,
    val outlineCurveIdAxo: String? = null,
    val outlineGeometryVersion: Int = 0,
    val generatorLineIds: List<String> = emptyList(),
    val generatorLinesVersion: Int = 0,
    val creationIndex: Long,
    val show: Boolean,
)

private fun RuledSurfaceDirectrixRef.toSerializable() =
    SerializableRuledSurfaceDirectrixRef(objectId, kind)

private fun SerializableRuledSurfaceDirectrixRef.toRuntime() =
    RuledSurfaceDirectrixRef(objectId, kind)

private fun RuledSurfaceDirectrixSnapshot.toSerializable() =
    SerializableRuledSurfaceDirectrixSnapshot(
        reference = reference.toSerializable(),
        components = components,
        closed = closed,
        line = line?.toSerializable(),
        conic = conic?.toSerializable(),
        sphereCenter = sphereCenter,
        sphereRadius = sphereRadius,
        ellipseArcParams = ellipseArcParams,
        ellipseArcEnds = ellipseArcEnds,
        parabolaArcParams = parabolaArcParams,
        parabolaArcEnds = parabolaArcEnds,
        hyperbolaArcParams = hyperbolaArcParams,
        hyperbolaArcEnds = hyperbolaArcEnds,
    )

private fun SerializableRuledSurfaceDirectrixSnapshot.toRuntime() =
    RuledSurfaceDirectrixSnapshot(
        reference = reference.toRuntime(),
        components = components,
        closed = closed,
        line = line?.toRuntime(),
        conic = conic?.toRuntime(),
        sphereCenter = sphereCenter,
        sphereRadius = sphereRadius,
        ellipseArcParams = ellipseArcParams,
        ellipseArcEnds = ellipseArcEnds,
        parabolaArcParams = parabolaArcParams,
        parabolaArcEnds = parabolaArcEnds,
        hyperbolaArcParams = hyperbolaArcParams,
        hyperbolaArcEnds = hyperbolaArcEnds,
    )

fun RuledSurface3D.toSerializable() = SerializableRuledSurface3D(
    id = id,
    name = name,
    firstBoundaryDirectrix = firstBoundaryDirectrix.toSerializable(),
    secondBoundaryDirectrix = secondBoundaryDirectrix.toSerializable(),
    thirdDirectrix = thirdDirectrix?.toSerializable(),
    directrixOrderVersion = directrixOrderVersion,
    directorPlaneId = directorPlaneId,
    directrixSnapshots = directrixSnapshots.map { it.toSerializable() },
    directorPlaneEquation = directorPlaneEquation,
    generatorCount = generatorCount,
    generatorTrimStartEnabled = generatorTrimStartEnabled,
    generatorTrimEndEnabled = generatorTrimEndEnabled,
    generatorTrimStart = generatorTrimStart,
    generatorTrimEnd = generatorTrimEnd,
    generatorOverhangStart = generatorOverhangStart,
    generatorOverhangEnd = generatorOverhangEnd,
    color = SerializableColor.from(color),
    wireWidth = wireWidth,
    fillFaces = fillFaces,
    outlineCurveIdPudorys = outlineCurveIdPudorys,
    outlineCurveIdNarys = outlineCurveIdNarys,
    outlineCurveIdBokorys = outlineCurveIdBokorys,
    outlineCurveIdAxo = outlineCurveIdAxo,
    outlineGeometryVersion = outlineGeometryVersion,
    generatorLineIds = generatorLineIds,
    generatorLinesVersion = generatorLinesVersion,
    creationIndex = creationIndex,
    show = show,
)

fun SerializableRuledSurface3D.toRuntime() = RuledSurface3D(
    id = id,
    name = name,
    firstBoundaryDirectrix = firstBoundaryDirectrix.toRuntime(),
    secondBoundaryDirectrix = secondBoundaryDirectrix.toRuntime(),
    thirdDirectrix = thirdDirectrix?.toRuntime(),
    directrixOrderVersion = directrixOrderVersion,
    directorPlaneId = directorPlaneId,
    directrixSnapshots = directrixSnapshots.map { it.toRuntime() },
    directorPlaneEquation = directorPlaneEquation,
    generatorCount = generatorCount,
    generatorTrimStartEnabled = generatorTrimStartEnabled,
    generatorTrimEndEnabled = generatorTrimEndEnabled,
    generatorTrimStart = generatorTrimStart,
    generatorTrimEnd = generatorTrimEnd,
    generatorOverhangStart = generatorOverhangStart,
    generatorOverhangEnd = generatorOverhangEnd,
    color = color.toColor(),
    wireWidth = wireWidth,
    fillFaces = fillFaces,
    outlineCurveIdPudorys = outlineCurveIdPudorys,
    outlineCurveIdNarys = outlineCurveIdNarys,
    outlineCurveIdBokorys = outlineCurveIdBokorys,
    outlineCurveIdAxo = outlineCurveIdAxo,
    outlineGeometryVersion = outlineGeometryVersion,
    generatorLineIds = generatorLineIds,
    generatorLinesVersion = generatorLinesVersion,
    creationIndex = creationIndex,
    show = show,
)
