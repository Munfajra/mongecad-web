package model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

enum class MenuCommand { NewFile, Open, Save, SaveAs, Export, Print, Preferences, Quit, OpenMonge,OpenKoto, OpenAxo }

class AppMenuBus {
    var pending by mutableStateOf<MenuCommand?>(null)
}

val LocalMenuBus = staticCompositionLocalOf<AppMenuBus> {
    error("MenuBus not provided")
}