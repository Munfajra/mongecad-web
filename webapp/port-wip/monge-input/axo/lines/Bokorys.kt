package monge.input.axo.lines

import androidx.compose.ui.geometry.Offset
import model.ConstructionModifier
import model.Mongeobjects
import model.ProjectionType
import model.axo.AxoMode
import model.classes.Line3DProjectionBokorys
import model.classes.Point3DBokorys
import monge.input.axo.AxoRenderBasis
import monge.input.axo.getLogicalCursorAxo
import state.MongeState
import ui.mongeui.toolbar.setProjectionPhase
import ui.mongeui.toolbar.updateConstructionInfo
import utils.allocIndex
import kotlin.math.abs

fun handleSingleLineBokorysAxo(
    snappedPointLogical: Offset?,
    state: MongeState
) {
    val logical = getLogicalCursorAxo(
        snapped = snappedPointLogical,
        cursor = state.cursorPosition,
        canvasOffset = state.canvasOffset,
        scale = state.scale,
        canvasWidth = state.canvasWidth,
        canvasHeight = state.canvasHeight,
        flipX = false,
        flipY = false,
        mode = AxoMode.AXO_BOKORYS,
        axoModel = state.activeAxoModel
    ) ?: return
    val logicalY = logical.x
    val logicalZ = logical.y

    if (state.constructionModifier == ConstructionModifier.PARALLEL) {
        handleParallelLineConstructionBokorysAxo(logical, state)
        return
    }
    if (state.constructionModifier == ConstructionModifier.ORTHOGONAL) {
        handleOrthogonalLineConstructionBokorysAxo(logical, state)
        return
    }

    if (state.drawobjects == Mongeobjects.LINES && state.constructionModifier == ConstructionModifier.NONE) {
        val existing = state.pointsBokorys.find {
            abs(it.y - logicalY) < 0.01f && abs(it.z - logicalZ) < 0.01f
        }

        val newPoint = existing ?: Point3DBokorys(
            y = logicalY,
            z = logicalZ,
            name = state.inputName.ifBlank { "" },
            parent = null
        )

        if (state.lineStartPoint3DBokorys == null) {
            state.lineStartPoint3DBokorys = newPoint
            println("Začátek přímky (bokorys): $newPoint")
            updateConstructionInfo(state)
        } else {

            val start = state.lineStartPoint3DBokorys!!
            val direction = Offset(newPoint.y - start.y, newPoint.z - start.z)

            if (direction.getDistance() != 0f) {
                val tempLine = Line3DProjectionBokorys(start, direction, creationIndex = allocIndex(state))

                state.inputName = ""
                state.isNameConfirmed = false
                setProjectionPhase("single_bokorys_line", state)
                state.rename.lineBeingRenamedBokorys = tempLine

                println("Přímka přidána z $start se směrem $direction")
            }

            state.lineStartPoint3DBokorys = null
        }
    }
}
fun handleOrthogonalLineConstructionBokorysAxo(logical: Offset, state: MongeState) {
    when (state.drawobjects) {
        Mongeobjects.LINES -> {
            if (!hasOverlayReference(state)) {
                pickOverlayReferenceFromCurrentHover(state)
                if (hasOverlayReference(state)) return
                return
            }

            val direction = resolveBokorysDirectionAxo(state,true)?: return
            // ⬇ Konstrukce přímky (proběhne hned po nastavení)
            val basePoint = Point3DBokorys(logical.x, logical.y, name = "")


                val newLine = Line3DProjectionBokorys(basePoint, direction, creationIndex = allocIndex(state))

                state.rename.lineBeingRenamedBokorys = newLine
                state.inputName = ""
                state.isNameConfirmed = false
                setProjectionPhase("single_bokorys_line", state)

                val sourceName = state.selectedLineForParallelBokorys?.name ?: state.selectedSegmentForParallelBokorys?.name ?: ""
                println("🟢 Vytvořena přímka kolmá na $sourceName, skrze bod $basePoint")

                // ⬇ Reset stavu
                state.selectedLineForParallelBokorys = null
                state.selectedSegmentForParallelBokorys = null
                state.selectedLinesBokorys.clear()
                state.selectedSegmentsBokorys.clear()
                state.constructionModifier = ConstructionModifier.NONE
        }
        else -> {
            println("⚠️ Konstrukce kolmice není pro tento režim podporována.")

        }
    }
}
fun handleParallelLineConstructionBokorysAxo(logical: Offset, state: MongeState) {
    when (state.drawobjects) {
        Mongeobjects.LINES -> {
            if (!hasOverlayReference(state)) {
                pickOverlayReferenceFromCurrentHover(state)
                if (hasOverlayReference(state)) return
                return
            }

            val direction =resolveBokorysDirectionAxo(state,false)?: return
                // ⬇ Konstrukce přímky (proběhne hned po nastavení)
                val basePoint = Point3DBokorys(logical.x, logical.y, name = "")

                val newLine = Line3DProjectionBokorys(basePoint, direction, creationIndex = allocIndex(state))

                state.rename.lineBeingRenamedBokorys = newLine
                state.inputName = ""
                state.isNameConfirmed = false
                setProjectionPhase("single_bokorys_line", state)

                println("🟢 Vytvořena přímka rovnoběžná s ${state.selectedLineForParallelBokorys?.name}/${state.selectedSegmentForParallelBokorys?.name}, skrze bod $basePoint")

                // ⬇ Reset stavu
                state.selectedLineForParallelBokorys = null
                state.selectedSegmentForParallelBokorys = null
                state.selectedLinesBokorys.clear()
                state.constructionModifier = ConstructionModifier.NONE

        }

        else -> {
            println("⚠️ Konstrukce rovnoběžky není pro tento režim podporována.")
            println("DEBUG 🧠 Aktuální projectionPhase: ${state.projectionPhase}")

        }
    }
}
fun resolveBokorysDirectionAxo(
    state: MongeState,
    wantPerpendicular: Boolean = false
): Offset? {
    val basis = state.basis ?: return null

    fun fromBokorys(dir: Offset): Offset {
        return if (wantPerpendicular) perpendicular2D(dir) else dir
    }

    fun fromOverlay(dir: Offset): Offset? {
        val overlayDir =
            if (wantPerpendicular) perpendicular2D(dir) else dir

        return projectAxoOverlayToBokorysDirection(overlayDir, basis)
    }

    val dirProjected =
        when {
            state.selectedLineForParallelBokorys != null -> {
                fromBokorys(state.selectedLineForParallelBokorys!!.direction)
            }

            state.selectedSegmentForParallelBokorys!= null -> {
                val seg = state.selectedSegmentForParallelBokorys!!
                fromBokorys(
                    Offset(seg.end.y, seg.end.z) - Offset(seg.start.y, seg.start.z)
                )
            }

            state.selectedSegmentForParallelAxo != null -> {
                val seg = state.selectedSegmentForParallelAxo!!
                val dir = Offset(seg.end.x, seg.end.y) - Offset(seg.start.x, seg.start.y)
                fromOverlay(dir)
            }

            state.selectedSegmentForParallelAO != null -> {
                val seg = state.selectedSegmentForParallelAO!!
                val dir = Offset(seg.end.x, seg.end.y) - Offset(seg.start.x, seg.start.y)
                fromOverlay(dir)
            }

            state.selectedLineForParallelAxo != null -> {
                fromOverlay(state.selectedLineForParallelAxo!!.dir)
            }

            state.selectedLineForParallelAO != null -> {
                fromOverlay(state.selectedLineForParallelAO!!.dir)
            }

            else -> null
        }

    return dirProjected?.normalizedOrNull()
}
fun projectAxoOverlayToBokorysDirection(
    overlay: Offset,
    basis: AxoRenderBasis
): Offset? {

    val ey = basis.ey
    val ez = basis.ez

    val det = ey.x * ez.y - ey.y * ez.x
    if (kotlin.math.abs(det) < 1e-6f) return null // degenerovaná báze

    val y = (overlay.x * ez.y - overlay.y * ez.x) / det
    val z = (ey.x * overlay.y - ey.y * overlay.x) / det

    return Offset(y, z)
}