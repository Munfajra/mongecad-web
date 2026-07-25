package monge.input.axo.lines.linecomplete

import utils.withSuffixOnce
import utils.System
import dialogs.nameInput.withSuffixOnce
import androidx.compose.ui.geometry.Offset
import serialization.commitSnapshot
import model.ConstructionModifier
import model.Offset3D
import model.Point3D
import model.axo.AxoMode
import model.classes.*
import monge.input.axo.getLogicalCursorAxo
import monge.input.axo.lines.clearOverlayLineReferenceSelection
import monge.input.axo.lines.hasOverlayReference
import monge.input.axo.lines.normalizedOrNull
import monge.input.axo.lines.perpendicular2D
import monge.input.axo.lines.pickOverlayReferenceFromCurrentHover
import monge.input.axo.lines.resolveBokorysDirectionAxo
import monge.input.axo.lines.resolveNarysDirectionAxo
import monge.input.axo.lines.resolveOverlayReferenceDirectionAxo
import monge.input.axo.lines.resolvePudorysDirectionAxo
import monge.input.axo.points.screenToAxoOverlayLocal
import state.MongeState
import ui.mongeui.toolbar.setProjectionPhase
import ui.resetStavu
import utils.allocIndex
import kotlin.math.abs

data class PendingAxoLineCompletion(
    val firstProjectionId: String,
    val firstKind: ProjectionKind,
    val secondKind: ProjectionKind? = null
)
enum class ProjectionKind {
    PUDORYS,
    NARYS,
    BOKORYS,
    AXO
}
fun projectionKindFromAxoMode(mode: AxoMode): ProjectionKind? {
    return when (mode) {
        AxoMode.AXO_PUDORYS -> ProjectionKind.PUDORYS
        AxoMode.AXO_NARYS -> ProjectionKind.NARYS
        AxoMode.AXO_BOKORYS -> ProjectionKind.BOKORYS
        AxoMode.NORMAL_2D -> ProjectionKind.AXO
    }
}
fun axoModeFromProjectionKind(kind: ProjectionKind): AxoMode {
    return when (kind) {
        ProjectionKind.PUDORYS -> AxoMode.AXO_PUDORYS
        ProjectionKind.NARYS -> AxoMode.AXO_NARYS
        ProjectionKind.BOKORYS -> AxoMode.AXO_BOKORYS
        ProjectionKind.AXO -> AxoMode.NORMAL_2D
    }
}

// Výchozí cíl pro doplnění průmětu: z půdorysu/nárysu/bokorysu se přepneme do axonometrie,
// z axonometrie do půdorysu. Uživatel si pak může kdykoliv přepnout jinam sám (kromě
// původního průmětu, který je v přepínači zakázaný - viz disabledMode v AxoPlaneSwitchButton).
fun defaultAxoModeForCompletionTarget(firstKind: ProjectionKind): AxoMode {
    return if (firstKind == ProjectionKind.AXO) AxoMode.AXO_PUDORYS else AxoMode.NORMAL_2D
}
fun completeAxoLineFromPudorys(
    state: MongeState,
    line: Line3DProjectionPudorys
) {
    clearOverlayLineReferenceSelection(state)
    clearAxoLineCompletionSelection(state)

    state.pendingAxoLineCompletion = PendingAxoLineCompletion(
        firstProjectionId = line.id,
        firstKind = ProjectionKind.PUDORYS,
        secondKind = null
    )

    state.completingLineSecondStart = null
    state.axoMode = defaultAxoModeForCompletionTarget(ProjectionKind.PUDORYS)

    setProjectionPhase("axo_complete_line_waiting_for_second_projection", state)
}
fun completeAxoLineFromNarys(
    state: MongeState,
    line: Line3DProjectionNarys
) {
    clearOverlayLineReferenceSelection(state)
    clearAxoLineCompletionSelection(state)

    state.pendingAxoLineCompletion = PendingAxoLineCompletion(
        firstProjectionId = line.id,
        firstKind = ProjectionKind.NARYS,
        secondKind = null
    )

    state.completingLineSecondStart = null
    state.axoMode = defaultAxoModeForCompletionTarget(ProjectionKind.NARYS)

    setProjectionPhase("axo_complete_line_waiting_for_second_projection", state)
}
fun completeAxoLineFromBokorys(
    state: MongeState,
    line: Line3DProjectionBokorys
) {
    clearOverlayLineReferenceSelection(state)
    clearAxoLineCompletionSelection(state)

    state.pendingAxoLineCompletion = PendingAxoLineCompletion(
        firstProjectionId = line.id,
        firstKind = ProjectionKind.BOKORYS,
        secondKind = null
    )

    state.completingLineSecondStart = null
    state.axoMode = defaultAxoModeForCompletionTarget(ProjectionKind.BOKORYS)

    setProjectionPhase("axo_complete_line_waiting_for_second_projection", state)
}
fun completeAxoLineFromAxo(
    state: MongeState,
    line: Line3DProjectionAxo
) {
    clearOverlayLineReferenceSelection(state)
    clearAxoLineCompletionSelection(state)

    state.pendingAxoLineCompletion = PendingAxoLineCompletion(
        firstProjectionId = line.id,
        firstKind = ProjectionKind.AXO,
        secondKind = null
    )

    state.completingLineSecondStart = null
    state.axoMode = defaultAxoModeForCompletionTarget(ProjectionKind.AXO)

    setProjectionPhase("axo_complete_line_waiting_for_second_projection", state)
}
fun clearAxoLineCompletionSelection(state: MongeState) {
    state.selectedLinesPudorys.clear()
    state.selectedLinesNarys.clear()
    state.selectedLinesBokorys.clear()
    state.selectedLinesAxo.clear()
    state.selectedLines3D.clear()

    state.selectedLineIdsPudorys.clear()
    state.selectedLineIdsNarys.clear()
    state.selectedLineIdsBokorys.clear()
    state.selectedLineIdsAxo.clear()
    state.selectedAOLineIds.clear()

    state.selectedSegmentsPudorys.clear()
    state.selectedSegmentsNarys.clear()
    state.selectedSegmentsBokorys.clear()
    state.selectedSegmentsAxo.clear()
    state.selectedSegments3D.clear()
    state.selectedAOSegIds.clear()
}
fun handleAxoLineCompletionClick(
    state: MongeState
) {
    if (
        state.projectionPhase != "axo_complete_line_waiting_for_second_projection" &&
        state.projectionPhase != "axo_complete_line_second_projection"
    ) return

    val pending = state.pendingAxoLineCompletion ?: return

    val currentKind = projectionKindFromAxoMode(state.axoMode)

    if (currentKind == null) {
        state.consInfo.value = "Přepni se do půdorysu, nárysu, bokorysu nebo axo průmětu."
        return
    }

    if (currentKind == pending.firstKind) {
        state.consInfo.value = "Přepni se do jiného průmětu než je původní."
        return
    }

    val effectiveSecondKind =
        pending.secondKind ?: currentKind

    if (pending.secondKind == null) {
        state.pendingAxoLineCompletion = pending.copy(
            secondKind = effectiveSecondKind
        )

        setProjectionPhase("axo_complete_line_second_projection", state)
    }

    if (effectiveSecondKind != currentKind) {
        state.consInfo.value = "Dokončuješ přímku v jiném průmětu, než ve kterém jsi začal."
        return
    }

    if (state.reusingExistingProjection) {
        state.consInfo.value = "Vyber existující druhý průmět přímky."
        return
    }

    val logical = if (currentKind == ProjectionKind.AXO) {
        val basis = state.basis?:return
        state.snappedPointLogical
            ?: screenToAxoOverlayLocal(state.cursorPosition, state, basis)
    } else {getLogicalCursorAxo(
        snapped = state.snappedPointLogical,
        cursor = state.cursorPosition,
        canvasOffset = state.canvasOffset,
        scale = state.scale,
        canvasWidth = state.canvasWidth,
        canvasHeight = state.canvasHeight,
        flipX = false,
        flipY = false,
        mode = state.axoMode,
        axoModel = state.activeAxoModel
    ) }?: return

    if (
        state.constructionModifier == ConstructionModifier.PARALLEL ||
        state.constructionModifier == ConstructionModifier.ORTHOGONAL
    ) {
        if (!hasOverlayReference(state)) {
            pickOverlayReferenceFromCurrentHover(state)
            if (hasOverlayReference(state)) return
            return
        }

        val direction = resolveCompletionSecondDirection(
            state = state,
            kind = currentKind,
            modifier = state.constructionModifier
        ) ?: return

        finishAxoLineCompletionWithSecondProjection(
            state = state,
            pending = state.pendingAxoLineCompletion ?: return,
            secondStart = logical,
            secondDirection = direction
        )

        if (state.showLineCompletionErrorDialog) return

        clearOverlayLineReferenceSelection(state)
        state.constructionModifier = ConstructionModifier.NONE
        return
    }

    val start = state.completingLineSecondStart

    if (start == null) {
        state.completingLineSecondStart = logical
        state.deferSelectionUntil = System.currentTimeMillis() + 100

        state.consInfo.value = "Zadej druhý bod druhého průmětu přímky."
        return
    }

    val end = logical
    val direction = end - start

    if (direction.getDistance() < 0.0001f) {
        state.consInfo.value = "Druhý bod je příliš blízko prvnímu."
        return
    }

    finishAxoLineCompletionWithSecondProjection(
        state = state,
        pending = state.pendingAxoLineCompletion ?: return,
        secondStart = start,
        secondDirection = direction
    )
}
fun tryHandleAxoLineCompletionSelection(
    state: MongeState,
    clicked: Any
): Boolean {
    val pending = state.pendingAxoLineCompletion ?: return false

    val clickedKind = when (clicked) {
        is Line3DProjectionPudorys -> ProjectionKind.PUDORYS
        is Line3DProjectionNarys -> ProjectionKind.NARYS
        is Line3DProjectionBokorys -> ProjectionKind.BOKORYS
        is Line3DProjectionAxo -> ProjectionKind.AXO
        else -> return false
    }

    if (clickedKind == pending.firstKind) {
        state.consInfo.value = "Vyber druhý průmět v jiném pohledu."
        return true
    }

    when (pending.firstKind) {
        ProjectionKind.PUDORYS -> {
            val first = state.lines3DPudorys.firstOrNull { it.id == pending.firstProjectionId }
                ?: run {
                    cancelAxoLineCompletion(state)
                    return true
                }

            when (val second = clicked) {
                is Line3DProjectionNarys -> createCompletedLine3DFromPudorysAndNarys(state, first, second)
                is Line3DProjectionBokorys -> createCompletedLine3DFromPudorysAndBokorys(state, first, second)
                is Line3DProjectionAxo -> createCompletedLine3DFromPudorysAndAxo(
                    state = state,
                    pudorys = first,
                    axo = second,
                    axoWasExisting = true
                )
                else -> return true
            }
        }

        ProjectionKind.NARYS -> {
            val first = state.lines3DNarys.firstOrNull { it.id == pending.firstProjectionId }
                ?: run {
                    cancelAxoLineCompletion(state)
                    return true
                }

            when (val second = clicked) {
                is Line3DProjectionPudorys -> createCompletedLine3DFromPudorysAndNarys(state, second, first)
                is Line3DProjectionBokorys -> createCompletedLine3DFromNarysAndBokorys(state, first, second)
                is Line3DProjectionAxo -> createCompletedLine3DFromNarysAndAxo(
                    state = state,
                    narys = first,
                    axo = second,
                    axoWasExisting = true
                )
                else -> return true
            }
        }

        ProjectionKind.BOKORYS -> {
            val first = state.lines3DBokorys.firstOrNull { it.id == pending.firstProjectionId }
                ?: run {
                    cancelAxoLineCompletion(state)
                    return true
                }

            when (val second = clicked) {
                is Line3DProjectionPudorys -> createCompletedLine3DFromPudorysAndBokorys(state, second, first)
                is Line3DProjectionNarys -> createCompletedLine3DFromNarysAndBokorys(state, second, first)
                is Line3DProjectionAxo -> createCompletedLine3DFromBokorysAndAxo(
                    state = state,
                    bokorys = first,
                    axo = second,
                    axoWasExisting = true
                )
                else -> return true
            }
        }

        ProjectionKind.AXO -> {
            val first = state.lines3DAxo.firstOrNull { it.id == pending.firstProjectionId }
                ?: run {
                    cancelAxoLineCompletion(state)
                    return true
                }

            when (val second = clicked) {
                is Line3DProjectionPudorys -> createCompletedLine3DFromPudorysAndAxo(
                    state = state,
                    pudorys = second,
                    axo = first,
                    axoWasExisting = true
                )
                is Line3DProjectionNarys -> createCompletedLine3DFromNarysAndAxo(
                    state = state,
                    narys = second,
                    axo = first,
                    axoWasExisting = true
                )
                is Line3DProjectionBokorys -> createCompletedLine3DFromBokorysAndAxo(
                    state = state,
                    bokorys = second,
                    axo = first,
                    axoWasExisting = true
                )
                else -> return true
            }
        }
    }

    return true
}
fun resolveCompletionSecondDirection(
    state: MongeState,
    kind: ProjectionKind,
    modifier: ConstructionModifier
): Offset? {
    val wantPerpendicular = modifier == ConstructionModifier.ORTHOGONAL

    return when (kind) {
        ProjectionKind.PUDORYS -> resolvePudorysDirectionAxo(state, wantPerpendicular)
        ProjectionKind.NARYS -> resolveNarysDirectionAxo(state, wantPerpendicular)
        ProjectionKind.BOKORYS -> resolveBokorysDirectionAxo(state, wantPerpendicular)
        ProjectionKind.AXO -> {
            val baseDirection = resolveOverlayReferenceDirectionAxo(state) ?: return null

            when (modifier) {
                ConstructionModifier.PARALLEL -> baseDirection
                ConstructionModifier.ORTHOGONAL -> perpendicular2D(baseDirection).normalizedOrNull()
                else -> null
            }
        }
    }
}
fun finishAxoLineCompletionWithSecondProjection(
    state: MongeState,
    pending: PendingAxoLineCompletion,
    secondStart: Offset,
    secondDirection: Offset
) {
    val secondKind = pending.secondKind ?: return

    when (pending.firstKind) {
        ProjectionKind.PUDORYS -> {
            val first = state.lines3DPudorys
                .firstOrNull { it.id == pending.firstProjectionId }
                ?: return cancelAxoLineCompletion(state)

            when (secondKind) {
                ProjectionKind.NARYS -> {
                    val second = createNarysLineProjectionFromAxoInput(
                        start = secondStart,
                        direction = secondDirection,
                        state = state
                    )

                    createCompletedLine3DFromPudorysAndNarys(
                        state = state,
                        pudorys = first,
                        narys = second
                    )
                }

                ProjectionKind.BOKORYS -> {
                    val second = createBokorysLineProjectionFromAxoInput(
                        start = secondStart,
                        direction = secondDirection,
                        state = state
                    )

                    createCompletedLine3DFromPudorysAndBokorys(
                        state = state,
                        pudorys = first,
                        bokorys = second
                    )
                }

                ProjectionKind.PUDORYS -> return
                ProjectionKind.AXO -> {
                    val second = createAxoLineProjectionFromAxoInput(
                        start = secondStart,
                        direction = secondDirection,
                        state = state
                    )

                    createCompletedLine3DFromPudorysAndAxo(
                        state = state,
                        pudorys = first,
                        axo = second,
                        axoWasExisting = false
                    )
                }
            }
        }

        ProjectionKind.NARYS -> {
            val first = state.lines3DNarys
                .firstOrNull { it.id == pending.firstProjectionId }
                ?: return cancelAxoLineCompletion(state)

            when (secondKind) {
                ProjectionKind.PUDORYS -> {
                    val second = createPudorysLineProjectionFromAxoInput(
                        start = secondStart,
                        direction = secondDirection,
                        state = state
                    )

                    createCompletedLine3DFromPudorysAndNarys(
                        state = state,
                        pudorys = second,
                        narys = first
                    )
                }

                ProjectionKind.BOKORYS -> {
                    val second = createBokorysLineProjectionFromAxoInput(
                        start = secondStart,
                        direction = secondDirection,
                        state = state
                    )

                    createCompletedLine3DFromNarysAndBokorys(
                        state = state,
                        narys = first,
                        bokorys = second
                    )
                }

                ProjectionKind.NARYS -> return
                ProjectionKind.AXO -> {
                    val second = createAxoLineProjectionFromAxoInput(
                        start = secondStart,
                        direction = secondDirection,
                        state = state
                    )

                    createCompletedLine3DFromNarysAndAxo(
                        state = state,
                        narys = first,
                        axo = second,
                        axoWasExisting = false
                    )
                }
            }
        }

        ProjectionKind.BOKORYS -> {
            val first = state.lines3DBokorys
                .firstOrNull { it.id == pending.firstProjectionId }
                ?: return cancelAxoLineCompletion(state)

            when (secondKind) {
                ProjectionKind.PUDORYS -> {
                    val second = createPudorysLineProjectionFromAxoInput(
                        start = secondStart,
                        direction = secondDirection,
                        state = state
                    )

                    createCompletedLine3DFromPudorysAndBokorys(
                        state = state,
                        pudorys = second,
                        bokorys = first
                    )
                }

                ProjectionKind.NARYS -> {
                    val second = createNarysLineProjectionFromAxoInput(
                        start = secondStart,
                        direction = secondDirection,
                        state = state
                    )

                    createCompletedLine3DFromNarysAndBokorys(
                        state = state,
                        narys = second,
                        bokorys = first
                    )
                }

                ProjectionKind.BOKORYS -> return
                ProjectionKind.AXO -> {
                    val second = createAxoLineProjectionFromAxoInput(
                        start = secondStart,
                        direction = secondDirection,
                        state = state
                    )

                    createCompletedLine3DFromBokorysAndAxo(
                        state = state,
                        bokorys = first,
                        axo = second,
                        axoWasExisting = false
                    )
                }
            }
        }

        ProjectionKind.AXO -> {
            val first = state.lines3DAxo
                .firstOrNull { it.id == pending.firstProjectionId }
                ?: return cancelAxoLineCompletion(state)

            val firstAxo = first

            when (secondKind) {
                ProjectionKind.PUDORYS -> {
                    val second = createPudorysLineProjectionFromAxoInput(
                        start = secondStart,
                        direction = secondDirection,
                        state = state
                    )

                    createCompletedLine3DFromPudorysAndAxo(
                        state = state,
                        pudorys = second,
                        axo = firstAxo,
                        axoWasExisting = true
                    )
                }

                ProjectionKind.NARYS -> {
                    val second = createNarysLineProjectionFromAxoInput(
                        start = secondStart,
                        direction = secondDirection,
                        state = state
                    )

                    createCompletedLine3DFromNarysAndAxo(
                        state = state,
                        narys = second,
                        axo = firstAxo,
                        axoWasExisting = true
                    )
                }

                ProjectionKind.BOKORYS -> {
                    val second = createBokorysLineProjectionFromAxoInput(
                        start = secondStart,
                        direction = secondDirection,
                        state = state
                    )

                    createCompletedLine3DFromBokorysAndAxo(
                        state = state,
                        bokorys = second,
                        axo = firstAxo,
                        axoWasExisting = true
                    )
                }

                ProjectionKind.AXO -> return
            }
        }
    }
}
fun createPudorysLineProjectionFromAxoInput(
    start: Offset,
    direction: Offset,
    state: MongeState
): Line3DProjectionPudorys {
    val point = Point3DPudorys(
        x = start.x,
        y = start.y,
        name = "₁",
        parent = null,
        creationIndex = allocIndex(state)
    )

    return Line3DProjectionPudorys(
        point = point,
        direction = direction,
        creationIndex = allocIndex(state)
    )
}
fun createNarysLineProjectionFromAxoInput(
    start: Offset,
    direction: Offset,
    state: MongeState
): Line3DProjectionNarys {
    val point = Point3DNarys(
        x = start.x,
        z = start.y,
        name = "₂",
        parent = null,
        creationIndex = allocIndex(state)
    )

    return Line3DProjectionNarys(
        point = point,
        direction = direction,
        creationIndex = allocIndex(state)
    )
}
fun createBokorysLineProjectionFromAxoInput(
    start: Offset,
    direction: Offset,
    state: MongeState
): Line3DProjectionBokorys {
    val point = Point3DBokorys(
        y = start.x,
        z = start.y,
        name = "₃",
        parent = null,
        creationIndex = allocIndex(state)
    )

    return Line3DProjectionBokorys(
        point = point,
        direction = direction,
        creationIndex = allocIndex(state)
    )
}
private const val EPS_LINE = 0.0001f

private fun directionComponentIsZero(component: Float, direction: Offset): Boolean {
    val length = direction.getDistance()
    return length < EPS_LINE || abs(component) < EPS_LINE * length
}

fun yOnPudorysAtX(
    line: Line3DProjectionPudorys,
    x: Float
): Float {
    val p = line.point
    val d = line.direction

    if (directionComponentIsZero(d.x, d)) {
        return p.y
    }

    val t = (x - p.x) / d.x
    return p.y + t * d.y
}

fun zOnNarysAtX(
    line: Line3DProjectionNarys,
    x: Float
): Float {
    val p = line.point
    val d = line.direction

    if (directionComponentIsZero(d.x, d)) {
        return p.z
    }

    val t = (x - p.x) / d.x
    return p.z + t * d.y
}

fun xOnNarysAtZ(
    line: Line3DProjectionNarys,
    z: Float
): Float {
    val p = line.point
    val d = line.direction

    if (directionComponentIsZero(d.y, d)) {
        return p.x
    }

    val t = (z - p.z) / d.y
    return p.x + t * d.x
}

fun yOnBokorysAtZ(
    line: Line3DProjectionBokorys,
    z: Float
): Float {
    val p = line.point
    val d = line.direction

    if (directionComponentIsZero(d.y, d)) {
        return p.y
    }

    val t = (z - p.z) / d.y
    return p.y + t * d.x
}

fun xOnPudorysAtY(
    line: Line3DProjectionPudorys,
    y: Float
): Float {
    val p = line.point
    val d = line.direction

    if (directionComponentIsZero(d.y, d)) {
        return p.x
    }

    val t = (y - p.y) / d.y
    return p.x + t * d.x
}

fun zOnBokorysAtY(
    line: Line3DProjectionBokorys,
    y: Float
): Float {
    val p = line.point
    val d = line.direction

    if (directionComponentIsZero(d.x, d)) {
        return p.z
    }

    val t = (y - p.y) / d.x
    return p.z + t * d.y
}
fun alignDirectionByCommonComponent(
    firstCommon: Float,
    firstDirection: Offset,
    secondDirection: Offset,
    secondCommon: Float
): Offset {
    if (
        directionComponentIsZero(firstCommon, firstDirection) ||
        directionComponentIsZero(secondCommon, secondDirection)
    ) {
        return secondDirection
    }

    return if (firstCommon * secondCommon < 0f) {
        -secondDirection
    } else {
        secondDirection
    }
}
fun commonProjectionComponentIsCompatible(
    firstCommon: Float,
    firstDirection: Offset,
    secondCommon: Float,
    secondDirection: Offset,
    firstPointCommon: Float,
    secondPointCommon: Float
): Boolean {
    val firstZero = directionComponentIsZero(firstCommon, firstDirection)
    val secondZero = directionComponentIsZero(secondCommon, secondDirection)

    if (firstZero != secondZero) return false

    return if (firstZero) {
        abs(firstPointCommon - secondPointCommon) < EPS_LINE
    } else {
        true
    }
}
fun showImpossibleLineCompletionDialog(state: MongeState) {
    state.lineCompletionErrorMessage =
        "Zadané průměty nemohou být průměty jedné prostorové přímky. Směry se neshodují ve společné souřadnici průmětů."
    state.showLineCompletionErrorDialog = true
}
fun commonProjectionComponentIsAmbiguous(
    firstCommon: Float,
    firstDirection: Offset,
    secondCommon: Float,
    secondDirection: Offset,
    firstPointCommon: Float,
    secondPointCommon: Float
): Boolean {
    return directionComponentIsZero(firstCommon, firstDirection) &&
            directionComponentIsZero(secondCommon, secondDirection) &&
            abs(firstPointCommon - secondPointCommon) < EPS_LINE
}
fun showAmbiguousLineCompletionDialog(state: MongeState) {
    state.lineCompletionErrorMessage =
        "Tyto průměty neurčují jednoznačně prostorovou přímku. Leží nad stejnou společnou souřadnicí, a proto určují rovinu, ve které může ležet nekonečně mnoho přímek."
    state.showLineCompletionErrorDialog = true
}
fun createCompletedLine3DFromPudorysAndNarys(
    state: MongeState,
    pudorys: Line3DProjectionPudorys,
    narys: Line3DProjectionNarys
) {
    if (commonProjectionComponentIsAmbiguous(
            firstCommon = pudorys.direction.x,
            firstDirection = pudorys.direction,
            secondCommon = narys.direction.x,
            secondDirection = narys.direction,
            firstPointCommon = pudorys.point.x,
            secondPointCommon = narys.point.x
        )
    ) {
        showAmbiguousLineCompletionDialog(state)
        return
    }

    if (!commonProjectionComponentIsCompatible(
            firstCommon = pudorys.direction.x,
            firstDirection = pudorys.direction,
            secondCommon = narys.direction.x,
            secondDirection = narys.direction,
            firstPointCommon = pudorys.point.x,
            secondPointCommon = narys.point.x
        )
    ) {
        showImpossibleLineCompletionDialog(state)
        return
    }

    val styleSource = originalLineProjection(
        state = state,
        pudorys = pudorys,
        narys = narys
    )

    val rawName =
        baseNameOf(styleSource)
            ?: baseNameOf(pudorys)
            ?: baseNameOf(narys)
            ?: "p"

    val pDir = pudorys.direction
    val nDirAligned = alignDirectionByCommonComponent(
        firstCommon = pDir.x,
        firstDirection = pDir,
        secondDirection = narys.direction,
        secondCommon = narys.direction.x
    )

    val baseX = pudorys.point.x
    val baseY = pudorys.point.y
    val baseZ = zOnNarysAtX(narys, baseX)

    val direction3D =
        if (
            !directionComponentIsZero(pDir.x, pDir) &&
            !directionComponentIsZero(nDirAligned.x, nDirAligned)
        ) {
            Offset3D(
                x = 1f,
                y = pDir.y / pDir.x,
                z = nDirAligned.y / nDirAligned.x
            )
        } else {
            Offset3D(
                x = 0f,
                y = pDir.y,
                z = nDirAligned.y
            )
        }

    val line3D = Line3D(
        start = Point3D(
            x = baseX,
            y = baseY,
            z = baseZ,
            name = "",
            creationIndex = allocIndex(state)
        ),
        direction = direction3D,
        name = rawName,
        color = styleSource.color,
        lineStyle = styleSource.lineStyle,
        strokeWidth = styleSource.strokeWidth,
        superscript = styleSource.superscript,
        creationIndex = allocIndex(state)
    ).withInheritedTrim(state, pudorys = pudorys, narys = narys)

    val newPudorys = pudorys.copy(
        parent = line3D,
        localName = rawName.withSuffixOnce("₁")
    )

    val newNarys = narys.copy(
        parent = line3D,
        localName = rawName.withSuffixOnce("₂")
    )

    val newBokorys = createBokorysProjectionFromLine3D(
        state = state,
        line3D = line3D,
        name = rawName.withSuffixOnce("₃")
    )
    val newAxo = createAxoProjectionFromLine3D(
        state = state,
        line3D = line3D,
        name = rawName
    )

    applyCompletedLineProjectionVisibility(
        visibleKinds = completedVisibleKinds(state, ProjectionKind.PUDORYS, ProjectionKind.NARYS),
        pudorys = newPudorys,
        narys = newNarys,
        bokorys = newBokorys,
        axo = newAxo
    )

    replacePudorysLineKeepingPosition(state, pudorys, newPudorys)
    replaceNarysLineKeepingPosition(state, narys, newNarys)
    addBokorysProjectionOrPoint(state, newBokorys)
    addAxoProjectionOrPoint(state, newAxo)
    state.lines3D.add(line3D)

    finishAxoLineCompletionState(state)
}
fun createCompletedLine3DFromPudorysAndBokorys(
    state: MongeState,
    pudorys: Line3DProjectionPudorys,
    bokorys: Line3DProjectionBokorys
) {
    if (commonProjectionComponentIsAmbiguous(
            firstCommon = pudorys.direction.y,
            firstDirection = pudorys.direction,
            secondCommon = bokorys.direction.x,
            secondDirection = bokorys.direction,
            firstPointCommon = pudorys.point.y,
            secondPointCommon = bokorys.point.y
        )
    ) {
        showAmbiguousLineCompletionDialog(state)
        return
    }

    if (!commonProjectionComponentIsCompatible(
            firstCommon = pudorys.direction.y,
            firstDirection = pudorys.direction,
            secondCommon = bokorys.direction.x,
            secondDirection = bokorys.direction,
            firstPointCommon = pudorys.point.y,
            secondPointCommon = bokorys.point.y
        )
    ) {
        showImpossibleLineCompletionDialog(state)
        return
    }

    val styleSource = originalLineProjection(
        state = state,
        pudorys = pudorys,
        bokorys = bokorys
    )

    val rawName =
        baseNameOf(styleSource)
            ?: baseNameOf(pudorys)
            ?: baseNameOf(bokorys)
            ?: "p"

    val pDir = pudorys.direction
    val bDirAligned = alignDirectionByCommonComponent(
        firstCommon = pDir.y,
        firstDirection = pDir,
        secondDirection = bokorys.direction,
        secondCommon = bokorys.direction.x
    )

    val baseY = pudorys.point.y
    val baseX = pudorys.point.x
    val baseZ = zOnBokorysAtY(bokorys, baseY)

    val direction3D =
        if (
            !directionComponentIsZero(pDir.y, pDir) &&
            !directionComponentIsZero(bDirAligned.x, bDirAligned)
        ) {
            Offset3D(
                x = pDir.x / pDir.y,
                y = 1f,
                z = bDirAligned.y / bDirAligned.x
            )
        } else {
            Offset3D(
                x = pDir.x,
                y = 0f,
                z = bDirAligned.y
            )
        }

    val line3D = Line3D(
        start = Point3D(
            x = baseX,
            y = baseY,
            z = baseZ,
            name = "",
            creationIndex = allocIndex(state)
        ),
        direction = direction3D,
        name = rawName,
        color = styleSource.color,
        lineStyle = styleSource.lineStyle,
        strokeWidth = styleSource.strokeWidth,
        superscript = styleSource.superscript,
        creationIndex = allocIndex(state)
    ).withInheritedTrim(state, pudorys = pudorys, bokorys = bokorys)

    val newPudorys = pudorys.copy(
        parent = line3D,
        localName = rawName.withSuffixOnce("₁")
    )

    val newBokorys = bokorys.copy(
        parent = line3D,
        localName = rawName.withSuffixOnce("₃")
    )

    val newNarys = createNarysProjectionFromLine3D(
        state = state,
        line3D = line3D,
        name = rawName.withSuffixOnce("₂")
    )
    val newAxo = createAxoProjectionFromLine3D(
        state = state,
        line3D = line3D,
        name = rawName
    )

    applyCompletedLineProjectionVisibility(
        visibleKinds = completedVisibleKinds(state, ProjectionKind.PUDORYS, ProjectionKind.BOKORYS),
        pudorys = newPudorys,
        narys = newNarys,
        bokorys = newBokorys,
        axo = newAxo
    )

    replacePudorysLineKeepingPosition(state, pudorys, newPudorys)
    replaceBokorysLineKeepingPosition(state, bokorys, newBokorys)
    addNarysProjectionOrPoint(state, newNarys)
    addAxoProjectionOrPoint(state, newAxo)
    state.lines3D.add(line3D)

    finishAxoLineCompletionState(state)
}
fun createCompletedLine3DFromNarysAndBokorys(
    state: MongeState,
    narys: Line3DProjectionNarys,
    bokorys: Line3DProjectionBokorys
) {
    if (commonProjectionComponentIsAmbiguous(
            firstCommon = narys.direction.y,
            firstDirection = narys.direction,
            secondCommon = bokorys.direction.y,
            secondDirection = bokorys.direction,
            firstPointCommon = narys.point.z,
            secondPointCommon = bokorys.point.z
        )
    ) {
        showAmbiguousLineCompletionDialog(state)
        return
    }

    if (!commonProjectionComponentIsCompatible(
            firstCommon = narys.direction.y,
            firstDirection = narys.direction,
            secondCommon = bokorys.direction.y,
            secondDirection = bokorys.direction,
            firstPointCommon = narys.point.z,
            secondPointCommon = bokorys.point.z
        )
    ) {
        showImpossibleLineCompletionDialog(state)
        return
    }

    val styleSource = originalLineProjection(
        state = state,
        narys = narys,
        bokorys = bokorys
    )

    val rawName =
        baseNameOf(styleSource)
            ?: baseNameOf(narys)
            ?: baseNameOf(bokorys)
            ?: "p"

    val nDir = narys.direction
    val bDirAligned = alignDirectionByCommonComponent(
        firstCommon = nDir.y,
        firstDirection = nDir,
        secondDirection = bokorys.direction,
        secondCommon = bokorys.direction.y
    )

    val baseZ = narys.point.z
    val baseX = narys.point.x
    val baseY = yOnBokorysAtZ(bokorys, baseZ)

    val direction3D =
        if (
            !directionComponentIsZero(nDir.y, nDir) &&
            !directionComponentIsZero(bDirAligned.y, bDirAligned)
        ) {
            Offset3D(
                x = nDir.x / nDir.y,
                y = bDirAligned.x / bDirAligned.y,
                z = 1f
            )
        } else {
            Offset3D(
                x = nDir.x,
                y = bDirAligned.x,
                z = 0f
            )
        }

    val line3D = Line3D(
        start = Point3D(
            x = baseX,
            y = baseY,
            z = baseZ,
            name = "",
            creationIndex = allocIndex(state)
        ),
        direction = direction3D,
        name = rawName,
        color = styleSource.color,
        lineStyle = styleSource.lineStyle,
        strokeWidth = styleSource.strokeWidth,
        superscript = styleSource.superscript,
        creationIndex = allocIndex(state)
    ).withInheritedTrim(state, narys = narys, bokorys = bokorys)

    val newNarys = narys.copy(
        parent = line3D,
        localName = rawName.withSuffixOnce("₂")
    )

    val newBokorys = bokorys.copy(
        parent = line3D,
        localName = rawName.withSuffixOnce("₃")
    )

    val newPudorys = createPudorysProjectionFromLine3D(
        state = state,
        line3D = line3D,
        name = rawName.withSuffixOnce("₁")
    )
    val newAxo = createAxoProjectionFromLine3D(
        state = state,
        line3D = line3D,
        name = rawName
    )

    applyCompletedLineProjectionVisibility(
        visibleKinds = completedVisibleKinds(state, ProjectionKind.NARYS, ProjectionKind.BOKORYS),
        pudorys = newPudorys,
        narys = newNarys,
        bokorys = newBokorys,
        axo = newAxo
    )

    replaceNarysLineKeepingPosition(state, narys, newNarys)
    replaceBokorysLineKeepingPosition(state, bokorys, newBokorys)
    addPudorysProjectionOrPoint(state, newPudorys)
    addAxoProjectionOrPoint(state, newAxo)
    state.lines3D.add(line3D)

    finishAxoLineCompletionState(state)
}
fun createPudorysProjectionFromLine3D(
    state: MongeState,
    line3D: Line3D,
    name: String
): Line3DProjectionPudorys {
    val p = Point3DPudorys(
        x = line3D.start.x,
        y = line3D.start.y,
        name = name,
        parent = null,
        creationIndex = allocIndex(state)
    )

    return Line3DProjectionPudorys(
        point = p,
        direction = Offset(
            line3D.direction.x,
            line3D.direction.y
        ),
        localName = name,
        parent = line3D,
        creationIndex = allocIndex(state)
    )
}
fun createNarysProjectionFromLine3D(
    state: MongeState,
    line3D: Line3D,
    name: String
): Line3DProjectionNarys {
    val p = Point3DNarys(
        x = line3D.start.x,
        z = line3D.start.z,
        name = name,
        parent = null,
        creationIndex = allocIndex(state)
    )

    return Line3DProjectionNarys(
        point = p,
        direction = Offset(
            line3D.direction.x,
            line3D.direction.z
        ),
        localName = name,
        parent = line3D,
        creationIndex = allocIndex(state)
    )
}
fun createBokorysProjectionFromLine3D(
    state: MongeState,
    line3D: Line3D,
    name: String
): Line3DProjectionBokorys {
    val p = Point3DBokorys(
        y = line3D.start.y,
        z = line3D.start.z,
        name = name,
        parent = null,
        creationIndex = allocIndex(state)
    )

    return Line3DProjectionBokorys(
        point = p,
        direction = Offset(
            line3D.direction.y,
            line3D.direction.z
        ),
        localName = name,
        parent = line3D,
        creationIndex = allocIndex(state)
    )
}
fun addPudorysProjectionOrPoint(
    state: MongeState,
    line: Line3DProjectionPudorys
) {
    if (line.direction.getDistance() >= EPS_LINE) {
        state.lines3DPudorys.add(line)
        return
    }

    state.pointsPudorys.add(
        Point3DPudorys(
            x = line.point.x,
            y = line.point.y,
            name = line.name?.removeSuffix("\u2081"),
            localSuperscript = line.superscript,
            showInAxoInitial = line.showInAxo,
            creationIndex = allocIndex(state),
            parentLine = line.parent,
            pendingParentLineId = line.parent?.id
        ).also { it.showInAxo = line.showInAxo }
    )
}
fun addNarysProjectionOrPoint(
    state: MongeState,
    line: Line3DProjectionNarys
) {
    if (line.direction.getDistance() >= EPS_LINE) {
        state.lines3DNarys.add(line)
        return
    }

    state.pointsNarys.add(
        Point3DNarys(
            x = line.point.x,
            z = line.point.z,
            name = line.name?.removeSuffix("\u2082"),
            localSuperscript = line.superscript,
            showInAxoInitial = line.showInAxo,
            creationIndex = allocIndex(state),
            parentLine = line.parent,
            pendingParentLineId = line.parent?.id
        ).also { it.showInAxo = line.showInAxo }
    )
}
fun addBokorysProjectionOrPoint(
    state: MongeState,
    line: Line3DProjectionBokorys
) {
    if (line.direction.getDistance() >= EPS_LINE) {
        state.lines3DBokorys.add(line)
        return
    }

    state.pointsBokorys.add(
        Point3DBokorys(
            y = line.point.y,
            z = line.point.z,
            name = line.name?.removeSuffix("\u2083"),
            localSuperscript = line.superscript,
            showInAxoInitial = line.showInAxo,
            creationIndex = allocIndex(state),
            parentLine = line.parent,
            pendingParentLineId = line.parent?.id
        ).also { it.showInAxo = line.showInAxo }
    )
}
fun addAxoProjectionOrPoint(
    state: MongeState,
    line: Line3DProjectionAxo
) {
    if (line.direction.getDistance() >= EPS_LINE) {
        state.lines3DAxo.add(line)
        return
    }

    state.pointsAxo.add(
        Point3DAxo(
            x = line.point.x,
            y = line.point.y,
            name = line.name?.removeSuffix("ₐ"),
            localSuperscript = line.superscript,
            showInAxoInitial = line.showInAxo,
            creationIndex = allocIndex(state),
            parentLine = line.parent,
            pendingParentLineId = line.parent?.id
        ).also { it.showInAxo = line.showInAxo }
    )
}
fun replacePudorysLineKeepingPosition(
    state: MongeState,
    oldLine: Line3DProjectionPudorys,
    newLine: Line3DProjectionPudorys
) {
    val index = state.lines3DPudorys.indexOfFirst { it.id == oldLine.id }

    if (index >= 0) {
        state.lines3DPudorys[index] = newLine
    } else {
        state.lines3DPudorys.add(newLine)
    }
}
fun replaceNarysLineKeepingPosition(
    state: MongeState,
    oldLine: Line3DProjectionNarys,
    newLine: Line3DProjectionNarys
) {
    val index = state.lines3DNarys.indexOfFirst { it.id == oldLine.id }

    if (index >= 0) {
        state.lines3DNarys[index] = newLine
    } else {
        state.lines3DNarys.add(newLine)
    }
}
fun replaceBokorysLineKeepingPosition(
    state: MongeState,
    oldLine: Line3DProjectionBokorys,
    newLine: Line3DProjectionBokorys
) {
    val index = state.lines3DBokorys.indexOfFirst { it.id == oldLine.id }

    if (index >= 0) {
        state.lines3DBokorys[index] = newLine
    } else {
        state.lines3DBokorys.add(newLine)
    }
}
fun finishAxoLineCompletionState(state: MongeState) {
    commitSnapshot(state)
    cancelAxoLineCompletion(state)
    resetStavu(state)
}
fun cancelAxoLineCompletion(state: MongeState) {
    state.pendingAxoLineCompletion = null
    state.completingLineSecondStart = null
    state.tempLine = null
    state.consInfo.value = ""
    setProjectionPhase("pudorys_start", state)
}
data class Solve2Result(
    val u: Float,
    val v: Float
)

fun solve2D(
    a: Offset,
    b: Offset,
    target: Offset
): Solve2Result? {
    val det = a.x * b.y - a.y * b.x

    if (abs(det) < 1e-6f) return null

    val u = (target.x * b.y - target.y * b.x) / det
    val v = (a.x * target.y - a.y * target.x) / det

    return Solve2Result(u, v)
}
fun createCompletedLine3DFromPudorysAndAxo(
    state: MongeState,
    pudorys: Line3DProjectionPudorys,
    axo: Line3DProjectionAxo,
    axoWasExisting: Boolean = false
) {
    val basis = state.basis ?: return

    val styleSource = originalLineProjection(
        state = state,
        pudorys = pudorys,
        axo = axo
    )

    val rawName =
        baseNameOf(styleSource)
            ?: baseNameOf(axo)
            ?: baseNameOf(pudorys)
            ?: "p"

    val p = pudorys.point
    val d = pudorys.direction

    val axoDir = axo.direction.normalizedOrNull()
        ?: return

    // base: ex*x + ey*y + ez*z leží na axo přímce
    val knownBase =
        basis.ex * p.x +
                basis.ey * p.y

    val baseSolve = solve2D(
        a = basis.ez,
        b = -axoDir,
        target = axo.point - knownBase
    ) ?: run {
        state.consInfo.value = "Axo průmět nelze spojit s půdorysem."
        showImpossibleLineCompletionDialog(state)
        return
    }

    val z = baseSolve.u

    // direction: ex*dx + ey*dy + ez*dz je rovnoběžné s axoDir
    val knownDirection =
        basis.ex * d.x +
                basis.ey * d.y

    val dirSolve = solve2D(
        a = basis.ez,
        b = -axoDir,
        target = -knownDirection
    ) ?: run {
        state.consInfo.value = "Směr axo průmětu nelze spojit s půdorysem."
        showImpossibleLineCompletionDialog(state)
        return
    }

    val dz = dirSolve.u

    val line3D = Line3D(
        start = Point3D(
            x = p.x,
            y = p.y,
            z = z,
            name = "",
            creationIndex = allocIndex(state)
        ),
        direction = Offset3D(
            x = d.x,
            y = d.y,
            z = dz
        ),
        name = rawName,
        color = styleSource.color,
        lineStyle = styleSource.lineStyle,
        strokeWidth = styleSource.strokeWidth,
        superscript = styleSource.superscript,
        creationIndex = allocIndex(state)
    ).withInheritedTrim(state, pudorys = pudorys, axo = axo)

    val newPudorys = pudorys.copy(
        parent = line3D,
        localName = rawName.withSuffixOnce("₁")
    )

    val newNarys = createNarysProjectionFromLine3D(
        state = state,
        line3D = line3D,
        name = rawName.withSuffixOnce("₂")
    )

    val newBokorys = createBokorysProjectionFromLine3D(
        state = state,
        line3D = line3D,
        name = rawName.withSuffixOnce("₃")
    )

    val newAxo = if (axoWasExisting) {
        attachAxoProjectionToLine3D(
            oldAxo = axo,
            line3D = line3D,
            rawName = rawName
        )
    } else {
        createAxoProjectionFromLine3D(
            state = state,
            line3D = line3D,
            name = rawName
        )
    }

    applyCompletedLineProjectionVisibility(
        visibleKinds = completedVisibleKinds(state, ProjectionKind.PUDORYS, ProjectionKind.AXO),
        pudorys = newPudorys,
        narys = newNarys,
        bokorys = newBokorys,
        axo = newAxo
    )

    replacePudorysLineKeepingPosition(state, pudorys, newPudorys)
    addNarysProjectionOrPoint(state, newNarys)
    addBokorysProjectionOrPoint(state, newBokorys)

    if (axoWasExisting) {
        replaceAxoLineKeepingPosition(state, axo, newAxo)
    } else {
        addAxoProjectionOrPoint(state, newAxo)
    }

    state.lines3D.add(line3D)

    finishAxoLineCompletionState(state)
}
fun createCompletedLine3DFromNarysAndAxo(
    state: MongeState,
    narys: Line3DProjectionNarys,
    axo: Line3DProjectionAxo,
    axoWasExisting: Boolean = false
) {
    val basis = state.basis ?: return

    val styleSource = originalLineProjection(
        state = state,
        narys = narys,
        axo = axo
    )

    val rawName =
        baseNameOf(styleSource)
            ?: baseNameOf(axo)
            ?: baseNameOf(narys)
            ?: "p"

    val p = narys.point
    val d = narys.direction

    val axoDir = axo.direction.normalizedOrNull()
        ?: return

    val knownBase =
        basis.ex * p.x +
                basis.ez * p.z

    val baseSolve = solve2D(
        a = basis.ey,
        b = -axoDir,
        target = axo.point - knownBase
    ) ?: run {
        state.consInfo.value = "Axo průmět nelze spojit s nárysem."
        showImpossibleLineCompletionDialog(state)
        return
    }

    val y = baseSolve.u

    val knownDirection =
        basis.ex * d.x +
                basis.ez * d.y

    val dirSolve = solve2D(
        a = basis.ey,
        b = -axoDir,
        target = -knownDirection
    ) ?: run {
        state.consInfo.value = "Směr axo průmětu nelze spojit s nárysem."
        showImpossibleLineCompletionDialog(state)
        return
    }

    val dy = dirSolve.u

    val line3D = Line3D(
        start = Point3D(
            x = p.x,
            y = y,
            z = p.z,
            name = "",
            creationIndex = allocIndex(state)
        ),
        direction = Offset3D(
            x = d.x,
            y = dy,
            z = d.y
        ),
        name = rawName,
        color = styleSource.color,
        lineStyle = styleSource.lineStyle,
        strokeWidth = styleSource.strokeWidth,
        superscript = styleSource.superscript,
        creationIndex = allocIndex(state)
    ).withInheritedTrim(state, narys = narys, axo = axo)

    val newNarys = narys.copy(
        parent = line3D,
        localName = rawName.withSuffixOnce("₂")
    )

    val newPudorys = createPudorysProjectionFromLine3D(
        state = state,
        line3D = line3D,
        name = rawName.withSuffixOnce("₁")
    )

    val newBokorys = createBokorysProjectionFromLine3D(
        state = state,
        line3D = line3D,
        name = rawName.withSuffixOnce("₃")
    )

    val newAxo = if (axoWasExisting) {
        attachAxoProjectionToLine3D(
            oldAxo = axo,
            line3D = line3D,
            rawName = rawName
        )
    } else {
        createAxoProjectionFromLine3D(
            state = state,
            line3D = line3D,
            name = rawName
        )
    }

    applyCompletedLineProjectionVisibility(
        visibleKinds = completedVisibleKinds(state, ProjectionKind.NARYS, ProjectionKind.AXO),
        pudorys = newPudorys,
        narys = newNarys,
        bokorys = newBokorys,
        axo = newAxo
    )

    replaceNarysLineKeepingPosition(state, narys, newNarys)
    addPudorysProjectionOrPoint(state, newPudorys)
    addBokorysProjectionOrPoint(state, newBokorys)
    if (axoWasExisting) {
        replaceAxoLineKeepingPosition(state, axo, newAxo)
    } else {
        addAxoProjectionOrPoint(state, newAxo)
    }
    state.lines3D.add(line3D)

    finishAxoLineCompletionState(state)
}
fun createCompletedLine3DFromBokorysAndAxo(
    state: MongeState,
    bokorys: Line3DProjectionBokorys,
    axo: Line3DProjectionAxo,
    axoWasExisting: Boolean = false
) {
    val basis = state.basis ?: return

    val styleSource = originalLineProjection(
        state = state,
        bokorys = bokorys,
        axo = axo
    )

    val rawName =
        baseNameOf(styleSource)
            ?: baseNameOf(axo)
            ?: baseNameOf(bokorys)
            ?: "p"

    val p = bokorys.point
    val d = bokorys.direction

    val axoDir = axo.direction.normalizedOrNull()
        ?: return

    val knownBase =
        basis.ey * p.y +
                basis.ez * p.z

    val baseSolve = solve2D(
        a = basis.ex,
        b = -axoDir,
        target = axo.point - knownBase
    ) ?: run {
        state.consInfo.value = "Axo průmět nelze spojit s bokorysem."
        showImpossibleLineCompletionDialog(state)
        return
    }

    val x = baseSolve.u

    val knownDirection =
        basis.ey * d.x +
                basis.ez * d.y

    val dirSolve = solve2D(
        a = basis.ex,
        b = -axoDir,
        target = -knownDirection
    ) ?: run {
        state.consInfo.value = "Směr axo průmětu nelze spojit s bokorysem."
        showImpossibleLineCompletionDialog(state)
        return
    }

    val dx = dirSolve.u

    val line3D = Line3D(
        start = Point3D(
            x = x,
            y = p.y,
            z = p.z,
            name = "",
            creationIndex = allocIndex(state)
        ),
        direction = Offset3D(
            x = dx,
            y = d.x,
            z = d.y
        ),
        name = rawName,
        color = styleSource.color,
        lineStyle = styleSource.lineStyle,
        strokeWidth = styleSource.strokeWidth,
        superscript = styleSource.superscript,
        creationIndex = allocIndex(state)
    ).withInheritedTrim(state, bokorys = bokorys, axo = axo)

    val newBokorys = bokorys.copy(
        parent = line3D,
        localName = rawName.withSuffixOnce("₃")
    )

    val newPudorys = createPudorysProjectionFromLine3D(
        state = state,
        line3D = line3D,
        name = rawName.withSuffixOnce("₁")
    )

    val newNarys = createNarysProjectionFromLine3D(
        state = state,
        line3D = line3D,
        name = rawName.withSuffixOnce("₂")
    )

    val newAxo = if (axoWasExisting) {
        attachAxoProjectionToLine3D(
            oldAxo = axo,
            line3D = line3D,
            rawName = rawName
        )
    } else {
        createAxoProjectionFromLine3D(
            state = state,
            line3D = line3D,
            name = rawName
        )
    }

    applyCompletedLineProjectionVisibility(
        visibleKinds = completedVisibleKinds(state, ProjectionKind.BOKORYS, ProjectionKind.AXO),
        pudorys = newPudorys,
        narys = newNarys,
        bokorys = newBokorys,
        axo = newAxo
    )

    replaceBokorysLineKeepingPosition(state, bokorys, newBokorys)
    addPudorysProjectionOrPoint(state, newPudorys)
    addNarysProjectionOrPoint(state, newNarys)
    if (axoWasExisting) {
        replaceAxoLineKeepingPosition(state, axo, newAxo)
    } else {
        addAxoProjectionOrPoint(state, newAxo)
    }
    state.lines3D.add(line3D)

    finishAxoLineCompletionState(state)
}

fun createAxoLineProjectionFromAxoInput(
    start: Offset,
    direction: Offset,
    state: MongeState
): Line3DProjectionAxo {
    return Line3DProjectionAxo(
        p = Point3DAxo(start.x, start.y),
        dir = direction,
        localName = "",
        parent = null,
        creationIndex = allocIndex(state)
    )
}
private fun baseNameOf(line: Line3DProjectionPudorys): String? {
    return line.parent?.name.cleanLineBaseName()
        ?: line.localName.cleanLineBaseName()
        ?: line.name.cleanLineBaseName()
}

private fun baseNameOf(line: Line3DProjectionNarys): String? {
    return line.parent?.name.cleanLineBaseName()
        ?: line.localName.cleanLineBaseName()
        ?: line.name.cleanLineBaseName()
}

private fun baseNameOf(line: Line3DProjectionBokorys): String? {
    return line.parent?.name.cleanLineBaseName()
        ?: line.localName.cleanLineBaseName()
        ?: line.name.cleanLineBaseName()
}

private fun baseNameOf(line: Line3DProjectionAxo): String? {
    return line.parent?.name.cleanLineBaseName()
        ?: line.localName.cleanLineBaseName()
        ?: line.name.cleanLineBaseName()
}
private fun String?.cleanLineBaseName(): String? {
    var s = this?.trim().orEmpty()
    if (s.isBlank()) return null

    var changed: Boolean

    do {
        changed = false

        val suffixes = listOf(
            "₁", "₂", "₃", "ₐ",
            "_1", "_2", "_3", "_a",
            "¹", "²", "³"
        )

        for (suffix in suffixes) {
            if (s.endsWith(suffix)) {
                s = s.removeSuffix(suffix).trim()
                changed = true
            }
        }
    } while (changed)

    return s.takeIf { it.isNotBlank() }
}
fun replaceAxoLineKeepingPosition(
    state: MongeState,
    oldLine: Line3DProjectionAxo,
    newLine: Line3DProjectionAxo
) {
    val index = state.lines3DAxo.indexOfFirst { it.id == oldLine.id }

    if (index >= 0) {
        state.lines3DAxo[index] = newLine
    } else {
        state.lines3DAxo.add(newLine)
    }
}
fun attachAxoProjectionToLine3D(
    oldAxo: Line3DProjectionAxo,
    line3D: Line3D,
    rawName: String
): Line3DProjectionAxo {
    return oldAxo.copy(
        parent = line3D,
        localName = rawName.cleanLineBaseName() ?: rawName
    )
}
fun createAxoProjectionFromLine3D(
    state: MongeState,
    line3D: Line3D,
    name: String
): Line3DProjectionAxo {
    val basis = state.basis ?: error("Missing axo basis")

    val p =
        basis.ex * line3D.start.x +
                basis.ey * line3D.start.y +
                basis.ez * line3D.start.z

    val d =
        basis.ex * line3D.direction.x +
                basis.ey * line3D.direction.y +
                basis.ez * line3D.direction.z

    val baseName = name.cleanLineBaseName() ?: name

    return Line3DProjectionAxo(
        p = Point3DAxo(p.x, p.y),
        dir = d,
        localName = baseName,
        parent = line3D,
        creationIndex = allocIndex(state)
    )
}

private fun Line3D.withInheritedTrim(
    state: MongeState,
    pudorys: Line3DProjectionPudorys? = null,
    narys: Line3DProjectionNarys? = null,
    bokorys: Line3DProjectionBokorys? = null,
    axo: Line3DProjectionAxo? = null
): Line3D {
    val range = inheritedTrimRangeForCompletedLine(
        state = state,
        line3D = this,
        pudorys = pudorys,
        narys = narys,
        bokorys = bokorys,
        axo = axo
    )
    return copy(customTrimRange = range)
}

private fun inheritedTrimRangeForCompletedLine(
    state: MongeState,
    line3D: Line3D,
    pudorys: Line3DProjectionPudorys?,
    narys: Line3DProjectionNarys?,
    bokorys: Line3DProjectionBokorys?,
    axo: Line3DProjectionAxo?
): LineTrimRange? {
    val pRange = pudorys?.let {
        remapLineTrimRange(
            range = it.customTrimRange,
            sourcePoint = Offset(it.point.x, it.point.y),
            sourceDir = it.direction,
            targetPoint = Offset(line3D.start.x, line3D.start.y),
            targetDir = Offset(line3D.direction.x, line3D.direction.y)
        )
    }

    val nRange = narys?.let {
        remapLineTrimRange(
            range = it.customTrimRange,
            sourcePoint = Offset(it.point.x, it.point.z),
            sourceDir = it.direction,
            targetPoint = Offset(line3D.start.x, line3D.start.z),
            targetDir = Offset(line3D.direction.x, line3D.direction.z)
        )
    }

    val bRange = bokorys?.let {
        remapLineTrimRange(
            range = it.customTrimRange,
            sourcePoint = Offset(it.point.y, it.point.z),
            sourceDir = it.direction,
            targetPoint = Offset(line3D.start.y, line3D.start.z),
            targetDir = Offset(line3D.direction.y, line3D.direction.z)
        )
    }

    val aRange = axo?.let {
        val basis = state.basis ?: return@let null
        remapLineTrimRange(
            range = it.customTrimRange,
            sourcePoint = it.point,
            sourceDir = it.direction,
            targetPoint = basis.ex * line3D.start.x +
                    basis.ey * line3D.start.y +
                    basis.ez * line3D.start.z,
            targetDir = basis.ex * line3D.direction.x +
                    basis.ey * line3D.direction.y +
                    basis.ez * line3D.direction.z
        )
    }

    return intersectLineTrimRanges(listOf(pRange, nRange, bRange, aRange))
}

private fun originalLineProjection(
    state: MongeState,
    pudorys: Line3DProjectionPudorys? = null,
    narys: Line3DProjectionNarys? = null,
    bokorys: Line3DProjectionBokorys? = null,
    axo: Line3DProjectionAxo? = null
): Line2DProjection {
    return when (state.pendingAxoLineCompletion?.firstKind) {
        ProjectionKind.PUDORYS -> pudorys
        ProjectionKind.NARYS -> narys
        ProjectionKind.BOKORYS -> bokorys
        ProjectionKind.AXO -> axo
        null -> null
    } ?: pudorys ?: narys ?: bokorys ?: axo ?: error("Missing line projection source")
}

private fun baseNameOf(line: Line2DProjection): String? {
    return when (line) {
        is Line3DProjectionPudorys -> baseNameOf(line)
        is Line3DProjectionNarys -> baseNameOf(line)
        is Line3DProjectionBokorys -> baseNameOf(line)
        is Line3DProjectionAxo -> baseNameOf(line)
    }
}

private fun completedVisibleKinds(
    state: MongeState,
    firstInputKind: ProjectionKind,
    secondInputKind: ProjectionKind
): Set<ProjectionKind> {
    val originalKind = state.pendingAxoLineCompletion?.firstKind ?: firstInputKind
    val completedKind = state.pendingAxoLineCompletion?.secondKind
        ?: if (originalKind == firstInputKind) secondInputKind else firstInputKind

    return setOf(originalKind, completedKind)
}

private fun applyCompletedLineProjectionVisibility(
    visibleKinds: Set<ProjectionKind>,
    pudorys: Line3DProjectionPudorys? = null,
    narys: Line3DProjectionNarys? = null,
    bokorys: Line3DProjectionBokorys? = null,
    axo: Line3DProjectionAxo? = null
) {
    pudorys?.setAxoVisibility(ProjectionKind.PUDORYS in visibleKinds)
    narys?.setAxoVisibility(ProjectionKind.NARYS in visibleKinds)
    bokorys?.setAxoVisibility(ProjectionKind.BOKORYS in visibleKinds)
    axo?.setAxoVisibility(ProjectionKind.AXO in visibleKinds)
}

private fun Line3DProjectionPudorys.setAxoVisibility(visible: Boolean) {
    showInAxoInitial = visible
    showInAxo = visible
}

private fun Line3DProjectionNarys.setAxoVisibility(visible: Boolean) {
    showInAxoInitial = visible
    showInAxo = visible
}

private fun Line3DProjectionBokorys.setAxoVisibility(visible: Boolean) {
    showInAxoInitial = visible
    showInAxo = visible
}

private fun Line3DProjectionAxo.setAxoVisibility(visible: Boolean) {
    showInAxoInitial = visible
    showInAxo = visible
}
