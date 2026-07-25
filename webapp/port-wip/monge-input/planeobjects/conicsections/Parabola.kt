package monge.input.planeobjects.conicsections

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import serialization.commitSnapshot
import model.*
import model.classes.ConicSection3D
import model.classes.ConicSectionAxo
import model.classes.ConicSectionBokorys
import model.classes.ConicSectionNarys
import model.classes.ConicSectionPudorys
import model.classes.Matrix2x2
import model.classes.Matrix3x3
import model.classes.Plane3D
import model.classes.PlaneEquation
import model.classes.prettyFormat
import model.classes.projectToXY
import model.classes.projectToXZ
import model.classes.projectToYZ
import model.classes.projectToAxo
import model.classes.projectPoint3DToAxoLocal
import model.classes.projectVector3DToAxoLocal
import model.normalize
import model.classes.times
import model.classes.transpose
import monge.input.axo.AxoRenderBasis
import monge.input.planeobjects.planelift.LiftShow
import monge.input.planeobjects.planelift.liftShow
import state.MongeState
import ui.mongeui.toolbar.repeatCons
import ui.mongeui.toolbar.setProjectionPhase
import ui.resetStavu
import utils.allocIndex
import utils.dot
import utils.normalize
import kotlin.math.abs
import kotlin.math.sqrt
import monge.input.conixections.computeParabolaFromVertexAndFocus as computeParabolaProjectionFromVertexAndFocus
import monge.input.conixections.computeParabolaFromVertexAndFocusNarys as computeParabolaProjectionFromVertexAndFocusNarys

private const val EPS = 1e-6f

private fun finiteOffsetOrNull(x: Float, y: Float): Offset? =
    if (x.isFinite() && y.isFinite()) Offset(x, y) else null

private fun Offset.isUsable(): Boolean =
    isSpecified && x.isFinite() && y.isFinite()

data class XYDegenerate(
    val origin: Offset,   // vrchol degenerované projekce v XY
    val dir: Offset,      // jednotkový směr v XY (ray/line)
    val isLine: Boolean   // true = přímka, false = polopřímka
)

data class XZDegeneracy(
    val origin: Offset,   // začátek (nejzazší bod) v logickém XZ
    val dir: Offset,      // směr jednotkový v XZ
    val isLine: Boolean   // true = přímka, false = polopřímka
)

data class XZDegenerate(
    val origin: Offset, // nejzazší bod v XZ
    val dir: Offset,    // jednotkový směr v XZ
    val isLine: Boolean // true = přímka, false = polopřímka
)

fun computeXZDegeneracyFromVF(eq: PlaneEquation, v3: Offset3D, f3: Offset3D): XZDegenerate {
    val nrm = Offset3D(eq.a, eq.b, eq.c).normalize()

    // směr průsečnice s XZ: uDir3D ∥ (n × e_y)
    val ey = Offset3D(0f, 1f, 0f)
    var uDir3D = nrm.cross(ey)
    var uDir = Offset(uDir3D.x, uDir3D.z)
    var L = uDir.getDistance()
    if (L < EPS) { uDir = Offset(1f, 0f); L = 1f }
    uDir /= L

    // osa paraboly v rovině a „normálový“ směr v rovině
    val axis3D   = (f3 - v3).normalized()
    val nInPlane = (nrm.cross(axis3D)).normalized()

    // projekce do XZ
    val uXZ = Offset(nInPlane.x, nInPlane.z) // lineární člen
    val vXZ = Offset(axis3D.x,   axis3D.z)   // kvadratický člen

    // ohnisková „poloosa“ (stejná logika jako u XY)
    val p = (f3 - v3).length().coerceAtLeast(EPS)

    // koeficienty kvadratické funkce s(u) = α u + β u^2 podél uDir
    val alpha = (uXZ.x * uDir.x + uXZ.y * uDir.y)
    val beta  = (vXZ.x * uDir.x + vXZ.y * uDir.y) / (4f * p)

    val Vxz = Offset(v3.x, v3.z)

    return if (kotlin.math.abs(beta) < 1e-8f) {
        // přímka: procházející Vxz ve směru uDir
        XZDegenerate(origin = Vxz, dir = uDir, isLine = true)
    } else {
        val tStar = -alpha / (2f * beta)
        val sMin  = alpha * tStar + beta * tStar * tStar
        val origin = Vxz + uDir * sMin
        val dir = if (beta >= 0f) uDir else Offset(-uDir.x, -uDir.y)
        XZDegenerate(origin = origin, dir = dir, isLine = false)
    }
}


fun computeXYDegeneracyFromVF(eq: PlaneEquation, v3: Offset3D, f3: Offset3D): XYDegenerate {
    val nrm = Offset3D(eq.a, eq.b, eq.c).normalize()
    // směr průsečnice s XY: uDir ∥ (n × e_z) = (b,-a,0)
    var uDir = Offset(nrm.y, -nrm.x)
    var L = uDir.getDistance()
    if (L < EPS) { uDir = Offset(1f, 0f); L = 1f }
    uDir /= L

    // osa paraboly v rovině a „normálový“ směr v rovině
    val axis3D   = (f3 - v3).normalized()
    val nInPlane = (nrm.cross(axis3D)).normalized()

    // projekce do XY
    val uXY = Offset(nInPlane.x, nInPlane.y) // lineární člen
    val vXY = Offset(axis3D.x,   axis3D.y)   // kvadratický člen
    val p   = (f3 - v3).length().coerceAtLeast(EPS)

    val alpha = (uXY.x * uDir.x + uXY.y * uDir.y)
    val beta  = (vXY.x * uDir.x + vXY.y * uDir.y) / (4f * p)

    val Vxy = Offset(v3.x, v3.y)

    return if (kotlin.math.abs(beta) < 1e-8f) {
        // přímka: procházející Vxy ve směru uDir
        XYDegenerate(origin = Vxy, dir = uDir, isLine = true)
    } else {
        val tStar = -alpha / (2f * beta)
        val sMin  = alpha * tStar + beta * tStar * tStar
        val origin = Vxy + uDir * sMin
        val dir = if (beta >= 0f) uDir else Offset(-uDir.x, -uDir.y)
        XYDegenerate(origin = origin, dir = dir, isLine = false)
    }
}

fun handleParabolaInPlaneConstructionNarys(logical: Offset, state: MongeState) {
    // 1️⃣ Vyber rovinu, ve které budeme stavět
    if (state.selectedPlaneForCircle == null) {
        val rememberedPlane = state.selectedPlanes.firstOrNull()

        if (rememberedPlane != null) {
            val eq = rememberedPlane.equation
            if (eq == null) {
                println("❌ Vybraná rovina nemá rovnici – nelze zkontrolovat orientaci.")
                return
            }

            val normal = Offset3D(eq.a, eq.b, eq.c).normalize()
            val isVerticalInNarys = abs(normal.y) < 1e-3f

            if (isVerticalInNarys) {
                println("❌ Nelze konstruovat elipsu v nárysu – rovina '${rememberedPlane.name}' je kolmá k nárysně.")
                return
            }

            state.selectedPlaneForCircle = rememberedPlane
            println("🟦 Rovina '${rememberedPlane.name}' vybrána pro konstrukci elipsy.")
        } else {
            println("⚠️ Neoznačena žádná rovina – vyber jednu kliknutím.")
            return
        }
    }

    val plane = state.selectedPlaneForCircle!!
    val eq = plane.equation ?: run {
        println("❌ Rovina nemá rovnici!")
        return
    }
    val EPS = 1e-6f
    val isPerpToPudorys = abs(eq.c) < EPS   // ⬅️ rovina kolmá na půdorysnu (c≈0)
    if (abs(eq.b) < EPS) {                  // ⬅️ z (x,z) nedopočítáme y
        println("❌ V nárysu nelze konstruovat – |b|≈0 (z (x,z) nedopočítám y).")
        return
    }
    when (state.projectionPhase) {
        // startujeme sběr nárysu
        "narys_start" -> {
            setProjectionPhase("narys_vertex", state)
        }

        // první klik = vrchol
        "narys_vertex" -> {
            state.pendingPoint1 = Offset(logical.x,-logical.y)
            setProjectionPhase("narys_focus", state)
        }

        // druhý klik = ohnisko
        "narys_focus" -> {
            val vertex2D = state.pendingPoint1!!
            val focus2D  = Offset(logical.x,-logical.y)
            if ((focus2D - vertex2D).getDistance() < 1e-6f) {
                println("⚠️ Vrchol a ohnisko paraboly splývají (nárys).")
                return
            }

            val activeNarysConic = computeParabolaProjectionFromVertexAndFocusNarys(vertex2D, focus2D)
            val rawName = state.inputName.ifBlank { "π" }
            val sourceNarys = ConicSectionNarys(
                a = activeNarysConic.a, b = activeNarysConic.b, c = activeNarysConic.c,
                d = activeNarysConic.d, e = activeNarysConic.e, f = activeNarysConic.f,
                rawName = rawName,
                localColor = state.currentLineStyleSettings.color,
                strokeWidth = state.currentLineStyleSettings.strokeWidth,
                lineStyle = state.currentLineStyleSettings.style
            )

            // 3️⃣ Zvedneme aktivní nárysnou parabolu do roviny.
            val conic3D = canonizeParabolaFrame(
                liftConicToPlaneFromNarys(sourceNarys, plane) ?: run {
                    println("❌ Zvednutí paraboly z nárysu do roviny selhalo.")
                    return
                }
            ).copy(creationIndex = allocIndex(state))
            val vertexFocus3D = vertexFocus3DFromLocalConic(conic3D)

            // 4️⃣ Vypíšeme a promítneme do obou průmětů
            println("📐 3D matice kuželosečky:")
            println(conic3D.matrix.prettyFormat())

            val coeffsPudorys = Matrix3x3.toCoefficients(conic3D.projectToXY())
            val coeffsNarys   = Matrix3x3.toCoefficients(conic3D.projectToXZ())
            println("📏 Koeficienty PŮDORYS: ${coeffsPudorys.joinToString()}")
            println("📏 Koeficienty NÁRYS:   ${coeffsNarys.joinToString()}")

            // 5️⃣ Sestavíme ConicSection pro kreslení
            val pudorys = ConicSectionPudorys(
                a = coeffsPudorys[0], b = coeffsPudorys[1], c = coeffsPudorys[2],
                d = coeffsPudorys[3], e = coeffsPudorys[4], f = coeffsPudorys[5],
                rawName = conic3D.rawName,
                localColor = conic3D.color,
                strokeWidth = conic3D.strokeWidth,
                lineStyle = conic3D.lineStyle,
                parent = conic3D, creationIndex = allocIndex(state)
            )
            val narys = ConicSectionNarys(
                a = activeNarysConic.a, b = activeNarysConic.b, c = activeNarysConic.c,
                d = activeNarysConic.d, e = activeNarysConic.e, f = activeNarysConic.f,
                rawName = conic3D.rawName,
                localColor = conic3D.color,
                strokeWidth = conic3D.strokeWidth,
                lineStyle = conic3D.lineStyle,
                parent = conic3D, creationIndex = allocIndex(state)
            )

            // 6️⃣ Vypočteme 2D vrchol a ohnisko pro nárys (pomocí té nové implicitní funkce)
            var vertexP: Offset
            var focusP: Offset
            var vertexN: Offset
            var focusN: Offset
            if (isPerpToPudorys) {
                // 1) Koeficienty půdorysné degenerace
                val coeffsXY = Matrix3x3.toCoefficients(conic3D.projectToXY())

                // 2) Sestav 2D conic objekty
                val pud = ConicSectionPudorys(
                    a = coeffsXY[0], b = coeffsXY[1], c = coeffsXY[2],
                    d = coeffsXY[3], e = coeffsXY[4], f = coeffsXY[5],
                    rawName = conic3D.rawName, localColor = conic3D.color,
                    strokeWidth = conic3D.strokeWidth, lineStyle = conic3D.lineStyle,
                    parent = conic3D, creationIndex = allocIndex(state)
                )
                val nar = ConicSectionNarys(
                    a = activeNarysConic.a, b = activeNarysConic.b, c = activeNarysConic.c,
                    d = activeNarysConic.d, e = activeNarysConic.e, f = activeNarysConic.f,
                    rawName = conic3D.rawName, localColor = conic3D.color,
                    strokeWidth = conic3D.strokeWidth, lineStyle = conic3D.lineStyle,
                    parent = conic3D, creationIndex = allocIndex(state)
                )

                // 3) XY (půdorys): degenerace z helperu → origin/dir/isLine
                val (vertex3D, focus3D) = vertexFocus3D ?: run {
                    println("❌ Nepodařilo se vypočítat 3D vrchol/ohnisko paraboly.")
                    return
                }
                val deg = computeXYDegeneracyFromVF(eq, v3 = vertex3D, f3 = focus3D)
                val originXY = deg.origin
                val dirXY    = deg.dir

                // 5) Uložení do stavu

                state.conics3D.add(conic3D)
                state.conicsPudorys.add(pud)
                state.conicsNarys.add(nar)

                // XY: flagy + vstupní body (p1 = origin degenerace; p2 = origin + dir*50)
                pud.isDegenerate      = true
                pud.degenerateDir     = dirXY
                pud.isLineDegenerate  = deg.isLine
                state.conicInputPointsPudorys[pud.id]         = Triple(originXY, originXY + dirXY * 50f, Offset.Unspecified)

                // XZ: standardní vstupní body
                state.conicInputPointsNarys[nar.id] = Triple(vertex2D, focus2D, Offset.Unspecified)
                state.selectedPlaneForCircle = null
                state.selectedPlanes.clear()
                commitSnapshot(state)
                resetStavu(state)
                repeatCons(state)
                setProjectionPhase("narys_start", state)
                state.inputName = ""
                println("✅ Parabola: XY = degenerace (origin=nejzazší bod), XZ = extrakce z rovnice.")
                return
            }


            else {
                // 🔁 Obecný případ – nech stávající chování:
                val (vP, fP) = extractVertexAndFocusFromConic(
                        a = coeffsPudorys[0], b = coeffsPudorys[1], c = coeffsPudorys[2],
                        d = coeffsPudorys[3], e = coeffsPudorys[4], f = coeffsPudorys[5]
                    ) ?: run {
                        println("❌ Nepodařilo se najít vrchol a ohnisko v půdorysu.")
                        return
                    }

                extractVertexAndFocusFromConic(
                        a = coeffsNarys[0], b = coeffsNarys[1], c = coeffsNarys[2],
                        d = coeffsNarys[3], e = coeffsNarys[4], f = coeffsNarys[5]
                    ) ?: run {
                        println("❌ Nepodařilo se najít vrchol a ohnisko v nárysu.")
                        return
                    }

                vertexP = vP; focusP = fP
                vertexN = vertex2D; focusN = focus2D


                state.conics3D.add(conic3D)
                state.conicsPudorys.add(pudorys)
                state.conicsNarys.add(narys)
                state.conicInputPointsPudorys[pudorys.id] = Triple(vertexP, focusP, Offset.Unspecified)
                state.conicInputPointsNarys  [narys.id  ] = Triple(vertexN, focusN, Offset.Unspecified)
                state.selectedPlanes.clear()
                commitSnapshot(state)
                resetStavu(state)
                repeatCons(state)
                setProjectionPhase("narys_start", state)
                state.inputName = ""
                println("✅ Parabola v rovině ${plane.name} vytvořena jako ${conic3D.rawName}")
            }
            }
    }
}
fun handleParabolaInPlaneConstructionPudorys(
    logical: Offset,
    state: MongeState
) {
    // 1) Výběr roviny (stejně jako dřív)
    if (state.selectedPlaneForCircle == null) {
        val rememberedPlane = state.selectedPlanes.firstOrNull()
        if (rememberedPlane != null) {
            val eq = rememberedPlane.equation ?: run {
                println("❌ Vybraná rovina nemá rovnici – nelze zkontrolovat orientaci.")
                return
            }
            val normal = Offset3D(eq.a, eq.b, eq.c).normalize()
            val isPerpToPudorys = kotlin.math.abs(normal.z) < 1e-3f // c≈0
            if (isPerpToPudorys) {
                println("ℹ️ Pozn.: Rovina je (téměř) kolmá k půdorysně – tady nás to netrápí, klikáme v XY.")
            }
            state.selectedPlaneForCircle = rememberedPlane
            println("🟦 Rovina '${rememberedPlane.name}' vybrána pro konstrukci paraboly (půdorys).")
        } else {
            println("⚠️ Neoznačena žádná rovina – vyber jednu kliknutím.")
            return
        }
    }

    val plane = state.selectedPlaneForCircle!!
    val eq = plane.equation ?: run {
        println("❌ Rovina nemá rovnici!")
        return
    }

    val EPS = 1e-6f
    val isPerpToNarys = kotlin.math.abs(eq.b) < EPS     // ⟂ NÁRYS (b≈0) → speciál: degenerace v XZ
    if (kotlin.math.abs(eq.c) < EPS) {                  // z (x,y) nedopočítám z
        println("❌ V půdorysu nelze konstruovat – |c|≈0 (z (x,y) nedopočítám z).")
        return
    }

    when (state.projectionPhase) {
        "pudorys_start" -> setProjectionPhase("pudorys_vertex", state)

        "pudorys_vertex" -> {
            state.pendingPoint1 = logical   // vrchol v XY
            setProjectionPhase("pudorys_focus", state)
        }

        "pudorys_focus" -> {
            val vertex2D = state.pendingPoint1 ?: return
            val focus2D  = logical
            if ((focus2D - vertex2D).getDistance() < 1e-6f) {
                println("⚠️ Vrchol a ohnisko paraboly splývají.")
                return
            }

            val activePudorysConic = computeParabolaProjectionFromVertexAndFocus(vertex2D, focus2D)
            val rawName = state.inputName.ifBlank { "π" }
            val sourcePudorys = ConicSectionPudorys(
                a = activePudorysConic.a, b = activePudorysConic.b, c = activePudorysConic.c,
                d = activePudorysConic.d, e = activePudorysConic.e, f = activePudorysConic.f,
                rawName = rawName,
                localColor = state.currentLineStyleSettings.color,
                strokeWidth = state.currentLineStyleSettings.strokeWidth,
                lineStyle = state.currentLineStyleSettings.style
            )

            // 2) Zvedneme aktivní půdorysnou parabolu do roviny.
            val conic3D = canonizeParabolaFrame(
                liftConicToPlaneFromPudorys(sourcePudorys, plane) ?: run {
                    println("❌ Zvednutí paraboly z půdorysu do roviny selhalo.")
                    return
                }
            ).copy(creationIndex = allocIndex(state))
            val vertexFocus3D = vertexFocus3DFromLocalConic(conic3D)

            // 3) Koeficienty druhého průmětu
            val coeffsXZ = Matrix3x3.toCoefficients(conic3D.projectToXZ())

            // 4) 2D Conic objekty
            val pud = ConicSectionPudorys(
                a = activePudorysConic.a, b = activePudorysConic.b, c = activePudorysConic.c,
                d = activePudorysConic.d, e = activePudorysConic.e, f = activePudorysConic.f,
                rawName = conic3D.rawName, localColor = conic3D.color,
                strokeWidth = conic3D.strokeWidth, lineStyle = conic3D.lineStyle,
                parent = conic3D, creationIndex = allocIndex(state)
            )
            val nar = ConicSectionNarys(
                a = coeffsXZ[0], b = coeffsXZ[1], c = coeffsXZ[2],
                d = coeffsXZ[3], e = coeffsXZ[4], f = coeffsXZ[5],
                rawName = conic3D.rawName, localColor = conic3D.color,
                strokeWidth = conic3D.strokeWidth, lineStyle = conic3D.lineStyle,
                parent = conic3D, creationIndex = allocIndex(state)
            )

            // 5) Vstupní body
            //    XY: používáme přímo zadaný vrchol a ohnisko výsledného půdorysu.
            val vP = vertex2D
            val fP = focus2D

            if (isPerpToNarys) {
                // ⟂ nárys → XZ degenerace (přímka/polopřímka) — úplně stejně jako NÁRYS verze dělá pro XY
                val (vertex3D, focus3D) = vertexFocus3D ?: run {
                    println("❌ Nepodařilo se vypočítat 3D vrchol/ohnisko paraboly.")
                    return
                }
                val deg = computeXZDegeneracyFromVF(eq, vertex3D, focus3D)
                val originXZ = deg.origin
                val dirXZ    = deg.dir

                // 6) Uložení do stavu (DEGENERACE) + early return — symetricky k nárysové verzi

                state.conics3D.add(conic3D)
                state.conicsPudorys.add(pud)
                state.conicsNarys.add(nar)

                // XY: normální vstupy
                state.conicInputPointsPudorys[pud.id] = Triple(vP, fP, Offset.Unspecified)

                // XZ: DEGENERACE — p1 = origin (nejzazší bod), p2 = origin + dir*50
                nar.isDegenerate      = true
                nar.degenerateDir     = dirXZ
                nar.isLineDegenerate  = deg.isLine
                state.conicInputPointsNarys[nar.id]         = Triple(originXZ, originXZ + dirXZ * 50f, Offset.Unspecified)

                state.selectedPlaneForCircle = null
                state.selectedPlanes.clear()
                commitSnapshot(state)
                resetStavu(state)
                repeatCons(state)
                setProjectionPhase("pudorys_start", state)
                state.inputName = ""

                val typ = if (deg.isLine) "přímka" else "polopřímka"
                println("✅ Parabola: XY = standard, XZ = degenerace ($typ; origin = nejzazší bod).")
                return
            }

            // 🔁 Obecný případ – jako dřív (žádná degenerace v XZ)
            val (vN, fN) = extractVertexAndFocusFromConic(
                    a = coeffsXZ[0], b = coeffsXZ[1], c = coeffsXZ[2],
                    d = coeffsXZ[3], e = coeffsXZ[4], f = coeffsXZ[5]
                ) ?: run {
                    println("❌ Nepodařilo se najít vrchol a ohnisko v nárysu.")
                    return
                }

            // 6) Uložení do stavu (STANDARD)
            state.conics3D.add(conic3D)
            state.conicsPudorys.add(pud)
            state.conicsNarys.add(nar)

            state.conicInputPointsPudorys[pud.id] = Triple(vP, fP, Offset.Unspecified)
            state.conicInputPointsNarys[nar.id]   = Triple(vN, fN, Offset.Unspecified)

            // parabola v rovině je kompletní → commit (undo krok) až teď
            commitSnapshot(state)

            state.selectedPlaneForCircle = null
            state.selectedPlanes.clear()
            resetStavu(state)
            repeatCons(state)
            setProjectionPhase("pudorys_start", state)
            state.inputName = ""
            println("✅ Parabola v rovině ${plane.name} vytvořena jako ${conic3D.rawName}")
        }
    }
}


fun computeParabolaInPlaneFromVertexAndFocus(
    plane: Plane3D,
    vertex: Offset3D,
    focus: Offset3D,
    rawName: String = "",
    color: Color = Color.Black,
    strokeWidth: Float = 1f,
    style: LineStyle = LineStyle.Solid,
    index: Long
): ConicSection3D {
    val eq = plane.equation ?: error("Plane has no equation")
    val normal = Offset3D(eq.a, eq.b, eq.c).normalize()

    val v = (focus - vertex).normalize()       // osa k ohnisku
    var u = (normal cross v).normalize()       // kolmá v rovině
    // pravotočivost: u × v = normal
    if (((u cross v) dot normal) < 0f) u = u * -1f

    // lokální 2D (vertex jako počátek)
    fun toLocal(p: Offset3D): Offset {
        val d = p - vertex
        return Offset(d.dot(u), d.dot(v))
    }
    val vertex2D = Offset(0f, 0f)
    val focus2D  = toLocal(focus)

    val conic2D = computeParabolaFromVertexAndFocus3D(vertex2D, focus2D)
    val matrix = Matrix3x3.fromCoefficients(
        conic2D.a, conic2D.b, conic2D.c,
        conic2D.d, conic2D.e, conic2D.f
    )

    return ConicSection3D(
        p0 = vertex, u = u, v = v, matrix = matrix,
        rawName = rawName, color = color, strokeWidth = strokeWidth, lineStyle = style, creationIndex = index
    )
}

fun computeParabolaFromVertexAndFocus3D(vertex: Offset, focus: Offset): ConicSectionPudorys {
    val dx = focus.x - vertex.x
    val dy = focus.y - vertex.y
    val axis = Offset(dx, dy).normalize()

    // Vyjádříme ohnisko ve souřadnicích osy paraboly (u = ortho, v = osa)
    val f = (focus - vertex).dot(axis) // vzdálenost ohniska podél osy

    // Kvadratická forma paraboly: y = x² / (4f)
    val a = 1f / (4f * f)

    // Matice:
    // A = a, B = 0, C = 0, D = 0, E = -1, F = 0   →  ax² - y = 0
    return ConicSectionPudorys(
        a = a,
        b = 0f,
        c = 0f,
        d = 0f,
        e = -1f,
        f = 0f
    )
}
fun extractVertexAndFocusFromConic(
    a: Float, b: Float, c: Float,
    d: Float, e: Float, f: Float
): Pair<Offset, Offset>? {
    val EPS = 1e-8f
    if (!a.isFinite() || !b.isFinite() || !c.isFinite() || !d.isFinite() || !e.isFinite() || !f.isFinite()) {
        return null
    }
    val scale = maxOf(abs(a), abs(b), abs(c), abs(d), abs(e), abs(f))
    if (!scale.isFinite() || scale < EPS) return null

    val aa = a / scale
    val bb = b / scale
    val cc = c / scale
    val dd = d / scale
    val ee = e / scale
    val ff = f / scale

    // 1) Diagonalizace kvadratické části
    val q = Matrix2x2(aa, bb/2f, bb/2f, cc)
    val (lams, vecs) = q.eigenDecomposition()

    // Vezmi vektor s menším |λ| jako osu paraboly
    val idxAxis = if (kotlin.math.abs(lams[0]) < kotlin.math.abs(lams[1])) 0 else 1
    val axis = vecs[idxAxis].normalize()
    if (!axis.isUsable() || axis.getDistance() < EPS) return null
    val ortho = Offset(-axis.y, axis.x)

    // R = [ortho | axis]
    val r  = Matrix2x2(ortho.x, axis.x, ortho.y, axis.y)
    val rt = r.transpose()

    // 2) Transformace do lokální soustavy (x',y')
    val qp = rt * q * r            // ~ diag(λ_⊥, λ_axis≈0)
    val dp = Offset(dd, ee).transform(rt)
    val Axx = qp.m00
    val Ayy = qp.m11
    val Dx  = dp.x
    val Dy  = dp.y

    // Pomocná funkce: výpočet pro variantu "kvadratika v x'"
    fun tryQuadraticInX(): Pair<Offset, Offset>? {
        if (kotlin.math.abs(Axx) < EPS || kotlin.math.abs(Dy) < EPS) return null
        val xV = -Dx / (2f * Axx)
        val yV = -(Axx*xV*xV + Dx*xV + ff) / Dy
        val p  = -Dy / (4f * Axx)
        val vLocal = finiteOffsetOrNull(xV, yV) ?: return null
        val fLocal = finiteOffsetOrNull(xV, yV + p) ?: return null
        // zpět do původní soustavy
        val vWorld = vLocal.transform(r)
        val fWorld = fLocal.transform(r)
        if (!vWorld.isUsable() || !fWorld.isUsable()) return null
        return vWorld to fWorld
    }

    // Varianta "kvadratika v y'"
    fun tryQuadraticInY(): Pair<Offset, Offset>? {
        if (kotlin.math.abs(Ayy) < EPS || kotlin.math.abs(Dx) < EPS) return null
        val yV = -Dy / (2f * Ayy)
        val xV = -(Ayy*yV*yV + Dy*yV + ff) / Dx
        val p  = -Dx / (4f * Ayy)
        val vLocal = finiteOffsetOrNull(xV, yV) ?: return null
        val fLocal = finiteOffsetOrNull(xV + p, yV) ?: return null
        val vWorld = vLocal.transform(r)
        val fWorld = fLocal.transform(r)
        if (!vWorld.isUsable() || !fWorld.isUsable()) return null
        return vWorld to fWorld
    }

    // 3) Zkus obě – která vyjde, tu vrať
    return tryQuadraticInX() ?: tryQuadraticInY()
}

fun Offset.transform(m: Matrix2x2): Offset {
    if (!isUsable()) return Offset.Unspecified
    return Offset(
        m.m00 * this.x + m.m01 * this.y,
        m.m10 * this.x + m.m11 * this.y
    )
}

fun vertexFocus3DFromLocalConic(conic3D: ConicSection3D): Pair<Offset3D, Offset3D>? {
    val coeffs = Matrix3x3.toCoefficients(conic3D.matrix)
    val (vertex2D, focus2D) = extractVertexAndFocusFromConic(
        coeffs[0], coeffs[1], coeffs[2],
        coeffs[3], coeffs[4], coeffs[5]
    ) ?: return null

    fun to3D(p: Offset): Offset3D =
        conic3D.p0 + conic3D.u * p.x + conic3D.v * p.y

    return to3D(vertex2D) to to3D(focus2D)
}

private fun vertexFocus3DForPudorysLift(
    state: MongeState,
    conic: ConicSectionPudorys,
    plane: Plane3D,
    conic3D: ConicSection3D
): Pair<Offset3D, Offset3D>? {
    val eq = plane.equation ?: return vertexFocus3DFromLocalConic(conic3D)
    val input = state.conicInputPointsPudorys[conic.id]
        ?: return vertexFocus3DFromLocalConic(conic3D)

    val v = liftPudorysToPlane(input.first.x, input.first.y, eq)
    val f = liftPudorysToPlane(input.second.x, input.second.y, eq)

    return if (v != null && f != null) {
        Offset3D(v.x, v.y, v.z) to Offset3D(f.x, f.y, f.z)
    } else {
        vertexFocus3DFromLocalConic(conic3D)
    }
}

private fun vertexFocus3DForNarysLift(
    state: MongeState,
    conic: ConicSectionNarys,
    plane: Plane3D,
    conic3D: ConicSection3D
): Pair<Offset3D, Offset3D>? {
    val eq = plane.equation ?: return vertexFocus3DFromLocalConic(conic3D)
    val input = state.conicInputPointsNarys[conic.id]
        ?: return vertexFocus3DFromLocalConic(conic3D)

    val v = liftNarysToPlane(input.first.x, input.first.y, eq)
    val f = liftNarysToPlane(input.second.x, input.second.y, eq)

    return if (v != null && f != null) {
        Offset3D(v.x, v.y, v.z) to Offset3D(f.x, f.y, f.z)
    } else {
        vertexFocus3DFromLocalConic(conic3D)
    }
}

operator fun Matrix2x2.times(other: Matrix2x2): Matrix2x2 {
    return Matrix2x2(
        m00 = this.m00 * other.m00 + this.m01 * other.m10,
        m01 = this.m00 * other.m01 + this.m01 * other.m11,
        m10 = this.m10 * other.m00 + this.m11 * other.m10,
        m11 = this.m10 * other.m01 + this.m11 * other.m11
    )
}



fun finalizeLiftParabolaToPlaneFromPudorys(
    state: MongeState,
    conic: ConicSectionPudorys,
    plane: Plane3D
) {
    val conic3D = canonizeParabolaFrame(liftConicToPlaneFromPudorys(conic, plane) ?: run {
        println("❌ Zvednutí paraboly do roviny selhalo.")
        return
    }).copy(creationIndex = allocIndex(state))
    val mongeLift = state.projectionMode != ProjectionMode.AXO
    val show = liftShow("pudorys", mongeLift)
    state.conics3D.add(conic3D)
    if (mongeLift) {
        // Monge: volný 2D průmět se stane půdorysem nové 3D paraboly.
        conic.parent = conic3D
        conic.parentId = conic3D.id
        conic.showInAxo = show.pud
        addParabolaProjectionsExcept(state, conic3D, state.basis, skip = "pudorys", show = show)
    } else {
        // Axo: starou 3D parabolu nech být, vytvoř novou se všemi 4 čerstvými průměty.
        addParabolaProjectionsExcept(state, conic3D, state.basis, skip = "none", show = show)
    }
    commitSnapshot(state)
    setProjectionPhase("pudorys_start", state)
    state.drawobjects = Mongeobjects.NONE
    println("✅ Parabola '${conic.rawName}' zvednuta do roviny '${plane.name}'.")
}
fun startLiftParabolaToPlaneFromPudorys(state: MongeState) {
    val conic = state.selectedConicsPudorys.firstOrNull()
    if (conic == null) {
        println("❌ Nevybral jsi žádnou kuželosečku v půdorysu.")
        return
    }

    state.liftingConicPudorys = conic

    setProjectionPhase("lift_parabola_select_plane_pudorys", state)

    println("📌 Kuželosečka '${conic.rawName}' připravena ke zvednutí.")
    println("ℹ️ Klikni na rovinu, do které ji chceš zvednout.")
}
fun liftConicToPlaneFromPudorys(
    conic: ConicSectionPudorys,
    plane: Plane3D
): ConicSection3D? {
    val eq = plane.equation ?: return null

    // Bod p0 v rovině (projekce počátku)
    val D = eq.a*eq.a + eq.b*eq.b + eq.c*eq.c
    val p0 = Offset3D(-eq.a*eq.d/D, -eq.b*eq.d/D, -eq.c*eq.d/D)

    // Normála a ortonormální báze (u, v)
    val normal = Offset3D(eq.a, eq.b, eq.c).normalize()
    val up     = if (abs(normal dot Offset3D(0f,0f,1f))<0.9f) Offset3D(0f,0f,1f) else Offset3D(1f,0f,0f)
    val u      = (up cross normal).normalize()
    val v      = (normal cross u).normalize()

    // Stejná H, jakou používá projectToXY
    val H = Matrix3x3(
        u.x, v.x, p0.x,
        u.y, v.y, p0.y,
        0f, 0f, 1f
    )

    // Půdorysná matice M2D
    val M2 = Matrix3x3.fromCoefficients(
        conic.a, conic.b, conic.c,
        conic.d, conic.e, conic.f
    )

    // Zpětná projekce: M3D = Hᵀ · M2D · H
    val matrix3D = H.transpose() * M2 * H

    return ConicSection3D(
        p0 = p0,
        u = u,
        v = v,
        matrix = matrix3D,
        rawName = conic.rawName,
        color = conic.localColor ?: Color.Black,
        strokeWidth = conic.strokeWidth,
        lineStyle = conic.lineStyle
    )
}
fun canonizeParabolaFrame(conic: ConicSection3D): ConicSection3D {
    // 1) vytáhni koeficienty v lokální rovině (ξ,η)
    val cs = Matrix3x3.toCoefficients(conic.matrix)
    val A = cs[0]; val B = cs[1]; val C = cs[2]; val D = cs[3]; val E = cs[4]; val F = cs[5]

    // 2) vrchol a ohnisko v (ξ,η)
    val vf = extractVertexAndFocusFromConic(A,B,C,D,E,F)
        ?: return conic // bezpečný fallback – nic neměň

    val V = Offset(vf.first.x, vf.first.y)   // vrchol
    val Fo = Offset(vf.second.x, vf.second.y)
    if (!V.isUsable() || !Fo.isUsable()) return conic

    // 3) osa (směr V→F) a příčný směr v rovině
    val ax2 = run {
        val dx = Fo.x - V.x; val dy = Fo.y - V.y
        if (!dx.isFinite() || !dy.isFinite()) return conic
        val len = kotlin.math.sqrt(dx*dx + dy*dy).coerceAtLeast(1e-9f)
        if (!len.isFinite()) return conic
        Offset(dx/len, dy/len)
    }
    // u' chceme kolmo na osu (pro tvoji parametrizaci x=t po u', y=t²/(4p) po v')
    val pr2 = Offset(-ax2.y, ax2.x) // 90° rotace v rovině

    // 4) posuň p0 do vrcholu, přepočti bázi (u', v')
    val p0p = conic.p0 + conic.u * V.x + conic.v * V.y
    var up  = conic.u * pr2.x + conic.v * pr2.y   // u' = příčný směr
    val vp  = conic.u * ax2.x + conic.v * ax2.y   // v' = směr osy

    // zachovej pravotočivost: u' × v' ~ normála (u×v)
    val n  = (conic.u cross conic.v)
    if (((up cross vp) dot n) < 0f) up = up * -1f

    // 5) matici přepiš do vrcholového původu a zarovnej osy
    // T: translate o -V (přesunutí vrcholu do (0,0))
    val T = Matrix3x3(
        1f, 0f, V.x,
        0f, 1f, V.y,
        0f, 0f, 1f
    )
    val Mt = T.transpose() * conic.matrix * T

    // R: rotate tak, aby x' = pr2, y' = ax2
    val R = Matrix3x3(
        pr2.x, ax2.x, 0f,
        pr2.y, ax2.y, 0f,
        0f, 0f, 1f
    )
    var Mcanon = R.transpose() * Mt * R

    // 6) normalizuj škálu, aby koeficient u y byl -1 (kanonická forma y - x^2/(4p) = 0)
    val coeffAfter = Matrix3x3.toCoefficients(Mcanon)
    val E2 = coeffAfter[4]
    if (abs(E2) > 1e-9f) {
        val s = -1f / E2
        Mcanon = Mcanon * s
    }

    return ConicSection3D(
        p0 = p0p,
        u = up,
        v = vp,
        matrix = Mcanon,       // teď má B≈0, vrchol v (0,0), E≈-1
        rawName = conic.rawName,
        color = conic.color,
        strokeWidth = conic.strokeWidth,
        lineStyle = conic.lineStyle,
        a = conic.a,
        b = conic.b
    )
}
operator fun Matrix3x3.times(k: Float): Matrix3x3 = Matrix3x3(
    m00 * k, m01 * k, m02 * k,
    m10 * k, m11 * k, m12 * k,
    m20 * k, m21 * k, m22 * k
)
operator fun Matrix3x3.times(other: Matrix3x3): Matrix3x3 {
    return Matrix3x3(
        m00 * other.m00 + m01 * other.m10 + m02 * other.m20,
        m00 * other.m01 + m01 * other.m11 + m02 * other.m21,
        m00 * other.m02 + m01 * other.m12 + m02 * other.m22,

        m10 * other.m00 + m11 * other.m10 + m12 * other.m20,
        m10 * other.m01 + m11 * other.m11 + m12 * other.m21,
        m10 * other.m02 + m11 * other.m12 + m12 * other.m22,

        m20 * other.m00 + m21 * other.m10 + m22 * other.m20,
        m20 * other.m01 + m21 * other.m11 + m22 * other.m21,
        m20 * other.m02 + m21 * other.m12 + m22 * other.m22
    )
}

operator fun Float.times(M: Matrix3x3): Matrix3x3 = M * this
fun finalizeLiftParabolaToPlaneFromNarys(
    state: MongeState,
    conic: ConicSectionNarys,
    plane: Plane3D
) {
    val conic3D = canonizeParabolaFrame(liftConicToPlaneFromNarys(conic, plane) ?: run {
        println("❌ Zvednutí paraboly do roviny z nárysu selhalo.")
        return
    }).copy(creationIndex = allocIndex(state))
    val mongeLift = state.projectionMode != ProjectionMode.AXO
    val show = liftShow("narys", mongeLift)
    state.conics3D.add(conic3D)
    if (mongeLift) {
        conic.parent = conic3D
        conic.parentId = conic3D.id
        conic.showInAxo = show.nar
        addParabolaProjectionsExcept(state, conic3D, state.basis, skip = "narys", show = show)
    } else {
        addParabolaProjectionsExcept(state, conic3D, state.basis, skip = "none", show = show)
    }
    commitSnapshot(state)
    setProjectionPhase("narys_start", state)
    state.drawobjects = Mongeobjects.NONE
    println("✅ Parabola '${conic.rawName}' zvednuta do roviny '${plane.name}'.")
}

fun finalizeLiftParabolaToPlaneFromBokorys(
    state: MongeState,
    conic: ConicSectionBokorys,
    plane: Plane3D
) {
    val conic3D = canonizeParabolaFrame(liftConicToPlaneFromBokorys(conic, plane) ?: run {
        println("❌ Zvednutí paraboly do roviny z bokorysu selhalo.")
        return
    }).copy(creationIndex = allocIndex(state))
    val mongeLift = state.projectionMode != ProjectionMode.AXO
    val show = liftShow("bokorys", mongeLift)
    state.conics3D.add(conic3D)
    if (mongeLift) {
        conic.parent = conic3D
        conic.parentId = conic3D.id
        conic.showInAxo = show.bok
        addParabolaProjectionsExcept(state, conic3D, state.basis, skip = "bokorys", show = show)
    } else {
        addParabolaProjectionsExcept(state, conic3D, state.basis, skip = "none", show = show)
    }
    commitSnapshot(state)
    setProjectionPhase("narys_start", state)
    state.drawobjects = Mongeobjects.NONE
    println("✅ Parabola '${conic.rawName}' zvednuta z bokorysu do roviny '${plane.name}'.")
}

fun finalizeLiftParabolaToPlaneFromAxo(
    state: MongeState,
    conic: ConicSectionAxo,
    plane: Plane3D
) {
    val basis = state.basis ?: run {
        println("❌ Axo basis chybí – nelze zvednout parabolu z axo.")
        return
    }
    val conic3D = canonizeParabolaFrame(liftConicToPlaneFromAxo(conic, plane, basis) ?: run {
        println("❌ Zvednutí paraboly do roviny z axo selhalo.")
        return
    }).copy(creationIndex = allocIndex(state))
    val mongeLift = state.projectionMode != ProjectionMode.AXO
    val show = liftShow("axo", mongeLift)
    state.conics3D.add(conic3D)
    if (mongeLift) {
        conic.parent = conic3D
        conic.parentId = conic3D.id
        conic.showInAxo = show.axo
        addParabolaProjectionsExcept(state, conic3D, basis, skip = "axo", show = show)
    } else {
        addParabolaProjectionsExcept(state, conic3D, basis, skip = "none", show = show)
    }
    commitSnapshot(state)
    setProjectionPhase("narys_start", state)
    state.drawobjects = Mongeobjects.NONE
    println("✅ Parabola '${conic.rawName}' zvednuta z axo do roviny '${plane.name}'.")
}

/** Promítne 3D bod do nativních 2D souřadnic daného průmětu. */
private fun parabolaNativeProject(p: Offset3D, view: String, basis: AxoRenderBasis?): Offset =
    when (view) {
        "pudorys" -> Offset(p.x, p.y)
        "narys"   -> Offset(p.x, p.z)
        "bokorys" -> Offset(p.y, p.z)
        "axo"     -> basis?.let { projectPoint3DToAxoLocal(p, it) } ?: Offset.Zero
        else      -> Offset.Zero
    }

/**
 * Vytvoří všechny průměty paraboly kromě [skip] (zdroj). Pro každý průmět spočítá vrchol/ohnisko
 * z projikované matice; při degeneraci (projekce do přímky) promítne 3D vrchol/ohnisko a uloží
 * degenerovanou úsečku. showInAxo dle [show].
 */
private fun addParabolaProjectionsExcept(
    state: MongeState,
    conic3D: ConicSection3D,
    basis: AxoRenderBasis?,
    skip: String,
    show: LiftShow
) {
    val vf3 = vertexFocus3DFromLocalConic(conic3D)

    fun degInput(view: String): Triple<Offset, Offset, Offset> {
        if (vf3 == null) return Triple(Offset.Zero, Offset.Zero, Offset.Unspecified)
        val vp = parabolaNativeProject(vf3.first, view, basis)
        val fp = parabolaNativeProject(vf3.second, view, basis)
        val dxv = fp.x - vp.x; val dyv = fp.y - vp.y
        val L = sqrt(dxv * dxv + dyv * dyv)
        val dir = if (L < 1e-6f) Offset(1f, 0f) else Offset(dxv / L, dyv / L)
        return Triple(vp, Offset(vp.x + dir.x * 50f, vp.y + dir.y * 50f), Offset.Unspecified)
    }

    if (skip != "pudorys") {
        val cf = Matrix3x3.toCoefficients(conic3D.projectToXY())
        val c2 = ConicSectionPudorys(
            a = cf[0], b = cf[1], c = cf[2], d = cf[3], e = cf[4], f = cf[5],
            rawName = conic3D.rawName, localColor = conic3D.color, strokeWidth = conic3D.strokeWidth,
            lineStyle = conic3D.lineStyle, parent = conic3D, creationIndex = allocIndex(state),
            showInAxoInitial = show.pud
        )
        val vf = extractVertexAndFocusFromConic(cf[0], cf[1], cf[2], cf[3], cf[4], cf[5])
        state.conicsPudorys.add(c2)
        if (vf != null) {
            state.conicInputPointsPudorys[c2.id] = Triple(vf.first, vf.second, Offset.Unspecified)
        } else {
            val di = degInput("pudorys")
            c2.isDegenerate = true; c2.degenerateDir = Offset(di.second.x - di.first.x, di.second.y - di.first.y)
            state.conicInputPointsPudorys[c2.id] = di
        }
    }
    if (skip != "narys") {
        val cf = Matrix3x3.toCoefficients(conic3D.projectToXZ())
        val c2 = ConicSectionNarys(
            a = cf[0], b = cf[1], c = cf[2], d = cf[3], e = cf[4], f = cf[5],
            rawName = conic3D.rawName, localColor = conic3D.color, strokeWidth = conic3D.strokeWidth,
            lineStyle = conic3D.lineStyle, parent = conic3D, creationIndex = allocIndex(state),
            showInAxoInitial = show.nar
        )
        val vf = extractVertexAndFocusFromConic(cf[0], cf[1], cf[2], cf[3], cf[4], cf[5])
        state.conicsNarys.add(c2)
        if (vf != null) {
            state.conicInputPointsNarys[c2.id] = Triple(vf.first, vf.second, Offset.Unspecified)
        } else {
            val di = degInput("narys")
            c2.isDegenerate = true; c2.degenerateDir = Offset(di.second.x - di.first.x, di.second.y - di.first.y)
            state.conicInputPointsNarys[c2.id] = di
        }
    }
    if (skip != "bokorys") {
        val cf = Matrix3x3.toCoefficients(conic3D.projectToYZ())
        val c2 = ConicSectionBokorys(
            a = cf[0], b = cf[1], c = cf[2], d = cf[3], e = cf[4], f = cf[5],
            rawName = conic3D.rawName, localColor = conic3D.color, strokeWidth = conic3D.strokeWidth,
            lineStyle = conic3D.lineStyle, parent = conic3D, creationIndex = allocIndex(state),
            showInAxoInitial = show.bok
        )
        val vf = extractVertexAndFocusFromConic(cf[0], cf[1], cf[2], cf[3], cf[4], cf[5])
        state.conicsBokorys.add(c2)
        if (vf != null) {
            state.conicInputPointsBokorys[c2.id] = Triple(vf.first, vf.second, Offset.Unspecified)
        } else {
            val di = degInput("bokorys")
            c2.isDegenerate = true; c2.degenerateDir = Offset(di.second.x - di.first.x, di.second.y - di.first.y)
            state.conicInputPointsBokorys[c2.id] = di
        }
    }
    if (skip != "axo" && basis != null) {
        val cf = Matrix3x3.toCoefficients(conic3D.projectToAxo(basis))
        val c2 = ConicSectionAxo(
            a = cf[0], b = cf[1], c = cf[2], d = cf[3], e = cf[4], f = cf[5],
            rawName = conic3D.rawName, localColor = conic3D.color, strokeWidth = conic3D.strokeWidth,
            lineStyle = conic3D.lineStyle, parent = conic3D, creationIndex = allocIndex(state),
            showInAxoInitial = show.axo
        )
        val vf = extractVertexAndFocusFromConic(cf[0], cf[1], cf[2], cf[3], cf[4], cf[5])
        state.conicsAxo.add(c2)
        if (vf != null) {
            state.conicInputPointsAxo[c2.id] = Triple(vf.first, vf.second, Offset.Unspecified)
        } else {
            val di = degInput("axo")
            c2.isDegenerate = true; c2.degenerateDir = Offset(di.second.x - di.first.x, di.second.y - di.first.y)
            state.conicInputPointsAxo[c2.id] = di
        }
    }
}

/** Zpětná projekce bokorysné paraboly do roviny (analogie liftConicToPlaneFromNarys pro YZ). */
fun liftConicToPlaneFromBokorys(
    conic: ConicSectionBokorys,
    plane: Plane3D
): ConicSection3D? {
    val eq = plane.equation ?: return null
    val D = eq.a * eq.a + eq.b * eq.b + eq.c * eq.c
    val p0 = Offset3D(-eq.a * eq.d / D, -eq.b * eq.d / D, -eq.c * eq.d / D)
    val normal = Offset3D(eq.a, eq.b, eq.c).normalize()
    val up = if (abs(normal dot Offset3D(0f, 0f, 1f)) < 0.9f) Offset3D(0f, 0f, 1f) else Offset3D(1f, 0f, 0f)
    val u = (up cross normal).normalize()
    val v = (normal cross u).normalize()
    val Hyz = Matrix3x3(
        u.y, v.y, p0.y,
        u.z, v.z, p0.z,
        0f, 0f, 1f
    )
    val M2 = Matrix3x3.fromCoefficients(conic.a, conic.b, conic.c, conic.d, conic.e, conic.f)
    val matrix3D = Hyz.transpose() * M2 * Hyz
    return ConicSection3D(
        p0 = p0, u = u, v = v, matrix = matrix3D,
        rawName = conic.rawName, color = conic.localColor ?: Color.Black,
        strokeWidth = conic.strokeWidth, lineStyle = conic.lineStyle
    )
}

/** Zpětná projekce axonometrické paraboly do roviny (analogie projectToAxo, H z axo báze). */
fun liftConicToPlaneFromAxo(
    conic: ConicSectionAxo,
    plane: Plane3D,
    basis: AxoRenderBasis
): ConicSection3D? {
    val eq = plane.equation ?: return null
    val D = eq.a * eq.a + eq.b * eq.b + eq.c * eq.c
    val p0 = Offset3D(-eq.a * eq.d / D, -eq.b * eq.d / D, -eq.c * eq.d / D)
    val normal = Offset3D(eq.a, eq.b, eq.c).normalize()
    val up = if (abs(normal dot Offset3D(0f, 0f, 1f)) < 0.9f) Offset3D(0f, 0f, 1f) else Offset3D(1f, 0f, 0f)
    val u = (up cross normal).normalize()
    val v = (normal cross u).normalize()
    val p = projectPoint3DToAxoLocal(p0, basis)
    val u2 = projectVector3DToAxoLocal(u, basis)
    val v2 = projectVector3DToAxoLocal(v, basis)
    val H = Matrix3x3(
        u2.x, v2.x, p.x,
        u2.y, v2.y, p.y,
        0f, 0f, 1f
    )
    val M2 = Matrix3x3.fromCoefficients(conic.a, conic.b, conic.c, conic.d, conic.e, conic.f)
    val matrix3D = H.transpose() * M2 * H
    return ConicSection3D(
        p0 = p0, u = u, v = v, matrix = matrix3D,
        rawName = conic.rawName, color = conic.localColor ?: Color.Black,
        strokeWidth = conic.strokeWidth, lineStyle = conic.lineStyle
    )
}
fun liftConicToPlaneFromNarys(
    conic: ConicSectionNarys,
    plane: Plane3D
): ConicSection3D? {
    val eq = plane.equation ?: return null

    // Bod p0 v rovině (ortogonální projekce počátku na rovinu ax+by+cz+d = 0)
    val D = eq.a*eq.a + eq.b*eq.b + eq.c*eq.c
    val p0 = Offset3D(-eq.a*eq.d/D, -eq.b*eq.d/D, -eq.c*eq.d/D)

    // Normála a ortonormální báze (u, v) ležící v rovině
    val normal = Offset3D(eq.a, eq.b, eq.c).normalize()
    val up     = if (abs(normal dot Offset3D(0f,0f,1f)) < 0.9f)
        Offset3D(0f,0f,1f) else Offset3D(1f,0f,0f)
    val u      = (up cross normal).normalize()
    val v      = (normal cross u).normalize()

    // Stejná H_xz, jakou používá projectToXZ (bere složky x a z)
    val Hxz = Matrix3x3(
        u.x, v.x, p0.x,
        u.z, v.z, p0.z,
        0f, 0f, 1f
    )

    // Nárysná 2D matice kuželosečky
    val M2 = Matrix3x3.fromCoefficients(
        conic.a, conic.b, conic.c,
        conic.d, conic.e, conic.f
    )

    // Zpětná projekce do 3D: M3D = H_xzᵀ · M2D · H_xz
    val matrix3D = Hxz.transpose() * M2 * Hxz

    return ConicSection3D(
        p0 = p0,
        u = u,
        v = v,
        matrix = matrix3D,
        rawName = conic.rawName,
        color = conic.localColor ?: Color.Black,
        strokeWidth = conic.strokeWidth,
        lineStyle = conic.lineStyle
    )
}
fun startLiftParabolaToPlaneFromNarys(state: MongeState) {
    val conic = state.selectedConicsNarys.firstOrNull()
    if (conic == null) {
        println("❌ Nevybral jsi žádnou kuželosečku v půdorysu.")
        return
    }

    state.liftingConicNarys = conic

    setProjectionPhase("lift_parabola_select_plane_narys", state)

    println("📌 Kuželosečka '${conic.rawName}' připravena ke zvednutí.")
    println("ℹ️ Klikni na rovinu, do které ji chceš zvednout.")
}
fun startLiftParabolaToPlaneFromBokorys(state: MongeState) {
    val conic = state.selectedConicsBokorys.firstOrNull()
    if (conic == null) {
        println("❌ Nevybral jsi žádnou kuželosečku v bokorysu.")
        return
    }

    state.liftingConicBokorys = conic
    setProjectionPhase("lift_parabola_select_plane_bokorys", state)

    println("📌 Kuželosečka '${conic.rawName}' připravena ke zvednutí.")
    println("ℹ️ Klikni na rovinu, do které ji chceš zvednout.")
}
