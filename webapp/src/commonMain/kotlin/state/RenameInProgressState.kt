package state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import model.classes.HelpLineNarys
import model.classes.HelpLinePudorys
import model.classes.Line3D
import model.classes.Line3DProjectionBokorys
import model.classes.Line3DProjectionNarys
import model.classes.Line3DProjectionPudorys
import model.classes.Plane3D
import model.classes.Point3DNarys
import model.classes.Point3DPudorys
import model.classes.Segment2DAxo
import model.classes.SegmentsBokorys
import model.classes.SegmentsNarys
import model.classes.SegmentsPudorys

/**
 * Stav probíhajícího inline přejmenování objektů (který bod/přímka/rovina se
 * právě edituje v UI). Čistě efemérní — neserializuje se (není v
 * SerializableMongeSnapshot ani SerializedMongeState).
 *
 * Všechna pole zůstávají Compose-observable, přístup přes state.rename.* se
 * chová identicky jako dřív přes state.* .
 *
 * Pozn.: [reset] nuluje jen body a přímky — přesně to, co dělal resetStavu.
 * [planeBeingRenamed] a helpline pole se záměrně NEresetují přes resetStavu
 * (nulují se na svých vlastních místech po dokončení přejmenování), aby
 * zůstalo zachováno původní chování.
 */
class RenameInProgressState {
    var pointBeingRenamed by mutableStateOf<Any?>(null)
    var pointNarysBeingRenamed by mutableStateOf<Point3DNarys?>(null)
    var pointBokorysBeingRenamed by mutableStateOf<Point3DNarys?>(null)
    var pointPudorysBeingRenamed by mutableStateOf<Point3DPudorys?>(null)
    var lineBeingRenamedPudorys by mutableStateOf<Line3DProjectionPudorys?>(null)
    var lineBeingRenamedNarys by mutableStateOf<Line3DProjectionNarys?>(null)
    var lineBeingRenamedBokorys by mutableStateOf<Line3DProjectionBokorys?>(null)
    var lineBeingRenamed3D by mutableStateOf<Line3D?>(null)
    var segmentBeingRenamedPudorys by mutableStateOf<SegmentsPudorys?>(null)
    var segmentBeingRenamedNarys by mutableStateOf<SegmentsNarys?>(null)
    var segmentBeingRenamedBokorys by mutableStateOf<SegmentsBokorys?>(null)
    var segmentBeingRenamedAxo by mutableStateOf<Segment2DAxo?>(null)
    var helplineBeingRenamedNarys by mutableStateOf<HelpLineNarys?>(null)
    var helplineBeingRenamedPudorys by mutableStateOf<HelpLinePudorys?>(null)
    var planeBeingRenamed by mutableStateOf<Plane3D?>(null)

    /**
     * Zruší probíhající přejmenování bodů a přímek (odpovídá tomu, co dřív
     * dělal resetStavu). Plane/helpline pole se zde záměrně neřeší.
     */
    fun reset() {
        pointBeingRenamed = null
        lineBeingRenamed3D = null
        lineBeingRenamedPudorys = null
        lineBeingRenamedBokorys = null
        lineBeingRenamedNarys = null
        segmentBeingRenamedPudorys = null
        segmentBeingRenamedNarys = null
        segmentBeingRenamedBokorys = null
        segmentBeingRenamedAxo = null
        pointNarysBeingRenamed = null
        pointBokorysBeingRenamed = null
        pointPudorysBeingRenamed = null
    }
}
