package serialization
import monge.input.ruledsurface.captureRuledSurfaceGeometry


import model.classes.isAxis
import model.classes.isAxisProjection
import model.classes.isAxisX
import model.classes.isAxisY
import model.classes.isAxisZ
import model.classes.isAxoPlane
import model.classes.isAxoPlaneTraceBokorys
import model.classes.isAxoPlaneTraceNarys
import model.classes.isAxoPlaneTracePudorys
import model.classes.isOrigin
import model.classes.isX12Line3D
import model.classes.isX12Narys
import model.classes.isX12Pud
import model.ArcMode
import model.classes.*
import monge.input.lines.*
import serialization.classes.*
import state.MongeState


fun MongeState.toSerialized(): SerializedMongeState {
    ruledSurfaces.forEach { captureRuledSurfaceGeometry(this, it) }

    return SerializedMongeState(
        points3D = sharedPoints3D.map { it.toSerializable() },
        planes3D = planes3D
            .filterNot(::isAxoPlane)
            .map { it.toSerializable() },
        segments3D = segments3D.map { it.toSerializable() },
        segmentSolids3D = segmentSolids3D.map { it.toSerializable() },

        pointsPudorys = pointsPudorys.map { it.toSerializable() },
        pointsBokorys = this.pointsBokorys.map { it.toSerializable() },
        pointsNarys = pointsNarys.map { it.toSerializable() },
        pointsAxo = pointsAxo.map { it.toSerializable() },
        aidPoints = aidPointsLogical
            .filterNot(::isOrigin)
            .map {it. toSerializable()},
        conics3D = conics3D.map { it.toSerializable() },
        lines3D = this.lines3D
            .filterNot(::isX12Line3D)
            .filterNot (::isAxis)
            .map { it.toSerializable() },

        lines3DNarys = this.lines3DNarys
            .filterNot(::isX12Narys)
            .filterNot(::isAxisProjection)
            .map { it.toSerializable() },

        lines3DPudorys = this.lines3DPudorys
            .filterNot(::isX12Pud)
            .filterNot(::isAxisProjection)
            .map { it.toSerializable() },
        lines3DBokorys = this.lines3DBokorys
            .filterNot(::isAxisProjection)
            .map { it.toSerializable() },
        lines3DAxo = this.lines3DAxo
            .filterNot(::isAxisProjection)
            .map { it.toSerializable() },

        segmentsPudorys = segmentsPudorys.map { it.toSerializable() },
        segmentsNarys = segmentsNarys.map { it.toSerializable() },
        segmentsBokorys = segmentsBokorys.map { it.toSerializable() },
        segmentsAxo = segmentsAxo.map { it.toSerializable() },

        planeTracesPudorys = lineTracesPudorys
            .filterNot(::isAxoPlaneTracePudorys)
            .filterNot { it.isVirtual }
            .map { it.toSerializable() },
        planeTracesNarys = lineTracesNarys
            .filterNot(::isAxoPlaneTraceNarys)
            .filterNot { it.isVirtual }
            .map { it.toSerializable() },
        planeTracesBokorys = lineTracesBokorys
            .filterNot(::isAxoPlaneTraceBokorys)
            .filterNot { it.isVirtual }
            .map { it.toSerializable() },

        helpLinesPudorys = helpLinePudorys
            .filterNot (::isAxisY)
            .filterNot (::isAxisX)
            .map { it.toSerializable() },
        helpLinesNarys = helpLineNarys
            .filterNot (::isAxisZ)
            .map { it.toSerializable() },

        helpSegmentsPudorys = helpSegmentsPudorys.map { it.toSerializable() },
        helpSegmentsNarys = helpSegmentsNarys.map { it.toSerializable() },

        arcsNarys = arcsNarys.map { it.toSerializable() },
        arcsPudorys = arcsPudorys.map { it.toSerializable()},
        arcsBokorys = arcsBokorys.map { it.toSerializable() },
        arcsAxoOverlay = arcsAxoOverlay.map { it.toSerializable() },

        circlesPudorys = circlesPudorys.map { it.toSerializable() },
        circlesNarys = circlesNarys.map {it.toSerializable()},
        circlesBokorys = circlesBokorys.map { it.toSerializable() },
        polygons3D = polygons3D.map { it.toSerializable() },
        planePolygons2D = planePolygons2D.map { it.toSerializable() },


        conicSectionPudorys = conicsPudorys.map { conic ->
            val triple = conicInputPointsPudorys[conic.id]
            val hyperbola = hyperbolaInputsPudorys[conic.id]
            conic.toSerializable(triple, hyperbola)
        },
        conicSectionBokorys = conicsBokorys.map { conic ->
            val triple = conicInputPointsBokorys[conic.id]
            val hyperbola = hyperbolaInputsBokorys[conic.id]
            conic.toSerializable(triple, hyperbola)
        },

        conicSectionNarys = conicsNarys.map { conic ->
            val triple = conicInputPointsNarys[conic.id]
            val hyperbola = hyperbolaInputsNarys[conic.id]
            conic.toSerializable(triple, hyperbola)
        },
        conicSectionAxo = conicsAxo.map{conic ->
            val triple = conicInputPointsAxo[conic.id]
            val hyperbola = hyperbolaInputsAxo[conic.id]
            conic.toSerializable(triple, hyperbola)
        },
        circleArcsPudorys =
            circleArcEnds.mapNotNull { (circleId, ends) ->

                val mode = circleArcMode[circleId] ?: ArcMode.SHORTEST

                CircleArcPudorys(
                    circleId = circleId,
                    a = ends.first,
                    b = ends.second,
                    mode = mode
                ).toSerializable()
            },
        circleArcsNarys =
            circleArcEnds.mapNotNull { (circleId, ends) ->

                val mode = circleArcMode[circleId] ?: ArcMode.SHORTEST

                CircleArcNarys(
                    circleId = circleId,
                    a = ends.first,
                    b = ends.second,
                    mode = mode
                ).toSerializable()
            },
        ellipseArcsPudorys = serializeEllipseArcsPudorys(),
        ellipseArcsNarys = serializeEllipseArcsNarys(),
        parabolaArcsPudorys = serializeParabolaArcsPudorys(),
        parabolaArcsNarys = serializeParabolaArcsNarys(),
        hyperbolaBranch1Pudorys = serializeHyperbolaBranch1P(),
        hyperbolaBranch2Pudorys = serializeHyperbolaBranch2P(),
        hyperbolaBranch1Narys   = serializeHyperbolaBranch1N(),
        hyperbolaBranch2Narys   = serializeHyperbolaBranch2N(),
        ellipseArcsBokorys = serializeEllipseArcs2D(conicsBokorys.map { it.id }.toSet()),
        parabolaArcsBokorys = serializeParabolaArcs2D(conicsBokorys.map { it.id }.toSet()),
        hyperbolaBranch1Bokorys = serializeHyperbolaBranch1_2D(conicsBokorys.map { it.id }.toSet()),
        hyperbolaBranch2Bokorys = serializeHyperbolaBranch2_2D(conicsBokorys.map { it.id }.toSet()),
        ellipseArcsAxo = serializeEllipseArcs2D(conicsAxo.map { it.id }.toSet()),
        parabolaArcsAxo = serializeParabolaArcs2D(conicsAxo.map { it.id }.toSet()),
        hyperbolaBranch1Axo = serializeHyperbolaBranch1_2D(conicsAxo.map { it.id }.toSet()),
        hyperbolaBranch2Axo = serializeHyperbolaBranch2_2D(conicsAxo.map { it.id }.toSet()),
        ellipseArcs3D = serializeEllipseArcs3D(),
        parabolaArcs3D = serializeParabolaArcs3D(),
        hyperbolaArcs3D = serializeHyperbolaArcs3D(),
        conicSegmentations = serializeConicSegmentations(),
        conicalSurfaces = conicalSurfaces.map { it.toSerializable() },
        cylindricalSurfaces = cylindricalSurfaces.map {it.toSerializable()},
        ruledSurfaces = ruledSurfaces.map { it.toSerializable() },
        spheres3D = spheres3D.map { it.toSerializable() },
        projectionMode = projectionMode,
        xAxisDirection = xAxisDirection,
        yAxisDirectionPlane = yAxisDirectionPlane,
        paperAnchorPinned = paperAnchorPinned,
        paperAnchorLogical = paperAnchorLogical.toSerializable(),
        showPaperPreview = showPaperPreview,
        labelOffsetsNarys = this.labelOffsetsNarys.mapValues { it.value.toSerializable() },
        labelOffsetsPudorys = this.labelOffsetsPudorys.mapValues { it.value.toSerializable() },
        labelOffsetsBokorys = this.labelOffsetsBokorys.mapValues { it.value.toSerializable() },
        labelOffsetsHelpNarys = this.labelOffsetsHelpNarys.mapValues { it.value.toSerializable() },
        labelOffsetsHelpPudorys = this.labelOffsetsHelpPudorys.mapValues { it.value.toSerializable() },
        labelOffsetsPointsNarys = this.labelOffsetsPointsNarys.mapValues { it.value.toSerializable() },
        labelOffsetsPointsPudorys = this.labelOffsetsPointsPudorys.mapValues { it.value.toSerializable() },
        labelOffsetsPointsBokorys = this.labelOffsetsPointsBokorys.mapValues { it.value.toSerializable() },
        labelOffsetsPointsAxo = this.labelOffsetsPointsAxo.mapValues { it.value.toSerializable() },
        labelOffsetsTracePudorys = this.labelOffsetsTracePudorys.mapValues { it.value.toSerializable() },
        labelOffsetsTraceNarys = this.labelOffsetsTraceNarys.mapValues { it.value.toSerializable() },
        labelOffsetsTraceBokorys = this.labelOffsetsTraceBokorys.mapValues { it.value.toSerializable() },
        labelOffsetsAidPoints = this.labelOffsetsAidPoints.mapValues { it.value.toSerializable() },
        labelOffsetsAOPoints = this.labelOffsetsAOPoints.mapValues { it.value.toSerializable() },
        labelOffsetsAOLines = this.labelOffsetsAOLines.mapValues { it.value.toSerializable() },
        labelOffsetsAxoLines = this.labelOffsetsAxoLines.mapValues { it.value.toSerializable() },
        labelOffsetsSegmentsPudorys = this.labelOffsetsSegmentsPudorys.mapValues { it.value.toSerializable() },
        labelOffsetsSegmentsNarys = this.labelOffsetsSegmentsNarys.mapValues { it.value.toSerializable() },
        labelOffsetsSegmentsBokorys = this.labelOffsetsSegmentsBokorys.mapValues { it.value.toSerializable() },
        labelOffsetsSegmentsAxo = this.labelOffsetsSegmentsAxo.mapValues { it.value.toSerializable() },
        labelOffsetsHelpSegmentsPudorys = this.labelOffsetsHelpSegmentsPudorys.mapValues { it.value.toSerializable() },
        labelOffsetsHelpSegmentsNarys = this.labelOffsetsHelpSegmentsNarys.mapValues { it.value.toSerializable() },
        labelOffsetsAOSegments = this.labelOffsetsAOSegments.mapValues { it.value.toSerializable() },
        axisVisible = this.axisVisible,
        curvesNarys = this.curvesNarys.map { it.toSerializable() },
        curvesPudorys = this.curvesPudorys.map { it.toSerializable() },
        curves3D = this.curves3D.map { it.toSerializable() },
        curvesBokorys = this.curvesBokorys.map { it.toSerializable() },
        curvesAxo = this.curvesAxo.map { it.toSerializable() },
        solidsOfRevolutionsNarys = solidsOfRevolutionNarys.map { it.toSerializable() },
        solidsOfRevolutionsPudorys = solidsOfRevolutionPudorys.map { it.toSerializable() },
        axoModel = this.activeAxoModel.toSerializable(),
        axoOverlayPoint = this.axoOverlayPoints.map { it.toSerializable() },
        axoOverlayLine = this.axoOverlayLines.map { it.toSerializable() },
        axoOverlaySegment = this.axoOverlaySegments.map { it.toSerializable() },
        intersectionGroups = this.intersectionGroups.map { group ->
            SerializableIntersectionGroup(
                id = group.id,
                operandAId = group.operandAId,
                operandBId = group.operandBId,
                operandALabel = group.operandALabel,
                operandBLabel = group.operandBLabel,
                parts = group.parts.map { SerializableIntersectionPartRef(it.kind.name, it.id) },
                creationIndex = group.creationIndex
            )
        },


        )

}


