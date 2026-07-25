package state.snapMonge


import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import draw.mongescreen.objects.toModelPlaneEquation
import draw.mongescreen.previews.conicsarcs.HyperbolaBasis
import draw.mongescreen.previews.conicsarcs.planeEquationFromConic3D
import draw.mongescreen.previews.conicsarcs.projectParabolaAndParam
import draw.mongescreen.previews.conicsarcs.projectToHyperbola
import model.*
import model.classes.ConicSection2D
import model.classes.ConicSectionBokorys
import model.classes.ConicSectionNarys
import model.classes.ConicSectionPudorys
import monge.input.ConicArcs.single.ellipseBasisFromDiameters
import monge.input.ConicArcs.single.ellipseParamAndProjection
import monge.input.axo.AxoRenderBasis
import monge.input.planeobjects.conicsections.conicCenter2D
import monge.input.planeobjects.conicsections.extremeEnds2D
import monge.input.planeobjects.conicsections.liftXYtoPlane
import monge.input.planeobjects.conicsections.liftXZtoPlane
import state.MongeState
import utils.dot
import utils.getLogicalCursor
import utils.normalize
import utils.toScreen
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.sqrt

data class SnapResult(
    val conic: ConicSection2D, // Každý nárys je spojován s výsledkem.
    val bestCandidatePoint: Offset, // Nejlepší nalezený bod (Offset) v narysou/display souřadnicích.
    val distanceToCursor: Float  // Vzdálenost z tohoto bodu do kurzoru.
)

fun snapConicNarys (state: MongeState,
                    logicalCursor: Offset,
                    axoBasis: AxoRenderBasis? = null
                    ): SnapResult? {
    val isMonge = state.projectionMode != ProjectionMode.AXO
    val screenCursor = state.cursorPosition
    val results = mutableListOf<SnapResult>()
    for (conic in state.conicsNarys) {
        if (!isMonge && !conic.showInAxo) continue

        fun narysPointToScreen(pDisp: Offset): Offset {
            return if (isMonge) {
                pDisp.toScreen(
                    state.scale,
                    state.canvasOffset,
                    state.canvasHeight,
                    state,
                    state.canvasWidth
                )
            } else {
                val basis = axoBasis ?: state.basis ?: return Offset.Zero

                // pDisp = nárysový display bod: (x, -z)
                val x = pDisp.x
                val z = -pDisp.y

                val axoLocal =
                    basis.origin +
                            basis.ex * x +
                            basis.ez * z

                axoLocal * state.scale + state.canvasOffset
            }
        }
        fun toUserLogicalViaGetLogical(pDisp: Offset): Offset {
            // 1) pDisp (logical) -> screen
            val sp = pDisp.toScreen(
                state.scale,
                state.canvasOffset,
                state.canvasHeight,
                state,
                state.canvasWidth
            )
            if (isMonge)
            // 2) screen -> user-logical (STEJNĚ jako pro kurzor)
            {
                return getLogicalCursor(
                    snapped = null,
                    cursor = sp,
                    canvasOffset = state.canvasOffset,
                    scale = state.scale,
                    canvasWidth = state.canvasWidth,
                    canvasHeight = state.canvasHeight,
                    flipX = (state.xAxisDirection == XAxisDirection.POSITIVE_LEFT),
                    flipY = (state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP)
                )
            }
            else {
                return pDisp
            }
        }
        fun distanceToCursorScreen(p: Offset): Float {
            val screen = narysPointToScreen(p)
            return (screen - screenCursor).getDistance()
        }

        val hyperInput = state.hyperbolaInputsNarys[conic.id]
        if (hyperInput != null) {
            // ⬇️ Degenerovaný NÁRYS: snap na raye/přímku (žádné vzorkování hyperboly)
            if (conic.isDegenerate) {
                fun toNarysScreen(pDisp: Offset) =
                    pDisp.toScreen(
                        state.scale,
                        state.canvasOffset,
                        state.canvasHeight,
                        state,
                        state.canvasWidth
                    )

                val conic3D = conic.parent ?: continue
                val eq = toModelPlaneEquation(planeEquationFromConic3D(conic3D))

                // Stejná konstrukce jako při kreslení: nárys je uložen jako display (x, -z).
                val nrm = Offset3D(eq.a, eq.b, eq.c).normalize()
                var u3 = nrm.cross(Offset3D(0f, 1f, 0f))
                val l3 = sqrt(u3.x * u3.x + u3.y * u3.y + u3.z * u3.z)
                u3 = if (l3 < 1e-9f) Offset3D(1f, 0f, 0f) else Offset3D(u3.x / l3, u3.y / l3, u3.z / l3)

                val uXY = run {
                    val v = Offset(u3.x, u3.y)
                    val l = sqrt(v.x * v.x + v.y * v.y).coerceAtLeast(1e-12f)
                    Offset(v.x / l, v.y / l)
                }
                val uDisp = run {
                    val v =Offset(u3.x, -u3.z)
                    val l = sqrt(v.x * v.x + v.y * v.y).coerceAtLeast(1e-12f)
                    Offset(v.x / l, v.y / l)
                }

                val pud = state.conicsPudorys.find { it.parent === conic.parent }
                val coeffsP = pud?.let { ConicCoeffs(it.a, it.b, it.c, it.d, it.e, it.f) }
                val cXY = coeffsP?.let { conicCenter2D(it) } ?: Offset.Zero
                val vertexXY = pud?.let { state.hyperbolaInputsPudorys[it.id]?.vertex } ?: cXY
                val sBranch = if ((vertexXY - cXY).dot(uXY) >= 0f) 1f else -1f

                fun toDispFromXY(pXY: Offset): Offset {
                    val p3d = liftXYtoPlane(pXY.x, pXY.y, eq)

                    return Offset(p3d.x, -p3d.z)
                }

                fun snapLine(origin: Offset, dir: Offset): Pair<Offset, Float>? {
                    val unit = dir.normalize()
                    val candidate = origin + unit * ((logicalCursor - origin).dot(unit))
                    val screen = toNarysScreen(candidate)
                    val dist = distanceToCursorScreen(screen)
                    return if (
                        abs(screen.x - screenCursor.x) <= state.snapThreshold &&
                        abs(screen.y - screenCursor.y) <= state.snapThreshold &&
                        dist <= state.snapThreshold
                    ) candidate to dist else null
                }

                fun snapRay(origin: Offset, dir: Offset): Pair<Offset, Float>? {
                    val unit = dir.normalize()
                    val tau = max(0f, (logicalCursor - origin).dot(unit))
                    val candidate = origin + unit * tau
                    val screen = toNarysScreen(candidate)
                    val dist = distanceToCursorScreen(screen)
                    return if (
                        abs(screen.x - screenCursor.x) <= state.snapThreshold &&
                        abs(screen.y - screenCursor.y) <= state.snapThreshold &&
                        dist <= state.snapThreshold
                    ) candidate to dist else null
                }

                val endsXY = coeffsP?.let { extremeEnds2D(it, uXY) }
                if (pud == null || endsXY == null) {
                    val origin = if (pud != null) {
                        toDispFromXY(cXY)
                    } else {
                        val kN = ConicCoeffs(conic.a, conic.b, conic.c, conic.d, conic.e, conic.f)
                        val centerNat = conicCenter2D(kN) ?: Offset.Zero
                         Offset(centerNat.x, -centerNat.y)
                    }
                    snapLine(origin, uDisp)?.let { (point, dist) ->
                        results += SnapResult(conic,point,dist)
                    }
                    continue
                }

                val (pPlusXY, pMinusXY) = endsXY
                val oPlus = toDispFromXY(pPlusXY)
                val oMinus = toDispFromXY(pMinusXY)
                val sPlus = if ((pPlusXY - cXY).dot(uXY) >= 0f) 1f else -1f
                val endA = if (sPlus == sBranch) oPlus else oMinus
                val endB = if (sPlus == sBranch) oMinus else oPlus

                listOfNotNull(
                    snapRay(endA, uDisp * sBranch),
                    snapRay(endB, -uDisp * sBranch)
                ).minByOrNull { it.second }?.let { (point, dist) ->
                    results +=  SnapResult(conic,point,dist)
                }
                continue
            }

            val center = computeIntersection(
                Offset(hyperInput.line1.point.x, hyperInput.line1.point.z),
                Offset(hyperInput.line1.direction.x, hyperInput.line1.direction.y),
                Offset(hyperInput.line2.point.x, hyperInput.line2.point.z),
                Offset(hyperInput.line2.direction.x, hyperInput.line2.direction.y)
            ) ?: continue

            val axisRaw = hyperInput.axis.normalize()
            val finalAxis = if ((hyperInput.vertex - center).dot(axisRaw) < 0f) -axisRaw else axisRaw
            val otherDir = Offset(-finalAxis.y, finalAxis.x)

            val a = (hyperInput.vertex - center).dot(finalAxis).absoluteValue

            val v1 = hyperInput.line1.direction.normalize()
            val v2 = hyperInput.line2.direction.normalize()

            val v1x = v1.dot(finalAxis)
            val v1y = v1.dot(otherDir)
            val v2x = v2.dot(finalAxis)
            val v2y = v2.dot(otherDir)

            val slope1 = v1y / v1x
            val slope2 = v2y / v2x

            val b = a * ((abs(slope1) + abs(slope2)) / 2f)

            val maxT = 2.5f

            fun point(t: Float, sign: Float): Offset {
                val x = a * t
                val y = b * sqrt(t * t - 1f) * sign
                return center + finalAxis * x + otherDir * y
            }

            var bestDist = Float.POSITIVE_INFINITY
            var bestPoint: Offset? = null
            for (sign in listOf(+1f, -1f)) {
                val points = mutableListOf<Offset>()
                points += point(1.0f, sign)
                var t = 1.001f
                while (t <= 1.2f) {
                    points += point(t, sign)
                    t += 0.002f  // jemnější krok
                }

// Hrubší vzorkování dál
                while (t <= maxT) {
                    points += point(t, sign)
                    t += 0.02f
                }
                for (i in 0 until points.size - 1) {
                    val p1 = points[i]
                    val p2 = points[i + 1]

                    val cursorNat =
                        Offset(logicalCursor.x, -logicalCursor.y)


                    val candNat = closestPointOnSegment(p1, p2, cursorNat)

                    val candDisp =  Offset(candNat.x, -candNat.y)

                    val dist = distanceToCursorScreen(candDisp)

                    if (dist < bestDist && dist <= state.snapThreshold) {
                        bestDist = dist
                        bestPoint = candDisp
                    }

                    val mirror1 = center * 2f - p1
                    val mirror2 = center * 2f - p2

                    val mcNat = closestPointOnSegment(mirror1, mirror2, cursorNat)

                    val mcDisp = Offset(mcNat.x, -mcNat.y)


                    val mdist = distanceToCursorScreen(mcDisp)

                    if (mdist < bestDist && mdist <= state.snapThreshold) {
                        bestDist = mdist
                        bestPoint = mcDisp
                    }
                }
            }

            if (bestPoint != null && bestDist <= state.snapThreshold / state.scale) {
                results +=  SnapResult(conic,bestPoint,bestDist)

            }

            continue
        }

        val input = state.conicInputPointsNarys[conic.id] ?: continue
        val (p1, p2, p3) = input

        if (p1 == Offset.Unspecified || p2 == Offset.Unspecified) continue

        if (p3 != Offset.Unspecified) {
            val input = state.conicInputPointsNarys[conic.id] ?: continue
            val (rawP1, rawP2, rawP3) = input

            val p1 = rawP1
            val p2 = rawP2
            val p3 = rawP3
            val degenerateClosest = closestPointOnDegenerateEllipse(p1, p2, p3, logicalCursor)
            if (conic.isDegenerate || degenerateClosest != null) {
                val closestDisp = degenerateClosest ?: continue
                val dist = distanceToCursorScreen(closestDisp)

                if (dist <= state.snapThreshold) {
                    results += SnapResult(conic,closestDisp,dist)
                }
                continue
            }
            // 🔄 Elipsa– parametrický průběh
            val rawCenter = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
            val rawU = (p2 - p1) / 2f
            val rawA = rawU.getDistance()
            if (rawA < 1e-6f) continue

            val rawMirrorP3 = Offset(2 * rawCenter.x - p3.x, 2 * rawCenter.y - p3.y)
            val rawV = (p3 - rawMirrorP3) / 2f
            val rawB = rawV.getDistance()
            if (rawB < 1e-6f) {
                val closestDisp = closestPointOnDegenerateEllipse(p1, p2, p3, logicalCursor)
                if (closestDisp != null) {
                    val dist = distanceToCursorScreen(closestDisp)
                    if (dist <= state.snapThreshold) {
                        results +=  SnapResult(conic,closestDisp,dist)

                    }
                }
                continue
            }
            val basis = ellipseBasisFromDiameters(p1, p2, p3)
            val (_, pt) = ellipseParamAndProjection(basis, logicalCursor)

            val distScreen = distanceToCursorScreen(pt)

            if (distScreen <= state.snapThreshold) {
                results += SnapResult(conic, pt, distScreen)
            }

        } else {
            val (rawVertex, rawFocus, _) = input

            val vertex = rawVertex
            val focus = rawFocus

            val maxSnapDist = state.snapThreshold / state.scale

            val isDeg = conic.isDegenerate

            if (isDeg) {
                var dir = conic.degenerateDir
                    ?: (focus - vertex)

                val L = dir.getDistance()
                dir = if (L < 1e-6f) Offset(1f, 0f) else dir / L

                val hint = focus - vertex
                if (hint.getDistance() > 1e-6f && hint.dot(dir) < 0f) {
                    dir = -dir
                }

                val isLine = conic.isLineDegenerate

                val cursorNat =Offset(logicalCursor.x, -logicalCursor.y)


                val vx = cursorNat.x - vertex.x
                val vy = cursorNat.y - vertex.y

                var t = vx * dir.x + vy * dir.y

                if (!isLine && t < 0f) {
                    t = 0f
                }

                val closestNat = vertex + dir * t

                val closestDisp =   Offset(closestNat.x, -closestNat.y)


                val dist = distanceToCursorScreen(closestDisp)

                if (dist <= state.snapThreshold) {
                    results += SnapResult(conic, closestDisp, dist)
                }

                continue
            }
            // ▼ NENÍ degenerace → přesná projekce kurzoru na parabolu v native nárysu (x,z)
            val axis = focus - vertex
            val p = axis.getDistance()
            if (p < 1e-6f) continue

            val cursorNat = Offset(logicalCursor.x, -logicalCursor.y)

            val (_, bestNat) = projectParabolaAndParam(vertex, focus, cursorNat)

            val bestDisp = if (isMonge) {
                Offset(bestNat.x, -bestNat.y)
            } else {
                Offset(bestNat.x, bestNat.y)
            }

            val bestDist = distanceToCursorScreen(bestDisp)

            if (bestDist <= state.snapThreshold) {
                results += SnapResult(conic, bestDisp, bestDist)
            }
        }

    }
    val bestresult = results.minByOrNull { it.distanceToCursor }

    state.snappedConicNarys = bestresult?.conic as? ConicSectionNarys

    return bestresult
}
fun snapConicPudorys (state: MongeState,
                      logicalCursor: Offset,
                      axoBasis: AxoRenderBasis? = null
): SnapResult?{
    val screenCursor = state.cursorPosition
    val isMonge = state.projectionMode != ProjectionMode.AXO
    val results = mutableListOf<SnapResult>()
    for (conic in state.conicsPudorys) {
        if (!isMonge && !conic.showInAxo) continue

        fun pudorysPointToScreen(pDisp: Offset): Offset {
            return if (isMonge) {
                pDisp.toScreen(
                    state.scale,
                    state.canvasOffset,
                    state.canvasHeight,
                    state,
                    state.canvasWidth
                )
            } else {
                val basis = axoBasis ?: state.basis ?: return Offset.Zero

                val x = pDisp.x
                val y = pDisp.y

                val axoLocal =
                    basis.origin +
                            basis.ex * x +
                            basis.ey * y

                axoLocal * state.scale + state.canvasOffset
            }
        }
        fun distanceToCursorScreen(p: Offset): Float {
            return (pudorysPointToScreen(p) - screenCursor).getDistance()
        }

        fun distanceSqToCursorScreen(p: Offset): Float {
            val d = pudorysPointToScreen(p) - screenCursor
            return d.x * d.x + d.y * d.y
        }

        // Nejprve máme k dispozici `conic`
        val hyperInput = state.hyperbolaInputsPudorys[conic.id]
        if (hyperInput != null) {
            val sc = state.cursorPosition                   // screen kurzor
            val thr = state.snapThreshold                   // práh v pixelech
            val thrSq = thr * thr
            val txScale = state.scale
            val txOffset = state.canvasOffset
            val txH = state.canvasHeight

// Rychlý screen-box kolem kurzoru (± threshold)
            val screenBox = Rect(
                sc.x - thr, sc.y - thr,
                sc.x + thr, sc.y + thr
            )

            fun toScreen(p: Offset): Offset =
                pudorysPointToScreen(p)

            fun inScreenBox(p: Offset): Boolean {
                val sp = toScreen(p)
                return screenBox.contains(sp)
            }

            fun distSqScreen(p: Offset): Float {
                val sp = toScreen(p)
                val dx = sp.x - sc.x
                val dy = sp.y - sc.y
                return dx * dx + dy * dy
            }

            // Předfiltr pro segment [p1, p2]:
            fun segCloseToCursor(p1: Offset, p2: Offset): Boolean {
                val s1 = toScreen(p1)
                val s2 = toScreen(p2)
                val left = minOf(s1.x, s2.x) - thr
                val right = maxOf(s1.x, s2.x) + thr
                val top = minOf(s1.y, s2.y) - thr
                val bottom = maxOf(s1.y, s2.y) + thr
                val segBox = Rect(left, top, right, bottom)
                return segBox.overlaps(screenBox)
            }

            val logicalCursorRaw = logicalCursor


            if (conic.isDegenerate) {
                val conic3D = conic.parent ?: continue
                val eq = toModelPlaneEquation(planeEquationFromConic3D(conic3D))

                // směr degenerované přímky v XY: uXY = (n × e_z) |_XY
                val n = Offset3D(eq.a, eq.b, eq.c).normalize()
                var u3 = n.cross(Offset3D(0f, 0f, 1f))
                val l3 = sqrt(u3.x * u3.x + u3.y * u3.y + u3.z * u3.z).coerceAtLeast(1e-12f)
                u3 = Offset3D(u3.x / l3, u3.y / l3, u3.z / l3)
                val uXY = run {
                    val v = Offset(u3.x, u3.y)
                    val L = sqrt(v.x * v.x + v.y * v.y).coerceAtLeast(1e-12f)
                    Offset(v.x / L, v.y / L)
                }

                // potřebujeme konce "gap" z nedegradovaného nárysu
                val narys = state.conicsNarys.find { it.parent === conic.parent }
                val (E1, E2) = if (narys != null) {
                    val kN = ConicCoeffs(narys.a, narys.b, narys.c, narys.d, narys.e, narys.f)
                    val eXZ = run {
                        val v = Offset(u3.x, u3.z);
                        val L = sqrt(v.x * v.x + v.y * v.y).coerceAtLeast(1e-12f); Offset(v.x / L, v.y / L)
                    }
                    val ends = extremeEnds2D(kN, eXZ)
                    fun toXY(pXZ: Offset): Offset {
                        val P3 = liftXZtoPlane(pXZ.x, pXZ.y, eq); return Offset(P3.x, P3.y)
                    }
                    if (ends != null) {
                        val (pPlus, pMinus) = ends
                        toXY(pPlus) to toXY(pMinus)
                    } else {
                        // dvojnásobná přímka: střed z P a konce splynou
                        val kP = ConicCoeffs(conic.a, conic.b, conic.c, conic.d, conic.e, conic.f)
                        val C = conicCenter2D(kP) ?: Offset.Zero
                        C to C
                    }
                } else {
                    // fallback: C ± g uXY (g z vrcholu v P)
                    val kP = ConicCoeffs(conic.a, conic.b, conic.c, conic.d, conic.e, conic.f)
                    val C = conicCenter2D(kP) ?: Offset.Zero
                    val v1 = hyperInput.vertex
                    val g = abs((v1 - C).dot(uXY))
                    (C + uXY * g) to (C - uXY * g)
                }

                // snap na dvě polopřímky: R1: E1 + τ u1 , R2: E2 + τ u2, τ ≥ 0
                fun snapOnRay(E: Offset, dir: Offset): Offset {
                    val tau = kotlin.math.max(0f, (logicalCursorRaw - E).dot(dir))
                    return E + dir * tau
                }

                val isLine = (E1 - E2).getDistance() < 1e-6f

                if (isLine) {
                    val tau = (logicalCursorRaw - E1).dot(uXY)
                    val cand = E1 + uXY * tau

                    // levný filtr + porovnání čtverců vzdáleností
                    if (inScreenBox(cand)) {
                        val d2 = distSqScreen(cand)
                        if (d2 <= thrSq) {
                            results += SnapResult(conic,cand,kotlin.math.sqrt(d2))
                        }
                    }
                    continue
                }
                // směr obou polopřímek musí mířit "ven" (od středu)
                val CforDir = (E1 + E2) * 0.5f
                val u1 = if ((E1 - CforDir).dot(uXY) >= 0f) uXY else -uXY
                val u2 = if ((E2 - CforDir).dot(uXY) >= 0f) uXY else -uXY
                val cand1 = snapOnRay(E1, u1) // s logicalCursorRaw uvnitř
                val cand2 = snapOnRay(E2, u2)

                var best = cand1
                var bestD2 = Float.POSITIVE_INFINITY

                if (inScreenBox(cand1)) {
                    bestD2 = distSqScreen(cand1)
                }
                if (inScreenBox(cand2)) {
                    val d2 = distSqScreen(cand2)
                    if (d2 < bestD2) {
                        bestD2 = d2
                        best = cand2
                    }
                }

                if (bestD2 <= thrSq) {
                    results += SnapResult(conic,best,kotlin.math.sqrt(bestD2))
                }
                continue
            }
            val center = computeIntersection(
                Offset(hyperInput.line1.point.x, hyperInput.line1.point.y),
                Offset(hyperInput.line1.direction.x, hyperInput.line1.direction.y),
                Offset(hyperInput.line2.point.x, hyperInput.line2.point.y),
                Offset(hyperInput.line2.direction.x, hyperInput.line2.direction.y)
            ) ?: continue // přeskoč, pokud se asymptoty neprotínají

            val axisRaw = hyperInput.axis.normalize()
            val finalAxis = if ((hyperInput.vertex - center).dot(axisRaw) < 0f) -axisRaw else axisRaw
            val otherDir = Offset(-finalAxis.y, finalAxis.x)

            val a = (hyperInput.vertex - center).dot(finalAxis).absoluteValue

            // ✅ Použijeme obě asymptoty pro výpočet b
            val v1 = hyperInput.line1.direction.normalize()
            val v2 = hyperInput.line2.direction.normalize()

            val v1x = v1.dot(finalAxis)
            val v1y = v1.dot(otherDir)
            val v2x = v2.dot(finalAxis)
            val v2y = v2.dot(otherDir)

            val slope1 = v1y / v1x
            val slope2 = v2y / v2x

            val b = a * ((abs(slope1) + abs(slope2)) / 2f)

            var bestPoint: Offset? = null
            var bestD2 = Float.POSITIVE_INFINITY

            val basis = HyperbolaBasis(
                center = center,
                ex = finalAxis,
                ey = otherDir,
                a = a.coerceAtLeast(1e-6f),
                b = b.coerceAtLeast(1e-6f)
            )

            for (branch in intArrayOf(1, -1)) {
                val cand = projectToHyperbola(basis, logicalCursorRaw, branch)?.on ?: continue
                if (!inScreenBox(cand)) continue
                val d2 = distSqScreen(cand)
                if (d2 <= thrSq && d2 < bestD2) {
                    bestD2 = d2
                    bestPoint = cand
                }
            }

            if (bestPoint != null) {
                results += SnapResult(conic,bestPoint,kotlin.math.sqrt(bestD2))
            }


            continue // přeskoč další části elipsy/paraboly
        }

        val input = state.conicInputPointsPudorys[conic.id] ?: continue
        val (p1, p2, p3) = input

        if (p3 != Offset.Unspecified) {
            val degenerateClosest = closestPointOnDegenerateEllipse(p1, p2, p3, logicalCursor)
            if (conic.isDegenerate || degenerateClosest != null) {
                val closest = degenerateClosest ?: continue
                val dist = distanceToCursorScreen(closest)
                if (dist <= state.snapThreshold) {
                    results += SnapResult(conic,closest,dist)
                }
                continue
            }
            // Parametrický průběh – elipsa
            val center = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)

            val u = (p2 - p1) / 2f
            val a = u.getDistance()
            if (a < 1e-6f) continue

            val mirrorP3 = Offset(2 * center.x - p3.x, 2 * center.y - p3.y)
            val v = (p3 - mirrorP3) / 2f
            val b = v.getDistance()
            if (b < 1e-6f) {
                val closest = closestPointOnDegenerateEllipse(p1, p2, p3, logicalCursor)
                if (closest != null) {
                    val dist = distanceToCursorScreen(closest)
                    if (dist <= state.snapThreshold) {
                        results += SnapResult(conic,closest,dist)
                    }
                }
                continue
            }

            // --- screen-space pomocné proměnné (jednou na začátku bloku) ---
            val sc = state.cursorPosition
            val thr = state.snapThreshold
            val thrSq = thr * thr
            val screenBox = Rect(sc.x - thr, sc.y - thr, sc.x + thr, sc.y + thr)

            fun toScreen(p: Offset): Offset =
                pudorysPointToScreen(p)
            fun inScreenBox(p: Offset): Boolean {
                val sp = toScreen(p)
                return screenBox.contains(sp)
            }

            fun distSqScreen(p: Offset): Float {
                val sp = toScreen(p)
                val dx = sp.x - sc.x
                val dy = sp.y - sc.y
                return dx * dx + dy * dy
            }
            // ---------------------------------------------------------------

            val basis = ellipseBasisFromDiameters(p1, p2, p3)
            val (_, pt) = ellipseParamAndProjection(basis, logicalCursor)
            if (inScreenBox(pt)) {
                val d2 = distSqScreen(pt)
                if (d2 <= thrSq) {
                    val dist = kotlin.math.sqrt(d2)
                    results += SnapResult(conic,pt,dist)
                }
            }
        } else {
            val (rawVertex, rawFocus, _) = input

// XY → bez invertace Y
            var origin = Offset(rawVertex.x, rawVertex.y)  // p1
            val focus = Offset(rawFocus.x, rawFocus.y)   // p2 (jen „hint“ směru)

            val cursor = logicalCursor


// Degenerace?
            val isDeg = conic.isDegenerate
            if (isDeg) {
                // pokud zvlášť ukládáš origin, preferuj ho:
                origin = p1

                // směr: uložený → fallback z (focus-origin) → poslední záchrana (1,0)
                var dir = conic.degenerateDir ?: (focus - origin)
                val l = dir.getDistance()
                dir = if (l < 1e-6f) Offset(1f, 0f) else dir / l

                // přímka vs. polopřímka
                val isLine = conic.isLineDegenerate

                // zarovnej orientaci podle (focus - origin), ať „neukazuje zpět“
                val hint = focus - origin
                if (hint.getDistance() > 1e-6f && (hint.x * dir.x + hint.y * dir.y) < 0f) {
                    dir = Offset(-dir.x, -dir.y)
                }

                // projekce kurzoru na přímku/polopřímku
                val vx = cursor.x - origin.x
                val vy = cursor.y - origin.y
                var t = vx * dir.x + vy * dir.y   // (cursor-origin)·dir
                if (!isLine && t < 0f) t = 0f

                val closest = origin + dir * t
                val dist = distanceToCursorScreen(closest)

                if (dist <= state.snapThreshold) {
                    results += SnapResult(conic, closest, dist)
                }
                continue
            }

// ▼ NENÍ degenerace → přesná projekce kurzoru na parabolu
            val vertex = origin            // pro čitelnost
            val axis = focus - vertex
            val p = axis.getDistance()
            if (p < 1e-6f) continue

            val (_, bestPt) = projectParabolaAndParam(vertex, focus, cursor)
            val bestDist = distanceToCursorScreen(bestPt)

            if (bestDist <= state.snapThreshold) {
                results += SnapResult(conic, bestPt, bestDist)
            }

        }
    }
    val bestresult = results.minByOrNull { it.distanceToCursor }

    state.snappedConicPudorys = bestresult?.conic as? ConicSectionPudorys

    return bestresult

}

fun snapConicBokorys(
    state: MongeState,
    logicalCursor: Offset,
    axoBasis: AxoRenderBasis? = null
): SnapResult? {
    val screenCursor = state.cursorPosition
    val isMonge = state.projectionMode != ProjectionMode.AXO
    val results = mutableListOf<SnapResult>()

    for (conic in state.conicsBokorys) {
        if (!isMonge && !conic.showInAxo) continue

        fun bokorysPointToScreen(p: Offset): Offset {
            return if (isMonge) {
                p.toScreen(
                    state.scale,
                    state.canvasOffset,
                    state.canvasHeight,
                    state,
                    state.canvasWidth
                )
            } else {
                val basis = axoBasis ?: state.basis ?: return Offset.Zero
                (basis.origin + basis.ey * p.x + basis.ez * p.y) * state.scale + state.canvasOffset
            }
        }

        fun distanceToCursorScreen(p: Offset): Float {
            return (bokorysPointToScreen(p) - screenCursor).getDistance()
        }

        val hyperInput = state.hyperbolaInputsBokorys[conic.id]
        if (hyperInput != null) {
            val center = computeIntersection(
                Offset(hyperInput.line1.point.y, hyperInput.line1.point.z),
                Offset(hyperInput.line1.direction.x, hyperInput.line1.direction.y),
                Offset(hyperInput.line2.point.y, hyperInput.line2.point.z),
                Offset(hyperInput.line2.direction.x, hyperInput.line2.direction.y)
            ) ?: continue

            val axisRaw = hyperInput.axis.normalize()
            val finalAxis = if ((hyperInput.vertex - center).dot(axisRaw) < 0f) -axisRaw else axisRaw
            val otherDir = Offset(-finalAxis.y, finalAxis.x)

            if (conic.isDegenerate) {
                var dir = conic.degenerateDir ?: finalAxis
                val len = dir.getDistance()
                dir = if (len < 1e-6f) Offset(1f, 0f) else dir / len

                val isLine = conic.isLineDegenerate
                val vx = logicalCursor.x - hyperInput.vertex.x
                val vy = logicalCursor.y - hyperInput.vertex.y
                var t = vx * dir.x + vy * dir.y
                if (!isLine && t < 0f) t = 0f

                val closest = hyperInput.vertex + dir * t
                val dist = distanceToCursorScreen(closest)
                if (dist <= state.snapThreshold) {
                    results += SnapResult(conic, closest, dist)
                }
                continue
            }

            val a = (hyperInput.vertex - center).dot(finalAxis).absoluteValue
            val v1 = hyperInput.line1.direction.normalize()
            val v2 = hyperInput.line2.direction.normalize()
            val slope1 = v1.dot(otherDir) / v1.dot(finalAxis)
            val slope2 = v2.dot(otherDir) / v2.dot(finalAxis)
            val b = a * ((abs(slope1) + abs(slope2)) / 2f)

            val basis = HyperbolaBasis(
                center = center,
                ex = finalAxis,
                ey = otherDir,
                a = a.coerceAtLeast(1e-6f),
                b = b.coerceAtLeast(1e-6f)
            )

            var bestPoint: Offset? = null
            var bestDist = Float.POSITIVE_INFINITY
            for (branch in intArrayOf(1, -1)) {
                val candidate = projectToHyperbola(basis, logicalCursor, branch)?.on ?: continue
                val dist = distanceToCursorScreen(candidate)
                if (dist <= state.snapThreshold && dist < bestDist) {
                    bestDist = dist
                    bestPoint = candidate
                }
            }

            if (bestPoint != null) {
                results += SnapResult(conic, bestPoint, bestDist)
            }
            continue
        }

        val input = state.conicInputPointsBokorys[conic.id] ?: continue
        val (p1, p2, p3) = input

        if (p1 == Offset.Unspecified || p2 == Offset.Unspecified) continue

        if (p3 != Offset.Unspecified) {
            val degenerateClosest = closestPointOnDegenerateEllipse(p1, p2, p3, logicalCursor)
            if (conic.isDegenerate || degenerateClosest != null) {
                val closest = degenerateClosest ?: continue
                val dist = distanceToCursorScreen(closest)
                if (dist <= state.snapThreshold) {
                    results += SnapResult(conic, closest, dist)
                }
                continue
            }

            val basis = ellipseBasisFromDiameters(p1, p2, p3)
            val (_, projected) = ellipseParamAndProjection(basis, logicalCursor)
            val dist = distanceToCursorScreen(projected)
            if (dist <= state.snapThreshold) {
                results += SnapResult(conic, projected, dist)
            }
        } else {
            val vertex = p1
            val focus = p2

            if (conic.isDegenerate) {
                var dir = conic.degenerateDir ?: (focus - vertex)
                val len = dir.getDistance()
                dir = if (len < 1e-6f) Offset(1f, 0f) else dir / len

                val hint = focus - vertex
                if (hint.getDistance() > 1e-6f && hint.dot(dir) < 0f) {
                    dir = -dir
                }

                var t = (logicalCursor - vertex).dot(dir)
                if (!conic.isLineDegenerate && t < 0f) t = 0f

                val closest = vertex + dir * t
                val dist = distanceToCursorScreen(closest)
                if (dist <= state.snapThreshold) {
                    results += SnapResult(conic, closest, dist)
                }
                continue
            }

            if ((focus - vertex).getDistance() < 1e-6f) continue
            val (_, bestPt) = projectParabolaAndParam(vertex, focus, logicalCursor)
            val dist = distanceToCursorScreen(bestPt)
            if (dist <= state.snapThreshold) {
                results += SnapResult(conic, bestPt, dist)
            }
        }
    }

    val bestResult = results.minByOrNull { it.distanceToCursor }
    state.snappedConicBokorys = bestResult?.conic as? ConicSectionBokorys
    return bestResult
}
