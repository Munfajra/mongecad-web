package dialogs

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.focus.FocusRequester

data class LineInputRow(
    val name: MutableState<String> = mutableStateOf(""),
    val px: MutableState<String> = mutableStateOf(""),
    val py: MutableState<String> = mutableStateOf(""),
    val pz: MutableState<String> = mutableStateOf(""),
    val dx: MutableState<String> = mutableStateOf(""),
    val dy: MutableState<String> = mutableStateOf(""),
    val dz: MutableState<String> = mutableStateOf(""),
    val nameFocus: FocusRequester = FocusRequester(),
    val pxFocus: FocusRequester = FocusRequester(),
    val pyFocus: FocusRequester = FocusRequester(),
    val pzFocus: FocusRequester = FocusRequester(),
    val dxFocus: FocusRequester = FocusRequester(),
    val dyFocus: FocusRequester = FocusRequester(),
    val dzFocus: FocusRequester = FocusRequester(),
    val shouldRequestFocus: MutableState<Boolean> = mutableStateOf(false)
)
