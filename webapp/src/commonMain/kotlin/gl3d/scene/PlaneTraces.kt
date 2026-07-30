package gl3d.scene

import gl3d.math.Vec3
import gl3d.render.LineBatch
import gl3d.render.LineStyle3D
import gl3d.render.ScreenProjector
import model.ProjectionMode
import model.classes.Plane3D
import model.classes.PlaneEquation
import state.MongeState
import kotlin.math.abs
import model.gl3dLineColor

/**
 * Stopy rovin – průsečnice s půdorysnou π (z = 0) a nárysnou ν (y = 0).
 *
 * Port `planeTrace*AsLine` z `opengl/model/Traces.kt` a rozdělení na plnou
 * a zeslabenou část z `drawTraceClipped3D` v `RenderScene.kt`.
 *
 * **Bokorysná stopa se nekreslí**: desktop ji zapíná jen v AXO režimu, který
 * webová verze nemá.
 */
internal fun collectPlaneTraces(
    state: MongeState,
    planes: List<Plane3D>,
    batch: LineBatch,
    projector: ScreenProjector,
    size: Float,
    labels: MutableList<Scene3DLabel>,
) {
    for (plane in planes) {
        if (!plane.show) continue
        val equation = plane.equation ?: continue

        // Značení stop je stejné jako na desktopu (`opengl/model/Traces.kt`):
        // p₁ v půdorysně, n₂ v nárysně, a horní index nese název roviny.
        // Šířka jde z roviny, ne z `plane.tracePudorys.strokeWidth`. Ten getter
        // sice na rovinu ukazuje (`parent?.strokeWidth`), ale `relinkPlaneToTraces`
        // po změně přepojí jen stopy v `state.lineTraces*`; instance uvnitř
        // `Plane3D` si drží odkaz na **předchozí** kopii roviny, takže by 3D
        // náhled navždy kreslil původní šířku.
        val width = plane.strokeWidth

        tracePudorys(equation, size)?.let { ends ->
            // Půdorysná stopa je plná tam, kde je y ≥ 0; v KOTO se nezeslabuje.
            val strongAxis = if (state.projectionMode == ProjectionMode.KOTO) null else Axis.Y
            addTrace(batch, projector, ends, plane, "p₁", strongAxis, width, labels)
        }
        if (state.projectionMode != ProjectionMode.KOTO) {
            traceNarys(equation, size)?.let { ends ->
                // Nárysná stopa je plná nad půdorysnou, tedy kde je z ≥ 0.
                addTrace(batch, projector, ends, plane, "n₂", Axis.Z, width, labels)
            }
        }
    }
}

private enum class Axis { Y, Z }

private fun Vec3.coord(axis: Axis): Float = when (axis) {
    Axis.Y -> y
    Axis.Z -> z
}

/**
 * Stopa rozdělená na plnou a zeslabenou část.
 *
 * Desktop kreslí plnou barvou jen tu část, která leží v kladné části prostoru,
 * a zbytek nechává jen naznačený (`positiveAlpha` 1,0 vs. `weakAlpha` 0,24) –
 * jinak by konstrukce pod půdorysnou a za nárysnou působila stejně platně jako
 * ta viditelná. Ořez podél záporné osy x řeší desktop jen v AXO; tady jde
 * čistě o tu sníženou viditelnost.
 *
 * @param width šířka stopy, tedy `Plane3D.strokeWidth`. Desktop má
 *   v `drawTraceClipped3D` napevno 5 px; 3D náhled tak odpovídá tomu, co je
 *   nastavené na plátně. Zeslabená část si drží desktopový poměr
 *   [WEAK_WIDTH_RATIO].
 */
private fun addTrace(
    batch: LineBatch,
    projector: ScreenProjector,
    ends: Pair<Vec3, Vec3>,
    plane: Plane3D,
    text: String,
    strongAxis: Axis?,
    width: Float,
    labels: MutableList<Scene3DLabel>,
) {
    val (a, b) = ends
    val traceColor = plane.color.gl3dLineColor()
    val strongWidth = width.coerceAtLeast(0.5f)
    val strongStyle = LineStyle3D.of(color = traceColor, width = strongWidth, alpha = STRONG_ALPHA)
    val weakStyle = LineStyle3D.of(
        color = traceColor,
        width = (strongWidth * WEAK_WIDTH_RATIO).coerceAtLeast(0.5f),
        alpha = WEAK_ALPHA,
    )

    if (strongAxis == null) {
        batch.addSegment(a, b, strongStyle, projector)
    } else {
        val ca = a.coord(strongAxis)
        val cb = b.coord(strongAxis)
        when {
            ca >= 0f && cb >= 0f -> batch.addSegment(a, b, strongStyle, projector)
            ca < 0f && cb < 0f -> batch.addSegment(a, b, weakStyle, projector)
            else -> {
                val t = (ca / (ca - cb)).coerceIn(0f, 1f)
                val crossing = a + (b - a) * t
                val strongEnd = if (ca >= 0f) a else b
                val weakEnd = if (ca >= 0f) b else a
                batch.addSegment(strongEnd, crossing, strongStyle, projector)
                batch.addSegment(crossing, weakEnd, weakStyle, projector)
            }
        }
    }

    // Popisek kousek od konce stopy, ne uprostřed – tam se kříží s ostatními
    // stopami a s osami.
    val anchor = a + (b - a) * LABEL_POSITION_ALONG
    val screen = projector.screenPoint(anchor) ?: return
    labels += Scene3DLabel(
        text = text,
        superscript = plane.name,
        x = screen.x,
        y = screen.y,
        color = traceColor,
    )
}

/** Průsečnice s půdorysnou z = 0. */
private fun tracePudorys(e: PlaneEquation, span: Float): Pair<Vec3, Vec3>? {
    if (abs(e.a) < EPS && abs(e.b) < EPS) return null
    return if (abs(e.b) >= EPS) {
        Vec3(-span, (e.a * span - e.d) / e.b, 0f) to
                Vec3(span, (-e.a * span - e.d) / e.b, 0f)
    } else {
        val x = -e.d / e.a
        Vec3(x, -span, 0f) to Vec3(x, span, 0f)
    }
}

/** Průsečnice s nárysnou y = 0. */
private fun traceNarys(e: PlaneEquation, span: Float): Pair<Vec3, Vec3>? {
    if (abs(e.a) < EPS && abs(e.c) < EPS) return null
    return if (abs(e.c) >= EPS) {
        Vec3(-span, 0f, (e.a * span - e.d) / e.c) to
                Vec3(span, 0f, (-e.a * span - e.d) / e.c)
    } else {
        val x = -e.d / e.a
        Vec3(x, 0f, -span) to Vec3(x, 0f, span)
    }
}

private const val EPS = 1e-6f

/** Průhlednosti obou částí stopy, hodnoty z `drawTraceClipped3D`. */
private const val STRONG_ALPHA = 1f
private const val WEAK_ALPHA = 0.24f

/** Poměr šířky zeslabené a plné části – desktopových 2,8 : 5 px. */
private const val WEAK_WIDTH_RATIO = 0.56f

/** Podíl délky stopy, kde sedí popisek. Uprostřed by se křížil s osami. */
private const val LABEL_POSITION_ALONG = 0.78f
