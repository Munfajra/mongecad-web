package state.snapMonge

import androidx.compose.ui.geometry.Offset
import model.classes.AidPointLogical
import model.classes.AxoOverlayPoint
import model.classes.Point3DAxo
import kotlin.math.pow

fun state.MongeState.findNearestAidPointLogical(
    cursorLogical: Offset,
    snapRadiusLogical: Float = 6f
): AidPointLogical? =
    aidPointsLogical
        .minByOrNull { (it.x - cursorLogical.x).pow(2) + (it.y - cursorLogical.y).pow(2) }
        ?.takeIf { p ->
            val dist2 = (p.x - cursorLogical.x).pow(2) + (p.y - cursorLogical.y).pow(2)
            dist2 <= snapRadiusLogical * snapRadiusLogical
        }
fun state.MongeState.findNearestAOPoint(
    cursorLogical: Offset,
    snapRadiusLogical: Float = 6f
): AxoOverlayPoint? =
    axoOverlayPoints
        .minByOrNull {
            val dx = it.positionLogical.x - cursorLogical.x
            val dy = it.positionLogical.y - cursorLogical.y
            dx * dx + dy * dy
        }
        ?.takeIf { p ->
            val dx = p.positionLogical.x - cursorLogical.x
            val dy = p.positionLogical.y - cursorLogical.y
            dx * dx + dy * dy <= snapRadiusLogical * snapRadiusLogical
        }
fun state.MongeState.findNearestAxoPoint(
    cursorLogical: Offset,
    snapRadiusLogical: Float = 6f
): Point3DAxo? =
    pointsAxo
        .minByOrNull {
            val dx = it.x - cursorLogical.x
            val dy = it.y - cursorLogical.y
            dx * dx + dy * dy
        }
        ?.takeIf { p ->
            val dx = p.x - cursorLogical.x
            val dy = p.y - cursorLogical.y
            dx * dx + dy * dy <= snapRadiusLogical * snapRadiusLogical
        }