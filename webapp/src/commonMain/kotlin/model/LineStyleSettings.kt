package model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

class LineStyleSettings {
    var color by mutableStateOf(Color.Black)
    var style by mutableStateOf(LineStyle.Solid)
    var strokeWidth by mutableStateOf(1f)
}
