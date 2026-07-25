package ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import mongecad.web.generated.resources.NotoSans_Regular
import mongecad.web.generated.resources.Res
import org.jetbrains.compose.resources.Font

/**
 * Výchozí rodina písma pro veškerý text v UI (dialogy, panely, lišty).
 *
 * Desktop nechává `FontFamily.Default`, což Skia rozřeší na systémové písmo
 * a chybějící znaky doplní ze systémového fallbacku. V prohlížeči ale žádné
 * systémové fonty nejsou, takže dolní indexy průmětů (₀₁₂₃ₐ), horní indexy
 * a řecká písmena vycházely jako prázdné čtverečky.
 *
 * Noto Sans má plné pokrytí a je zároveň to, co jako systémové bezpatkové
 * písmo vybírá fontconfig na Linuxu – vzhled tedy odpovídá desktopu.
 * Stejný font slouží i jako fallback pro popisky na plátně
 * (viz `draw/mongescreen/labels/LabelsText.kt`).
 */
@Composable
fun rememberAppFontFamily(): FontFamily = FontFamily(Font(Res.font.NotoSans_Regular))
