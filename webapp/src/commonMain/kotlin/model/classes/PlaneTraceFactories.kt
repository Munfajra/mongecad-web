package model.classes

import androidx.compose.ui.geometry.Offset

/*
 * Prázdné (zástupné) stopy roviny. Dřív v `dialogs/batchinput/PointInput.kt`,
 * i když je volá i obsluha klikání na roviny – s dialogem nesouvisí.
 */
fun dummyPudorys(): PlaneTracePudorys =
    PlaneTracePudorys(Point3DPudorys(0f, 0f), Offset(1f, 0f))

fun dummyNarys(): PlaneTraceNarys =
    PlaneTraceNarys(Point3DNarys(0f, 0f), Offset(1f, 0f))

fun dummyBokorys(): PlaneTraceBokorys =
    PlaneTraceBokorys(Point3DBokorys(0f, 0f), Offset(1f, 0f))
