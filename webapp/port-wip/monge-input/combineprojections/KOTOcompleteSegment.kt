package monge.input.combineprojections

import serialization.commitSnapshot
import model.*
import model.classes.Point3DNarys
import model.classes.Segment2DNarys
import model.classes.Segment2DPudorys
import model.classes.Segment3D
import monge.input.segments.addSegment3DAndDetectSolids
import state.MongeState
import ui.mongeui.toolbar.updateConstructionInfo
import utils.allocIndex

fun upgradePudorysSegmentTo3DWithKotas(
    segP: Segment2DPudorys,
    zA: Float,
    zB: Float,
    state: MongeState
) {
    if (segP.parent != null) return

    val a1 = segP.start
    val b1 = segP.end

    // nové 3D body
    val A = Point3D(a1.x, a1.y, zA, name = a1.name.orEmpty(), superscript = a1.superscript, color = segP.color, creationIndex = allocIndex(state))
    val B = Point3D(b1.x, b1.y, zB, name = b1.name.orEmpty(), superscript = b1.superscript, color = segP.color, creationIndex = allocIndex(state))

    // ✅ NAVÁZAT existující půdorysné endpointy na tyto 3D body
    a1.parent = A
    b1.parent = B

    // 3D úsečka
    val seg3D = Segment3D(
        start = A,
        end = B,
        name = segP.name ?: "",
        color = segP.color,
        lineStyle = segP.lineStyle,
        strokeWidth = segP.strokeWidth, creationIndex = allocIndex(state)
    )

    // ✅ navázat půdorysný segment na 3D segment
    segP.parentId = seg3D.id
    segP.parent = seg3D

    // nárysné endpointy (projekce)
    val a2 = Point3DNarys(
        x = A.x,
        z = A.z,
        name = "",
        isSegmentEndpoint = true,
        parent = A,
        creationIndex = allocIndex(state)
    )
    val b2 = Point3DNarys(
        x = B.x,
        z = B.z,
        name = "",
        isSegmentEndpoint = true,
        parent = B,
        creationIndex = allocIndex(state)
    )

    val segN = Segment2DNarys(
        start = a2,
        end = b2,
        name = segP.name,
        parent = seg3D,
        localColor = null,
        localLineStyle = segP.lineStyle,      // nebo segP.localLineStyle pokud chceš lokální
        localStrokeWidth = null,
        parentId = seg3D.id, creationIndex = allocIndex(state)
    )

    // zapsat do state
    state.sharedPoints3D.add(A)
    state.sharedPoints3D.add(B)
    state.pointsNarys.add(a2)
    state.pointsNarys.add(b2)
    state.segmentsNarys.add(segN)
    addSegment3DAndDetectSolids(state, seg3D)

    // ✅ jen jednou – na konci

    updateConstructionInfo(state)
    commitSnapshot(state)

}
