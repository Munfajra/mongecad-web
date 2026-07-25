package serialization.classes

import kotlinx.serialization.Serializable
import model.Offset3D
import model.classes.ConicSection3D
import serialization.SerializableOffset3D
import state.MongeState

@Serializable
data class SerializedEllipseArc3D(
    val parentId: String,
    val t1: Float,
    val t2: Float,
    val A: SerializableOffset3D,
    val B: SerializableOffset3D
)
fun MongeState.serializeEllipseArcs3D(): List<SerializedEllipseArc3D> {
    return ellipseArcParams3D.mapNotNull { (parentId, params) ->
        val ends = ellipseArcEnds3D[parentId] ?: return@mapNotNull null
        val (t1, t2) = params
        val (A, B) = ends
        SerializedEllipseArc3D(
            parentId = parentId,
            t1 = t1, t2 = t2,
            A = SerializableOffset3D(A.x, A.y, A.z),
            B = SerializableOffset3D(B.x, B.y, B.z)
        )
    }
}
fun MongeState.toRuntime(list: List<SerializedEllipseArc3D>) {
    list.forEach { arc ->
        ellipseArcParams3D[arc.parentId] = arc.t1 to arc.t2
        ellipseArcEnds3D[arc.parentId] =
            Offset3D(arc.A.x, arc.A.y, arc.A.z) to Offset3D(arc.B.x, arc.B.y, arc.B.z)
    }
}
@Serializable
data class SerializedParabolaArc3D(
    val parentId: String,
    val t1: Float,
    val t2: Float,
    val A: SerializableOffset3D,
    val B: SerializableOffset3D
)
fun MongeState.serializeParabolaArcs3D(): List<SerializedParabolaArc3D>{
    return parabolaArcParams3D.mapNotNull { (parentId, params) ->
        val ends = parabolaArcEnds3D[parentId] ?: return@mapNotNull null
        val (t1, t2) = params
        val (A, B) = ends
        SerializedParabolaArc3D(
            parentId = parentId,
            t1 = t1, t2 = t2,
            A = SerializableOffset3D(A.x, A.y, A.z),
            B = SerializableOffset3D(B.x, B.y, B.z)
        )
    }
}
fun MongeState.deserializeParabolaArcs3D(list: List<SerializedParabolaArc3D>) {
    list.forEach { arc ->
        parabolaArcParams3D[arc.parentId] = arc.t1 to arc.t2
        parabolaArcEnds3D[arc.parentId] =
            Offset3D(arc.A.x, arc.A.y, arc.A.z) to Offset3D(arc.B.x, arc.B.y, arc.B.z)
    }
}
@Serializable
data class SerializedHyperbolaArc3D(
    val parentId: String,

    // větev 1 (volitelná)
    val t1Branch1: Float? = null,
    val t2Branch1: Float? = null,
    val A1: SerializableOffset3D? = null,
    val B1: SerializableOffset3D? = null,
    val sign1: Int? = null,     // +1 pravá (x>0), -1 levá (x<0)

    // větev 2 (volitelná)
    val t1Branch2: Float? = null,
    val t2Branch2: Float? = null,
    val A2: SerializableOffset3D? = null,
    val B2: SerializableOffset3D? = null,
    val sign2: Int? = null
)

private fun hyperbolaLocalXY(conic: ConicSection3D, p3: Offset3D): Pair<Float, Float> {
    val uN = conic.u.normalized()
    val vN = (conic.v - uN * conic.v.dot(uN)).normalized()
    val r = p3 - conic.p0
    return r.dot(uN) to r.dot(vN)
}
private fun branchSignFromEnds(conic: ConicSection3D, ends: Pair<Offset3D, Offset3D>): Int {
    val (xA, _) = hyperbolaLocalXY(conic, ends.first)
    val (xB, _) = hyperbolaLocalXY(conic, ends.second)
    return if (xA + xB >= 0f) +1 else -1
}


fun MongeState.serializeHyperbolaArcs3D(): List<SerializedHyperbolaArc3D> {
    val keys = (hyperbolaArcParams3D.keys + hyperbolaArcEnds3D.keys).toSet()
    return keys.mapNotNull { parentId ->
        val params = hyperbolaArcParams3D[parentId]
        val ends   = hyperbolaArcEnds3D[parentId]
        if ((params?.first == null || ends?.first == null) &&
            (params?.second == null || ends?.second == null)) return@mapNotNull null

        val b1 = params.first
        val e1 = ends.first
        val b2 = params.second
        val e2 = ends.second

        // zkusíme určit sign podle uložených konců (nejpřesnější)
        val s1 = e1?.let { (A, B) ->
            val conic = conics3D.firstOrNull { it.id == parentId }
            if (conic != null) branchSignFromEnds(conic, A to B) else null
        }
        val s2 = e2?.let { (A, B) ->
            val conic = conics3D.firstOrNull { it.id == parentId }
            if (conic != null) branchSignFromEnds(conic, A to B) else null
        }

        SerializedHyperbolaArc3D(
            parentId = parentId,
            t1Branch1 = b1?.first, t2Branch1 = b1?.second,
            A1 = e1?.first?.let { SerializableOffset3D(it.x, it.y, it.z) },
            B1 = e1?.second?.let { SerializableOffset3D(it.x, it.y, it.z) },
            sign1 = s1,
            t1Branch2 = b2?.first, t2Branch2 = b2?.second,
            A2 = e2?.first?.let { SerializableOffset3D(it.x, it.y, it.z) },
            B2 = e2?.second?.let { SerializableOffset3D(it.x, it.y, it.z) },
            sign2 = s2
        )
    }
}


fun MongeState.deserializeHyperbolaArcs3D(list: List<SerializedHyperbolaArc3D>) {
    list.forEach { arc ->
        val p1 = if (arc.t1Branch1 != null && arc.t2Branch1 != null) arc.t1Branch1 to arc.t2Branch1 else null
        val e1 = if (arc.A1 != null && arc.B1 != null)
            Offset3D(arc.A1.x, arc.A1.y, arc.A1.z) to Offset3D(arc.B1.x, arc.B1.y, arc.B1.z)
        else null

        val p2 = if (arc.t1Branch2 != null && arc.t2Branch2 != null) arc.t1Branch2 to arc.t2Branch2 else null
        val e2 = if (arc.A2 != null && arc.B2 != null)
            Offset3D(arc.A2.x, arc.A2.y, arc.A2.z) to Offset3D(arc.B2.x, arc.B2.y, arc.B2.z)
        else null

        // Zapiš rovnou
        val paramsPair: Pair<Pair<Float, Float>?, Pair<Float, Float>?> = p1 to p2
        val endsPair:   Pair<Pair<Offset3D, Offset3D>?, Pair<Offset3D, Offset3D>?> = e1 to e2

        if (paramsPair.first != null || paramsPair.second != null) {
            hyperbolaArcParams3D[arc.parentId] = paramsPair
        }
        if (endsPair.first != null || endsPair.second != null) {
            hyperbolaArcEnds3D[arc.parentId] = endsPair
        }

        // 🔧 Integrita: pokud máme obě větve a vychází stejný sign, pokusíme se je rozlišit.
        if (e1 != null && e2 != null) {
            val conic = conics3D.firstOrNull { it.id == arc.parentId }
            if (conic != null) {
                val s1 = arc.sign1 ?: branchSignFromEnds(conic, e1)
                val s2 = arc.sign2 ?: branchSignFromEnds(conic, e2)

                if (s1 == s2) {
                    // Pokus o rozlišení: urč podle průměrného lokálního x a swapni tak,
                    // aby branch1 byla x>0, branch2 x<0 (nebo opačně podle uložených signů).
                    val (x1a, _) = hyperbolaLocalXY(conic, e1.first)
                    val (x1b, _) = hyperbolaLocalXY(conic, e1.second)
                    val (x2a, _) = hyperbolaLocalXY(conic, e2.first)
                    val (x2b, _) = hyperbolaLocalXY(conic, e2.second)
                    val avg1 = x1a + x1b
                    x2a + x2b

                    val ePos = if (avg1 >= 0f) e1 else e2
                    val eNeg = if (avg1 >= 0f) e2 else e1
                    val pPos = if (avg1 >= 0f) p1 else p2
                    val pNeg = if (avg1 >= 0f) p2 else p1

                    hyperbolaArcParams3D[arc.parentId] = pPos to pNeg
                    hyperbolaArcEnds3D[arc.parentId]   = ePos to eNeg
                }
            }
        }
    }
}