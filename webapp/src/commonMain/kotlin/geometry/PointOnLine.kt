package geometry

import androidx.compose.ui.geometry.Offset

/*
 * Kolmý průmět bodu na přímku danou bodem a směrem.
 * Na desktopu v `monge/input/axo/segments/Pudorys.kt`, ale s axonometrií
 * nesouvisí – volá to i náhled úsečky v Monge.
 */
fun projectPointOntoLineByPointAndDir(
    p: Offset,
    linePoint: Offset,
    lineDir: Offset
): Offset {
    val dLenSq = lineDir.x * lineDir.x + lineDir.y * lineDir.y
    if (dLenSq < 1e-6f) return linePoint

    val ap = p - linePoint
    val t = (ap.x * lineDir.x + ap.y * lineDir.y) / dLenSq

    return linePoint + lineDir * t
}

