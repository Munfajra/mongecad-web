package draw.mongescreen.previews.lines.previewlinesconstrucion

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import draw.mongescreen.axoPreviewDashPatternPx
import geometry.clipInfiniteLineToQuad
import draw.mongescreen.objects.orth.clipLineBelowX12
import model.VisibleQuad
import model.classes.Point3DBokorys
import utils.dotProduct



// Vyříznuto: drawDashedPreviewLineBokorysAxo, drawDashedParallelLinePreviewBokorysAxo – axo varianty náhledů; web axonometrii nekreslí.