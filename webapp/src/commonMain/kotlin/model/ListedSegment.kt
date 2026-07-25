package model

import androidx.compose.ui.graphics.Color

data class ListedSegment(
    val name: String,
    val parent: Any?,
    val color: Color,
    val projectionType: ListProjectionType,
    val source: Any
)
