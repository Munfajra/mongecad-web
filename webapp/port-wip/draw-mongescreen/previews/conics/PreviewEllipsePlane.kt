package draw.mongescreen.previews.conics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import draw.mongescreen.objects.orth.conics.drawEllipseFromDiameters
import model.*
import monge.input.conixections.conjugateDiameterInputFromRadii
import state.MongeState
import utils.getLogicalCursor

fun DrawScope.drawEllipseConstructionPreviewBothViews(state: MongeState, snappedPointLogical: Offset?) {
    val p1 = state.pendingPoint1
    val p2 = state.pendingPoint2

    val cursor = getLogicalCursor(
        snappedPointLogical,
        state.cursorPosition,
        state.canvasOffset,
        state.scale,
        state.canvasWidth,
        state.canvasHeight,
        state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
        state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
    )

    when (state.projectionPhase) {
        "ellipse_plane_point2" -> {
            if (p1 != null) {

                drawRedCross(p1, state)
                drawDashedLine(p1, cursor, Color.Gray, state=state)
                if (state.projectionMode != ProjectionMode.KOTO) {
                val plane = state.selectedPlaneForCircle ?: state.selectedPlanes.lastOrNull()

                val eq = plane?.equation
                if (eq != null && eq.c != 0f) {
                    val z1 = -(eq.a * p1.x + eq.b * p1.y + eq.d) / eq.c
                    val z2 = -(eq.a * cursor.x + eq.b * cursor.y + eq.d) / eq.c

                    val p1n = Offset(p1.x, -z1)
                    val p2n = Offset(cursor.x, -z2)

                    drawRedCross(p1n, state)
                    drawDashedLine(p1n, p2n, Color.Gray, state = state)
                }
                }
            }
        }

        "ellipse_plane_point3" -> {
            if (p1 != null) drawRedCross(p1, state)
            if (p2 != null) drawRedCross(p2, state)

            if (p1 != null && p2 != null) {
                val center = p1
                drawDashedLine(center, p2, Color.Gray, state=state)
                drawDashedLine(center, cursor, Color.Gray, state=state)

                val dist1 = (cursor - center).getDistance()
                val dist2 = (p2 - center).getDistance()

                if (dist1 > 2f / state.scale && dist2 > 2f / state.scale) {
                    runCatching {
                        val plane = state.selectedPlaneForCircle ?: state.selectedPlanes.lastOrNull() ?: return@runCatching
                        val eq = plane.equation ?: return@runCatching

                        fun projectToPlane(x: Float, y: Float): Point3D {
                            val z = -(eq.a * x + eq.b * y + eq.d) / eq.c
                            return Point3D(x, y, z,name="")
                        }

                        val centerPt = projectToPlane(center.x, center.y)
                        val firstPt = projectToPlane(p2.x, p2.y)
                        val secondPt = projectToPlane(cursor.x, cursor.y)

                        // PŮDORYS
                        val (d1p, d2p, d3p) = conjugateDiameterInputFromRadii(center, p2, cursor)
                        drawEllipseFromDiameters(d1p, d2p, d3p, state.scale, state.canvasOffset, Color.LightGray, 1f, LineStyle.Dashed)

                        // NÁRYS (Z souřadnice → -Y)
                        val centerN = Offset(centerPt.x, -centerPt.z)
                        val firstN = Offset(firstPt.x, -firstPt.z)
                        val secondN = Offset(secondPt.x, -secondPt.z)
                        val (d1n, d2n, d3n) = conjugateDiameterInputFromRadii(centerN, firstN, secondN)
                        if (state.projectionMode != ProjectionMode.KOTO) {
                            drawRedCross(centerN, state)
                            drawRedCross(firstN, state)

                            drawDashedLine(centerN, firstN, Color.Gray, state = state)
                            drawDashedLine(centerN, secondN, Color.Gray, state = state)

                            drawEllipseFromDiameters(
                                d1n,
                                d2n,
                                d3n,
                                state.scale,
                                state.canvasOffset,
                                Color.LightGray,
                                1f,
                                LineStyle.Dashed
                            )
                        }
                    }.onFailure {
                        println("⚠️ Náhled elipsy nelze vytvořit: ${it.message}")
                    }
                }
            }
        }
        "circle_plane_radius" -> {
            val p1 = state.pendingPoint1 ?: return

            val cursor = getLogicalCursor(
                snappedPointLogical,
                state.cursorPosition,
                state.canvasOffset,
                state.scale,
                state.canvasWidth,
                state.canvasHeight,
                state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
                state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
            )

            val plane = state.selectedPlaneForCircle ?: state.selectedPlanes.lastOrNull() ?: return
            val eq = plane.equation ?: return

            fun lift(x: Float, y: Float): Point3D {
                val z = -(eq.a * x + eq.b * y + eq.d) / eq.c
                return Point3D(x, y, z, "")
            }

            val ptCenter = lift(p1.x, p1.y)
            val ptRadius = lift(cursor.x, cursor.y)

            val center3D = Offset3D(ptCenter.x, ptCenter.y, ptCenter.z)
            val radiusVec = Offset3D(
                ptRadius.x - ptCenter.x,
                ptRadius.y - ptCenter.y,
                ptRadius.z - ptCenter.z
            )
            val radius = radiusVec.length()
            val normal = Offset3D(eq.a, eq.b, eq.c).normalize()

            val ortho3D = (radiusVec cross normal).normalize() * radius
            val pt1mirror = center3D * 2f - Offset3D(ptRadius.x, ptRadius.y, ptRadius.z)
            val pt3 = center3D + ortho3D

            // Půdorys (XY)
            val p1p = Offset(pt1mirror.x, pt1mirror.y)
            val p2p = Offset(ptRadius.x, ptRadius.y)
            val p3p = Offset(pt3.x, pt3.y)
            drawEllipseFromDiameters(p1p, p2p, p3p, state.scale, state.canvasOffset, Color.LightGray, 1f, LineStyle.Dashed)
            if (state.projectionMode != ProjectionMode.KOTO) {
                // Nárys (XZ)
                val p1n = Offset(pt1mirror.x, -pt1mirror.z)
                val p2n = Offset(ptRadius.x, -ptRadius.z)
                val p3n = Offset(pt3.x, -pt3.z)
                drawEllipseFromDiameters(
                    p1n,
                    p2n,
                    p3n,
                    state.scale,
                    state.canvasOffset,
                    Color.LightGray,
                    1f,
                    LineStyle.Dashed
                )
            }
            // 🔴 Červený křížek – pouze ve středu
            val centerXY = Offset(center3D.x, center3D.y)
            val centerXZ = Offset(center3D.x, -center3D.z)
            drawRedCross(centerXY, state)
            if (state.projectionMode != ProjectionMode.KOTO) {
                drawRedCross(centerXZ, state)
            }
            // Pomocná čára střed → kurzor (v půdorysu)
            drawDashedLine(centerXY, cursor, Color.Gray, state=state)
        }
        "circle_plane_radius_narys" -> {
            val p1 = state.pendingPoint1 ?: return

            val cursor = getLogicalCursor(
                snappedPointLogical,
                state.cursorPosition,
                state.canvasOffset,
                state.scale,
                state.canvasWidth,
                state.canvasHeight,
                state.xAxisDirection == XAxisDirection.POSITIVE_LEFT,
                state.yAxisDirectionPlane == YAxisDirectionPlane.POSITIVE_UP
            )

            val plane = state.selectedPlaneForCircle ?: state.selectedPlanes.lastOrNull() ?: return
            val eq = plane.equation ?: return

            fun lift(x: Float, z: Float): Point3D {
                val y = -(eq.a * x + eq.c * z + eq.d) / eq.b
                return Point3D(x, y, z, "")
            }

            val ptCenter = lift(p1.x, -p1.y)
            val ptRadius = lift(cursor.x, -cursor.y)

            val center3D = Offset3D(ptCenter.x, ptCenter.y, ptCenter.z)
            val radiusVec = Offset3D(
                ptRadius.x - ptCenter.x,
                ptRadius.y - ptCenter.y,
                ptRadius.z - ptCenter.z
            )
            val radius = radiusVec.length()
            val normal = Offset3D(eq.a, eq.b, eq.c).normalize()

            val ortho3D = (radiusVec cross normal).normalize() * radius
            val pt1mirror = center3D * 2f - Offset3D(ptRadius.x, ptRadius.y, ptRadius.z)
            val pt3 = center3D + ortho3D

            val p1p = Offset(pt1mirror.x, pt1mirror.y)
            val p2p = Offset(ptRadius.x, ptRadius.y)
            val p3p = Offset(pt3.x, pt3.y)

            val p1n = Offset(pt1mirror.x, -pt1mirror.z)
            val p2n = Offset(ptRadius.x, -ptRadius.z)
            val p3n = Offset(pt3.x, -pt3.z)

            // elipsa z průměrů
            drawEllipseFromDiameters(p1p, p2p, p3p, state.scale, state.canvasOffset, Color.LightGray, 1f, LineStyle.Dashed)
            if (state.projectionMode != ProjectionMode.KOTO) {
                drawEllipseFromDiameters(
                    p1n,
                    p2n,
                    p3n,
                    state.scale,
                    state.canvasOffset,
                    Color.LightGray,
                    1f,
                    LineStyle.Dashed
                )
            }
            // červený křížek ve středu
            val centerXY = Offset(center3D.x, center3D.y)
            val centerXZ = Offset(center3D.x, -center3D.z)
            drawRedCross(centerXY, state)
            if (state.projectionMode != ProjectionMode.KOTO) {
                drawRedCross(centerXZ, state)
            }
            drawDashedLine(Offset(p1.x, p1.y), cursor, Color.Gray, state=state)}
        "ellipse_plane_point2_narys" -> {
            if (p1 != null) {
                drawRedCross(p1, state)
                drawDashedLine(p1, cursor, Color.Gray, state = state)

                val plane = state.selectedPlaneForCircle ?: state.selectedPlanes.lastOrNull()
                val eq = plane?.equation
                if (eq != null && eq.b != 0f) {
                    val z1 = -p1.y
                    val z2 = -cursor.y
                    val y1 = -(eq.a * p1.x + eq.c * z1 + eq.d) / eq.b
                    val y2 = -(eq.a * cursor.x + eq.c * z2 + eq.d) / eq.b

                    val p1p = Offset(p1.x, y1)
                    val p2p = Offset(cursor.x, y2)

                    drawRedCross(p1p, state)
                    drawDashedLine(p1p, p2p, Color.Gray, state = state)
                }
            }
        }

        "ellipse_plane_point3_narys" -> {
            if (p1 != null) drawRedCross(p1, state)
            if (p2 != null) drawRedCross(p2, state)

            if (p1 != null && p2 != null) {
                val center = p1
                drawDashedLine(center, p2, Color.Gray, state = state)
                drawDashedLine(center, cursor, Color.Gray, state = state)

                val dist1 = (cursor - center).getDistance()
                val dist2 = (p2 - center).getDistance()

                if (dist1 > 2f / state.scale && dist2 > 2f / state.scale) {
                    runCatching {
                        val plane = state.selectedPlaneForCircle ?: state.selectedPlanes.lastOrNull() ?: return@runCatching
                        val eq = plane.equation ?: return@runCatching

                        fun liftXZto3D(x: Float, z: Float): Point3D {
                            val y = -(eq.a * x + eq.c * z + eq.d) / eq.b
                            return Point3D(x, y, z,name="")
                        }

                        val centerPt = liftXZto3D(center.x, -center.y)
                        val firstPt = liftXZto3D(p2.x, -p2.y)
                        val secondPt = liftXZto3D(cursor.x, -cursor.y)

                        val centerP = Offset(centerPt.x, centerPt.y)
                        val firstP = Offset(firstPt.x, firstPt.y)
                        val secondP = Offset(secondPt.x, secondPt.y)

                        val centerN = Offset(centerPt.x, -centerPt.z)
                        val firstN = Offset(firstPt.x, -firstPt.z)
                        val secondN = Offset(secondPt.x, -secondPt.z)

                        drawRedCross(centerP, state)
                        drawRedCross(firstP, state)

                        drawDashedLine(centerP, firstP, Color.Gray, state = state)
                        drawDashedLine(centerP, secondP, Color.Gray, state = state)

                        val (d1p, d2p, d3p) = conjugateDiameterInputFromRadii(centerP, firstP, secondP)
                        drawEllipseFromDiameters(d1p, d2p, d3p, state.scale, state.canvasOffset, Color.LightGray, 1f, LineStyle.Dashed)
                        if (state.projectionMode != ProjectionMode.KOTO) {
                            val (d1n, d2n, d3n) = conjugateDiameterInputFromRadii(centerN, firstN, secondN)
                            drawEllipseFromDiameters(
                                d1n,
                                d2n,
                                d3n,
                                state.scale,
                                state.canvasOffset,
                                Color.LightGray,
                                1f,
                                LineStyle.Dashed
                            )
                        }
                    }.onFailure {
                        println("⚠️ Náhled elipsy z nárysu nelze vytvořit: ${it.message}")
                    }
                }
            }
        }



    }
}
