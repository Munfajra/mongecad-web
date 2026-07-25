@file:Suppress("EXTENSION_SHADOWED_BY_MEMBER")

package monge.input.planeobjects.conicsections

import androidx.compose.ui.geometry.Offset
import model.ConicCoeffs

import serialization.commitSnapshot
import model.Mongeobjects
import model.Offset3D
import model.classes.*
import model.normalize
import monge.input.conixections.computeConicFromParametricHyperbola
import state.MongeState
import ui.mongeui.toolbar.repeatCons
import ui.mongeui.toolbar.setProjectionPhase
import ui.resetStavu
import utils.allocIndex
import kotlin.math.*


fun handleHyperbolaInPlaneConstructionPudorys(logical: Offset, state: MongeState) {
    if (state.selectedPlaneForCircle == null)  {
            val rememberedPlane = state.selectedPlanes.firstOrNull()

            if (rememberedPlane != null) {
                val eq = rememberedPlane.equation
                if (eq == null) {
                    println("❌ Vybraná rovina nemá rovnici – nelze zkontrolovat orientaci.")
                    return
                }
                val normal = Offset3D(eq.a, eq.b, eq.c).normalize()
                val isVerticalInPudorys = abs(normal.z) < 1e-3f

                if (isVerticalInPudorys) {
                    println("❌ Nelze konstruovat elipsu v půdorysu – rovina '${rememberedPlane.name}' je kolmá k půdorysně.")
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

    when (state.projectionPhase) {
        "pudorys_start" -> {
            setProjectionPhase("pudorys_asymptote1", state)
            println("ℹ️ Klikněte na první asymptotu (přímku) v půdorysu.")
        }

        "pudorys_asymptote1" -> {
            val line1 = state.selectedLinesPudorys.firstOrNull()
            if (line1 == null) {
                println("⚠️ Neoznačena žádná přímka – vyber asymptotu.")
                return
            }
            state.selectedLineForParallelPudorys = line1
            println("🟦 První asymptota '${line1.name}' vybrána.")
            setProjectionPhase("pudorys_asymptote2", state)
            println("ℹ️ Klikněte na druhou asymptotu.")
        }

        "pudorys_asymptote2" -> {
            val line2 = state.selectedLinesPudorys.firstOrNull()
            if (line2 == null || line2 === state.selectedLineForParallelPudorys) {
                println("⚠️ Vyber jinou přímku než tu první.")
                return
            }
            state.selectedLineForParallelPudorysSecond = line2
            state.selectedLinesPudorys.clear()
            println("🟦 Druhá asymptota '${line2.name}' vybrána.")
            setProjectionPhase("pudorys_vertex", state)
            println("ℹ️ Klikněte na vrchol hyperboly.")
        }

        "pudorys_vertex" -> {
            val line1 = state.selectedLineForParallelPudorys ?: return
            val line2 = state.selectedLineForParallelPudorysSecond ?: return

            val vertex2D = logical

            val conic = constructHyperbola3DFromAsymptotesAndVertex(line1, line2, vertex2D, plane, state)
            if (conic == null) {
                println("❌ Konstrukce hyperboly se nezdařila.")
                return
            }

            // Projekční koeficienty (obecný případ)
            val coeffsP = Matrix3x3.toCoefficients(conic.projectToXY())
            val coeffsN = Matrix3x3.toCoefficients(conic.projectToXZ())

            val pudorys = ConicSectionPudorys(
                a = coeffsP[0], b = coeffsP[1], c = coeffsP[2],
                d = coeffsP[3], e = coeffsP[4], f = coeffsP[5],
                rawName = conic.rawName,
                localColor = conic.color,
                strokeWidth = conic.strokeWidth,
                lineStyle = conic.lineStyle,
                parent = conic, creationIndex = allocIndex(state)
            )

            val narys = ConicSectionNarys(
                a = coeffsN[0], b = coeffsN[1], c = coeffsN[2],
                d = coeffsN[3], e = coeffsN[4], f = coeffsN[5],
                rawName = conic.rawName,
                localColor = conic.color,
                strokeWidth = conic.strokeWidth,
                lineStyle = conic.lineStyle,
                parent = conic, creationIndex = allocIndex(state)
            )

            // --- připrav si 3D data asymptot a jejich XZ projekci (budeme to potřebovat i pro degeneraci) ---
            val pt3D1 = liftXYtoPlane(line1.point.x, line1.point.y, eq)
            val pt3D2 = liftXYtoPlane(line2.point.x, line2.point.y, eq)

            val dir3D1 = liftXYtoPlane(
                line1.point.x + line1.direction.x,
                line1.point.y + line1.direction.y,
                eq
            ) - pt3D1

            val dir3D2 = liftXYtoPlane(
                line2.point.x + line2.direction.x,
                line2.point.y + line2.direction.y,
                eq
            ) - pt3D2

            val line1N = Line3DProjectionNarys(
                point = Point3DNarys(pt3D1.x, pt3D1.z),
                direction = Offset(dir3D1.x, dir3D1.z).normalize()   // ⬅️ normalize
            )
            val line2N = Line3DProjectionNarys(
                point = Point3DNarys(pt3D2.x, pt3D2.z),
                direction = Offset(dir3D2.x, dir3D2.z).normalize()   // ⬅️ normalize
            )


            // --- vrchol + osa z PŮDORYSU (spolehlivé i v degeneraci) ---
            val (vertexP, axisP) = extractVertexAndAxisFromPudorys(pudorys, eq)

            // ------------------------------------------------------------
            // DETEKCE DEGENERACE V NÁRYSU: rovina kolmá k nárysně ⇒ |b| ≈ 0
            // ------------------------------------------------------------
            val EPS = 1e-6f
            val nrmUnit = Offset3D(eq.a, eq.b, eq.c).normalize()
            val isDegenerateNarys = abs(nrmUnit.y) < 1e-6f
            val (vertexNraw, axisN) = if (!isDegenerateNarys) {
                extractVertexAndAxisFromNarys(narys, eq)
            } else {
                val v3D = liftXYtoPlane(vertex2D.x, vertex2D.y, eq)
                val vN = Offset(v3D.x, v3D.z)   // <--- rovnou Offset pro nárys
                val p0 = liftXYtoPlane(vertexP.x, vertexP.y, eq)
                val p1 = liftXYtoPlane(vertexP.x + axisP.x, vertexP.y + axisP.y, eq)
                val axisNdir = Offset(p1.x - p0.x, p1.z - p0.z)
                Pair(vN, axisNdir)
            }

            // --- Ulož vstupy (půdorys + nárys) ---
            state.hyperbolaInputsPudorys[pudorys.id] = ConicInputHyperbolaPudorys(
                vertex = vertexP,
                axis = axisP,
                line1 = line1,
                line2 = line2
            )

            // Pozn.: u nárysu u degenerace do 'axis' uložíme směr osy (nebo bisektoru),
            // kreslení ale stejně můžeš vzít přímo z line1N/line2N.
            state.hyperbolaInputsNarys[narys.id] = ConicInputHyperbolaNarys(
                vertex = Offset(vertexNraw.x, -vertexNraw.y),
                axis = Offset(axisN.x, -axisN.y),
                line1 = line1N,
                line2 = line2N
            )

            // ---------------------------------
            // NASTAV FLAGY DEGENERACE V NÁRYSU
            // ---------------------------------
            if (isDegenerateNarys) {
                narys.isDegenerate = true

                // Zjisti, zda dvě XZ-projekce asymptot splývají ⇒ jedna přímka
                fun norm(v: Offset): Offset {
                    val len = kotlin.math.sqrt(v.x * v.x + v.y * v.y)
                    return if (len < EPS) Offset.Zero else Offset(v.x / len, v.y / len)
                }


                val d1 = line1N.direction.normalize()
                val d2 = line2N.direction.normalize()

                val dot = d1.x*d2.x + d1.y*d2.y
                val areCollinear = abs(1f - kotlin.math.abs(dot)) < 1e-3f

                if (areCollinear) {
                    narys.isLineDegenerate = true
                    val dDisp = Offset(d1.x, -d1.y)  // ⬅️ display (x,-z)
                    narys.degenerateDir = dDisp
                } else {
                    narys.isLineDegenerate = false
                    narys.degenerateDir = null
                }
            } else {
                // nedegenerovaný nárys – jistota, že flagy jsou čisté
                narys.isDegenerate = false
                narys.isLineDegenerate = false
                narys.degenerateDir = null
            }

            // --- commit do stavu ---

            state.conicsPudorys.add(pudorys)
            state.conicsNarys.add(narys)
            state.conics3D.add(conic)
            commitSnapshot(state)
            println("✅ Hyperbola byla úspěšně vytvořena." + if (isDegenerateNarys) " (nárys je degenerovaný)" else "")
            repeatCons(state)
            setProjectionPhase("pudorys_start", state)
            resetStavu(state)
        }
    }
}
fun handleHyperbolaInPlaneConstructionNarys(logical: Offset, state: MongeState) {
    // 0) Zajisti vybranou rovinu stejně jako v pudorysu
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
    val eq    = plane.equation ?: run {
        println("❌ Rovina nemá rovnici!")
        return
    }

    when(state.projectionPhase) {
        "narys_start" -> {
            setProjectionPhase("narys_asymptote1", state)
            println("ℹ️ Klikněte na první asymptotu (přímku) v nárysu.")
        }

        "narys_asymptote1" -> {
            val line1 = state.selectedLinesNarys.firstOrNull()
            if (line1 == null) {
                println("⚠️ Neoznačena žádná přímka – vyber asymptotu.")
                return
            }
            state.selectedLineForParallelNarys = line1
            println("🟦 První asymptota '${line1.name}' vybrána.")
            setProjectionPhase("narys_asymptote2", state)
            println("ℹ️ Klikněte na druhou asymptotu.")
        }

        "narys_asymptote2" -> {
            val line2 = state.selectedLinesNarys.firstOrNull()
            if (line2 == null || line2 === state.selectedLineForParallelNarys) {
                println("⚠️ Vyber jinou přímku než tu první.")
                return
            }
            state.selectedLineForParallelNarysSecond = line2
            state.selectedLinesNarys.clear()
            println("🟦 Druhá asymptota '${line2.name}' vybrána.")
            setProjectionPhase("narys_vertex", state)
            println("ℹ️ Klikněte na vrchol hyperboly (nárys).")
        }

        "narys_vertex" -> {
            // Zkontroluj, že máme obě asymptoty
            val asym1 = state.selectedLineForParallelNarys
                ?: error("První asymptota chybí!")
            val asym2 = state.selectedLineForParallelNarysSecond
                ?: error("Druhá asymptota chybí!")

            // 1) Vertex v nárysu (x,z) – z logical.y invertujeme
            val vertex2D = Offset(logical.x, -logical.y)

            // 2) Postav 3D-hyperbolu ze dvou asymptot a vrcholu
            val conic3d = constructHyperbola3DFromAsymptotesAndVertexNarys(
                asym1, asym2, vertex2D, plane, state
            )
            if (conic3d == null) {
                println("❌ Konstrukce hyperboly se nezdařila.")
                return
            }

            // 3) Souřadnice konice v obou promítáních
            val coeffsP = Matrix3x3.toCoefficients(conic3d.projectToXY())
            val coeffsN = Matrix3x3.toCoefficients(conic3d.projectToXZ())

            // 4) Vytvoř ConicSection pro oba pohledy
            val pudorys = ConicSectionPudorys(
                a = coeffsP[0], b = coeffsP[1], c = coeffsP[2],
                d = coeffsP[3], e = coeffsP[4], f = coeffsP[5],
                rawName = conic3d.rawName,
                localColor = conic3d.color,
                strokeWidth = conic3d.strokeWidth,
                lineStyle = conic3d.lineStyle,
                parent = conic3d, creationIndex = allocIndex(state)
            )
            val narys = ConicSectionNarys(
                a = coeffsN[0], b = coeffsN[1], c = coeffsN[2],
                d = coeffsN[3], e = coeffsN[4], f = coeffsN[5],
                rawName = conic3d.rawName,
                localColor = conic3d.color,
                strokeWidth = conic3d.strokeWidth,
                lineStyle = conic3d.lineStyle,
                parent = conic3d, creationIndex = allocIndex(state)
            )
            val EPS = 1e-6f
            val nrm = Offset3D(eq.a, eq.b, eq.c).normalize()
            val isDegeneratePudorys = kotlin.math.abs(nrm.z) < EPS

            if (isDegeneratePudorys) {
                pudorys.isDegenerate = true

                // směr průsečnice roviny s XY: u2 = n × e_z
                var u2 = nrm.cross(Offset3D(0f,0f,1f))
                val len2 = kotlin.math.sqrt(u2.x*u2.x + u2.y*u2.y + u2.z*u2.z).coerceAtLeast(1e-12f)
                u2 = Offset3D(u2.x/len2, u2.y/len2, u2.z/len2)

                // Pro případ „jen přímka“ si uložíme směr do XY (pudorys display)
                pudorys.degenerateDir = Offset(u2.x, u2.y) // (x,y), bez negací


                // Rychlé rozhodnutí line/gap (volitelné, ale užitečné):
                val coeffsN = ConicCoeffs(narys.a, narys.b, narys.c, narys.d, narys.e, narys.f)
                val eXZ = Offset(u2.x, u2.z).let {
                    val L = kotlin.math.sqrt(it.x*it.x + it.y*it.y).coerceAtLeast(1e-12f)
                    Offset(it.x/L, it.y/L)
                }
                val endsXZ = extremeEnds2D(coeffsN, eXZ)
                pudorys.isLineDegenerate = (endsXZ == null)
            }


            // bod a směr první asymptoty
            val p3D1 = liftXZtoPlane(asym1.point.x, asym1.point.z, eq)
            val dir3D1 = liftXZtoPlane(
                asym1.point.x + asym1.direction.x,
                asym1.point.z + asym1.direction.y,
                eq
            ) - p3D1
            // bod a směr druhé asymptoty
            val p3D2 = liftXZtoPlane(asym2.point.x, asym2.point.z, eq)
            val dir3D2 = liftXZtoPlane(
                asym2.point.x + asym2.direction.x,
                asym2.point.z + asym2.direction.y,
                eq
            ) - p3D2

            val line1P = Line3DProjectionPudorys(
                point = Point3DPudorys(p3D1.x, p3D1.y),
                direction = Offset(dir3D1.x, dir3D1.y).normalize()
            )
            val line2P = Line3DProjectionPudorys(
                point = Point3DPudorys(p3D2.x, p3D2.y),
                direction = Offset(dir3D2.x, dir3D2.y).normalize()
            )
            val line1N = Line3DProjectionNarys(
                point = Point3DNarys(p3D1.x, p3D1.z),
                direction = Offset(dir3D1.x, dir3D1.z).normalize()
            )
            val line2N = Line3DProjectionNarys(
                point = Point3DNarys(p3D2.x, p3D2.z),
                direction = Offset(dir3D2.x, dir3D2.z).normalize()
            )

            // 6) Spočti vrchol a osu pro oba pohledy
            val (vertexP, axisP) = extractVertexAndAxisFromPudorys(pudorys,eq)
            val (vertexN, axisN) = extractVertexAndAxisFromNarys(narys,eq)

            // 7) Ulož do stavu
            state.hyperbolaInputsPudorys[pudorys.id] = ConicInputHyperbolaPudorys(
                vertex = vertexP,
                axis = axisP,
                line1 = line1P,
                line2 = line2P
            )
            state.hyperbolaInputsNarys[narys.id] = ConicInputHyperbolaNarys(
                vertex = Offset(vertexN.x, -vertexN.y),
                axis = Offset(axisN.x, -axisN.y),
                line1 = line1N,
                line2 = line2N
            )

            state.conicsPudorys.add(pudorys)
            state.conicsNarys.add(narys)
            state.conics3D.add(conic3d)
            commitSnapshot(state)

            println("✅ Hyperbola byla úspěšně vytvořena (nárys).")
            repeatCons(state)
            setProjectionPhase("pudorys_start",state)
            resetStavu(state)
        }
    }
}
fun extractVertexAndAxisFromPudorys(
    pudorys: ConicSectionPudorys,
    planeEquation: PlaneEquation? // nutný navíc
): Pair<Offset, Offset> {
    val (a, b, c, d, e, f) = pudorys

    // 1) Výpočet středu
    val det = 4 * a * c - b * b
    val x0 = if (abs(det) < 1e-10f) {
        println("⚠️ Degenerace: determinant ≈ 0, použiji fallback střed (0,0)")
        0f
    } else {
        (b * e - 2 * c * d) / det
    }

    val y0 = if (abs(det) < 1e-10f) {
        0f
    } else {
        (b * d - 2 * a * e) / det
    }

    val center = Offset(x0, y0)

    // 2) Posunutý volný člen
    val Fp = a * x0 * x0 + b * x0 * y0 + c * y0 * y0 + d * x0 + e * y0 + f

    // 3) Vlastní čísla
    val trace = a + c
    val diff = a - c
    val disc = sqrt(diff * diff + b * b)
    val lambda1 = (trace + disc) / 2.0
    val lambda2 = (trace - disc) / 2.0

    // 4) Výběr směru osy
    val phi = 0.5 * atan2(b.toDouble(), (a - c).toDouble())
    val (lambda, ux, uy) = when {
        -Fp / lambda1 > 0 -> Triple(lambda1, cos(phi), sin(phi))
        -Fp / lambda2 > 0 -> Triple(lambda2, -sin(phi), cos(phi))
        else -> {
            println("⚠️ Nešlo spočítat osu – použiji půdorysnou stopu roviny.")
            if (planeEquation != null) {
                computePudorysStopa(planeEquation)?.let { (pt, dir) ->
                    return pt to dir
                }
            }
            return center to Offset(1f, 0f)
        }
    }

    // 5) Poloosa
    val aSemiSquared = -Fp / lambda
    if (aSemiSquared <= 0.0 || aSemiSquared.isNaN()) {
        println("⚠️ Poloosa nelze spočítat – fallback na půdorysnou stopu roviny.")
        if (planeEquation != null) {
            computePudorysStopa(planeEquation)?.let { (pt, dir) ->
                return pt to dir
            }
        }
        return center to Offset(1f, 0f)
    }

    val aSemi = sqrt(aSemiSquared).toFloat()
    val axis = Offset(ux.toFloat(), uy.toFloat())
    val vertex = Offset(
        center.x + axis.x * aSemi,
        center.y + axis.y * aSemi
    )
    return vertex to axis
}
fun extractVertexAndAxisFromNarys(
    narys: ConicSectionNarys,
    planeEquation: PlaneEquation? // nutný parametr navíc
): Pair<Offset, Offset> {
    val (a, b, c, d, e, f) = narys

    // 1) Střed koniky
    val det = 4 * a * c - b * b
    val x0 = if (abs(det) < 1e-10f) 0f else (b * e - 2 * c * d) / det
    val z0 = if (abs(det) < 1e-10f) 0f else (b * d - 2 * a * e) / det

    val Fp = a * x0 * x0 + b * x0 * z0 + c * z0 * z0 + d * x0 + e * z0 + f

    val trace = a + c
    val diff = a - c
    val disc = sqrt(diff * diff + b * b)
    val lambda1 = (trace + disc) / 2.0
    val lambda2 = (trace - disc) / 2.0
    val phi = 0.5 * atan2(b.toDouble(), (a - c).toDouble())

    val (lambda, uxw, uzw) = when {
        -Fp / lambda1 > 0 -> Triple(lambda1, cos(phi), sin(phi))
        -Fp / lambda2 > 0 -> Triple(lambda2, -sin(phi), cos(phi))
        else -> {
            println("⚠️ Nelze spočítat směr osy – fallback na nárysnou stopu roviny.")
            if (planeEquation != null) {
                computeNarysStopa(planeEquation)?.let { (point, direction) ->
                    return point to direction
                }
            }
            // poslední fallback: výchozí osa
            return Offset(x0, -z0) to Offset(1f, 0f)
        }
    }

    val aSemiSquared = -Fp / lambda
    if (aSemiSquared <= 0.0 || aSemiSquared.isNaN()) {
        println("⚠️ Nešlo spočítat délku poloosy – fallback na nárysnou stopu roviny.")
        if (planeEquation != null) {
            computeNarysStopa(planeEquation)?.let { (point, direction) ->
                return point to direction
            }
        }
        return Offset(x0, -z0) to Offset(1f, 0f)
    }

    val aSemi = sqrt(aSemiSquared).toFloat()
    val center = Offset(x0, -z0)
    val axis = Offset(uxw.toFloat(), -uzw.toFloat())
    val vertex = Offset(
        center.x + axis.x * aSemi,
        center.y + axis.y * aSemi
    )
    return vertex to axis
}

fun computeNarysStopa(eq: PlaneEquation): Pair<Offset, Offset>? {
    // Rovnice roviny: ax + by + cz + d = 0
    // V nárysu: y = 0 → rovnice se zjednoduší na: ax + cz + d = 0

    // Pokud a == 0 a c == 0 → rovina je rovnoběžná s nárysnou → nemá stopu
    if (abs(eq.a) < 1e-6f && abs(eq.c) < 1e-6f) return null

    // Najdeme dva body na přímce ax + cz + d = 0 v rovině XZ
    // Např. x = 0 → z₁, z = 0 → x₁
    val p1 = if (abs(eq.c) > 1e-6f) {
        val z = -eq.d / eq.c
        Offset(0f, -z)
    } else {
        val x = -eq.d / eq.a
        Offset(x, 0f)
    }

    val p2 = if (abs(eq.a) > 1e-6f) {
        val x = -eq.d / eq.a
        Offset(x, 0f)
    } else {
        val z = -eq.d / eq.c
        Offset(0f, -z)
    }

    val direction = (p2 - p1).normalize()
    return p1 to direction
}
fun computePudorysStopa(eq: PlaneEquation): Pair<Offset, Offset>? {
    // Rovnice roviny: ax + by + cz + d = 0
    // V půdorysu: z = 0 → ax + by + d = 0

    if (abs(eq.a) < 1e-6f && abs(eq.b) < 1e-6f) return null

    val p1 = if (abs(eq.b) > 1e-6f) {
        val y = -eq.d / eq.b
        Offset(0f, y)
    } else {
        val x = -eq.d / eq.a
        Offset(x, 0f)
    }

    val p2 = if (abs(eq.a) > 1e-6f) {
        val x = -eq.d / eq.a
        Offset(x, 0f)
    } else {
        val y = -eq.d / eq.b
        Offset(0f, y)
    }

    val direction = (p2 - p1).normalize()
    return p1 to direction
}

fun Offset.normalize(): Offset {
    val len = hypot(x, y)
    return if (len > 1e-6f) Offset(x/len, y/len) else Offset.Zero
}
fun Offset3D.normalize(): Offset3D {
    val len = length()
    return if (len > 1e-6f) Offset3D(x/len, y/len, z/len) else this
}
fun constructHyperbola3DFromAsymptotesAndVertex(
    line1: NamedLinePudorys,
    line2: NamedLinePudorys,
    vertex2D: Offset,
    plane: Plane3D,
    state: MongeState,
): ConicSection3D? {
    val eq = plane.equation ?: return null

    // 1️⃣ Zvednutí asymptot do roviny
    val pt3D1 = liftXYtoPlane(line1.point.x, line1.point.y, eq)
    val pt3D2 = liftXYtoPlane(line2.point.x, line2.point.y, eq)

    val dir3D1 = liftXYtoPlane(
        line1.point.x + line1.direction.x,
        line1.point.y + line1.direction.y,
        eq
    ) - pt3D1

    val dir3D2 = liftXYtoPlane(
        line2.point.x + line2.direction.x,
        line2.point.y + line2.direction.y,
        eq
    ) - pt3D2
// 0) Normála roviny
    val n = Offset3D(eq.a, eq.b, eq.c).normalize()

// 1) Lokální báze (osa u ve směru první asymptoty)
    val u = dir3D1.normalize()
    val v = n.cross(u).normalize() // ortogonální doplnění

    // 2) Lokální souřadnice obou přímek
    fun toLocal(p: Offset3D): Offset {
        val d = p - pt3D1
        return Offset(d.dot(u), d.dot(v))
    }

    val p1 = Offset(0f, 0f)                   // začátek první přímky (v místě pt3D_1)
    val d1p = Offset(1f, 0f)                   // první přímka = osa u
    val p2 = toLocal(pt3D2)                  // druhý bod v lokálních souřadnicích
    val d2p = Offset(dir3D2.dot(u), dir3D2.dot(v)) // směr druhé přímky v rovině

// 3) Průsečík dvou přímek v 2D
    val intersection2D = intersectLines2D(p1, d1p, p2, d2p) ?: return null

// 4) Transformace zpět do 3D
    val center3D = pt3D1 + u * intersection2D.x + v * intersection2D.y


    // 3️⃣ Osy hyperboly (symetrie asymptot)
    val d1 = dir3D1.normalize()
    val d2 = dir3D2.normalize()
    val axisSymmetry1 = (d1 + d2).normalize()
    val axisSymmetry2 = (d1 - d2).normalize()
    val vertex3Dinput = liftXYtoPlane(vertex2D.x, vertex2D.y, eq)
    val centerToVertex = vertex3Dinput - center3D

    val len1 = (centerToVertex - centerToVertex.projectOnto(axisSymmetry1)).length()
    val len2 = (centerToVertex - centerToVertex.projectOnto(axisSymmetry2)).length()
    val axisMain3D = if (len1 < len2) axisSymmetry1 else axisSymmetry2
    val axisOther3D = Offset3D(eq.a, eq.b, eq.c).normalize().cross(axisMain3D).normalize()
    println("📐 axisMain3D = $axisMain3D")
    println("📐 axisOther3D = $axisOther3D")
    println("📐 rovina normal = ${Offset3D(eq.a, eq.b, eq.c).normalize()}")

// 4️⃣ Zvednutí vrcholu a výpočet správného středu
    val vertex3D = liftXYtoPlane(vertex2D.x, vertex2D.y, eq)

// Vyber správně orientovanou hlavní osu (už jsi to udělal)

    val center3Dcorrected = center3D
    val asigned = (vertex3D - center3D).dot(axisMain3D)
    val a = abs(asigned)
    // 5️⃣ Výpočet b z úhlů asymptot vůči hlavní ose
    val d12D = Offset(
        dir3D1.dot(axisMain3D),
        dir3D1.dot(axisOther3D)
    ).normalize()

    val d22D = Offset(
        dir3D2.dot(axisMain3D),
        dir3D2.dot(axisOther3D)
    ).normalize()

    val slope1 = d12D.y / d12D.x
    val slope2 = d22D.y / d22D.x
    val b = a * ((abs(slope1) + abs(slope2)) / 2f)

    // 6️⃣ Matice kuželosečky v lokální bázi

    val conic = computeConicFromParametricHyperbola(
        center = Offset(0f, 0f),
        finalAxis = Offset(1f, 0f),
        otherDir = Offset(0f, 1f),
        a = a,
        b = b
    )
    return ConicSection3D(
        p0 = center3Dcorrected,
        u = axisMain3D,
        v = axisOther3D,
        color = state.currentLineStyleSettings.color,
        lineStyle = state.currentLineStyleSettings.style,
        strokeWidth = state.currentLineStyleSettings.strokeWidth,
        matrix = Matrix3x3.fromCoefficients(
            conic.a, conic.b, conic.c,
            conic.d, conic.e, conic.f
        ),
        a = a,
        b = b, creationIndex = allocIndex(state)
    )
}
fun constructHyperbola3DFromAsymptotesAndVertexNarys(
    line1: NamedLineNarys,
    line2: NamedLineNarys,
    vertex2D: Offset,       // screen‐koordináty v nárysu (X, Y_screen = -Z_world)
    plane: Plane3D,
    state: MongeState
): ConicSection3D? {
    val eq = plane.equation ?: return null

    // 1️⃣ Zvednutí asymptot do roviny pomocí (x,z) → (x, –y_screen)
    //    liftXZtoPlane bere (X_world, Z_world) a vrací 3D bod
    val pt3D1 = liftXZtoPlane(line1.point.x, line1.point.z, eq)
    val pt3D2 = liftXZtoPlane(line2.point.x, line2.point.z, eq)

    val dir3D1 = liftXZtoPlane(
        line1.point.x + line1.direction.x,
        line1.point.z + line1.direction.y,
        eq
    ) - pt3D1

    val dir3D2 = liftXZtoPlane(
        line2.point.x + line2.direction.x,
        line2.point.z + line2.direction.y,
        eq
    ) - pt3D2

    // 0) Normála roviny
    val n = Offset3D(eq.a, eq.b, eq.c).normalize()

    // 1) Lokální báze v rovině
    val u = dir3D1.normalize()            // první asymptota
    val v = n.cross(u).normalize()         // doplňuje ortogonálně

    // 2) Koordináty asymptot v lokální bázi
    fun toLocal(p: Offset3D): Offset {
        val d = p - pt3D1
        return Offset(d.dot(u), d.dot(v))
    }
    val p1  = Offset(0f, 0f)               // první asymptota začíná v (0,0)
    val d1p = Offset(1f, 0f)               // směr (osa u)
    val p2  = toLocal(pt3D2)
    val d2p = Offset(dir3D2.dot(u), dir3D2.dot(v))

    // 3) Průsečík asymptot v 2D
    val intersection2D = intersectLines2D(p1, d1p, p2, d2p)
        ?: return null

    // 4) Transformace průsečíku zpět do 3D
    val center3D = pt3D1 + u * intersection2D.x + v * intersection2D.y

    // 5️⃣ Symetrické osy hyperboly (bisektory asymptot)
    val d1 = dir3D1.normalize()
    val d2 = dir3D2.normalize()
    val axisSym1 = (d1 + d2).normalize()
    val axisSym2 = (d1 - d2).normalize()

    // 6️⃣ Určení hlavní osy podle zadaného vrcholu
    val vertex3Dinput = liftXZtoPlane(vertex2D.x, vertex2D.y, eq)
    val cv = vertex3Dinput - center3D
    val len1 = (cv - cv.projectOnto(axisSym1)).length()
    val len2 = (cv - cv.projectOnto(axisSym2)).length()
    val axisMain3D  = if (len1 < len2) axisSym1 else axisSym2
    val axisOther3D = n.cross(axisMain3D).normalize()

    // 7️⃣ Výpočet hlavního a vedlejšího poloprostoru
    val asigned = (vertex3Dinput - center3D).dot(axisMain3D)
    val a = abs(asigned)
    // spočteme průměrný sklon asymptot vůči hlavní ose
    val d12D = Offset(
        dir3D1.dot(axisMain3D),
        dir3D1.dot(axisOther3D)
    ).normalize()
    val d22D = Offset(
        dir3D2.dot(axisMain3D),
        dir3D2.dot(axisOther3D)
    ).normalize()
    val slope1 = d12D.y / d12D.x
    val slope2 = d22D.y / d22D.x
    val b = a * ((abs(slope1) + abs(slope2)) / 2f)

    // 8️⃣ Sestrojení parametrické hyperboly a konvert do implicitního tvaru
    val paramConic = computeConicFromParametricHyperbola(
        center    = Offset(0f, 0f),
        finalAxis = Offset(1f, 0f),
        otherDir  = Offset(0f, 1f),
        a = a,
        b = b
    )

    return ConicSection3D(
        p0 = center3D,
        u = axisMain3D,
        v = axisOther3D,
        color = state.currentLineStyleSettings.color,
        lineStyle = state.currentLineStyleSettings.style,
        strokeWidth = state.currentLineStyleSettings.strokeWidth,
        matrix = Matrix3x3.fromCoefficients(
            paramConic.a, paramConic.b, paramConic.c,
            paramConic.d, paramConic.e, paramConic.f
        ),
        a = a,
        b = b, creationIndex = allocIndex(state)
    )
}
fun intersectLines2D(p1: Offset, d1: Offset, p2: Offset, d2: Offset): Offset? {
    val det = d1.x * d2.y - d1.y * d2.x
    if (abs(det) < 1e-6f) return null
    val t = ((p2.x - p1.x) * d2.y - (p2.y - p1.y) * d2.x) / det
    return p1 + d1 * t
}
fun Offset3D.projectOnto(axis: Offset3D): Offset3D {
    val unit = axis.normalize()
    return unit * this.dot(unit)
}
fun Offset3D.length(): Float =
    sqrt(this.x * this.x + this.y * this.y + this.z * this.z)
fun liftXYtoPlane(x: Float, y: Float, eq: PlaneEquation): Offset3D {
    val z = -(eq.a * x + eq.b * y + eq.d) / eq.c
    return Offset3D(x, y, z)
}
fun liftXZtoPlane(x: Float, z: Float, eq: PlaneEquation): Offset3D {
    val y = -(eq.a * x + eq.c * z + eq.d) / eq.b
    return Offset3D(x, y, z)
}
private data class HyperbolaLiftVisibility(
    val pudorys: Boolean,
    val narys: Boolean,
    val bokorys: Boolean,
    val axo: Boolean
)

private fun hyperbolaLiftVisibility(source: String, mongeLift: Boolean): HyperbolaLiftVisibility =
    if (mongeLift) {
        HyperbolaLiftVisibility(pudorys = true, narys = true, bokorys = false, axo = true)
    } else {
        when (source) {
            "pudorys" -> HyperbolaLiftVisibility(pudorys = true, narys = false, bokorys = false, axo = true)
            "narys" -> HyperbolaLiftVisibility(pudorys = false, narys = true, bokorys = false, axo = true)
            "bokorys" -> HyperbolaLiftVisibility(pudorys = false, narys = false, bokorys = true, axo = true)
            else -> HyperbolaLiftVisibility(pudorys = false, narys = false, bokorys = false, axo = true)
        }
    }

fun finalizeLiftHyperbolaToPlaneFromPudorys(
    state: MongeState,
    conicP: ConicSectionPudorys,
    plane: Plane3D,
    mongeLift: Boolean = true
) {
    plane.equation ?: run {
        println("❌ Rovina nemá rovnici!")
        return
    }

    val conic3Duncanon = liftConicToPlaneFromPudorys(conicP, plane) ?: return
    val conic3Dcanonized = canonizeHyperbolaFrame(conic3Duncanon)
    val conic3D = setHyperbolaABFromMatrix(conic3Dcanonized).copy(creationIndex = allocIndex(state))
    val visibility = hyperbolaLiftVisibility("pudorys", mongeLift)

    val coeffsN = Matrix3x3.toCoefficients(conic3D.projectToXZ())
    val narys = ConicSectionNarys(
        a = coeffsN[0], b = coeffsN[1], c = coeffsN[2],
        d = coeffsN[3], e = coeffsN[4], f = coeffsN[5],
        rawName = conic3D.rawName,
        localColor = conic3D.color,
        strokeWidth = conic3D.strokeWidth,
        lineStyle = conic3D.lineStyle,
        parent = conic3D,
        parentId = conic3D.id,
        creationIndex = allocIndex(state),
        showInAxoInitial = visibility.narys
    )

    val coeffsB = Matrix3x3.toCoefficients(conic3D.projectToYZ())
    val bokorys = ConicSectionBokorys(
        a = coeffsB[0], b = coeffsB[1], c = coeffsB[2],
        d = coeffsB[3], e = coeffsB[4], f = coeffsB[5],
        rawName = conic3D.rawName,
        localColor = conic3D.color,
        strokeWidth = conic3D.strokeWidth,
        lineStyle = conic3D.lineStyle,
        parent = conic3D,
        parentId = conic3D.id,
        creationIndex = allocIndex(state),
        showInAxoInitial = visibility.bokorys
    )

    val axo = state.basis?.let { basis ->
        val coeffsA = Matrix3x3.toCoefficients(conic3D.projectToAxo(basis))
        ConicSectionAxo(
            a = coeffsA[0], b = coeffsA[1], c = coeffsA[2],
            d = coeffsA[3], e = coeffsA[4], f = coeffsA[5],
            rawName = conic3D.rawName,
            localColor = conic3D.color,
            strokeWidth = conic3D.strokeWidth,
            lineStyle = conic3D.lineStyle,
            parent = conic3D,
            parentId = conic3D.id,
            creationIndex = allocIndex(state),
            showInAxoInitial = visibility.axo
        )
    }

    if (mongeLift) {
        conicP.parent = conic3D
        conicP.parentId = conic3D.id
        conicP.showInAxo = visibility.pudorys
        addHyperbolaInputsForLift(state, pudorys = conicP)
    } else {
        val liftedPudorys = conicP.copy(
            parent = conic3D,
            parentId = conic3D.id,
            id = java.util.UUID.randomUUID().toString(),
            creationIndex = allocIndex(state),
            showInAxoInitial = visibility.pudorys
        )
        state.conicsPudorys.add(liftedPudorys)
        addHyperbolaInputsForLift(state, pudorys = liftedPudorys)
    }

    state.conics3D.add(conic3D)
    state.conicsNarys.add(narys)
    state.conicsBokorys.add(bokorys)
    axo?.let { state.conicsAxo.add(it) }
    addHyperbolaInputsForLift(state, narys = narys, bokorys = bokorys, axo = axo)

    println("✅ Hyperbola '${conicP.rawName}' zvednuta do roviny '${plane.name}' a průměty vytvořeny.")
    setProjectionPhase("pudorys_start", state)
    state.drawobjects = Mongeobjects.NONE
    commitSnapshot(state)
}
fun setHyperbolaABFromMatrix(conic: ConicSection3D): ConicSection3D {
    val M = conic.matrix
    // očekáváme nulové xy, nulové lineární členy – po centrování/rotaci by měly být ~0
    val alpha = M.m00
    val beta  = M.m11
    val gamma = M.m22

    // přenormovat tak, aby gamma = -1 (hyperbola) – pokud gamma > 0, vynásobíme -1
    val k = if (abs(gamma) > 1e-12f) -1f / gamma else 1f
    val alphaN = alpha * k
    val betaN  = beta  * k
    gamma * k // bude ≈ -1

    // kontrola, že je to hyperbola
    require(alphaN * betaN < 0f) { "Not a hyperbola: signs not opposite (alphaN=$alphaN, betaN=$betaN)" }

    val a = 1f / sqrt(abs(alphaN))
    val b = 1f / sqrt(abs(betaN))
    return conic.copy(a = a, b = b)
}
fun canonizeHyperbolaFrame(conic: ConicSection3D): ConicSection3D {
    // 1) Koeficienty v lokální rovině (ξ,η)
    val cs = Matrix3x3.toCoefficients(conic.matrix)
    val A = cs[0]; val B = cs[1]; val C = cs[2]; val D = cs[3]; val E = cs[4]; cs[5]

    // 2) Střed hyperboly (řeší ∇=0): [2A B; B 2C][cx;cy] = -[D;E]
    val S00 = 2f * A
    val S01 = B
    val S11 = 2f * C
    val det = S00 * S11 - S01 * S01
    if (abs(det) < 1e-12f) return conic // bezpečný fallback

    val cx = (B*E - 2f*C*D) / det
    val cy = (B*D - 2f*A*E) / det
    val Ctr = Offset(cx, cy)

    // 3) Posuň p0 do středu a uprav bázi později rotací (stejně jako u paraboly)
    val p0p = conic.p0 + conic.u * Ctr.x + conic.v * Ctr.y

    // T: translate o -Ctr do středu (stejný vzorec jako u tvé paraboly – T^T * M * T s T(tx=+cx,ty=+cy))
    val T = Matrix3x3(
        1f, 0f, Ctr.x,
        0f, 1f, Ctr.y,
        0f, 0f, 1f
    )
    val Mt = T.transpose() * conic.matrix * T

    // 4) Otoč osy podle kvadratické části Q = [[A, B/2],[B/2, C]]
    //    Najdeme ortonormální bázi (e1, e2) – vlastní vektory Q
    val a11 = A
    val a12 = B * 0.5f
    val a22 = C
    val tr = a11 + a22
    val disc = sqrt(kotlin.math.max(0f, (a11 - a22)*(a11 - a22) + 4f*a12*a12))
    val l1 = 0.5f * (tr + disc)

    val vx = if (abs(a12) > 1e-12f || abs(a11 - l1) > 1e-12f) a12 else 1f
    val vy = if (abs(a12) > 1e-12f || abs(a11 - l1) > 1e-12f) (l1 - a11) else 0f
    val L  = sqrt(vx*vx + vy*vy).coerceAtLeast(1e-12f)
    val e1 = Offset(vx / L, vy / L)
    val e2 = Offset(-e1.y, e1.x) // kolmá jednotková

    // R: rotate tak, aby x' = e1, y' = e2 (stejný zápis jako u paraboly)
    val R = Matrix3x3(
        e1.x, e2.x, 0f,
        e1.y, e2.y, 0f,
        0f, 0f, 1f
    )
    var Mcanon = R.transpose() * Mt * R

    // 5) Nastav kanonické škálování: u hyperboly chceme konstantu F' = -1 (tzn. γ ≈ -1)
    val coeffAfter = Matrix3x3.toCoefficients(Mcanon)
    val F2 = coeffAfter[5]
    if (abs(F2) > 1e-9f) {
        val s = -1f / F2
        Mcanon = Mcanon * s    // máš operator times(Float)
    } else if (F2 > 0f) {
        Mcanon = Mcanon * (-1f)
    }

    // 6) Přepočti 3D bázi (u', v') podle e1, e2 a zachovej pravotočivost (stejně jako u paraboly)
    var up  = conic.u * e1.x + conic.v * e1.y
    var vp  = conic.u * e2.x + conic.v * e2.y
    up = up.normalize(); vp = vp.normalize()

    val n = (conic.u cross conic.v)
    if (((up cross vp) dot n) < 0f) vp = vp * -1f

    return conic.copy(
        p0 = p0p,
        u  = up,
        v  = vp,
        matrix      = Mcanon,   // po posunu a rotaci, se škálováním jen přes globální s
        rawName     = conic.rawName,
        color       = conic.color,
        strokeWidth = conic.strokeWidth,
        lineStyle   = conic.lineStyle,
        a           = conic.a,
        b           = conic.b
    )
}

fun computeAsymptotes2D(
    a: Float, b: Float, c: Float,
    d: Float, e: Float, f: Float
): Triple<Offset, Offset, Offset>? {
    // 1) Střed (řešení ∇=0): [2a b; b 2c] [x0;y0] = -[d;e]
    val det = 4*a*c - b*b
    if (abs(det) < 1e-12f) return null  // necentrální případ -> hyperbola by neměla nastat, ale raději ověř
    val x0 = (b*e - 2*c*d) / det
    val y0 = (b*d - 2*a*e) / det
    val center = Offset(x0, y0)

    val disc = b*b - 4*a*c
    if (disc <= 0f) return null
    val sqrtDisc = sqrt(disc)

    fun norm(o: Offset): Offset {
        val L = sqrt(o.x*o.x + o.y*o.y)
        return if (L > 1e-12f) Offset(o.x / L, o.y / L) else o
    }
    val (dir1, dir2) = if (abs(c) > 1e-12f) {
        val m1 = (-b + sqrtDisc) / (2*c)
        val m2 = (-b - sqrtDisc) / (2*c)
        norm(Offset(1f, m1)) to norm(Offset(1f, m2))
    } else if (abs(b) > 1e-12f) {
        norm(Offset(0f, 1f)) to norm(Offset(1f, -a / b))
    } else {
        return null
    }
    return Triple(center, dir1, dir2)
}

private data class HyperbolaInput2D(
    val center: Offset,
    val dir1: Offset,
    val dir2: Offset,
    val vertex: Offset,
    val axis: Offset
)

private fun hyperbolaInput2D(
    a: Float, b: Float, c: Float,
    d: Float, e: Float, f: Float
): HyperbolaInput2D? {
    val (center, dir1, dir2) = computeAsymptotes2D(a, b, c, d, e, f) ?: return null
    val trace = a + c
    val diff = a - c
    val disc = sqrt(diff * diff + b * b)
    val lambda1 = (trace + disc) / 2f
    val lambda2 = (trace - disc) / 2f

    val fp = a * center.x * center.x +
            b * center.x * center.y +
            c * center.y * center.y +
            d * center.x +
            e * center.y +
            f

    // Hlavní (reálná, příčná) osa hyperboly je vlastní číslo λ, pro které je
    // poloosa² = -fp/λ kladná – NE nutně to s větší absolutní hodnotou. Výběr
    // podle |λ| umisťoval vrchol na vedlejší (konjugovanou) osu, takže zvednutá
    // hyperbola měla větve podél špatné osy.
    val semiSq1 = if (abs(lambda1) > 1e-12f) -fp / lambda1 else Float.NaN
    val semiSq2 = if (abs(lambda2) > 1e-12f) -fp / lambda2 else Float.NaN
    val (lambda, semiSq) = when {
        semiSq1.isFinite() && semiSq1 > 1e-12f -> lambda1 to semiSq1
        semiSq2.isFinite() && semiSq2 > 1e-12f -> lambda2 to semiSq2
        else -> return null
    }

    val axisRaw = if (abs(b) > 1e-12f || abs(a - lambda) > 1e-12f) {
        Offset(b, 2f * (lambda - a)).normalize()
    } else {
        Offset(1f, 0f)
    }
    if (axisRaw == Offset.Zero) return null

    val semi = sqrt(semiSq)
    val vertex = center + axisRaw * semi
    return HyperbolaInput2D(center, dir1, dir2, vertex, axisRaw)
}

/**
 * Nositelka degenerovaného průmětu hyperboly: rovina hyperboly se promítá do přímky,
 * takže všechny její body leží na jedné přímce. Směr získáme promítnutím libovolného
 * vektoru ležícího v rovině (u nebo v – ten s delší projekcí), střed z průmětu [p0].
 * Vrací (střed2D, jednotkový směr) v souřadnicích daného průmětu, nebo null.
 */
private fun degenerateHyperbolaCarrier2D(
    parent: ConicSection3D?,
    project: (Offset3D) -> Offset
): Pair<Offset, Offset>? {
    parent ?: return null
    val c = project(parent.p0)
    val du = project(parent.p0 + parent.u) - c
    val dv = project(parent.p0 + parent.v) - c
    val dir = if (du.getDistance() >= dv.getDistance()) du else dv
    val len = dir.getDistance()
    if (!len.isFinite() || len < 1e-6f) return null
    return c to Offset(dir.x / len, dir.y / len)
}

fun addHyperbolaInputsForLift(
    state: MongeState,
    pudorys: ConicSectionPudorys? = null,
    narys: ConicSectionNarys? = null,
    bokorys: ConicSectionBokorys? = null,
    axo: ConicSectionAxo? = null
) {
    pudorys?.let { conic ->
        val input = hyperbolaInput2D(conic.a, conic.b, conic.c, conic.d, conic.e, conic.f)
        conic.isDegenerate = input == null
        if (input != null) {
            state.hyperbolaInputsPudorys[conic.id] = ConicInputHyperbolaPudorys(
                vertex = input.vertex,
                axis = input.axis,
                line1 = Line3DProjectionPudorys(Point3DPudorys(input.center.x, input.center.y), input.dir1),
                line2 = Line3DProjectionPudorys(Point3DPudorys(input.center.x, input.center.y), input.dir2)
            )
        } else {
            // degenerovaný průmět (rovina hyperboly se promítá do přímky): ulož nositelku,
            // aby vykreslovací blok hyperboly proběhl a uplatnil oříznutí oblouku (jinak by
            // fallback vykreslil celou přímku bez ořezu)
            degenerateHyperbolaCarrier2D(conic.parent) { Offset(it.x, it.y) }?.let { (c, dir) ->
                conic.isLineDegenerate = true
                conic.degenerateDir = dir
                state.hyperbolaInputsPudorys[conic.id] = ConicInputHyperbolaPudorys(
                    vertex = c + dir, axis = dir,
                    line1 = Line3DProjectionPudorys(Point3DPudorys(c.x, c.y), dir),
                    line2 = Line3DProjectionPudorys(Point3DPudorys(c.x, c.y), dir)
                )
            }
        }
    }
    narys?.let { conic ->
        val input = hyperbolaInput2D(conic.a, conic.b, conic.c, conic.d, conic.e, conic.f)
        conic.isDegenerate = input == null
        if (input != null) {
            state.hyperbolaInputsNarys[conic.id] = ConicInputHyperbolaNarys(
                vertex = input.vertex,
                axis = input.axis,
                line1 = Line3DProjectionNarys(Point3DNarys(input.center.x, input.center.y), input.dir1),
                line2 = Line3DProjectionNarys(Point3DNarys(input.center.x, input.center.y), input.dir2)
            )
        } else {
            degenerateHyperbolaCarrier2D(conic.parent) { Offset(it.x, it.z) }?.let { (c, dir) ->
                conic.isLineDegenerate = true
                conic.degenerateDir = dir
                state.hyperbolaInputsNarys[conic.id] = ConicInputHyperbolaNarys(
                    vertex = c + dir, axis = dir,
                    line1 = Line3DProjectionNarys(Point3DNarys(c.x, c.y), dir),
                    line2 = Line3DProjectionNarys(Point3DNarys(c.x, c.y), dir)
                )
            }
        }
    }
    bokorys?.let { conic ->
        val input = hyperbolaInput2D(conic.a, conic.b, conic.c, conic.d, conic.e, conic.f)
        conic.isDegenerate = input == null
        if (input != null) {
            state.hyperbolaInputsBokorys[conic.id] = ConicInputHyperbolaBokorys(
                vertex = input.vertex,
                axis = input.axis,
                line1 = Line3DProjectionBokorys(Point3DBokorys(input.center.x, input.center.y), input.dir1),
                line2 = Line3DProjectionBokorys(Point3DBokorys(input.center.x, input.center.y), input.dir2)
            )
        } else {
            degenerateHyperbolaCarrier2D(conic.parent) { Offset(it.y, it.z) }?.let { (c, dir) ->
                conic.isLineDegenerate = true
                conic.degenerateDir = dir
                state.hyperbolaInputsBokorys[conic.id] = ConicInputHyperbolaBokorys(
                    vertex = c + dir, axis = dir,
                    line1 = Line3DProjectionBokorys(Point3DBokorys(c.x, c.y), dir),
                    line2 = Line3DProjectionBokorys(Point3DBokorys(c.x, c.y), dir)
                )
            }
        }
    }
    axo?.let { conic ->
        val input = hyperbolaInput2D(conic.a, conic.b, conic.c, conic.d, conic.e, conic.f)
        conic.isDegenerate = input == null
        if (input != null) {
            state.hyperbolaInputsAxo[conic.id] = ConicInputHyperbolaAxo(
                vertex = input.vertex,
                axis = input.axis,
                line1 = Line3DProjectionAxo(Point3DAxo(input.center.x, input.center.y), input.dir1),
                line2 = Line3DProjectionAxo(Point3DAxo(input.center.x, input.center.y), input.dir2)
            )
        } else {
            state.basis?.let { basis ->
                degenerateHyperbolaCarrier2D(conic.parent) { projectPoint3DToAxoLocal(it, basis) }?.let { (c, dir) ->
                    conic.isLineDegenerate = true
                    conic.degenerateDir = dir
                    state.hyperbolaInputsAxo[conic.id] = ConicInputHyperbolaAxo(
                        vertex = c + dir, axis = dir,
                        line1 = Line3DProjectionAxo(Point3DAxo(c.x, c.y), dir),
                        line2 = Line3DProjectionAxo(Point3DAxo(c.x, c.y), dir)
                    )
                }
            }
        }
    }
}

fun startLiftHyperbolaToPlaneFromPudorys(state: MongeState) {
    val conic = state.selectedConicsPudorys.firstOrNull()
    if (conic == null) {
        println("❌ Nevybral jsi žádnou kuželosečku v půdorysu.")
        return
    }

    state.liftingConicPudorys = conic

    setProjectionPhase("lift_hyperbola_select_plane_pudorys", state)

    println("📌 Kuželosečka '${conic.rawName}' připravena ke zvednutí.")
    println("ℹ️ Klikni na rovinu, do které ji chceš zvednout.")
}
fun finalizeLiftHyperbolaToPlaneFromNarys(
    state: MongeState,
    conicN: ConicSectionNarys,
    plane: Plane3D,
    mongeLift: Boolean = true
) {
    plane.equation ?: run {
        println("❌ Rovina nemá rovnici!")
        return
    }

    val conic3Duncanon = liftConicToPlaneFromNarys(conicN, plane) ?: return
    val conic3Dcanon = canonizeHyperbolaFrame(conic3Duncanon)
    val conic3D = setHyperbolaABFromMatrix(conic3Dcanon).copy(creationIndex = allocIndex(state))
    val visibility = hyperbolaLiftVisibility("narys", mongeLift)

    val coeffsP = Matrix3x3.toCoefficients(conic3D.projectToXY())
    val pudorys = ConicSectionPudorys(
        a = coeffsP[0], b = coeffsP[1], c = coeffsP[2],
        d = coeffsP[3], e = coeffsP[4], f = coeffsP[5],
        rawName = conic3D.rawName,
        localColor = conic3D.color,
        strokeWidth = conic3D.strokeWidth,
        lineStyle = conic3D.lineStyle,
        parent = conic3D,
        parentId = conic3D.id,
        creationIndex = allocIndex(state),
        showInAxoInitial = visibility.pudorys
    )

    val coeffsB = Matrix3x3.toCoefficients(conic3D.projectToYZ())
    val bokorys = ConicSectionBokorys(
        a = coeffsB[0], b = coeffsB[1], c = coeffsB[2],
        d = coeffsB[3], e = coeffsB[4], f = coeffsB[5],
        rawName = conic3D.rawName,
        localColor = conic3D.color,
        strokeWidth = conic3D.strokeWidth,
        lineStyle = conic3D.lineStyle,
        parent = conic3D,
        parentId = conic3D.id,
        creationIndex = allocIndex(state),
        showInAxoInitial = visibility.bokorys
    )

    val axo = state.basis?.let { basis ->
        val coeffsA = Matrix3x3.toCoefficients(conic3D.projectToAxo(basis))
        ConicSectionAxo(
            a = coeffsA[0], b = coeffsA[1], c = coeffsA[2],
            d = coeffsA[3], e = coeffsA[4], f = coeffsA[5],
            rawName = conic3D.rawName,
            localColor = conic3D.color,
            strokeWidth = conic3D.strokeWidth,
            lineStyle = conic3D.lineStyle,
            parent = conic3D,
            parentId = conic3D.id,
            creationIndex = allocIndex(state),
            showInAxoInitial = visibility.axo
        )
    }

    if (mongeLift) {
        conicN.parent = conic3D
        conicN.parentId = conic3D.id
        conicN.showInAxo = visibility.narys
        addHyperbolaInputsForLift(state, narys = conicN)
    } else {
        val liftedNarys = conicN.copy(
            parent = conic3D,
            parentId = conic3D.id,
            id = java.util.UUID.randomUUID().toString(),
            creationIndex = allocIndex(state),
            showInAxoInitial = visibility.narys
        )
        state.conicsNarys.add(liftedNarys)
        addHyperbolaInputsForLift(state, narys = liftedNarys)
    }

    state.conics3D.add(conic3D)
    state.conicsPudorys.add(pudorys)
    state.conicsBokorys.add(bokorys)
    axo?.let { state.conicsAxo.add(it) }
    addHyperbolaInputsForLift(state, pudorys = pudorys, bokorys = bokorys, axo = axo)

    println("✅ Hyperbola '${conicN.rawName}' zvednuta do roviny '${plane.name}' a průměty vytvořeny.")
    setProjectionPhase("narys_start", state)
    state.drawobjects = Mongeobjects.NONE
    commitSnapshot(state)
}
fun finalizeLiftHyperbolaToPlaneFromBokorys(
    state: MongeState,
    conicB: ConicSectionBokorys,
    plane: Plane3D,
    mongeLift: Boolean = true
) {
    val conic3Duncanon = liftConicToPlaneFromBokorys(conicB, plane) ?: return
    val conic3D = setHyperbolaABFromMatrix(canonizeHyperbolaFrame(conic3Duncanon)).copy(creationIndex = allocIndex(state))
    val visibility = hyperbolaLiftVisibility("bokorys", mongeLift)

    val coeffsP = Matrix3x3.toCoefficients(conic3D.projectToXY())
    val pudorys = ConicSectionPudorys(
        a = coeffsP[0], b = coeffsP[1], c = coeffsP[2],
        d = coeffsP[3], e = coeffsP[4], f = coeffsP[5],
        rawName = conic3D.rawName,
        localColor = conic3D.color,
        strokeWidth = conic3D.strokeWidth,
        lineStyle = conic3D.lineStyle,
        parent = conic3D,
        parentId = conic3D.id,
        creationIndex = allocIndex(state),
        showInAxoInitial = visibility.pudorys
    )

    val coeffsN = Matrix3x3.toCoefficients(conic3D.projectToXZ())
    val narys = ConicSectionNarys(
        a = coeffsN[0], b = coeffsN[1], c = coeffsN[2],
        d = coeffsN[3], e = coeffsN[4], f = coeffsN[5],
        rawName = conic3D.rawName,
        localColor = conic3D.color,
        strokeWidth = conic3D.strokeWidth,
        lineStyle = conic3D.lineStyle,
        parent = conic3D,
        parentId = conic3D.id,
        creationIndex = allocIndex(state),
        showInAxoInitial = visibility.narys
    )

    val axo = state.basis?.let { basis ->
        val coeffsA = Matrix3x3.toCoefficients(conic3D.projectToAxo(basis))
        ConicSectionAxo(
            a = coeffsA[0], b = coeffsA[1], c = coeffsA[2],
            d = coeffsA[3], e = coeffsA[4], f = coeffsA[5],
            rawName = conic3D.rawName,
            localColor = conic3D.color,
            strokeWidth = conic3D.strokeWidth,
            lineStyle = conic3D.lineStyle,
            parent = conic3D,
            parentId = conic3D.id,
            creationIndex = allocIndex(state),
            showInAxoInitial = visibility.axo
        )
    }

    if (mongeLift) {
        conicB.parent = conic3D
        conicB.parentId = conic3D.id
        conicB.showInAxo = visibility.bokorys
        addHyperbolaInputsForLift(state, bokorys = conicB)
    } else {
        val liftedBokorys = conicB.copy(
            parent = conic3D,
            parentId = conic3D.id,
            id = java.util.UUID.randomUUID().toString(),
            creationIndex = allocIndex(state),
            showInAxoInitial = visibility.bokorys
        )
        state.conicsBokorys.add(liftedBokorys)
        addHyperbolaInputsForLift(state, bokorys = liftedBokorys)
    }

    state.conics3D.add(conic3D)
    state.conicsPudorys.add(pudorys)
    state.conicsNarys.add(narys)
    axo?.let { state.conicsAxo.add(it) }
    addHyperbolaInputsForLift(state, pudorys = pudorys, narys = narys, axo = axo)
    setProjectionPhase("bokorys_start", state)
    state.drawobjects = Mongeobjects.NONE
    commitSnapshot(state)
}
fun startLiftHyperbolaToPlaneFromNarys(state: MongeState) {
    val conic = state.selectedConicsNarys.firstOrNull()
    if (conic == null) {
        println("❌ Nevybral jsi žádnou kuželosečku v nárysu.")
        return
    }

    state.liftingConicNarys = conic

    setProjectionPhase("lift_hyperbola_select_plane_narys", state)

    println("📌 Kuželosečka '${conic.rawName}' připravena ke zvednutí.")
    println("ℹ️ Klikni na rovinu, do které ji chceš zvednout.")
}
fun startLiftHyperbolaToPlaneFromBokorys(state: MongeState) {
    val conic = state.selectedConicsBokorys.firstOrNull()
    if (conic == null) {
        println("❌ Nevybral jsi žádnou kuželosečku v bokorysu.")
        return
    }

    state.liftingConicBokorys = conic
    setProjectionPhase("lift_hyperbola_select_plane_bokorys", state)

    println("📌 Kuželosečka '${conic.rawName}' připravena ke zvednutí.")
    println("ℹ️ Klikni na rovinu, do které ji chceš zvednout.")
}
fun extremeEnds2D(coeff: ConicCoeffs, eUnit: Offset): Pair<Offset, Offset>? {
    val c0 = conicCenter2D(coeff) ?: return null
    val (a,b,c,_,_,_) = coeff
    val inv = invS(a,b,c) ?: return null
    val (ia, ib, ic) = inv

    val ex = eUnit.x; val ey = eUnit.y
    val s = ia*ex*ex + 2f*ib*ex*ey + ic*ey*ey
    if (kotlin.math.abs(s) < 1e-12f) return null

    val f0 = f0Shifted(coeff)
    val rad = -f0 / s
    if (rad <= 1e-12f) return null

    val k = kotlin.math.sqrt(rad)
    val vx = (ia*ex + ib*ey) * k
    val vy = (ib*ex + ic*ey) * k

    return (c0 + Offset(vx,vy)) to (c0 - Offset(vx,vy))
}

private fun invS(a: Float, b: Float, c: Float): Triple<Float, Float, Float>? {
    val bh = b * 0.5f
    val det = a*c - bh*bh
    if (kotlin.math.abs(det) < 1e-12f) return null
    return Triple(c/det, -bh/det, a/det) // S^{-1} = [[ia,ib],[ib,ic]]
}

fun conicCenter2D(coeff: ConicCoeffs): Offset? {
    val (a,b,c,d,e,_) = coeff
    val inv = invS(a,b,c) ?: return null
    val (ia, ib, ic) = inv
    val cx = -0.5f * (ia*d + ib*e)
    val cy = -0.5f * (ib*d + ic*e)
    return Offset(cx, cy)
}

private fun f0Shifted(coeff: ConicCoeffs): Float {
    val (a,b,c,d,e,f) = coeff
    val inv = invS(a,b,c) ?: return f
    val (ia, ib, ic) = inv
    val quad = ia*d*d + 2f*ib*d*e + ic*e*e
    return f - 0.25f * quad
}
