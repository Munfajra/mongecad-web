package ui

/**
 * Otevření stránky webu MongeCAD (stažení, návody…) v nové záložce.
 * Desktop na tohle používá `java.awt.Desktop.browse`.
 */
expect fun openWebsitePage(path: String)
