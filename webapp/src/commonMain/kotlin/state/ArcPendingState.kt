package state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import model.classes.Point3DNarys
import model.classes.Point3DPudorys

/**
 * Pending stav konstrukce oblouku (ARC). Drží dočasné body a příznaky během
 * klikací konstrukce oblouku v půdorysu a nárysu.
 *
 * Neserializuje se — není součástí .monge ani undo/redo snapshotu (viz
 * SerializableMongeSnapshot), jde čistě o efemérní konstrukční/UI stav.
 *
 * [reset] vrací vše do výchozího stavu po dokončení/zrušení konstrukce (volá
 * se z resetStavu). [arcDirectionClockwise] je mód směru kreslení, který se
 * záměrně NEresetuje.
 *
 * Pole zůstávají Compose-observable (mutableStateOf u příznaků), takže přístup
 * přes state.arc.* se chová identicky jako dřív přes state.* .
 */
class ArcPendingState {
    var arcCenterPudorys: Point3DPudorys? = null
    var arcRadiusPointPudorys: Point3DPudorys? = null
    var arcPreviewEndPudorys: Offset? = null // pro náhled myší
    var arcCenterNarys: Point3DNarys? = null
    var arcRadiusPointNarys: Point3DNarys? = null
    var arcPreviewEndNarys: Offset? = null // pro náhled myší
    var arcRadiusConfirmedPudorys by mutableStateOf(false)
    var arcRadiusConfirmedNarys by mutableStateOf(false)
    var arcStartPointPudorys: Point3DPudorys? = null
    var arcStartPointNarys: Point3DNarys? = null
    var isSnappingToArc = false
    var isSnappingToArcNarysOnly = false
    var arcDirectionClockwise by mutableStateOf(true)

    /** Vynuluje dočasný stav konstrukce oblouku (mimo arcDirectionClockwise). */
    fun reset() {
        arcCenterPudorys = null
        arcRadiusPointPudorys = null
        arcPreviewEndPudorys = null
        arcCenterNarys = null
        arcRadiusPointNarys = null
        arcPreviewEndNarys = null
        arcRadiusConfirmedPudorys = false
        arcRadiusConfirmedNarys = false
        arcStartPointPudorys = null
        arcStartPointNarys = null
        isSnappingToArc = false
        isSnappingToArcNarysOnly = false
        // arcDirectionClockwise se NEresetuje (mód směru kreslení)
    }
}
