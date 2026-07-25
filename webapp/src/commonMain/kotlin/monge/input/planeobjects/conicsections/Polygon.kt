package monge.input.planeobjects.conicsections

import geometry.liftNarysToPlane
import geometry.liftPudorysToPlane
import geometry.Vec3
import utils.withSuffixOnce
import utils.withSuffixOnce
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import serialization.commitSnapshot
import model.*
import model.classes.AidPointLogical
import model.classes.Plane3D
import model.classes.PlaneEquation
import model.classes.PlanePolygon2D
import model.classes.Point3DAxo
import model.classes.Point3DBokorys
import model.classes.Point3DNarys
import model.classes.Point3DPudorys
import model.classes.RegularPolygon3D
import model.classes.HelpSegmentPudorys
import model.classes.Segment2DAxo
import model.classes.Segment2DBokorys
import model.classes.Segment2DNarys
import model.classes.Segment2DPudorys
import model.classes.Segment3D
import model.classes.projectPoint3DToAxoLocal
import monge.input.ConicArcs.associated.PlaneEq


import monge.input.segments.addHelpSegmentPudorysAndDetectPlanePolygon
import state.MongeState
import ui.mongeui.toolbar.repeatCons
import ui.mongeui.toolbar.setProjectionPhase
import ui.resetStavu
import utils.allocIndex
import utils.UUID



fun ensurePointProjections(
    state: MongeState,
    p3: Point3D
): Pair<Point3DPudorys, Point3DNarys> {
    // PŮDORYS (₁)
    val p1 = state.pointsPudorys.firstOrNull { it.parent?.id == p3.id } ?: run {
        val obj = Point3DPudorys(
            x = p3.x, y = p3.y,
            name = p3.name.withSuffixOnce("₁"),
            parent = p3
        )
        state.pointsPudorys.add(obj)
        obj
    }

    // NÁRYS (₂)
    val p2 = state.pointsNarys.firstOrNull { it.parent?.id == p3.id } ?: run {
        val obj = Point3DNarys(
            x = p3.x, z = p3.z,
            name = p3.name.withSuffixOnce("₂"),
            parent = p3
        )
        state.pointsNarys.add(obj)
        obj
    }

    return p1 to p2
}

fun createSegmentProjectionsFor(
    state: MongeState,
    seg3: Segment3D,
    nameBase: String? = null,
    style: LineStyle = LineStyle.Solid,
    color: Color,
    width: Float,
): Pair<Segment2DPudorys, Segment2DNarys> {
    // koncové body – zajisti průměty

    val (a1, a2) = ensurePointProjections(state, seg3.start)
    val (b1, b2) = ensurePointProjections(state, seg3.end)

    // Půdorys
    val segP = Segment2DPudorys(
        start = a1, end = b1,
        name = nameBase?.withSuffixOnce("₁"),
        parent = seg3,
        parentId = seg3.id,
        localLineStyle = style,
        localStrokeWidth = width,
        localColor = color
    )
    state.segmentsPudorys.add(segP)

    // Nárys
    val segN = Segment2DNarys(
        start = a2, end = b2,
        name = nameBase?.withSuffixOnce("₂"),
        parent = seg3,
        parentId = seg3.id,
        localLineStyle = style,
        localStrokeWidth = width,
        localColor = color
    )
    state.segmentsNarys.add(segN)

    return segP to segN
}

private data class PolygonProjectionPoints(
    val pudorys: Point3DPudorys,
    val narys: Point3DNarys,
    val bokorys: Point3DBokorys,
    val axo: Point3DAxo
)

private fun ensurePointProjectionsForAxoPolygon(
    state: MongeState,
    p3: Point3D
): PolygonProjectionPoints? {
    val basis = state.basis ?: return null
    val p1 = Point3DPudorys(
        x = p3.x,
        y = p3.y,
        name = p3.name.withSuffixOnce("₁"),
        parent = p3,
        localColor = p3.color,
        localWidth = p3.width,
        creationIndex = allocIndex(state),
        showInAxoInitial = false
    ).also {
        it.showInAxo = false
        state.pointsPudorys.add(it)
    }
    val p2 = Point3DNarys(
        x = p3.x,
        z = p3.z,
        name = p3.name.withSuffixOnce("₂"),
        parent = p3,
        localColor = p3.color,
        localWidth = p3.width,
        creationIndex = allocIndex(state),
        showInAxoInitial = false
    ).also {
        it.showInAxo = false
        state.pointsNarys.add(it)
    }
    val p3b = Point3DBokorys(
        y = p3.y,
        z = p3.z,
        name = p3.name.withSuffixOnce("₃"),
        parent = p3,
        localColor = p3.color,
        localWidth = p3.width,
        creationIndex = allocIndex(state),
        showInAxoInitial = false
    ).also {
        it.showInAxo = false
        state.pointsBokorys.add(it)
    }
    val axoLocal = projectPoint3DToAxoLocal(Offset3D(p3.x, p3.y, p3.z), basis)
    val pA = Point3DAxo(
        x = axoLocal.x,
        y = axoLocal.y,
        name = p3.name.withSuffixOnce("ₐ"),
        parent = p3,
        localColor = p3.color,
        localWidth = p3.width,
        creationIndex = allocIndex(state),
        showInAxoInitial = true
    ).also {
        it.showInAxo = true
        state.pointsAxo.add(it)
    }
    return PolygonProjectionPoints(p1, p2, p3b, pA)
}

private fun createSegmentProjectionsForAxoPolygon(
    state: MongeState,
    seg3: Segment3D,
    start: PolygonProjectionPoints,
    end: PolygonProjectionPoints,
    style: LineStyle,
    color: Color,
    width: Float
): Triple<Segment2DPudorys, Segment2DNarys, Segment2DAxo> {
    val segP = Segment2DPudorys(
        start = start.pudorys,
        end = end.pudorys,
        name = seg3.name.withSuffixOnce("₁"),
        parent = seg3,
        parentId = seg3.id,
        localColor = color,
        localLineStyle = style,
        localStrokeWidth = width,
        creationIndex = seg3.creationIndex,
        showInAxoInitial = false
    ).also {
        it.showInAxo = false
        start.pudorys.parentSegment = it
        end.pudorys.parentSegment = it
        state.segmentsPudorys.add(it)
    }
    val segN = Segment2DNarys(
        start = start.narys,
        end = end.narys,
        name = seg3.name.withSuffixOnce("₂"),
        parent = seg3,
        parentId = seg3.id,
        localColor = color,
        localLineStyle = style,
        localStrokeWidth = width,
        creationIndex = seg3.creationIndex,
        showInAxoInitial = false
    ).also {
        it.showInAxo = false
        start.narys.parentSegment = it
        end.narys.parentSegment = it
        state.segmentsNarys.add(it)
    }
    Segment2DBokorys(
        start = start.bokorys,
        end = end.bokorys,
        name = seg3.name.withSuffixOnce("₃"),
        parent = seg3,
        parentId = seg3.id,
        localColor = color,
        localLineStyle = style,
        localStrokeWidth = width,
        creationIndex = seg3.creationIndex,
        showInAxoInitial = false
    ).also {
        it.showInAxo = false
        start.bokorys.parentSegment = it
        end.bokorys.parentSegment = it
        state.segmentsBokorys.add(it)
    }
    val segA = Segment2DAxo(
        start = start.axo,
        end = end.axo,
        name = seg3.name.withSuffixOnce("ₐ"),
        parent = seg3,
        parentId = seg3.id,
        localColor = color,
        localLineStyle = style,
        localStrokeWidth = width,
        creationIndex = seg3.creationIndex,
        showInAxoInitial = true
    ).also {
        it.showInAxo = true
        start.axo.parentSegment = it
        end.axo.parentSegment = it
        state.segmentsAxo.add(it)
    }
    return Triple(segP, segN, segA)
}


fun planeEquationFromPlane3D(plane: Plane3D): PlaneEq {
    // Použij svůj existující výpočet. Placeholder:
    if (plane.equation!=null){
    val a = plane.equation.a
    val b = plane.equation.b
    val c = plane.equation.c
    val d = plane.equation.d

        return PlaneEq(a,b,c,d)

    }
    else return PlaneEq(0f,0f,0f,0f)
}


fun makeRegularPolygonVertices3D(center: Vec3, vertex0: Vec3, n: Int, planeNormal: Vec3): List<Vec3> {
    require(n >= 3) { "Polygon must have ≥ 3 sides." }
    val Rvec = vertex0 - center
    val R = Rvec.norm()
    val u = Rvec.normalize()
    val v = planeNormal.cross(u).normalize()
    val twoPi = (2.0 * kotlin.math.PI).toFloat()
    return (0 until n).map { i ->
        val theta = twoPi * (i.toFloat() / n.toFloat())
        val ct = kotlin.math.cos(theta)
        val st = kotlin.math.sin(theta)
        center + (u * (R * ct) + v * (R * st))
    }
}

fun buildRegularPolygonWithProjections(
    state: MongeState,
    center: Vec3,
    vertex0: Vec3,
    n: Int,
    normal: Vec3,
    baseName: String = "P",
    planeId: String,
    color: Color,
    width: Float,
    style: LineStyle
): RegularPolygon3D {
    // 1) Spočítej 3D vrcholy
    val verts = makeRegularPolygonVertices3D(center, vertex0, n, normal)

    // 2) Ulož 3D body (jen vrcholy), pojmenované A, B, C...
    val points3D = verts.mapIndexed { i, v ->
        val letter = indexToLetter(i, state.namingPolygon, state.namingPolygonStartLetter)
        val p = Point3D(
            x = v.x, y = v.y, z = v.z,
            name = letter,
            color = color,
        )
        state.sharedPoints3D.add(p)
        p
    }

    // 3) 3D úsečky
    val seg3D = buildList {
        for (i in points3D.indices) {
            val a = points3D[i]
            val b = points3D[(i + 1) % points3D.size]
            val s = Segment3D(
                id = UUID.randomUUID().toString(),
                start = a, end = b,
                name = "",
                color = color,
                strokeWidth = width
            )
            state.segments3D.add(s)
            add(s)
        }
    }

    // 4) Projekce vrcholů (pro pohodlné mazání/manipulace)
    val vertsP = mutableListOf<String>()
    val vertsN = mutableListOf<String>()
    points3D.forEach { p3 ->
        val (p1, p2) = ensurePointProjections(state, p3)
        vertsP += p1.id
        vertsN += p2.id
    }

    // 5) Projekce hran (TADY se reálně přidávají do state.* a parent = seg3D)
    val segPIds = mutableListOf<String>()
    val segNIds = mutableListOf<String>()
    val segAIds = mutableListOf<String>()
    seg3D.forEach { s3 ->
        val (sp, sn) = createSegmentProjectionsFor(
            state = state,
            seg3 = s3,
            nameBase = s3.name,
            style = style,
            color = color,
            width = width,
        )
        segPIds += sp.id
        segNIds += sn.id
    }

    // 6) Parent polygon
    val polygon = RegularPolygon3D(
        name = baseName,
        n = n,
        planeId = planeId,
        vertexPointIds = points3D.map { it.id },
        segmentIds3D = seg3D.map { it.id },
        vertexPointIdsPudorys = vertsP,
        vertexPointIdsNarys = vertsN,
        segmentIdsPudorys = segPIds,
        segmentIdsNarys = segNIds,
        segmentIdsAxo = segAIds,
        style = style, creationIndex = allocIndex(state)
    )
    state.polygons3D.add(polygon)

    return polygon
}

fun buildRegularPolygonWithAxoProjections(
    state: MongeState,
    center: Vec3,
    vertex0: Vec3,
    n: Int,
    normal: Vec3,
    baseName: String = "P",
    planeId: String,
    color: Color,
    width: Float,
    style: LineStyle
): RegularPolygon3D? {
    val verts = makeRegularPolygonVertices3D(center, vertex0, n, normal)

    val points3D = verts.mapIndexed { i, v ->
        val letter = indexToLetter(i, state.namingPolygon, state.namingPolygonStartLetter)
        Point3D(
            x = v.x,
            y = v.y,
            z = v.z,
            name = letter,
            color = color,
            width = width,
        ).also { state.sharedPoints3D.add(it) }
    }

    val projections = points3D.map { p3 ->
        ensurePointProjectionsForAxoPolygon(state, p3) ?: return null
    }

    val seg3D = buildList {
        for (i in points3D.indices) {
            val a = points3D[i]
            val b = points3D[(i + 1) % points3D.size]
            val segment = Segment3D(
                id = UUID.randomUUID().toString(),
                start = a,
                end = b,
                name = "",
                color = color,
                strokeWidth = width,
                lineStyle = style,
                creationIndex = allocIndex(state)
            )
            state.segments3D.add(segment)
            add(segment)
        }
    }

    val segPIds = mutableListOf<String>()
    val segNIds = mutableListOf<String>()
    val segAIds = mutableListOf<String>()
    seg3D.forEachIndexed { i, segment ->
        val start = projections[i]
        val end = projections[(i + 1) % projections.size]
        val (p, narys, axo) = createSegmentProjectionsForAxoPolygon(
            state = state,
            seg3 = segment,
            start = start,
            end = end,
            style = style,
            color = color,
            width = width
        )
        segPIds += p.id
        segNIds += narys.id
        segAIds += axo.id
    }

    val polygon = RegularPolygon3D(
        name = baseName,
        n = n,
        planeId = planeId,
        vertexPointIds = points3D.map { it.id },
        segmentIds3D = seg3D.map { it.id },
        vertexPointIdsPudorys = projections.map { it.pudorys.id },
        vertexPointIdsNarys = projections.map { it.narys.id },
        segmentIdsPudorys = segPIds,
        segmentIdsNarys = segNIds,
        segmentIdsAxo = segAIds,
        color = color,
        width = width,
        style = style,
        creationIndex = allocIndex(state)
    )
    state.polygons3D.add(polygon)

    return polygon
}

// handleRegularPolygonInPlaneClickAxo(...) – AXO varianta n-úhelníku v rovině; web axonometrii nemá.


fun handleRegularPolygonInPlaneClickPudorys(state: MongeState, logical: Offset) {
    if (state.drawobjects != Mongeobjects.REGULAR_POLYGON_IN_PLANE) return
    when (state.projectionMode) {
    ProjectionMode.MONGE, ProjectionMode.KOTO -> { when (state.projectionPhase) {
        "pudorys_start" -> {
            // Rovina mohla být označena kliknutím na stopu (→ selectedPlaneForCircle),
            // přičemž selectedPlanes je v tu chvíli prázdná. Neporušuj výběr přepsáním null.
            val spane = state.selectedPlaneForCircle ?: state.selectedPlanes.firstOrNull()
            if (spane == null) {
                println("ℹ️ Vyber nejdřív rovinu.")
                return
            }
            state.selectedPlaneForCircle = spane
            setProjectionPhase(  "rp_center_pud" ,state)
            println("➡️ Zvol průmět STŘEDU v půdorysu.")
        }

        // 1) první klik = STŘED (v pudorysu)
        "rp_center_pud" -> {
            state.pendingPoint1 = logical  // nyní uložíme střed
            setProjectionPhase("rp_vertex_pud",state)
            println("➡️ Zvol průmět VRCHOLU v půdorysu.")
        }

        // 2) druhý klik = VRCHOL (v pudorysu), dopočet 3D a stavba polygonu
        "rp_vertex_pud" -> {
            val plane = state.selectedPlaneForCircle ?: return
            val plane3D = state.selectedPlaneForCircle
            if (plane3D == null) {
                println("❗Rovina nenalezena.")
                setProjectionPhase("pudorys_start",state)
                return
            }
            val eq = plane3D.equation ?: return

            val cPud = state.pendingPoint1  // uložený střed z 1. kliku
            val vPud = logical              // teď kliknutý vrchol
            if (cPud == null) {
                println("❗Chybí první klik (střed).")
                setProjectionPhase("pudorys_start",state)
                return
            }

            // Zvednutí z půdorysu → do roviny
            val C3 = liftPudorysToPlane(cPud.x, cPud.y, eq)
            val V3 = liftPudorysToPlane(vPud.x, vPud.y, eq)

            if (C3 == null) {
                println("❗Nelze zvednout střed: rovina má c≈0 (rovnoběžná s půdorysnou). Zvol jiný pohled.")
                setProjectionPhase("pudorys_start",state)
                return
            }
            if (V3 == null) {
                println("❗Nelze zvednout vrchol: rovina má c≈0 (rovnoběžná s půdorysnou). Zvol jiný pohled.")
                setProjectionPhase("pudorys_start",state)
                return
            }

            // Postav polygon
            val n = state.regularPolygon.sides.coerceIn(3,30)
            val normal = planeEquationFromPlane3D(plane3D).normal().normalize()

            // Ochrana: když je V3 == C3 nebo R ~ 0, nedělej nic
            if ((Vec3(V3.x - C3.x, V3.y - C3.y, V3.z - C3.z)).norm() < 1e-6f) {
                println("❗Vrchol se shoduje se středem – nelze vytvořit polygon.")
                setProjectionPhase("pudorys_start",state)
                return
            }


            buildRegularPolygonWithProjections(
                state = state,
                center = Vec3(C3.x, C3.y, C3.z),
                vertex0 = Vec3(V3.x, V3.y, V3.z),
                n = n,
                normal = normal,
                baseName = "P",
                planeId = plane.id,
                color = state.currentLineStyleSettings.color,
                width = state.currentLineStyleSettings.strokeWidth,
                style = state.currentLineStyleSettings.style

            )
            commitSnapshot(state)

            // Úklid stavu a návrat na start
            state.pendingPoint1 = null
            state.pendingPoint2 = null
            setProjectionPhase("pudorys_start",state)
            resetStavu(state)
            repeatCons(state)
            println("✅ Vytvořen pravidelný $n-úhelník v rovině ${plane3D.name}.")
        }
    }}

        ProjectionMode.AXO -> TODO()
        ProjectionMode.PLANE -> when (state.projectionPhase) {


            // 1) první klik = STŘED (v půdorysu)
            "pudorys_start" -> {
                state.pendingPoint1 = logical
                setProjectionPhase("rp_vertex_pud", state)
                println("➡️ Zvol VRCHOL (určuje poloměr + orientaci) v půdorysu.")
            }

            // 2) druhý klik = VRCHOL (v půdorysu), postavit 2D polygon
            "rp_vertex_pud" -> {
                val c = state.pendingPoint1
                if (c == null) {
                    println("❗Chybí střed – vrať se na start.")
                    setProjectionPhase("pudorys_start", state)
                    return
                }

                val v0 = logical
                val n = state.regularPolygon.sides.coerceIn(3, 30)

                val dx = v0.x - c.x
                val dy = v0.y - c.y
                val r = kotlin.math.sqrt(dx * dx + dy * dy)

                if (r < 1e-4f) {
                    println("❗Vrchol je moc blízko středu – nelze vytvořit polygon.")
                    setProjectionPhase("pudorys_start", state)
                    state.pendingPoint1 = null
                    return
                }


                buildRegularPolygonInPlane2D(
                    state = state,
                    centerPud = c,          // Offset
                    vertex0Pud = logical,   // Offset
                    n = n,
                    baseName = "P",
                    color = state.currentHelpLineStyleSettings.color,
                    width = state.currentHelpLineStyleSettings.strokeWidth,
                    style = state.currentHelpLineStyleSettings.style
                )
                commitSnapshot(state)

                state.pendingPoint1 = null
                setProjectionPhase("pudorys_start", state)
                resetStavu(state)
                repeatCons(state)
                state.triggerRedraw++
            }
        }

    }

}

fun indexToLetter(idx: Int, naming: Boolean, startLetter: Char = 'A'): String {
    if (!naming) return "" else
    return ('A'.code + (startLetter.uppercaseChar().code - 'A'.code + idx) % 26).toChar().toString()
}

fun handleRegularPolygonInPlaneClickNarys(state: MongeState, logical: Offset) {
    if (state.drawobjects != Mongeobjects.REGULAR_POLYGON_IN_PLANE) return

    when (state.projectionPhase) {
        "narys_start" -> {
            // Rovina mohla být označena kliknutím na stopu (→ selectedPlaneForCircle),
            // přičemž selectedPlanes je v tu chvíli prázdná. Neporušuj výběr přepsáním null.
            val spane = state.selectedPlaneForCircle ?: state.selectedPlanes.firstOrNull()
            if (spane == null) {
                println("ℹ️ Vyber nejdřív rovinu.")
                return
            }
            state.selectedPlaneForCircle = spane
            setProjectionPhase("rp_center_nar", state)
            println("➡️ Zvol průmět STŘEDU v nárysu.")
        }

        // 1) první klik = STŘED (v nárysu)
        "rp_center_nar" -> {
            state.pendingPoint1 = logical  // uložíme střed (x, -z)
            setProjectionPhase("rp_vertex_nar", state)
            println("➡️ Zvol průmět VRCHOLU v nárysu.")
        }

        // 2) druhý klik = VRCHOL (v nárysu), dopočet 3D a stavba polygonu
        "rp_vertex_nar" -> {
            val plane = state.selectedPlaneForCircle ?: return
            val plane3D = state.selectedPlaneForCircle
            if (plane3D == null) {
                println("❗Rovina nenalezena.")
                setProjectionPhase("narys_start", state)
                return
            }
            val eq = plane3D.equation ?: return

            val cNar = state.pendingPoint1   // uložený střed z 1. kliku (x, -z)
            val vNar = logical               // teď kliknutý vrchol (x, -z)
            if (cNar == null) {
                println("❗Chybí první klik (střed).")
                setProjectionPhase("narys_start", state)
                return
            }

            // Vytažení (x, z) z nárysových logických souřadnic:
            // POZOR: pokud držíš v NÁRYSU přímo z (bez mínusu), nahraď `-cNar.y` → `cNar.y` a `-vNar.y` → `vNar.y`.
            val cX = cNar.x
            val cZ = -cNar.y
            val vX = vNar.x
            val vZ = -vNar.y

            // Zvednutí z nárysu → do roviny (řeší se y; vyžaduje b ≠ 0)
            val C3 = liftNarysToPlane(cX, cZ, eq)
            val V3 = liftNarysToPlane(vX, vZ, eq)

            if (C3 == null) {
                println("❗Nelze zvednout střed: rovina má b≈0 (rovnoběžná s nárysnou). Zvol jiný pohled.")
                setProjectionPhase("narys_start", state)
                return
            }
            if (V3 == null) {
                println("❗Nelze zvednout vrchol: rovina má b≈0 (rovnoběžná s nárysnou). Zvol jiný pohled.")
                setProjectionPhase("narys_start", state)
                return
            }

            // Postav polygon
            val n = state.regularPolygon.sides.coerceIn(3, 30)
            val normal = planeEquationFromPlane3D(plane3D).normal().normalize()

            // Ochrana: když je V3 == C3 nebo R ~ 0, nedělej nic
            if ((Vec3(V3.x - C3.x, V3.y - C3.y, V3.z - C3.z)).norm() < 1e-6f) {
                println("❗Vrchol se shoduje se středem – nelze vytvořit polygon.")
                setProjectionPhase("narys_start", state)
                return
            }


            buildRegularPolygonWithProjections(
                state = state,
                center = Vec3(C3.x, C3.y, C3.z),
                vertex0 = Vec3(V3.x, V3.y, V3.z),
                n = n,
                normal = normal,
                baseName = "P",
                planeId = plane.id,
                color = state.currentLineStyleSettings.color,
                width = state.currentLineStyleSettings.strokeWidth,
                style = state.currentLineStyleSettings.style
            )
            commitSnapshot(state)

            // Úklid a návrat na start
            state.pendingPoint1 = null
            state.pendingPoint2 = null
            setProjectionPhase("narys_start", state)
            resetStavu(state)
            repeatCons(state)
            println("✅ Vytvořen pravidelný $n-úhelník v rovině ${plane3D.name} (nárys).")
        }
    }
}
fun buildRegularPolygonInPlane2D(
    state: MongeState,
    centerPud: Offset,
    vertex0Pud: Offset,
    n: Int,
    baseName: String = "P",
    color: Color,
    width: Float,
    style: LineStyle
): PlanePolygon2D {
    val nn = n.coerceIn(3, 30)

    val dx = vertex0Pud.x - centerPud.x
    val dy = vertex0Pud.y - centerPud.y
    val r = kotlin.math.sqrt(dx * dx + dy * dy)
    require(r > 1e-4f) { "Vertex too close to center." }

    val startAngle = kotlin.math.atan2(dy, dx)
    val step = (2.0 * kotlin.math.PI / nn).toFloat()

    val verts2D: List<Offset> = List(nn) { i ->
        val a = startAngle + i * step
        Offset(
            x = centerPud.x + r * kotlin.math.cos(a),
            y = centerPud.y + r * kotlin.math.sin(a)
        )
    }

    // V PLANE jsou registrované vrcholy AidPointLogical. Point3DPudorys níže
    // zůstávají jen interními endpointy HelpSegmentPudorys a do state.pointsPudorys
    // se nepřidávají, aby se při převodu režimu netvářily jako průměty.
    val aidPoints = verts2D.mapIndexed { i, point ->
        val letter = indexToLetter(i, state.namingPolygon, state.namingPolygonStartLetter)
        AidPointLogical(
            x = point.x,
            y = point.y,
            name = letter,
            color = color,
            width = width,
            creationIndex = allocIndex(state)
        ).also {
            state.aidPointsLogical.add(it)
        }
    }
    val points2D = aidPoints.map { aidPoint ->
        Point3DPudorys(
            x = aidPoint.x,
            y = aidPoint.y,
            name = aidPoint.name,
            isSegmentEndpoint = true,
            localColor = aidPoint.color,
            localWidth = aidPoint.width
        )
    }

    val segments2D = points2D.indices.map { i ->
        HelpSegmentPudorys(
            start = points2D[i],
            end = points2D[(i + 1) % points2D.size],
            name = "",
            localColor = color,
            localLineStyle = style,
            localStrokeWidth = width,
            creationIndex = allocIndex(state)
        )
    }
    val polygonSegmentIds = segments2D.mapTo(mutableSetOf()) { it.id }
    var polygon: PlanePolygon2D? = null
    for (segment in segments2D) {
        polygon = addHelpSegmentPudorysAndDetectPlanePolygon(
            state = state,
            segment = segment,
            allowedSegmentIds = polygonSegmentIds
        ) ?: polygon
    }

    val detected = requireNotNull(polygon) {
        "Pravidelný PLANE mnohoúhelník se nepodařilo uzavřít."
    }
    val completed = detected.copy(
        name = baseName,
        vertexAidPointIds = aidPoints.map { it.id }
    )
    val index = state.planePolygons2D.indexOfFirst { it.id == detected.id }
    if (index >= 0) state.planePolygons2D[index] = completed
    return completed
}
