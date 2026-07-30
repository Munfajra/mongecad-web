package monge.input.intersections

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import utils.withSuffixOnce
import monge.input.axo.projectDirection3DToAxo
import monge.input.axo.projectPoint3DToAxoLocal
import model.Offset3D
import model.Point3D
import model.ProjectionMode
import model.classes.*
import model.normalize
import monge.input.segments.addSegment3DAndDetectSolids
import state.MongeState
import utils.allocIndex
import utils.update2DSnapshots

const val INTERSECTION_RESULT_NAME = "P"
const val INTERSECTION_RESULT_STROKE_WIDTH = 3f
val INTERSECTION_RESULT_COLOR: Color = Color.Red

fun styleIntersectionConic3D(conic3D: ConicSection3D): ConicSection3D =
    conic3D.copy(
        rawName = INTERSECTION_RESULT_NAME,
        color = INTERSECTION_RESULT_COLOR,
        strokeWidth = INTERSECTION_RESULT_STROKE_WIDTH
    )

/**
 * Společné oznámení o prázdném průniku. Volá se z kterékoli průnikové funkce,
 * jakmile zjistí, že se vybrané objekty neprotínají.
 */
fun notifyEmptyIntersection(state: MongeState) {
    state.showEmptyIntersectionDialog = true
    state.consInfo.value = "Průnik je prázdný – objekty se neprotínají."
}

/**
 * Vytvoří 3D bod průniku a jeho průměty ve všech relevantních průmětnách.
 * V Monge/KOTO stačí půdorys + nárys; v axonometrii přidá i bokorys a axo průmět.
 *
 * Vrací vytvořený [Point3D] (master objekt v [MongeState.sharedPoints3D]).
 */
fun addIntersectionPoint3D(
    state: MongeState,
    x: Float,
    y: Float,
    z: Float,
): Point3D {
    val p3d = Point3D(
        x, y, z,
        "P",
        color = INTERSECTION_RESULT_COLOR,
        width = INTERSECTION_RESULT_STROKE_WIDTH,
        creationIndex = allocIndex(state)
    )
    state.sharedPoints3D.add(p3d)

    // v axonometrii má svítit jen axo průmět, ostatní projekce skryj
    val showOthersInAxo = state.projectionMode != ProjectionMode.AXO

    // půdorys (x, y) + nárys (x, z) – základ pro všechny módy
    state.pointsPudorys.add(
        Point3DPudorys(x, y, "P".withSuffixOnce("₁"), parent = p3d, creationIndex = allocIndex(state), showInAxoInitial = showOthersInAxo)
    )
    state.pointsNarys.add(
        Point3DNarys(x, z, "P".withSuffixOnce("₂"), parent = p3d, creationIndex = allocIndex(state), showInAxoInitial = showOthersInAxo)
    )

    // v axonometrii navíc bokorys (y, z) a axonometrický průmět
    if (state.projectionMode == ProjectionMode.AXO) {
        state.pointsBokorys.add(
            Point3DBokorys(
                y = y,
                z = z,
                name = "P".withSuffixOnce("₃"),
                parent = p3d,
                creationIndex = allocIndex(state),
                showInAxoInitial = false
            )
        )
        state.basis?.let { basis ->
            val axo = projectPoint3DToAxoLocal(p3d, basis)
            state.pointsAxo.add(
                Point3DAxo(
                    x = axo.x,
                    y = axo.y,
                    name = "P".withSuffixOnce("ₐ"),
                    parent = p3d,
                    creationIndex = allocIndex(state)
                )
            )
        }
    }

    update2DSnapshots(state)
    state.triggerRedraw++
    return p3d
}

/**
 * Vytvoří 3D přímku průniku (parent [Line3D]) a její průměty.
 * V Monge/KOTO půdorys (x,y) + nárys (x,z); v axonometrii navíc bokorys (y,z) a axo.
 *
 * @param p0  libovolný bod ležící na přímce
 * @param dir směrový vektor přímky (nemusí být jednotkový)
 */
fun addIntersectionLine3D(
    state: MongeState,
    p0: Offset3D,
    dir: Offset3D,
): Line3D {
    val d = dir.normalize()
    val start = Point3D(
        p0.x, p0.y, p0.z,
        INTERSECTION_RESULT_NAME,
        color = INTERSECTION_RESULT_COLOR,
        width = INTERSECTION_RESULT_STROKE_WIDTH,
        creationIndex = allocIndex(state)
    )
    val line3D = Line3D(
        start = start,
        direction = d,
        name = INTERSECTION_RESULT_NAME,
        color = INTERSECTION_RESULT_COLOR,
        strokeWidth = INTERSECTION_RESULT_STROKE_WIDTH,
        creationIndex = allocIndex(state)
    )
    state.lines3D.add(line3D)

    // v axonometrii má svítit jen axo průmět, ostatní projekce skryj
    val showOthersInAxo = state.projectionMode != ProjectionMode.AXO

    // půdorys (x,y) + nárys (x,z)
    state.lines3DPudorys.add(
        Line3DProjectionPudorys(
            point = Point3DPudorys(p0.x, p0.y, name = INTERSECTION_RESULT_NAME.withSuffixOnce("₁")),
            direction = Offset(d.x, d.y),
            parent = line3D,
            parentId = line3D.id,
            creationIndex = allocIndex(state),
            showInAxoInitial = showOthersInAxo
        )
    )
    state.lines3DNarys.add(
        Line3DProjectionNarys(
            point = Point3DNarys(p0.x, p0.z, name = INTERSECTION_RESULT_NAME.withSuffixOnce("₂")),
            direction = Offset(d.x, d.z),
            parent = line3D,
            parentId = line3D.id,
            creationIndex = allocIndex(state),
            showInAxoInitial = showOthersInAxo
        )
    )

    // v axonometrii navíc bokorys (y,z) a axonometrický průmět
    if (state.projectionMode == ProjectionMode.AXO) {
        state.lines3DBokorys.add(
            Line3DProjectionBokorys(
                point = Point3DBokorys(y = p0.y, z = p0.z, name = INTERSECTION_RESULT_NAME.withSuffixOnce("₃")),
                direction = Offset(d.y, d.z),
                parent = line3D,
                parentId = line3D.id,
                creationIndex = allocIndex(state),
                showInAxoInitial = false
            )
        )
        state.basis?.let { basis ->
            val a0 = projectPoint3DToAxoLocal(start, basis)
            val ad = projectDirection3DToAxo(d, basis)
            state.lines3DAxo.add(
                Line3DProjectionAxo(
                    p = Point3DAxo(a0.x, a0.y, name = INTERSECTION_RESULT_NAME.withSuffixOnce("ₐ")),
                    dir = ad,
                    parent = line3D,
                    parentId = line3D.id,
                    creationIndex = allocIndex(state)
                )
            )
        }
    }

    update2DSnapshots(state)
    state.triggerRedraw++
    return line3D
}

/**
 * Vytvoří 3D úsečku průniku (parent [Segment3D]) a její průměty.
 * V Monge/KOTO půdorys (x,y) + nárys (x,z); v axonometrii navíc bokorys (y,z) a axo.
 * Vzniká např. když přímka splývá s površkou kužele/válce.
 */
fun addIntersectionSegment3D(
    state: MongeState,
    a: Offset3D,
    b: Offset3D,
): Segment3D {
    val pA = Point3D(a.x, a.y, a.z, INTERSECTION_RESULT_NAME, color = INTERSECTION_RESULT_COLOR, width = INTERSECTION_RESULT_STROKE_WIDTH, creationIndex = allocIndex(state))
    val pB = Point3D(b.x, b.y, b.z, INTERSECTION_RESULT_NAME, color = INTERSECTION_RESULT_COLOR, width = INTERSECTION_RESULT_STROKE_WIDTH, creationIndex = allocIndex(state))
    val seg3D = Segment3D(
        pA,
        pB,
        name = "",
        color = INTERSECTION_RESULT_COLOR,
        strokeWidth = INTERSECTION_RESULT_STROKE_WIDTH,
        creationIndex = allocIndex(state)
    )
    addSegment3DAndDetectSolids(state, seg3D)

    val showOthersInAxo = state.projectionMode != ProjectionMode.AXO

    state.segmentsPudorys.add(
        Segment2DPudorys(
            start = Point3DPudorys(a.x, a.y, name = "", parent = pA),
            end = Point3DPudorys(b.x, b.y, name = "", parent = pB),
            parent = seg3D,
            parentId = seg3D.id,
            showInAxoInitial = showOthersInAxo,
            creationIndex = allocIndex(state)
        )
    )
    state.segmentsNarys.add(
        Segment2DNarys(
            start = Point3DNarys(a.x, a.z, name = "", parent = pA),
            end = Point3DNarys(b.x, b.z, name = "", parent = pB),
            parent = seg3D,
            parentId = seg3D.id,
            showInAxoInitial = showOthersInAxo,
            creationIndex = allocIndex(state)
        )
    )

    if (state.projectionMode == ProjectionMode.AXO) {
        state.segmentsBokorys.add(
            Segment2DBokorys(
                start = Point3DBokorys(y = a.y, z = a.z, name = "", parent = pA),
                end = Point3DBokorys(y = b.y, z = b.z, name = "", parent = pB),
                parent = seg3D,
                parentId = seg3D.id,
                showInAxoInitial = false,
                creationIndex = allocIndex(state)
            )
        )
        state.basis?.let { basis ->
            val a0 = projectPoint3DToAxoLocal(pA, basis)
            val b0 = projectPoint3DToAxoLocal(pB, basis)
            state.segmentsAxo.add(
                Segment2DAxo(
                    start = Point3DAxo(a0.x, a0.y, name = "", parent = pA),
                    end = Point3DAxo(b0.x, b0.y, name = "", parent = pB),
                    parent = seg3D,
                    parentId = seg3D.id,
                    creationIndex = allocIndex(state)
                )
            )
        }
    }

    update2DSnapshots(state)
    state.triggerRedraw++
    return seg3D
}

/**
 * Přidá 3D kuželosečku průniku ([ConicSection3D]) a její průměty.
 * V Monge/KOTO půdorys + nárys; v axonometrii navíc bokorys a axo, přičemž v axu
 * zůstává viditelný jen axonometrický průmět.
 */
fun addIntersectionConic3D(state: MongeState, conic3D: ConicSection3D): ConicSection3D {
    val conic = styleIntersectionConic3D(conic3D)
    state.conics3D.add(conic)
    val showOthersInAxo = state.projectionMode != ProjectionMode.AXO

    // tři body sdružených průměrů (konce 1. průměru + konec sdruženého poloměru),
    // ze kterých vykreslovač rekonstruuje elipsu v jednotlivých průmětech
    val ra = conic.a ?: 1f
    val rb = conic.b ?: 1f
    val ctr = conic.p0
    val pA = ctr - conic.u * ra
    val pB = ctr + conic.u * ra
    val pC = ctr + conic.v * rb

    val pud = Matrix3x3.toCoefficients(conic.projectToXY())
    val pudConic = ConicSectionPudorys(
        a = pud[0], b = pud[1], c = pud[2], d = pud[3], e = pud[4], f = pud[5],
        rawName = conic.rawName, localColor = conic.color,
        strokeWidth = conic.strokeWidth, lineStyle = conic.lineStyle,
        parent = conic, parentId = conic.id,
        showInAxoInitial = showOthersInAxo, creationIndex = allocIndex(state)
    )
    state.conicsPudorys.add(pudConic)
    state.conicInputPointsPudorys[pudConic.id] =
        Triple(Offset(pA.x, pA.y), Offset(pB.x, pB.y), Offset(pC.x, pC.y))

    val nar = Matrix3x3.toCoefficients(conic.projectToXZ())
    val narConic = ConicSectionNarys(
        a = nar[0], b = nar[1], c = nar[2], d = nar[3], e = nar[4], f = nar[5],
        rawName = conic.rawName, localColor = conic.color,
        strokeWidth = conic.strokeWidth, lineStyle = conic.lineStyle,
        parent = conic, parentId = conic.id,
        showInAxoInitial = showOthersInAxo, creationIndex = allocIndex(state)
    )
    state.conicsNarys.add(narConic)
    state.conicInputPointsNarys[narConic.id] =
        Triple(Offset(pA.x, -pA.z), Offset(pB.x, -pB.z), Offset(pC.x, -pC.z))

    if (state.projectionMode == ProjectionMode.AXO) {
        val bok = Matrix3x3.toCoefficients(conic.projectToYZ())
        val bokConic = ConicSectionBokorys(
            a = bok[0], b = bok[1], c = bok[2], d = bok[3], e = bok[4], f = bok[5],
            rawName = conic.rawName, localColor = conic.color,
            strokeWidth = conic.strokeWidth, lineStyle = conic.lineStyle,
            parent = conic, parentId = conic.id,
            showInAxoInitial = false, creationIndex = allocIndex(state)
        )
        state.conicsBokorys.add(bokConic)
        state.conicInputPointsBokorys[bokConic.id] =
            Triple(Offset(pA.y, pA.z), Offset(pB.y, pB.z), Offset(pC.y, pC.z))

        state.basis?.let { basis ->
            val ax = Matrix3x3.toCoefficients(conic.projectToAxo(basis))
            val axoConic = ConicSectionAxo(
                a = ax[0], b = ax[1], c = ax[2], d = ax[3], e = ax[4], f = ax[5],
                rawName = conic.rawName, localColor = conic.color,
                strokeWidth = conic.strokeWidth, lineStyle = conic.lineStyle,
                parent = conic, parentId = conic.id,
                showInAxoInitial = true, creationIndex = allocIndex(state)
            )
            state.conicsAxo.add(axoConic)
            fun axoImg(p: Offset3D): Offset = projectPoint3DToAxoLocal(Point3D(p.x, p.y, p.z, ""), basis)
            state.conicInputPointsAxo[axoConic.id] =
                Triple(axoImg(pA), axoImg(pB), axoImg(pC))
        }
    }

    update2DSnapshots(state)
    state.triggerRedraw++
    return conic
}
