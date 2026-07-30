package monge.input.selection

import model.DrawingModeMonge
import model.Mongeobjects
import model.Offset3D
import model.ProjectionMode
import model.ProjectionType
import model.classes.Plane3D
import model.classes.PlaneEquation
import monge.input.planeobjects.planelift.finalizePendingConicLiftWithPlane
import monge.input.quadrics.cylindricalsurface.handleCylindricalToolClick
import monge.input.intersections.handleIntersectionClick
import monge.input.ruledsurface.handleRuledSurfaceSelection
import state.MongeState
import ui.mongeui.toolbar.setProjectionPhase
import kotlin.math.abs
import kotlin.math.sqrt

private fun dot3(a: Offset3D, b: Offset3D) = a.x*b.x + a.y*b.y + a.z*b.z
private fun normalize3(a: Offset3D): Offset3D {
    val n = sqrt(a.x*a.x + a.y*a.y + a.z*a.z)
    return if (n > 1e-12f) Offset3D(a.x/n, a.y/n, a.z/n) else Offset3D(0f,0f,0f)
}
fun isPlaneDegenerateForView(eq: PlaneEquation, mode: DrawingModeMonge): Boolean {
    val n = normalize3(Offset3D(eq.a, eq.b, eq.c))
    val vProj = if (mode == DrawingModeMonge.NARYS) Offset3D(0f, 1f, 0f) else Offset3D(0f, 0f, 1f)
    // Degenerace = pohled leží v rovině (vProj ⟂ n) → elipsa se zhroutí do úsečky
    return abs(dot3(n, vProj)) < 1e-4f
}
fun toggleSelectionPlane(plane: Plane3D, state: MongeState) {
    val id = plane.id
    if (state.selectedPlanes.any { it.id == id }) {
        state.selectedPlanes.removeAll { it.id == id }
        println("🟤 Odznačena rovina: ${plane.name}")
    } else {
        if (state.projectionMode == ProjectionMode.AXO &&
            state.drawobjects == Mongeobjects.REGULAR_POLYGON_IN_PLANE &&
            state.projekcnityp == ProjectionType.ASSOCIATED
        ) {
            state.selectedPlanes.clear()
            state.selectedPlanes.add(plane)
            state.selectedPlaneForCircle = plane
            setProjectionPhase("rp_center_axo", state)
            state.triggerRedraw++
            state.skipNextClick = true
            println("🟢 Označena rovina: ${plane.name}")
            return
        }
        if(((state.drawobjects in listOf(
                Mongeobjects.CIRCLE,
                Mongeobjects.PARABOLA,
                Mongeobjects.ELLIPSE,
                Mongeobjects.HYPERBOLA,
                Mongeobjects.REGULAR_POLYGON_IN_PLANE
            ) && state.projekcnityp == ProjectionType.ASSOCIATED))|| state.drawobjects == Mongeobjects.PLANE_LIFT)
        {
            if (
                state.drawobjects in listOf(
                    Mongeobjects.CIRCLE,
                    Mongeobjects.PARABOLA,
                    Mongeobjects.ELLIPSE,
                    Mongeobjects.HYPERBOLA,
                    Mongeobjects.REGULAR_POLYGON_IN_PLANE
                ) && state.projekcnityp == ProjectionType.ASSOCIATED && state.projectionPhase in listOf("pudorys_start","narys_start")
            ) {
                val eq = plane.equation
                if (eq == null) {
                    println("❌ Vybraná rovina nemá rovnici – nelze zkontrolovat orientaci.")
                    return
                }

                if (isPlaneDegenerateForView(eq, state.mongeMode)) {
                    println("⚠️ Rovina ${plane.name} je v aktuálním pohledu kolmá (degenerace) – výběr zamítnut.")
                    return
                }

                state.selectedPlaneForCircle = plane
                when (state.drawobjects) {
                Mongeobjects.CIRCLE -> when (state.mongeMode) {
                    DrawingModeMonge.NARYS -> setProjectionPhase("narys_ready", state)
                    DrawingModeMonge.PUDORYS -> setProjectionPhase("pudorys_ready", state)
                }
                Mongeobjects.ELLIPSE -> when (state.mongeMode) {
                    DrawingModeMonge.NARYS -> setProjectionPhase( "ellipse_plane_point1_narys", state)
                    DrawingModeMonge.PUDORYS -> setProjectionPhase( "ellipse_plane_point1", state)
                }
                Mongeobjects.PARABOLA -> when (state.mongeMode) {
                    DrawingModeMonge.NARYS ->  setProjectionPhase("narys_vertex", state)
                    DrawingModeMonge.PUDORYS -> setProjectionPhase("pudorys_vertex", state)
                }
                Mongeobjects.HYPERBOLA -> when (state.mongeMode) {
                    DrawingModeMonge.NARYS -> setProjectionPhase("narys_asymptote1", state)
                    DrawingModeMonge.PUDORYS -> setProjectionPhase("pudorys_asymptote1", state)
                }
                Mongeobjects.REGULAR_POLYGON_IN_PLANE -> when (state.mongeMode) {
                DrawingModeMonge.NARYS -> setProjectionPhase("rp_center_nar", state)
                DrawingModeMonge.PUDORYS -> setProjectionPhase(  "rp_center_pud" ,state)
            }
                else -> {}
            }
            state.triggerRedraw++
            state.skipNextClick = true
         }
            if (state.drawobjects== Mongeobjects.PLANE_LIFT)
            {
                val eq = plane.equation
                if (eq == null) {
                    println("❌ Vybraná rovina nemá rovnici – nelze zkontrolovat orientaci.")
                    return
                }

                if (isPlaneDegenerateForView(eq, state.mongeMode)) {
                    println("⚠️ Rovina ${plane.name} je v aktuálním pohledu kolmá (degenerace) – výběr zamítnut.")
                    return
                }
                setProjectionPhase("ready_planelift", state)
                state.selectedPlaneForCircle = plane
                state.triggerRedraw++
                state.skipNextClick = true
            }
        }
        else{
            state.selectedPlanes.add(plane)
        }
       // klidně nová instance – porovnáváme podle id
        println("🟢 Označena rovina: ${plane.name}")
        if (finalizePendingConicLiftWithPlane(state, plane)) {
            return
        }
    }
    handleRuledSurfaceSelection(state)
    handleCylindricalToolClick(state, axoConstruction = state.projectionMode == ProjectionMode.AXO)
    // Průnik se posouvá stejně jako konstrukce výše. Bez toho by rovina označená
    // odjinud než z plátna (ObjectList, řídicí rovina pod přímkovou plochou) do
    // průniku nespadla. Opakované volání je neškodné – `intersectionPicks` se
    // deduplikuje podle id a po odpálení výpočtu drží `intersectionComputing`
    // další vstupy stranou.
    handleIntersectionClick(state)
}
