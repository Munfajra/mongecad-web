package ui.mongeui.toolbar

// Na desktopu tenhle enum bydlí uvnitř ToolBar.kt; tady je zvlášť, aby na něm
// mohl viset MongeState bez tažení celého toolbaru.
enum class PaperFormat(val label: String, val wMm: Float, val hMm: Float) {
    A0("A0", 841f, 1189f),
    A1("A1", 594f, 841f),
    A2("A2", 420f, 594f),
    A3("A3", 297f, 420f),
    A4("A4", 210f, 297f),
    A5("A5", 148f, 210f)
}
