package model.classes

import monge.input.lines.AXO_PLANE_ID
import monge.input.lines.AXO_PLANE_TRACE_BOKORYS_ID
import monge.input.lines.AXO_PLANE_TRACE_NARYS_ID
import monge.input.lines.AXO_PLANE_TRACE_PUDORYS_ID
import monge.input.lines.XA_ID
import monge.input.lines.YA_ID
import monge.input.lines.ZA_ID

/*
 * Predikáty pro vestavěné objekty výkresu – osa x₁₂, souřadnicové osy,
 * počátek a axonometrická průmětna.
 *
 * Dřív v `serialization/JSONsave.kt`, ale se serializací nesouvisí:
 * podle nich se rozhoduje kreslení, mazání, pravý panel i OpenGL scéna.
 */
private const val X12_ID = "X12_ID"

fun isX12Line3D(line: Line3D) = line.id == X12_ID
fun isX12Narys(line: Line3DProjectionNarys) = line.id == "${X12_ID}_N" || line.parent?.id == X12_ID
fun isX12Pud(line: Line3DProjectionPudorys) = line.id == "${X12_ID}_P" || line.parent?.id == X12_ID
fun isAxisX(line: HelpLinePudorys) = (line.id == "axisX" ||line.id == "")
fun isAxisY(line: HelpLinePudorys) = line.id == "axisY"
fun isAxisZ(line: HelpLineNarys) = line.id =="axisZ"
fun isOrigin(point: AidPointLogical) = point.id == "origin"
fun isAxis(line: Line3D) = (line.id == "x_axis" || line.id == "y_axis" || line.id == "z_axis")
fun isAxisProjection(line2DProjection: Line2DProjection) = line2DProjection.id in listOf(
    "xn_ID","xp_ID","yb_ID","yp_ID","zn_ID","zb_ID", XA_ID, YA_ID, ZA_ID
)
fun isAxoPlane(plane: Plane3D) = plane.id == AXO_PLANE_ID
fun isAxoPlaneTracePudorys(trace: PlaneTracePudorys) =
    trace.id == AXO_PLANE_TRACE_PUDORYS_ID || trace.parentId == AXO_PLANE_ID || trace.parent?.id == AXO_PLANE_ID
fun isAxoPlaneTraceNarys(trace: PlaneTraceNarys) =
    trace.id == AXO_PLANE_TRACE_NARYS_ID || trace.parentId == AXO_PLANE_ID || trace.parent?.id == AXO_PLANE_ID
fun isAxoPlaneTraceBokorys(trace: PlaneTraceBokorys) =
    trace.id == AXO_PLANE_TRACE_BOKORYS_ID || trace.parentId == AXO_PLANE_ID || trace.parent?.id == AXO_PLANE_ID
