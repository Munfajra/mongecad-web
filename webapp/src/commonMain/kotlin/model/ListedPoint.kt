package model

import androidx.compose.ui.graphics.Color
import utils.UUID

enum class ListProjectionType { PUDORYS, NARYS,BOKORYS,AXO }

data class ListedPoint(
    val name: String?,
    val parent: Any?,
    val isSegmentEndpoint: Boolean,
    val isProjectedLine: Boolean,
    val projectionType: ListProjectionType,
    val source: Any,
    val id: String = UUID.randomUUID().toString(),
    val color: Color,
)