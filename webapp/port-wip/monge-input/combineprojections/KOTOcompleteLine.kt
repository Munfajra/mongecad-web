package monge.input.combineprojections

import androidx.compose.ui.geometry.Offset
import model.*
import model.classes.Line3D
import model.classes.Line3DProjectionNarys
import model.classes.Line3DProjectionPudorys
import model.classes.Point3DNarys
import model.classes.Point3DPudorys
import state.MongeState
import ui.resetStavu
import utils.allocIndex
import kotlin.math.abs
import kotlin.math.hypot

private fun cross2(a: Offset, b: Offset): Float = a.x * b.y - a.y * b.x
private fun length2(v: Offset): Float = hypot(v.x, v.y)

private fun distancePointToInfiniteLine(p: Offset, linePoint: Offset, lineDir: Offset): Float {
    val denom = length2(lineDir)
    if (denom <= 1e-6f) return Float.POSITIVE_INFINITY
    return abs(cross2(p - linePoint, lineDir)) / denom
}

private fun isPointOnLine(p: Offset, linePoint: Offset, lineDir: Offset, eps: Float): Boolean {
    return distancePointToInfiniteLine(p, linePoint, lineDir) <= eps
}


fun handleKotoLiftExistingPudorysLineByTwoParentedPointsClick(
    state: MongeState,
    targetLineP: Line3DProjectionPudorys, // vstupem je vybraná půdorysná přímka
    clicked: Point3DPudorys               // kliknutý bod v půdorysu (musí mít parent)
) {
    val clicked3D = clicked.parent ?: return

    // --- jaká tolerance?
    // Ideálně použij to, co máš pro snapping v LOGICAL souřadnicích.
    // Když nic nemáš, dej rozumné číslo podle měřítka:
    val epsLogical = (6f / state.scale).coerceAtLeast(0.5f) // uprav dle chování

    // --- vytažení 2D pozice bodu v půdorysu (logical)
    val p = Offset(clicked.x, clicked.y) // uprav podle typu: někdy clicked.p.x / clicked.p.y

    // --- vytažení 2D definice přímky v půdorysu
    val linePoint = Offset(targetLineP.point.x, targetLineP.point.y) // uprav podle typu
    val lineDir   = targetLineP.direction                            // očekávám Offset (x,y)

    // musí ležet na přímce
    if (!isPointOnLine(p, linePoint, lineDir, epsLogical)) {
        state.consInfo.value = "Bod neleží na vybrané přímce (půdorys)."
        return
    }

    state.consInfo.value = "Vyberte druhý bod ležící na přímce"

    // pokud začínáme nový výběr, uložíme si i ID přímky, abys neudělal 1. bod na jiné
    val aId = state.kotoLinePickAId
    val lineIdStored = state.kotoLiftLinePickLineId

    if (aId == null) {
        state.kotoLinePickAId = clicked.id
        state.kotoLiftLinePickLineId = targetLineP.id
        return
    }

    // když user mezitím kliká na bod, ale aktuálně má jinou targetLineP → reset
    if (lineIdStored != null && lineIdStored != targetLineP.id) {
        state.kotoLinePickAId = null
        state.kotoLiftLinePickLineId = null
        state.consInfo.value = "Změnila se cílová přímka – začněte znovu."
        return
    }

    // druhý bod nesmí být stejný
    if (clicked.id == aId) return

    // najdi první bod
    val firstPud = state.pointsPudorys.find { it.id == aId } ?: run {
        state.kotoLinePickAId = null
        state.kotoLiftLinePickLineId = null
        return
    }
    val a3 = firstPud.parent ?: run {
        state.kotoLinePickAId = null
        state.kotoLiftLinePickLineId = null
        return
    }
    val b3 = clicked3D

    // ještě jednou ověř, že první bod leží na přímce (kdyby se mezitím něco změnilo)
    val a2d = Offset(firstPud.x, firstPud.y) // uprav podle typu
    if (!isPointOnLine(a2d, linePoint, lineDir, epsLogical)) {
        state.kotoLinePickAId = null
        state.kotoLiftLinePickLineId = null
        state.consInfo.value = "První vybraný bod už neleží na přímce (půdorys)."
        return
    }

    // --- FINÁL: vytvoř 3D parent + nárys, a půdorysnou přímku jen propoj přes parent
    add3DLineAndNarysProjectionUsingExistingPudorysProjection(
        state = state,
        a3 = a3,
        b3 = b3,
        existingPudLine = targetLineP
    )

    // cleanup
    state.kotoLinePickAId = null
    state.kotoLiftLinePickLineId = null
}


// --------------------------------------------
// Vytvoří Line3D parent z a3,b3,
// vytvoří jen Line3DProjectionNarys,
// a existující Line3DProjectionPudorys napojí na parent.
// --------------------------------------------
fun add3DLineAndNarysProjectionUsingExistingPudorysProjection(
    state: MongeState,
    a3: Point3D,
    b3: Point3D,
    existingPudLine: Line3DProjectionPudorys
) {
    // 1) vytvoř 3D přímku
    val baseName = existingPudLine.name
        ?.removeSuffix("₁")
        ?.removeSuffix("₂")
        ?.trim()
        ?.ifEmpty { "p" } ?: "p"

    val line3D = Line3D(
        // uprav konstruktor podle tvého typu
        start = a3,
        direction = Offset3D(b3.x - a3.x, b3.y - a3.y, b3.z - a3.z),
        name = baseName,
        color = existingPudLine.color,
        strokeWidth = existingPudLine.strokeWidth,
        lineStyle = existingPudLine.lineStyle, creationIndex = allocIndex(state)
    )

    state.lines3D.add(line3D) // uprav: kde držíš Line3D list

    // 2) napoj existující půdorysný průmět na parent
    run {
        val updated = existingPudLine.copy(
            localName = baseName,
            parentId = line3D.id,   // nebo parent = line3D
            parent = line3D         // pokud máš přímou referenci
        )
        val idx = state.lines3DPudorys.indexOfFirst { it.id == existingPudLine.id }
        if (idx != -1) state.lines3DPudorys[idx] = updated
    }

    // 3) vytvoř NÁRYS průmět z 3D přímky
    val narysProj = projectLine3DToNarys(line3D,state).copy(
        localName = baseName,
        parentId = line3D.id,
        parent = line3D,

    )

    state.lines3DNarys.add(narysProj) // uprav: kde držíš nárysové přímky
    resetStavu(state)
}


fun projectLine3DToNarys(line3D: Line3D, state: MongeState): Line3DProjectionNarys {
    // Zvolíme bod na přímce a dir:
    val p0 = line3D.start
    val d  = line3D.direction

    // nárys: (x, -z)
    val p2 = Offset(p0.x, p0.z)
    val dir2 = Offset(d.x, d.z)

    return Line3DProjectionNarys(
        point = Point3DNarys(p2.x, p2.y),
        direction = dir2,
        parentId = line3D.id,
        parent = line3D, creationIndex = allocIndex(state)
    )
}