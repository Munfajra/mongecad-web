package monge.input.ConicArcs.single

import androidx.compose.ui.geometry.Offset
import serialization.commitSnapshot
import monge.input.ConicArcs.finalizeConicVisibilityIfActive
import model.ArcMode
import model.Mongeobjects
import model.XAxisDirection
import model.YAxisDirectionPlane
import monge.input.conixections.projectToCircle
import monge.input.conixections.projectToCircleNarys
import monge.input.selection.setCircleArc
import geometry.conics.ConicType
import geometry.conics.classifyConicFromMatrix
import state.MongeState
import ui.mongeui.toolbar.setProjectionPhase
import utils.getLogicalCursor
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sqrt
import kotlin.math.sin

data class EllipseBasis(
    val center: Offset,
    val a: Float,
    val b: Float,
    val uN: Offset,
    val vN: Offset,
    val c: Float,            // uN·vN
    val oneMinusC2: Float    // 1 - c^2
)

fun ellipseBasisFromDiameters(p1: Offset, p2: Offset, p3: Offset): EllipseBasis {
    val center = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)

    val u = (Offset(p2.x - p1.x, p2.y - p1.y)) / 2f
    val a = u.getDistance().coerceAtLeast(1e-6f)

    val mirrorP3 = Offset(2 * center.x - p3.x, 2 * center.y - p3.y)
    val v = (Offset(p3.x - mirrorP3.x, p3.y - mirrorP3.y)) / 2f
    val b = v.getDistance().coerceAtLeast(1e-6f)

    val uN = Offset(u.x / a, u.y / a)
    val vN = Offset(v.x / b, v.y / b)

    val c = (uN.x * vN.x + uN.y * vN.y)
    val oneMinusC2 = (1f - c * c).coerceAtLeast(1e-6f)
    return EllipseBasis(center, a, b, uN, vN, c, oneMinusC2)
}

/** Vrátí (t, P*), kde P* je promítnutý bod na elipse a t jeho parametr. */
fun ellipseParamAndProjection(b: EllipseBasis, pt: Offset): Pair<Float, Offset> {
    val r  = pt - b.center
    val ru = r.x * b.uN.x + r.y * b.uN.y
    val rv = r.x * b.vN.x + r.y * b.vN.y

    // "raw" cos/sin z lineárního řešení
    val cosRaw = (ru - b.c * rv) / (b.a * b.oneMinusC2)
    val sinRaw = (rv - b.c * ru) / (b.b * b.oneMinusC2)

    // normalizuj přes úhel → zajistí přesně on-curve bod
    val t = atan2(sinRaw, cosRaw)
    val on = b.center + Offset(
        b.a * cos(t) * b.uN.x + b.b * sin(t) * b.vN.x,
        b.a * cos(t) * b.uN.y + b.b * sin(t) * b.vN.y
    )
    return t to on
}


fun projectToEllipseFromDiameters(p1: Offset, p2: Offset, p3: Offset, pt: Offset): Offset {
    val center = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
    val u = (p2 - p1) / 2f
    val v = p3 - center
    val radius1 = u.getDistance()
    val radius2 = v.getDistance()
    val cross = u.x * v.y - u.y * v.x
    if (radius1 < 1e-5f || radius2 < 1e-5f || kotlin.math.abs(cross) < 1e-5f * radius1 * radius2) {
        val dirRaw = if (radius1 >= radius2) u else v
        val dirLen = dirRaw.getDistance()
        if (dirLen < 1e-5f) return center
        val dir = dirRaw / dirLen
        val radius = sqrt(radius1 * radius1 + radius2 * radius2)
        val a = center - dir * radius
        val b = center + dir * radius
        val ab = b - a
        val len2 = ab.x * ab.x + ab.y * ab.y
        val ap = pt - a
        val t = ((ap.x * ab.x + ap.y * ab.y) / len2).coerceIn(0f, 1f)
        return a + ab * t
    }

    val basis = ellipseBasisFromDiameters(p1, p2, p3)
    return ellipseParamAndProjection(basis, pt).second
}

fun MongeState.setEllipseArc(conicId: String, a: Offset, b: Offset, mode: ArcMode = ArcMode.SHORTEST) {
    ellipseArcEnds[conicId] = a to b
    ellipseArcMode[conicId] = mode
    triggerRedraw++
}

fun arcEllipsePudorys(state: MongeState, snappedPointLogical: Offset?, cursor: Offset) {
    val logical = getLogicalCursor(
        snappedPointLogical,
        cursor,
        state.canvasOffset,
        state.scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
        state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    )

    when (state.projectionPhase) {
        "pudorys_cir_start" -> {
            val circle = state.selectedCirclesPudorys.lastOrNull()
            if (circle == null) {
                println("⚠️ Vyber nejdřív kružnici.")
                return
            }
            state.activeConicIdForArc = circle.id
            setProjectionPhase("pudorys_circle_arc_hold", state)
        }

        "pudorys_circle_arc_hold" -> {
            val conicId = state.activeConicIdForArc
            if (conicId == null) {
                println("⚠️ Není uzamčena žádná kružnice.")
                setProjectionPhase("pudorys_start", state)
                return
            }

            val circle = state.circlesPudorys.find { it.id == conicId }
            if (circle == null) {
                println("⚠️ Zvolená kružnice už není k dispozici.")
                setProjectionPhase("pudorys_start", state)
                state.activeConicIdForArc = null
                return
            }

            state.pendingArcA = projectToCircle(circle, logical)
            println("✅ A-bod kružnicového oblouku uložen.")

            setProjectionPhase("pudorys_circle_arc", state)
        }

        "pudorys_circle_arc" -> {
            val conicId = state.activeConicIdForArc
            if (conicId == null) {
                setProjectionPhase("pudorys_start", state)
                println("⚠️ Nebyla uzamčena kružnice pro oblouk, vracím se na začátek.")
                return
            }

            val circle = state.circlesPudorys.find { it.id == conicId }
            if (circle == null) {
                println("⚠️ Kružnice už není k dispozici.")
                setProjectionPhase("pudorys_start", state)
                state.activeConicIdForArc = null
                state.pendingArcA = null
                return
            }

            val aProj = state.pendingArcA ?: run {
                println("⚠️ Chybí A-bod, vracím se na začátek.")
                setProjectionPhase("pudorys_start", state)
                state.activeConicIdForArc = null
                return
            }

            val bProj = projectToCircle(circle, logical)
            val mode = state.activeArcMode
                ?: state.circleArcMode[conicId]
                ?: ArcMode.SHORTEST

            state.setCircleArc(conicId, aProj, bProj, mode)
            println("🎯 Kružnicový oblouk nastaven pro circle=$conicId, mode=$mode.")
            commitSnapshot(state)

            state.pendingArcA = null
            state.activeConicIdForArc = null
            state.activeArcMode = null
            state.drawobjects = Mongeobjects.NONE
            setProjectionPhase("pudorys_start", state)
        }

        "pudorys_elip_start" -> {
            val conic = state.selectedConicsPudorys.lastOrNull()
            if (conic == null) {
                println("⚠️ Vyber nejdřív elipsu.")
                return
            }
            state.activeConicIdForArc = conic.id
            setProjectionPhase("pudorys_ellipse_arc_hold", state)}
        "pudorys_ellipse_arc_hold"->{
            val conic = state.activeConicIdForArc
            val inputs = state.conicInputPointsPudorys[conic]
            if (inputs == null || inputs.third == Offset.Unspecified || state.hyperbolaInputsPudorys.containsKey(conic)) {
                println("⚠️ Zvolená kuželosečka není elipsa.")
                return
            }
            val (p1, p2, p3) = inputs
            // ulož A promítnuté na elipsu
            state.pendingArcA = projectToEllipseFromDiameters(p1, p2, p3, logical)
            println("✅ A-bod elipsového oblouku uložen.")

            setProjectionPhase("pudorys_ellipse_arc", state)
        }

        "pudorys_ellipse_arc" -> {
            val conicId = state.activeConicIdForArc
            if (conicId == null) {
                // ochrana: vrať se na začátek
                setProjectionPhase("pudorys_start", state)
                println("⚠️ Nebyla uzamčena elipsa pro oblouk, vracím se na začátek.")
                return
            }
            val inputs = state.conicInputPointsPudorys[conicId]
            if (inputs == null || inputs.third == Offset.Unspecified) {
                println("⚠️ Elipsa už není k dispozici.")
                return
            }
            val (p1, p2, p3) = inputs

            val Aproj = state.pendingArcA ?: run {
                println("⚠️ Chybí A-bod, vracím se na začátek.")
                setProjectionPhase("pudorys_start", state)
                return
            }
            val Bproj = projectToEllipseFromDiameters(p1, p2, p3, logical)
            val mode = state.ellipseArcMode[conicId] ?: ArcMode.SHORTEST

            state.setEllipseArc(conicId, Aproj, Bproj, mode)
            println("🎯 Elipsový oblouk nastaven pro conic=$conicId, mode=$mode.")
            state.finalizeConicVisibilityIfActive(conicId)
            commitSnapshot(state)
            // úklid a návrat do klidu
            state.pendingArcA = null
            state.activeConicIdForArc = null
            state.drawobjects = Mongeobjects.NONE
            setProjectionPhase("pudorys_start", state)

        }

        else -> {
        }
    }
}


fun arcEllipseNarys(state: MongeState, snappedPointLogical: Offset?, cursor: Offset) {
    val logical = getLogicalCursor(
        snappedPointLogical,
        cursor,
        state.canvasOffset,
        state.scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
        state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    )

    when (state.projectionPhase) {
        "narys_cir_start" -> {
            val circle = state.selectedCirclesNarys.lastOrNull()
            if (circle == null) {
                println("⚠️ Vyber nejdřív kružnici.")
                return
            }
            state.activeConicIdForArc = circle.id
            setProjectionPhase("narys_circle_arc_hold", state)
        }

        "narys_circle_arc_hold" -> {
            val conicId = state.activeConicIdForArc
            if (conicId == null) {
                println("⚠️ Není uzamčena žádná kružnice.")
                setProjectionPhase("narys_start", state)
                return
            }

            val circle = state.circlesNarys.find { it.id == conicId }
            if (circle == null) {
                println("⚠️ Zvolená kružnice už není k dispozici.")
                setProjectionPhase("narys_start", state)
                state.activeConicIdForArc = null
                return
            }

            state.pendingArcA = projectToCircleNarys(circle, logical)
            println("✅ A-bod kružnicového oblouku uložen.")

            setProjectionPhase("narys_circle_arc", state)
        }

        "narys_circle_arc" -> {
            val conicId = state.activeConicIdForArc
            if (conicId == null) {
                setProjectionPhase("narys_start", state)
                println("⚠️ Nebyla uzamčena kružnice pro oblouk, vracím se na začátek.")
                return
            }

            val circle = state.circlesNarys.find { it.id == conicId }
            if (circle == null) {
                println("⚠️ Kružnice už není k dispozici.")
                setProjectionPhase("narys_start", state)
                state.activeConicIdForArc = null
                state.pendingArcA = null
                return
            }

            val aProj = state.pendingArcA ?: run {
                println("⚠️ Chybí A-bod, vracím se na začátek.")
                setProjectionPhase("narys_start", state)
                state.activeConicIdForArc = null
                return
            }

            val bProj = projectToCircleNarys(circle, logical)
            val mode = state.activeArcMode
                ?: state.circleArcMode[conicId]
                ?: ArcMode.SHORTEST

            state.setCircleArc(conicId, aProj, bProj, mode)
            println("🎯 Kružnicový oblouk nastaven pro circle=$conicId, mode=$mode.")
            commitSnapshot(state)

            state.pendingArcA = null
            state.activeConicIdForArc = null
            state.activeArcMode = null
            state.drawobjects = Mongeobjects.NONE
            setProjectionPhase("narys_start", state)
        }
        "narys_elip_start" -> {
            val conic = state.selectedConicsNarys.lastOrNull()
                ?: run { println("⚠️ Vyber nejdřív elipsu v nárysu."); return }

            val inputs = state.conicInputPointsNarys[conic.id]
            if (inputs == null || inputs.third == Offset.Unspecified || state.hyperbolaInputsNarys.containsKey(conic.id)) {
                println("⚠️ Zvolená kuželosečka v nárysu není elipsa."); return
            }


            // zamkni elipsu pro druhou fázi
            state.activeConicIdForArc = conic.id
            setProjectionPhase("narys_ellipse_arc_hold", state)}
        "narys_ellipse_arc_hold"->{
            val conic = state.activeConicIdForArc
            val inputs = state.conicInputPointsNarys[conic]
            if (inputs == null || inputs.third == Offset.Unspecified || state.hyperbolaInputsNarys.containsKey(conic)) {
                println("⚠️ Zvolená kuželosečka v nárysu není elipsa."); return
            }
            val (p1, p2, p3) = inputs
            // A-bod promítnout na elipsu
            state.pendingArcA = projectToEllipseFromDiameters(p1, p2, p3, logical)
            println("✅ [N] A-bod elipsového oblouku uložen.")

            setProjectionPhase("narys_ellipse_arc", state)
        }

        "narys_ellipse_arc" -> {
            val conicId = state.activeConicIdForArc
                ?: run { setProjectionPhase("narys_start", state); println("⚠️ [N] Chybí aktivní elipsa."); return }

            val inputs = state.conicInputPointsNarys[conicId]
            if (inputs == null || inputs.third == Offset.Unspecified) {
                println("⚠️ [N] Elipsa už není k dispozici."); return
            }
            val (p1, p2, p3) = inputs

            val Aproj = state.pendingArcA ?: run {
                setProjectionPhase("narys_start", state); println("⚠️ [N] Chybí A-bod."); return
            }
            val Bproj = projectToEllipseFromDiameters(p1, p2, p3, logical)
            val mode  = state.ellipseArcMode[conicId] ?: ArcMode.SHORTEST

            state.setEllipseArc(conicId, Aproj, Bproj, mode)
            println("🎯 [N] Elipsový oblouk nastaven pro conic=$conicId, mode=$mode.")
            state.finalizeConicVisibilityIfActive(conicId)
            commitSnapshot(state)
            // úklid
            state.pendingArcA = null
            state.activeConicIdForArc = null
            state.drawobjects = Mongeobjects.NONE
            setProjectionPhase("narys_start", state) // nebo tvůj default

        }

        else -> Unit
    }
}
fun decideConicPudorys(state: MongeState) {
    val conic = state.selectedConicsPudorys.lastOrNull()
    val circle = state.selectedCirclesPudorys.lastOrNull()
    if (conic == null && circle == null) { println("⚠️ Vyber nejdřív kuželosečku/kružnici"); return }
    if (conic != null) {
        val parent3D = conic.parent
        if ((parent3D != null && classifyConicFromMatrix(parent3D.matrix) == ConicType.ELLIPSE) &&
            state.drawobjects == Mongeobjects.CONICARCAS
        ) {
            // 🚀 3D elipsa – budeme zadávat oblouk přes půdorys (A,B), ale výsledek je 3D + nárys
            state.activeParentConic3DIdForEllipseArc = parent3D.id
            setProjectionPhase("pudorys_elip3d_start", state)
            println("ℹ️ 3D elipsa: vybíráš A,B na půdorysu (oblouk se propíše i do nárysu a 3D)")
            return
        }
        if ((parent3D != null && classifyConicFromMatrix(parent3D.matrix) == ConicType.PARABOLA) &&
            state.drawobjects == Mongeobjects.CONICARCAS
        ) {
            state.activeConicIdForArc = parent3D.id
            setProjectionPhase("pudorys_par3d_start", state)
            println("ℹ️ 3D parabola: vybíráš A,B na půdorysu (oblouk se propíše i do nárysu a 3D)")
            return
        }
        val is3DHyperbola = parent3D != null &&
                (classifyConicFromMatrix(parent3D.matrix) == ConicType.HYPERBOLA ||
                        state.hyperbolaInputsPudorys.containsKey(conic.id))
        if (is3DHyperbola &&
            state.drawobjects == Mongeobjects.CONICARCAS
        ) {
            state.activeConicIdForArc = parent3D!!.id
            setProjectionPhase("pudorys_hyp3d_start", state)
            println("ℹ️ 3D parabola: vybíráš A,B na půdorysu (oblouk se propíše i do nárysu a 3D)")
            return
        }
        // 🔽 2D větve tak, jak je už máš
        val inputs = state.conicInputPointsPudorys[conic.id]
        val isHyper = state.hyperbolaInputsPudorys.containsKey(conic.id)
        val isParab = (inputs == null || inputs.third == Offset.Unspecified) && !isHyper
        if (state.drawobjects == Mongeobjects.CONICARC) {
            when {
                isHyper -> {
                    setProjectionPhase("pudorys_hyp_start", state); println("ℹ️ Vybrána hyperbola")
                }

                isParab -> {
                    setProjectionPhase("pudorys_par_start", state); println("ℹ️ Vybrána parabola")
                }

                else -> {
                    setProjectionPhase("pudorys_elip_start", state); println("ℹ️ Vybrána elipsa (2D)")
                }
            }
        }
    }
    if (circle!=null){
        setProjectionPhase("pudorys_cir_start",state);println("ℹ️ Vybrána kružnice")
    }
}

fun decideConicNarys(state: MongeState) {
    val conic = state.selectedConicsNarys.lastOrNull()
    val circle = state.selectedCirclesNarys.lastOrNull()
    if (conic == null&& circle == null) { println("⚠️ Vyber nejdřív kuželosečku"); return }
    if (conic != null ){
    val parent3D = conic.parent

    if ((parent3D != null && classifyConicFromMatrix(parent3D.matrix) == ConicType.ELLIPSE) &&
        state.drawobjects==Mongeobjects.CONICARCAS) {
        state.activeParentConic3DIdForEllipseArc = parent3D.id
        setProjectionPhase("narys_elip3d_start", state)
        println("ℹ️ 3D elipsa: vybíráš A,B na nárysu (oblouk se propíše i do půdorysu a 3D)")
        return
    }
    if ((parent3D != null && classifyConicFromMatrix(parent3D.matrix) == ConicType.PARABOLA) &&
        state.drawobjects==Mongeobjects.CONICARCAS){
        state.activeConicIdForArc = parent3D.id
        setProjectionPhase("narys_par3d_start",state)
        println("ℹ️ 3D parabola: vybíráš A,B na nárysu (oblouk se propíše i do půdorysu a 3D)")
        return
    }
    val is3DHyperbola = parent3D != null &&
            (classifyConicFromMatrix(parent3D.matrix) == ConicType.HYPERBOLA ||
                    state.hyperbolaInputsNarys.containsKey(conic.id))
    if (is3DHyperbola &&
        state.drawobjects==Mongeobjects.CONICARCAS){
        state.activeConicIdForArc = parent3D!!.id
        setProjectionPhase("narys_hyp3d_start",state)
        println("ℹ️ 3D parabola: vybíráš A,B na nárysu (oblouk se propíše i do půdorysu a 3D)")
        return
    }
    // 🔽 2D větve tak, jak je už máš
    val inputs = state.conicInputPointsNarys[conic.id]
    val isHyper = state.hyperbolaInputsNarys.containsKey(conic.id)
    val isParab = (inputs == null || inputs.third == Offset.Unspecified) && !isHyper
    if (state.drawobjects== Mongeobjects.CONICARC){
    when {
        isHyper -> { setProjectionPhase("narys_hyp_start", state); println("ℹ️ Vybrána hyperbola") }
        isParab -> { setProjectionPhase("narys_par_start", state); println("ℹ️ Vybrána parabola") }
        else    -> { setProjectionPhase("narys_elip_start", state); println("ℹ️ Vybrána elipsa (2D)") }
    }}}
    if (circle != null) {
        setProjectionPhase("narys_cir_start", state); println("ℹ️ Vybrána kružnice")
    }
}
