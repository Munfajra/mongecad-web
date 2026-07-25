package model.classes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import model.LineStyle
class HelpLineStyleSettings {
    var color by mutableStateOf(Color.Gray)
    var style by mutableStateOf(LineStyle.Solid)
    var strokeWidth by mutableStateOf(1f)
}
