package monge.input.tools

import androidx.compose.ui.geometry.Offset
import serialization.commitSnapshot
import model.*
import model.classes.AidPointLogical
import monge.input.helix.applyTransferredHelixPitchAngle
import state.MongeState
import ui.mongeui.toolbar.setProjectionPhase
import ui.resetStavu
import utils.allocIndex
import utils.angleBetween
import utils.getLogicalCursor
import kotlin.math.cos
import kotlin.math.sin

fun handleClickAnglePlacement(
    snappedPointLogical: Offset?,
    state: MongeState,
    cursorWorld: Offset
) {
    if (state.drawobjects != Mongeobjects.GETANGLE) return

    val logical = getLogicalCursor(
        snappedPointLogical,
        cursorWorld,
        state.canvasOffset,
        state.scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
        state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    )

    when (state.projectionPhase) {
        "pudorys_start","narys_start" -> {
            state.pendingPoint1 = logical
            setProjectionPhase("angle_point2", state)
            println("🟢 Vrchol úhlu uložen: $logical")
        }

        "angle_point2" -> {
            state.pendingPoint2 = logical
            setProjectionPhase("angle_point3", state)
            println("🟡 První rameno úhlu uloženo: $logical")
        }

        "angle_point3" -> {
            val v = state.pendingPoint1 ?: return
            val a = state.pendingPoint2 ?: return
            val b = logical

            val vec1 = a - v
            val vec2 = b - v

            val len1 = vec1.getDistance()
            val len2 = vec2.getDistance()
            if (len1 < 1e-6f || len2 < 1e-6f) {
                println("⚠️ Ramena jsou příliš krátká")
                return
            }

            val angle = angleBetween(vec1, vec2)
            if (state.helixPitchAngleTransferActive) {
                applyTransferredHelixPitchAngle(state, angle)
                println("🔵 Přenesený úhel použit jako stoupání šroubovice: $angle rad")
            } else {
                state.pendingAngle = angle
                setProjectionPhase("angle_new_vertex", state)
                println("🔵 Druhé rameno uloženo, úhel = $angle rad")
            }
        }

        "angle_new_vertex" -> {
            state.pendingPoint3 = logical
            setProjectionPhase("angle_new_ray", state)
            println("🟣 Nový vrchol úhlu: $logical")
        }

        "angle_new_ray" -> {
            val v    = state.pendingPoint3 ?: return   // nový vrchol
            val a    = logical                         // klik – první bod nového ramene
            val dir1 = a - v
            val len  = dir1.getDistance()
            if (len < 1e-6f) {
                println("⚠️ První rameno příliš krátké")
                return
            }

            /* -- otočení o uložený úhel -- */
            val angle = state.pendingAngle ?: return
            val adjusted = if (state.arc.arcDirectionClockwise) angle else -angle
            val sinA = sin(adjusted)
            val cosA = cos(adjusted)

            val dir2 = Offset(
                cosA * dir1.x - sinA * dir1.y,
                sinA * dir1.x + cosA * dir1.y
            ).let { d -> d / d.getDistance() * len }   // normalizace & délka = len

            val b = v + dir2                           // KONEČNÝ bod nového ramene

            /* ---------- vložení pomocného bodu ---------- */

            state.aidPointsLogical += AidPointLogical(
                x = b.x,
                y = b.y,
                color = state.currentHelpLineStyleSettings.color, creationIndex = allocIndex(state)
            )
            println("✅ Pomocný bod vložen do LOGICAL: $b")
            commitSnapshot(state)
            /* --- reset stavu --- */
            state.pendingPoint1 = null
            state.pendingPoint2 = null
            state.pendingPoint3 = null
            state.pendingAngle  = null
            resetStavu(state)

            state.projectionPhase = when (state.mongeMode) {
                DrawingModeMonge.NARYS   -> "narys_start"
                DrawingModeMonge.PUDORYS -> "pudorys_start"
            }
        }

    }
}
fun logicalToScreen(
    logical: Offset,
    canvasOffset: Offset,
    scale: Float
): Offset = Offset(
    x = logical.x * scale + canvasOffset.x,
    y = logical.y * scale + canvasOffset.y
)
