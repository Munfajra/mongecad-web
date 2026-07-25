package export.pdfRenderer

private const val MM_PER_INCH = 25.4f
private const val PT_PER_INCH = 72f

/**
 * Rozměry stránky v bodech.
 *
 * Desktop tu používá PDFBoxový `PDRectangle`; web PDF negeneruje, takže
 * stačí dvojice rozměrů. Název `PdfPage` zůstává, aby exportní dialog
 * a rasterizér byly s desktopem porovnatelné řádek po řádku.
 */
data class PageRect(val width: Float, val height: Float)

enum class PdfPage(val widthMm: Float, val heightMm: Float) {
    A0(841f, 1189f),
    A1(594f, 841f),
    A2(420f, 594f),
    A3(297f, 420f),
    A4(210f, 297f),
    A5(148f, 210f);

    fun toPDRectangle(portrait: Boolean): PageRect {
        val wMm = if (portrait) widthMm else heightMm
        val hMm = if (portrait) heightMm else widthMm
        return PageRect(
            wMm / MM_PER_INCH * PT_PER_INCH,
            hMm / MM_PER_INCH * PT_PER_INCH
        )
    }
}

/** Otočení obdélníku podle orientace – protějšek `orientedRect` z desktopu. */
fun orientedRect(base: PageRect, portrait: Boolean): PageRect =
    if (portrait == (base.height >= base.width)) base
    else PageRect(base.height, base.width)
