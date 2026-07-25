package draw.mongescreen.objects.orth

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import draw.mongescreen.objects.conicCenterXY
import draw.mongescreen.objects.conics.drawEllipseSegments
import draw.mongescreen.objects.conics.drawHyperbolaBranchSegments
import draw.mongescreen.objects.conics.drawParabolaSegments
import draw.mongescreen.objects.conics.restrictEllipseSegmentsToArc
import draw.mongescreen.objects.conics.restrictHyperbolaSegmentsToArcs
import draw.mongescreen.objects.conics.restrictParabolaSegmentsToArc
import draw.mongescreen.objects.extremeEndsXY
import draw.mongescreen.objects.orth.conics.*
import draw.mongescreen.objects.toModelPlaneEquation
import draw.mongescreen.previews.conicsarcs.*
import draw.mongescreen.objects.HOVER_HALO_EXTRA_PX

import draw.mongescreen.objects.PENDING_HALO_EXTRA_PX
import draw.mongescreen.objects.SELECTION_HALO_EXTRA_PX
import model.*
import monge.input.intersections.ops.sampleIntersectionHyperbolaBranchArc3D
import monge.input.ruledsurface.isPendingRuledSurfaceDirectrix
import monge.input.planeobjects.conicsections.conicCenter2D
import monge.input.planeobjects.conicsections.extremeEnds2D
import monge.input.planeobjects.conicsections.liftXYtoPlane
import monge.input.planeobjects.conicsections.liftXZtoPlane
import state.MongeState
import state.snapMonge.computeIntersection
import utils.dot
import utils.normalize
import utils.toScreenOld
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

fun DrawScope.drawAllConicsPudorys(state: MongeState,pxPerPt: Float, showHelpConic: Boolean) {
    for (haloPass in listOf(true, false)) {
        for (conic in state.conicsPudorys) {
            val pathEffect = when (conic.lineStyle) {
                LineStyle.Solid -> null
                LineStyle.Dashed -> PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                LineStyle.Dotted -> PathEffect.dashPathEffect(floatArrayOf(3f, 12f), 0f)
                LineStyle.DashDot -> PathEffect.dashPathEffect(floatArrayOf(20f, 10f, 4f, 10f), 0f)
            }
            val isPending = if (conic.parent != null) {
                isPendingRuledSurfaceDirectrix(state, conic.parent?.id ?: conic.parentId) ||
                        state.pendingConic3DId == conic.parent?.id ||
                        state.activeParentConic3DIdForEllipseArc == conic.parent?.id ||
                        state.activeConicIdForArc == conic.id ||
                        state.activeConicIdForArc == conic.id ||
                        state.activeConicIdForArc == conic.id
            } else if (conic.parent == null) {
                isPendingRuledSurfaceDirectrix(state, conic.parentId) ||
                        state.activeConicIdForArc == conic.id ||
                        state.activeConicIdForArc == conic.id ||
                        state.activeConicIdForArc == conic.id
            } else false
            val isSelected =      state.selectedConicsPudorys.any { it.id == conic.id } ||    // přímo vybraná 2D
                    state.selectedConicsNarys.any { it.id == conic.id }
            val isSnapped = state.drawobjects == Mongeobjects.NONE && state.snappedConicPudorys?.id == conic.id
            if (haloPass && !isPending && !isSelected && !isSnapped) continue
            val drawColor = when {
                haloPass && isPending  -> Color(0xFF1CD9B3).copy(alpha = 0.45f)
                haloPass && isSelected -> state.selectedHaloColor
                haloPass && isSnapped  -> state.hoverHaloColor
                isPending  -> Color(0xFF1CD9B3)
                else       -> conic.color
            }
            val drawWidth = when {
                haloPass && isSelected -> conic.strokeWidth * pxPerPt + SELECTION_HALO_EXTRA_PX * pxPerPt
                haloPass && isSnapped  -> conic.strokeWidth * pxPerPt + HOVER_HALO_EXTRA_PX * pxPerPt
                haloPass && isPending  -> conic.strokeWidth * pxPerPt + PENDING_HALO_EXTRA_PX * pxPerPt
                else       -> conic.strokeWidth * pxPerPt
            }
            if (state.hyperbolaInputsPudorys.containsKey(conic.id)) {
                val input = state.hyperbolaInputsPudorys[conic.id] ?: continue

                input.line1.direction.normalize()
                computeIntersection(
                    Offset(input.line1.point.x, input.line1.point.y),
                    Offset(input.line1.direction.x, input.line1.direction.y),
                    Offset(input.line2.point.x, input.line2.point.y),
                    Offset(input.line2.direction.x, input.line2.direction.y)
                ) ?: run {
                    println("⚠️ Asymptoty v nárysu se neprotínají – použiji vrchol jako střed.")
                    input.vertex // fallback: použij vrchol místo středu
                }

                if (state.hyperbolaInputsPudorys.containsKey(conic.id)) {
                    val input = state.hyperbolaInputsPudorys[conic.id] ?: continue
                    val v1 = input.line1.direction.normalize()
                    val center = computeIntersection(
                        Offset(input.line1.point.x, input.line1.point.y), Offset(input.line1.direction.x, input.line1.direction.y),
                        Offset(input.line2.point.x, input.line2.point.y), Offset(input.line2.direction.x, input.line2.direction.y)
                    ) ?: input.vertex

                    val basis = hyperbolaBasisFrom(center, input.vertex, v1, input.axis)

                    // Po částech stylovaná hyperbola (střídavě plná/čárkovaná)
                    val segH = state.conicSegments[conic.id]
                    if (segH != null && !conic.isDegenerate && !segH.isEmpty()) {
                        val primarySegments = restrictHyperbolaSegmentsToArcs(
                            basis = basis,
                            forcedBranchSX = +1,
                            segs = segH.primary,
                            branch1 = state.hyperbolaArcBranch1[conic.id],
                            branch2 = state.hyperbolaArcBranch2[conic.id]
                        )
                        drawHyperbolaBranchSegments(
                            basis, +1, primarySegments,
                            project = { it.toScreenOld(state.scale, state.canvasOffset) },
                            color = drawColor.runtimeDrawColor(), strokeWidth = drawWidth, dashScale = state.scale
                        )
                        segH.secondary?.let { sec ->
                            val secondarySegments = restrictHyperbolaSegmentsToArcs(
                                basis = basis,
                                forcedBranchSX = -1,
                                segs = sec,
                                branch1 = state.hyperbolaArcBranch1[conic.id],
                                branch2 = state.hyperbolaArcBranch2[conic.id]
                            )
                            drawHyperbolaBranchSegments(
                                basis, -1, secondarySegments,
                                project = { it.toScreenOld(state.scale, state.canvasOffset) },
                                color = drawColor.runtimeDrawColor(), strokeWidth = drawWidth, dashScale = state.scale
                            )
                        }
                        continue
                    }

                    val branch1 = state.hyperbolaArcBranch1[conic.id]
                    val branch2 = state.hyperbolaArcBranch2[conic.id]
                    if (branch1 != null || branch2 != null) {
                        val conic3D = conic.parent
                        val eq = conic3D?.let { toModelPlaneEquation( planeEquationFromConic3D(it) ) }

                        if (conic.isDegenerate && conic3D != null) {
                            // degenerovaný půdorys (rovina hyperboly se promítá do přímky):
                            // navzorkuj oblouk ze 3D a promítni do (x,y) – přesné umístění bez posunu
                            fun drawBranch(ends3D: Pair<model.Offset3D, model.Offset3D>?) {
                                if (ends3D == null) return
                                val pts = sampleIntersectionHyperbolaBranchArc3D(conic3D, ends3D)
                                if (pts.size < 2) return
                                val screen = pts.map { Offset(it.x, it.y).toScreenOld(state.scale, state.canvasOffset) }
                                drawPathFromPoints(screen, drawColor.runtimeDrawColor(), drawWidth, conic.lineStyle, scale = state.scale)
                            }
                            drawBranch(state.hyperbolaArcEnds3D[conic3D.id]?.first)
                            drawBranch(state.hyperbolaArcEnds3D[conic3D.id]?.second)
                            continue
                        }

                        // ⬇️ Nedegradovaný PŮDORYS: tvoje původní kreslení
                        if (isSelected) {
                            drawConicHyperbolaPudorys(
                                center = center, asymptote1 = v1, vertex = input.vertex,
                                canvasOffset = state.canvasOffset, scale = state.scale,
                                color = Color.Gray.copy(alpha = 0.6f), strokeWidth = 0.5f, lineStyle = LineStyle.Dashed,
                                axis = input.axis
                            )
                        }
                        // 1) větev 1
                        branch1?.let { (A, B) ->
                            val sX = if ((A - basis.center).dot(basis.ex) >= 0f) +1 else -1
                            drawHyperbolaBranchArcPudorys(
                                basis, A, B, sX,
                                canvasOffset = state.canvasOffset, scale = state.scale,
                                color = drawColor.runtimeDrawColor(), strokeWidth = drawWidth, lineStyle = conic.lineStyle
                            )
                        }
                        // 2) větev 2
                        branch2?.let { (A, B) ->
                            val sX = if ((A - basis.center).dot(basis.ex) >= 0f) +1 else -1
                            drawHyperbolaBranchArcPudorys(
                                basis, A, B, sX,
                                canvasOffset = state.canvasOffset, scale = state.scale,
                                color = drawColor.runtimeDrawColor(), strokeWidth = drawWidth, lineStyle = conic.lineStyle
                            )
                        }
                        continue
                    }
                    if (conic.isDegenerate) {
                        val conic3D = conic.parent ?: continue
                        val eq = toModelPlaneEquation( planeEquationFromConic3D(conic3D) )

                        // najdi "sesterský" nárys pro koeficienty XZ
                        val narys = state.conicsNarys.find { it.parent === conic.parent } ?: continue
                        val coeffsN = ConicCoeffs(narys.a, narys.b, narys.c, narys.d, narys.e, narys.f)

                        // směr u2 = n × e_z
                        val n = Offset3D(eq.a, eq.b, eq.c).normalize()
                        var u2 = n.cross(Offset3D(0f,0f,1f))
                        val L3 = kotlin.math.sqrt(u2.x*u2.x + u2.y*u2.y + u2.z*u2.z).coerceAtLeast(1e-12f)
                        u2 = Offset3D(u2.x/L3, u2.y/L3, u2.z/L3)

                        // v XZ (nárys) pro algebra (eXZ), v XY (půdorys) pro kreslení (uXY)
                        val eXZ = Offset(u2.x, u2.z).let {
                            val L = kotlin.math.sqrt(it.x*it.x + it.y*it.y).coerceAtLeast(1e-12f)
                            Offset(it.x/L, it.y/L)
                        }
                        val uXY = Offset(u2.x, u2.y).let {
                            val L = kotlin.math.sqrt(it.x*it.x + it.y*it.y).coerceAtLeast(1e-12f)
                            Offset(it.x/L, it.y/L)
                        }

                        // dva „nejzazší“ body v NÁRYSU (XZ); null ⇒ jen přímka
                        val endsXZ = extremeEnds2D(coeffsN, eXZ)

                        // kvadrant/ větev z nárysu (aby polopřímky mířily správně)
                        val inpN = state.hyperbolaInputsNarys[narys.id]
                        val centerN = conicCenter2D(coeffsN) ?: Offset.Zero          // nativní (x,z)
                        val vertexN_native = inpN?.let { Offset(it.vertex.x, -it.vertex.y) } ?: centerN
                        fun sgn(v: Float) = if (v >= 0f) +1 else -1
                        val sBranch = sgn((vertexN_native - centerN).dot(eXZ)).toFloat()

                        // kreslení helper
                        fun DrawScope.drawRayXY(origin: Offset, dir: Offset) {
                            val L = 50000f / max(1f, state.scale)
                            val A = Offset(origin.x*state.scale + state.canvasOffset.x, origin.y*state.scale + state.canvasOffset.y)
                            val B = Offset((origin.x + dir.x*L)*state.scale + state.canvasOffset.x,
                                (origin.y + dir.y*L)*state.scale + state.canvasOffset.y)
                            drawLine(drawColor, A, B, drawWidth, pathEffect = pathEffect)
                        }

                        if (endsXZ == null) {
                            // dvojnásobná přímka (žádný gap): vezmi střed (z nárysu), zvedni do 3D, shoď do XY a kresli ±uXY
                            val C3 = liftXZtoPlane(centerN.x, centerN.y, eq)
                            val cXY = Offset(C3.x, C3.y)
                            drawRayXY(cXY,  uXY)
                            drawRayXY(cXY, -uXY)
                        } else {
                            // dva XZ body → zvednout do 3D → do XY; přiřadit podle kvadrantu a kreslit dvě polopřímky
                            val (pPlusXZ, pMinusXZ) = endsXZ
                            fun toXY(pXZ: Offset): Offset {
                                val P3 = liftXZtoPlane(pXZ.x, pXZ.y, eq)  // (x,z) → 3D
                                return Offset(P3.x, P3.y)               // do XY (bez negací)
                            }
                            val oPlusXY  = toXY(pPlusXZ)
                            val oMinusXY = toXY(pMinusXZ)

                            val sPlus = sgn((pPlusXZ - centerN).dot(eXZ))
                            val endA  = if (sPlus.toFloat() == sBranch) oPlusXY else oMinusXY
                            val endB  = if (sPlus.toFloat() == sBranch) oMinusXY else oPlusXY

                            drawRayXY(endA,  uXY * sBranch)
                            drawRayXY(endB, -uXY * sBranch)
                        }
                        continue
                    }
                    // fallback: žádné oblouky → tvoje původní plné vykreslení
                    drawConicHyperbolaPudorys(
                        center = center, asymptote1 = v1, vertex = input.vertex,
                        canvasOffset = state.canvasOffset, scale = state.scale,
                        color = drawColor.runtimeDrawColor(), strokeWidth = drawWidth, lineStyle = conic.lineStyle,
                        axis = input.axis
                    )
                    continue
                }
            }

            // Degenerovaná hyperbola ze zvednutí (lift) nemá záznam v hyperbolaInputsPudorys
            // (hyperbolaInput2D vrátí null), takže ji blok výše přeskočí a níže by ji
            // zahodilo `?: continue`. Vykresli ji navzorkováním 3D hyperboly (dvě
            // polopřímky / přímka). Pro ne-hyperboly vrátí helper false → fallback níže.
            if (
                conic.isDegenerate &&
                !state.hyperbolaInputsPudorys.containsKey(conic.id) &&
                state.conicInputPointsPudorys[conic.id]?.third != Offset.Unspecified
            ) {
                val parent3D = conic.parent
                if (parent3D != null && drawDegenerateHyperbolaFrom3D(
                        conic3D = parent3D,
                        viewDrop = { Offset(it.x, it.y) },
                        project = { it.toScreenOld(state.scale, state.canvasOffset) },
                        color = drawColor.runtimeDrawColor(),
                        strokeWidth = drawWidth,
                        lineStyle = conic.lineStyle,
                        dashScale = state.scale
                    )
                ) continue
            }

            val inputs = state.conicInputPointsPudorys[conic.id] ?: continue
            val p1 = inputs.first
            val p2 = inputs.second
            val p3 = inputs.third

            if (p1 == Offset.Unspecified || p2 == Offset.Unspecified) continue

            if (p3 == Offset.Unspecified) {
                // Po částech stylovaná parabola (střídavě plná/čárkovaná)
                val segP = state.conicSegments[conic.id]
                if (segP != null && !conic.isDegenerate && !segP.isEmpty()) {
                    val segments = restrictParabolaSegmentsToArc(
                        vertex = p1,
                        focus = p2,
                        segs = segP.primary,
                        arcEnds = state.parabolaArcEnds[conic.id]
                    )
                    drawParabolaSegments(
                        p1, p2, segments,
                        project = { it.toScreenOld(state.scale, state.canvasOffset) },
                        color = drawColor.runtimeDrawColor(), strokeWidth = drawWidth, dashScale = state.scale
                    )
                    continue
                }
                val ends = state.parabolaArcEnds[conic.id]
                if (ends != null) {
                    val (A, B) = ends

                    val isDeg = conic.isDegenerate
                    if (isDeg) {
                        val origin = p1
                        var dir = conic.degenerateDir ?: (p2 - origin)
                        val Ld = dir.getDistance()
                        dir = if (Ld < 1e-6f) Offset(1f, 0f) else dir / Ld
                        val isLine = conic.isLineDegenerate
                        var drawn = false

                        // ❗ pokus o přesné ‘nad sebou’: vezmi koncové body oblouku z NÁRYSU a promítni je do XY
                        val parent3D = conic.parent
                        if (parent3D != null) {

// rovnici roviny pro převod XZ → XY
                            val eqPlane = planeEquationFromConic3D(parent3D)

// spárovaný nárys + jeho vstupy (vrchol/ohnisko v XZ)
                            val nar = state.conicsNarys.firstOrNull { it.parent?.id == parent3D.id }
                            val inputsN = nar?.let { state.conicInputPointsNarys[it.id] }
                            if (nar != null && inputsN != null) {

                                val (vN, fN, _) = inputsN  // v nárysové logice ukládáš (x,z)
                                val endsN = state.parabolaArcEnds[nar.id]
                                if (endsN == null) {
                                    drawParabolaDegenerateArcPudorys(
                                        origin = p1,
                                        dirIn = dir,
                                        isLine = isLine,
                                        A = A,
                                        B = B,
                                        canvasOffset = state.canvasOffset,
                                        scale = state.scale,
                                        color = drawColor.runtimeDrawColor(),
                                        strokeWidth = drawWidth,
                                        lineStyle = conic.lineStyle
                                    )
                                    continue
                                }
                                val (aNArc, bNArc) = endsN

// on-curve konce a jejich parametry uA,uB v NÁRYSU
                                val (uA, AonN) = projectParabolaAndParam(vN, fN, aNArc)
                                val (uB, BonN) = projectParabolaAndParam(vN, fN, bNArc)

// převod těchto on-curve XZ bodů do XY na stejné rovině
                                val Axy = liftXZtoXY(eqPlane, AonN.x, AonN.y)
                                val Bxy = liftXZtoXY(eqPlane, BonN.x, BonN.y)
                                if (Axy != null && Bxy != null) {

// směr ray/přímky v XY
                                    dir = if (Ld < 1e-6f) Offset(1f, 0f) else dir / Ld

                                    // parametr s na přímce/rayi (origin = p1 – vrchol degenerované projekce)
                                    fun sOf(P: Offset) = (P.x - p1.x) * dir.x + (P.y - p1.y) * dir.y
                                    var s1 = sOf(Axy)
                                    var s2 = sOf(Bxy)

// pokud nárysový oblouk přechází přes vrchol (u mění znaménko), zahrň i s=0 (origin)
                                    val crossesVertex = (uA * uB) <= 0f
                                    if (crossesVertex) {
                                        s1 = min(s1, 0f)
                                        s2 = max(s2, 0f)
                                    }

// ořízni na polopřímku
                                    if (!isLine) {
                                        s1 = max(0f, s1)
                                        s2 = max(0f, s2)
                                    }
                                    if (abs(s2 - s1) >= 1e-6f) {
                                        drawParabolaDegenerateArcPudorys(
                                            origin = p1,
                                            dirIn = dir,
                                            isLine = isLine,
                                            A = p1 + dir * s1,
                                            B = p1 + dir * s2,
                                            canvasOffset = state.canvasOffset,
                                            scale = state.scale,
                                            color = drawColor.runtimeDrawColor(),
                                            strokeWidth = drawWidth,
                                            lineStyle = conic.lineStyle
                                        )
                                        drawn = true
                                    }
                                }
                            }
                        }

                        if (!drawn) {
                            drawParabolaDegenerateArcPudorys(
                                origin = p1,
                                dirIn = dir,
                                isLine = isLine,
                                A = A,
                                B = B,
                                canvasOffset = state.canvasOffset,
                                scale = state.scale,
                                color = drawColor.runtimeDrawColor(),
                                strokeWidth = drawWidth,
                                lineStyle = conic.lineStyle
                            )
                        }

                        continue

                    }


                    // ⬇️ NENÍ degenerace → původní chování
                    if (isSelected) {
                        drawConicParabolaPudorys(
                            vertex = p1, focus = p2,
                            canvasOffset = state.canvasOffset, scale = state.scale,
                            color = Color.Gray.copy(alpha = 0.6f), strokeWidth = 0.5f, lineStyle = LineStyle.Dashed
                        )
                    }
                    drawParabolaArcPudorys(
                        vertex = p1, focus = p2,
                        A = A, B = B,
                        canvasOffset = state.canvasOffset, scale = state.scale,
                        color = drawColor.runtimeDrawColor(), strokeWidth = drawWidth, lineStyle = conic.lineStyle
                    )
                    continue
                }


                // když oblouk není → tvé původní kreslení celé paraboly
                val isDeg = conic.isDegenerate
                if (isDeg) {
                    // origin = vrchol degenerované projekce (p1 by jím měl být, pokud jsi ho tak uložil v handleru)
                    val origin = p1

                    // směr: preferuj uložený, fallback z (p2 - origin)
                    var dir = conic.degenerateDir ?: (p2 - origin)
                    val L = dir.getDistance()
                    dir = if (L < 1e-6f) Offset(1f, 0f) else dir / L

                    // zarovnání orientace podle (p2 - origin), ať není „otočená“
                    val hint = p2 - origin
                    if (hint.getDistance() > 1e-6f && (hint.x * dir.x + hint.y * dir.y) < 0f) {
                        dir = Offset(-dir.x, -dir.y)
                    }

                    val isLine = conic.isLineDegenerate

                    // styl čáry dle conic.lineStyle
                    val pathEffect = when (conic.lineStyle) {
                        LineStyle.Solid  -> null
                        LineStyle.Dashed -> PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                        LineStyle.Dotted -> PathEffect.dashPathEffect(floatArrayOf(3f, 12f), 0f)
                        LineStyle.DashDot-> PathEffect.dashPathEffect(floatArrayOf(20f, 10f, 4f, 10f), 0f)
                    }

                    if (isLine) {
                        // přímka přes origin oběma směry
                        val span = max(size.width, size.height) / max(1e-6f, state.scale)
                        val A = origin - dir * span
                        val B = origin + dir * span

                        // pozadí (volitelné)
                        if (state.showConstruction.value && showHelpConic) {
                            drawLine(
                                color = Color.Gray.copy(alpha = 0.6f),
                                start = A.toScreenOld(state.scale, state.canvasOffset),
                                end   = B.toScreenOld(state.scale, state.canvasOffset),
                                strokeWidth = 0.5f * pxPerPt,
                                pathEffect = pathEffect
                            )
                        }
                        // hlavní tah
                        drawLine(
                            color = drawColor.runtimeDrawColor(),
                            start = A.toScreenOld(state.scale, state.canvasOffset),
                            end   = B.toScreenOld(state.scale, state.canvasOffset),
                            strokeWidth = drawWidth,
                            pathEffect = pathEffect
                        )
                    } else {
                        // polopřímka z origin ve směru dir
                        if (state.showConstruction.value && showHelpConic) {
                            drawConicParabolaPudorys(
                                vertex = origin, focus = origin + dir,
                                canvasOffset = state.canvasOffset, scale = state.scale,
                                color = Color.Gray.copy(alpha = 0.6f),
                                strokeWidth = 0.5f, lineStyle = conic.lineStyle,
                                tStep = 1f, degenerateRay = true
                            )
                        }
                        drawConicParabolaPudorys(
                            vertex = origin, focus = origin + dir,
                            canvasOffset = state.canvasOffset, scale = state.scale,
                            color = drawColor.runtimeDrawColor(), strokeWidth = drawWidth,
                            lineStyle = conic.lineStyle, tStep = 1f,
                            degenerateRay = true
                        )
                    }
                    continue
                }

// ▼ NENÍ degenerace → klasicky celá parabola
                drawConicParabolaPudorys(
                    vertex = p1, focus = p2,
                    canvasOffset = state.canvasOffset, scale = state.scale,
                    color = drawColor.runtimeDrawColor(), strokeWidth = drawWidth, lineStyle = conic.lineStyle
                )

            } else {
                // Po částech stylovaná elipsa (střídavě plná/čárkovaná)
                val segE = state.conicSegments[conic.id]
                val inpE = state.conicInputPointsPudorys[conic.id]
                if (segE != null && inpE != null && inpE.third != Offset.Unspecified &&
                    !conic.isDegenerate && !segE.isEmpty()) {
                    val segments = restrictEllipseSegmentsToArc(
                        p1 = inpE.first,
                        p2 = inpE.second,
                        p3 = inpE.third,
                        segs = segE.primary,
                        arcEnds = state.ellipseArcEnds[conic.id],
                        mode = state.ellipseArcMode[conic.id] ?: ArcMode.SHORTEST
                    )
                    drawEllipseSegments(
                        inpE.first, inpE.second, inpE.third, segments,
                        project = { it.toScreenOld(state.scale, state.canvasOffset) },
                        color = drawColor.runtimeDrawColor(), strokeWidth = drawWidth, dashScale = state.scale
                    )
                    continue
                }
                val ends  = state.ellipseArcEnds[conic.id]
                val mode  = state.ellipseArcMode[conic.id] ?: ArcMode.SHORTEST
                val inputs= state.conicInputPointsPudorys[conic.id]

                if (ends != null && inputs != null && inputs.third != Offset.Unspecified) {
                    val (p1, p2, p3) = inputs
                    val (A, B) = ends

                    // 1) slabé pozadí: celá elipsa (jen když je vybraná)
                    if (isSelected) {
                        drawEllipseFromDiameters(
                            p1 = p1, p2 = p2, p3 = p3,
                            scale = state.scale,
                            canvasOffset = state.canvasOffset,
                            color = Color.Gray.copy(alpha = 0.6f),
                            strokeWidth = 0.5f,
                            lineStyle = LineStyle.Dashed // klidně Solid, jak chceš
                        )}

                    // 2) vlastní oblouk
                    drawEllipseArcFromDiameters(
                        p1 = p1, p2 = p2, p3 = p3,
                        A = A, B = B,
                        mode = mode,
                        scale = state.scale,
                        canvasOffset = state.canvasOffset,
                        color = drawColor.runtimeDrawColor(),
                        strokeWidth = drawWidth,
                        lineStyle = conic.lineStyle
                    )

                    continue // nenech už kreslit plnou elipsu pod tím
                }

                // když oblouk není → klasicky celá elipsa
                drawEllipseFromDiameters(
                    p1 = p1,
                    p2 = p2,
                    p3 = p3,
                    scale = state.scale,
                    canvasOffset = state.canvasOffset,
                    color = drawColor.runtimeDrawColor(),
                    strokeWidth = drawWidth,
                    lineStyle = conic.lineStyle
                )
            }

        }
    } // haloPass
}
fun DrawScope.drawAllConicsNarys(state: MongeState,pxPerPt: Float, showHelpConic: Boolean) {
    for (haloPass in listOf(true, false)) {
        for (conic in state.conicsNarys) {
            val pathEffect = when (conic.lineStyle) {
                LineStyle.Solid -> null
                LineStyle.Dashed -> PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                LineStyle.Dotted -> PathEffect.dashPathEffect(floatArrayOf(3f, 12f), 0f)
                LineStyle.DashDot -> PathEffect.dashPathEffect(floatArrayOf(20f, 10f, 4f, 10f), 0f)
            }
            val isPending = if(conic.parent != null) {
                isPendingRuledSurfaceDirectrix(state, conic.parent?.id ?: conic.parentId) ||
                    state.pendingConic3DId == conic.parent?.id||
                    state.activeParentConic3DIdForEllipseArc==conic.parent?.id||state.activeConicIdForArc == conic.id||
                    state.activeConicIdForArc==conic.id || state.activeConicIdForArc==conic.id
            } else if (conic.parent == null) {
                isPendingRuledSurfaceDirectrix(state, conic.parentId) ||
                    state.activeConicIdForArc == conic.id||
                    state.activeConicIdForArc==conic.id || state.activeConicIdForArc==conic.id} else false
            val isSelected =      state.selectedConicsPudorys.any { it.id == conic.id } ||    // přímo vybraná 2D
                    state.selectedConicsNarys.any { it.id == conic.id }

            val isSnapped = state.drawobjects == Mongeobjects.NONE && state.snappedConicNarys?.id == conic.id
            if (haloPass && !isPending && !isSelected && !isSnapped) continue
            val drawColor = when {
                haloPass && isPending  -> Color(0xFF1CD9B3).copy(alpha = 0.45f)
                haloPass && isSelected -> state.selectedHaloColor
                haloPass && isSnapped  -> state.hoverHaloColor
                isPending  -> Color(0xFF1CD9B3)
                else       -> conic.color
            }
            val drawWidth = when {
                haloPass && isSelected -> conic.strokeWidth * pxPerPt + SELECTION_HALO_EXTRA_PX * pxPerPt
                haloPass && isSnapped  -> conic.strokeWidth * pxPerPt + HOVER_HALO_EXTRA_PX * pxPerPt
                haloPass && isPending  -> conic.strokeWidth * pxPerPt + PENDING_HALO_EXTRA_PX * pxPerPt
                else       -> conic.strokeWidth * pxPerPt
            }

            if (state.hyperbolaInputsNarys.containsKey(conic.id)) {
                val input = state.hyperbolaInputsNarys[conic.id] ?: continue

                fun Offset3D.normalize(): Offset3D {
                    val L = kotlin.math.sqrt(x*x + y*y + z*z)
                    return if (L <= 1e-6f) this else Offset3D(x/L, y/L, z/L)
                }

                if (conic.isDegenerate) {
                    state.hyperbolaInputsNarys[conic.id] ?: continue
                    val conic3D = conic.parent ?: continue
                    val eq = toModelPlaneEquation( planeEquationFromConic3D(conic3D) )
                    val br1 = state.hyperbolaArcBranch1[conic.id]
                    val br2 = state.hyperbolaArcBranch2[conic.id]
                    if (br1 != null || br2 != null) {
                        // degenerovaný nárys (rovina hyperboly se promítá do přímky):
                        // navzorkuj oblouk ze 3D a promítni do nárysu (x,-z) – přesné umístění
                        fun drawBranch(ends3D: Pair<model.Offset3D, model.Offset3D>?) {
                            if (ends3D == null) return
                            val pts = sampleIntersectionHyperbolaBranchArc3D(conic3D, ends3D)
                            if (pts.size < 2) return
                            val screen = pts.map { Offset(it.x, -it.z).toScreenOld(state.scale, state.canvasOffset) }
                            drawPathFromPoints(screen, drawColor.runtimeDrawColor(), drawWidth, conic.lineStyle, scale = state.scale)
                        }
                        drawBranch(state.hyperbolaArcEnds3D[conic3D.id]?.first)
                        drawBranch(state.hyperbolaArcEnds3D[conic3D.id]?.second)
                        continue
                    }

                    // směr průsečnice roviny s XZ: u3 = n × e_y
                    val n = Offset3D(eq.a, eq.b, eq.c).normalize()
                    var u3 = n.cross(Offset3D(0f,1f,0f)).normalize()
                    if (abs(u3.x) + abs(u3.z) < 1e-9f) u3 = Offset3D(1f,0f,0f)

                    val uXY = Offset(u3.x, u3.y).let {
                        val L = kotlin.math.sqrt(it.x*it.x + it.y*it.y).coerceAtLeast(1e-9f)
                        Offset(it.x/L, it.y/L)
                    }
                    val uDisp = Offset(u3.x, -u3.z).let {
                        val L = kotlin.math.sqrt(it.x*it.x + it.y*it.y).coerceAtLeast(1e-9f)
                        Offset(it.x/L, it.y/L)
                    }

                    // koeficienty hyperboly v PŮDORYSU (XY)
                    val conicP = state.conicsPudorys.find { it.parent === conic.parent } ?: continue
                    val coeffsP = ConicCoeffs(conicP.a, conicP.b, conicP.c, conicP.d, conicP.e, conicP.f)

                    // dva krajní XY body (pokud existují)
                    val ends = extremeEndsXY(coeffsP, uXY)

                    // střed & kvadrant (větev) z půdorysu
                    val cXY = conicCenterXY(coeffsP) ?: Offset.Zero
                    val vXY = state.hyperbolaInputsPudorys[conicP.id]?.vertex ?: cXY
                    val sBranch = if ((vXY - cXY).dot(uXY) >= 0f) +1f else -1f

                    // pomocné kreslení jednoho ray-e
                    fun DrawScope.drawRay(origin: Offset, dir: Offset) {
                        val L = 50000f / max(1f, state.scale)
                        val A = Offset(origin.x*state.scale + state.canvasOffset.x, origin.y*state.scale + state.canvasOffset.y)
                        val B = Offset((origin.x + dir.x*L)*state.scale + state.canvasOffset.x,
                            (origin.y + dir.y*L)*state.scale + state.canvasOffset.y)
                        drawLine(drawColor, A, B, drawWidth,pathEffect=pathEffect)
                    }

                    if (ends == null) {
                        // ⇒ žádné konečné extrémy → dvojnásobná přímka
                        val c3 = liftXYtoPlane(cXY.x, cXY.y, eq)
                        val o = Offset(c3.x, -c3.z)
                        drawRay(o,  uDisp)
                        drawRay(o, -uDisp)
                    } else {
                        // zvednout oba body do 3D a shoď do XZ → display (x,-z)
                        val (pPlusXY, pMinusXY) = ends
                        fun toDisp(p: Offset): Offset {
                            val P3 = liftXYtoPlane(p.x, p.y, eq)
                            return Offset(P3.x, -P3.z)
                        }
                        val oPlus  = toDisp(pPlusXY)
                        val oMinus = toDisp(pMinusXY)

                        // přiřazení podle kvadrantu (kterou větev uživatel vybral v půdorysu)
                        val sPlus  = if ((pPlusXY  - cXY).dot(uXY) >= 0f) +1 else -1
                        val endA   = if (sPlus.toFloat() == sBranch) oPlus  else oMinus
                        val endB   = if (sPlus.toFloat() == sBranch) oMinus else oPlus

                        drawRay(endA,  uDisp * sBranch)   // „naše“ větev
                        drawRay(endB, -uDisp * sBranch)   // druhá větev
                    }
                    continue
                }


                // ⬆️⬆️ konec větve degenerace ⬆️⬆️

                // (nedegenerováno) – tvoje původní vykreslení:
                val v1 = input.line1.direction.normalize()
                val center = computeIntersection(
                    Offset(input.line1.point.x, input.line1.point.z),
                    Offset(input.line1.direction.x, input.line1.direction.y),
                    Offset(input.line2.point.x, input.line2.point.z),
                    Offset(input.line2.direction.x, input.line2.direction.y)
                ) ?: input.vertex

                val basis = hyperbolaBasisFrom(center, input.vertex, v1, input.axis)

                // Po částech stylovaná hyperbola (střídavě plná/čárkovaná) – nárys flip
                val segH = state.conicSegments[conic.id]
                if (segH != null && !conic.isDegenerate && !segH.isEmpty()) {
                    val primarySegments = restrictHyperbolaSegmentsToArcs(
                        basis = basis,
                        forcedBranchSX = +1,
                        segs = segH.primary,
                        branch1 = state.hyperbolaArcBranch1[conic.id],
                        branch2 = state.hyperbolaArcBranch2[conic.id]
                    )
                    drawHyperbolaBranchSegments(
                        basis, +1, primarySegments,
                        project = { it.toScreenNarys(state.scale, state.canvasOffset) },
                        color = drawColor.runtimeDrawColor(), strokeWidth = drawWidth, dashScale = state.scale
                    )
                    segH.secondary?.let { sec ->
                        val secondarySegments = restrictHyperbolaSegmentsToArcs(
                            basis = basis,
                            forcedBranchSX = -1,
                            segs = sec,
                            branch1 = state.hyperbolaArcBranch1[conic.id],
                            branch2 = state.hyperbolaArcBranch2[conic.id]
                        )
                        drawHyperbolaBranchSegments(
                            basis, -1, secondarySegments,
                            project = { it.toScreenNarys(state.scale, state.canvasOffset) },
                            color = drawColor.runtimeDrawColor(), strokeWidth = drawWidth, dashScale = state.scale
                        )
                    }
                    continue
                }

                val br1 = state.hyperbolaArcBranch1[conic.id]
                val br2 = state.hyperbolaArcBranch2[conic.id]

                if (br1 != null || br2 != null) {
                    if (isSelected) {
                        drawConicHyperbolaNarys(
                            center = center, asymptote1 = v1, vertex = input.vertex,
                            canvasOffset = state.canvasOffset, scale = state.scale,
                            color = Color.Gray.copy(alpha = 0.6f), strokeWidth = 0.5f, lineStyle = LineStyle.Dashed,
                            axis = input.axis
                        )
                    }
                    br1?.let { (A, B) ->
                        val sX = if ((A - basis.center).dot(basis.ex) >= 0f) +1 else -1
                        drawHyperbolaBranchArcNarys(
                            basis, A, B, sX,
                            canvasOffset = state.canvasOffset, scale = state.scale,
                            color = drawColor.runtimeDrawColor(), strokeWidth = drawWidth, lineStyle = conic.lineStyle
                        )
                    }
                    br2?.let { (A, B) ->
                        val sX = if ((A - basis.center).dot(basis.ex) >= 0f) +1 else -1
                        drawHyperbolaBranchArcNarys(
                            basis, A, B, sX,
                            canvasOffset = state.canvasOffset, scale = state.scale,
                            color = drawColor.runtimeDrawColor(), strokeWidth = drawWidth, lineStyle = conic.lineStyle
                        )
                    }
                    continue
                }

                // fallback: celá hyperbola
                drawConicHyperbolaNarys(
                    center = center, asymptote1 = v1, vertex = input.vertex,
                    canvasOffset = state.canvasOffset, scale = state.scale,
                    color = drawColor.runtimeDrawColor(), strokeWidth = drawWidth, lineStyle = conic.lineStyle,
                    axis = input.axis
                )
                continue
            }

            // Degenerovaná hyperbola ze zvednutí (lift) nemá záznam v hyperbolaInputsNarys,
            // takže by ji `?: continue` níže zahodilo. Vykresli ji navzorkováním 3D
            // hyperboly; nárysné plátno převrací z (Offset(x,-z)).
            if (
                conic.isDegenerate &&
                !state.hyperbolaInputsNarys.containsKey(conic.id) &&
                state.conicInputPointsNarys[conic.id]?.third != Offset.Unspecified
            ) {
                val parent3D = conic.parent
                if (parent3D != null && drawDegenerateHyperbolaFrom3D(
                        conic3D = parent3D,
                        viewDrop = { Offset(it.x, it.z) },
                        project = { Offset(it.x, -it.y).toScreenOld(state.scale, state.canvasOffset) },
                        color = drawColor.runtimeDrawColor(),
                        strokeWidth = drawWidth,
                        lineStyle = conic.lineStyle,
                        dashScale = state.scale
                    )
                ) continue
            }

            val inputs = state.conicInputPointsNarys[conic.id] ?: continue
            val p1 = inputs.first
            val p2 = inputs.second
            val p3 = inputs.third

            if (p1 == Offset.Unspecified || p2 == Offset.Unspecified) continue

            if (p3 == Offset.Unspecified) {

                // Po částech stylovaná parabola (střídavě plná/čárkovaná) – nárys flip
                val segP = state.conicSegments[conic.id]
                if (segP != null && !conic.isDegenerate && !segP.isEmpty()) {
                    val segments = restrictParabolaSegmentsToArc(
                        vertex = p1,
                        focus = p2,
                        segs = segP.primary,
                        arcEnds = state.parabolaArcEnds[conic.id]
                    )
                    drawParabolaSegments(
                        p1, p2, segments,
                        project = { it.toScreenNarys(state.scale, state.canvasOffset) },
                        color = drawColor.runtimeDrawColor(), strokeWidth = drawWidth, dashScale = state.scale
                    )
                    continue
                }

                val isDegN = conic.isDegenerate
                if (isDegN) {
                    val dir = conic.degenerateDir ?: (p2 - p1)
                    val isLine = conic.isLineDegenerate
                    val origin = state.conicInputPointsNarys[conic.id]?.first ?: continue
                    val endsN = state.parabolaArcEnds[conic.id]
                    if (endsN != null) {
                        val (aN, bN) = endsN

                        drawParabolaDegenerateArcNarys(
                            origin = origin,
                            dirIn = dir,
                            isLine = isLine,
                            A = aN,
                            B = bN,
                            scale = state.scale,
                            canvasOffset = state.canvasOffset,
                            color = drawColor.runtimeDrawColor(),
                            strokeWidth = drawWidth,
                            lineStyle = conic.lineStyle
                        )
                        continue
                    }

                    // pokus o „nad sebou“ přes PŮDORYS (XY → XZ)
                    val parent3D = conic.parent
                    val pud      = state.conicsPudorys.firstOrNull { it.parent?.id == parent3D?.id }
                    val inputsP  = pud?.let { state.conicInputPointsPudorys[it.id] }
                    val endsP    = pud?.let { state.parabolaArcEnds[it.id] }

                    if (parent3D != null && inputsP != null && endsP != null) {
                        // fallback na přímé konce z půdorysu nepoužíváme; vlastní narys konce jsou autoritativní
                    }

                    // fallback: nemám XY konce → vykresli čistou přímku / polopřímku
                    val pathEffect = when (conic.lineStyle) {
                        LineStyle.Solid  -> null
                        LineStyle.Dashed -> PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                        LineStyle.Dotted -> PathEffect.dashPathEffect(floatArrayOf(3f, 12f), 0f)
                        LineStyle.DashDot-> PathEffect.dashPathEffect(floatArrayOf(20f, 10f, 4f, 10f), 0f)
                    }
                    val span = max(size.width, size.height) / max(1e-6f, state.scale)

                    if (isLine) {
                        val A = origin - dir * span
                        val B = origin + dir * span
                        if (state.showConstruction.value && showHelpConic) {
                            drawLine(
                                color = Color.Gray.copy(alpha = 0.6f),
                                start = A.toScreenNarys(state.scale, state.canvasOffset),
                                end   = B.toScreenNarys(state.scale, state.canvasOffset),
                                strokeWidth = 0.5f * pxPerPt,
                                pathEffect = pathEffect
                            )
                        }
                        drawLine(
                            color = drawColor.runtimeDrawColor(),
                            start = A.toScreenNarys(state.scale, state.canvasOffset),
                            end   = B.toScreenNarys(state.scale, state.canvasOffset),
                            strokeWidth = drawWidth,
                            pathEffect = pathEffect
                        )
                    } else {
                        if (state.showConstruction.value && showHelpConic) {
                            drawConicParabolaNarys(
                                vertex = origin, focus = origin + dir,
                                canvasOffset = state.canvasOffset, scale = state.scale,
                                color = Color.Gray.copy(alpha = 0.6f),
                                strokeWidth = 0.5f, lineStyle = conic.lineStyle,
                                tStep = 1f, degenerateRay = true
                            )
                        }
                        drawConicParabolaNarys(
                            vertex = origin, focus = origin + dir,
                            canvasOffset = state.canvasOffset, scale = state.scale,
                            color = drawColor.runtimeDrawColor(), strokeWidth = drawWidth,
                            lineStyle = conic.lineStyle, tStep = 1f,
                            degenerateRay = true
                        )
                    }
                    continue
                }

                // -------- NENÍ degenerace: nejdřív oblouk, jinak plná parabola --------
                val endsN = state.parabolaArcEnds[conic.id]
                if (endsN != null) {
                    val (A, B) = endsN
                    if (isSelected) {
                        drawConicParabolaNarys(
                            vertex = p1, focus = p2,
                            canvasOffset = state.canvasOffset, scale = state.scale,
                            color = Color.Gray.copy(alpha = 0.6f), strokeWidth = 0.5f, lineStyle = LineStyle.Dashed
                        )
                    }
                    drawParabolaArcNarys(
                        vertex = p1, focus = p2, A = A, B = B,
                        canvasOffset = state.canvasOffset, scale = state.scale,
                        color = drawColor.runtimeDrawColor(), strokeWidth = drawWidth, lineStyle = conic.lineStyle
                    )
                    continue
                }

                // klasicky celá parabola
                drawConicParabolaNarys(
                    vertex = p1, focus = p2,
                    canvasOffset = state.canvasOffset, scale = state.scale,
                    color = drawColor.runtimeDrawColor(), strokeWidth = drawWidth, lineStyle = conic.lineStyle
                )
            }
            else {
                // Po částech stylovaná elipsa (střídavě plná/čárkovaná)
                val segE = state.conicSegments[conic.id]
                val inpE = state.conicInputPointsNarys[conic.id]
                if (segE != null && inpE != null && inpE.third != Offset.Unspecified &&
                    !conic.isDegenerate && !segE.isEmpty()) {
                    val segments = restrictEllipseSegmentsToArc(
                        p1 = inpE.first,
                        p2 = inpE.second,
                        p3 = inpE.third,
                        segs = segE.primary,
                        arcEnds = state.ellipseArcEnds[conic.id],
                        mode = state.ellipseArcMode[conic.id] ?: ArcMode.SHORTEST
                    )
                    drawEllipseSegments(
                        inpE.first, inpE.second, inpE.third, segments,
                        project = { it.toScreenOld(state.scale, state.canvasOffset) },
                        color = drawColor.runtimeDrawColor(), strokeWidth = drawWidth, dashScale = state.scale
                    )
                    continue
                }
                val inputs = state.conicInputPointsNarys[conic.id]
                val ends   = state.ellipseArcEnds[conic.id]
                if (ends != null && inputs != null && inputs.third != Offset.Unspecified) {
                    val (p1, p2, p3) = inputs
                    val (A, B) = ends
                    val mode = state.ellipseArcMode[conic.id] ?: ArcMode.SHORTEST

                    // 🔹 1) vykresli celou elipsu slabě (jen když je vybraná)
                    if (isSelected) {
                        drawEllipseFromDiameters(
                            p1 = p1,
                            p2 = p2,
                            p3 = p3,
                            scale = state.scale,
                            canvasOffset = state.canvasOffset,
                            color = Color.Gray.copy(alpha = 0.6f), // jemně šedá
                            strokeWidth = 0.5f,                    // tenčí čára
                            lineStyle = LineStyle.Dashed           // volitelně přerušovaně
                        )}

                    // 🔹 2) vykresli oblouk v plné barvě
                    drawEllipseArcFromDiameters(
                        p1 = p1, p2 = p2, p3 = p3,
                        A = A, B = B,
                        mode = mode,
                        scale = state.scale,
                        canvasOffset = state.canvasOffset,
                        color = drawColor.runtimeDrawColor(),
                        strokeWidth = drawWidth,
                        lineStyle = conic.lineStyle
                    )

                    continue // ⬅️ teď pokračuj, aby se nekreslila elipsa podruhé normálně
                }

                // normální celý tvar, když oblouk není
                drawEllipseFromDiameters(
                    p1 = p1,
                    p2 = p2,
                    p3 = p3,
                    scale = state.scale,
                    canvasOffset = state.canvasOffset,
                    color = drawColor.runtimeDrawColor(),
                    strokeWidth = drawWidth,
                    lineStyle = conic.lineStyle
                )
            }

        }
    } // haloPass
}
