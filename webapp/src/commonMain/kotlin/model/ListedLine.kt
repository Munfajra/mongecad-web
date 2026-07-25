package model

import androidx.compose.ui.graphics.Color

data class ListedLine(
    val name: String,
    val parent: Any?,
    val color: Color,
    val projectionType: ListProjectionType,
    val source: Any?
)
